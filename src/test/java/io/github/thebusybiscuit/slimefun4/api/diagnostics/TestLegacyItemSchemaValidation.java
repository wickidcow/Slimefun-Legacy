package io.github.thebusybiscuit.slimefun4.api.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TestLegacyItemSchemaValidation {

    @Test
    void verifiedValidationMayCarryPrivateMigrationPayload() {
        LegacyItemSchemaValidation validation = new LegacyItemSchemaValidation(
                LegacyItemSchemaValidation.Status.VERIFIED,
                "Backing state matches.",
                "private-modern-record-id");

        assertEquals(LegacyItemSchemaValidation.Status.VERIFIED, validation.getStatus());
        assertEquals("private-modern-record-id", validation.getMigrationPayload());
    }

    @Test
    void nonVerifiedValidationCannotCarryMigrationPayload() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LegacyItemSchemaValidation(
                        LegacyItemSchemaValidation.Status.STATE_MISMATCH,
                        "State does not match.",
                        "must-not-be-authorized"));
    }

    @Test
    void legacyTwoArgumentConstructorRemainsDiagnosticOnly() {
        LegacyItemSchemaValidation validation = new LegacyItemSchemaValidation(
                LegacyItemSchemaValidation.Status.VERIFIED,
                "Verified for diagnostics only.");
        assertNull(validation.getMigrationPayload());
    }

    @Test
    void blankAndOversizedPayloadsAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LegacyItemSchemaValidation(
                        LegacyItemSchemaValidation.Status.VERIFIED,
                        "Backing state matches.",
                        "   "));
        assertThrows(
                IllegalArgumentException.class,
                () -> new LegacyItemSchemaValidation(
                        LegacyItemSchemaValidation.Status.VERIFIED,
                        "Backing state matches.",
                        "x".repeat(2049)));
    }
}
