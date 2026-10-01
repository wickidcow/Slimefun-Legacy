package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import static org.junit.jupiter.api.Assertions.*;

import city.norain.slimefun4.api.menu.UniversalMenu;
import city.norain.slimefun4.api.menu.UniversalMenuPreset;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.BlockStorageMigration;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.FieldKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordSet;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.ScopeKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.DataUtils;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.ItemStackDataCodec;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.LocationUtils;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.BlockListener;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/** Real controller/cache/event code with SQLite-backed fixture reads and intercepted write submissions. */
class InventoryLifecycleSafetyTest {
    private static final byte[] BAD = {'S', 'F', '2', 0};
    private ServerMock server;
    private InventoryReadTestPlugin fixture;
    private RecordingController controller;
    private Store store;
    private Map<Class<? extends ADataController>, Object> holders;
    private Map<Class<? extends ADataController>, Object> previousHolders;
    private Location location;
    private SlimefunChunkData chunk;
    private SlimefunBlockData data;

    @TempDir
    Path directory;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        server = MockBukkit.mock();
        server.addSimpleWorld("old_world");
        server.addSimpleWorld("unrelated_world");
        fixture = new InventoryReadTestPlugin(server);
        new BlockPreset();
        new UniversalPreset();
        store = new Store(directory.resolve("legacy.db"));
        controller = new RecordingController(store);
        holders = (Map<Class<? extends ADataController>, Object>)
                field(ControllerHolder.class, "holders").get(null);
        previousHolders = new HashMap<>(holders);
        holders.remove(BlockDataController.class);
        ControllerHolder.createController(BlockDataController.class, StorageType.SQLITE);
        var holder = holders.get(BlockDataController.class);
        ((Map<StorageType, ADataController>)
                        field(ControllerHolder.class, "controllers").get(holder))
                .put(StorageType.SQLITE, controller);
        field(Slimefun.getDatabaseManager().getClass(), "blockDataStorageType")
                .set(Slimefun.getDatabaseManager(), StorageType.SQLITE);
        field(BlockDataController.class, "chunkDataLoadMode").set(controller, ChunkDataLoadMode.LOAD_WITH_CHUNK);
        location = new Location(server.getWorld("old_world"), 10, 64, -20);
        location.getBlock().setType(Material.STONE);
        chunk = new SlimefunChunkData(location.getChunk());
        chunk.setIsDataLoaded(true);
        data = new SlimefunBlockData(location, "LIFECYCLE_BLOCK");
        chunk.addBlockCacheInternal(data, true);
        ((Map<String, SlimefunChunkData>)
                        field(BlockDataController.class, "loadedChunk").get(controller))
                .put(chunk.getKey(), chunk);
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            if (store != null) store.close();
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
    void failedReadPreventsDirectChunkRemovalBeforeCacheMutation() throws Exception {
        failBlockLoad();
        assertThrows(IllegalStateException.class, () -> chunk.removeBlockData(location));
        assertSame(data, controller.getBlockDataFromCache(location), "The guarded cache identity must survive");
        assertGuardedBlockUntouched();
    }

    enum Mutation {
        REMOVE,
        REMOVE_DATA,
        DIRECT_DELETE,
        CREATE,
        CREATE_CHUNK,
        CREATE_UNIVERSAL,
        SAVE_NEW,
        REMOVE_UNIVERSAL_AT_LOCATION
    }

    @ParameterizedTest
    @EnumSource(Mutation.class)
    void failedReadsProtectEveryAuditedLocationMutation(Mutation mutation) throws Exception {
        failBlockLoad();
        assertThrows(IllegalStateException.class, () -> {
            switch (mutation) {
                case REMOVE -> controller.removeBlock(location);
                case REMOVE_DATA -> controller.removeBlockData(location);
                case DIRECT_DELETE -> controller.removeBlockDirectly(location);
                case CREATE -> controller.createBlock(location, "LIFECYCLE_BLOCK");
                case CREATE_CHUNK -> chunk.createBlockData(location, "LIFECYCLE_BLOCK");
                case CREATE_UNIVERSAL -> controller.createUniversalBlock(location, "LIFECYCLE_UNIVERSAL");
                case SAVE_NEW -> controller.saveNewBlock(location, "LIFECYCLE_BLOCK");
                case REMOVE_UNIVERSAL_AT_LOCATION -> controller.removeUniversalBlockData(location);
            }
        });
        assertGuardedBlockUntouched();
        store.reopen();
        assertArrayEquals(BAD, store.raw());
    }

