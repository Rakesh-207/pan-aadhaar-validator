package com.validator.auth;

/**
 * Identity claims extracted from a verified Google ID token.
 *
 * <p>{@code sub} is Google's stable account identifier and is the only value
 * suitable as a user key. Email is informational only (users can change it).
 */
public record VerifiedUser(String sub, String email, String name, String picture, boolean emailVerified) {
}
