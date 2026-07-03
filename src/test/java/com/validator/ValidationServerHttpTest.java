package com.validator;

import com.validator.server.ValidationServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationServerHttpTest {

    private ValidationServer server;
    private HttpClient client;
    private String base;

    @BeforeEach
    void setUp() throws Exception {
        server = ValidationServer.start(0);
        base = "http://localhost:" + server.getPort();
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

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
}
