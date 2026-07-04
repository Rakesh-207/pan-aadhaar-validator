package com.validator.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.validator.auth.AuthConfig;
import com.validator.auth.AuthGate;
import com.validator.auth.GoogleIdTokenVerifierImpl;
import com.validator.auth.IdTokenVerifier;
import com.validator.auth.SessionClaims;
import com.validator.auth.SessionException;
import com.validator.auth.SessionToken;
import com.validator.auth.SessionUser;
import com.validator.auth.VerifiedUser;
import com.validator.core.DocumentValidator;
import com.validator.json.Json;
import com.validator.json.JsonReader;
import com.validator.model.DocumentType;
import com.validator.model.ValidationResult;
import com.validator.vision.CerebrasVisionExtractor;
import com.validator.vision.Extraction;
import com.validator.vision.ExtractionException;
import com.validator.vision.ExtractionOutcome;
import com.validator.vision.ExtractionService;
import com.validator.vision.VisionExtractor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;

public final class ValidationServer {

    private static final String SERVICE = "pan-aadhaar-validator";
    private static final String VERSION = "1.0.0";

    private final HttpServer server;

    private ValidationServer(HttpServer server) {
        this.server = server;
    }

    /**
     * Production entry point. Reads cloud-vision + auth configuration from the
     * environment. Throws {@link IllegalStateException} with a clear remediation
     * message if required auth config is missing (see {@link AuthConfig#fromEnv()}).
     */
    public static ValidationServer start(int port) throws IOException {
        boolean cloudEnabled = "true".equalsIgnoreCase(env("ENABLE_CLOUD_VISION", "false"));
        AuthConfig auth = AuthConfig.fromEnv();
        IdTokenVerifier verifier = auth.devBypass()
                ? null
                : new GoogleIdTokenVerifierImpl(auth.googleClientId());
        return buildServer(port, cloudEnabled, auth, verifier, AuthGate.cookie(auth), null);
    }

    /**
     * Test seam with an explicit cloud-vision toggle. Auth is open so the
     * existing cloud-disabled test is unaffected.
     */
    public static ValidationServer start(int port, boolean cloudEnabled) throws IOException {
        return buildServer(port, cloudEnabled, devDummyConfig(), null, AuthGate.open(), null);
    }

    /**
     * Test seam with a stub vision extractor. Auth is open so the existing
     * HTTP validation tests pass unchanged.
     */
    public static ValidationServer start(int port, VisionExtractor extractor) throws IOException {
        return buildServer(port, true, devDummyConfig(), null, AuthGate.open(), extractor);
    }

    /**
     * Auth integration-test seam: real cookie session gate with a test
     * {@link AuthConfig} (known secret) and a stub/real {@link IdTokenVerifier}.
     */
    public static ValidationServer start(int port, VisionExtractor extractor, AuthConfig auth,
                                         IdTokenVerifier verifier) throws IOException {
        return buildServer(port, true, auth, verifier, AuthGate.cookie(auth), extractor);
    }

    private static ValidationServer buildServer(int port, boolean cloudEnabled, AuthConfig auth,
                                                IdTokenVerifier verifier, AuthGate gate,
                                                VisionExtractor explicitExtractor) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/auth", new AuthHandler(auth, verifier, gate));
        server.createContext("/api/health", new HealthHandler());
        server.createContext("/api/validate", new AuthFilter(new ValidateHandler(), gate));

        HttpHandler extractHandler;
        if (explicitExtractor != null) {
            extractHandler = new ExtractHandler(explicitExtractor);
        } else if (cloudEnabled) {
            extractHandler = new ExtractHandler(buildDefaultExtractor());
        } else {
            extractHandler = new DisabledExtractHandler();
        }
        server.createContext("/api/extract-and-validate", new AuthFilter(extractHandler, gate));

