package com.example.sevasetu.auth.dto;


import java.time.LocalDateTime;
import java.util.UUID;

public record OtpRecord(
    UUID id,
    String otpCode,
    LocalDateTime expiresAt,
    boolean isVerified,
    int attempts,
    LocalDateTime createdAt
) {}