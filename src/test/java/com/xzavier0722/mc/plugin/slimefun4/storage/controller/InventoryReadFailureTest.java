package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import static org.junit.jupiter.api.Assertions.*;

import city.norain.slimefun4.api.menu.UniversalMenu;
import city.norain.slimefun4.api.menu.UniversalMenuPreset;
import com.xzavier0722.mc.plugin.slimefun4.storage.callback.IAsyncReadCallback;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.FieldKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordSet;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.ScopeKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.attributes.UniversalDataTrait;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.ItemStackDataCodec;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerBackpack;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/** Actual controller loads with SQLite-backed reads and intercepted write submissions, not a server-world test. */
class InventoryReadFailureTest {
    private ServerMock server;
    private InventoryReadTestPlugin fixture;

    @TempDir
    Path directory;

    enum Kind {
        BLOCK,
        UNIVERSAL,
        BACKPACK
    }

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("old_world");
        fixture = new InventoryReadTestPlugin(server);
        new BlockPreset();
        new UniversalPreset();
    }

    @AfterEach
    void tearDown() {
        try {
            if (fixture != null) fixture.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void failedLoadRetainsRowsBlocksSavesAndRecoversOnlyAfterCompleteRetry(Kind kind) throws Exception {
        try (Harness harness = new Harness(kind)) {
            byte[] valid = ItemStackDataCodec.serialize(StoredInventoryReaderTest.item());
            byte[] bad = {'S', 'F', '2', 0};
            harness.store.put("0", valid);
            harness.store.put("8", bad);
            assertThrows(IllegalStateException.class, harness::load);
            harness.assertUnpublished();
            var diagnostic = harness.recoverySnapshot();
            assertEquals(1, diagnostic.totalEntries());
            assertTrue(diagnostic.hasEntries());
            assertEquals(0, harness.writes.get());
            assertArrayEquals(bad, harness.store.raw("8"));
            assertArrayEquals(valid, harness.store.raw("0"));
            assertTrue(harness.save().isCompletedExceptionally());
            assertEquals(0, harness.writes.get());
            harness.store.reopen();
            assertThrows(IllegalStateException.class, harness::load);
            assertArrayEquals(bad, harness.store.raw("8"));

            // Represents an explicit external repair/restore; the loader itself never rewrites data.
            harness.store.put("8", valid);
            ItemStack[] contents = harness.load();
            assertFalse(harness.recoverySnapshot().hasEntries());
            assertEquals(1, diagnostic.totalEntries(), "Old diagnostic snapshots must stay detached");
            assertEquals(37, contents[0].getAmount());
            assertEquals(37, contents[8].getAmount());
            assertEquals(StoredInventoryReaderTest.item().getItemMeta(), contents[8].getItemMeta());
            assertFalse(harness.save().isCompletedExceptionally());
            assertArrayEquals(valid, harness.store.raw("8"));
        }
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void outOfRangeAndMalformedSlotKeysBlockLoadsWithoutDeletion(Kind kind) throws Exception {
        for (String slot : List.of("-1", "54", "not-a-slot")) {
            try (Harness harness = new Harness(kind)) {
                byte[] valid = ItemStackDataCodec.serialize(StoredInventoryReaderTest.item());
                harness.store.put(slot, valid);
                assertThrows(IllegalStateException.class, harness::load);
                harness.assertUnpublished();
                assertTrue(harness.save().isCompletedExceptionally());
                assertEquals(0, harness.writes.get());
                assertArrayEquals(valid, harness.store.raw(slot));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(
            value = Kind.class,
            names = {"BLOCK", "UNIVERSAL"})
    void missingPresetKeepsItemsUnavailableButRecoverable(Kind kind) throws Exception {
        try (Harness harness = new Harness(kind)) {
            byte[] valid = ItemStackDataCodec.serialize(StoredInventoryReaderTest.item());
            harness.store.put("8", valid);
            Slimefun.getRegistry().getMenuPresets().remove(harness.id());
            assertThrows(IllegalStateException.class, harness::load);
            harness.assertUnpublished();
            assertTrue(harness.save().isCompletedExceptionally());
            assertEquals(0, harness.writes.get());
            new BlockPreset();
            new UniversalPreset();
            assertEquals(37, harness.load()[8].getAmount());
            assertArrayEquals(valid, harness.store.raw("8"));
        }
    }

    @Test
    void migrationChecksUnreadableSourceBeforeAllocatingDestinationOrRemovingSource() throws Exception {
        AtomicInteger sideEffects = new AtomicInteger();
        var controller = new BlockDataController() {
            @Override
            public SlimefunUniversalBlockData createUniversalBlock(Location location, String id) {
                sideEffects.incrementAndGet();
                throw new AssertionError("Migration must not reach destination creation");
            }

            @Override
            public void removeBlockData(Location location) {
                sideEffects.incrementAndGet();
            }
        };
        var method = BlockDataController.class.getDeclaredMethod(
                "migrateUniversalData", Location.class, String.class, List.class, List.class);
        method.setAccessible(true);
        byte[] broken = {'S', 'F', '2', 0};
        var record = StoredInventoryReaderTest.row("8", broken);
        InvocationTargetException failure = assertThrows(
                InvocationTargetException.class,
                () -> method.invoke(controller, location(), "READ_UNIVERSAL", List.of(), List.of(record)));
        assertInstanceOf(IllegalStateException.class, failure.getCause());
        assertEquals(0, sideEffects.get());
        assertArrayEquals(broken, (byte[]) record.getValue(FieldKey.INVENTORY_ITEM));
        assertTrue(Slimefun.getTickerTask().getTickLocations().isEmpty());
    }

    @Test
    void collectionCallbackRunsOnlyAfterAllLoadsAndNeverAfterFailure() {
        List<Runnable> queued = new ArrayList<>();
        AtomicInteger loads = new AtomicInteger();
        AtomicInteger callbacks = new AtomicInteger();
        var controller = new BlockDataController() {
            @Override
            protected void scheduleReadTask(Runnable task) {
                queued.add(task);
            }

            @Override
            public void loadBlockData(SlimefunBlockData data) {
                loads.incrementAndGet();
                if (data.getSfId().equals("BROKEN")) throw new IllegalStateException("fixture read failure");
            }

            @Override
            protected <T> void invokeCallback(IAsyncReadCallback<T> callback, T result) {
                callbacks.incrementAndGet();
            }
        };
        var healthy = new SlimefunBlockData(location(), "READ_BLOCK");
        controller.loadBlockDataAsync(List.of(healthy, healthy), null);
        assertEquals(0, callbacks.get());
        assertEquals(0, loads.get());
        queued.removeFirst().run();
        assertEquals(2, loads.get());
        assertEquals(1, callbacks.get());
        controller.loadBlockDataAsync(List.of(healthy, new SlimefunBlockData(location(), "BROKEN")), null);
        assertEquals(1, callbacks.get());
        assertThrows(IllegalStateException.class, () -> queued.removeFirst().run());
        assertEquals(1, callbacks.get());
    }

    private Location location() {
        return new Location(server.getWorld("old_world"), 10, 64, -20);
    }

    private final class Harness implements AutoCloseable {
        final Kind kind;
        final Store store;
        final AtomicInteger writes = new AtomicInteger();
        final TestBlocks blocks;
        final TestProfiles profiles;
        final SlimefunBlockData block;
        final SlimefunUniversalData universal;
        final UUID backpackId = UUID.randomUUID();
        PlayerBackpack backpack;

        Harness(Kind kind) throws Exception {
            this.kind = kind;
            store = new Store(directory.resolve(UUID.randomUUID() + ".db"));
            blocks = new TestBlocks(store, writes);
            profiles = new TestProfiles(store, writes);
            block = new SlimefunBlockData(location(), "READ_BLOCK");
            universal = new SlimefunUniversalData(UUID.randomUUID(), "READ_UNIVERSAL");
            universal.addTrait(UniversalDataTrait.INVENTORY);
        }

        InventoryRecoverySnapshot recoverySnapshot() {
            return kind == Kind.BACKPACK
                    ? profiles.getInventoryRecoverySnapshot()
                    : blocks.getInventoryRecoverySnapshot();
        }

        String id() {
            return kind == Kind.BLOCK ? "READ_BLOCK" : "READ_UNIVERSAL";
        }

        ItemStack[] load() throws Exception {
            if (kind == Kind.BLOCK) {
                blocks.loadBlockData(block);
                return block.getMenuContents();
            }
            if (kind == Kind.UNIVERSAL) {
                blocks.loadUniversalData(universal);
                return universal.getMenuContents();
            }
            var method = ProfileDataController.class.getDeclaredMethod("getBackpackInv", String.class, int.class);
            method.setAccessible(true);
            ItemStack[] items;
            try {
                items = (ItemStack[]) method.invoke(profiles, backpackId.toString(), 9);
            } catch (InvocationTargetException failure) {
                if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
                throw failure;
            }
            backpack = new PlayerBackpack(server.addPlayer(), backpackId, "Existing backpack", 1, 9, items);
            return items;
        }

        CompletableFuture<Void> save() {
            if (kind == Kind.BLOCK) return blocks.saveBlockInventoryAsync(block);
            if (kind == Kind.UNIVERSAL) return blocks.saveUniversalInventoryAsync(universal);
            PlayerBackpack candidate = backpack == null
                    ? new PlayerBackpack(server.addPlayer(), backpackId, "Unpublished", 1, 9, null)
                    : backpack;
            return profiles.saveBackpackInventoryAsync(candidate);
        }

        void assertUnpublished() {
            if (kind == Kind.BLOCK) {
                assertFalse(block.isDataLoaded());
                assertNull(block.getBlockMenu());
            }
            if (kind == Kind.UNIVERSAL) {
                assertFalse(universal.isDataLoaded());
                assertNull(universal.getMenu());
                assertFalse(blocks.getAllLoadedUniversalData().contains(universal));
            }
            if (kind == Kind.BACKPACK) assertNull(backpack);
            assertTrue(Slimefun.getTickerTask().getTickLocations().isEmpty());
        }

        @Override
        public void close() throws Exception {
            var field = ProfileDataController.class.getDeclaredField("backpackCache");
            field.setAccessible(true);
            ((BackpackCache) field.get(profiles)).clean();
            store.close();
        }
    }

    private static class TestBlocks extends BlockDataController {
        final Store store;
        final AtomicInteger writes;

        TestBlocks(Store store, AtomicInteger writes) {
            this.store = store;
            this.writes = writes;
        }

        @Override
        protected List<RecordSet> getData(RecordKey key) {
            return key.getScope() == DataScope.BLOCK_INVENTORY || key.getScope() == DataScope.UNIVERSAL_INVENTORY
                    ? store.read()
                    : List.of();
        }

        @Override
        protected CompletableFuture<Void> scheduleWriteTaskWithCompletion(
                ScopeKey scope, RecordKey key, RecordSet data, boolean serial) {
            writes.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        protected CompletableFuture<Void> scheduleDeleteTaskWithCompletion(
                ScopeKey scope, RecordKey key, boolean serial) {
            writes.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class TestProfiles extends ProfileDataController {
        final Store store;
        final AtomicInteger writes;

        TestProfiles(Store store, AtomicInteger writes) {
            this.store = store;
            this.writes = writes;
        }

        @Override
        protected List<RecordSet> getData(RecordKey key) {
            return store.read();
        }

        @Override
        protected CompletableFuture<Void> scheduleWriteTaskWithCompletion(
                ScopeKey scope, RecordKey key, RecordSet data, boolean serial) {
            writes.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        protected CompletableFuture<Void> scheduleDeleteTaskWithCompletion(
                ScopeKey scope, RecordKey key, boolean serial) {
            writes.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class BlockPreset extends BlockMenuPreset {
        BlockPreset() {
            super("READ_BLOCK", "Old machine");
        }

        @Override
        public void init() {
            setSize(54);
        }

        @Override
        public boolean canOpen(Block block, Player player) {
            return true;
        }

        @Override
        public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
            return new int[] {0, 8};
        }

        @Override
        public void newInstance(BlockMenu menu, Location location) {}
    }

    private static final class UniversalPreset extends UniversalMenuPreset {
        UniversalPreset() {
            super("READ_UNIVERSAL", "Old universal machine");
        }

        @Override
        public void init() {
            setSize(54);
            addItem(53, null);
        }

        @Override
        public boolean canOpen(Block block, Player player) {
            return true;
        }

        @Override
        public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
            return new int[] {0, 8};
        }

        @Override
        public void newInstance(UniversalMenu menu, Block block) {}
    }

    private static final class Store implements AutoCloseable {
        final String jdbc;
        Connection connection;

        Store(Path path) throws Exception {
            Class.forName("org.sqlite.JDBC");
            jdbc = "jdbc:sqlite:" + path;
            connection = DriverManager.getConnection(jdbc);
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE saved_inventory(slot TEXT PRIMARY KEY, item BLOB NOT NULL)");
            }
        }

        void put(String slot, byte[] item) throws SQLException {
            try (var statement = connection.prepareStatement("INSERT OR REPLACE INTO saved_inventory VALUES (?, ?)")) {
                statement.setString(1, slot);
                statement.setBytes(2, item);
                statement.executeUpdate();
            }
        }

        List<RecordSet> read() {
            List<RecordSet> result = new ArrayList<>();
            try (var statement = connection.createStatement();
                    var rows = statement.executeQuery("SELECT slot,item FROM saved_inventory ORDER BY slot")) {
                while (rows.next()) result.add(StoredInventoryReaderTest.row(rows.getString(1), rows.getBytes(2)));
            } catch (SQLException failure) {
                throw new IllegalStateException(failure);
            }
            return result;
        }

        byte[] raw(String slot) throws SQLException {
            try (var statement = connection.prepareStatement("SELECT item FROM saved_inventory WHERE slot=?")) {
                statement.setString(1, slot);
                try (var row = statement.executeQuery()) {
                    assertTrue(row.next());
                    return row.getBytes(1);
                }
            }
        }

        void reopen() throws SQLException {
            connection.close();
            connection = DriverManager.getConnection(jdbc);
        }

        @Override
        public void close() throws SQLException {
            connection.close();
        }
    }
}
