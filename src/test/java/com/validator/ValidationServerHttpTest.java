package com.validator;

import com.validator.server.ValidationServer;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationServerHttpTest {

    private ValidationServer server;
    private HttpClient client;
    private String base;
    private StubExtractor stub;

    @BeforeEach
    void setUp() throws Exception {
        stub = new StubExtractor(new Extraction("PAN", "AFZPK7190K", "HIGH"));
        server = ValidationServer.start(0, stub);
        base = "http://localhost:" + server.getPort();
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    static final class StubExtractor implements VisionExtractor {
        Extraction result;
        ExtractionException error;

        StubExtractor(Extraction result) {
            this.result = result;
        }

        @Override
        public Extraction extract(byte[] image, String mime) throws ExtractionException {
            if (error != null) {
                throw error;
            }
            return result;
        }
    }

    private static final byte[] PNG_SIG = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A};

    @Test
    void healthReturnsUp() throws Exception {
        HttpResponse<String> res = client.send(
                HttpRequest.newBuilder().uri(URI.create(base + "/api/health")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("\"status\":\"UP\""));
    }

    @Test
    void validateGetReturnsResult() throws Exception {
        HttpResponse<String> res = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(base + "/api/validate?type=pan&value=AFZPK7190K"))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("\"valid\":true"));
    }

    @Test
    void validatePostReturnsResult() throws Exception {
        HttpResponse<String> res = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(base + "/api/validate"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"type\":\"aadhaar\",\"value\":\"234567890124\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("\"documentType\":\"AADHAAR\""));
        assertTrue(res.body().contains("\"valid\":true"));
    }

    @Test
    void postRejectsNonJsonContentType() throws Exception {
        HttpResponse<String> res = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(base + "/api/validate"))
                        .header("Content-Type", "text/plain")
                        .POST(HttpRequest.BodyPublishers.ofString("x"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(415, res.statusCode());
    }

    @Test
    void postRejectsInvalidType() throws Exception {
        HttpResponse<String> res = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(base + "/api/validate"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"type\":\"passport\",\"value\":\"X\"}"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(400, res.statusCode());
    }

    @Test
    void postRejectsTrailingGarbage() throws Exception {
        HttpResponse<String> res = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(base + "/api/validate"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(
                                "{\"type\":\"pan\",\"value\":\"AFZPK7190K\"} trailing"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(400, res.statusCode());
    }

    @Test
    void unsupportedMethodIsNotAllowed() throws Exception {
        HttpResponse<String> res = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(base + "/api/validate"))
                        .DELETE().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(405, res.statusCode());
        assertEquals("GET, POST", res.headers().firstValue("Allow").orElse(""));
    }

    @Test
    void servesIndexHtml() throws Exception {
        HttpResponse<String> res = client.send(
                HttpRequest.newBuilder().uri(URI.create(base + "/")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("<!DOCTYPE html>"));
    }

    @Test
    void extractRejectsGetMethod() throws Exception {
        HttpResponse<String> res = client.send(
                HttpRequest.newBuilder().uri(URI.create(base + "/api/extract-and-validate")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(405, res.statusCode());
        assertEquals("POST", res.headers().firstValue("Allow").orElse(""));
    }

    @Test
    void extractRejectsNonImageContentType() throws Exception {
        HttpResponse<String> res = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(base + "/api/extract-and-validate"))
                        .header("Content-Type", "text/plain")
                        .POST(HttpRequest.BodyPublishers.ofString("x"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(415, res.statusCode());
    }

    @Test
    void extractRejectsUnsupportedImageType() throws Exception {
        HttpResponse<String> res = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(base + "/api/extract-and-validate"))
                        .header("Content-Type", "image/gif")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(PNG_SIG))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(415, res.statusCode());
    }

    @Test
    void extractRejectsOversizedBody() throws Exception {
        byte[] tooBig = new byte[5 * 1024 * 1024 + 1];
        HttpResponse<String> res = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(base + "/api/extract-and-validate"))
                        .header("Content-Type", "image/png")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(tooBig))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(413, res.statusCode());
    }

    @Test
    void extractRejectsEmptyBody() throws Exception {
        HttpResponse<String> res = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(base + "/api/extract-and-validate"))
                        .header("Content-Type", "image/png")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(new byte[0]))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(400, res.statusCode());
    }

    @Test
    void extractRejectsBadHint() throws Exception {
        HttpResponse<String> res = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(base + "/api/extract-and-validate?hint=passport"))
                        .header("Content-Type", "image/png")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(PNG_SIG))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(400, res.statusCode());
    }

    @Test
    void extractReturnsValidPanWithExtractionBlock() throws Exception {
        stub.result = new Extraction("PAN", "AFZPK7190K", "HIGH");
        HttpResponse<String> res = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(base + "/api/extract-and-validate?hint=pan"))
                        .header("Content-Type", "image/png")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(PNG_SIG))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("\"valid\":true"));
        assertTrue(res.body().contains("\"documentType\":\"PAN\""));
        assertTrue(res.body().contains("\"extraction\":{"));
        assertTrue(res.body().contains("\"extractedType\":\"PAN\""));
        assertTrue(res.body().contains("\"transient\":true"));
    }

    @Test
    void extractReturnsValidAadhaar() throws Exception {
        stub.result = new Extraction("AADHAAR", "234567890124", "HIGH");
        HttpResponse<String> res = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(base + "/api/extract-and-validate"))
                        .header("Content-Type", "image/png")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(PNG_SIG))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("\"documentType\":\"AADHAAR\""));
        assertTrue(res.body().contains("\"valid\":true"));
    }

    @Test
    void extractValidatesModelValueDeterministically() throws Exception {
        stub.result = new Extraction("PAN", "ABCDE1234F", "HIGH");
        HttpResponse<String> res = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(base + "/api/extract-and-validate"))
                        .header("Content-Type", "image/png")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(PNG_SIG))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("\"valid\":false"));
        assertTrue(res.body().contains("PAN_INVALID_CATEGORY"));
    }

    @Test
    void extractFlagsTypeMismatchWhenHintConflicts() throws Exception {
        stub.result = new Extraction("AADHAAR", "234567890124", "HIGH");
        HttpResponse<String> res = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(base + "/api/extract-and-validate?hint=pan"))
                        .header("Content-Type", "image/png")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(PNG_SIG))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("\"typeMismatch\":true"));
        assertTrue(res.body().contains("\"effectiveType\":\"PAN\""));
    }

    @Test
    void extractModelUnknownWithoutHintReturns422() throws Exception {
        stub.result = new Extraction("UNKNOWN", "", "LOW");
        HttpResponse<String> res = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(base + "/api/extract-and-validate"))
                        .header("Content-Type", "image/png")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(PNG_SIG))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(422, res.statusCode());
        assertTrue(res.body().contains("\"status\":\"NO_DOCUMENT\""));
    }

    @Test
    void extractUpstreamErrorReturns502() throws Exception {
        stub.error = new ExtractionException(
                ExtractionException.Status.UPSTREAM_ERROR, "boom");
        HttpResponse<String> res = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(base + "/api/extract-and-validate"))
                        .header("Content-Type", "image/png")
                        .POST(HttpRequest.BodyPublishers.ofByteArray(PNG_SIG))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(502, res.statusCode());
        assertFalse(res.body().contains("AFZPK7190K"));
    }
}
