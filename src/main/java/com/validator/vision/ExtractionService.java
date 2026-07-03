package com.validator.vision;

import com.validator.core.DocumentValidator;
import com.validator.model.DocumentType;
import com.validator.model.ValidationResult;

/**
 * Pure, deterministic orchestration: takes an advisory {@link Extraction} plus an
 * optional hint, resolves which validator to run, sanitises the value, and always
 * passes it through {@link DocumentValidator}. The model output is never trusted
 * on its own; it only selects (or suggests) the validator and seeds the value.
 *
 * <p>Type-resolution rules:
 * <ul>
 *   <li>An empty extracted value always yields {@code NO_DOCUMENT}.</li>
 *   <li>If a hint is supplied, it selects the validator (model type is advisory).</li>
 *   <li>With no hint, the model's type selects the validator; {@code UNKNOWN} is
 *       rejected as {@code NO_DOCUMENT}.</li>
 *   <li>A hint/model conflict still validates on the hint but flags
 *       {@code typeMismatch}.</li>
 * </ul>
 */
public final class ExtractionService {

    private ExtractionService() {}

    public static ExtractionOutcome extractAndValidate(Extraction extraction, DocumentType hintType)
            throws ExtractionException {
        String value = sanitize(extraction.value());
        if (value.isEmpty()) {
            throw new ExtractionException(ExtractionException.Status.NO_DOCUMENT,
                    "No document number detected in the image.");
        }

        String modelType = extraction.type() == null ? "" : extraction.type().trim().toUpperCase();
        boolean modelKnowsType = modelType.equals("PAN") || modelType.equals("AADHAAR");

        DocumentType effectiveType;
        if (hintType != null) {
            effectiveType = hintType;
        } else if (modelKnowsType) {
            effectiveType = DocumentType.valueOf(modelType);
        } else {
            throw new ExtractionException(ExtractionException.Status.NO_DOCUMENT,
                    "Could not determine document type from the image; provide a hint.");
        }

        boolean typeMismatch = hintType != null && modelKnowsType
                && hintType != DocumentType.valueOf(modelType);

        ValidationResult result = DocumentValidator.validate(effectiveType, value);

        return new ExtractionOutcome(result, modelType, hintType, effectiveType,
                typeMismatch, value, extraction.confidence());
    }

    /**
     * Light sanitisation of the raw model string: trim, drop surrounding quotes,
     * and collapse internal whitespace runs to single spaces. It deliberately
     * keeps internal spaces (e.g. a grouped {@code 2345 6789 0124}) because the
     * deterministic validators strip spaces/hyphens during their own
     * normalisation. Further upper-casing and punctuation handling happens there.
     */
    static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        if (s.length() >= 2) {
            char first = s.charAt(0);
            char last = s.charAt(s.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                s = s.substring(1, s.length() - 1).trim();
            }
        }
        return s.replaceAll("\\s+", " ").trim();
    }
}
