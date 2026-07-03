package com.validator.auth;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionTokenTest {

    private static final byte[] SECRET = "supersecret-session-key-1234".getBytes(StandardCharsets.UTF_8);
    private static final byte[] OTHER = "a-completely-different-key-9999".getBytes(StandardCharsets.UTF_8);

    private static SessionClaims claims(long exp) {
        return new SessionClaims("sub-123", "rakesh@example.com", "Rakesh", "https://img/p.png", exp);
    }

    @Test
    void signThenVerifyRoundtripsClaims() {
        String token = SessionToken.sign(claims(9_999_999_999L), SECRET);
        SessionClaims out = assertDoesNotThrow(() -> SessionToken.verify(token, SECRET, 1_000L));
        assertEquals("sub-123", out.sub());
        assertEquals("rakesh@example.com", out.email());
        assertEquals("Rakesh", out.name());
        assertEquals("https://img/p.png", out.picture());
        assertEquals(9_999_999_999L, out.exp());
    }

    @Test
    void tamperedPayloadRejected() {
        String token = SessionToken.sign(claims(9_999_999_999L), SECRET);
        int dot = token.indexOf('.');
        // Flip a character in the payload base64 portion (changes claims + breaks tag).
        char flipped = token.charAt(0) == 'A' ? 'B' : 'A';
        String tampered = flipped + token.substring(1, dot) + token.substring(dot);
        assertThrows(SessionException.class, () -> SessionToken.verify(tampered, SECRET, 1_000L));
    }

    @Test
    void tamperedTagRejected() {
        String token = SessionToken.sign(claims(9_999_999_999L), SECRET);
        int dot = token.indexOf('.');
        char flipped = token.charAt(dot + 1) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, dot + 1) + flipped + token.substring(dot + 2);
        assertThrows(SessionException.class, () -> SessionToken.verify(tampered, SECRET, 1_000L));
    }

    @Test
    void expiredRejectedBeyondLeeway() {
        // exp = 5000; now = 5000 + leeway + 1 -> expired.
        long exp = 5000L;
        long now = exp + SessionToken.DEFAULT_LEEWAY_SECONDS + 1;
        String token = SessionToken.sign(claims(exp), SECRET);
        assertThrows(SessionException.class, () -> SessionToken.verify(token, SECRET, now));
    }

    @Test
    void expWithinLeewayStillValid() {
        long exp = 5000L;
        long now = exp + SessionToken.DEFAULT_LEEWAY_SECONDS; // exactly at boundary -> still valid
        String token = SessionToken.sign(claims(exp), SECRET);
        assertDoesNotThrow(() -> SessionToken.verify(token, SECRET, now));
    }

    @Test
    void wrongSecretRejected() {
        String token = SessionToken.sign(claims(9_999_999_999L), SECRET);
        assertThrows(SessionException.class, () -> SessionToken.verify(token, OTHER, 1_000L));
    }

    @Test
    void malformedTokenRejected() {
        assertThrows(SessionException.class, () -> SessionToken.verify("no-dot-here", SECRET, 1_000L));
        assertThrows(SessionException.class, () -> SessionToken.verify("", SECRET, 1_000L));
        // tag present but empty payload
        assertThrows(SessionException.class, () -> SessionToken.verify(".abc", SECRET, 1_000L));
        // payload present but empty tag
        assertThrows(SessionException.class, () -> SessionToken.verify("abc.", SECRET, 1_000L));
    }

    @Test
    void nullTokenRejected() {
        assertThrows(SessionException.class, () -> SessionToken.verify(null, SECRET, 1_000L));
    }
}
