package com.validator.core;

import com.validator.model.DocumentType;
import com.validator.model.ValidationResult;

public final class DocumentValidator {

    private DocumentValidator() {}

    public static ValidationResult validate(DocumentType type, String value) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        return switch (type) {
            case PAN -> PanValidator.validate(value);
            case AADHAAR -> AadhaarValidator.validate(value);
        };
    }

    public static ValidationResult validate(String type, String value) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        return validate(DocumentType.valueOf(type.trim().toUpperCase()), value);
    }
}
