package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.FieldKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordSet;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.ScopeKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.DataUtils;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.ItemStackDataCodec;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerBackpack;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * Real controller staging/acknowledgement with controlled database completions and disposable SQLite.
 * The fixture deliberately replaces the executor/adapter hand-off, not the item codec or save logic.
 * It is not a live-server, crash-recovery, or Folia region-ownership test.
 */
class InventorySerializationFailureTest {
    private static final NamespacedKey ITEM_ID = NamespacedKey.fromString("slimefun:slimefun_item");
    private static final String PAPER_EMPTY = "Empty itemstack cannot be serialized";
    private ServerMock server;

    @TempDir
    Path directory;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        server.addSimpleWorld("old_world");
        Class.forName("org.sqlite.JDBC");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void recordSetPreservesExistingValueWhenNonEmptyPaperSerializationFails() {
        var record = new RecordSet();
        byte[] original = ItemStackDataCodec.serialize(oldItem(37));
        record.put(FieldKey.INVENTORY_ITEM, original);
        record.put(FieldKey.BACKPACK_ID, "existing-backpack");
        var faulty = new FaultyItem(Failure.EMPTY_MESSAGE);

        assertThrows(IllegalArgumentException.class, () -> record.put(FieldKey.INVENTORY_ITEM, faulty));

        assertArrayEquals(original, (byte[]) record.getValue(FieldKey.INVENTORY_ITEM));
        assertEquals("existing-backpack", record.getString(FieldKey.BACKPACK_ID));
        assertEquals(1, faulty.control.serializations.get());
        assertEquals(17, faulty.getAmount());
    }

    @Test
    @SuppressWarnings("deprecation") // Verify the retained addon-facing String encoder, not a new writer.
    void successfulStrictAndLegacyWritesHaveTheSameExistingFormat() throws Exception {
        var item = oldItem(37);
        byte[] expected = ItemStackDataCodec.serialize(item);
        byte[] strict = DataUtils.serializeItemStackBytesForStorage(item);

        assertArrayEquals(expected, strict);
        assertArrayEquals(expected, DataUtils.serializeItemStackBytes(item));
        assertEquals(Base64.getEncoder().encodeToString(expected), DataUtils.serializeItemStack(item));
        var restored = ItemStackDataCodec.deserialize(strict);
        assertEquals(37, restored.getAmount());
        assertEquals(item.getItemMeta(), restored.getItemMeta());
    }

    @Test
    void genuinelyEmptyInputsStillHaveTheEstablishedEmptyRepresentation() {
        ItemStack zero = new ItemStack(Material.DIAMOND, 1);
        zero.setAmount(0);
        for (ItemStack empty : Arrays.asList(null, new ItemStack(Material.AIR),
                new ItemStack(Material.CAVE_AIR), new ItemStack(Material.VOID_AIR), zero)) {
            assertArrayEquals(new byte[0], DataUtils.serializeItemStackBytesForStorage(empty));
            assertArrayEquals(new byte[0], DataUtils.serializeItemStackBytes(empty));
        }
    }

    @Test
    void strictStorageDoesNotConvertNativeOrLinkageFailuresIntoEmptyBytes() {
        var nativeFailure = new FaultyItem(Failure.SERIALIZE);
        var error = assertThrows(IllegalStateException.class,
                () -> DataUtils.serializeItemStackBytesForStorage(nativeFailure));
        assertEquals("injected native serialization failure", error.getMessage());
        assertEquals(17, nativeFailure.getAmount());

        var linkageFailure = new FaultyItem(Failure.LINKAGE);
        assertThrows(NoClassDefFoundError.class,
                () -> DataUtils.serializeItemStackBytesForStorage(linkageFailure));
        assertEquals(17, linkageFailure.getAmount());
    }

