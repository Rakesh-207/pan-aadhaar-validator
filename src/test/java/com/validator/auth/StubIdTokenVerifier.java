package com.validator.auth;

/**
 * Test-only {@link IdTokenVerifier} that accepts exactly one preconfigured
 * token string and returns a fixed {@link VerifiedUser}. Lets auth-route
 * integration tests exercise the full cookie flow without ever calling Google.
 */
public final class StubIdTokenVerifier implements IdTokenVerifier {

    private final String acceptedToken;
    private final VerifiedUser user;

    public StubIdTokenVerifier(String acceptedToken, VerifiedUser user) {
        this.acceptedToken = acceptedToken;
        this.user = user;
    }

    @Override
    public VerifiedUser verify(String idToken) throws SessionException {
        if (acceptedToken.equals(idToken)) {
            return user;
        }
        throw new SessionException("stub: invalid ID token");
    }
}
