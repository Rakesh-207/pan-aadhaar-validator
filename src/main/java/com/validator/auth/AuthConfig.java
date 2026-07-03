package com.validator.auth;

import java.util.function.Function;

/**
 * Auth/session configuration derived from environment variables.
 *
 * <p>This is a pure config holder: it knows the session secret, TTL, Google
 * client id, and cookie flags, but it does <em>not</em> construct the token
 * verifier (the server builds that from {@link #googleClientId()}). This keeps
 * config wiring decoupled from the Google library so tests can exercise config
 * failure paths without the verifier class on the classpath.
 *
 * <h2>Dev bypass</h2>
 * When {@code DEV_BYPASS_AUTH=true}, Google verification is skipped and a
 * fixed dev-only login endpoint mints a real signed session cookie. This mode
 * is <strong>refused</strong> if {@code FLY_APP_NAME} is set, so it can never
 * reach production. It exists purely to make local manual testing painless.
 */
public final class AuthConfig {

    public static final String COOKIE_NAME = "session";
    public static final long DEFAULT_TTL_SECONDS = 28800L; // 8 hours

    private final String sessionSecret;
    private final long ttlSeconds;
    private final String googleClientId;
    private final boolean secureCookie;
    private final boolean devBypass;

    public AuthConfig(String sessionSecret, long ttlSeconds, String googleClientId,
                      boolean secureCookie, boolean devBypass) {
        this.sessionSecret = sessionSecret;
        this.ttlSeconds = ttlSeconds;
        this.googleClientId = googleClientId;
        this.secureCookie = secureCookie;
        this.devBypass = devBypass;
    }

    public String sessionSecret() {
        return sessionSecret;
    }

    public byte[] secretBytes() {
        return sessionSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    public long ttlSeconds() {
        return ttlSeconds;
    }

    public String googleClientId() {
        return googleClientId;
    }

    public boolean secureCookie() {
        return secureCookie;
    }

    public boolean devBypass() {
        return devBypass;
    }

    public String cookieName() {
        return COOKIE_NAME;
    }

    // ---- Factory --------------------------------------------------------

    public static AuthConfig fromEnv() {
        return fromEnv(System::getenv);
    }

    public static AuthConfig fromEnv(Function<String, String> env) {
        boolean devBypass = "true".equalsIgnoreCase(env.apply("DEV_BYPASS_AUTH"));
        String flyApp = env.apply("FLY_APP_NAME");
        if (devBypass && flyApp != null && !flyApp.isBlank()) {
            throw new IllegalStateException(
                    "DEV_BYPASS_AUTH=true is refused on Fly (FLY_APP_NAME=" + flyApp
                    + "). Unset DEV_BYPASS_AUTH before deploying.");
        }
        long ttl = parseLong(env.apply("SESSION_TTL_SECONDS"), DEFAULT_TTL_SECONDS);
        boolean secure = parseBool(env.apply("COOKIE_SECURE"), true);
        String secret = env.apply("SESSION_SECRET");
        String clientId = env.apply("GOOGLE_CLIENT_ID");

        if (devBypass) {
            String s = (secret == null || secret.isBlank())
                    ? "dev-only-secret-not-for-production-use" : secret;
            return new AuthConfig(s, ttl, null, secure, true);
        }

        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException(missing("GOOGLE_CLIENT_ID"));
        }
        if (secret == null || secret.isBlank() || secret.length() < 16) {
            throw new IllegalStateException(
                    "SESSION_SECRET is missing or too short (need at least 16 characters). "
                    + "Set a strong value via environment variable / `fly secrets set SESSION_SECRET=...`.");
        }
        return new AuthConfig(secret, ttl, clientId, secure, false);
    }

    private static String missing(String var) {
        return "Missing required auth configuration: " + var + ". "
                + "For local development without Google, set DEV_BYPASS_AUTH=true "
                + "(and COOKIE_SECURE=false for http://localhost). "
                + "For production, set GOOGLE_CLIENT_ID and SESSION_SECRET.";
    }

    private static long parseLong(String v, long def) {
        if (v == null || v.isBlank()) {
            return def;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static boolean parseBool(String v, boolean def) {
        if (v == null || v.isBlank()) {
            return def;
        }
        return "true".equalsIgnoreCase(v.trim());
    }
}