    @Test
    void legacyEmptyRaceCompatibilityIsRetainedButStorageRefusesTheSameNonEmptySnapshot() {
        var faulty = new FaultyItem(Failure.EMPTY_MESSAGE);
        assertArrayEquals(new byte[0], DataUtils.serializeItemStackBytes(faulty));
        assertThrows(IllegalArgumentException.class,
                () -> DataUtils.serializeItemStackBytesForStorage(faulty));
        assertEquals(17, faulty.getAmount());
    }

    @Test
    void readOnlyRecordRejectsBeforeRunningAnAddonSerializer() {
        var record = new RecordSet();
        byte[] original = ItemStackDataCodec.serialize(oldItem(37));
        record.put(FieldKey.INVENTORY_ITEM, original);
        record.readonly();
        var faulty = new FaultyItem(Failure.SERIALIZE);

        assertThrows(IllegalStateException.class, () -> record.put(FieldKey.INVENTORY_ITEM, faulty));
        assertEquals(0, faulty.control.serializations.get());
        assertArrayEquals(original, (byte[]) record.getValue(FieldKey.INVENTORY_ITEM));
    }

    @Test
    void maintenanceCannotConstructAnEmptyReplacementFromAFailedNonEmptyItem() {
        var current = PersistedItemStorageMaintenance.StoredItemValue.current(oldItem(37));
        byte[] before = current.binaryCopy();
        assertThrows(IllegalArgumentException.class, () -> PersistedItemStorageMaintenance.StoredItemValue.current(
                new FaultyItem(Failure.EMPTY_MESSAGE)));
        assertArrayEquals(before, current.binaryCopy());
        byte[] copy = current.binaryCopy();
        copy[0] ^= 1;
        assertArrayEquals(before, current.binaryCopy());
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void stagingFailurePreservesAllRowsAndAcknowledgementsAndRetrySucceeds(Kind kind) throws Exception {
        try (var harness = new Harness(kind)) {
            harness.set(0, oldItem(37));
            harness.set(8, new ItemStack(Material.DIAMOND, 17));
            harness.saveAndComplete();
            Map<Integer, byte[]> before = harness.store.rows();

            for (Failure failure : Failure.values()) {
                harness.set(0, oldItem(12));
                var faulty = new FaultyItem(failure);
                harness.set(8, faulty);
                faulty.control.cloneFailureEnabled = true;
                int submittedBefore = harness.store.submitted;
                Object acknowledged = harness.acknowledgedSnapshot();

                CompletableFuture<Void> rejected = assertDoesNotThrow(harness::save);
                assertTrue(rejected.isCompletedExceptionally());
                assertThrows(CompletionException.class, rejected::join);
                assertEquals(submittedBefore, harness.store.submitted,
                        "A later bad slot must prevent earlier valid slots or deletes from reaching storage");
                assertSame(acknowledged, harness.acknowledgedSnapshot(), "A failed stage must not be acknowledged");
                assertRowsEqual(before, harness.store.rows());
            }

            harness.set(8, new ItemStack(Material.DIAMOND, 22));
            var retry = harness.save();
            assertFalse(retry.isDone());
            assertRowsEqual(before, harness.store.rows());
            harness.store.completeBatch(false);
            retry.join();
            assertEquals(12, harness.store.item(0).getAmount());
            assertEquals("COMPRESSED_CARBON", harness.store.item(0).getItemMeta()
                    .getPersistentDataContainer().get(ITEM_ID, PersistentDataType.STRING));
            assertEquals(22, harness.store.item(8).getAmount());
            int submitted = harness.store.submitted;
            harness.save().join();
            assertEquals(submitted, harness.store.submitted, "A successful retry must acknowledge its snapshot");
            var saved = harness.store.rows();
            harness.store.reopen();
            assertRowsEqual(saved, harness.store.rows());
        }
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void fullBatchEmptySlotsDeleteRecordsInsteadOfWritingEmptyBlobs(Kind kind) throws Exception {
        try (var harness = new Harness(kind)) {
            for (int i = 0; i < 3; i++) {
                harness.set(i, oldItem(10 + i));
            }
            harness.saveAndComplete();
            harness.set(0, null);
            harness.set(1, new ItemStack(Material.AIR));
            var zero = new ItemStack(Material.DIAMOND, 1);
            zero.setAmount(0);
            harness.set(2, zero);
            int writes = harness.store.writes;
            harness.saveAndComplete();
            assertTrue(harness.store.rows().isEmpty());
            assertEquals(writes, harness.store.writes, "Real empty states must be deletes, not zero-byte item writes");
        }
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void partialDatabaseFailureDoesNotAcknowledgeAndRetryReconciles(Kind kind) throws Exception {
        try (var harness = new Harness(kind)) {
            harness.set(0, oldItem(37));
            harness.set(8, new ItemStack(Material.DIAMOND, 17));
            harness.saveAndComplete();
            Object before = harness.acknowledgedSnapshot();
            harness.set(0, oldItem(12));
            harness.set(8, null);
            var attempt = harness.save();
            harness.store.completeBatch(true);
            assertThrows(CompletionException.class, attempt::join);
            if (kind == Kind.BACKPACK) {
                assertSame(before, harness.acknowledgedSnapshot());
            } else {
                assertNull(harness.acknowledgedSnapshot(), "Uncertain database baseline must be invalidated");
            }
            int previousSubmissions = harness.store.submitted;
            var retry = harness.save();
            assertEquals(54, harness.store.submitted - previousSubmissions);
            harness.store.completeBatch(false);
            retry.join();
            assertEquals(12, harness.store.item(0).getAmount());
            assertNull(harness.store.item(8));
            int submitted = harness.store.submitted;
            harness.save().join();
            assertEquals(submitted, harness.store.submitted);
        }
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    void overlappingSaveSnapshotsRemainOrderedUntilEachDatabaseCompletion(Kind kind) throws Exception {
        try (var harness = new Harness(kind)) {
            harness.set(0, oldItem(37));
            var first = harness.save();
            int submitted = harness.store.submitted;
            harness.set(0, oldItem(12));
            var second = harness.save();
            assertEquals(submitted, harness.store.submitted, "Second state must not overtake an unfinished save");
            harness.store.completeBatch(false);
            first.join();
            assertFalse(second.isDone());
            assertEquals(37, harness.store.item(0).getAmount(), "First save must retain its cloned item amount");
            harness.store.completeBatch(false);
            second.join();
            assertEquals(12, harness.store.item(0).getAmount());
            int acknowledged = harness.store.submitted;
            harness.save().join();
            assertEquals(acknowledged, harness.store.submitted);
        }
    }

    private static ItemStack oldItem(int amount) {
        var item = new ItemStack(Material.COAL, amount);
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(ITEM_ID, PersistentDataType.STRING, "COMPRESSED_CARBON");
        meta.getPersistentDataContainer().set(NamespacedKey.fromString("legacyaddon:retained"),
                PersistentDataType.LONG, 9_000_000_001L);
        item.setItemMeta(meta);
        return item;
    }

    private static void assertRowsEqual(Map<Integer, byte[]> expected, Map<Integer, byte[]> actual) {
        assertEquals(expected.keySet(), actual.keySet());
        expected.forEach((slot, bytes) -> assertArrayEquals(bytes, actual.get(slot), "Slot " + slot));
    }

    private enum Kind { BLOCK, UNIVERSAL, BACKPACK }
    private enum Failure { SERIALIZE, EMPTY_MESSAGE, LINKAGE, CLONE }

    private static final class FailureControl {
        private final AtomicInteger serializations = new AtomicInteger();
        private boolean cloneFailureEnabled;
    }

    private static final class FaultyItem extends ItemStack {
        private final Failure failure;
        private final FailureControl control;

        private FaultyItem(Failure failure) {
            this(failure, new FailureControl());
        }

        private FaultyItem(Failure failure, FailureControl control) {
            super(Material.DIAMOND, 17);
            this.failure = failure;
            this.control = control;
        }

        @Override
        public byte[] serializeAsBytes() {
            control.serializations.incrementAndGet();
            if (failure == Failure.LINKAGE) {
                throw new NoClassDefFoundError("injected codec linkage failure");
            }
            if (failure == Failure.EMPTY_MESSAGE) {
                throw new IllegalArgumentException(PAPER_EMPTY);
            }
            throw new IllegalStateException("injected native serialization failure");
        }

        @Override
        public FaultyItem clone() {
            if (failure == Failure.CLONE && control.cloneFailureEnabled) {
                throw new IllegalStateException("injected snapshot failure");
            }
            return new FaultyItem(failure, control);
        }
    }

    private final class Harness implements AutoCloseable {
        private final Store store;
        private final RecordingBlockController blocks;
        private final RecordingProfileController profiles;
        private final TestBlock block;
        private final TestUniversal universal;
        private final PlayerBackpack backpack;
        private final Kind kind;
        private final ItemStack[] contents = new ItemStack[9];

        private Harness(Kind kind) throws Exception {
            this.kind = kind;
            store = new Store(directory.resolve(UUID.randomUUID() + ".db"));
            blocks = kind == Kind.BACKPACK ? null : new RecordingBlockController(store);
            profiles = kind == Kind.BACKPACK ? new RecordingProfileController(store) : null;
            block = kind == Kind.BLOCK ? new TestBlock(contents) : null;
            universal = kind == Kind.UNIVERSAL ? new TestUniversal(contents) : null;
            backpack = kind == Kind.BACKPACK ? new PlayerBackpack(server.addPlayer(), UUID.randomUUID(),
                    "Old backpack", 18, 9, null) : null;
        }

        private void set(int slot, ItemStack item) {
            if (backpack != null) {
                backpack.getInventory().setItem(slot, item);
            } else {
                contents[slot] = item;
            }
        }

        private CompletableFuture<Void> save() {
            return switch (kind) {
                case BLOCK -> blocks.saveBlockInventoryAsync(block);
                case UNIVERSAL -> blocks.saveUniversalInventoryAsync(universal);
                case BACKPACK -> profiles.saveBackpackInventoryAsync(backpack);
            };
        }

        private void saveAndComplete() throws Exception {
            var future = save();
            store.completeBatch(false);
            future.join();
        }

        private Object acknowledgedSnapshot() throws Exception {
            if (backpack != null) {
                return backpack.getSnapshot();
            }
            var field = BlockDataController.class.getDeclaredField("invSnapshots");
            field.setAccessible(true);
            var snapshots = (Map<?, ?>) field.get(blocks);
            return snapshots.get(block == null ? universal.getKey() : block.getKey());
        }

        @Override
        public void close() throws Exception {
            try {
                if (profiles != null) {
                    var field = ProfileDataController.class.getDeclaredField("backpackCache");
                    field.setAccessible(true);
                    ((BackpackCache) field.get(profiles)).clean();
                }
            } finally {
                store.close();
            }
        }
    }

    private final class TestBlock extends SlimefunBlockData {
        private final ItemStack[] contents;
        private TestBlock(ItemStack[] contents) {
            super(new Location(server.getWorld("old_world"), 10, 64, -20), "ELECTRIC_FURNACE");
            this.contents = contents;
        }
        @Override
        public ItemStack[] getMenuContents() { return contents; }
    }

    private static final class TestUniversal extends SlimefunUniversalData {
        private final ItemStack[] contents;
        private TestUniversal(ItemStack[] contents) {
            super(UUID.randomUUID(), "ELECTRIC_FURNACE");
            this.contents = contents;
        }
        @Override
        public ItemStack[] getMenuContents() { return contents; }
    }

    private static final class RecordingBlockController extends BlockDataController {
        private final Store store;
        private RecordingBlockController(Store store) { this.store = store; }
        @Override
        protected CompletableFuture<Void> scheduleWriteTaskWithCompletion(
                ScopeKey scope, RecordKey key, RecordSet data, boolean serial) {
            return store.submit(key, data);
        }
        @Override
        protected CompletableFuture<Void> scheduleDeleteTaskWithCompletion(
                ScopeKey scope, RecordKey key, boolean serial) {
            return store.submit(key, null);
        }
    }

    private static final class RecordingProfileController extends ProfileDataController {
        private final Store store;
        private RecordingProfileController(Store store) { this.store = store; }
        @Override
        protected CompletableFuture<Void> scheduleWriteTaskWithCompletion(
                ScopeKey scope, RecordKey key, RecordSet data, boolean serial) {
            return store.submit(key, data);
        }
        @Override
        protected CompletableFuture<Void> scheduleDeleteTaskWithCompletion(
                ScopeKey scope, RecordKey key, boolean serial) {
            return store.submit(key, null);
        }
    }

    private static final class Store implements AutoCloseable {
        private final String jdbc;
        private Connection connection;
        private final List<Pending> pending = new ArrayList<>();
        private int submitted;
        private int writes;

        private Store(Path path) throws SQLException {
            jdbc = "jdbc:sqlite:" + path;
            connection = DriverManager.getConnection(jdbc);
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE inventory (scope TEXT, owner TEXT, slot INTEGER, item BLOB, "
                        + "PRIMARY KEY (scope, owner, slot))");
            }
        }

        private CompletableFuture<Void> submit(RecordKey key, RecordSet data) {
            String owner = null;
            int slot = -1;
            for (var condition : key.getConditions()) {
                if (condition.getFirstValue() == FieldKey.INVENTORY_SLOT) {
                    slot = Integer.parseInt(condition.getSecondValue());
                } else {
                    owner = condition.getSecondValue();
                }
            }
            assertNotNull(owner);
            assertTrue(slot >= 0 && slot < 54);
            byte[] bytes = data == null ? null : ((byte[]) data.getValue(FieldKey.INVENTORY_ITEM)).clone();
            if (bytes != null) {
                assertTrue(bytes.length > 0, "A non-empty write must never be replaced by an empty blob");
            }
            var result = new CompletableFuture<Void>();
            pending.add(new Pending(key.getScope().name(), owner, slot, bytes, result));
            submitted++;
            return result;
        }

        private void completeBatch(boolean failFirst) throws SQLException {
            List<Pending> batch = List.copyOf(pending);
            pending.clear();
            for (int i = 0; i < batch.size(); i++) {
                Pending write = batch.get(i);
                if (failFirst && i == 0) {
                    write.completion().completeExceptionally(new SQLException("injected database failure"));
                    continue;
                }
                String sql = write.bytes() == null
                        ? "DELETE FROM inventory WHERE scope=? AND owner=? AND slot=?"
                        : "INSERT OR REPLACE INTO inventory (scope, owner, slot, item) VALUES (?, ?, ?, ?)";
                try (var statement = connection.prepareStatement(sql)) {
                    statement.setString(1, write.scope());
                    statement.setString(2, write.owner());
                    statement.setInt(3, write.slot());
                    if (write.bytes() != null) {
                        statement.setBytes(4, write.bytes());
                        writes++;
                    }
                    statement.executeUpdate();
                }
                write.completion().complete(null);
            }
        }

        private Map<Integer, byte[]> rows() throws SQLException {
            Map<Integer, byte[]> result = new HashMap<>();
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT slot, item FROM inventory")) {
                while (rows.next()) {
                    result.put(rows.getInt(1), rows.getBytes(2));
                }
            }
            return result;
        }

        private ItemStack item(int slot) throws Exception {
            byte[] bytes = rows().get(slot);
            return bytes == null ? null : ItemStackDataCodec.deserialize(bytes);
        }

        private void reopen() throws SQLException {
            connection.close();
            connection = DriverManager.getConnection(jdbc);
        }

        @Override
        public void close() throws SQLException { connection.close(); }

        private record Pending(String scope, String owner, int slot, byte[] bytes, CompletableFuture<Void> completion) {}
    }
}
