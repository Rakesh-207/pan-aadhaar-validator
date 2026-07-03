package com.validator.core;

import com.validator.model.DocumentType;
import com.validator.model.ReasonCode;
import com.validator.model.ValidationResult;
import com.validator.model.ValidationResult.Builder;
import com.validator.model.ValidationResult.Check;

import java.util.ArrayList;
import java.util.List;

public final class AadhaarValidator {

    private static final String[] LABELS = {
            "Input provided",
            "Only digits 0-9",
            "Length is 12 digits",
            "Does not start with 0 or 1",
            "Verhoeff checksum"
    };

    private AadhaarValidator() {}

    public static ValidationResult validate(String raw) {
        String original = raw == null ? "" : raw;
        Builder b = ValidationResult.builder(DocumentType.AADHAAR, original);

        String trimmed = Normalizer.trim(original);
        if (trimmed.isEmpty()) {
            return b.reason(ReasonCode.EMPTY)
                    .message("Aadhaar is empty.")
                    .checks(checks(0, new String[]{"No value entered"}))
                    .build();
        }

        String normalized = Normalizer.normalizeAadhaar(original);
        b.normalized(normalized);

        char bad = 0;
        boolean nonAsciiDigit = false;
        boolean invalidChar = false;
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (Normalizer.isAsciiDigit(c)) {
                continue;
            }
            bad = c;
            nonAsciiDigit = Character.isDigit(c);
            invalidChar = !nonAsciiDigit;
            break;
        }
        if (nonAsciiDigit) {
            return b.reason(ReasonCode.AADHAAR_NON_ASCII_DIGIT)
                    .message("Aadhaar must use ASCII digits 0-9 only; found '" + bad + "'.")
                    .checks(checks(1, new String[]{"", "Locale digit '" + bad + "' rejected"}))
                    .build();
        }
        if (invalidChar) {
            return b.reason(ReasonCode.INVALID_CHARACTER)
                    .message("Aadhaar contains an invalid character: '" + bad + "'.")
                    .checks(checks(1, new String[]{"", "Found '" + bad + "'"}))
                    .build();
        }

        if (normalized.length() != 12) {
            return b.reason(ReasonCode.AADHAAR_WRONG_LENGTH)
                    .message("Aadhaar must be 12 digits, but got " + normalized.length() + ".")
                    .checks(checks(2, new String[]{"", "", "Got " + normalized.length()}))
                    .build();
        }

        char first = normalized.charAt(0);
        if (first == '0' || first == '1') {
            return b.reason(ReasonCode.AADHAAR_LEADING_DIGIT)
                    .message("Aadhaar must not start with 0 or 1 (UIDAI rule); first digit is '"
                            + first + "'.")
                    .checks(checks(3, new String[]{"", "", "", "Starts with '" + first + "'"}))
                    .build();
        }

        if (!Verhoeff.validate(normalized)) {
            return b.reason(ReasonCode.AADHAAR_VERHOEFF_FAILED)
                    .message("Aadhaar failed the Verhoeff checksum (possible typo).")
                    .checks(checks(4, new String[]{"", "", "", "", "Checksum mismatch"}))
                    .build();
        }

        String[] details = {"Value received", "ASCII digits only", "12 digits",
                "Starts with '" + first + "'", "Checksum verified"};
        return b.message("Valid Aadhaar format. Format validation is not identity verification.")
                .checks(checks(-1, details))
                .build();
    }

    private static List<Check> checks(int failIndex, String[] details) {
        List<Check> list = new ArrayList<>(LABELS.length);
        for (int i = 0; i < LABELS.length; i++) {
            String detail = (details != null && i < details.length) ? details[i] : "";
            String status;
            if (failIndex < 0) {
                status = "pass";
            } else if (i < failIndex) {
                status = "pass";
            } else if (i == failIndex) {
                status = "fail";
            } else {
                status = "skip";
            }
            list.add(new Check(LABELS[i], status, detail));
        }
        return list;
    }
}