        server.createContext("/app", new AppRouteHandler(gate));
        server.createContext("/", new StaticHandler());
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        return new ValidationServer(server);
    }

    /** Auth config used by the open (no-auth) test seams; the secret is never exercised. */
    private static AuthConfig devDummyConfig() {
        return new AuthConfig("dev-dummy-not-used", AuthConfig.DEFAULT_TTL_SECONDS, null, false, true);
    }

    private static VisionExtractor buildDefaultExtractor() {
        String endpoint = env("CEREBRAS_ROUTER_URL",
                "https://cerebras-router-worker.vallangirakesh.workers.dev/v1/chat/completions");
        String model = env("CEREBRAS_MODEL", "gemma-4-31b");
        String bearer = env("CEREBRAS_API_KEY", null);
        int connectTimeout = parseIntEnv("EXTRACT_CONNECT_TIMEOUT_SECONDS", 5);
        int readTimeout = parseIntEnv("EXTRACT_READ_TIMEOUT_SECONDS", 30);
        int maxTokens = parseIntEnv("EXTRACT_MAX_TOKENS", 120);
        return new CerebrasVisionExtractor(endpoint, model, bearer,
                Duration.ofSeconds(connectTimeout), Duration.ofSeconds(readTimeout), maxTokens);
    }

    private static String env(String name, String def) {
        String v = System.getenv(name);
        return (v == null || v.isBlank()) ? def : v;
    }

    private static int parseIntEnv(String name, int def) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) {
            return def;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public void stop() {
        server.stop(0);
    }

    /** The actual bound port (useful for ephemeral-port tests). */
    public int getPort() {
        return server.getAddress().getPort();
    }

    static final class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!ensureMethod(ex, "GET")) {
                return;
            }
            String body = "{\"status\":\"UP\",\"service\":\"" + SERVICE
                    + "\",\"version\":\"" + VERSION + "\"}";
            writeJson(ex, 200, body);
        }
    }

    static final class ValidateHandler implements HttpHandler {
        private static final int MAX_BODY = 8192;

        @Override
        public void handle(HttpExchange ex) throws IOException {
            String method = ex.getRequestMethod();
            if (method.equalsIgnoreCase("GET")) {
                Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());
                String type = q.getOrDefault("type", "").trim().toLowerCase();
                String value = q.getOrDefault("value", "");
                validateAndRespond(ex, type, value);
            } else if (method.equalsIgnoreCase("POST")) {
                handlePost(ex);
            } else {
                ex.getResponseHeaders().set("Allow", "GET, POST");
                writeJson(ex, 405, "{\"error\":\"Method not allowed. Use GET or POST.\"}");
            }
        }

        private void handlePost(HttpExchange ex) throws IOException {
            String ct = ex.getRequestHeaders().getFirst("Content-Type");
            if (ct == null || !ct.toLowerCase().contains("application/json")) {
                writeJson(ex, 415,
                        "{\"error\":\"Unsupported Media Type. POST requires Content-Type: application/json.\"}");
                return;
            }
            byte[] body;
            try (InputStream in = ex.getRequestBody()) {
                body = in.readNBytes(MAX_BODY + 1);
            }
            if (body.length > MAX_BODY) {
                writeJson(ex, 400, "{\"error\":\"Request body too large.\"}");
                return;
            }
            String text = new String(body, StandardCharsets.UTF_8);
            Map<String, String> json;
            try {
                json = JsonReader.readObject(text);
            } catch (IllegalArgumentException e) {
                writeJson(ex, 400, "{\"error\":\"Malformed JSON body.\"}");
                return;
            }
            String type = json.getOrDefault("type", "").trim().toLowerCase();
            String value = json.getOrDefault("value", "");
            validateAndRespond(ex, type, value);
        }
    }

    private static void validateAndRespond(HttpExchange ex, String type, String value)
            throws IOException {
        DocumentType dt;
        try {
            dt = DocumentType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            writeJson(ex, 400,
                    "{\"error\":\"Invalid or missing 'type'. Use type=pan or type=aadhaar.\"}");
            return;
        }
        ValidationResult result = DocumentValidator.validate(dt, value);
        writeJson(ex, 200, result.toJson());
    }

    static final class ExtractHandler implements HttpHandler {
        private static final int MAX_UPLOAD = 5 * 1024 * 1024;
        private static final Set<String> ALLOWED_MIME = Set.of("image/png", "image/jpeg");

        private final VisionExtractor extractor;

        ExtractHandler(VisionExtractor extractor) {
            this.extractor = extractor;
        }

        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!ensureMethod(ex, "POST")) {
                return;
            }
            String ct = ex.getRequestHeaders().getFirst("Content-Type");
            String mime = ct == null ? "" : ct.toLowerCase().trim();
            int semi = mime.indexOf(';');
            if (semi >= 0) {
                mime = mime.substring(0, semi).trim();
            }
            if (!ALLOWED_MIME.contains(mime)) {
                writeJson(ex, 415,
                        "{\"error\":\"Unsupported Media Type. Use Content-Type: image/png or image/jpeg.\"}");
                return;
            }

            byte[] body;
            try (InputStream in = ex.getRequestBody()) {
                body = in.readNBytes(MAX_UPLOAD + 1);
            }
            if (body.length > MAX_UPLOAD) {
                writeJson(ex, 413, "{\"error\":\"Payload too large. Maximum upload size is 5MB.\"}");
                return;
            }
            if (body.length == 0) {
                writeJson(ex, 400, "{\"error\":\"No image bytes received.\"}");
                return;
            }

            Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());
            DocumentType hintType = null;
            String hintRaw = q.get("hint");
            if (hintRaw != null && !hintRaw.isBlank()) {
                String h = hintRaw.trim().toUpperCase();
                if (!h.equals("PAN") && !h.equals("AADHAAR")) {
                    writeJson(ex, 400, "{\"error\":\"Invalid hint; use pan or aadhaar.\"}");
                    return;
                }
                hintType = DocumentType.valueOf(h);
            }

            Extraction extraction;
            try {
                extraction = extractor.extract(body, mime);
            } catch (ExtractionException e) {
                writeJson(ex, statusFor(e), errorJsonFor(e));
                return;
            }

            ExtractionOutcome outcome;
            try {
                outcome = ExtractionService.extractAndValidate(extraction, hintType);
            } catch (ExtractionException e) {
                writeJson(ex, statusFor(e), errorJsonFor(e));
                return;
            }

            System.out.println("extract ok mime=" + mime + " size=" + body.length
                    + " hint=" + (hintType == null ? "-" : hintType)
                    + " modelType=" + outcome.extractedType()
                    + " confidence=" + outcome.confidence()
                    + " valid=" + outcome.validation().isValid());
            writeJson(ex, 200, outcome.toJson());
        }

        private static int statusFor(ExtractionException e) {
            return switch (e.status()) {
                case NO_DOCUMENT, UNPARSEABLE -> 422;
                case UPSTREAM_ERROR, TIMEOUT -> 502;
                case RATE_LIMITED -> 503;
            };
        }

        private static String errorJsonFor(ExtractionException e) {
            String msg = e.getMessage() == null ? e.status().name() : e.getMessage();
            return "{\"error\":\"" + Json.escape(msg) + "\",\"status\":\"" + e.status().name() + "\"}";
        }
    }

    /**
     * Stand-in for the legacy cloud extraction endpoint when cloud vision is
     * disabled (the production default). Returns 503 for any method so the
     * endpoint is effectively absent while local browser OCR is the flow.
     */
    static final class DisabledExtractHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            writeJson(ex, 503,
                    "{\"error\":\"Cloud vision extraction is disabled. "
                    + "OCR runs locally in the browser.\",\"status\":\"CLOUD_VISION_DISABLED\"}");
        }
    }

    /**
     * Wraps an inner handler with an authentication check. If the request has no
     * valid session, returns 401 JSON instead of dispatching to the inner
     * handler. With {@link AuthGate#open()} (legacy test seams) this is a no-op
     * pass-through, so existing validation tests are unchanged.
     */
    static final class AuthFilter implements HttpHandler {
        private final HttpHandler inner;
        private final AuthGate gate;

        AuthFilter(HttpHandler inner, AuthGate gate) {
            this.inner = inner;
            this.gate = gate;
        }

        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (gate.authenticate(ex).isEmpty()) {
                writeJson(ex, 401, "{\"error\":\"unauthenticated\"}");
                return;
            }
            inner.handle(ex);
        }
    }

    /**
     * Serves the protected validator app at {@code /app}. Unauthenticated
     * requests are redirected to the public landing page. Any other path under
     * {@code /app} (e.g. {@code /app.html}) returns 404 so the app HTML cannot
     * be fetched directly without a session.
     */
    static final class AppRouteHandler implements HttpHandler {
        private final AuthGate gate;

        AppRouteHandler(AuthGate gate) {
            this.gate = gate;
        }

        @Override
        public void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath();
            if (!path.equals("/app") && !path.equals("/app/")) {
                writeText(ex, 404, "Not found");
                return;
            }
            if (gate.authenticate(ex).isEmpty()) {
                ex.getResponseHeaders().set("Location", "/");
                ex.sendResponseHeaders(302, -1);
                return;
            }
            try (InputStream in = ValidationServer.class.getResourceAsStream("/public/app.html")) {
                if (in == null) {
                    writeText(ex, 500, "app.html not found");
                    return;
                }
                byte[] body = in.readAllBytes();
                ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                ex.sendResponseHeaders(200, body.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(body);
                }
            }
        }
    }

    /**
     * All authentication endpoints under {@code /api/auth}. Login endpoints are
     * same-origin checked (Origin/Referer host must equal the request Host);
     * the session cookie is HttpOnly + SameSite=Lax (+ Secure in prod).
     * Never logs ID tokens, cookies, or personal values.
     */
    static final class AuthHandler implements HttpHandler {
        private static final int MAX_BODY = 8192;
        private final AuthConfig cfg;
        private final IdTokenVerifier verifier; // null in dev-bypass mode
        private final AuthGate gate;

        AuthHandler(AuthConfig cfg, IdTokenVerifier verifier, AuthGate gate) {
            this.cfg = cfg;
            this.verifier = verifier;
            this.gate = gate;
        }

        @Override
        public void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath();
            String method = ex.getRequestMethod().toUpperCase();
            if (path.equals("/api/auth/config") && method.equals("GET")) {
                configJson(ex);
            } else if (path.equals("/api/auth/google") && method.equals("POST")) {
                googleLogin(ex);
            } else if (path.equals("/api/auth/dev-login") && method.equals("POST")) {
                devLogin(ex);
            } else if (path.equals("/api/auth/me") && method.equals("GET")) {
                me(ex);
            } else if (path.equals("/api/auth/logout") && method.equals("POST")) {
                logout(ex);
            } else {
                ex.getResponseHeaders().set("Allow", "GET, POST");
                writeJson(ex, 404, "{\"error\":\"Not found\"}");
            }
        }

        private void configJson(HttpExchange ex) throws IOException {
            String mode = cfg.devBypass() ? "dev_bypass" : "google";
            String cid = cfg.googleClientId() == null ? "" : Json.escape(cfg.googleClientId());
            writeJson(ex, 200, "{\"mode\":\"" + mode + "\",\"googleClientId\":\"" + cid + "\"}");
        }

        private void googleLogin(HttpExchange ex) throws IOException {
            if (cfg.devBypass() || verifier == null) {
                writeJson(ex, 503,
                        "{\"error\":\"Google login is disabled in dev-bypass mode.\","
                        + "\"status\":\"GOOGLE_AUTH_UNAVAILABLE\"}");
                return;
            }
            if (!sameOrigin(ex)) {
                writeJson(ex, 403, "{\"error\":\"Forbidden: cross-origin request.\"}");
                return;
            }
            Map<String, String> body = readJsonBody(ex);
            if (body == null) {
                writeJson(ex, 400, "{\"error\":\"Malformed JSON body.\"}");
                return;
            }
            String idToken = body.get("credential");
            if (idToken == null || idToken.isBlank()) {
                idToken = body.get("idToken");
            }
            if (idToken == null || idToken.isBlank()) {
                writeJson(ex, 400, "{\"error\":\"Missing 'credential' (Google ID token).\"}");
                return;
            }
            VerifiedUser user;
            try {
                user = verifier.verify(idToken);
            } catch (SessionException e) {
                writeJson(ex, 401, "{\"error\":\"Invalid Google ID token.\"}");
                return;
            }
            issueSession(ex, user);
            writeJson(ex, 200, "{\"ok\":true}");
        }

        private void devLogin(HttpExchange ex) throws IOException {
            // The guest/dev login mints a real signed session without a Google
            // ID token, so it must only ever be reachable when DEV_BYPASS_AUTH
            // is active. In every other (production) deployment it is refused
            // as a 404 so it is not even discoverable.
            if (!cfg.devBypass()) {
                writeJson(ex, 404, "{\"error\":\"Not found.\"}");
                return;
            }
            if (!sameOrigin(ex)) {
                writeJson(ex, 403, "{\"error\":\"Forbidden: cross-origin request.\"}");
                return;
            }
            issueSession(ex, new VerifiedUser("guest-local", "guest@localhost", "Guest User", "", true));
            writeJson(ex, 200, "{\"ok\":true}");
        }

        private void me(HttpExchange ex) throws IOException {
            Optional<SessionUser> user = gate.authenticate(ex);
            if (user.isEmpty()) {
                writeJson(ex, 401, "{\"error\":\"unauthenticated\"}");
                return;
            }
            SessionUser s = user.get();
            writeJson(ex, 200, "{\"sub\":\"" + Json.escape(s.sub()) + "\","
                    + "\"email\":\"" + Json.escape(s.email()) + "\","
                    + "\"name\":\"" + Json.escape(s.name()) + "\","
                    + "\"picture\":\"" + Json.escape(s.picture()) + "\"}");
        }

        private void logout(HttpExchange ex) throws IOException {
            if (!sameOrigin(ex)) {
                writeJson(ex, 403, "{\"error\":\"Forbidden: cross-origin request.\"}");
                return;
            }
            ex.getResponseHeaders().add("Set-Cookie", clearCookieValue(cfg));
            writeJson(ex, 200, "{\"ok\":true}");
        }

        private void issueSession(HttpExchange ex, VerifiedUser user) {
            long now = java.time.Instant.now().getEpochSecond();
            SessionClaims claims = new SessionClaims(user.sub(), user.email(), user.name(),
                    user.picture(), now + cfg.ttlSeconds());
            String token = SessionToken.sign(claims, cfg.secretBytes());
            ex.getResponseHeaders().add("Set-Cookie", setCookieValue(cfg, token));
        }

        private Map<String, String> readJsonBody(HttpExchange ex) throws IOException {
            byte[] body;
            try (InputStream in = ex.getRequestBody()) {
                body = in.readNBytes(MAX_BODY + 1);
            }
            if (body.length > MAX_BODY) {
                return null;
            }
            try {
                return JsonReader.readObject(new String(body, StandardCharsets.UTF_8));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    static final class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath();
            if (path == null || path.isEmpty() || path.equals("/")) {
                path = "/index.html";
            }
            if (path.contains("..")) {
                writeText(ex, 403, "Forbidden");
                return;
            }
            String resource = "/public" + path;
            try (InputStream in = ValidationServer.class.getResourceAsStream(resource)) {
                if (in == null) {
                    writeText(ex, 404, "Not found: " + path);
                    return;
                }
                byte[] body = in.readAllBytes();
                ex.getResponseHeaders().set("Content-Type", contentType(path));
                if (path.startsWith("/tesseract/")) {
                    ex.getResponseHeaders().set(
                            "Cache-Control", "public, max-age=604800, immutable");
                }
                ex.sendResponseHeaders(200, body.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(body);
                }
            }
        }
    }

    // ---- Session cookie + same-origin helpers --------------------------

    static String setCookieValue(AuthConfig cfg, String token) {
        StringBuilder sb = new StringBuilder();
        sb.append(cfg.cookieName()).append("=").append(token);
        sb.append("; HttpOnly");
        sb.append("; SameSite=Lax");
        sb.append("; Path=/");
        sb.append("; Max-Age=").append(cfg.ttlSeconds());
        if (cfg.secureCookie()) {
            sb.append("; Secure");
        }
        return sb.toString();
    }

    static String clearCookieValue(AuthConfig cfg) {
        StringBuilder sb = new StringBuilder();
        sb.append(cfg.cookieName()).append("=; HttpOnly; SameSite=Lax; Path=/; Max-Age=0");
        if (cfg.secureCookie()) {
            sb.append("; Secure");
        }
        return sb.toString();
    }

    /**
     * Returns true when the request's Origin (or Referer fallback) host matches
     * the request Host header. Used to block cross-site state-changing auth
     * POSTs (CSRF defense-in-depth on top of the SameSite=Lax cookie).
     */
    static boolean sameOrigin(HttpExchange ex) {
        String host = ex.getRequestHeaders().getFirst("Host");
        if (host == null || host.isBlank()) {
            return false;
        }
        String origin = ex.getRequestHeaders().getFirst("Origin");
        if (origin == null || origin.isBlank()) {
            origin = ex.getRequestHeaders().getFirst("Referer");
        }
        if (origin == null || origin.isBlank()) {
            return false;
        }
        return host.equalsIgnoreCase(hostOf(origin));
    }

    static String hostOf(String url) {
        int scheme = url.indexOf("://");
        String rest = scheme >= 0 ? url.substring(scheme + 3) : url;
        int slash = rest.indexOf('/');
        if (slash >= 0) {
            rest = rest.substring(0, slash);
        }
        int at = rest.indexOf('@');
        if (at >= 0) {
            rest = rest.substring(at + 1);
        }
        return rest;
    }

    private static boolean ensureMethod(HttpExchange ex, String method) throws IOException {
        if (!ex.getRequestMethod().equalsIgnoreCase(method)) {
            ex.getResponseHeaders().set("Allow", method);
            writeJson(ex, 405, "{\"error\":\"Method not allowed\"}");
            return false;
        }
        return true;
    }

    private static void writeJson(HttpExchange ex, int status, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, data.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
    }

    private static void writeText(HttpExchange ex, int status, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        ex.sendResponseHeaders(status, data.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
    }

    private static String contentType(String path) {
        if (path.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (path.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (path.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        if (path.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (path.endsWith(".png")) {
            return "image/png";
        }
        if (path.endsWith(".ico")) {
            return "image/x-icon";
        }
        if (path.endsWith(".wasm")) {
            return "application/wasm";
        }
        if (path.endsWith(".gz")) {
            return "application/gzip";
        }
        if (path.endsWith(".woff2")) {
            return "font/woff2";
        }
        return "application/octet-stream";
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> map = new HashMap<>();
        if (raw == null || raw.isEmpty()) {
            return map;
        }
        for (String pair : raw.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int idx = pair.indexOf('=');
            String k;
            String v;
            if (idx < 0) {
                k = pair;
                v = "";
            } else {
                k = pair.substring(0, idx);
                v = pair.substring(idx + 1);
            }
            map.put(URLDecoder.decode(k, StandardCharsets.UTF_8),
                    URLDecoder.decode(v, StandardCharsets.UTF_8));
        }
        return map;
    }
}
