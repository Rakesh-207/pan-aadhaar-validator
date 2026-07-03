package com.validator.vision;

import com.validator.json.JsonReader;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * Calls an OpenAI-compatible chat-completions endpoint (the Cerebras router
 * fronting {@code gemma-4-31b}) with a base64 image data URI and a strict-JSON
 * prompt, then parses the model's textual reply into an {@link Extraction}.
 *
 * <p>The image is held in memory only; it is base64-encoded for the single
 * outbound request and then discarded. Nothing is written to disk or a database.
 *
 * <p>The response-parsing helpers ({@link #extractAssistantContent(String)} and
 * {@link #parseModelContent(String)}) are pure and unit-tested in isolation,
 * so the deterministic test suite never burns a live router call.
 */
public final class CerebrasVisionExtractor implements VisionExtractor {

    private static final String SYSTEM_PROMPT =
            "You are an OCR assistant that extracts Indian government ID numbers from images. "
            + "Respond with STRICT JSON only - no markdown, no code fences, no commentary. "
            + "The JSON schema is exactly: "
            + "{\"type\":\"PAN\"|\"AADHAAR\"|\"UNKNOWN\",\"value\":\"<printed id>\","
            + "\"confidence\":\"HIGH\"|\"MEDIUM\"|\"LOW\"}. "
            + "Use type PAN for a 10-character income-tax ID and AADHAAR for a 12-digit UIDAI number. "
            + "Use UNKNOWN when no such ID is present or it cannot be read. "
            + "value is the exact printed string: letters upper-cased and digits only, with no spaces. "
            + "If no ID is present, set type to UNKNOWN and value to an empty string. "
            + "Output nothing except the JSON object.";

    private static final String USER_TEXT =
            "Extract the printed Indian PAN or Aadhaar number from this image.";

    private final String endpoint;
    private final String model;
    private final String bearer;
    private final int maxTokens;
    private final Duration readTimeout;
    private final HttpClient client;

    public CerebrasVisionExtractor(String endpoint, String model, String bearer,
                                   Duration connectTimeout, Duration readTimeout, int maxTokens) {
        this.endpoint = endpoint;
        this.model = model;
        this.bearer = bearer;
        this.maxTokens = maxTokens;
        this.readTimeout = readTimeout;
        this.client = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
    }

    @Override
    public Extraction extract(byte[] image, String mime) throws ExtractionException {
        String dataUri = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(image);
        String requestJson = buildRequest(dataUri);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(readTimeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson, StandardCharsets.UTF_8));
        if (bearer != null && !bearer.isBlank()) {
            builder.header("Authorization", "Bearer " + bearer);
        }

        HttpResponse<String> resp;
        try {
            resp = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new ExtractionException(ExtractionException.Status.TIMEOUT,
                    "Extraction service timed out.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExtractionException(ExtractionException.Status.UPSTREAM_ERROR,
                    "Extraction call interrupted.", e);
        } catch (IOException e) {
            throw new ExtractionException(ExtractionException.Status.UPSTREAM_ERROR,
                    "Could not reach extraction service.", e);
        }

        int code = resp.statusCode();
        if (code == 429) {
            throw new ExtractionException(ExtractionException.Status.RATE_LIMITED,
                    "Extraction service is rate limited. Try again shortly.");
        }
        if (code < 200 || code >= 300) {
            throw new ExtractionException(ExtractionException.Status.UPSTREAM_ERROR,
                    "Extraction service returned HTTP " + code + ".");
        }

        String content = extractAssistantContent(resp.body());
        return parseModelContent(content);
    }

    private String buildRequest(String dataUri) {
        StringBuilder sb = new StringBuilder(256 + dataUri.length());
        sb.append('{');
        sb.append("\"model\":\"").append(model).append("\",");
        sb.append("\"stream\":false,");
        sb.append("\"temperature\":0,");
        sb.append("\"max_tokens\":").append(maxTokens).append(',');
        sb.append("\"messages\":[");
        sb.append("{\"role\":\"system\",\"content\":\"").append(escape(SYSTEM_PROMPT)).append("\"},");
        sb.append("{\"role\":\"user\",\"content\":[");
        sb.append("{\"type\":\"text\",\"text\":\"").append(escape(USER_TEXT)).append("\"},");
        sb.append("{\"type\":\"image_url\",\"image_url\":{\"url\":\"").append(dataUri).append("\"}}");
        sb.append("]}");
        sb.append("]}");
        return sb.toString();
    }

    private static String escape(String s) {
        return com.validator.json.Json.escape(s);
    }

    /**
     * Pull the assistant {@code content} string out of an OpenAI-style chat
     * completion response. Scans for the first {@code "content"} key and reads
     * the following JSON string literal with full escape handling. Returns
     * {@code null} if no content string is present (e.g. content is null or an
     * array). Robust for well-formed responses; does not validate the whole tree.
     */
    static String extractAssistantContent(String json) {
        if (json == null) {
            return null;
        }
        int keyIdx = json.indexOf("\"content\"");
        if (keyIdx < 0) {
            return null;
        }
        int colon = json.indexOf(':', keyIdx);
        if (colon < 0) {
            return null;
        }
        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        if (i >= json.length() || json.charAt(i) != '"') {
            return null;
        }
        i++;
        StringBuilder sb = new StringBuilder();
        while (i < json.length()) {
            char c = json.charAt(i++);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\') {
                if (i >= json.length()) {
                    return null;
                }
                char e = json.charAt(i++);
                switch (e) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (i + 4 > json.length()) {
                            return null;
                        }
                        String hex = json.substring(i, i + 4);
                        i += 4;
                        try {
                            sb.append((char) Integer.parseInt(hex, 16));
                        } catch (NumberFormatException nfe) {
                            return null;
                        }
                    }
                    default -> {
                        return null;
                    }
                }
            } else if (c < 0x20) {
                return null;
            } else {
                sb.append(c);
            }
        }
        return null;
    }

    /**
     * Coerce a model textual reply into a typed {@link Extraction}. Strips
     * markdown fences, isolates the JSON object substring, parses it with the
     * project's flat-object reader, and normalises the type label. The
     * UNKNOWN / empty-value decision is left to {@link ExtractionService}.
     */
    static Extraction parseModelContent(String content) throws ExtractionException {
        if (content == null || content.isBlank()) {
            throw new ExtractionException(ExtractionException.Status.UNPARSEABLE,
                    "Empty model response.");
        }
        String s = content.trim();
        if (s.startsWith("```")) {
            int firstNl = s.indexOf('\n');
            if (firstNl >= 0) {
                s = s.substring(firstNl + 1);
            } else {
                s = s.substring(3);
            }
            int fence = s.lastIndexOf("```");
            if (fence >= 0) {
                s = s.substring(0, fence);
            }
            s = s.trim();
        }
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new ExtractionException(ExtractionException.Status.UNPARSEABLE,
                    "No JSON object in model response.");
        }
        String json = s.substring(start, end + 1);
        Map<String, String> fields;
        try {
            fields = JsonReader.readObject(json);
        } catch (IllegalArgumentException ex) {
            throw new ExtractionException(ExtractionException.Status.UNPARSEABLE,
                    "Malformed JSON in model response.", ex);
        }
        String type = fields.getOrDefault("type", "").trim().toUpperCase();
        String value = fields.getOrDefault("value", "").trim();
        String confidence = fields.getOrDefault("confidence", "").trim().toUpperCase();
        if (!type.equals("PAN") && !type.equals("AADHAAR") && !type.equals("UNKNOWN")) {
            throw new ExtractionException(ExtractionException.Status.UNPARSEABLE,
                    "Unrecognised type label in model response.");
        }
        return new Extraction(type, value, confidence);
    }
}
