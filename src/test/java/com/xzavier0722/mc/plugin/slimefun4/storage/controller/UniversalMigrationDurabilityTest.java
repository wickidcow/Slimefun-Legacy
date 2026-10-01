package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import static org.junit.jupiter.api.Assertions.*;

import city.norain.slimefun4.api.menu.UniversalMenu;
import city.norain.slimefun4.api.menu.UniversalMenuPreset;
import com.xzavier0722.mc.plugin.slimefun4.storage.adapter.IDataSourceAdapter;
import com.xzavier0722.mc.plugin.slimefun4.storage.adapter.sqlite.SqliteAdapter;
import com.xzavier0722.mc.plugin.slimefun4.storage.adapter.sqlite.SqliteConfig;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.BlockStorageMigration;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataType;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordSet;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.attributes.UniversalBlock;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.DataUtils;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.ItemStackDataCodec;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.LocationUtils;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Real controller, tracked writer queue, production SQLite adapter and disk database; not a live Minecraft server. */
class UniversalMigrationDurabilityTest {
    private static final String ID = "ATOMIC_EXISTING_MACHINE";

    @TempDir
    Path directory;

    private InventoryReadTestPlugin fixture;
    private TestController controller;
    private TestAdapter adapter;
    private Connection observer;
    private Location location;
    private SlimefunBlockData source;
    private SlimefunChunkData chunk;
    private byte[] savedItem;
    private Map<Class<? extends ADataController>, Object> holders;
    private Map<Class<? extends ADataController>, Object> previousHolders;
    private final AtomicBoolean failMenu = new AtomicBoolean();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        var server = MockBukkit.mock();
        server.addSimpleWorld("old_world");
        fixture = new InventoryReadTestPlugin(server);
        controller = new TestController();
        holders = (Map<Class<? extends ADataController>, Object>)
                field(ControllerHolder.class, "holders").get(null);
        previousHolders = new HashMap<>(holders);
        holders.remove(BlockDataController.class);
        ControllerHolder.createController(BlockDataController.class, StorageType.SQLITE);
        ((Map<StorageType, ADataController>)
                        field(ControllerHolder.class, "controllers").get(holders.get(BlockDataController.class)))
                .put(StorageType.SQLITE, controller);
        field(Slimefun.getDatabaseManager().getClass(), "blockDataStorageType")
                .set(Slimefun.getDatabaseManager(), StorageType.SQLITE);
        field(BlockDataController.class, "chunkDataLoadMode").set(controller, ChunkDataLoadMode.LOAD_WITH_CHUNK);
        location = new Location(server.getWorld("old_world"), 10, 64, -20);
        location.getBlock().setType(Material.CHEST);
        new Preset(failMenu);
        Slimefun.getRegistry().getSlimefunItemIds().put(ID, new UniversalItem());
        chunk = new SlimefunChunkData(location.getChunk());
        chunk.setIsDataLoaded(true);
        source = new SlimefunBlockData(location, ID);
        chunk.addBlockCacheInternal(source, true);
        ((Map<String, SlimefunChunkData>)
                        field(BlockDataController.class, "loadedChunk").get(controller))
                .put(chunk.getKey(), chunk);
        var config = new SqliteConfig(directory.resolve("real-adapter.db").toString(), 3);
        adapter = new TestAdapter();
        adapter.prepare(config);
        adapter.initStorage(DataType.BLOCK_STORAGE);
        field(ADataController.class, "dataAdapter").set(controller, adapter);
        observer = DriverManager.getConnection(config.jdbcUrl());
        savedItem = ItemStackDataCodec.serialize(StoredInventoryReaderTest.item());
        insertSource();
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            if (controller != null) controller.writeExecutor.shutdownNow();
            if (observer != null) observer.close();
            if (adapter != null) adapter.shutdown();
            if (holders != null && previousHolders != null) {
                holders.clear();
                holders.putAll(previousHolders);
            }
            if (fixture != null) fixture.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void normalLoadPublishesOnlyAfterProductionAdapterCommitAndNeverQueuesASecondSourceDelete() throws Exception {
        adapter.before = plan -> {
            assertEquals(1, countUnchecked("block_record"));
            assertEquals(0, countUnchecked("universal_record"));
            assertSame(source, chunk.getBlockCacheInternal(source.getKey()));
            assertTrue(controller.getAllLoadedUniversalData().isEmpty());
            assertTrue(controller.isInventoryMutationBlocked(location));
            assertThrows(IllegalStateException.class, () -> controller.removeBlock(location));
        };
        adapter.after = plan -> {
            assertEquals(0, countUnchecked("block_record"));
            assertEquals(1, countUnchecked("universal_record"));
            assertSame(source, chunk.getBlockCacheInternal(source.getKey()));
            assertTrue(controller.getAllLoadedUniversalData().isEmpty());
        };
        controller.loadBlockData(source);
        assertRecovered();
        assertTrue(source.isPendingRemove());
        assertFalse(source.isDataLoaded());
        assertEquals(1, adapter.attempts.size());
    }

