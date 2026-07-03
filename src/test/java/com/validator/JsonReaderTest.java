package com.validator;

import com.validator.json.JsonReader;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonReaderTest {

    @Test
    void parsesFlatObject() {
        Map<String, String> m = JsonReader.readObject("{\"type\":\"pan\",\"value\":\"AFZPK7190K\"}");
        assertEquals("pan", m.get("type"));
        assertEquals("AFZPK7190K", m.get("value"));
    }

    @Test
    void toleratesWhitespace() {
        Map<String, String> m = JsonReader.readObject("  {  \"type\" : \"aadhaar\" , \"value\":\"x\" }  ");
        assertEquals("aadhaar", m.get("type"));
        assertEquals("x", m.get("value"));
    }

    @Test
    void parsesEmptyObject() {
        assertTrue(JsonReader.readObject("{}").isEmpty());
    }

    @Test
    void parsesEscapes() {
        Map<String, String> m = JsonReader.readObject(
                "{\"value\":\"a\\\"b\\\\c\\/d\\be\\ff\\ng\\rh\\ti\\u0049\"}");
        assertEquals("a\"b\\c/d\be\ff\ng\rh\tiI", m.get("value"));
    }

    @Test
    void rejectsNonStringValue() {
        assertThrows(IllegalArgumentException.class,
                () -> JsonReader.readObject("{\"type\":123}"));
    }

    @Test
    void rejectsArray() {
        assertThrows(IllegalArgumentException.class,
                () -> JsonReader.readObject("[\"type\",\"pan\"]"));
    }

    @Test
    void rejectsMissingClosingBrace() {
        assertThrows(IllegalArgumentException.class,
                () -> JsonReader.readObject("{\"type\":\"pan\""));
    }

    @Test
    void rejectsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> JsonReader.readObject(null));
    }

    @Test
    void rejectsTrailingContent() {
        assertThrows(IllegalArgumentException.class,
                () -> JsonReader.readObject("{\"type\":\"pan\"} trailing"));
        assertThrows(IllegalArgumentException.class,
                () -> JsonReader.readObject("{\"type\":\"pan\"}{\"value\":\"x\"}"));
        assertThrows(IllegalArgumentException.class,
                () -> JsonReader.readObject("{\"type\":\"pan\"} junk"));
    }

    @Test
    void acceptsTrailingWhitespace() {
        Map<String, String> m = JsonReader.readObject("{\"type\":\"pan\"}   \n\t ");
        assertEquals("pan", m.get("type"));
    }
}
