package com.xzavier0722.mc.plugin.slimefun4.storage.util;

import static org.junit.jupiter.api.Assertions.*;

import io.github.bakedlibs.dough.collections.Pair;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/** Snapshot ownership and existing comparison rules, not an uncontrolled concurrency test. */
class InvSnapshotOwnershipTest {
    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void listConstructorDetachesTheOriginalItems() {
        var item = richItem(37);
        var snapshot = new InvSnapshot(List.of(new Pair<>(item, 37)));
        rename(item, "Changed outside the snapshot");
        assertEquals(Set.of(0), snapshot.getChangedSlots(new ItemStack[] {item}));
        assertEquals(
                Component.text("Original owner name"),
                snapshot.getSnapshot().getFirst().getFirstValue().getItemMeta().displayName());
    }

    @Test
    void listConstructorDetachesMutablePairFields() {
        var pair = new Pair<>(richItem(37), 37);
        var snapshot = new InvSnapshot(List.of(pair));
        pair.setFirstValue(richItem(12));
        pair.setSecondValue(12);
        assertEquals(Set.of(0), snapshot.getChangedSlots(new ItemStack[] {richItem(12)}));
        assertEquals(37, snapshot.getSnapshot().getFirst().getSecondValue());
    }

    @Test
    void listStructureRemainsDetached() {
        var source = new ArrayList<Pair<ItemStack, Integer>>();
        source.add(new Pair<>(richItem(37), 37));
        var snapshot = new InvSnapshot(source);
        source.clear();
        assertEquals(1, snapshot.getSnapshot().size());
        assertTrue(snapshot.getChangedSlots(new ItemStack[] {richItem(37)}).isEmpty());
    }

    @Test
    void exportedAmountCannotAcknowledgeAnUnsavedChange() {
        var snapshot = new InvSnapshot(new ItemStack[] {richItem(37)});
        var exported = snapshot.getSnapshot().getFirst();
        exported.getFirstValue().setAmount(12);
        exported.setSecondValue(12);
        assertEquals(Set.of(0), snapshot.getChangedSlots(new ItemStack[] {richItem(12)}));
        assertEquals(37, snapshot.getSnapshot().getFirst().getFirstValue().getAmount());
    }

    @Test
    void exportedMetadataCannotAcknowledgeAnUnsavedChange() {
        var snapshot = new InvSnapshot(new ItemStack[] {richItem(37)});
        var item = richItem(37);
        rename(item, "Not yet saved");
        snapshot.getSnapshot().getFirst().setFirstValue(item.clone());
        assertEquals(Set.of(0), InvStorageUtils.getChangedSlots(snapshot, new ItemStack[] {item}));
    }

    @Test
    void separateExportsDoNotSharePairsOrItemInstances() {
        var snapshot = new InvSnapshot(new ItemStack[] {richItem(37), null});
        var first = snapshot.getSnapshot();
        var second = snapshot.getSnapshot();
        assertNotSame(first.get(0), second.get(0));
        assertNotSame(first.get(0).getFirstValue(), second.get(0).getFirstValue());
        assertNotSame(first.get(1), second.get(1));
        assertEquals(first.get(0).getSecondValue(), second.get(0).getSecondValue());
        assertEquals(
                first.get(0).getFirstValue().getType(),
                second.get(0).getFirstValue().getType());
    }

    @Test
    void arrayAndInventoryInputsRemainDetached() {
        var item = richItem(37);
        var source = new ItemStack[] {item};
        var arraySnapshot = new InvSnapshot(source);
        source[0] = null;
        item.setAmount(1);
        assertTrue(arraySnapshot.getChangedSlots(new ItemStack[] {richItem(37)}).isEmpty());
        var inventory = server.createInventory(null, 9);
        inventory.setItem(0, richItem(37));
        var inventorySnapshot = new InvSnapshot(inventory);
        inventory.setItem(0, richItem(12));
        assertEquals(Set.of(0), inventorySnapshot.getChangedSlots(inventory));
    }