    @Test
    void failedStorageCommitLeavesSourceAndCacheIntactAndCompleteRetrySucceeds() throws Exception {
        adapter.before = plan -> {
            throw new BlockStorageMigration.Failure("injected pre-commit failure", null, true);
        };
        assertThrows(IllegalStateException.class, () -> controller.loadBlockData(source));
        assertEquals(1, count("block_record"));
        assertEquals(0, count("universal_record"));
        assertSame(source, chunk.getBlockCacheInternal(source.getKey()));
        assertArrayEquals(savedItem, storedBytes("block_inventory"));
        assertTrue(controller.isInventoryMutationBlocked(location));
        assertTrue(controller.getAllLoadedUniversalData().isEmpty());
        adapter.before = null;
        controller.loadBlockData(source);
        assertRecovered();
    }

    @Test
    void lostCommitAcknowledgementKeepsTheSameUuidAndResumesFromTheCommittedDatabase() throws Exception {
        adapter.after = plan -> {
            throw new BlockStorageMigration.Failure("injected lost ack", null, false);
        };
        assertThrows(IllegalStateException.class, () -> controller.loadBlockData(source));
        assertEquals(0, count("block_record"));
        assertEquals(1, count("universal_record"));
        assertArrayEquals(savedItem, storedBytes("universal_inventory"));
        assertTrue(controller.getAllLoadedUniversalData().isEmpty());
        assertSame(source, chunk.getBlockCacheInternal(source.getKey()));
        assertTrue(controller.isInventoryMutationBlocked(location));
        UUID first = adapter.attempts.getFirst().destination();
        adapter.after = null;
        controller.loadBlockData(source);
        assertRecovered();
        assertEquals(2, adapter.attempts.size());
        assertEquals(first, adapter.attempts.getLast().destination());
        assertEquals(
                first, controller.getAllLoadedUniversalData().iterator().next().getUUID());
    }

