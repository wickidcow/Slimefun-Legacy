package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import static org.junit.jupiter.api.Assertions.*;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.FieldKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordSet;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.ScopeKey;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

    @SuppressWarnings("unchecked")
    private void hold(String backpackId) throws Exception {
        Field field = ProfileDataController.class.getDeclaredField("incompleteInventoryLoads");
        field.setAccessible(true);
        ((Set<String>) field.get(controller)).add(backpackId);
    }

    private static byte[] broken(byte suffix) {
        return new byte[] {'S', 'F', '2', 0, suffix};
    }

    private static final class TestProfiles extends ProfileDataController {
        private final Path archiveRoot;
        private final AtomicInteger deletes = new AtomicInteger();
        private final Map<String, RecordSet> inventory = new LinkedHashMap<>();
        private RecordSet profile;

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
