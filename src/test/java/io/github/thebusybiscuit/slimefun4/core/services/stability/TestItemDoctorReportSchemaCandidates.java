package io.github.thebusybiscuit.slimefun4.core.services.stability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        report.schemaMigrationCandidateFound("FluffyMachines", "Dolly migration", "DOLLY", candidate);
        report.schemaMigrationCandidateFound("FluffyMachines", "Dolly migration", "DOLLY", candidate);

        assertEquals(2L, report.getSchemaMigrationCandidates());
        List<LegacyItemSchemaCandidateSummary> summaries = report.getSchemaMigrationCandidateSummaries();
        assertEquals(1, summaries.size());
        assertEquals("FluffyMachines", summaries.getFirst().getProviderId());
        assertEquals("DOLLY", summaries.getFirst().getSlimefunId());
        assertEquals("legacy-dolly-binding", summaries.getFirst().getCandidateType());
        assertEquals(Readiness.VALIDATION_REQUIRED, summaries.getFirst().getReadiness());
        assertTrue(summaries.getFirst().hasItemLocalClaim());
        assertEquals(2L, summaries.getFirst().getCount());
    }

    @Test
    void keepsSameCandidateTypeSeparateAcrossCurrentItemIds() {
        ItemDoctorReport report = new ItemDoctorReport(false);
        LegacyItemSchemaCandidate candidate =
                new LegacyItemSchemaCandidate("legacy-storage", Readiness.READY, "Old metadata");

        report.schemaMigrationCandidateFound("Addon", "Migration", "CURRENT_A", candidate);
        report.schemaMigrationCandidateFound("Addon", "Migration", "CURRENT_B", candidate);

        List<LegacyItemSchemaCandidateSummary> summaries = report.getSchemaMigrationCandidateSummaries();
        assertEquals(2, summaries.size());
        assertEquals("CURRENT_A", summaries.get(0).getSlimefunId());
        assertEquals("CURRENT_B", summaries.get(1).getSlimefunId());
    }

    @Test
    void separatesClaimedAndClaimlessReadyCandidates() {
        ItemDoctorReport report = new ItemDoctorReport(false);
        LegacyItemSchemaCandidate claimless =
                new LegacyItemSchemaCandidate("legacy-item", Readiness.READY, "Recognized old metadata");
        LegacyItemSchemaCandidate claimed =
                new LegacyItemSchemaCandidate("legacy-item", Readiness.READY, "Recognized old metadata", "local-claim");

        report.schemaMigrationCandidateFound("Addon", "Migration", "CURRENT_ITEM", claimless);
        report.schemaMigrationCandidateFound("Addon", "Migration", "CURRENT_ITEM", claimed);
        report.schemaMigrationCandidateFound("Addon", "Migration", "CURRENT_ITEM", claimed);

        List<LegacyItemSchemaCandidateSummary> summaries = report.getSchemaMigrationCandidateSummaries();
        assertEquals(2, summaries.size());
        LegacyItemSchemaCandidateSummary withoutClaim =
                summaries.stream().filter(summary -> !summary.hasItemLocalClaim()).findFirst().orElseThrow();
        LegacyItemSchemaCandidateSummary withClaim =
                summaries.stream().filter(LegacyItemSchemaCandidateSummary::hasItemLocalClaim).findFirst().orElseThrow();
        assertFalse(withoutClaim.hasItemLocalClaim());
        assertEquals(1L, withoutClaim.getCount());
        assertTrue(withClaim.hasItemLocalClaim());
        assertEquals(2L, withClaim.getCount());
    }

    @Test
    void aggregatesValidationResultsByCandidateCount() {
        ItemDoctorReport report = new ItemDoctorReport(false);
        report.schemaValidationFound(
                "FluffyMachines",
                "Dolly migration",
                "DOLLY",
                "legacy-dolly-binding",
                new LegacyItemSchemaValidation(
                        LegacyItemSchemaValidation.Status.VERIFIED,
                        "Backing state matches."),
                3L);

        assertEquals(3L, report.getSchemaValidatedCandidates());
        List<LegacyItemSchemaValidationSummary> summaries = report.getSchemaValidationSummaries();
        assertEquals(1, summaries.size());
        assertEquals("DOLLY", summaries.getFirst().getSlimefunId());
        assertEquals(LegacyItemSchemaValidation.Status.VERIFIED, summaries.getFirst().getStatus());
        assertEquals(3L, summaries.getFirst().getCount());
    }

    @Test
    void exposesSchemaSummariesAsReadOnlySnapshot() {
        ItemDoctorReport report = new ItemDoctorReport(false);
        report.schemaMigrationCandidateFound(
                "Addon",
                "Migration",
                "CURRENT_ITEM",
                new LegacyItemSchemaCandidate("legacy-item", Readiness.READY, "Recognized old metadata"));

        List<LegacyItemSchemaCandidateSummary> snapshot = report.getSchemaMigrationCandidateSummaries();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(snapshot.getFirst()));

        report.schemaMigrationCandidateFound(
                "Addon",
                "Migration",
                "CURRENT_ITEM",
                new LegacyItemSchemaCandidate("legacy-item", Readiness.READY, "Recognized old metadata"));
        assertEquals(1L, snapshot.getFirst().getCount());
        assertEquals(2L, report.getSchemaMigrationCandidateSummaries().getFirst().getCount());
    }
}
