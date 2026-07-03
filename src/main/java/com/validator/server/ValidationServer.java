package com.validator.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.validator.core.DocumentValidator;
import com.validator.json.JsonReader;
import com.validator.model.DocumentType;
import com.validator.model.ValidationResult;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public final class ValidationServer {

    private static final String SERVICE = "pan-aadhaar-validator";
    private static final String VERSION = "1.0.0";

    private final HttpServer server;

    private ValidationServer(HttpServer server) {
        this.server = server;
    }

    public static ValidationServer start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/health", new HealthHandler());
        server.createContext("/api/validate", new ValidateHandler());
        server.createContext("/", new StaticHandler());
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        return new ValidationServer(server);
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
                ex.sendResponseHeaders(200, body.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(body);
                }
            }
        }
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
