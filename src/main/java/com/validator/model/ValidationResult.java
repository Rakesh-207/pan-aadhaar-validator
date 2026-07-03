package com.validator.model;

import com.validator.json.Json;

import java.util.ArrayList;
import java.util.List;

public final class ValidationResult {

    private final boolean valid;
    private final DocumentType documentType;
    private final String originalValue;
    private final String normalizedValue;
    private final ReasonCode reasonCode;
    private final String message;
    private final List<Check> checks;

    private ValidationResult(boolean valid, DocumentType documentType, String originalValue,
                             String normalizedValue, ReasonCode reasonCode, String message,
                             List<Check> checks) {
        this.valid = valid;
        this.documentType = documentType;
        this.originalValue = originalValue;
        this.normalizedValue = normalizedValue;
        this.reasonCode = reasonCode;
        this.message = message;
        this.checks = List.copyOf(checks);
    }

    public boolean isValid() {
        return valid;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public String getOriginalValue() {
        return originalValue;
    }

    public String getNormalizedValue() {
        return normalizedValue;
    }

    public ReasonCode getReasonCode() {
        return reasonCode;
    }

    public String getMessage() {
        return message;
    }

    public List<Check> getChecks() {
        return checks;
    }

    public static Builder builder(DocumentType type, String original) {
        return new Builder(type, original);
    }

    public record Check(String label, String status, String detail) {}

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"valid\":").append(valid).append(',');
        sb.append("\"documentType\":\"").append(esc(documentType == null ? "" : documentType.name())).append("\",");
        sb.append("\"originalValue\":\"").append(esc(originalValue)).append("\",");
        sb.append("\"normalizedValue\":\"").append(esc(normalizedValue)).append("\",");
        sb.append("\"reasonCode\":\"").append(esc(reasonCode == null ? "" : reasonCode.wire())).append("\",");
        sb.append("\"message\":\"").append(esc(message)).append("\",");
        sb.append("\"checks\":[");
        for (int i = 0; i < checks.size(); i++) {
            Check c = checks.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"label\":\"").append(esc(c.label())).append("\",");
            sb.append("\"status\":\"").append(esc(c.status())).append("\",");
            sb.append("\"detail\":\"").append(esc(c.detail())).append("\"}");
        }
        sb.append(']');
        sb.append('}');
        return sb.toString();
    }

    private static String esc(String s) {
        return Json.escape(s);
    }

    public static final class Builder {
        private final DocumentType type;
        private final String original;
        private List<Check> checks = new ArrayList<>();
        private String normalized = "";
        private boolean valid = true;
        private ReasonCode reasonCode = ReasonCode.VALID;
        private String message = "";

        Builder(DocumentType type, String original) {
            this.type = type;
            this.original = original == null ? "" : original;
        }

        public Builder normalized(String n) {
            this.normalized = n == null ? "" : n;
            return this;
        }

        public Builder reason(ReasonCode code) {
            this.reasonCode = code;
            this.valid = false;
            return this;
        }

        public Builder message(String m) {
            this.message = m == null ? "" : m;
            return this;
        }

        public Builder checks(List<Check> list) {
            this.checks = (list == null) ? new ArrayList<>() : new ArrayList<>(list);
            return this;
        }

        public ValidationResult build() {
            if (message == null || message.isEmpty()) {
                message = valid ? defaultValid(type) : reasonCode.wire();
            }
            return new ValidationResult(valid, type, original, normalized, reasonCode, message, checks);
        }

        private static String defaultValid(DocumentType t) {
            return (t == DocumentType.PAN ? "Valid PAN format." : "Valid Aadhaar format.")
                    + " Format validation is not identity verification.";
        }
    }
}
