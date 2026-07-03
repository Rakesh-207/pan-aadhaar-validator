package com.validator.auth;

/**
 * Verifies a Google ID token (a signed JWT issued by Google) and returns the
 * embedded identity claims.
 *
 * <p>The production implementation wraps Google's {@code GoogleIdTokenVerifier}
 * (signature, audience, issuer, expiry). Tests supply a stub so no live Google
 * call is ever made from the test suite.
 */
public interface IdTokenVerifier {

    VerifiedUser verify(String idToken) throws SessionException;
}
