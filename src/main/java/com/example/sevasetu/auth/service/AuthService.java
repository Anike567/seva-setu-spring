package com.example.sevasetu.auth.service;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.sevasetu.common.ApiResponse;
import com.example.sevasetu.entities.RefreshToken;
import com.example.sevasetu.auth.dto.OtpRecord;
import com.example.sevasetu.auth.dto.RefreshTokenDto;
import com.example.sevasetu.auth.dto.SendOtpRequest;
import com.example.sevasetu.auth.dto.VerifyOtp;

import java.util.Map;
import java.util.Optional;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

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

        @Transactional(rollbackFor = Exception.class)
        public ResponseEntity<ApiResponse<Void>> sendOtp(SendOtpRequest request) {
                // 1. Invalidate older pending OTPs for this phone number
                String invalidateOldOtp = """
                                UPDATE otps
                                SET is_verified = true
                                WHERE phone_number = :phoneNumber AND is_verified = false
                                """;

                jdbcClient.sql(invalidateOldOtp)
                                .param("phoneNumber", request.phoneNumber())
                                .update();

                // 2. Generate random 6-digit OTP
                String generatedOtp = String.format("%06d", secureRandom.nextInt(1_000_000));
                System.out.println("Generated OTP for " + request.phoneNumber() + ": " + generatedOtp);
                // 3. Insert new OTP record with a 5-minute TTL
                String insertOtp = """
                                INSERT INTO otps (id, phone_number, otp_code, expires_at, is_verified, attempts, created_at)
                                VALUES (:id, :phoneNumber, :otpCode, NOW() + INTERVAL '5 minutes', false, 0, NOW())
                                """;

                jdbcClient.sql(insertOtp)
                                .param("id", UUID.randomUUID())
                                .param("phoneNumber", request.phoneNumber())
                                .param("otpCode", generatedOtp)
                                .update();

                return ResponseEntity.ok(ApiResponse.success(
                                "OTP sent successfully to phone number: " + request.phoneNumber()));
        }

        @Transactional(rollbackFor = Exception.class)
        public ResponseEntity<ApiResponse<Map<String, String>>> verifyOtp(VerifyOtp verifyOtp) {

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

                // 1. Fetch latest active OTP
                OtpRecord otpRecord = jdbcClient.sql(selectOtpSql)
                                .param("phoneNumber", verifyOtp.phoneNumber())
                                .query(OtpRecord.class)
                                .optional()
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "No active OTP found. Please request a new one."));

                // 2. Check maximum attempts
                if (otpRecord.attempts() >= MAX_ATTEMPTS) {
                        throw new IllegalStateException("Maximum OTP attempts exceeded. Please request a new one.");
                }

                // 3. Check expiration
                if (otpRecord.expiresAt().isBefore(LocalDateTime.now())) {
                        throw new IllegalArgumentException("OTP expired. Please request new OTP.");
                }

                // 4. Validate OTP match
                if (!otpRecord.otpCode().equals(verifyOtp.otp())) {
                        int updatedAttempts = otpRecord.attempts() + 1;

                        // Persist the attempt count increment
                        String incrementAttemptSql = """
                                        UPDATE otps
                                        SET attempts = :attempts
                                        WHERE id = :id
                                        """;

                        jdbcClient.sql(incrementAttemptSql)
                                        .param("attempts", updatedAttempts)
                                        .param("id", otpRecord.id())
                                        .update();

                        int remainingAttempts = MAX_ATTEMPTS - updatedAttempts;
                        throw new IllegalArgumentException("Invalid OTP. Remaining attempts: " + remainingAttempts);
                }

                // 5. Mark OTP as verified (fixed "whre" typo)
                String markVerifiedSql = """
                                UPDATE otps
                                SET is_verified = true
                                WHERE id = :id
                                """;

                jdbcClient.sql(markVerifiedSql)
                                .param("id", otpRecord.id())
                                .update();

                // 6. Return tokens
                String accessToken = jwtService.generateAccessToken(verifyOtp.phoneNumber(), Map.of("role", "user"));
                String refreshToken = UUID.randomUUID().toString();
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

                jdbcClient.sql(writeRefreshToken)
                                .param("phoneNumber", verifyOtp.phoneNumber())
                                .param("refreshToken", refreshToken)
                                .param("expiresAt", expiresAt)
                                .update();
                return ResponseEntity.ok(ApiResponse.success(
                                "OTP verified successfully",
                                Map.of(
                                                "accessToken", accessToken,
                                                "refreshToken", refreshToken,
                                                "tokenType", "Bearer")));
        }

        @Transactional
        public ResponseEntity<ApiResponse<Map<String, String>>> refreshToken(RefreshTokenDto refreshTokenDto) {
                String validateRefreshTokenSql = """
                                SELECT id, phone_number, token, expiry_date, revoked, created_at
                                FROM refresh_tokens
                                WHERE phone_number = :phoneNumber AND token = :refreshToken
                                """;

                // Explicit row mapping avoids JDBC driver issues when converting timestamptz to
                // Instant
                Optional<RefreshToken> refreshTokenOpt = jdbcClient.sql(validateRefreshTokenSql)
                                .param("phoneNumber", refreshTokenDto.phoneNumber())
                                .param("refreshToken", refreshTokenDto.refreshToken())
                                .query((rs, rowNum) -> {
                                        RefreshToken token = new RefreshToken();
                                        token.setId(rs.getObject("id", UUID.class));
                                        token.setPhoneNumber(rs.getString("phone_number"));
                                        token.setToken(rs.getString("token"));

                                        var expiry = rs.getTimestamp("expiry_date");
                                        if (expiry != null) {
                                                token.setExpiryDate(expiry.toInstant());
                                        }

                                        var created = rs.getTimestamp("created_at");
                                        if (created != null) {
                                                token.setCreatedAt(created.toInstant());
                                        }

                                        token.setRevoked(rs.getBoolean("revoked"));
                                        return token;
                                })
                                .optional();

                // Check if token exists, is revoked, or is expired
                if (refreshTokenOpt.isEmpty() || refreshTokenOpt.get().isRevoked()
                                || refreshTokenOpt.get().isExpired()) {
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                                        ApiResponse.error(
                                                        "No active session found or session expired. Please log in again."));
                }

                RefreshToken currentToken = refreshTokenOpt.get();

                // 1. Generate new credentials (Token Rotation)
                String newAccessToken = jwtService.generateAccessToken(currentToken.getPhoneNumber(),
                                Map.of("role", "user"));
                String newRefreshToken = UUID.randomUUID().toString();
                OffsetDateTime newExpiryDate = OffsetDateTime.now(ZoneOffset.UTC).plusDays(30);

                // 2. Update existing refresh token record in the database
                String updateTokenSql = """
                                UPDATE refresh_tokens
                                SET token = :newToken,
                                    expiry_date = :expiryDate,
                                    revoked = false
                                WHERE phone_number = :phoneNumber
                                """;

                jdbcClient.sql(updateTokenSql)
                                .param("newToken", newRefreshToken)
                                .param("expiryDate", newExpiryDate)
                                .param("phoneNumber", currentToken.getPhoneNumber())
                                .update();

                // 3. Return the new token pair
                Map<String, String> tokens = Map.of(
                                "accessToken", newAccessToken,
                                "refreshToken", newRefreshToken,
                                "tokenType", "Bearer");

                return ResponseEntity.ok(ApiResponse.success("session renewed successfully", tokens));
        }
}