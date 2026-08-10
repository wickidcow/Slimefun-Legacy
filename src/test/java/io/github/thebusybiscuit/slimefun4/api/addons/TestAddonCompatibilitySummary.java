package io.github.thebusybiscuit.slimefun4.api.addons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TestAddonCompatibilitySummary {

    @Test
    void testSummaryCountsEveryStatus() {
        List<AddonCompatibilityResult> results = List.of(
                result("A", AddonCompatibilityStatus.COMPATIBLE),
                result("B", AddonCompatibilityStatus.WARNING),
                result("C", AddonCompatibilityStatus.UNDECLARED),
                result("D", AddonCompatibilityStatus.DISABLED),
                result("E", AddonCompatibilityStatus.INCOMPATIBLE));

        AddonCompatibilitySummary summary = AddonCompatibilitySummary.from(results);
        assertEquals(5, summary.getTotal());
        for (AddonCompatibilityStatus status : AddonCompatibilityStatus.values()) {
            assertEquals(1, summary.getCount(status));
        }
        assertTrue(summary.hasProblems());
        assertThrows(
                UnsupportedOperationException.class,
                () -> summary.getCounts().put(AddonCompatibilityStatus.COMPATIBLE, 99));
    }

    @Test
    void testEmptySummaryHasNoProblems() {
        AddonCompatibilitySummary summary = AddonCompatibilitySummary.from(List.of());
        assertEquals(0, summary.getTotal());
        assertFalse(summary.hasProblems());
    }

    private static AddonCompatibilityResult result(String name, AddonCompatibilityStatus status) {
        return new AddonCompatibilityResult(name, "1.0", status, AddonCompatibilitySource.NONE, null, List.of());
    }
}
