package io.github.thebusybiscuit.slimefun4.core.services.stability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TestItemDoctorReportLegacyCandidates {

    @Test
    void countsEveryDeclaredLegacyCandidateById() {
        ItemDoctorReport report = new ItemDoctorReport(false);

        report.legacyMigrationCandidateFound("OLD_MACHINE");
        report.legacyMigrationCandidateFound("OLD_MACHINE");
        report.legacyMigrationCandidateFound("OLD_TOOL");

        assertEquals(3L, report.getLegacyMigrationCandidates());
        assertEquals(Map.of("OLD_MACHINE", 2L, "OLD_TOOL", 1L), report.getLegacyMigrationCandidateCounts());
    }

    @Test
    void exposesCandidateCountsAsReadOnlySnapshot() {
        ItemDoctorReport report = new ItemDoctorReport(false);
        report.legacyMigrationCandidateFound("OLD_MACHINE");

        Map<String, Long> snapshot = report.getLegacyMigrationCandidateCounts();
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put("OTHER", 1L));

        report.legacyMigrationCandidateFound("OLD_MACHINE");
        assertEquals(1L, snapshot.get("OLD_MACHINE"));
        assertEquals(2L, report.getLegacyMigrationCandidateCounts().get("OLD_MACHINE"));
    }
}
