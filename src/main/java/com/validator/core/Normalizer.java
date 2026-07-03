package com.validator.core;

public final class Normalizer {

    private Normalizer() {}

    public static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    public static String normalizePan(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ' ' || c == '-') {
                continue;
            }
            if (c >= 'A' && c <= 'Z') {
                sb.append(c);
            } else if (c >= 'a' && c <= 'z') {
                sb.append((char) (c - 32));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String normalizeAadhaar(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.trim();
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ' ' || c == '-') {
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    public static boolean isAsciiLetter(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    public static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }
}
