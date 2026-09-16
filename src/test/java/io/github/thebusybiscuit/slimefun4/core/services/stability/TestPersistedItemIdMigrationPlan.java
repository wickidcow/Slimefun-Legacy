package io.github.thebusybiscuit.slimefun4.core.services.stability;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestPersistedItemIdMigrationPlan {

    @Test
    void testFingerprintAuthorizesExactPlan() {
        PersistedItemIdMigrationPlan plan = plan(1L, "OLD_ITEM", "CURRENT_ITEM", "hash-a");

        Assertions.assertEquals(12, plan.getShortFingerprint().length());
        Assertions.assertTrue(plan.matchesFingerprint(plan.getFingerprint()));
        Assertions.assertTrue(plan.matchesFingerprint(plan.getShortFingerprint().toUpperCase()));
        Assertions.assertFalse(plan.matchesFingerprint("deadbeef"));
    }

    @Test
    void testFingerprintChangesWhenMappingOrStoredValueChanges() {
        PersistedItemIdMigrationPlan baseline = plan(1L, "OLD_ITEM", "CURRENT_ITEM", "hash-a");
        PersistedItemIdMigrationPlan mappingChanged = plan(1L, "OLD_ITEM", "OTHER_ITEM", "hash-a");
        PersistedItemIdMigrationPlan valueChanged = plan(1L, "OLD_ITEM", "CURRENT_ITEM", "hash-b");
        PersistedItemIdMigrationPlan generationChanged = plan(2L, "OLD_ITEM", "CURRENT_ITEM", "hash-a");

        Assertions.assertNotEquals(baseline.getFingerprint(), mappingChanged.getFingerprint());
        Assertions.assertNotEquals(baseline.getFingerprint(), valueChanged.getFingerprint());
        Assertions.assertNotEquals(baseline.getFingerprint(), generationChanged.getFingerprint());
    }

    @Test
    void testPlanExpiryAndScopeValidation() {
        PersistedItemIdMigrationPlan plan = plan(1L, "OLD_ITEM", "CURRENT_ITEM", "hash-a");
        Assertions.assertFalse(plan.isExpired(1_999L));
        Assertions.assertTrue(plan.isExpired(2_000L));

        Assertions.assertThrows(IllegalArgumentException.class, () -> new PersistedItemIdMigrationPlan.Entry(
                DataScope.BLOCK_RECORD, "owner", "slot", "OLD_ITEM", "CURRENT_ITEM", "hash"));
    }

    private PersistedItemIdMigrationPlan plan(long generation, String legacyId, String canonicalId, String hash) {
        var entry = new PersistedItemIdMigrationPlan.Entry(
                DataScope.BLOCK_INVENTORY, "world;1;2;3", "4", legacyId, canonicalId, hash);
        return new PersistedItemIdMigrationPlan(
                generation,
                1_000L,
                1_000L,
                List.of(entry),
                1L,
                0L,
                0L,
                0L,
                0L,
                0L);
    }
}