    @Test
    void publicUtilityEmptyPairsCannotPoisonOtherSlotsOrComparisons() {
        var first = InvStorageUtils.getInvSnapshot(new ItemStack[2]);
        var second = InvStorageUtils.getInvSnapshot(new ItemStack[1]);
        var pair = first.getFirst();
        try {
            pair.setFirstValue(new ItemStack(Material.DIAMOND));
            pair.setSecondValue(1);
            assertNull(first.get(1).getFirstValue());
            assertNull(second.getFirst().getFirstValue());
            assertEquals(
                    Set.of(1),
                    InvStorageUtils.getChangedSlots(
                            List.of(new Pair<ItemStack, Integer>(null, 0)),
                            new ItemStack[] {null, new ItemStack(Material.DIAMOND)}));
        } finally {
            pair.setFirstValue(null);
            pair.setSecondValue(0);
        }
    }

    @Test
    void exportedEmptyEntryCannotChangeItsSnapshot() {
        var snapshot = new InvSnapshot(new ItemStack[1]);
        var pair = snapshot.getSnapshot().getFirst();
        try {
            pair.setFirstValue(new ItemStack(Material.DIAMOND));
            pair.setSecondValue(1);
            assertTrue(snapshot.getChangedSlots(new ItemStack[1]).isEmpty());
        } finally {
            pair.setFirstValue(null);
            pair.setSecondValue(0);
        }
    }

    @Test
    void typedOldItemDataSurvivesCopyingWithoutRegistrationOrNormalization() {
        var item = richItem(37);
        var meta = item.getItemMeta();
        var original = meta.getPersistentDataContainer();
        original.set(key("old:bytes"), PersistentDataType.BYTE_ARRAY, new byte[] {0, -1, 4});
        var nested = original.getAdapterContext().newPersistentDataContainer();
        nested.set(key("old:owner"), PersistentDataType.STRING, "owner");
        original.set(key("old:nested"), PersistentDataType.TAG_CONTAINER, nested);
        item.setItemMeta(meta);
        var copy = new InvSnapshot(List.of(new Pair<>(item, 37)))
                .getSnapshot()
                .getFirst()
                .getFirstValue();
        var data = copy.getItemMeta().getPersistentDataContainer();
        assertEquals(item.getType(), copy.getType());
        assertEquals(37, copy.getAmount());
        assertEquals(item.getItemMeta().displayName(), copy.getItemMeta().displayName());
        assertEquals(item.getItemMeta().lore(), copy.getItemMeta().lore());
        assertEquals("UNREGISTERED_OLD_ID", data.get(key("slimefun:slimefun_item"), PersistentDataType.STRING));
        assertEquals(9_007_199_254_740_993L, data.get(key("old:count"), PersistentDataType.LONG));
        assertEquals(
                Float.floatToRawIntBits(123.4567F),
                Float.floatToRawIntBits(data.get(key("slimefun:item_charge"), PersistentDataType.FLOAT)));
        assertArrayEquals(new byte[] {0, -1, 4}, data.get(key("old:bytes"), PersistentDataType.BYTE_ARRAY));
        assertEquals(
                "owner",
                data.get(key("old:nested"), PersistentDataType.TAG_CONTAINER)
                        .get(key("old:owner"), PersistentDataType.STRING));
    }

    @Test
    void recordedAmountsAreCopiedRatherThanRecomputed() {
        var snapshot = new InvSnapshot(List.of(new Pair<>(richItem(37), 12), new Pair<>(null, 7)));
        var exported = snapshot.getSnapshot();
        assertEquals(37, exported.get(0).getFirstValue().getAmount());
        assertEquals(12, exported.get(0).getSecondValue());
        assertNull(exported.get(1).getFirstValue());
        assertEquals(7, exported.get(1).getSecondValue());
    }

