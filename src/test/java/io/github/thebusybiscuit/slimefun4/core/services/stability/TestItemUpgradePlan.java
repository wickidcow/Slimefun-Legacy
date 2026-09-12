package io.github.thebusybiscuit.slimefun4.core.services.stability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TestItemUpgradePlan {

    @Test
    void fingerprintIsStableAcrossMappingAndCandidateInsertionOrder() {
        Map<String, String> first = new LinkedHashMap<>();
        first.put("OLD_B", "NEW_B");
        first.put("OLD_A", "NEW_A");

        Map<String, String> second = new LinkedHashMap<>();
        second.put("OLD_A", "NEW_A");
        second.put("OLD_B", "NEW_B");

        ItemUpgradePlan firstPlan =
                ItemUpgradePlan.createForTest(1000L, 2000L, first, Set.of("OLD_B", "OLD_A"), 3L);
        ItemUpgradePlan secondPlan =
                ItemUpgradePlan.createForTest(1000L, 2000L, second, Set.of("OLD_A", "OLD_B"), 3L);

        assertEquals(firstPlan.getFingerprint(), secondPlan.getFingerprint());
        assertEquals(firstPlan.getShortFingerprint(), secondPlan.getShortFingerprint());
    }

    @Test
    void mappingSnapshotIsReadOnlyAndDetectsDrift() {
        ItemUpgradePlan plan = ItemUpgradePlan.createForTest(
                1000L, 2000L, Map.of("OLD_MACHINE", "NEW_MACHINE"), Set.of("OLD_MACHINE"), 0L);

        assertTrue(plan.matchesMappings(Map.of("OLD_MACHINE", "NEW_MACHINE")));
        assertFalse(plan.matchesMappings(Map.of("OLD_MACHINE", "DIFFERENT_MACHINE")));
        assertThrows(UnsupportedOperationException.class, () -> plan.getMappings().put("OLD", "NEW"));
        assertThrows(UnsupportedOperationException.class, () -> plan.getCandidateLegacyIds().add("OTHER"));
    }

    @Test
    void fingerprintIsBoundToGenerationCandidatesAndPresentationCount() {
        Map<String, String> mappings = Map.of("OLD", "NEW");
        ItemUpgradePlan base = ItemUpgradePlan.createForTest(1000L, 2000L, mappings, Set.of("OLD"), 1L);
        ItemUpgradePlan generationChanged =
                ItemUpgradePlan.createForTest(1001L, 2000L, mappings, Set.of("OLD"), 1L);
        ItemUpgradePlan candidatesChanged =
                ItemUpgradePlan.createForTest(1000L, 2000L, mappings, Set.of("OTHER"), 1L);
        ItemUpgradePlan presentationChanged =
                ItemUpgradePlan.createForTest(1000L, 2000L, mappings, Set.of("OLD"), 2L);

        assertNotEquals(base.getFingerprint(), generationChanged.getFingerprint());
        assertNotEquals(base.getFingerprint(), candidatesChanged.getFingerprint());
        assertNotEquals(base.getFingerprint(), presentationChanged.getFingerprint());
    }

    @Test
    void acceptsFullOrShortFingerprintAndHonorsExpiryBoundary() {
        ItemUpgradePlan plan =
                ItemUpgradePlan.createForTest(1000L, 2000L, Map.of("OLD", "NEW"), Set.of("OLD"), 0L);

        assertTrue(plan.matchesFingerprint(plan.getFingerprint()));
        assertTrue(plan.matchesFingerprint(plan.getShortFingerprint()));
        assertFalse(plan.matchesFingerprint("deadbeefdead"));
        assertFalse(plan.isExpired(1999L));
        assertTrue(plan.isExpired(2000L));
    }
}
