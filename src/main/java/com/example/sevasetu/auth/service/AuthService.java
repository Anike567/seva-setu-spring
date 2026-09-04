package com.example.sevasetu.auth.service;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.sevasetu.common.ApiResponse;
import com.example.sevasetu.auth.dto.OtpRecord;
import com.example.sevasetu.auth.dto.SendOtpRequest;
import com.example.sevasetu.auth.dto.VerifyOtp;

import java.util.Map;
import java.security.SecureRandom;
import java.time.LocalDateTime;
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
                .orElseThrow(() -> new IllegalArgumentException("No active OTP found. Please request a new one."));

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
        String accessToken = jwtService.generateAccessToken(verifyOtp.phoneNumber(), Map.of("role", "user") );
        String refreshToken = jwtService.generateRefreshToken(verifyOtp.phoneNumber());

        return ResponseEntity.ok(ApiResponse.success(
                "OTP verified successfully",
                Map.of(
                        "accessToken", accessToken,
                        "refreshToken", refreshToken,
                        "tokenType", "Bearer")));
    }
}