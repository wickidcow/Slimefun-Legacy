package io.github.thebusybiscuit.slimefun4.core.services.stability;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestItemDoctorReport {

    @Test
    void identitySummaryIsBoundedAndNeverImpliedRepair() {
        ItemDoctorReport report = new ItemDoctorReport(false);
        report.identityCompared("SMALL_URANIUM", HistoricalItemIdentityScanner.Result.MATCH);
        report.identityCompared("CARBONADO", HistoricalItemIdentityScanner.Result.PRESENTATION_ONLY_CANDIDATE);
        report.identityCompared("CARBONADO", HistoricalItemIdentityScanner.Result.PRESENTATION_ONLY_CANDIDATE);
        report.identityCompared("OTHER", HistoricalItemIdentityScanner.Result.OTHER_DIFFERENCE);
        report.identityCompared("UNKNOWN", HistoricalItemIdentityScanner.Result.TEMPLATE_UNAVAILABLE);
        Assertions.assertEquals(0L, report.getIdentityObserved());
        Assertions.assertEquals(5L, report.getIdentityCompared());
        Assertions.assertEquals(1L, report.getIdentityTemplateMatches());
        Assertions.assertEquals(2L, report.getIdentityPresentationCandidates());
        Assertions.assertEquals(1L, report.getIdentityOtherDifferences());
        Assertions.assertEquals(1L, report.getIdentityUnavailable());
        Assertions.assertEquals(java.util.List.of("CARBONADO"), report.getIdentityPresentationCandidateIds());
        Assertions.assertEquals(0L, report.getRepairedStacks());
    }

    @Test
    void fullIdentityProbeBudgetIsReportedAsDeferredNotSilentlyPassed() {
        ItemDoctorReport report = new ItemDoctorReport(false);
        for (int i = 0; i < 20_000; i++) {
            Assertions.assertTrue(report.reserveIdentityInspection());
            report.identityCompared("TEST_ITEM", HistoricalItemIdentityScanner.Result.MATCH);
        }
        Assertions.assertFalse(report.reserveIdentityInspection());
        Assertions.assertEquals(20_001L, report.getIdentityObserved());
        Assertions.assertEquals(20_000L, report.getIdentityCompared());
        Assertions.assertEquals(1L, report.getIdentityDeferred());
        Assertions.assertEquals(0L, report.getIdentityPresentationCandidates());
    }

    @Test
    void recordsProgressAndLimitsDuplicateSamples() {
        ItemDoctorReport report = new ItemDoctorReport(true);
        report.inventoryScanned();
        report.backpackScanned();
        report.stackScanned();
        report.slimefunStackFound();
        report.cjkStackFound();
        report.stackRepaired();
        report.unknownIdFound("UNKNOWN_ITEM");
        report.unknownIdFound("UNKNOWN_ITEM");
        report.unresolvedTemplateFound("CHINESE_TEMPLATE");
        report.failure();
        report.markComplete();

        Assertions.assertTrue(report.isRepairMode());
        Assertions.assertEquals("repair", report.getModeName());
        Assertions.assertTrue(report.isComplete());
        Assertions.assertEquals(1, report.getInventories());
        Assertions.assertEquals(1, report.getBackpacks());
        Assertions.assertEquals(1, report.getScannedStacks());
        Assertions.assertEquals(1, report.getSlimefunStacks());
        Assertions.assertEquals(1, report.getCjkStacks());
        Assertions.assertEquals(1, report.getRepairedStacks());
        Assertions.assertEquals(2, report.getUnknownIds());
        Assertions.assertEquals(1, report.getUnresolvedTemplates());
        Assertions.assertEquals(1, report.getFailures());
        Assertions.assertEquals(1, report.getUnknownIdSamples().size());
        Assertions.assertEquals("UNKNOWN_ITEM", report.getUnknownIdSamples().get(0));
        Assertions.assertEquals(
                "CHINESE_TEMPLATE", report.getUnresolvedTemplateSamples().get(0));
        Assertions.assertTrue(report.getDurationMillis() >= 0);
    }
}
