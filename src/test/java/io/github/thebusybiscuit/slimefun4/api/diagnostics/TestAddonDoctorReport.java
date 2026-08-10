package io.github.thebusybiscuit.slimefun4.api.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class TestAddonDoctorReport {

    @Test
    void testDefensiveDetailsCopy() {
        List<String> details = new ArrayList<>(List.of("stale node"));
        AddonDoctorReport report = new AddonDoctorReport("Networks", true, 10, 2, 2, 0, details);
        details.add("mutated");

        assertEquals("Networks", report.getAddonName());
        assertTrue(report.isRepairMode());
        assertEquals(10, report.getScannedEntries());
        assertEquals(2, report.getIssuesFound());
        assertEquals(2, report.getRepairedEntries());
        assertEquals(List.of("stale node"), report.getDetails());
        assertThrows(
                UnsupportedOperationException.class, () -> report.getDetails().add("x"));
    }

    @Test
    void testRejectsNegativeCounters() {
        assertThrows(
                IllegalArgumentException.class, () -> new AddonDoctorReport("Networks", false, -1, 0, 0, 0, List.of()));
    }

    @Test
    void testRejectsInvalidNamesAndDetails() {
        assertThrows(NullPointerException.class, () -> new AddonDoctorReport(null, false, 0, 0, 0, 0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new AddonDoctorReport("   ", false, 0, 0, 0, 0, List.of()));
        assertThrows(NullPointerException.class, () -> new AddonDoctorReport("Networks", false, 0, 0, 0, 0, null));
        assertThrows(
                NullPointerException.class,
                () -> new AddonDoctorReport("Networks", false, 0, 0, 0, 0, Arrays.asList("valid", null)));
    }
}
