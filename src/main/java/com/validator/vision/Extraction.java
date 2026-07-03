package com.validator.vision;

/**
 * Raw extraction result produced by a vision model: the predicted document type
 * (one of {@code PAN}, {@code AADHAAR}, {@code UNKNOWN}), the extracted value
 * string (as printed), and the model's self-reported confidence.
 *
 * <p>This is <em>advisory</em> only. The deterministic Java validator is the
 * source of truth; an {@code Extraction} is never trusted without validation.
 */
public record Extraction(String type, String value, String confidence) {
}
