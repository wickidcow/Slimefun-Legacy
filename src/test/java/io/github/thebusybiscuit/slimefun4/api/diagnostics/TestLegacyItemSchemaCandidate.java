package io.github.thebusybiscuit.slimefun4.api.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaCandidate.Readiness;
import org.junit.jupiter.api.Test;

class TestLegacyItemSchemaCandidate {

    @Test
    void exposesValidatedCandidateMetadata() {
        LegacyItemSchemaCandidate candidate = new LegacyItemSchemaCandidate(
                "legacy-dolly-backpack-binding",
                Readiness.VALIDATION_REQUIRED,
                "Legacy backpack binding requires persistent-state verification",
                "opaque-claim");

        assertEquals("legacy-dolly-backpack-binding", candidate.getCandidateType());
        assertEquals(Readiness.VALIDATION_REQUIRED, candidate.getReadiness());
        assertEquals("Legacy backpack binding requires persistent-state verification", candidate.getDetail());
        assertEquals("opaque-claim", candidate.getValidationClaim());
    }

    @Test
    void validationRequiredCandidatesMustCarryClaim() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LegacyItemSchemaCandidate(
                        "legacy-item", Readiness.VALIDATION_REQUIRED, "Needs verification"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new LegacyItemSchemaCandidate(
                        "legacy-item", Readiness.VALIDATION_REQUIRED, "Needs verification", "   "));
    }

    @Test
    void rejectsUnsafeOrBlankCandidateKeys() {
        assertThrows(IllegalArgumentException.class, () -> new LegacyItemSchemaCandidate("", Readiness.READY, "detail"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new LegacyItemSchemaCandidate("contains spaces", Readiness.READY, "detail"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new LegacyItemSchemaCandidate("legacy:type", Readiness.READY, "detail"));
    }

    @Test
    void rejectsBlankDetails() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LegacyItemSchemaCandidate("legacy-item", Readiness.READY, "   "));
    }
}
