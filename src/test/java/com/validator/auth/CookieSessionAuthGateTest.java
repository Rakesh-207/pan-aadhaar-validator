package com.validator.auth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CookieSessionAuthGateTest {

    private static final byte[] SECRET = "supersecret-session-key-1234".getBytes(StandardCharsets.UTF_8);

    private static SessionClaims claims(long exp) {
        return new SessionClaims("sub-1", "rakesh@example.com", "Rakesh", "https://img/p.png", exp);
    }

    private static CookieSessionAuthGate gateWithFixedNow(long now) {
        AuthConfig cfg = new AuthConfig(new String(SECRET, StandardCharsets.UTF_8),
                28800L, "client-id", false, false);
        LongSupplier clock = () -> now;
        return new CookieSessionAuthGate(cfg, clock);
    }

    @Test
    void validCookieAuthenticates() {
        CookieSessionAuthGate gate = gateWithFixedNow(1_000L);
        String token = SessionToken.sign(claims(9_999_999_999L), SECRET);
        Optional<SessionUser> user = gate.authenticateHeader("session=" + token);
        assertTrue(user.isPresent());
        assertEquals("sub-1", user.get().sub());
        assertEquals("rakesh@example.com", user.get().email());
        assertEquals("Rakesh", user.get().name());
    }

    @Test
    void cookieAmongOtherCookiesAuthenticates() {
        CookieSessionAuthGate gate = gateWithFixedNow(1_000L);
        String token = SessionToken.sign(claims(9_999_999_999L), SECRET);
        Optional<SessionUser> user = gate.authenticateHeader("theme=dark; session=" + token + "; lang=en");
        assertTrue(user.isPresent());
    }

    @Test
    void noCookieHeaderIsEmpty() {
        CookieSessionAuthGate gate = gateWithFixedNow(1_000L);
        assertTrue(gate.authenticateHeader(null).isEmpty());
        assertTrue(gate.authenticateHeader("").isEmpty());
    }

    @Test
    void missingSessionNameIsEmpty() {
        CookieSessionAuthGate gate = gateWithFixedNow(1_000L);
        assertTrue(gate.authenticateHeader("other=value").isEmpty());
    }

    @Test
    void tamperedCookieIsEmpty() {
        CookieSessionAuthGate gate = gateWithFixedNow(1_000L);
        String token = SessionToken.sign(claims(9_999_999_999L), SECRET);
        String tampered = "x" + token.substring(1);
        assertTrue(gate.authenticateHeader("session=" + tampered).isEmpty());
    }

    @Test
    void expiredCookieIsEmpty() {
        long exp = 5000L;
        long now = exp + SessionToken.DEFAULT_LEEWAY_SECONDS + 1;
        CookieSessionAuthGate gate = gateWithFixedNow(now);
        String token = SessionToken.sign(claims(exp), SECRET);
        assertTrue(gate.authenticateHeader("session=" + token).isEmpty());
    }

    @Test
    void cookieValueParserHandlesEdgeCases() {
        // whitespace and equals-in-value tolerant
        assertEquals("a=b", CookieSessionAuthGate.cookieValue("  session=a=b ", "session"));
        assertEquals(null, CookieSessionAuthGate.cookieValue(null, "session"));
        assertEquals(null, CookieSessionAuthGate.cookieValue("novalue", "session"));
        assertEquals("", CookieSessionAuthGate.cookieValue("session=", "session"));
    }
}
