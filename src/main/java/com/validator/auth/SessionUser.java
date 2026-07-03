package com.validator.auth;

/**
 * Runtime identity resolved from a verified session, suitable for display
 * (signed-in chip) and for request attribution. {@code sub} is the stable key.
 */
public record SessionUser(String sub, String email, String name, String picture) {

    /** Sentinel returned by the open (no-auth) test seam. */
    public static final SessionUser OPEN = new SessionUser("test", "test@local", "Test", "");
}
