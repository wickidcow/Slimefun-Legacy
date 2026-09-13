package io.github.thebusybiscuit.slimefun4.core.services.stability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaCandidate;
import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaCandidate.Readiness;
import io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaValidation;
import java.util.List;
import org.junit.jupiter.api.Test;

class TestItemDoctorReportSchemaCandidates {

    @Test
    void aggregatesEquivalentSchemaCandidates() {
        ItemDoctorReport report = new ItemDoctorReport(false);
        LegacyItemSchemaCandidate candidate = new LegacyItemSchemaCandidate(
                "legacy-dolly-binding", Readiness.VALIDATION_REQUIRED, "Database verification required", "opaque");

        report.schemaMigrationCandidateFound("FluffyMachines", "Dolly migration", candidate);
        report.schemaMigrationCandidateFound("FluffyMachines", "Dolly migration", candidate);

        assertEquals(2L, report.getSchemaMigrationCandidates());
        List<LegacyItemSchemaCandidateSummary> summaries = report.getSchemaMigrationCandidateSummaries();
        assertEquals(1, summaries.size());
        assertEquals("FluffyMachines", summaries.getFirst().getProviderId());
        assertEquals("legacy-dolly-binding", summaries.getFirst().getCandidateType());
        assertEquals(Readiness.VALIDATION_REQUIRED, summaries.getFirst().getReadiness());
        assertEquals(2L, summaries.getFirst().getCount());
    }

    @Test
    void aggregatesValidationResultsByCandidateCount() {
        ItemDoctorReport report = new ItemDoctorReport(false);
        report.schemaValidationFound(
                "FluffyMachines",
                "Dolly migration",
                "legacy-dolly-binding",
                new LegacyItemSchemaValidation(
                        LegacyItemSchemaValidation.Status.VERIFIED,
                        "Backing state matches."),
                3L);

        assertEquals(3L, report.getSchemaValidatedCandidates());
        List<LegacyItemSchemaValidationSummary> summaries = report.getSchemaValidationSummaries();
        assertEquals(1, summaries.size());
        assertEquals(LegacyItemSchemaValidation.Status.VERIFIED, summaries.getFirst().getStatus());
        assertEquals(3L, summaries.getFirst().getCount());
    }

    @Test
    void exposesSchemaSummariesAsReadOnlySnapshot() {
        ItemDoctorReport report = new ItemDoctorReport(false);
        report.schemaMigrationCandidateFound(
                "Addon",
                "Migration",
                new LegacyItemSchemaCandidate("legacy-item", Readiness.READY, "Recognized old metadata"));

        List<LegacyItemSchemaCandidateSummary> snapshot = report.getSchemaMigrationCandidateSummaries();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(snapshot.getFirst()));

        report.schemaMigrationCandidateFound(
                "Addon",
                "Migration",
                new LegacyItemSchemaCandidate("legacy-item", Readiness.READY, "Recognized old metadata"));
        assertEquals(1L, snapshot.getFirst().getCount());
        assertEquals(2L, report.getSchemaMigrationCandidateSummaries().getFirst().getCount());
    }
}
