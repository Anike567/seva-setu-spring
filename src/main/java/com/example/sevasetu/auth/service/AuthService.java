package com.example.sevasetu.auth.service;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.sevasetu.common.ApiResponse;
import com.example.sevasetu.auth.dto.SendOtpRequest;

import java.security.SecureRandom;
import java.util.UUID;

@Service
public class AuthService {

    private final JdbcClient jdbcClient;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Transactional
    public ResponseEntity<ApiResponse<Void>> sendOtp(SendOtpRequest request) {
        try {
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

            // TODO: Call SMS provider gateway (e.g., Twilio, Fast2SMS) to send generatedOtp

            return ResponseEntity.ok(ApiResponse.success("OTP sent successfully to phone number: " + request.phoneNumber()));

        } catch (Exception e) {
            System.err.println("Error sending OTP: " + e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to send OTP: " + e.getMessage()));
        }
    }
}