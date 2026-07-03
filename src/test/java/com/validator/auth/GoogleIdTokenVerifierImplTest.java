package com.validator.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class GoogleIdTokenVerifierImplTest {

    // No live Google calls: null/blank/malformed tokens fail before any
    // certificate fetch, so this is hermetic and CI-safe.
    private final GoogleIdTokenVerifierImpl verifier =
            new GoogleIdTokenVerifierImpl("test-client-id.apps.googleusercontent.com");

    @Test
    void nullTokenRejected() {
        assertThrows(SessionException.class, () -> verifier.verify(null));
    }

    @Test
    void blankTokenRejected() {
        assertThrows(SessionException.class, () -> verifier.verify("   "));
    }

    @Test
    void malformedTokenRejected() {
        assertThrows(SessionException.class, () -> verifier.verify("not-a-jwt"));
    }
}