    @Test
    @SuppressWarnings({"deprecation", "removal"
    }) // Retain behavior for the historical move alias as well as modern callers.
    void movingFromOrOntoAProtectedPositionDoesNotDetachEitherCache() throws Exception {
        failBlockLoad();
        Location other = location.clone().add(1, 0, 0);
        var healthy = new SlimefunBlockData(other, "UNCHANGED_MACHINE_ID");
        chunk.addBlockCacheInternal(healthy, true);
        assertThrows(IllegalStateException.class, () -> controller.move(data, other));
        assertThrows(IllegalStateException.class, () -> controller.move(healthy, location));
        assertThrows(IllegalStateException.class, () -> controller.setBlockDataLocation(data, other));
        assertSame(data, controller.getBlockDataFromCache(location));
        assertSame(healthy, controller.getBlockDataFromCache(other));
        assertEquals(other, healthy.getLocation());
        assertEquals("UNCHANGED_MACHINE_ID", healthy.getSfId());
        assertGuardedBlockUntouched();
    }

    @Test
    void bulkAndQueuedDeletesRefuseBeforeDroppingCachesOrReportingSuccess() throws Exception {
        failBlockLoad();
        assertThrows(IllegalStateException.class, () -> controller.removeAllDataInChunk(location.getChunk()));
        assertThrows(IllegalStateException.class, () -> controller.removeAllDataInWorld(location.getWorld()));
        AtomicBoolean callback = new AtomicBoolean();
        controller.removeAllDataInChunkAsync(location.getChunk(), () -> callback.set(true));
        assertEquals(1, controller.queued.size());
        assertThrows(
                IllegalStateException.class,
                () -> controller.queued.removeFirst().run());
        assertFalse(callback.get());
        assertGuardedBlockUntouched();
    }

    @Test
    void unrelatedChunksAndWorldsAreNotGloballyBlocked() throws Exception {
        failBlockLoad();
        assertFalse(controller.isInventoryMutationBlocked(new Location(location.getWorld(), 32, 64, -20)));
        controller.removeAllDataInChunk(location.getWorld().getChunkAt(2, -2));
        controller.removeAllDataInWorld(server.getWorld("unrelated_world"));
        assertEquals(4, controller.directDeletes.size());
        assertArrayEquals(BAD, store.raw());
        assertSame(data, controller.getBlockDataFromCache(location));
        assertTrue(controller.isInventoryMutationBlocked(location));
    }

    @Test
    void completeRecoveryRestoresNormalRemovalAndRecreationWithoutResettingIds() throws Exception {
        failBlockLoad();
        store.put(validBytes()); // Explicit fixture repair, never performed by production guard code.
        controller.loadBlockData(data);
        assertFalse(controller.isInventoryMutationBlocked(location));
        assertTrue(data.isDataLoaded());
        assertEquals(37, data.getMenuContents()[8].getAmount());
        assertEquals("LIFECYCLE_BLOCK", data.getSfId());
        controller.removeBlock(location);
        assertNull(controller.getBlockDataFromCache(location));
        assertEquals(1, controller.deleted.size());
        var replacement = controller.createBlock(location, "LIFECYCLE_BLOCK");
        assertEquals("LIFECYCLE_BLOCK", replacement.getSfId());
        assertSame(replacement, controller.getBlockDataFromCache(location));
        assertFalse(controller.isInventoryMutationBlocked(location));
    }

    @Test
    void ordinaryEmptyTombstonesStillPreventRepeatedDeletes() throws Exception {
        chunk.removeBlockDataCacheInternal(data.getKey());
        assertNull(chunk.removeBlockData(location));
        assertTrue(controller.deleted.isEmpty());
        chunk.setIsDataLoaded(false);
        assertNull(chunk.removeBlockData(location));
        assertTrue(chunk.hasBlockCache(data.getKey()));
        assertEquals(1, controller.deleted.size());
        chunk.setIsDataLoaded(true);
        assertFalse(chunk.hasBlockCache(data.getKey()));
    }

