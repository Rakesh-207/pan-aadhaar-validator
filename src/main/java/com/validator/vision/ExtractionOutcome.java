package com.validator.vision;

import com.validator.json.Json;
import com.validator.model.DocumentType;
import com.validator.model.ValidationResult;

/**
 * The full result of an image-extraction-and-validate cycle: the deterministic
 * {@link ValidationResult} (reused unchanged from the text pipeline) augmented
 * with advisory extraction metadata for the client.
 */
public final class ExtractionOutcome {

    private static final String TRANSIENT_NOTE =
            "Image processed transiently for extraction and not stored.";

    private final ValidationResult validation;
    private final String extractedType;
    private final DocumentType hintType;
    private final DocumentType effectiveType;
    private final boolean typeMismatch;
    private final String value;
    private final String confidence;

    public ExtractionOutcome(ValidationResult validation, String extractedType, DocumentType hintType,
                             DocumentType effectiveType, boolean typeMismatch, String value,
                             String confidence) {
        this.validation = validation;
        this.extractedType = extractedType == null ? "" : extractedType;
        this.hintType = hintType;
        this.effectiveType = effectiveType;
        this.typeMismatch = typeMismatch;
        this.value = value == null ? "" : value;
        this.confidence = confidence == null ? "" : confidence;
    }

    public ValidationResult validation() {
        return validation;
    }

    public String extractedType() {
        return extractedType;
    }

    public DocumentType hintType() {
        return hintType;
    }

    public DocumentType effectiveType() {
        return effectiveType;
    }

    public boolean typeMismatch() {
        return typeMismatch;
    }

    public String value() {
        return value;
    }

    public String confidence() {
        return confidence;
    }

    /**
     * Serialise as the standard {@code ValidationResult} JSON with an added
     * {@code extraction} block, so the frontend can reuse its existing renderer.
     */
    public String toJson() {
        String base = validation.toJson();
        String head = base.substring(0, base.length() - 1);
        StringBuilder sb = new StringBuilder(head.length() + 220);
        sb.append(head).append(',');
        sb.append("\"extraction\":{");
        sb.append("\"extractedType\":\"").append(Json.escape(extractedType)).append("\",");
        sb.append("\"hintType\":\"").append(hintType == null ? "" : hintType.name()).append("\",");
        sb.append("\"effectiveType\":\"").append(effectiveType == null ? "" : effectiveType.name()).append("\",");
        sb.append("\"typeMismatch\":").append(typeMismatch).append(',');
        sb.append("\"value\":\"").append(Json.escape(value)).append("\",");
        sb.append("\"confidence\":\"").append(Json.escape(confidence)).append("\",");
        sb.append("\"transient\":true,");
        sb.append("\"note\":\"").append(Json.escape(TRANSIENT_NOTE)).append("\"");
        sb.append("}}");
        return sb.toString();
    }
}
