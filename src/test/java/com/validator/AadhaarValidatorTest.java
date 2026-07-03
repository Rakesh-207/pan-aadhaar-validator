package com.validator;

import com.validator.core.AadhaarValidator;
import com.validator.model.ReasonCode;
import com.validator.model.ValidationResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AadhaarValidatorTest {

    @Test
    void acceptsValidSample() {
        ValidationResult r = AadhaarValidator.validate("234567890124");
        assertTrue(r.isValid());
        assertEquals(ReasonCode.VALID, r.getReasonCode());
        assertEquals("234567890124", r.getNormalizedValue());
    }

    @Test
    void normalizesSeparators() {
        ValidationResult r = AadhaarValidator.validate("2345 6789 0124");
        assertTrue(r.isValid());
        assertEquals("234567890124", r.getNormalizedValue());
    }

    @Test
    void rejectsWrongLength() {
        ValidationResult r = AadhaarValidator.validate("2345678901");
        assertFalse(r.isValid());
        assertEquals(ReasonCode.AADHAAR_WRONG_LENGTH, r.getReasonCode());
    }

    @Test
    void rejectsLeadingZero() {
        ValidationResult r = AadhaarValidator.validate("012345678901");
        assertFalse(r.isValid());
        assertEquals(ReasonCode.AADHAAR_LEADING_DIGIT, r.getReasonCode());
    }

    @Test
    void rejectsChecksumFailure() {
        ValidationResult r = AadhaarValidator.validate("234567890120");
        assertFalse(r.isValid());
        assertEquals(ReasonCode.AADHAAR_VERHOEFF_FAILED, r.getReasonCode());
    }

    @Test
    void rejectsNonAsciiDigits() {
        ValidationResult r = AadhaarValidator.validate("२३४५६७८९०१२३");
        assertFalse(r.isValid());
        assertEquals(ReasonCode.AADHAAR_NON_ASCII_DIGIT, r.getReasonCode());
    }

    @Test
    void rejectsLetterAsInvalidCharacter() {
        ValidationResult r = AadhaarValidator.validate("23456789012A");
        assertFalse(r.isValid());
        assertEquals(ReasonCode.INVALID_CHARACTER, r.getReasonCode());
    }

    @Test
    void rejectsEmpty() {
        ValidationResult r = AadhaarValidator.validate("");
        assertFalse(r.isValid());
        assertEquals(ReasonCode.EMPTY, r.getReasonCode());
    }
}