    @Test
    void retryReusesADestinationAlreadyActivatedByTheOrdinaryLoader() throws Exception {
        adapter.after = plan -> {
            throw new BlockStorageMigration.Failure("injected lost ack", null, false);
        };
        assertThrows(IllegalStateException.class, () -> controller.loadBlockData(source));
        UUID target = adapter.attempts.getFirst().destination();
        adapter.after = null;
        var loaded = controller.getUniversalBlockData(target);
        assertNotNull(loaded);
        controller.loadUniversalData(loaded);
        controller.loadBlockData(source);
        assertRecovered();
        assertSame(loaded, controller.getUniversalDataFromCache(target));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aFreshControllerLoadsCommittedDataWithoutTheOldInMemoryMigrationPlan() throws Exception {
        adapter.after = plan -> {
            throw new BlockStorageMigration.Failure("injected lost ack", null, false);
        };
        assertThrows(IllegalStateException.class, () -> controller.loadBlockData(source));
        UUID target = adapter.attempts.getFirst().destination();
        controller.writeExecutor.shutdownNow();
        controller = new TestController();
        field(ADataController.class, "dataAdapter").set(controller, adapter);
        ((Map<StorageType, ADataController>)
                        field(ControllerHolder.class, "controllers").get(holders.get(BlockDataController.class)))
                .put(StorageType.SQLITE, controller);
        var loaded = controller.getUniversalBlockData(target);
        assertNotNull(loaded);
        controller.loadUniversalData(loaded);
        assertTrue(loaded.isDataLoaded());
        assertEquals(37, loaded.getMenuContents()[8].getAmount());
        assertEquals(StoredInventoryReaderTest.item().getItemMeta(), loaded.getMenuContents()[8].getItemMeta());
        assertEquals(loaded, controller.getUniversalBlockDataFromCache(location).orElseThrow());
        assertArrayEquals(savedItem, storedBytes("universal_inventory"));
        assertEquals(0, count("block_record"));
        assertEquals(1, adapter.attempts.size(), "Restart lookup must not create another destination");
    }

    @Test
    void menuActivationFailureAfterCommitRetainsDataAndRetriesWithoutAnotherMigration() throws Exception {
        failMenu.set(true);
        assertThrows(IllegalStateException.class, () -> controller.loadBlockData(source));
        assertEquals(0, count("block_record"));
        assertEquals(1, count("universal_record"));
        assertArrayEquals(savedItem, storedBytes("universal_inventory"));
        assertTrue(controller.getAllLoadedUniversalData().isEmpty());
        assertTrue(controller.isInventoryMutationBlocked(location));
        assertSame(source, chunk.getBlockCacheInternal(source.getKey()));
        failMenu.set(false);
        controller.loadBlockData(source);
        assertRecovered();
        assertEquals(
                1, adapter.attempts.size(), "A confirmed commit needs activation recovery, not a fresh database move");
    }

    @Test
    void sourceChangedAfterPreflightIsRetainedAndCanBeRestaged() throws Exception {
        adapter.before = plan -> {
            adapter.before = null;
            try (var statement = observer.prepareStatement("UPDATE block_data SET data_val=? WHERE data_key='mode'")) {
                statement.setString(1, DataUtils.blockDataBase64("newer-mode"));
                statement.executeUpdate();
            } catch (SQLException error) {
                throw new IllegalStateException(error);
            }
        };
        assertThrows(IllegalStateException.class, () -> controller.loadBlockData(source));
        assertEquals(1, count("block_record"));
        assertEquals(0, count("universal_record"));
        controller.loadBlockData(source);
        assertRecovered();
        assertEquals(
                "newer-mode",
                controller.getAllLoadedUniversalData().iterator().next().getData("mode"));
    }

    @Test
    void historicalReservedLocationIsPreservedWithoutReencodingOrChangingIdentity() throws Exception {
        String historical = "[world=old_world,x=10.0,y=64.0,z=-20.0]";
        String encoded = DataUtils.blockDataBase64(historical);
        try (var statement =
                observer.prepareStatement("INSERT INTO block_data (loc,data_key,data_val) VALUES (?,?,?)")) {
            statement.setString(1, source.getKey());
            statement.setString(2, "location");
            statement.setString(3, encoded);
            statement.executeUpdate();
        }
        controller.loadBlockData(source);
        assertRecovered();
        try (var statement = observer.createStatement();
                var rows = statement.executeQuery("SELECT data_val FROM universal_data WHERE data_key='location'")) {
            assertTrue(rows.next());
            assertEquals(encoded, rows.getString(1));
            assertFalse(rows.next());
        }
    }

    @Test
    void ordinarySourceRecordWritesCannotCompactAwayTheQueuedMigration() throws Exception {
        var writerPaused = new java.util.concurrent.CountDownLatch(1);
        var releaseWriter = new java.util.concurrent.CountDownLatch(1);
        controller.writeExecutor.submit(() -> {
            writerPaused.countDown();
            try {
                if (!releaseWriter.await(10, java.util.concurrent.TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Test did not release the writer");
                }
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(failure);
            }
        });
        assertTrue(writerPaused.await(10, java.util.concurrent.TimeUnit.SECONDS));
        var plan = new BlockStorageMigration(
                source.getKey(),
                chunk.getKey(),
                ID,
                UUID.randomUUID(),
                DataUtils.blockDataBase64(source.getKey()),
                Map.of("mode", DataUtils.blockDataBase64("existing-mode")),
                Map.of(8, savedItem));
        try {
            var migration = controller.persistUniversalMigration(plan);
            var ordinary = new RecordKey(DataScope.BLOCK_RECORD);
            ordinary.addCondition(
                    com.xzavier0722.mc.plugin.slimefun4.storage.common.FieldKey.LOCATION, source.getKey());
            var normalWrite = controller.scheduleWriteTaskWithCompletion(
                    new LocationKey(DataScope.NONE, source.getKey()), ordinary, () -> {}, true);
            releaseWriter.countDown();
            migration.get(10, java.util.concurrent.TimeUnit.SECONDS);
            normalWrite.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(1, adapter.attempts.size());
            assertEquals(0, count("block_record"));
            assertEquals(1, count("universal_record"));
            assertArrayEquals(savedItem, storedBytes("universal_inventory"));
        } finally {
            releaseWriter.countDown();
        }
    }

    @Test
    void oldAdapterWithoutAtomicSupportRefusesMigrationRatherThanFallingBackToUnsafeWrites() throws Exception {
        IDataSourceAdapter<Void> olderAdapter = new IDataSourceAdapter<>() {
            public void prepare(Void unused) {}

            public void initStorage(DataType type) {}

            public void shutdown() {}

            public void setData(RecordKey key, RecordSet record) {
                fail("No best-effort migration writes allowed");
            }

            public List<RecordSet> getData(RecordKey key, boolean distinct) {
                return adapter.getData(key, distinct);
            }

            public void deleteData(RecordKey key) {
                fail("No best-effort source deletion allowed");
            }

            public void patch() {}
        };
        field(ADataController.class, "dataAdapter").set(controller, olderAdapter);
        assertThrows(IllegalStateException.class, () -> controller.loadBlockData(source));
        assertEquals(1, count("block_record"));
        assertEquals(0, count("universal_record"));
        assertSame(source, chunk.getBlockCacheInternal(source.getKey()));
        assertArrayEquals(savedItem, storedBytes("block_inventory"));
        assertTrue(controller.getAllLoadedUniversalData().isEmpty());
    }

    private void assertRecovered() throws Exception {
        assertEquals(0, count("block_record"));
        assertEquals(0, count("block_data"));
        assertEquals(0, count("block_inventory"));
        assertEquals(1, count("universal_record"));
        assertArrayEquals(savedItem, storedBytes("universal_inventory"));
        assertEquals(1, controller.getAllLoadedUniversalData().size());
        var data = controller.getAllLoadedUniversalData().iterator().next();
        assertTrue(data.isDataLoaded());
        assertEquals(ID, data.getSfId());
        assertEquals(37, data.getMenuContents()[8].getAmount());
        assertEquals(StoredInventoryReaderTest.item().getItemMeta(), data.getMenuContents()[8].getItemMeta());
        assertEquals(
                LocationUtils.getLocKey(location),
                InventoryRecoveryLocations.canonicalLocationKey(data.getData("location")));
        assertFalse(controller.isInventoryMutationBlocked(location));
        assertNull(chunk.getBlockCacheInternal(source.getKey()));
    }

    private void insertSource() throws SQLException {
        try (var statement = observer.prepareStatement("INSERT INTO block_record (loc,chunk,sf_id) VALUES (?,?,?)")) {
            statement.setString(1, source.getKey());
            statement.setString(2, chunk.getKey());
            statement.setString(3, ID);
            statement.executeUpdate();
        }
        try (var statement =
                observer.prepareStatement("INSERT INTO block_data (loc,data_key,data_val) VALUES (?,?,?)")) {
            statement.setString(1, source.getKey());
            statement.setString(2, "mode");
            statement.setString(3, DataUtils.blockDataBase64("existing-mode"));
            statement.executeUpdate();
        }
        try (var statement =
                observer.prepareStatement("INSERT INTO block_inventory (loc,i_slot,i_item) VALUES (?,?,?)")) {
            statement.setString(1, source.getKey());
            statement.setInt(2, 8);
            statement.setBytes(3, savedItem);
            statement.executeUpdate();
        }
    }

    private int count(String table) throws SQLException {
        try (var statement = observer.createStatement();
                var rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }

    private int countUnchecked(String table) {
        try {
            return count(table);
        } catch (SQLException failure) {
            throw new AssertionError(failure);
        }
    }

    private byte[] storedBytes(String table) throws SQLException {
        try (var statement = observer.createStatement();
                var rows = statement.executeQuery("SELECT i_item FROM " + table + " WHERE i_slot=8")) {
            assertTrue(rows.next());
            return rows.getBytes(1);
        }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static final class TestController extends BlockDataController {
        private TestController() {
            writeExecutor = Executors.newSingleThreadExecutor();
        }

        @Override
        protected void deleteData(RecordKey key) {
            fail("Migration must not schedule an extra source deletion after commit");
        }
    }

    private static final class TestAdapter extends SqliteAdapter {
        private final List<BlockStorageMigration> attempts = new ArrayList<>();
        private Consumer<BlockStorageMigration> before, after;

        @Override
        public void migrateBlockToUniversal(BlockStorageMigration plan) {
            attempts.add(plan);
            if (before != null) before.accept(plan);
            super.migrateBlockToUniversal(plan);
            if (after != null) after.accept(plan);
        }
    }

    private static final class UniversalItem extends SlimefunItem implements UniversalBlock {
        private UniversalItem() {
            super(
                    new ItemGroup(
                            new NamespacedKey("slimefun", "atomic_migration_test"), new ItemStack(Material.CHEST)),
                    new SlimefunItemStack(ID, Material.CHEST, "Existing machine"),
                    RecipeType.ENHANCED_CRAFTING_TABLE,
                    new ItemStack[9]);
        }
    }

    private static final class Preset extends UniversalMenuPreset {
        private final AtomicBoolean fail;

        private Preset(AtomicBoolean fail) {
            super(ID, "Existing machine");
            this.fail = fail;
        }

        @Override
        public void init() {
            setSize(9);
        }

        @Override
        public boolean canOpen(Block block, Player player) {
            return true;
        }

        @Override
        public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
            return new int[] {8};
        }

        @Override
        public void newInstance(UniversalMenu menu, Block block) {
            if (fail.get()) throw new IllegalStateException("injected menu activation failure after commit");
        }
    }
}
