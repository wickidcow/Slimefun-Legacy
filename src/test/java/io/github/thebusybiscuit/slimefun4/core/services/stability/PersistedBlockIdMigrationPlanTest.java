package io.github.thebusybiscuit.slimefun4.core.services.stability;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.BlockIdStorageMaintenance;
import java.util.List;
import org.junit.jupiter.api.Test;

class PersistedBlockIdMigrationPlanTest {

    @Test
    void fingerprintBindsLocationOldAndNewIds() {
        long now = 1_000L;
        var original = new PersistedBlockIdMigrationPlan(
                1L,
                now,
                10_000L,
                List.of(new PersistedBlockIdMigrationPlan.Entry("world;1;2;3", "OLD_ID", "CURRENT_ID")),
                1L,
                0L,
                0L,
                0L);
        var changedOld = new PersistedBlockIdMigrationPlan(
                1L,
                now,
                10_000L,
                List.of(new PersistedBlockIdMigrationPlan.Entry("world;1;2;3", "OTHER_OLD", "CURRENT_ID")),
                1L,
                0L,
                0L,
                0L);
        var changedLocation = new PersistedBlockIdMigrationPlan(
                1L,
                now,
                10_000L,
                List.of(new PersistedBlockIdMigrationPlan.Entry("world;9;9;9", "OLD_ID", "CURRENT_ID")),
                1L,
                0L,
                0L,
                0L);

        assertNotEquals(original.getFingerprint(), changedOld.getFingerprint());
        assertNotEquals(original.getFingerprint(), changedLocation.getFingerprint());
        assertTrue(original.matchesFingerprint(original.getShortFingerprint()));
    }

    @Test
    void fingerprintIsOrderIndependentAndPlanExpires() {
        var first = new PersistedBlockIdMigrationPlan.Entry("world;1;2;3", "OLD_A", "NEW_A");
        var second = new PersistedBlockIdMigrationPlan.Entry("world;4;5;6", "OLD_B", "NEW_B");
        var left = new PersistedBlockIdMigrationPlan(2L, 5_000L, 500L, List.of(first, second), 2L, 0L, 0L, 0L);
        var right = new PersistedBlockIdMigrationPlan(2L, 5_000L, 500L, List.of(second, first), 2L, 0L, 0L, 0L);

        assertTrue(left.matchesFingerprint(right.getFingerprint()));
        assertFalse(left.isExpired(5_499L));
        assertTrue(left.isExpired(5_500L));
    }

    @Test
    void fingerprintSeparatesNormalAndUniversalStorageScopes() {
        var normal = new PersistedBlockIdMigrationPlan(
                3L,
                9_000L,
                1_000L,
                List.of(new PersistedBlockIdMigrationPlan.Entry(
                        BlockIdStorageMaintenance.BLOCK_SCOPE, "same-key", "OLD_ID", "NEW_ID")),
                1L,
                0L,
                0L,
                0L);
        var universal = new PersistedBlockIdMigrationPlan(
                3L,
                9_000L,
                1_000L,
                List.of(new PersistedBlockIdMigrationPlan.Entry(
                        BlockIdStorageMaintenance.UNIVERSAL_SCOPE, "same-key", "OLD_ID", "NEW_ID")),
                1L,
                0L,
                0L,
                0L);

        assertNotEquals(normal.getFingerprint(), universal.getFingerprint());
    }
}
