package com.validator.auth;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthConfigFromEnvTest {

    private static java.util.function.Function<String, String> map(Map<String, String> m) {
        return m::get;
    }

    private static Map<String, String> base() {
        // secure defaults that won't leak into assertions
        Map<String, String> m = new HashMap<>();
        m.put("COOKIE_SECURE", "false");
        return m;
    }

    @Test
    void productionConfigRequiresGoogleClientIdAndSecret() {
        Map<String, String> env = base();
        env.put("GOOGLE_CLIENT_ID", "abc.apps.googleusercontent.com");
        env.put("SESSION_SECRET", "supersecret-session-key-1234");
        AuthConfig cfg = AuthConfig.fromEnv(map(env));
        assertFalse(cfg.devBypass());
        assertTrue(!cfg.secureCookie()); // explicitly false here
        assertEquals("abc.apps.googleusercontent.com", cfg.googleClientId());
        assertEquals("supersecret-session-key-1234", cfg.sessionSecret());
        assertEquals(AuthConfig.DEFAULT_TTL_SECONDS, cfg.ttlSeconds());
        assertEquals("session", cfg.cookieName());
    }

    @Test
    void missingGoogleClientIdThrowsClearMessage() {
        Map<String, String> env = base();
        // no client id, no secret, no bypass
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> AuthConfig.fromEnv(map(env)));
        assertTrue(e.getMessage().contains("GOOGLE_CLIENT_ID"));
        assertTrue(e.getMessage().contains("DEV_BYPASS_AUTH"));
    }

    @Test
    void shortSessionSecretIsRejected() {
        Map<String, String> env = base();
        env.put("GOOGLE_CLIENT_ID", "abc.apps.googleusercontent.com");
        env.put("SESSION_SECRET", "short");
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> AuthConfig.fromEnv(map(env)));
        assertTrue(e.getMessage().contains("SESSION_SECRET"));
    }

    @Test
    void devBypassAllowedWhenNotOnFly() {
        Map<String, String> env = base();
        env.put("DEV_BYPASS_AUTH", "true");
        // secret optional in bypass
        AuthConfig cfg = AuthConfig.fromEnv(map(env));
        assertTrue(cfg.devBypass());
        assertNotNull(cfg.sessionSecret());
        assertEquals(AuthConfig.DEFAULT_TTL_SECONDS, cfg.ttlSeconds());
    }

    @Test
    void devBypassRefusedOnFly() {
        Map<String, String> env = base();
        env.put("DEV_BYPASS_AUTH", "true");
        env.put("FLY_APP_NAME", "pan-aadhaar-validator");
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> AuthConfig.fromEnv(map(env)));
        assertTrue(e.getMessage().contains("refused on Fly"));
    }

    @Test
    void cookieSecureDefaultsToTrueWhenUnset() {
        Map<String, String> env = new HashMap<>();
        env.put("GOOGLE_CLIENT_ID", "abc.apps.googleusercontent.com");
        env.put("SESSION_SECRET", "supersecret-session-key-1234");
        AuthConfig cfg = AuthConfig.fromEnv(map(env));
        assertTrue(cfg.secureCookie());
    }

    @Test
    void ttlIsOverrideable() {
        Map<String, String> env = base();
        env.put("GOOGLE_CLIENT_ID", "abc.apps.googleusercontent.com");
        env.put("SESSION_SECRET", "supersecret-session-key-1234");
        env.put("SESSION_TTL_SECONDS", "3600");
        AuthConfig cfg = AuthConfig.fromEnv(map(env));
        assertEquals(3600L, cfg.ttlSeconds());
    }
}
