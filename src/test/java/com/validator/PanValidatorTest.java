package com.validator;

import com.validator.core.PanValidator;
import com.validator.model.ReasonCode;
import com.validator.model.ValidationResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PanValidatorTest {

    @Test
    void acceptsValidGovernmentExample() {
        ValidationResult r = PanValidator.validate("AFZPK7190K");
        assertTrue(r.isValid());
        assertEquals(ReasonCode.VALID, r.getReasonCode());
        assertEquals("AFZPK7190K", r.getNormalizedValue());
    }

    @Test
    void normalizesLowercase() {
        ValidationResult r = PanValidator.validate("afzpk7190k");
        assertTrue(r.isValid());
        assertEquals("AFZPK7190K", r.getNormalizedValue());
    }

    @Test
    void normalizesSeparators() {
        ValidationResult r = PanValidator.validate("AFZPK 7190 K");
        assertTrue(r.isValid());
        assertEquals("AFZPK7190K", r.getNormalizedValue());
    }

    @Test
    void rejectsWrongLength() {
        ValidationResult r = PanValidator.validate("ABC123");
        assertFalse(r.isValid());
        assertEquals(ReasonCode.PAN_WRONG_LENGTH, r.getReasonCode());
    }

    @Test
    void rejectsInvalidCategory() {
        ValidationResult r = PanValidator.validate("ABCDE1234F");
        assertFalse(r.isValid());
        assertEquals(ReasonCode.PAN_INVALID_CATEGORY, r.getReasonCode());
    }

    @Test
    void rejectsExpectedLetterPosition() {
        ValidationResult r = PanValidator.validate("ABC1E1234F");
        assertFalse(r.isValid());
        assertEquals(ReasonCode.PAN_EXPECTED_LETTER, r.getReasonCode());
    }

    @Test
    void rejectsExpectedDigitPosition() {
        ValidationResult r = PanValidator.validate("ABCPEA234F");
        assertFalse(r.isValid());
        assertEquals(ReasonCode.PAN_EXPECTED_DIGIT, r.getReasonCode());
    }

    @Test
    void rejectsInvalidCharacter() {
        ValidationResult r = PanValidator.validate("ABC_E1234F");
        assertFalse(r.isValid());
        assertEquals(ReasonCode.INVALID_CHARACTER, r.getReasonCode());
    }

    @Test
    void rejectsEmpty() {
        ValidationResult r = PanValidator.validate("   ");
        assertFalse(r.isValid());
        assertEquals(ReasonCode.EMPTY, r.getReasonCode());
    }
}
