package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import static org.junit.jupiter.api.Assertions.*;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.FieldKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordSet;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.ScopeKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.ItemStackDataCodec;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipFile;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;

class BackpackRecoveryControllerTest {
    @TempDir
    Path directory;

    private TestProfiles controller;

    @AfterEach
    void tearDown() throws Exception {
        if (controller != null) {
            Field field = ProfileDataController.class.getDeclaredField("backpackCache");
            field.setAccessible(true);
            ((BackpackCache) field.get(controller)).clean();
        }
    }

    @Test
    void scanFindsOnlyUnreadableRowsAndFingerprintsWholeBackpackState() throws Exception {
        controller = new TestProfiles(directory);
        String backpackId = UUID.randomUUID().toString();
        controller.profile(backpackId, UUID.randomUUID().toString(), 9);
        controller.put("0", new byte[0]);
        controller.put("8", broken((byte) 1));
        hold(backpackId);

        var first = controller.scanBackpackRecovery(backpackId);
        assertTrue(first.isEligibleForQuarantine());
        assertEquals(2, first.storedRows());
        assertEquals(1, first.unreadableRows().size());
        assertEquals(8, first.unreadableRows().getFirst().slot());
        assertTrue(first.unreadableRows().getFirst().failure().contains("Exception")
                || first.unreadableRows().getFirst().failure().contains("Error"));

        controller.putText("0", " ");
        var second = controller.scanBackpackRecovery(backpackId);
        assertEquals(1, second.unreadableRows().size());
        assertNotEquals(first.fingerprint(), second.fingerprint(), "healthy-row changes must invalidate authorization");
    }

    @Test
    void quarantineWritesExactPayloadBeforeDeletingOnlyUnreadableRowsAndKeepsHold() throws Exception {
        controller = new TestProfiles(directory);
        String backpackId = UUID.randomUUID().toString();
        String owner = UUID.randomUUID().toString();
        byte[] bad = broken((byte) 7);
        controller.profile(backpackId, owner, 9);
        controller.put("0", new byte[0]);
        controller.put("8", bad);
        hold(backpackId);

        var scan = controller.scanBackpackRecovery(backpackId);
        var result = controller.quarantineUnreadableBackpackRows(backpackId, scan.fingerprint());

        assertEquals(1, result.quarantinedRows());
        assertEquals(1, controller.deletes.get());
        assertTrue(controller.inventory.containsKey("0"));
        assertFalse(controller.inventory.containsKey("8"));
        assertEquals(1, controller.getInventoryRecoverySnapshot().count(InventoryRecoverySnapshot.Kind.BACKPACK_LOAD));

        Path archive = Path.of(result.archivePath());
        assertTrue(Files.isRegularFile(archive));
        try (var zip = new ZipFile(archive.toFile())) {
            assertNotNull(zip.getEntry("manifest.txt"));
            var slot = zip.getEntry("slots/slot-8.bin");
            assertNotNull(slot);
            assertArrayEquals(bad, zip.getInputStream(slot).readAllBytes());
            String manifest = new String(zip.getInputStream(zip.getEntry("manifest.txt")).readAllBytes());
            assertTrue(manifest.contains("backpack=" + backpackId));
            assertTrue(manifest.contains("owner=" + owner));
            assertTrue(manifest.contains("slot=8"));
            assertTrue(manifest.contains(scan.fingerprint()));
        }
    }

