package io.github.thebusybiscuit.slimefun4.core.services.stability;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import java.util.List;
import org.junit.jupiter.api.Test;

class PersistedItemFormatMigrationPlanTest {

    @Test
    void fingerprintBindsScopeOwnerSlotAndStoredValue() {
        long now = 1_000L;
        var originalEntry = new PersistedItemFormatMigrationPlan.Entry(
                DataScope.BLOCK_INVENTORY, "world;1;2;3", "4", "aabbcc");
        var original = new PersistedItemFormatMigrationPlan(
                1L, now, 10_000L, List.of(originalEntry), 5L, 4L, 0L, List.of());
        var changedValue = new PersistedItemFormatMigrationPlan(
                1L,
                now,
                10_000L,
                List.of(new PersistedItemFormatMigrationPlan.Entry(
                        DataScope.BLOCK_INVENTORY, "world;1;2;3", "4", "ddeeff")),
                5L,
                4L,
                0L,
                List.of());
        var changedSlot = new PersistedItemFormatMigrationPlan(
                1L,
                now,
                10_000L,
                List.of(new PersistedItemFormatMigrationPlan.Entry(
                        DataScope.BLOCK_INVENTORY, "world;1;2;3", "5", "aabbcc")),
                5L,
                4L,
                0L,
                List.of());

        assertNotEquals(original.getFingerprint(), changedValue.getFingerprint());
        assertNotEquals(original.getFingerprint(), changedSlot.getFingerprint());
        assertTrue(original.matchesFingerprint(original.getShortFingerprint()));
    }

    @Test
    void fingerprintIsOrderIndependentAndPlanExpires() {
        var first = new PersistedItemFormatMigrationPlan.Entry(
                DataScope.BLOCK_INVENTORY, "world;1;2;3", "4", "aaa");
        var second = new PersistedItemFormatMigrationPlan.Entry(
                DataScope.UNIVERSAL_INVENTORY, "123e4567-e89b-12d3-a456-426614174000", "9", "bbb");
        var left = new PersistedItemFormatMigrationPlan(
                2L, 5_000L, 500L, List.of(first, second), 8L, 6L, 0L, List.of());
        var right = new PersistedItemFormatMigrationPlan(
                2L, 5_000L, 500L, List.of(second, first), 8L, 6L, 0L, List.of());

        assertTrue(left.matchesFingerprint(right.getFingerprint()));
        assertFalse(left.isExpired(5_499L));
        assertTrue(left.isExpired(5_500L));
    }

    @Test
    void storedValueHashIncludesRepresentationType() {
        byte[] bytes = "legacy".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String text = "legacy";

        String binaryHash = PersistedItemFormatMigrationPlan.hashStoredValue(true, bytes, null);
        String textHash = PersistedItemFormatMigrationPlan.hashStoredValue(false, null, text);

        assertNotEquals(binaryHash, textHash);
    }
}