    @Test
    void ordinaryMoveStillPreservesIdentityAndOnlyUpdatesItsExistingRecord() throws Exception {
        Location target = location.clone().add(1, 0, 0);
        controller.move(data, target);
        assertNull(controller.getBlockDataFromCache(location));
        var moved = controller.getBlockDataFromCache(target);
        assertNotNull(moved);
        assertEquals("LIFECYCLE_BLOCK", moved.getSfId());
        assertEquals(target, moved.getLocation());
        assertEquals(1, controller.writes.size());
        assertEquals(
                LocationUtils.getLocKey(target), controller.writes.getFirst().getString(FieldKey.LOCATION));
        assertTrue(controller.deleted.isEmpty());
    }

    @Test
    void universalUuidGuardSurvivesCacheEvictionAndProtectsItsKnownPosition() throws Exception {
        var universal = failUniversal(true, LocationUtils.getLocKey(location));
        assertNull(controller.getUniversalDataFromCache(universal.getUUID()));
        assertTrue(controller.isInventoryMutationBlocked(location));
        assertThrows(IllegalStateException.class, () -> controller.removeUniversalBlockData(universal.getUUID()));
        assertThrows(IllegalStateException.class, () -> controller.removeUniversalBlockDirectly(universal.getUUID()));
        assertThrows(
                IllegalStateException.class,
                () -> controller.createUniversalData(universal.getUUID(), "LIFECYCLE_UNIVERSAL"));
        assertThrows(
                IllegalStateException.class, () -> controller.createUniversalBlock(location, "LIFECYCLE_UNIVERSAL"));
        assertThrows(IllegalStateException.class, () -> controller.removeUniversalBlockData(location));
        assertThrows(
                IllegalStateException.class,
                () -> controller.move(universal, location.clone().add(1, 0, 0)));
        assertThrows(IllegalStateException.class, () -> controller.removeAllDataInChunk(location.getChunk()));
        assertFalse(universal.isPendingRemove());
        assertEquals(LocationUtils.getLocKey(location), universal.getKnownLocationKey());
        assertArrayEquals(BAD, store.raw());
        assertTrue(controller.deleted.isEmpty());
        assertTrue(controller.writes.isEmpty());
    }

    @Test
    void historicalUniversalLocationProtectsTheBlockEvenWithoutACachedAnchor() throws Exception {
        var universal = failUniversal(false, LocationUtils.locationToString(location));
        assertTrue(controller.isInventoryMutationBlocked(location));
        assertThrows(IllegalStateException.class, () -> controller.createBlock(location, "LIFECYCLE_BLOCK"));
        store.put(validBytes());
        controller.loadUniversalData(universal);
        assertFalse(controller.isInventoryMutationBlocked(location));
        assertTrue(universal.isDataLoaded());
        assertEquals(37, universal.getMenuContents()[8].getAmount());
        assertEquals(
                LocationUtils.getLocKey(location),
                LocationUtils.getLocKey(universal.getLastPresent().toLocation()));
    }

    @Test
    void twoUniversalOwnersAtOnePositionRequireTwoSuccessfulRecoveries() throws Exception {
        var first = failUniversal(true, LocationUtils.getLocKey(location));
        var second = failUniversal(true, LocationUtils.getLocKey(location));
        store.put(validBytes());
        controller.loadUniversalData(first);
        assertTrue(controller.isInventoryMutationBlocked(location));
        controller.loadUniversalData(second);
        assertFalse(controller.isInventoryMutationBlocked(location));
    }

    @Test
    void guardIsActiveDuringAnInProgressReadBeforeAnyFailureIsKnown() throws Exception {
        store.put(validBytes());
        controller.onRead = () -> {
            assertTrue(controller.isInventoryMutationBlocked(location));
            assertThrows(IllegalStateException.class, () -> controller.removeBlock(location));
            assertSame(data, controller.getBlockDataFromCache(location));
        };
        controller.loadBlockData(data);
        assertFalse(controller.isInventoryMutationBlocked(location));
        assertTrue(controller.deleted.isEmpty());
    }

