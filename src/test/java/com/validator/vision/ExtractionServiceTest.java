package com.validator.vision;

import com.validator.model.DocumentType;
import com.validator.model.ReasonCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtractionServiceTest {

    @Test
    void extractedPanFlowsThroughPanValidator() throws ExtractionException {
        ExtractionOutcome o = ExtractionService.extractAndValidate(
                new Extraction("PAN", "AFZPK7190K", "HIGH"), null);
        assertTrue(o.validation().isValid());
        assertEquals(DocumentType.PAN, o.effectiveType());
        assertEquals("AFZPK7190K", o.validation().getNormalizedValue());
        assertFalse(o.typeMismatch());
    }

    @Test
    void extractedAadhaarFlowsThroughAadhaarValidator() throws ExtractionException {
        ExtractionOutcome o = ExtractionService.extractAndValidate(
                new Extraction("AADHAAR", "234567890124", "HIGH"), null);
        assertTrue(o.validation().isValid());
        assertEquals(DocumentType.AADHAAR, o.effectiveType());
        assertEquals("234567890124", o.validation().getNormalizedValue());
    }

    @Test
    void modelValueIsValidatedDeterministically() throws ExtractionException {
        ExtractionOutcome o = ExtractionService.extractAndValidate(
                new Extraction("PAN", "ABCDE1234F", "HIGH"), null);
        assertFalse(o.validation().isValid());
        assertEquals(ReasonCode.PAN_INVALID_CATEGORY, o.validation().getReasonCode());
    }

    @Test
    void hintSelectsValidatorAndFlagsMismatch() throws ExtractionException {
        ExtractionOutcome o = ExtractionService.extractAndValidate(
                new Extraction("AADHAAR", "234567890124", "HIGH"), DocumentType.PAN);
        assertEquals(DocumentType.PAN, o.effectiveType());
        assertTrue(o.typeMismatch());
        assertFalse(o.validation().isValid());
        assertEquals(ReasonCode.PAN_WRONG_LENGTH, o.validation().getReasonCode());
    }

    @Test
    void hintWithUnknownModelTypeValidatesOnHint() throws ExtractionException {
        ExtractionOutcome o = ExtractionService.extractAndValidate(
                new Extraction("UNKNOWN", "AFZPK7190K", "LOW"), DocumentType.PAN);
        assertEquals(DocumentType.PAN, o.effectiveType());
        assertFalse(o.typeMismatch());
        assertTrue(o.validation().isValid());
    }

    @Test
    void noHintAndUnknownModelTypeYields422() {
        ExtractionException ex = assertThrows(ExtractionException.class, () ->
                ExtractionService.extractAndValidate(
                        new Extraction("UNKNOWN", "AFZPK7190K", "LOW"), null));
        assertEquals(ExtractionException.Status.NO_DOCUMENT, ex.status());
    }

    @Test
    void emptyValueYields422() {
        ExtractionException ex = assertThrows(ExtractionException.class, () ->
                ExtractionService.extractAndValidate(
                        new Extraction("PAN", "", "HIGH"), null));
        assertEquals(ExtractionException.Status.NO_DOCUMENT, ex.status());
    }

    @Test
    void sanitizesWhitespaceAndQuotes() {
        assertEquals("AFZPK7190K", ExtractionService.sanitize("  AFZPK7190K  "));
        assertEquals("AFZPK7190K", ExtractionService.sanitize("\"AFZPK7190K\""));
        assertEquals("AFZPK7190K", ExtractionService.sanitize("'AFZPK7190K'"));
        assertEquals("AFZPK 7190", ExtractionService.sanitize("AFZPK\t7190"));
        assertEquals("2345 6789 0124", ExtractionService.sanitize("2345 6789 0124"));
        assertEquals("", ExtractionService.sanitize(""));
        assertEquals("", ExtractionService.sanitize(null));
    }

    @Test
    void surroundingValueIsTrimmedBeforeValidation() throws ExtractionException {
        ExtractionOutcome o = ExtractionService.extractAndValidate(
                new Extraction("PAN", "  AFZPK7190K  ", "HIGH"), null);
        assertTrue(o.validation().isValid());
        assertEquals("AFZPK7190K", o.value());
    }

    @Test
    void recordsExtractedTypeAndConfidence() throws ExtractionException {
        ExtractionOutcome o = ExtractionService.extractAndValidate(
                new Extraction("PAN", "AFZPK7190K", "MEDIUM"), null);
        assertEquals("PAN", o.extractedType());
        assertEquals("MEDIUM", o.confidence());
    }
}
