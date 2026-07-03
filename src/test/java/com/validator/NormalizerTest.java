package com.validator;

import com.validator.core.Normalizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NormalizerTest {

    @Test
    void normalizesLowercasePan() {
        assertEquals("ABCDE1234F", Normalizer.normalizePan("abcde1234f"));
    }

    @Test
    void normalizesPanWithHyphens() {
        assertEquals("ABCDE1234F", Normalizer.normalizePan("ABCDE-1234-F"));
    }

    @Test
    void normalizesPanWithSpaces() {
        assertEquals("ABCDE1234F", Normalizer.normalizePan(" ABCDE 1234 F "));
    }

    @Test
    void normalizesAadhaarWithSpaces() {
        assertEquals("123456789012", Normalizer.normalizeAadhaar("1234 5678 9012"));
    }

    @Test
    void normalizesAadhaarWithHyphens() {
        assertEquals("123456789012", Normalizer.normalizeAadhaar("1234-5678-9012"));
    }

    @Test
    void trimsAndPreservesDigits() {
        assertEquals("234567890124", Normalizer.normalizeAadhaar(" 2345 6789 0124 "));
    }

    @Test
    void handlesNull() {
        assertEquals("", Normalizer.normalizePan(null));
        assertEquals("", Normalizer.normalizeAadhaar(null));
    }
}
