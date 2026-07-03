package com.validator.auth;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * Signs and verifies a compact, stateless session token.
 *
 * <p>Wire format: {@code <payloadB64>.<hmacB64>} where {@code payloadB64} is
 * base64url (unpadded) of the UTF-8 JSON claims, and {@code hmacB64} is
 * base64url (unpadded) of HMAC-SHA256(secret, payloadB64-as-ASCII).
 *
 * <p>Verification recomputes the HMAC over the received payloadB64 and compares
 * it to the received tag using {@link MessageDigest#isEqual} (constant-time).
 * Only after the tag checks out is the payload decoded and the {@code exp}
 * enforced. Any structural, signature, or expiry failure throws
 * {@link SessionException}; callers translate that into a 401/redirect.
 */
public final class SessionToken {

    /** Default clock leeway (seconds) tolerated on {@code exp} checks. */
    public static final long DEFAULT_LEEWAY_SECONDS = 60L;

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private SessionToken() {}

    public static String sign(SessionClaims claims, byte[] secret) {
        String payloadJson = claims.toJson();
        String payloadB64 = B64.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        String tagB64 = B64.encodeToString(hmacSha256(secret, payloadB64.getBytes(StandardCharsets.US_ASCII)));
        return payloadB64 + "." + tagB64;
    }

    public static SessionClaims verify(String token, byte[] secret, long nowSeconds) {
        return verify(token, secret, nowSeconds, DEFAULT_LEEWAY_SECONDS);
    }

    public static SessionClaims verify(String token, byte[] secret, long nowSeconds, long leewaySeconds) {
        if (token == null || token.isEmpty()) {
            throw new SessionException("Missing session token");
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            throw new SessionException("Malformed session token");
        }
        String payloadB64 = token.substring(0, dot);
        String tagB64 = token.substring(dot + 1);

        byte[] expectedTag = hmacSha256(secret, payloadB64.getBytes(StandardCharsets.US_ASCII));
        byte[] suppliedTag;
        try {
            suppliedTag = B64D.decode(tagB64);
        } catch (IllegalArgumentException e) {
            throw new SessionException("Malformed session tag");
        }
        if (!MessageDigest.isEqual(expectedTag, suppliedTag)) {
            throw new SessionException("Invalid session signature");
        }

        String payloadJson;
        try {
            payloadJson = new String(B64D.decode(payloadB64), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new SessionException("Malformed session payload");
        }
        SessionClaims claims = SessionClaims.fromJson(payloadJson);
        if (claims.exp() + leewaySeconds < nowSeconds) {
            throw new SessionException("Expired session");
        }
        return claims;
    }

    private static byte[] hmacSha256(byte[] secret, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }
}