    @Test
    void comparisonDoesNotCloneStoredItemsOnEverySaveCheck() {
        var clones = new AtomicInteger();
        var snapshot = new InvSnapshot(new ItemStack[] {new CountingItem(37, clones)});
        assertEquals(1, clones.get());
        ItemStack[] current = {new ItemStack(Material.DIAMOND, 37)};
        for (int check = 0; check < 1_000; check++) {
            assertTrue(snapshot.getChangedSlots(current).isEmpty());
            assertTrue(InvStorageUtils.getChangedSlots(snapshot, current).isEmpty());
        }
        assertEquals(1, clones.get(), "Comparison must not use the defensive export getter");
        snapshot.getSnapshot();
        assertEquals(2, clones.get());
    }

    @Test
    void nullEmptyResizeAndAmountRulesMatchTheExistingListComparator() {
        var random = new Random(0x53464cL);
        for (int sample = 0; sample < 1_000; sample++) {
            var baseline = new ItemStack[random.nextInt(10)];
            var current = sample % 7 == 0 ? null : new ItemStack[random.nextInt(10)];
            for (int slot = 0; slot < baseline.length; slot++) {
                if (random.nextBoolean()) baseline[slot] = new ItemStack(Material.DIAMOND, 1 + random.nextInt(64));
            }
            if (current != null) {
                for (int slot = 0; slot < current.length; slot++) {
                    if (random.nextBoolean()) current[slot] = new ItemStack(Material.DIAMOND, 1 + random.nextInt(64));
                }
            }
            var reference = new ArrayList<Pair<ItemStack, Integer>>();
            for (var item : baseline) reference.add(new Pair<>(item, item == null ? 0 : item.getAmount()));
            var expected = InvStorageUtils.getChangedSlots(reference, current);
            var snapshot = new InvSnapshot(baseline);
            assertEquals(expected, snapshot.getChangedSlots(current));
            assertEquals(expected, InvStorageUtils.getChangedSlots(snapshot, current));
        }
        assertTrue(InvStorageUtils.getChangedSlots((InvSnapshot) null, null).isEmpty());
        assertEquals(Set.of(0, 1), InvStorageUtils.getChangedSlots((InvSnapshot) null, new ItemStack[2]));
    }

    @Test
    void invalidConstructorInputsAndCloneFailuresNeverBecomeEmptyBaselines() {
        assertThrows(NullPointerException.class, () -> new InvSnapshot((List<Pair<ItemStack, Integer>>) null));
        assertThrows(NullPointerException.class, () -> new InvSnapshot(Arrays.asList((Pair<ItemStack, Integer>) null)));
        var failure = new ItemStack(Material.DIAMOND) {
            @Override
            public ItemStack clone() {
                throw new IllegalStateException("injected clone failure");
            }
        };
        assertThrows(IllegalStateException.class, () -> new InvSnapshot(List.of(new Pair<>(failure, 1))));
        assertThrows(IllegalStateException.class, () -> new InvSnapshot(new ItemStack[] {failure}));
    }

    private static class CountingItem extends ItemStack {
        private final AtomicInteger clones;

        CountingItem(int amount, AtomicInteger clones) {
            super(Material.DIAMOND, amount);
            this.clones = clones;
        }

        @Override
        public ItemStack clone() {
            clones.incrementAndGet();
            return new CountingItem(getAmount(), clones);
        }
    }

    private static NamespacedKey key(String key) {
        return java.util.Objects.requireNonNull(NamespacedKey.fromString(key));
    }

    private static void rename(ItemStack item, String name) {
        var meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        item.setItemMeta(meta);
    }

    private static ItemStack richItem(int amount) {
        var item = new ItemStack(Material.DIAMOND, amount);
        var meta = item.getItemMeta();
        meta.displayName(Component.text("Original owner name"));
        meta.lore(List.of(Component.text("Keep existing lore")));
        var data = meta.getPersistentDataContainer();
        data.set(key("slimefun:slimefun_item"), PersistentDataType.STRING, "UNREGISTERED_OLD_ID");
        data.set(key("slimefun:item_charge"), PersistentDataType.FLOAT, 123.4567F);
        data.set(key("old:count"), PersistentDataType.LONG, 9_007_199_254_740_993L);
        item.setItemMeta(meta);
        return item;
    }
}
