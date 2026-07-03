package com.validator.vision;

/**
 * Raised when image extraction cannot produce a usable, validatable value.
 * Carries a {@link Status} so the HTTP layer can map it to a precise status code.
 */
public final class ExtractionException extends Exception {

    public enum Status {
        /** The model responded, but no document/value could be identified. */
        NO_DOCUMENT,
        /** The model response was present but could not be parsed as strict JSON. */
        UNPARSEABLE,
        /** The upstream router/model returned an error or non-2xx status. */
        UPSTREAM_ERROR,
        /** The upstream router rejected the request for exceeding rate limits. */
        RATE_LIMITED,
        /** The upstream call did not complete within the configured timeout. */
        TIMEOUT
    }

    private final Status status;

    public ExtractionException(Status status, String message) {
        super(message);
        this.status = status;
    }

    public ExtractionException(Status status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public Status status() {
        return status;
    }
}
