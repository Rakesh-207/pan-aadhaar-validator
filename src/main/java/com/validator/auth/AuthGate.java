package com.validator.auth;

import com.sun.net.httpserver.HttpExchange;

import java.util.Optional;

/**
 * Decides whether an incoming request is authenticated and, if so, by whom.
 *
 * <p>Two implementations exist:
 * <ul>
 *   <li>{@link #open()} — always authenticated ({@link SessionUser#OPEN}).
 *       Used by the legacy test server seams so the existing validation tests
 *       keep passing unchanged.</li>
 *   <li>{@link #cookie(AuthConfig)} — verifies the signed session cookie.
 *       Used by production and the auth integration tests.</li>
 * </ul>
 */
public interface AuthGate {

    Optional<SessionUser> authenticate(HttpExchange ex);

    static AuthGate open() {
        return ex -> Optional.of(SessionUser.OPEN);
    }

    static AuthGate cookie(AuthConfig cfg) {
        return new CookieSessionAuthGate(cfg);
    }
}
