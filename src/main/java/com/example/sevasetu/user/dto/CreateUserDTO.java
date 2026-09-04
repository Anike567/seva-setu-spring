package com.example.sevasetu.user.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateUserDTO(

    // ==========================================
    // REQUIRED FIELDS (Initial Account Creation)
    // ==========================================

    // @NotBlank(message = "Phone number is required")
    // @Pattern(regexp = "^[6-9]\\d{9}$", message = "Invalid Indian mobile number")
    // String phoneNumber,

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 150, message = "Full name must be between 2 and 150 characters")
    String fullName,

    // ==========================================
    // OPTIONAL FIELDS (Can be updated later)
    // ==========================================

    @Min(value = 0, message = "Age cannot be negative")
    @Max(value = 125, message = "Age cannot exceed 125")
    Integer age,

    Gender gender,

    CasteCategory casteCategory,

    @DecimalMin(value = "0.00", inclusive = true, message = "Annual income cannot be negative")
    @Digits(integer = 10, fraction = 2, message = "Annual income format invalid (up to 10 digits and 2 decimals)")
    BigDecimal annualIncome,

    RationCardType rationCard,

    @Positive(message = "State ID must be a valid positive integer")
    Integer stateId,

    @Positive(message = "District ID must be a valid positive integer")
    Integer districtId,

    Boolean isStateDomicile,

    Boolean isDifferentlyAbled,

    @Size(max = 100, message = "Occupation cannot exceed 100 characters")
    String occupation

) {
    public enum Gender {
        MALE,
        FEMALE,
        TRANSGENDER,
        ALL
    }

    public enum RationCardType {
        BPL,
        APL,
        AAY,
        NONE
    }
}