    @Test
    void protectedPlayerBreakAndPlacementAreCancelledBeforeAnySideEffects() throws Exception {
        failBlockLoad();
        // Permit listener registration without starting production services. The test
        // invokes the real handlers directly, then restores the fixture's original state.
        var plugin = Slimefun.instance();
        var enabled = field(JavaPlugin.class, "isEnabled");
        boolean wasEnabled = enabled.getBoolean(plugin);
        BlockListener listener;
        try {
            enabled.setBoolean(plugin, true);
            listener = new BlockListener(plugin);
        } finally {
            enabled.setBoolean(plugin, wasEnabled);
        }
        org.bukkit.event.HandlerList.unregisterAll(listener);
        Player player = server.addPlayer();
        Block block = location.getBlock();
        var broken = new BlockBreakEvent(block, player);
        listener.onBlockBreak(broken);
        assertTrue(broken.isCancelled());
        assertFalse(data.isPendingRemove());
        var placed = new BlockPlaceEvent(
                block,
                block.getState(),
                block.getRelative(0, -1, 0),
                new ItemStack(Material.STONE),
                player,
                true,
                EquipmentSlot.HAND);
        listener.onBlockPlaceExisting(placed);
        assertTrue(placed.isCancelled());
        placed.setCancelled(false);
        listener.onBlockPlace(placed);
        assertTrue(placed.isCancelled());
        assertEquals(Material.STONE, block.getType());
        assertGuardedBlockUntouched();
    }

    @Test
    void validMigrationUsesPrivatePreflightWithoutLiftingThePublicSourceGuard() throws Exception {
        failBlockLoad();
        controller.beforeUniversalRecord = () -> {
            assertTrue(controller.isInventoryMutationBlocked(location));
            assertThrows(IllegalStateException.class, () -> controller.removeBlock(location));
        };
        var method = BlockDataController.class.getDeclaredMethod(
                "migrateUniversalData", Location.class, String.class, List.class, List.class);
        method.setAccessible(true);
        method.invoke(
                controller,
                location,
                "LIFECYCLE_UNIVERSAL",
                List.of(),
                List.of(StoredInventoryReaderTest.row("8", validBytes())));
        assertEquals(1, controller.getAllLoadedUniversalData().size());
        var migrated = controller.getAllLoadedUniversalData().iterator().next();
        assertEquals("LIFECYCLE_UNIVERSAL", migrated.getSfId());
        assertEquals(37, migrated.getMenuContents()[8].getAmount());
        assertEquals(0, controller.deleted.size(), "The atomic adapter contract already retired the source");
        assertNotNull(controller.committedMigration);
        assertTrue(
                controller.isInventoryMutationBlocked(location),
                "Only the complete outer loader may release the source guard");
        // This test intercepts the atomic adapter boundary; separate production-adapter tests exercise persistence
        // ordering.
    }

    private void failBlockLoad() throws Exception {
        store.put(BAD);
        assertThrows(IllegalStateException.class, () -> controller.loadBlockData(data));
        assertGuardedBlockUntouched();
    }

    private SlimefunUniversalBlockData failUniversal(boolean cached, String savedLocation) throws Exception {
        var universal = cached
                ? new SlimefunUniversalBlockData(UUID.randomUUID(), "LIFECYCLE_UNIVERSAL", location)
                : new SlimefunUniversalBlockData(UUID.randomUUID(), "LIFECYCLE_UNIVERSAL");
        universal.initTraits();
        controller.savedLocation = savedLocation;
        store.put(BAD);
        assertThrows(IllegalStateException.class, () -> controller.loadUniversalData(universal));
        return universal;
    }

    private void assertGuardedBlockUntouched() throws Exception {
        assertTrue(controller.isInventoryMutationBlocked(location));
        assertSame(data, controller.getBlockDataFromCache(location));
        assertFalse(data.isPendingRemove());
        assertFalse(data.isDataLoaded());
        assertTrue(controller.deleted.isEmpty());
        assertTrue(controller.directDeletes.isEmpty());
        assertTrue(controller.writes.isEmpty());
        assertArrayEquals(BAD, store.raw());
        assertTrue(Slimefun.getTickerTask().getTickLocations().isEmpty());
    }

