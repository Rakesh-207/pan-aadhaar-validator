package com.validator.auth;

import com.sun.net.httpserver.HttpExchange;

import java.time.Instant;
import java.util.Optional;
import java.util.function.LongSupplier;

/**
 * Authenticates requests by verifying the {@code session} cookie.
 *
 * <p>Reads the {@code Cookie} header, extracts the configured cookie name,
 * and runs it through {@link SessionToken#verify}. Any failure (missing,
 * tampered, expired) collapses to {@link Optional#empty()} so handlers can
 * uniformly translate "unauthenticated" into 401 / redirect. The clock is
 * injectable for deterministic expiry tests.
 */
public final class CookieSessionAuthGate implements AuthGate {

    private final AuthConfig cfg;
    private final LongSupplier clock;

    public CookieSessionAuthGate(AuthConfig cfg) {
        this(cfg, () -> Instant.now().getEpochSecond());
    }

    CookieSessionAuthGate(AuthConfig cfg, LongSupplier clock) {
        this.cfg = cfg;
        this.clock = clock;
    }

    @Override
    public Optional<SessionUser> authenticate(HttpExchange ex) {
        String header = ex.getRequestHeaders().getFirst("Cookie");
        return authenticateHeader(header);
    }

    Optional<SessionUser> authenticateHeader(String cookieHeader) {
        String token = cookieValue(cookieHeader, cfg.cookieName());
        if (token == null || token.isEmpty()) {
            return Optional.empty();
        }
        try {
            SessionClaims claims = SessionToken.verify(token, cfg.secretBytes(), clock.getAsLong());
            return Optional.of(new SessionUser(claims.sub(), claims.email(), claims.name(), claims.picture()));
        } catch (SessionException e) {
            return Optional.empty();
        }
    }

    static String cookieValue(String header, String name) {
        if (header == null || header.isBlank()) {
            return null;
        }
        for (String pair : header.split(";")) {
            String p = pair.trim();
            int eq = p.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String k = p.substring(0, eq).trim();
            if (k.equals(name)) {
                return p.substring(eq + 1).trim();
            }
        }
        return null;
    }
}
