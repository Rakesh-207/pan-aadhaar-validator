package com.validator.model;

public enum ReasonCode {
    VALID("VALID"),
    EMPTY("EMPTY"),
    INVALID_CHARACTER("INVALID_CHARACTER"),
    PAN_WRONG_LENGTH("PAN_WRONG_LENGTH"),
    PAN_EXPECTED_LETTER("PAN_EXPECTED_LETTER"),
    PAN_EXPECTED_DIGIT("PAN_EXPECTED_DIGIT"),
    PAN_INVALID_CATEGORY("PAN_INVALID_CATEGORY"),
    AADHAAR_WRONG_LENGTH("AADHAAR_WRONG_LENGTH"),
    AADHAAR_NON_ASCII_DIGIT("AADHAAR_NON_ASCII_DIGIT"),
    AADHAAR_LEADING_DIGIT("AADHAAR_LEADING_DIGIT"),
    AADHAAR_VERHOEFF_FAILED("AADHAAR_VERHOEFF_FAILED");

    private final String wire;

    ReasonCode(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }
}
