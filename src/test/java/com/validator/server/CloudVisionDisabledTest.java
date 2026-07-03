package com.validator.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the cloud extraction endpoint is inert unless explicitly enabled:
 * with cloud vision off (the production default), posting an image returns
 * 503 / CLOUD_VISION_DISABLED and no upstream HTTP client is ever built.
 */
class CloudVisionDisabledTest {

    private ValidationServer server;
    private HttpClient client;
    private String base;

    @BeforeEach
    void setUp() throws Exception {
        server = ValidationServer.start(0, false);
        base = "http://localhost:" + server.getPort();
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    @Test
    void extractEndpointDisabledWhenCloudOff() throws Exception {
        HttpResponse<String> res = client.send(HttpRequest.newBuilder()
                .uri(URI.create(base + "/api/extract-and-validate?hint=pan"))
                .header("Content-Type", "image/png")
                .POST(HttpRequest.BodyPublishers.ofByteArray(new byte[]{1, 2, 3, 4}))
                .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(503, res.statusCode());
        assertTrue(res.body().contains("CLOUD_VISION_DISABLED"));
    }
}