    private byte[] validBytes() {
        return ItemStackDataCodec.serialize(StoredInventoryReaderTest.item());
    }

    private static Field field(Class<?> owner, String name) throws ReflectiveOperationException {
        var field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static final class RecordingController extends BlockDataController {
        final Store store;
        final List<RecordSet> writes = new ArrayList<>();
        final List<RecordKey> deleted = new ArrayList<>();
        final List<RecordKey> directDeletes = new ArrayList<>();
        final List<Runnable> queued = new ArrayList<>();
        String savedLocation;
        Runnable onRead;
        Runnable beforeUniversalRecord;
        BlockStorageMigration committedMigration;

        RecordingController(Store store) {
            this.store = store;
        }

        @Override
        protected List<RecordSet> getData(RecordKey key) {
            if (onRead != null) {
                var task = onRead;
                onRead = null;
                task.run();
            }
            if (committedMigration != null && key.getScope() == DataScope.UNIVERSAL_INVENTORY) {
                return committedMigration.inventory().entrySet().stream()
                        .map(entry -> StoredInventoryReaderTest.row(String.valueOf(entry.getKey()), entry.getValue()))
                        .toList();
            }
            if (committedMigration != null && key.getScope() == DataScope.UNIVERSAL_DATA) {
                return committedMigration.destinationData().entrySet().stream()
                        .map(entry -> {
                            var row = new RecordSet();
                            row.put(FieldKey.DATA_KEY, entry.getKey());
                            row.put(FieldKey.DATA_VALUE, entry.getValue());
                            return row;
                        })
                        .toList();
            }
            if (key.getScope() == DataScope.BLOCK_INVENTORY || key.getScope() == DataScope.UNIVERSAL_INVENTORY) {
                return List.of(StoredInventoryReaderTest.row("8", store.raw()));
            }
            if (key.getScope() == DataScope.UNIVERSAL_DATA && savedLocation != null) {
                var row = new RecordSet();
                row.put(FieldKey.DATA_KEY, "location");
                row.put(FieldKey.DATA_VALUE, DataUtils.blockDataBase64(savedLocation));
                return List.of(row);
            }
            return List.of();
        }

        @Override
        protected CompletableFuture<Void> persistUniversalMigration(BlockStorageMigration migration) {
            if (beforeUniversalRecord != null) beforeUniversalRecord.run();
            committedMigration = migration;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        protected CompletableFuture<Void> scheduleWriteTaskWithCompletion(
                ScopeKey scope, RecordKey key, RecordSet record, boolean serial) {
            if (key.getScope() == DataScope.UNIVERSAL_RECORD && beforeUniversalRecord != null)
                beforeUniversalRecord.run();
            writes.add(record);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        protected CompletableFuture<Void> scheduleDeleteTaskWithCompletion(
                ScopeKey scope, RecordKey key, boolean serial) {
            deleted.add(key);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        protected void deleteData(RecordKey key) {
            directDeletes.add(key);
        }

        @Override
        protected void scheduleWriteTask(Runnable task) {
            queued.add(task);
        }
    }

    private static final class BlockPreset extends BlockMenuPreset {
        BlockPreset() {
            super("LIFECYCLE_BLOCK", "Existing machine");
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
            super("LIFECYCLE_UNIVERSAL", "Existing universal machine");
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
            try (var sql = connection.createStatement()) {
                sql.execute("CREATE TABLE item (slot INTEGER PRIMARY KEY, bytes BLOB NOT NULL)");
            }
        }

        void put(byte[] bytes) throws Exception {
            try (var sql = connection.prepareStatement("INSERT OR REPLACE INTO item VALUES (8,?)")) {
                sql.setBytes(1, bytes);
                sql.executeUpdate();
            }
        }

        byte[] raw() {
            try (var sql = connection.createStatement();
                    var rows = sql.executeQuery("SELECT bytes FROM item WHERE slot=8")) {
                return rows.next() ? rows.getBytes(1) : null;
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        }

        void reopen() throws Exception {
            connection.close();
            connection = DriverManager.getConnection(jdbc);
        }

        @Override
        public void close() throws Exception {
            connection.close();
        }
    }
}
