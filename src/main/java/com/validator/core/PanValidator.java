package com.validator.core;

import com.validator.model.DocumentType;
import com.validator.model.ReasonCode;
import com.validator.model.ValidationResult;
import com.validator.model.ValidationResult.Builder;
import com.validator.model.ValidationResult.Check;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class PanValidator {

    static final Set<Character> CATEGORIES = Set.of(
            'P', 'C', 'H', 'A', 'B', 'G', 'J', 'L', 'F', 'T');

    private static final String[] LABELS = {
            "Input provided",
            "Only allowed characters (A-Z, 0-9)",
            "Length is 10 characters",
            "Positions 1-5 are letters",
            "Positions 6-9 are digits",
            "Position 10 is a letter",
            "4th character is a valid category"
    };

    private PanValidator() {}

    public static ValidationResult validate(String raw) {
        String original = raw == null ? "" : raw;
        Builder b = ValidationResult.builder(DocumentType.PAN, original);

        String trimmed = Normalizer.trim(original);
        if (trimmed.isEmpty()) {
            return b.reason(ReasonCode.EMPTY)
                    .message("PAN is empty.")
                    .checks(checks(0, new String[]{"No value entered"}))
                    .build();
        }

        String normalized = Normalizer.normalizePan(original);
        b.normalized(normalized);

        char badChar = 0;
        boolean charsetOk = true;
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (!(Normalizer.isAsciiLetter(c) || Normalizer.isAsciiDigit(c))) {
                charsetOk = false;
                badChar = c;
                break;
            }
        }
        if (!charsetOk) {
            return b.reason(ReasonCode.INVALID_CHARACTER)
                    .message("PAN contains an invalid character: '" + badChar + "'.")
                    .checks(checks(1, new String[]{"", "Found '" + badChar + "'"}))
                    .build();
        }

        if (normalized.length() != 10) {
            return b.reason(ReasonCode.PAN_WRONG_LENGTH)
                    .message("PAN must be 10 characters, but got " + normalized.length() + ".")
                    .checks(checks(2, new String[]{"", "", "Got " + normalized.length()}))
                    .build();
        }

        int p = firstNot(normalized, 0, 4, false);
        if (p != -1) {
            return b.reason(ReasonCode.PAN_EXPECTED_LETTER)
                    .message("Expected a letter at position " + (p + 1) + " (found '"
                            + normalized.charAt(p) + "').")
                    .checks(checks(3, new String[]{"", "", "", "Position " + (p + 1)
                            + " is '" + normalized.charAt(p) + "'"}))
                    .build();
        }

        p = firstNot(normalized, 5, 8, true);
        if (p != -1) {
            return b.reason(ReasonCode.PAN_EXPECTED_DIGIT)
                    .message("Expected a digit at position " + (p + 1) + " (found '"
                            + normalized.charAt(p) + "').")
                    .checks(checks(4, new String[]{"", "", "", "", "Position " + (p + 1)
                            + " is '" + normalized.charAt(p) + "'"}))
                    .build();
        }

        if (!Normalizer.isAsciiLetter(normalized.charAt(9))) {
            char c = normalized.charAt(9);
            return b.reason(ReasonCode.PAN_EXPECTED_LETTER)
                    .message("Expected a letter at position 10 (found '" + c + "').")
                    .checks(checks(5, new String[]{"", "", "", "", "", "Position 10 is '" + c + "'"}))
                    .build();
        }

        char cat = normalized.charAt(3);
        if (!CATEGORIES.contains(cat)) {
            return b.reason(ReasonCode.PAN_INVALID_CATEGORY)
                    .message("4th character '" + cat + "' is not a valid PAN holder category "
                            + "(must be one of P,C,H,A,B,G,J,L,F,T).")
                    .checks(checks(6, new String[]{"", "", "", "", "", "", "'" + cat + "' not a category"}))
                    .build();
        }

        String[] details = {"Value received", "Letters and digits only", "10 characters",
                "A-Z in positions 1-5", "0-9 in positions 6-9", "Check letter",
                "'" + cat + "' = " + categoryLabel(cat)};
        return b.message("Valid PAN format. Format validation is not identity verification.")
                .checks(checks(-1, details))
                .build();
    }

    private static int firstNot(String s, int from, int to, boolean digit) {
        for (int i = from; i <= to; i++) {
            char c = s.charAt(i);
            boolean ok = digit ? Normalizer.isAsciiDigit(c) : Normalizer.isAsciiLetter(c);
            if (!ok) {
                return i;
            }
        }
        return -1;
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

    static String categoryLabel(char c) {
        return switch (c) {
            case 'P' -> "Individual (Person)";
            case 'C' -> "Company";
            case 'H' -> "Hindu Undivided Family (HUF)";
            case 'A' -> "Association of Persons (AOP)";
            case 'B' -> "Body of Individuals (BOI)";
            case 'G' -> "Government Agency";
            case 'J' -> "Artificial Juridical Person";
            case 'L' -> "Local Authority";
            case 'F' -> "Firm / LLP";
            case 'T' -> "Trust (AOP)";
            default -> "Unknown";
        };
    }
}
