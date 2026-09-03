package com.example.sevasetu.auth.dto;


import java.time.LocalDateTime;
import java.util.UUID;

public record OtpRecord(
    UUID id,
    String phoneNumber,
    String otpCode,
    LocalDateTime expiresAt,
    boolean verified,
    int attempts,
    LocalDateTime createdAt
) {}