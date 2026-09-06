package com.example.sevasetu.auth.service;

import com.example.sevasetu.auth.dto.OtpRecord;
import com.example.sevasetu.auth.dto.RefreshTokenDto;
import com.example.sevasetu.auth.dto.SendOtpRequest;
import com.example.sevasetu.auth.dto.VerifyOtp;
import com.example.sevasetu.common.ApiResponse;
import com.example.sevasetu.entities.RefreshToken;
import java.security.SecureRandom;
import java.sql.ResultSetMetaData;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final JdbcClient jdbcClient;
    private final JwtService jwtService;
    private final SecureRandom secureRandom = new SecureRandom();
    private static final int MAX_ATTEMPTS = 3;

    public AuthService(JdbcClient jdbcClient, JwtService jwtService) {
        this.jdbcClient = jdbcClient;
        this.jwtService = jwtService;
    }

    public ResponseEntity<ApiResponse<Void>> sendOtp(SendOtpRequest request) {
        // 1. Invalidate older pending OTPs (expire them rather than marking verified)
        String invalidateOldOtp = """
            UPDATE otps
            SET expires_at = NOW()
            WHERE phone_number = :phoneNumber AND is_verified = false AND expires_at > NOW()
            """;

        jdbcClient
            .sql(invalidateOldOtp)
            .param("phoneNumber", request.phoneNumber())
            .update();

        // 2. Generate random 6-digit OTP
        String generatedOtp = String.format(
            "%06d",
            secureRandom.nextInt(1_000_000)
        );

        System.out.println(generatedOtp);
        // 3. Persist new OTP record with 5-minute TTL
        String insertOtp = """
            INSERT INTO otps (id, phone_number, otp_code, expires_at, is_verified, attempts, created_at)
            VALUES (:id, :phoneNumber, :otpCode, NOW() + INTERVAL '5 minutes', false, 0, NOW())
            """;

        jdbcClient
            .sql(insertOtp)
            .param("id", UUID.randomUUID())
            .param("phoneNumber", request.phoneNumber())
            .param("otpCode", generatedOtp)
            .update();

        // TODO: Delegate generatedOtp to an SMS gateway (e.g., Twilio / AWS SNS / MSG91)

        return ResponseEntity.ok(
            ApiResponse.success("OTP sent successfully to: " + request.phoneNumber())
        );
    }

    public ResponseEntity<ApiResponse<Map<String, Object>>> verifyOtp(VerifyOtp verifyOtp) {
        // 1. Fetch latest active OTP
        String selectOtpSql = """
            SELECT
                id,
                otp_code AS "otpCode",
                expires_at AS "expiresAt",
                is_verified AS "isVerified",
                attempts,
                created_at AS "createdAt"
            FROM otps
            WHERE phone_number = :phoneNumber AND is_verified = false
            ORDER BY created_at DESC
            LIMIT 1
            """;

        OtpRecord otpRecord = jdbcClient
            .sql(selectOtpSql)
            .param("phoneNumber", verifyOtp.phoneNumber())
            .query(OtpRecord.class)
            .optional()
            .orElseThrow(() ->
                new IllegalArgumentException("No active OTP found. Please request a new one.")
            );

        // 2. Guard: check prior max attempts
        if (otpRecord.attempts() >= MAX_ATTEMPTS) {
            throw new IllegalStateException("Maximum OTP attempts exceeded. Please request a new one.");
        }

        // 3. Guard: check expiration (using UTC database-safe time)
        String checkExpirySql = "SELECT expires_at < NOW() FROM otps WHERE id = :id";
        Boolean isExpired = jdbcClient
            .sql(checkExpirySql)
            .param("id", otpRecord.id())
            .query(Boolean.class)
            .single();

        if (Boolean.TRUE.equals(isExpired)) {
            throw new IllegalArgumentException("OTP expired. Please request a new one.");
        }

        // 4. Validate OTP match and atomically increment attempt count on failure
        if (!otpRecord.otpCode().equals(verifyOtp.otp())) {
            String incrementAttemptSql = """
                UPDATE otps
                SET attempts = attempts + 1
                WHERE id = :id
                RETURNING attempts
                """;

            int updatedAttempts = jdbcClient
                .sql(incrementAttemptSql)
                .param("id", otpRecord.id())
                .query(Integer.class)
                .single();

            int remainingAttempts = Math.max(0, MAX_ATTEMPTS - updatedAttempts);
            throw new IllegalArgumentException("Invalid OTP. Remaining attempts: " + remainingAttempts);
        }

        // 5. Mark OTP as verified
        String markVerifiedSql = """
            UPDATE otps
            SET is_verified = true
            WHERE id = :id
            """;

        jdbcClient
            .sql(markVerifiedSql)
            .param("id", otpRecord.id())
            .update();

        // 6. Check if user profile exists
        Optional<Map<String, Object>> existingUser = fetchUserClaims(verifyOtp.phoneNumber());
        boolean isNewUser = existingUser.isEmpty();

        // 7. Assemble Claims based on onboarding state
        Map<String, Object> claims = new HashMap<>();
        String accessToken;
        String refreshToken = null;

        if (isNewUser) {
            // Limited temporary access token: restricted scope to complete profile
            claims.put("role", "TEMP_USER");
            claims.put("scope", "ONBOARDING");
            claims.put("isProfileComplete", false);

            accessToken = jwtService.generateAccessToken(verifyOtp.phoneNumber(), claims);
            // Notice: No long-lived refresh token issued until onboarding completes
        } else {
            // Full access token with loaded profile metadata
            claims.put("role", "USER");
            claims.put("isProfileComplete", true);
            claims.putAll(existingUser.get());

            accessToken = jwtService.generateAccessToken(verifyOtp.phoneNumber(), claims);
            refreshToken = rotateRefreshTokenInDb(verifyOtp.phoneNumber());
        }

        // 8. Prepare payload
        Map<String, Object> responseData = new HashMap<>();
        responseData.put("accessToken", accessToken);
        responseData.put("refreshToken", refreshToken);
        responseData.put("tokenType", "Bearer");
        responseData.put("isNewUser", isNewUser);

        return ResponseEntity.ok(
            ApiResponse.success("OTP verified successfully", responseData)
        );
    }

    @Transactional
    public ResponseEntity<ApiResponse<Map<String, String>>> refreshToken(RefreshTokenDto refreshTokenDto) {
        String validateRefreshTokenSql = """
            SELECT id, phone_number, token, expiry_date, revoked, created_at
            FROM refresh_tokens
            WHERE phone_number = :phoneNumber AND token = :refreshToken
            """;

        Optional<RefreshToken> refreshTokenOpt = jdbcClient
            .sql(validateRefreshTokenSql)
            .param("phoneNumber", refreshTokenDto.phoneNumber())
            .param("refreshToken", refreshTokenDto.refreshToken())
            .query((rs, rowNum) -> {
                RefreshToken token = new RefreshToken();
                token.setId(rs.getObject("id", UUID.class));
                token.setPhoneNumber(rs.getString("phone_number"));
                token.setToken(rs.getString("token"));

                var expiry = rs.getTimestamp("expiry_date");
                if (expiry != null) token.setExpiryDate(expiry.toInstant());

                var created = rs.getTimestamp("created_at");
                if (created != null) token.setCreatedAt(created.toInstant());

                token.setRevoked(rs.getBoolean("revoked"));
                return token;
            })
            .optional();

        if (
            refreshTokenOpt.isEmpty() ||
            refreshTokenOpt.get().isRevoked() ||
            refreshTokenOpt.get().isExpired()
        ) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ApiResponse.error("Session expired or invalid. Please log in again.")
            );
        }

        RefreshToken currentToken = refreshTokenOpt.get();

        // Check user existence & regenerate claims so they aren't lost on refresh
        Optional<Map<String, Object>> profile = fetchUserClaims(currentToken.getPhoneNumber());
        if (profile.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                ApiResponse.error("User profile not found. Please complete profile registration.")
            );
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "USER");
        claims.put("isProfileComplete", true);
        claims.putAll(profile.get());

        String newAccessToken = jwtService.generateAccessToken(currentToken.getPhoneNumber(), claims);
        String newRefreshToken = rotateRefreshTokenInDb(currentToken.getPhoneNumber());

        Map<String, String> tokens = Map.of(
            "accessToken", newAccessToken,
            "refreshToken", newRefreshToken,
            "tokenType", "Bearer"
        );

        return ResponseEntity.ok(ApiResponse.success("Session renewed successfully", tokens));
    }

    // Helper: fetches user profile columns dynamically
    private Optional<Map<String, Object>> fetchUserClaims(String phoneNumber) {
        String checkForProfileSql = """
            SELECT id, full_name, age, gender, caste_category, annual_income, occupation
            FROM users
            WHERE phone_number = :phoneNumber
            """;

        return jdbcClient
            .sql(checkForProfileSql)
            .param("phoneNumber", phoneNumber)
            .query((rs, rowNum) -> {
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();
                Map<String, Object> map = new HashMap<>();
                for (int i = 1; i <= columnCount; i++) {
                    String key = meta.getColumnLabel(i);
                    Object val = rs.getObject(i);
                    map.put(key, val);
                }
                return map;
            })
            .optional();
    }

    // Helper: atomic refresh token upsert
    private String rotateRefreshTokenInDb(String phoneNumber) {
        String newRefreshToken = UUID.randomUUID().toString();
        OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusDays(30);

        String writeRefreshToken = """
            INSERT INTO refresh_tokens (id, phone_number, token, expiry_date, revoked)
            VALUES (gen_random_uuid(), :phoneNumber, :refreshToken, :expiresAt, false)
            ON CONFLICT (phone_number)
            DO UPDATE SET
                token = EXCLUDED.token,
                expiry_date = EXCLUDED.expiry_date,
                revoked = false
            """;

        jdbcClient
            .sql(writeRefreshToken)
            .param("phoneNumber", phoneNumber)
            .param("refreshToken", newRefreshToken)
            .param("expiresAt", expiresAt)
            .update();

        return newRefreshToken;
    }
}
