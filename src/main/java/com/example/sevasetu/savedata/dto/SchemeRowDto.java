package com.example.sevasetu.savedata.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record SchemeRowDto(
    UUID id,
    String slug,
    Map<String, Object> details,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
