package io.github.thebusybiscuit.slimefun4.core.services.stability;

import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestPersistedBackpackItemIdMigrationPlan {

    @Test
    void fingerprintIsStableAcrossEntryOrder() {
        var first = new PersistedBackpackItemIdMigrationPlan.Entry(
                "11111111-1111-1111-1111-111111111111", "0", "OLD_A", "NEW_A", "aa");
        var second = new PersistedBackpackItemIdMigrationPlan.Entry(
                "22222222-2222-2222-2222-222222222222", "4", "OLD_B", "NEW_B", "bb");

        var left = plan(List.of(first, second), 0L);
        var right = plan(List.of(second, first), 0L);

        Assertions.assertEquals(left.getFingerprint(), right.getFingerprint());
        Assertions.assertTrue(left.matchesFingerprint(left.getShortFingerprint().toUpperCase()));
    }

    @Test
    void cachedDeferredRowsAreBoundIntoFingerprint() {
        var entry = new PersistedBackpackItemIdMigrationPlan.Entry(
                "11111111-1111-1111-1111-111111111111", "0", "OLD", "NEW", "aa");

        Assertions.assertNotEquals(plan(List.of(entry), 0L).getFingerprint(), plan(List.of(entry), 1L).getFingerprint());
    }

    @Test
    void rejectsInvalidEntriesAndExpiredPlans() {
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new PersistedBackpackItemIdMigrationPlan.Entry("backpack", "0", "SAME", "SAME", "aa"));

        var plan = new PersistedBackpackItemIdMigrationPlan(
                1L, 100L, 10L, List.of(), 0L, 0L, 0L, 0L, 0L, 0L, 0L);
        Assertions.assertFalse(plan.isExpired(109L));
        Assertions.assertTrue(plan.isExpired(110L));
        Assertions.assertFalse(plan.matchesFingerprint("wrong"));
    }

    private PersistedBackpackItemIdMigrationPlan plan(
            List<PersistedBackpackItemIdMigrationPlan.Entry> entries, long cached) {
        return new PersistedBackpackItemIdMigrationPlan(
                1L, 100L, 60_000L, entries, entries.size() + cached, cached, 0L, 0L, 0L, 0L, 0L);
    }
}
