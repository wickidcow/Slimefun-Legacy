package io.github.thebusybiscuit.slimefun4.core.networks.cargo;

import static org.junit.jupiter.api.Assertions.*;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.InventoryReadTestPlugin;
import io.github.thebusybiscuit.slimefun4.utils.itemstack.ItemStackWrapper;
import java.lang.reflect.Field;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/** Exercises the real predicate with a seeded snapshot; no database or gameplay scheduler is started. */
class ItemFilterTest {
    private ServerMock server;
    private InventoryReadTestPlugin fixture;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("cargo");
        fixture = new InventoryReadTestPlugin(server);
    }

    @AfterEach
    void tearDown() {
        try {
            if (fixture != null) fixture.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void busyNoMatchFilterReadsSubjectMaterialOnceWithoutReadingMetadata() throws Exception {
        var filter = filter(false, false, List.of(
                new ItemStack(Material.STONE), new ItemStack(Material.COAL), new ItemStack(Material.IRON_INGOT),
                new ItemStack(Material.GOLD_INGOT), new ItemStack(Material.COPPER_INGOT),
                new ItemStack(Material.REDSTONE), new ItemStack(Material.LAPIS_LAZULI),
                new ItemStack(Material.EMERALD), new ItemStack(Material.AMETHYST_SHARD)));
        var subject = new CountingItem(Material.DIAMOND);

        // Operation counts are deterministic; elapsed-time thresholds would depend on the CI host.
        for (int i = 0; i < 100_000; i++) {
            assertFalse(filter.test(subject));
        }
        assertEquals(100_000, subject.typeReads);
        assertEquals(0, subject.metaReads);
    }

    @Test
    void duplicateMaterialsKeepTheLaterMetadataMatchAndReadSubjectMetaOnce() throws Exception {
        var first = named(Material.DIAMOND, "first", "first lore");
        var later = named(Material.DIAMOND, "wanted", "wanted lore");
        var filter = filter(false, true, List.of(first, first, first, first, first, first, first, first, later));
        var subject = new CountingItem(Material.DIAMOND);
        subject.setItemMeta(later.getItemMeta());
        subject.reset();

        assertTrue(filter.test(subject));
        assertEquals(1, subject.metaReads);
        assertEquals(later.getItemMeta(), subject.getItemMeta());
    }

    @Test
    void whitelistBlacklistLoreAndDirtyStateKeepTheirExistingResults() throws Exception {
        var template = named(Material.DIAMOND, "same", "original lore");
        var changedLore = named(Material.DIAMOND, "same", "different lore");
        assertTrue(filter(false, false, List.of(template)).test(changedLore));
        assertFalse(filter(false, true, List.of(template)).test(changedLore));
        assertFalse(filter(true, false, List.of(template)).test(changedLore));
        assertTrue(filter(true, true, List.of(template)).test(changedLore));
        assertFalse(filter(false, false, List.of()).test(template));
        assertTrue(filter(true, false, List.of()).test(template));
        var dirty = filter(true, false, List.of(template));
        dirty.markDirty();
        assertFalse(dirty.test(new ItemStack(Material.COAL)));
    }

    @SuppressWarnings("unchecked")
    private ItemFilter filter(boolean rejectOnMatch, boolean checkLore, List<ItemStack> items) throws Exception {
        var filter = new SnapshotFilter(server.getWorld("cargo").getBlockAt(0, 64, 0));
        Field stacks = ItemFilter.class.getDeclaredField("items");
        stacks.setAccessible(true);
        ((List<ItemStackWrapper>) stacks.get(filter)).addAll(ItemStackWrapper.wrapList(items));
        set(filter, "rejectOnMatch", rejectOnMatch);
        set(filter, "checkLore", checkLore);
        set(filter, "dirty", false);
        return filter;
    }

    private static void set(ItemFilter filter, String name, boolean value) throws Exception {
        Field field = ItemFilter.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setBoolean(filter, value);
    }

    private static ItemStack named(Material type, String name, String lore) {
        var item = new ItemStack(type);
        var meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        meta.lore(List.of(Component.text(lore)));
        item.setItemMeta(meta);
        return item;
    }

    private static final class SnapshotFilter extends ItemFilter {
        private SnapshotFilter(Block block) {
            super(block);
        }

        @Override
        public void update(Block block) {
            // Snapshot seeded by the fixture. Database loading is outside this predicate test.
        }
    }

    private static final class CountingItem extends ItemStack {
        private int typeReads;
        private int metaReads;

        private CountingItem(Material type) {
            super(type);
            reset();
        }

        private void reset() {
            typeReads = 0;
            metaReads = 0;
        }

        @Override
        public Material getType() {
            typeReads++;
            return super.getType();
        }

        @Override
        public ItemMeta getItemMeta() {
            metaReads++;
            return super.getItemMeta();
        }
    }
}
