package com.validator.auth;

/**
 * Thrown when a session token or Google ID token is missing, malformed,
 * tampered, expired, or otherwise fails verification. Callers translate this
 * into an HTTP 401 / redirect, never into a stack trace shown to the user.
 */
public class SessionException extends RuntimeException {

    public SessionException(String message) {
        super(message);
    }
}
