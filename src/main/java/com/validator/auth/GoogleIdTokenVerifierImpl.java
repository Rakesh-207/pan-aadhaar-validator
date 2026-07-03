package com.validator.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;

import java.util.Collections;

/**
 * Production {@link IdTokenVerifier} backed by Google's
 * {@code GoogleIdTokenVerifier}.
 *
 * <p>Google's library verifies the JWT signature against Google's rotating
 * public certificates (fetched and cached automatically), enforces that
 * {@code aud} equals our configured client id, that {@code iss} is Google, and
 * that {@code exp} is valid. We map {@code null}/exception results to
 * {@link SessionException} and never log the token itself.
 */
public final class GoogleIdTokenVerifierImpl implements IdTokenVerifier {

    private final GoogleIdTokenVerifier verifier;

    public GoogleIdTokenVerifierImpl(String googleClientId,
                                     HttpTransport transport,
                                     JsonFactory jsonFactory) {
        this.verifier = new GoogleIdTokenVerifier.Builder(transport, jsonFactory)
                .setAudience(Collections.singletonList(googleClientId))
                .build();
    }

    /** Convenient constructor with JDK NetHttpTransport + Gson. */
    public GoogleIdTokenVerifierImpl(String googleClientId) {
        this(googleClientId,
                new com.google.api.client.http.javanet.NetHttpTransport(),
                com.google.api.client.json.gson.GsonFactory.getDefaultInstance());
    }

    @Override
    public VerifiedUser verify(String idToken) throws SessionException {
        if (idToken == null || idToken.isBlank()) {
            throw new SessionException("Missing ID token");
        }
        GoogleIdToken token;
        try {
            token = verifier.verify(idToken);
        } catch (Exception e) {
            // Signature, audience, issuer, expiry, network, or parse failure.
            // Do not echo the token; the exception type is enough signal.
            throw new SessionException("ID token verification failed");
        }
        if (token == null) {
            throw new SessionException("Invalid ID token");
        }
        GoogleIdToken.Payload p = token.getPayload();
        String sub = p.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new SessionException("ID token has no subject");
        }
        String email = p.getEmail();
        boolean emailVerified = Boolean.TRUE.equals(p.getEmailVerified());
        String name = (String) p.get("name");
        String picture = (String) p.get("picture");
        return new VerifiedUser(sub, email, name, picture, emailVerified);
    }
}
