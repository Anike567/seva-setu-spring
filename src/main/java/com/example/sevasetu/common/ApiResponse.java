package com.example.sevasetu.common;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL) // Omit null fields from final JSON
public record ApiResponse<T>(
    boolean success,
    String message,
    T data,
    String errorMessage
) {
    // Static helpers for clean code in controllers
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, null);
    }

    public static <T> ApiResponse<T> success(String message) {
        return new ApiResponse<>(true, message, null, null);
    }

    public static <T> ApiResponse<T> error(String errorMessage) {
        return new ApiResponse<>(false, null, null, errorMessage);
    }
}