package com.validator.server;

import com.validator.auth.AuthConfig;
import com.validator.auth.SessionClaims;
import com.validator.auth.SessionToken;
import com.validator.auth.StubIdTokenVerifier;
import com.validator.auth.VerifiedUser;
import com.validator.vision.Extraction;
import com.validator.vision.ExtractionException;
import com.validator.vision.VisionExtractor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the auth/session/access-control layer. Uses a stub
 * {@link VisionExtractor} and {@link StubIdTokenVerifier} so no live Google or
 * cloud call is ever made. Expiry is tested by minting a validly-signed token
 * with a past {@code exp} directly from the known test secret.
 */
class AuthRoutesTest {

    private static final String SECRET = "test-session-secret-1234";
    private static final String MAGIC_TOKEN = "magic-google-id-token";

    private ValidationServer server;
    private HttpClient client;
    private String base;

    @BeforeEach
    void setUp() throws Exception {
        StubExtractor stub = new StubExtractor(new Extraction("PAN", "AFZPK7190K", "HIGH"));
        AuthConfig auth = new AuthConfig(SECRET, 28800L, "test-client-id", false, false);
        StubIdTokenVerifier verifier = new StubIdTokenVerifier(MAGIC_TOKEN,
                new VerifiedUser("sub-1", "rakesh@example.com", "Rakesh", "https://img/p.png", true));
        server = ValidationServer.start(0, stub, auth, verifier);
        base = "http://localhost:" + server.getPort();
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void authConfigEndpointReportsMode() throws Exception {
        HttpResponse<String> res = get("/api/auth/config");
        assertEquals(200, res.statusCode());
        assertEquals("google", extract(res.body(), "\"mode\":\"", "\""));
        assertEquals("test-client-id", extract(res.body(), "\"googleClientId\":\"", "\""));
    }

    @Test
    void unauthenticatedAppRedirectsToLanding() throws Exception {
        HttpResponse<String> res = client.send(req("/app").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(302, res.statusCode());
        assertEquals("/", res.headers().firstValue("Location").orElse(""));
    }

    @Test
    void appWithTrailingSlashAlsoRedirects() throws Exception {
        HttpResponse<String> res = client.send(req("/app/").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(302, res.statusCode());
    }

    @Test
    void appHtmlCannotBeFetchedDirectly() throws Exception {
        HttpResponse<String> res = client.send(req("/app.html").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(404, res.statusCode());
    }

    @Test
    void unauthenticatedValidateReturns401() throws Exception {
        HttpResponse<String> res = postJson("/api/validate",
                "{\"type\":\"pan\",\"value\":\"AFZPK7190K\"}", null, null);
        assertEquals(401, res.statusCode());
        assertTrue(res.body().contains("unauthenticated"));
    }

    @Test
    void unauthenticatedMeReturns401() throws Exception {
        assertEquals(401, get("/api/auth/me").statusCode());
    }

    @Test
    void googleLoginWithoutOriginIs403() throws Exception {
        HttpResponse<String> res = postJson("/api/auth/google",
                "{\"credential\":\"" + MAGIC_TOKEN + "\"}", null, null);
        assertEquals(403, res.statusCode());
    }

    @Test
    void googleLoginWithStubTokenIssuesSession() throws Exception {
        HttpResponse<String> res = postJson("/api/auth/google",
                "{\"credential\":\"" + MAGIC_TOKEN + "\"}", null, origin());
        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("\"ok\":true"));
        String cookie = res.headers().firstValue("Set-Cookie").orElse("");
        assertTrue(cookie.startsWith("session="));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("SameSite=Lax"));
        assertTrue(cookie.contains("Path=/"));
        assertFalse(cookie.contains("Secure"), "secure flag off in this test config");
    }

    @Test
    void fullFlowLoginMeValidateApp() throws Exception {
        String cookie = login();

        HttpResponse<String> me = get("/api/auth/me", cookie);
        assertEquals(200, me.statusCode());
        assertEquals("sub-1", extract(me.body(), "\"sub\":\"", "\""));
        assertEquals("rakesh@example.com", extract(me.body(), "\"email\":\"", "\""));

        HttpResponse<String> validate = postJson("/api/validate",
                "{\"type\":\"pan\",\"value\":\"AFZPK7190K\"}", cookie, null);
        assertEquals(200, validate.statusCode());
        assertTrue(validate.body().contains("\"valid\":true"));

        HttpResponse<String> app = client.send(req("/app").header("Cookie", cookie).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, app.statusCode());
        assertTrue(app.body().contains("<html"));
    }

    @Test
    void tamperedCookieRejected() throws Exception {
        String cookie = login();
        String tampered = "x" + cookie.substring(1); // flip first char of token
        HttpResponse<String> res = postJson("/api/validate",
                "{\"type\":\"pan\",\"value\":\"AFZPK7190K\"}", tampered, null);
        assertEquals(401, res.statusCode());
    }

    @Test
    void expiredCookieRejected() throws Exception {
        // Mint a validly-signed token whose exp is in 1970 — gate rejects it.
        String token = SessionToken.sign(
                new SessionClaims("sub-1", "e", "n", "", 1L),
                SECRET.getBytes(StandardCharsets.UTF_8));
        HttpResponse<String> res = postJson("/api/validate",
                "{\"type\":\"pan\",\"value\":\"AFZPK7190K\"}", "session=" + token, null);
        assertEquals(401, res.statusCode());
    }

    @Test
    void logoutClearsCookie() throws Exception {
        HttpResponse<String> res = postJson("/api/auth/logout", "", null, origin());
        assertEquals(200, res.statusCode());
        String setCookie = res.headers().firstValue("Set-Cookie").orElse("");
        assertTrue(setCookie.contains("Max-Age=0"), "logout must clear the cookie");
        assertTrue(setCookie.contains("session="));
    }

    @Test
    void devLoginRefusedWhenNotInBypass() throws Exception {
        // Production shape: DEV_BYPASS_AUTH is off, so the guest/dev login
        // endpoint must be unreachable (404), never mint a session.
        HttpResponse<String> res = postJson("/api/auth/dev-login", "", null, origin());
        assertEquals(404, res.statusCode());
        assertFalse(res.headers().firstValue("Set-Cookie").isPresent(),
                "no session cookie must be issued when dev-bypass is off");
    }

    @Test
    void devLoginAllowedInBypassMode() throws Exception {
        // Local-only shape: DEV_BYPASS_AUTH=true. A fresh server is booted in
        // bypass mode; the guest login must succeed and mint a session cookie.
        AuthConfig bypass = new AuthConfig(SECRET, 28800L, null, false, true);
        ValidationServer bypassServer = ValidationServer.start(0,
                new StubExtractor(new Extraction("PAN", "AFZPK7190K", "HIGH")), bypass, null);
        try {
            String b = "http://localhost:" + bypassServer.getPort();
            HttpRequest post = HttpRequest.newBuilder().uri(URI.create(b + "/api/auth/dev-login"))
                    .header("Content-Type", "application/json")
                    .header("Origin", b)
                    .POST(HttpRequest.BodyPublishers.ofString("")).build();
            HttpResponse<String> res = client.send(post, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, res.statusCode());
            assertTrue(res.headers().firstValue("Set-Cookie").orElse("").startsWith("session="));
        } finally {
            bypassServer.stop();
        }
    }

    @Test
    void googleLoginWithBadTokenReturns401() throws Exception {
        HttpResponse<String> res = postJson("/api/auth/google",
                "{\"credential\":\"wrong-token\"}", null, origin());
        assertEquals(401, res.statusCode());
    }

    // ---- helpers -------------------------------------------------------

    private String login() throws Exception {
        HttpResponse<String> res = postJson("/api/auth/google",
                "{\"credential\":\"" + MAGIC_TOKEN + "\"}", null, origin());
        assertEquals(200, res.statusCode());
        return extractCookie(res.headers().firstValue("Set-Cookie").orElse(""));
    }

    private static String extractCookie(String setCookie) {
        int semi = setCookie.indexOf(';');
        return semi >= 0 ? setCookie.substring(0, semi) : setCookie;
    }

    private String origin() {
        return base;
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(req(path).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String cookie) throws Exception {
        return client.send(req(path).header("Cookie", cookie).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postJson(String path, String body, String cookie, String origin) throws Exception {
        HttpRequest.Builder b = req(path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (cookie != null) {
            b.header("Cookie", cookie);
        }
        if (origin != null) {
            b.header("Origin", origin);
        }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder req(String path) {
        return HttpRequest.newBuilder().uri(URI.create(base + path));
    }

    private static String extract(String json, String open, String close) {
        int s = json.indexOf(open);
        if (s < 0) {
            return "";
        }
        int e = json.indexOf(close, s + open.length());
        return e < 0 ? "" : json.substring(s + open.length(), e);
    }

    static final class StubExtractor implements VisionExtractor {
        private final Extraction result;

        StubExtractor(Extraction result) {
            this.result = result;
        }

        @Override
        public Extraction extract(byte[] image, String mime) throws ExtractionException {
            return result;
        }
    }
}
