package com.example.sevasetu.user.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum CasteCategory {
    SC("SC"),
    ST("ST"),
    CAT_1("CAT_1"),
    TWO_A("2A"),
    TWO_B("2B"),
    THREE_A("3A"),
    THREE_B("3B"),
    GENERAL("GENERAL");

    private final String value;

    CasteCategory(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static CasteCategory fromValue(String text) {
        if (text == null) return null;
        for (CasteCategory category : CasteCategory.values()) {
            if (category.value.equalsIgnoreCase(text) || category.name().equalsIgnoreCase(text)) {
                return category;
            }
        }
        throw new IllegalArgumentException("Invalid caste category: " + text);
    }
}