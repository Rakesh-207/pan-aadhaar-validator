package com.validator.json;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal, tightly-scoped JSON reader: parses a flat JSON object whose values are
 * JSON strings only (e.g. {@code {"type":"pan","value":"AFZPK7190K"}}).
 * <p>
 * This is NOT a general-purpose JSON library. It exists so the HTTP API can accept
 * JSON request bodies without pulling in an external dependency (Jackson/Gson).
 * Unknown shapes (arrays, nested objects, numbers, booleans, null) are rejected.
 */
public final class JsonReader {

    private final String s;
    private int pos;

    private JsonReader(String s) {
        this.s = s;
        this.pos = 0;
    }

    private JsonReader parseObject(Map<String, String> out) {
        skipWs();
        expect('{');
        skipWs();
        if (peek() == '}') {
            pos++;
            return this;
        }
        while (true) {
            skipWs();
            String key = readString();
            skipWs();
            expect(':');
            skipWs();
            String value = readString();
            out.put(key, value);
            skipWs();
            char c = next();
            if (c == ',') {
                continue;
            }
            if (c == '}') {
                return this;
            }
            throw new IllegalArgumentException(
                    "Expected ',' or '}' at position " + (pos - 1));
        }
    }

    private String readString() {
        skipWs();
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= s.length()) {
                throw new IllegalArgumentException("Unterminated string");
            }
            char c = s.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\') {
                sb.append(readEscape());
            } else if (c < 0x20) {
                throw new IllegalArgumentException("Unescaped control character in string");
            } else {
                sb.append(c);
            }
        }
    }

    private char readEscape() {
        if (pos >= s.length()) {
            throw new IllegalArgumentException("Trailing backslash");
        }
        char c = s.charAt(pos++);
        return switch (c) {
            case '"' -> '"';
            case '\\' -> '\\';
            case '/' -> '/';
            case 'b' -> '\b';
            case 'f' -> '\f';
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case 'u' -> readUnicodeEscape();
            default -> throw new IllegalArgumentException("Invalid escape '\\" + c + "'");
        };
    }

    private char readUnicodeEscape() {
        if (pos + 4 > s.length()) {
            throw new IllegalArgumentException("Incomplete \\u escape");
        }
        String hex = s.substring(pos, pos + 4);
        pos += 4;
        try {
            return (char) Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid \\u escape: " + hex);
        }
    }

    private void skipWs() {
        while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
            pos++;
        }
    }

    private char peek() {
        if (pos >= s.length()) {
            throw new IllegalArgumentException("Unexpected end of input");
        }
        return s.charAt(pos);
    }

    private char next() {
        if (pos >= s.length()) {
            throw new IllegalArgumentException("Unexpected end of input");
        }
        return s.charAt(pos++);
    }

    private void expect(char expected) {
        char c = next();
        if (c != expected) {
            throw new IllegalArgumentException(
                    "Expected '" + expected + "' but found '" + c + "' at position " + (pos - 1));
        }
    }

    /**
     * Parse a flat JSON object of string-to-string mappings.
     *
     * @param json the JSON text (must be a single object, e.g. {@code {"a":"b"}})
     * @return an insertion-ordered map of the parsed fields
     * @throws IllegalArgumentException if the input is not a valid flat object with string values
     */
    public static Map<String, String> readObject(String json) {
        if (json == null) {
            throw new IllegalArgumentException("Null JSON body");
        }
        Map<String, String> out = new LinkedHashMap<>();
        JsonReader r = new JsonReader(json);
        r.parseObject(out);
        r.skipWs();
        if (r.pos < r.s.length()) {
            throw new IllegalArgumentException(
                    "Trailing content after JSON object at position " + r.pos);
        }
        return out;
    }
}
