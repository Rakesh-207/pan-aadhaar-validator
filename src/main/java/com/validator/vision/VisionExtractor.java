package com.validator.vision;

/**
 * Boundary over an external vision model. Implementations receive raw image
 * bytes (held in memory only) and return an advisory {@link Extraction}.
 *
 * <p>Implementations must NOT persist the image bytes anywhere. The HTTP layer
 * is responsible for always feeding the returned value through the deterministic
 * Java validator before trusting it.
 */
public interface VisionExtractor {

    /**
     * Extract an ID candidate from the supplied in-memory image bytes.
     *
     * @param image the raw image bytes (PNG or JPEG), never persisted by the implementation
     * @param mime  the mime type of the image, e.g. {@code image/png}
     * @return an advisory extraction; never {@code null}
     * @throws ExtractionException if the model is unreachable, rate-limited, or returns
     *                             an unusable response
     */
    Extraction extract(byte[] image, String mime) throws ExtractionException;
}