    @Test
    void staleFingerprintRefusesBeforeArchiveOrDelete() throws Exception {
        controller = new TestProfiles(directory);
        String backpackId = UUID.randomUUID().toString();
        controller.profile(backpackId, UUID.randomUUID().toString(), 9);
        controller.put("8", broken((byte) 1));
        hold(backpackId);

        String stale = controller.scanBackpackRecovery(backpackId).fingerprint();
        controller.put("8", broken((byte) 2));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> controller.quarantineUnreadableBackpackRows(backpackId, stale));
        assertTrue(failure.getMessage().contains("state changed"));
        assertEquals(0, controller.deletes.get());
        assertTrue(controller.inventory.containsKey("8"));
        assertFalse(Files.exists(directory.resolve("backpacks")));
    }

    @Test
    void quarantineRequiresExistingFailedLoadHold() throws Exception {
        controller = new TestProfiles(directory);
        String backpackId = UUID.randomUUID().toString();
        controller.profile(backpackId, UUID.randomUUID().toString(), 9);
        controller.put("8", broken((byte) 3));

        var scan = controller.scanBackpackRecovery(backpackId);
        assertFalse(scan.loadHeld());
        assertFalse(scan.isEligibleForQuarantine());
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> controller.quarantineUnreadableBackpackRows(backpackId, scan.fingerprint()));
        assertTrue(failure.getMessage().contains("not under an incomplete-load hold"));
        assertEquals(0, controller.deletes.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"08", "+8", "-0", " 8", "eight", "2147483648"})
    void ambiguousSlotNamesRefuseRecoveryWithoutChangingAnyRows(String slot) throws Exception {
        controller = new TestProfiles(directory);
        String backpackId = UUID.randomUUID().toString();
        controller.profile(backpackId, UUID.randomUUID().toString(), 9);
        controller.put("8", new byte[0]);
        controller.put(slot, broken((byte) 1));
        hold(backpackId);

        assertThrows(IllegalStateException.class, () -> controller.scanBackpackRecovery(backpackId));
        assertEquals(0, controller.deletes.get());
        assertEquals(2, controller.inventory.size());
        assertTrue(controller.inventory.containsKey("8"));
        assertFalse(Files.exists(directory.resolve("backpacks")));
    }

    @Test
    void duplicateSlotsRefuseEvenWhenOnePayloadIsHealthy() throws Exception {
        controller = new TestProfiles(directory);
        String backpackId = UUID.randomUUID().toString();
        controller.profile(backpackId, UUID.randomUUID().toString(), 9);
        controller.put("8", new byte[0]);
        controller.inventory.put("duplicate-row", StoredInventoryReaderTest.row("8", broken((byte) 3)));
        hold(backpackId);

        assertThrows(IllegalStateException.class, () -> controller.scanBackpackRecovery(backpackId));
        assertEquals(0, controller.deletes.get());
        assertEquals(2, controller.inventory.size());
    }

    @Test
    void archiveFailureLeavesEveryRowAndTheLoadHoldIntact() throws Exception {
        controller = new TestProfiles(directory);
        String backpackId = UUID.randomUUID().toString();
        controller.profile(backpackId, UUID.randomUUID().toString(), 9);
        controller.put("8", broken((byte) 4));
        hold(backpackId);
        Files.writeString(directory.resolve("backpacks"), "blocks directory creation");
        var scan = controller.scanBackpackRecovery(backpackId);

        var failure = assertThrows(IllegalStateException.class,
                () -> controller.quarantineUnreadableBackpackRows(backpackId, scan.fingerprint()));

        assertTrue(failure.getMessage().contains("no rows were deleted"));
        assertEquals(0, controller.deletes.get());
        assertTrue(controller.inventory.containsKey("8"));
        assertTrue(controller.scanBackpackRecovery(backpackId).loadHeld());
    }

    @Test
    @SuppressWarnings("unchecked")
    void pendingSaveBlocksQuarantineBeforeArchiveCreation() throws Exception {
        controller = new TestProfiles(directory);
        String backpackId = UUID.randomUUID().toString();
        controller.profile(backpackId, UUID.randomUUID().toString(), 9);
        controller.put("8", broken((byte) 5));
        hold(backpackId);
        Field field = ProfileDataController.class.getDeclaredField("backpackSaveChains");
        field.setAccessible(true);
        var chains = (Map<String, CompletableFuture<Void>>) field.get(controller);
        chains.put(backpackId, new CompletableFuture<>());
        var scan = controller.scanBackpackRecovery(backpackId);

        assertTrue(scan.savePending());
        assertFalse(scan.isEligibleForQuarantine());
        assertThrows(IllegalStateException.class,
                () -> controller.quarantineUnreadableBackpackRows(backpackId, scan.fingerprint()));
        assertEquals(0, controller.deletes.get());
        assertFalse(Files.exists(directory.resolve("backpacks")));
    }

    @Test
    void deletionFailureKeepsTheExactArchiveAndRequiresAFreshScan() throws Exception {
        controller = new TestProfiles(directory);
        String backpackId = UUID.randomUUID().toString();
        controller.profile(backpackId, UUID.randomUUID().toString(), 9);
        byte[] firstBad = broken((byte) 6);
        byte[] secondBad = broken((byte) 7);
        controller.put("7", firstBad);
        controller.put("8", secondBad);
        controller.failDeleteSlot = "8";
        hold(backpackId);
        var scan = controller.scanBackpackRecovery(backpackId);

        var failure = assertThrows(IllegalStateException.class,
                () -> controller.quarantineUnreadableBackpackRows(backpackId, scan.fingerprint()));
        assertTrue(failure.getMessage().contains("deletions failed"));
        assertFalse(controller.inventory.containsKey("7"));
        assertTrue(controller.inventory.containsKey("8"));
        try (var files = Files.list(directory.resolve("backpacks"))) {
            var archives = files.toList();
            assertEquals(1, archives.size());
            try (var zip = new ZipFile(archives.getFirst().toFile())) {
                assertArrayEquals(firstBad, zip.getInputStream(zip.getEntry("slots/slot-7.bin")).readAllBytes());
                assertArrayEquals(secondBad, zip.getInputStream(zip.getEntry("slots/slot-8.bin")).readAllBytes());
            }
        }
        var retry = controller.scanBackpackRecovery(backpackId);
        assertTrue(retry.loadHeld());
        assertEquals(1, retry.unreadableRows().size());
        assertNotEquals(scan.fingerprint(), retry.fingerprint());
        assertThrows(IllegalStateException.class,
                () -> controller.quarantineUnreadableBackpackRows(backpackId, scan.fingerprint()));
    }

    @Test
    void quarantinePreservesHealthyOldAddonItemBytesAndProfileIdentity() throws Exception {
        MockBukkit.mock();
        try {
            controller = new TestProfiles(directory);
            String backpackId = UUID.randomUUID().toString();
            String owner = UUID.randomUUID().toString();
            controller.profile(backpackId, owner, 9);
            var item = StoredInventoryReaderTest.item();
            byte[] healthy = ItemStackDataCodec.serialize(item);
            controller.put("0", healthy);
            controller.put("8", broken((byte) 8));
            hold(backpackId);
            var scan = controller.scanBackpackRecovery(backpackId);

            controller.quarantineUnreadableBackpackRows(backpackId, scan.fingerprint());

            assertArrayEquals(healthy, (byte[]) controller.inventory.get("0").getValue(FieldKey.INVENTORY_ITEM));
            var restored = controller.inventory.get("0").getItemStack(FieldKey.INVENTORY_ITEM);
            assertEquals(item.getAmount(), restored.getAmount());
            assertEquals(item.getItemMeta(), restored.getItemMeta());
            assertEquals(backpackId, controller.profile.getString(FieldKey.BACKPACK_ID));
            assertEquals(owner, controller.profile.getString(FieldKey.PLAYER_UUID));
            assertEquals(9, controller.profile.getInt(FieldKey.BACKPACK_SIZE));
            var after = controller.scanBackpackRecovery(backpackId);
            assertTrue(after.loadHeld());
            assertTrue(after.unreadableRows().isEmpty());
            assertFalse(after.isEligibleForQuarantine());
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void interruptedQuarantineSurvivesDatabaseReopenAndNormalLoadReestablishesTheHold() throws Exception {
        MockBukkit.mock();
        String backpackId = UUID.randomUUID().toString();
        String owner = UUID.randomUUID().toString();
        Path database = directory.resolve("recovery.db");
        try {
            var item = StoredInventoryReaderTest.item();
            byte[] healthy = ItemStackDataCodec.serialize(item);
            String originalFingerprint;
            try (var first = new PersistedProfiles(database, directory.resolve("archives"))) {
                first.initialize(backpackId, owner, healthy);
                assertThrows(IllegalStateException.class, () -> first.readNormally(backpackId));
                var scan = first.scanBackpackRecovery(backpackId);
                originalFingerprint = scan.fingerprint();
                first.failDeleteSlot = "8";
                assertThrows(IllegalStateException.class,
                        () -> first.quarantineUnreadableBackpackRows(backpackId, scan.fingerprint()));
                assertTrue(first.scanBackpackRecovery(backpackId).loadHeld());
            }

            try (var restarted = new PersistedProfiles(database, directory.resolve("archives"))) {
                // A new controller must observe the real persisted failure before recovery can run.
                assertFalse(restarted.scanBackpackRecovery(backpackId).isEligibleForQuarantine());
                assertThrows(IllegalStateException.class, () -> restarted.readNormally(backpackId));
                var scan = restarted.scanBackpackRecovery(backpackId);
                assertTrue(scan.isEligibleForQuarantine());
                assertEquals(owner, scan.ownerUuid());
                assertEquals(2, scan.storedRows());
                assertEquals(8, scan.unreadableRows().getFirst().slot());
                assertNotEquals(originalFingerprint, scan.fingerprint());
                assertThrows(IllegalStateException.class,
                        () -> restarted.quarantineUnreadableBackpackRows(backpackId, originalFingerprint));
                restarted.quarantineUnreadableBackpackRows(backpackId, scan.fingerprint());
                assertTrue(restarted.scanBackpackRecovery(backpackId).loadHeld());
                var contents = restarted.readNormally(backpackId);
                assertEquals(item.getAmount(), contents[0].getAmount());
                assertEquals(item.getItemMeta(), contents[0].getItemMeta());
                assertNull(contents[7]);
                assertNull(contents[8]);
                assertFalse(restarted.scanBackpackRecovery(backpackId).loadHeld());
            }
            try (var reopened = new PersistedProfiles(database, directory.resolve("archives"))) {
                assertEquals(item.getItemMeta(), reopened.readNormally(backpackId)[0].getItemMeta());
                assertEquals(owner, reopened.scanBackpackRecovery(backpackId).ownerUuid());
                assertEquals(1, reopened.scanBackpackRecovery(backpackId).storedRows());
            }
        } finally {
            MockBukkit.unmock();
        }
    }

    @SuppressWarnings("unchecked")
    private void hold(String backpackId) throws Exception {
        Field field = ProfileDataController.class.getDeclaredField("incompleteInventoryLoads");
        field.setAccessible(true);
        ((Set<String>) field.get(controller)).add(backpackId);
    }

    private static byte[] broken(byte suffix) {
        return new byte[] {'S', 'F', '2', 0, suffix};
    }

    /** Real SQLite rows and normal reader; only executor hand-off is replaced with immediate completion. */
    private static final class PersistedProfiles extends ProfileDataController implements AutoCloseable {
        private final Connection connection;
        private final Path archives;
        private String failDeleteSlot;

        private PersistedProfiles(Path database, Path archives) throws SQLException {
            connection = DriverManager.getConnection("jdbc:sqlite:" + database);
            this.archives = archives;
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS profile(id TEXT, owner TEXT, size INTEGER)");
                statement.execute("CREATE TABLE IF NOT EXISTS inventory(slot TEXT PRIMARY KEY, item BLOB)");
            }
        }

        private void initialize(String backpackId, String owner, byte[] healthy) throws SQLException {
            try (var statement = connection.prepareStatement("INSERT INTO profile VALUES (?, ?, 9)")) {
                statement.setString(1, backpackId);
                statement.setString(2, owner);
                statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement("INSERT INTO inventory VALUES (?, ?)")) {
                for (String slot : List.of("0", "7", "8")) {
                    statement.setString(1, slot);
                    statement.setBytes(2, slot.equals("0") ? healthy : broken((byte) Integer.parseInt(slot)));
                    statement.executeUpdate();
                }
            }
        }

        private ItemStack[] readNormally(String backpackId) throws Exception {
            var method = ProfileDataController.class.getDeclaredMethod("getBackpackInv", String.class, int.class);
            method.setAccessible(true);
            try {
                return (ItemStack[]) method.invoke(this, backpackId, 9);
            } catch (InvocationTargetException failure) {
                if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
                throw failure;
            }
        }

        @Override
        protected List<RecordSet> getData(RecordKey key) {
            var result = new ArrayList<RecordSet>();
            boolean profile = key.getScope() == DataScope.BACKPACK_PROFILE;
            try (var statement = connection.createStatement();
                    var rows = statement.executeQuery(profile ? "SELECT id,owner,size FROM profile"
                            : "SELECT slot,item FROM inventory ORDER BY slot")) {
                while (rows.next()) {
                    if (profile) {
                        var row = new RecordSet();
                        row.put(FieldKey.BACKPACK_ID, rows.getString(1));
                        row.put(FieldKey.PLAYER_UUID, rows.getString(2));
                        row.put(FieldKey.BACKPACK_SIZE, rows.getString(3));
                        result.add(row);
                    } else {
                        result.add(StoredInventoryReaderTest.row(rows.getString(1), rows.getBytes(2)));
                    }
                }
            } catch (SQLException failure) {
                throw new IllegalStateException(failure);
            }
            return result;
        }

        @Override
        protected CompletableFuture<Void> scheduleDeleteTaskWithCompletion(
                ScopeKey scope, RecordKey key, boolean forceScopeKey) {
            String slot = key.getConditions().stream()
                    .filter(condition -> condition.getFirstValue() == FieldKey.INVENTORY_SLOT)
                    .map(condition -> condition.getSecondValue()).findFirst().orElseThrow();
            if (slot.equals(failDeleteSlot)) {
                return CompletableFuture.failedFuture(new IllegalStateException("injected persisted delete failure"));
            }
            try (var statement = connection.prepareStatement("DELETE FROM inventory WHERE slot=?")) {
                statement.setString(1, slot);
                statement.executeUpdate();
                return CompletableFuture.completedFuture(null);
            } catch (SQLException failure) {
                return CompletableFuture.failedFuture(failure);
            }
        }

        @Override
        protected Path getBackpackRecoveryDirectory() {
            return archives;
        }

        @Override
        public void close() throws Exception {
            Field field = ProfileDataController.class.getDeclaredField("backpackCache");
            field.setAccessible(true);
            ((BackpackCache) field.get(this)).clean();
            connection.close();
        }
    }

    private static final class TestProfiles extends ProfileDataController {
        private final Path archiveRoot;
        private final AtomicInteger deletes = new AtomicInteger();
        private final Map<String, RecordSet> inventory = new LinkedHashMap<>();
        private RecordSet profile;
        private String failDeleteSlot;

        private TestProfiles(Path directory) {
            archiveRoot = directory.resolve("backpacks");
        }

        private void profile(String backpackId, String owner, int size) {
            profile = new RecordSet();
            profile.put(FieldKey.BACKPACK_ID, backpackId);
            profile.put(FieldKey.PLAYER_UUID, owner);
            profile.put(FieldKey.BACKPACK_SIZE, Integer.toString(size));
        }

        private void put(String slot, byte[] payload) {
            var row = new RecordSet();
            row.put(FieldKey.INVENTORY_SLOT, slot);
            row.put(FieldKey.INVENTORY_ITEM, payload);
            inventory.put(slot, row);
        }

        private void putText(String slot, String payload) {
            var row = new RecordSet();
            row.put(FieldKey.INVENTORY_SLOT, slot);
            row.put(FieldKey.INVENTORY_ITEM, payload);
            inventory.put(slot, row);
        }

        @Override
        protected List<RecordSet> getData(RecordKey key) {
            if (key.getScope() == DataScope.BACKPACK_PROFILE) {
                return profile == null ? List.of() : List.of(profile);
            }
            if (key.getScope() == DataScope.BACKPACK_INVENTORY) {
                return new ArrayList<>(inventory.values());
            }
            return List.of();
        }

        @Override
        protected CompletableFuture<Void> scheduleDeleteTaskWithCompletion(
                ScopeKey scope, RecordKey key, boolean forceScopeKey) {
            String slot = key.getConditions().stream()
                    .filter(condition -> condition.getFirstValue() == FieldKey.INVENTORY_SLOT)
                    .map(condition -> condition.getSecondValue())
                    .findFirst()
                    .orElseThrow();
            if (slot.equals(failDeleteSlot)) {
                return CompletableFuture.failedFuture(new IllegalStateException("injected delete failure"));
            }
            inventory.remove(slot);
            deletes.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        protected Path getBackpackRecoveryDirectory() {
            return archiveRoot;
        }
    }
}
