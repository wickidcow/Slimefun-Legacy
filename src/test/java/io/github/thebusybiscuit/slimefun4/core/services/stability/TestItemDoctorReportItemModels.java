package io.github.thebusybiscuit.slimefun4.core.services.stability;

import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestItemDoctorReportItemModels {

    @Test
    void testItemModelCandidateCountsAndRepairs() {
        ItemDoctorReport report = new ItemDoctorReport(true);

        report.itemModelCandidateFound("STEEL_INGOT");
        report.itemModelCandidateFound("STEEL_INGOT");
        report.itemModelCandidateFound("COPPER_INGOT");
        report.itemModelRepaired();
        report.itemModelRepaired();

        Assertions.assertEquals(3L, report.getItemModelCandidates());
        Assertions.assertEquals(2L, report.getItemModelRepairs());
        Assertions.assertEquals(
                Map.of("COPPER_INGOT", 1L, "STEEL_INGOT", 2L),
                report.getItemModelCandidateCounts());
    }
}
