package com.example.sevasetu.savedata.dto;

public record SchemeRowDto(
    String id,
    String slug,
    String schemeName,
    String shortTitle,
    String ministry,
    String department,
    String level,
    String schemeFor,
    String targetBeneficiaries, // stored as serialized string / JSON / comma-separated text
    String categories,          // stored as serialized string / JSON / comma-separated text
    String openDate,
    String closeDate,
    String briefDescription,
    String detailedDescription,
    String benefits,
    String eligibility,
    String exclusions
) {}
