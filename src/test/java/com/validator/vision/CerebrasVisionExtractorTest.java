package com.validator.vision;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CerebrasVisionExtractorTest {

    @Test
    void parsesValidPanJson() throws ExtractionException {
        Extraction e = CerebrasVisionExtractor.parseModelContent(
                "{\"type\":\"PAN\",\"value\":\"AFZPK7190K\",\"confidence\":\"HIGH\"}");
        assertEquals("PAN", e.type());
        assertEquals("AFZPK7190K", e.value());
        assertEquals("HIGH", e.confidence());
    }

    @Test
    void parsesValidAadhaarJson() throws ExtractionException {
        Extraction e = CerebrasVisionExtractor.parseModelContent(
                "{\"type\":\"AADHAAR\",\"value\":\"234567890124\",\"confidence\":\"MEDIUM\"}");
        assertEquals("AADHAAR", e.type());
        assertEquals("234567890124", e.value());
    }

    @Test
    void stripsMarkdownCodeFences() throws ExtractionException {
        Extraction e = CerebrasVisionExtractor.parseModelContent(
                "```json\n{\"type\":\"PAN\",\"value\":\"AFZPK7190K\",\"confidence\":\"HIGH\"}\n```");
        assertEquals("PAN", e.type());
        assertEquals("AFZPK7190K", e.value());
    }

    @Test
    void isolatesJsonEmbeddedInProse() throws ExtractionException {
        Extraction e = CerebrasVisionExtractor.parseModelContent(
                "Here you go: {\"type\":\"AADHAAR\",\"value\":\"234567890124\",\"confidence\":\"LOW\"} thanks");
        assertEquals("AADHAAR", e.type());
        assertEquals("234567890124", e.value());
    }

    @Test
    void acceptsUnknownWithEmptyValue() throws ExtractionException {
        Extraction e = CerebrasVisionExtractor.parseModelContent(
                "{\"type\":\"UNKNOWN\",\"value\":\"\",\"confidence\":\"LOW\"}");
        assertEquals("UNKNOWN", e.type());
        assertEquals("", e.value());
    }

    @Test
    void rejectsMissingType() {
        ExtractionException ex = assertThrows(ExtractionException.class, () ->
                CerebrasVisionExtractor.parseModelContent("{\"value\":\"AFZPK7190K\",\"confidence\":\"HIGH\"}"));
        assertEquals(ExtractionException.Status.UNPARSEABLE, ex.status());
    }

    @Test
    void rejectsUnrecognisedTypeLabel() {
        ExtractionException ex = assertThrows(ExtractionException.class, () ->
                CerebrasVisionExtractor.parseModelContent(
                        "{\"type\":\"PASSPORT\",\"value\":\"X12345\",\"confidence\":\"HIGH\"}"));
        assertEquals(ExtractionException.Status.UNPARSEABLE, ex.status());
    }

    @Test
    void rejectsNonJsonGarbage() {
        assertThrows(ExtractionException.class, () ->
                CerebrasVisionExtractor.parseModelContent("I cannot read this image."));
    }

    @Test
    void rejectsNullContent() {
        assertThrows(ExtractionException.class, () ->
                CerebrasVisionExtractor.parseModelContent(null));
    }

    @Test
    void rejectsMalformedJson() {
        assertThrows(ExtractionException.class, () ->
                CerebrasVisionExtractor.parseModelContent("{\"type\":\"PAN\",\"value\":}"));
    }

    @Test
    void extractsAssistantContentFromChatResponse() {
        String response = "{\"id\":\"x\",\"choices\":[{\"index\":0,\"message\":"
                + "{\"role\":\"assistant\",\"content\":\"OK\",\"reasoning\":\"\"},\"finish_reason\":\"stop\"}]}";
        assertEquals("OK", CerebrasVisionExtractor.extractAssistantContent(response));
    }

    @Test
    void unescapesAssistantContent() {
        String response = "{\"choices\":[{\"message\":{\"role\":\"assistant\","
                + "\"content\":\"line1\\nline2\\t\\\"q\\\"\"}}]}";
        assertEquals("line1\nline2\t\"q\"", CerebrasVisionExtractor.extractAssistantContent(response));
    }

    @Test
    void returnsNullWhenNoContentKey() {
        assertNull(CerebrasVisionExtractor.extractAssistantContent("{\"foo\":\"bar\"}"));
    }

    @Test
    void returnsNullWhenContentIsNullLiteral() {
        String response = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null}}]}";
        assertNull(CerebrasVisionExtractor.extractAssistantContent(response));
    }

    @Test
    void returnsNullForNullInput() {
        assertNull(CerebrasVisionExtractor.extractAssistantContent(null));
    }

    @Test
    void jsonOutputContainsExtractionBlock() {
        ExtractionOutcome outcome = new ExtractionOutcome(
                com.validator.core.DocumentValidator.validate(
                        com.validator.model.DocumentType.PAN, "AFZPK7190K"),
                "PAN", com.validator.model.DocumentType.PAN, com.validator.model.DocumentType.PAN,
                false, "AFZPK7190K", "HIGH");
        String json = outcome.toJson();
        assertTrue(json.contains("\"valid\":true"));
        assertTrue(json.contains("\"extraction\":{"));
        assertTrue(json.contains("\"extractedType\":\"PAN\""));
        assertTrue(json.contains("\"typeMismatch\":false"));
        assertTrue(json.contains("\"transient\":true"));
        assertTrue(json.contains("not stored"));
    }
}
