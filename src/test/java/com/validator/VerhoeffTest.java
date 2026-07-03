package com.validator;

import com.validator.core.Verhoeff;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerhoeffTest {

    @Test
    void validatesKnownCorrectNumber() {
        assertTrue(Verhoeff.validate("2363"));
    }

    @Test
    void rejectsTamperedCheckDigit() {
        assertFalse(Verhoeff.validate("2369"));
    }

    @Test
    void generatesCheckDigitFor236() {
        assertEquals(3, Verhoeff.generateCheckDigit("236"));
    }

    @Test
    void generatedNumberValidates() {
        String base = "236";
        int cd = Verhoeff.generateCheckDigit(base);
        assertTrue(Verhoeff.validate(base + cd));
    }

    @Test
    void rosettaCodeVectors() {
        assertEquals(1, Verhoeff.generateCheckDigit("12345"));
        assertEquals(0, Verhoeff.generateCheckDigit("123456789012"));
    }

    @Test
    void twelveDigitAadhaarLikeNumberValidates() {
        String base = "23456789012";
        int cd = Verhoeff.generateCheckDigit(base);
        String full = base + cd;
        assertEquals(12, full.length());
        assertTrue(Verhoeff.validate(full));
    }

    @Test
    void rejectsNull() {
        assertFalse(Verhoeff.validate(null));
    }
}
