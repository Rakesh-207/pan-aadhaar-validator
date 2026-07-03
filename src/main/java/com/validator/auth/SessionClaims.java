package com.validator.auth;

import com.validator.json.Json;
import com.validator.json.JsonReader;

import java.util.Map;

/**
 * Minimal signed claims carried inside the stateless session cookie.
 *
 * <p>{@code exp} is Unix epoch seconds. It is serialized as a JSON string so
 * the payload round-trips through the existing string-only {@link JsonReader}.
 * {@code sub} is Google's stable account id (never use email as the key).
 */
public record SessionClaims(String sub, String email, String name, String picture, long exp) {

    public String toJson() {
        return "{"
                + "\"sub\":\"" + Json.escape(nullToEmpty(sub)) + "\""
                + ",\"email\":\"" + Json.escape(nullToEmpty(email)) + "\""
                + ",\"name\":\"" + Json.escape(nullToEmpty(name)) + "\""
                + ",\"picture\":\"" + Json.escape(nullToEmpty(picture)) + "\""
                + ",\"exp\":\"" + exp + "\""
                + "}";
    }

    public static SessionClaims fromJson(String json) {
        Map<String, String> m;
        try {
            m = JsonReader.readObject(json);
        } catch (IllegalArgumentException e) {
            throw new SessionException("Malformed session payload");
        }
        long exp;
        try {
            exp = Long.parseLong(m.getOrDefault("exp", "0"));
        } catch (NumberFormatException e) {
            throw new SessionException("Malformed session exp");
        }
        return new SessionClaims(
                m.getOrDefault("sub", ""),
                m.getOrDefault("email", ""),
                m.getOrDefault("name", ""),
                m.getOrDefault("picture", ""),
                exp);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
