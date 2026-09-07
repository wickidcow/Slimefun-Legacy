package io.github.thebusybiscuit.slimefun4.core.services.stability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TestLegacyItemMigrationPlan {

    @Test
    void fingerprintIsStableAcrossMappingInsertionOrder() {
        Map<String, String> first = new LinkedHashMap<>();
        first.put("OLD_B", "NEW_B");
        first.put("OLD_A", "NEW_A");

        Map<String, String> second = new LinkedHashMap<>();
        second.put("OLD_A", "NEW_A");
        second.put("OLD_B", "NEW_B");

        LegacyItemMigrationPlan firstPlan = new LegacyItemMigrationPlan("InfinityExpansion2", 1000L, 2000L, first);
        LegacyItemMigrationPlan secondPlan = new LegacyItemMigrationPlan("InfinityExpansion2", 1000L, 2000L, second);

        assertEquals(firstPlan.getFingerprint(), secondPlan.getFingerprint());
        assertEquals(firstPlan.getShortFingerprint(), secondPlan.getShortFingerprint());
    }

    @Test
    void mappingSnapshotIsReadOnlyAndDetectsDrift() {
        LegacyItemMigrationPlan plan = new LegacyItemMigrationPlan(
                "InfinityExpansion2", 1000L, 2000L, Map.of("OLD_MACHINE", "IE_NEW_MACHINE"));

        assertTrue(plan.matchesMappings(Map.of("OLD_MACHINE", "IE_NEW_MACHINE")));
        assertFalse(plan.matchesMappings(Map.of("OLD_MACHINE", "IE_DIFFERENT_MACHINE")));
        assertThrows(UnsupportedOperationException.class, () -> plan.getMappings().put("OLD", "NEW"));
    }

    @Test
    void fingerprintIsBoundToProviderAndGeneration() {
        Map<String, String> mappings = Map.of("OLD", "NEW");
        LegacyItemMigrationPlan base = new LegacyItemMigrationPlan("AddonA", 1000L, 2000L, mappings);
        LegacyItemMigrationPlan differentProvider = new LegacyItemMigrationPlan("AddonB", 1000L, 2000L, mappings);
        LegacyItemMigrationPlan differentGeneration = new LegacyItemMigrationPlan("AddonA", 1001L, 2000L, mappings);

        assertNotEquals(base.getFingerprint(), differentProvider.getFingerprint());
        assertNotEquals(base.getFingerprint(), differentGeneration.getFingerprint());
    }

    @Test
    void acceptsFullOrShortFingerprintAndHonorsExpiryBoundary() {
        LegacyItemMigrationPlan plan =
                new LegacyItemMigrationPlan("InfinityExpansion2", 1000L, 2000L, Map.of("OLD", "NEW"));

        assertTrue(plan.matchesFingerprint(plan.getFingerprint()));
        assertTrue(plan.matchesFingerprint(plan.getShortFingerprint()));
        assertFalse(plan.matchesFingerprint("deadbeefdead"));
        assertFalse(plan.isExpired(1999L));
        assertTrue(plan.isExpired(2000L));
    }
}
