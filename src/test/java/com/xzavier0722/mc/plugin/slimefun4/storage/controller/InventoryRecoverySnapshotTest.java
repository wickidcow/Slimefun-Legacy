package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import static org.junit.jupiter.api.Assertions.*;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.BlockStorageMigration;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.InventoryRecoverySnapshot.Kind;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InventoryRecoverySnapshotTest {
    @Test
    void emptyReportIsDetachedAndHasOneEmptyPage() {
        var snapshot = new InventoryRecoverySnapshot.Collector().build();
        assertFalse(snapshot.hasEntries());
        assertEquals(0, snapshot.totalEntries());
        assertEquals(1, snapshot.pageCount());
        assertTrue(snapshot.page(1).isEmpty());
        for (Kind kind : Kind.values()) assertEquals(0, snapshot.count(kind));
    }

    @Test
    void distinguishesGuardsFromMigrationEntriesRatherThanClaimingUniqueInventories() {
        var builder = new InventoryRecoverySnapshot.Collector();
        for (Kind kind : Kind.values()) builder.add(kind, "unchanged-key", "same-destination");
        var snapshot = builder.build();
        assertEquals(4, snapshot.totalEntries());
        for (Kind kind : Kind.values()) assertEquals(1, snapshot.count(kind));
    }

    @Test
    void capturesOnlyTwoHundredDetailsButCountsEveryObservedEntry() {
        var builder = new InventoryRecoverySnapshot.Collector();
        for (int i = 5000; i >= 0; i--) builder.add(Kind.BLOCK_LOAD, String.format("world;%05d:64:0", i), null);
        var snapshot = builder.build();
        assertEquals(5001, snapshot.totalEntries());
        assertEquals(200, snapshot.entries().size());
        assertEquals("world;00000:64:0", snapshot.entries().getFirst().owner());
        assertEquals("world;00199:64:0", snapshot.entries().getLast().owner());
        assertEquals(10, snapshot.pageCount());
    }

    @Test
    void samplingIsDeterministicRegardlessOfSetTraversalOrder() {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 250; i++) keys.add("world;" + i + ":64:0");
        var first = new InventoryRecoverySnapshot.Collector();
        keys.forEach(key -> first.add(Kind.BLOCK_LOAD, key, null));
        Collections.reverse(keys);
        var second = new InventoryRecoverySnapshot.Collector();
        keys.forEach(key -> second.add(Kind.BLOCK_LOAD, key, null));
        assertEquals(first.build().entries(), second.build().entries());
    }

    @Test
    void snapshotsCannotMutateGuardsOrBeChangedByLaterCollection() {
        var builder = new InventoryRecoverySnapshot.Collector();
        builder.add(Kind.BACKPACK_LOAD, "original-id", null);
        var before = builder.build();
        builder.add(Kind.BACKPACK_LOAD, "later-id", null);
        assertEquals(1, before.totalEntries());
        assertEquals("original-id", before.entries().getFirst().owner());
        assertThrows(UnsupportedOperationException.class, () -> before.entries().clear());
        assertThrows(UnsupportedOperationException.class, () -> before.page(1).clear());
    }

    @Test
    void paginationNeverDuplicatesOrLosesCapturedEntries() {
        var builder = new InventoryRecoverySnapshot.Collector();
        for (int i = 0; i < 43; i++) builder.add(Kind.BACKPACK_LOAD, "id-" + i, null);
        var snapshot = builder.build();
        List<InventoryRecoverySnapshot.Entry> observed = new ArrayList<>();
        for (int page = 1; page <= snapshot.pageCount(); page++) observed.addAll(snapshot.page(page));
        assertEquals(snapshot.entries(), observed);
        assertEquals(3, snapshot.page(3).size());
    }

    @Test
    void invalidPagesAreRejectedWithoutAccessingInventoryState() {
        var snapshot = new InventoryRecoverySnapshot.Collector().build();
        for (int page : new int[] {-1, 0, 2, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> snapshot.page(page));
        }
    }

    @Test
    void combinedControllersRetainAllCountsAndOneGlobalBound() {
        var blocks = new InventoryRecoverySnapshot.Collector();
        var profiles = new InventoryRecoverySnapshot.Collector();
        for (int i = 0; i < 300; i++) {
            blocks.add(Kind.BLOCK_LOAD, "block-" + i, null);
            profiles.add(Kind.BACKPACK_LOAD, "backpack-" + i, null);
        }
        var combined = InventoryRecoverySnapshot.combine(blocks.build(), profiles.build());
        assertEquals(600, combined.totalEntries());
        assertEquals(300, combined.count(Kind.BLOCK_LOAD));
        assertEquals(300, combined.count(Kind.BACKPACK_LOAD));
        assertEquals(200, combined.entries().size());
    }

    @Test
    void rawIdentityIsPreservedEvenWhenDisplayWillNeedEscaping() {
        String owner = "old &a world;10:64:-20\nunchanged";
        var builder = new InventoryRecoverySnapshot.Collector();
        builder.add(Kind.BLOCK_LOAD, owner, null);
        assertEquals(owner, builder.build().entries().getFirst().owner());
    }

    @Test
    @SuppressWarnings("unchecked")
    void blockControllerObservesHoldsWithoutLoadingStorageOrClearingThem() throws Exception {
        var controller = new BlockDataController(); // No adapter or server: any storage access would fail.
        Set<String> guards = (Set<String>)
                field(BlockDataController.class, "incompleteInventoryLoads").get(controller);
        String uuid = "11111111-2222-3333-4444-555555555555";
        guards.add("old_world;10:64:-20");
        guards.add(uuid);
        var before = Set.copyOf(guards);
        var snapshot = controller.getInventoryRecoverySnapshot();
        assertEquals(1, snapshot.count(Kind.BLOCK_LOAD));
        assertEquals(1, snapshot.count(Kind.UNIVERSAL_LOAD));
        assertEquals(before, guards);
        guards.clear();
        assertEquals(2, snapshot.totalEntries());
        assertFalse(controller.getInventoryRecoverySnapshot().hasEntries());
    }

    @Test
    @SuppressWarnings("unchecked")
    void profileControllerObservesBackpackIdsWithoutLoadingOrRepairingThem() throws Exception {
        Field active = field(BackpackCache.class, "activeCache");
        Object previous = active.get(null);
        try {
            var controller = new ProfileDataController();
            Set<String> guards = (Set<String>) field(ProfileDataController.class, "incompleteInventoryLoads")
                    .get(controller);
            guards.add("11111111-2222-3333-4444-555555555555");
            var snapshot = controller.getInventoryRecoverySnapshot();
            assertEquals(1, snapshot.count(Kind.BACKPACK_LOAD));
            assertEquals(1, guards.size());
            assertEquals(0, controller.getPendingBackpackSaveChainCount());
            assertEquals(0, controller.getUncertainBackpackBaselineCount());
        } finally {
            active.set(null, previous);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void pendingMigrationReportsTheExistingDestinationWithoutChangingAcknowledgement() throws Exception {
        var controller = new BlockDataController();
        var plan = new BlockStorageMigration(
                "old_world;10:64:-20",
                "old_world;0:-2",
                "OLD_MACHINE",
                UUID.fromString("11111111-2222-3333-4444-555555555555"),
                "old-location",
                Map.of(),
                Map.of());
        Class<?> type = Class.forName(BlockDataController.class.getName() + "$PendingUniversalMigration");
        var constructor = type.getDeclaredConstructor(BlockStorageMigration.class);
        constructor.setAccessible(true);
        Object pending = constructor.newInstance(plan);
        Map<String, Object> migrations = (Map<String, Object>)
                field(BlockDataController.class, "pendingUniversalMigrations").get(controller);
        migrations.put(plan.location(), pending);
        field(type, "committed").setBoolean(pending, true);
        var snapshot = controller.getInventoryRecoverySnapshot();
        assertEquals(1, snapshot.count(Kind.UNIVERSAL_MIGRATION));
        assertEquals(
                plan.destination().toString(), snapshot.entries().getFirst().destination());
        assertSame(pending, migrations.get(plan.location()));
        assertTrue(field(type, "committed").getBoolean(pending));
        assertFalse(field(type, "activated").getBoolean(pending));
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
