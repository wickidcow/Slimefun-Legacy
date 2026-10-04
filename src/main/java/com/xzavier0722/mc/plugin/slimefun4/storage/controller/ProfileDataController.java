package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import com.xzavier0722.mc.plugin.slimefun4.storage.callback.IAsyncReadCallback;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataType;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.FieldKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordSet;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.DataUtils;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.InvSnapshot;
import io.github.thebusybiscuit.slimefun4.api.events.AsyncProfileLoadEvent;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerBackpack;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.api.researches.Research;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;

public class ProfileDataController extends ADataController {
    private final BackpackCache backpackCache;
    private final Map<String, PlayerProfile> profileCache;
    private final Map<String, Runnable> invalidingBackpackTasks;
    private final Map<String, CompletableFuture<Void>> backpackSaveChains;
    private final Set<String> uncertainBackpackBaselines;
    private final Set<String> backpackRecoveryOperations = ConcurrentHashMap.newKeySet();
    /** In-flight or failed reads must never be persisted as an empty replacement inventory. */
    private final Set<String> incompleteInventoryLoads = ConcurrentHashMap.newKeySet();

    /** Read-only, bounded diagnostics; observing a backpack hold never loads it or clears it. */
    public InventoryRecoverySnapshot getInventoryRecoverySnapshot() {
        var snapshot = new InventoryRecoverySnapshot.Collector();
        incompleteInventoryLoads.forEach(
                owner -> snapshot.add(InventoryRecoverySnapshot.Kind.BACKPACK_LOAD, owner, null));
        return snapshot.build();
    }

    ProfileDataController() {
        super(DataType.PLAYER_PROFILE);
        backpackCache = new BackpackCache();
        profileCache = new ConcurrentHashMap<>();
        invalidingBackpackTasks = new ConcurrentHashMap<>();
        backpackSaveChains = new ConcurrentHashMap<>();
        uncertainBackpackBaselines = ConcurrentHashMap.newKeySet();
    }

    public PlayerProfile getProfileFromCache(OfflinePlayer p) {
        return profileCache.get(p.getUniqueId().toString());
    }

    public CompletableFuture<PlayerProfile> getProfileAsync(OfflinePlayer p) {
        checkDestroy();
        var re = profileCache.get(p.getUniqueId().toString());
        if (re != null) {
            return CompletableFuture.completedFuture(re);
        }
        return CompletableFuture.supplyAsync(() -> loadProfile(p), readExecutor);
    }

    public CompletableFuture<PlayerProfile> getOrCreateProfileAsync(OfflinePlayer p) {
        checkDestroy();
        var re = profileCache.get(p.getUniqueId().toString());
        if (re != null) {
            return CompletableFuture.completedFuture(re);
        }
        return CompletableFuture.supplyAsync(
                        () -> {
                            var profile = loadProfile(p);
                            return profile == null ? createProfile(p) : profile;
                        },
                        readExecutor)
                .thenApplyAsync(Function.identity(), callbackExecutor);
    }

    @Nullable public PlayerProfile getProfile(OfflinePlayer p) {
        checkDestroy();
        var uid = p.getUniqueId();
        var uuid = uid.toString();
        var re = profileCache.get(uuid);
        if (re != null) {
            return re;
        }
        return loadProfile(p);
    }

    private PlayerProfile loadProfile(OfflinePlayer p) {
        PlayerProfile re;
        var uid = p.getUniqueId();
        var uuid = uid.toString();
        var key = new RecordKey(DataScope.PLAYER_PROFILE);
        key.addField(FieldKey.PLAYER_BACKPACK_NUM);
        key.addField(FieldKey.PLAYER_NAME);
        key.addCondition(FieldKey.PLAYER_UUID, uuid);

        var result = getData(key);
        if (result.isEmpty()) {
            return null;
        }

        // check player name changed or not
        var currentPlayerName = p.getName();
        if (currentPlayerName != null && !currentPlayerName.equals(result.get(0).getString(FieldKey.PLAYER_NAME))) {
            updateUsername(uuid, currentPlayerName);
        }

        var bNum = result.get(0).getInt(FieldKey.BACKPACK_NUMBER);

        var researches = new HashSet<Research>();
        getUnlockedResearchKeys(uuid).forEach(rKey -> Research.getResearch(rKey).ifPresent(researches::add));

        re = new PlayerProfile(p, bNum, researches);
        re = registerProfile(re, uid);
        profileCache.put(uuid, re);

        return re;
    }

    public void getProfileAsync(OfflinePlayer p, IAsyncReadCallback<PlayerProfile> callback) {
        scheduleReadTask(() -> invokeCallback(callback, getProfile(p)));
    }

    public CompletableFuture<PlayerBackpack> getBackpackAsync(OfflinePlayer owner, int num) {
        checkDestroy();
        var uuid = owner.getUniqueId().toString();
        var re = backpackCache.get(uuid, num);
        if (re != null) {
            return CompletableFuture.completedFuture(re);
        }
        return CompletableFuture.supplyAsync(() -> getBackpack(owner, num), readExecutor);
    }

    @Nullable public PlayerBackpack getBackpack(OfflinePlayer owner, int num) {
        checkDestroy();
        var uuid = owner.getUniqueId().toString();
        return backpackCache.getOrLoad(uuid, num, () -> {
            var key = new RecordKey(DataScope.BACKPACK_PROFILE);
            key.addField(FieldKey.BACKPACK_ID);
            key.addField(FieldKey.BACKPACK_SIZE);
            key.addField(FieldKey.BACKPACK_NAME);
            key.addCondition(FieldKey.PLAYER_UUID, uuid);
            key.addCondition(FieldKey.BACKPACK_NUMBER, num + "");

            var bResult = getData(key);
            if (bResult.isEmpty()) {
                return null;
            }

            var result = bResult.get(0);
            var size = Integer.parseInt(bResult.get(0).getString(FieldKey.BACKPACK_SIZE));
            var idStr = result.getString(FieldKey.BACKPACK_ID);

            return new PlayerBackpack(
                    owner,
                    UUID.fromString(idStr),
                    DataUtils.profileDataDebase64(result.getOrDef(FieldKey.BACKPACK_NAME, "")),
                    num,
                    size,
                    getBackpackInv(idStr, size));
        });
    }

    public CompletableFuture<PlayerBackpack> getBackpackAsync(String uuid) {
        checkDestroy();
        var re = backpackCache.get(uuid);
        if (re != null) {
            return CompletableFuture.completedFuture(re);
        }
        return CompletableFuture.supplyAsync(() -> getBackpack(uuid), readExecutor);
    }

    /** Returns every stored backpack UUID without loading backpack inventories. */
    public CompletableFuture<Set<String>> getAllBackpackIdsAsync() {
        checkDestroy();
        return CompletableFuture.supplyAsync(
                () -> {
                    var key = new RecordKey(DataScope.BACKPACK_PROFILE);
                    key.addField(FieldKey.BACKPACK_ID);
                    return getData(key).stream()
                            .map(record -> record.getString(FieldKey.BACKPACK_ID))
                            .filter(id -> id != null && !id.isBlank())
                            .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
                },
                readExecutor);
    }

    /**
     * Loads a backpack for a maintenance operation without evicting or replacing an instance
     * already used by normal gameplay.
     */
    public CompletableFuture<MaintenanceBackpack> getBackpackForMaintenanceAsync(String uuid) {
        checkDestroy();
        return CompletableFuture.supplyAsync(
                () -> {
                    PlayerBackpack cached = backpackCache.peek(uuid);
                    if (cached != null) {
                        return new MaintenanceBackpack(cached, false);
                    }

                    PlayerBackpack loaded = loadBackpackByUuid(uuid);
                    if (loaded == null) {
                        return null;
                    }

                    BackpackCache.MaintenanceResult result = backpackCache.putForMaintenance(loaded);
                    return new MaintenanceBackpack(result.backpack(), result.maintenanceOwned());
                },
                readExecutor);
    }

    /** Releases a backpack only when it remained exclusive to the maintenance operation. */
    public void releaseMaintenanceBackpack(@Nonnull PlayerBackpack backpack) {
        backpackCache.releaseMaintenance(backpack);
    }

    public record MaintenanceBackpack(@Nonnull PlayerBackpack backpack, boolean maintenanceOwned) {}

    /**
     * One raw backpack row that cannot be decoded safely by the normal inventory reader.
     *
     * <p>The command surface exposes only the slot, payload digest and bounded failure summary.
     * Original bytes stay inside the controller until an explicit quarantine execution writes
     * them to the recovery archive.
     */
    public record BackpackRecoveryCandidate(int slot, @Nonnull String payloadSha256, @Nonnull String failure) {}

    /**
     * Read-only fingerprinted view of one held backpack.
     *
     * <p>The fingerprint covers the complete stored inventory state, not only unreadable rows, so
     * execution fails closed if any row changes between scan and quarantine.
     */
    public record BackpackRecoveryScan(
            @Nonnull String backpackId,
            @Nonnull String ownerUuid,
            int backpackSize,
            int storedRows,
            boolean loadHeld,
            boolean cached,
            boolean savePending,
            @Nonnull List<BackpackRecoveryCandidate> unreadableRows,
            @Nonnull String fingerprint) {
        public BackpackRecoveryScan {
            unreadableRows = List.copyOf(unreadableRows);
        }

        public boolean isEligibleForQuarantine() {
            return loadHeld && !cached && !savePending && !unreadableRows.isEmpty();
        }
    }

    /** Result returned only after the quarantine archive exists and every targeted delete completed. */
    public record BackpackRecoveryExecution(
            @Nonnull BackpackRecoveryScan scan, int quarantinedRows, @Nonnull String archivePath) {}

    private record RawBackpackRecoveryRow(
            int slot,
            @Nonnull String representation,
            @Nonnull byte[] payload,
            @Nonnull String payloadSha256,
            @Nonnull String failure) {
        private RawBackpackRecoveryRow {
            payload = payload.clone();
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }
    }

    private record BackpackRecoveryInspection(
            @Nonnull BackpackRecoveryScan scan, @Nonnull List<RawBackpackRecoveryRow> unreadableRows) {
        private BackpackRecoveryInspection {
            unreadableRows = List.copyOf(unreadableRows);
        }
    }

    /**
     * Inspects one backpack without loading it into gameplay state.
     *
     * <p>This is deliberately read-only. It does not clear the incomplete-load guard, populate the
     * backpack cache, retry a normal load or rewrite any stored item.
     */
    public CompletableFuture<BackpackRecoveryScan> scanBackpackRecoveryAsync(@Nonnull String backpackId) {
        checkDestroy();
        return CompletableFuture.supplyAsync(() -> inspectBackpackRecovery(backpackId).scan(), readExecutor);
    }

    /**
     * Quarantines only the exact unreadable rows authorized by a fresh scan fingerprint.
     *
     * <p>The raw bytes are written to a ZIP before deletion. The existing load hold is retained;
     * only a later complete normal backpack load may release it.
     */
    public CompletableFuture<BackpackRecoveryExecution> quarantineUnreadableBackpackRowsAsync(
            @Nonnull String backpackId, @Nonnull String expectedFingerprint) {
        checkDestroy();
        if (!backpackRecoveryOperations.add(backpackId)) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("A backpack recovery operation is already running for " + backpackId));
        }

        return CompletableFuture.supplyAsync(
                        () -> quarantineUnreadableBackpackRows(backpackId, expectedFingerprint), readExecutor)
                .whenComplete((ignored, failure) -> backpackRecoveryOperations.remove(backpackId));
    }

    BackpackRecoveryScan scanBackpackRecovery(@Nonnull String backpackId) {
        return inspectBackpackRecovery(backpackId).scan();
    }

    BackpackRecoveryExecution quarantineUnreadableBackpackRows(
            @Nonnull String backpackId, @Nonnull String expectedFingerprint) {
        final BackpackRecoveryExecution[] result = new BackpackRecoveryExecution[1];
        boolean acquired = backpackCache.runIfAllUncached(
                Set.of(backpackId), () -> result[0] = quarantineUnreadableBackpackRowsLocked(backpackId, expectedFingerprint));
        if (!acquired) {
            throw new IllegalStateException(
                    "Backpack cache state is busy or live; quarantine could not acquire the exclusive recovery gate.");
        }
        return result[0];
    }

    private BackpackRecoveryExecution quarantineUnreadableBackpackRowsLocked(
            @Nonnull String backpackId, @Nonnull String expectedFingerprint) {
        BackpackRecoveryInspection inspection = inspectBackpackRecovery(backpackId);
        BackpackRecoveryScan scan = inspection.scan();

        if (!scan.loadHeld()) {
            throw new IllegalStateException(
                    "Backpack is not under an incomplete-load hold. Trigger and inspect the real load failure first.");
        }
        if (scan.cached()) {
            throw new IllegalStateException("Backpack is currently cached/live; quarantine refuses to write behind it.");
        }
        if (scan.savePending()) {
            throw new IllegalStateException("Backpack has a pending persistence chain; wait for it before recovery.");
        }
        if (scan.unreadableRows().isEmpty()) {
            throw new IllegalStateException("No unreadable backpack rows are present.");
        }
        if (!scan.fingerprint().equalsIgnoreCase(expectedFingerprint)) {
            throw new IllegalStateException(
                    "Backpack state changed after the scan; run a new scan and use its exact fingerprint.");
        }

        Path archive = writeBackpackRecoveryArchive(inspection);
        UUID owner = UUID.fromString(scan.ownerUuid());
        var completions = new ArrayList<CompletableFuture<Void>>(inspection.unreadableRows().size());
        for (RawBackpackRecoveryRow row : inspection.unreadableRows()) {
            var key = new RecordKey(DataScope.BACKPACK_INVENTORY);
            key.addCondition(FieldKey.BACKPACK_ID, backpackId);
            key.addCondition(FieldKey.INVENTORY_SLOT, Integer.toString(row.slot()));
            completions.add(scheduleDeleteTaskWithCompletion(new UUIDKey(DataScope.NONE, owner), key, false));
        }

        try {
            CompletableFuture.allOf(completions.toArray(CompletableFuture[]::new)).join();
        } catch (RuntimeException failure) {
            throw new IllegalStateException(
                    "Quarantine archive was written, but one or more backpack row deletions failed. Archive: "
                            + archive,
                    failure);
        }

        return new BackpackRecoveryExecution(scan, inspection.unreadableRows().size(), archive.toString());
    }

    private BackpackRecoveryInspection inspectBackpackRecovery(@Nonnull String backpackId) {
        try {
            UUID.fromString(backpackId);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Backpack ID must be a UUID.", invalid);
        }

        var backpackKey = new RecordKey(DataScope.BACKPACK_PROFILE);
        backpackKey.addField(FieldKey.PLAYER_UUID);
        backpackKey.addField(FieldKey.BACKPACK_SIZE);
        backpackKey.addCondition(FieldKey.BACKPACK_ID, backpackId);
        var backpackRows = getData(backpackKey);
        if (backpackRows.isEmpty()) {
            throw new IllegalStateException("No stored backpack profile exists for " + backpackId);
        }
        if (backpackRows.size() != 1) {
            throw new IllegalStateException("Backpack profile identity is not unique; automatic recovery refuses.");
        }

        RecordSet backpack = backpackRows.getFirst();
        String ownerUuid = backpack.getString(FieldKey.PLAYER_UUID);
        int size = backpack.getInt(FieldKey.BACKPACK_SIZE);
        if (ownerUuid == null || size < 1 || size > 54) {
            throw new IllegalStateException("Backpack profile metadata is incomplete or invalid.");
        }
        UUID.fromString(ownerUuid);

        var inventoryKey = new RecordKey(DataScope.BACKPACK_INVENTORY);
        inventoryKey.addField(FieldKey.INVENTORY_SLOT);
        inventoryKey.addField(FieldKey.INVENTORY_ITEM);
        inventoryKey.addCondition(FieldKey.BACKPACK_ID, backpackId);
        List<RecordSet> stored = getData(inventoryKey);

        var stateEntries = new ArrayList<String>(stored.size());
        var unreadable = new ArrayList<RawBackpackRecoveryRow>();
        var seenSlots = new HashSet<Integer>();

        for (RecordSet row : stored) {
            String slotText = row.getString(FieldKey.INVENTORY_SLOT);
            if (slotText == null) {
                throw new IllegalStateException("Backpack contains a row with no slot identity; automatic recovery refuses.");
            }

            final int slot;
            try {
                slot = Integer.parseInt(slotText);
            } catch (NumberFormatException invalid) {
                throw new IllegalStateException(
                        "Backpack contains malformed slot identity '" + bounded(slotText)
                                + "'; automatic quarantine refuses.",
                        invalid);
            }

            Object raw = row.getValue(FieldKey.INVENTORY_ITEM);
            String representation;
            byte[] payload;
            if (raw instanceof byte[] bytes) {
                representation = "binary";
                payload = bytes.clone();
            } else if (raw instanceof String text) {
                representation = "text-utf8";
                payload = text.getBytes(StandardCharsets.UTF_8);
            } else if (raw == null) {
                representation = "null";
                payload = new byte[0];
            } else {
                representation = raw.getClass().getName();
                payload = String.valueOf(raw).getBytes(StandardCharsets.UTF_8);
            }

            String payloadSha256 = sha256Hex(payload);
            stateEntries.add(slot + "\u0000" + representation + "\u0000" + payloadSha256);

            Throwable rowFailure = null;
            try {
                if (slot < 0 || slot >= size || !seenSlots.add(slot)) {
                    throw new IllegalArgumentException("Stored slot is out of range or duplicated");
                }
                if (raw == null
                        || raw instanceof byte[] bytes && bytes.length == 0
                        || raw instanceof String text && text.isBlank()) {
                    continue;
                }
                if (!(raw instanceof byte[]) && !(raw instanceof String)) {
                    throw new IllegalArgumentException("Unsupported stored item representation");
                }
                ItemStack item = row.getItemStack(FieldKey.INVENTORY_ITEM);
                if (item == null || item.isEmpty() || item.getType().isAir() || item.getAmount() <= 0) {
                    throw new IllegalStateException("Non-empty item data did not decode to a usable item");
                }
            } catch (RuntimeException | LinkageError failure) {
                rowFailure = failure;
            }

            if (rowFailure != null) {
                unreadable.add(new RawBackpackRecoveryRow(
                        slot, representation, payload, payloadSha256, summarizeFailure(rowFailure)));
            }
        }

        stateEntries.sort(String::compareTo);
        unreadable.sort(Comparator.comparingInt(RawBackpackRecoveryRow::slot));

        String state = String.join("\n", stateEntries);
        String candidateState = unreadable.stream()
                .map(row -> row.slot() + "\u0000" + row.payloadSha256())
                .collect(Collectors.joining("\n"));
        String fingerprint = sha256Hex((backpackId
                        + "\n"
                        + ownerUuid
                        + "\n"
                        + size
                        + "\n"
                        + stored.size()
                        + "\n"
                        + state
                        + "\n--unreadable--\n"
                        + candidateState)
                .getBytes(StandardCharsets.UTF_8));

        boolean savePending;
        synchronized (backpackSaveChains) {
            savePending = backpackSaveChains.containsKey(backpackId);
        }

        List<BackpackRecoveryCandidate> publicRows = unreadable.stream()
                .map(row -> new BackpackRecoveryCandidate(row.slot(), row.payloadSha256(), row.failure()))
                .toList();
        var scan = new BackpackRecoveryScan(
                backpackId,
                ownerUuid,
                size,
                stored.size(),
                incompleteInventoryLoads.contains(backpackId),
                backpackCache.peek(backpackId) != null,
                savePending,
                publicRows,
                fingerprint);
        return new BackpackRecoveryInspection(scan, unreadable);
    }

    protected Path getBackpackRecoveryDirectory() {
        return Path.of("data-storage", "Slimefun", "recovery", "backpacks");
    }

    private Path writeBackpackRecoveryArchive(@Nonnull BackpackRecoveryInspection inspection) {
        BackpackRecoveryScan scan = inspection.scan();
        Path directory = getBackpackRecoveryDirectory();
        String shortFingerprint = scan.fingerprint().substring(0, 12);
        Path archive = directory.resolve(
                "backpack-" + scan.backpackId() + "-" + System.currentTimeMillis() + "-" + shortFingerprint + ".zip");

        try {
            Files.createDirectories(directory);
            try (var zip = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(archive)))) {
                var manifest = new StringBuilder();
                manifest.append("Slimefun Legacy backpack quarantine\n")
                        .append("created=").append(Instant.now()).append('\n')
                        .append("backpack=").append(scan.backpackId()).append('\n')
                        .append("owner=").append(scan.ownerUuid()).append('\n')
                        .append("size=").append(scan.backpackSize()).append('\n')
                        .append("storedRows=").append(scan.storedRows()).append('\n')
                        .append("fingerprint=").append(scan.fingerprint()).append('\n')
                        .append("quarantinedRows=").append(inspection.unreadableRows().size()).append("\n\n");

                for (RawBackpackRecoveryRow row : inspection.unreadableRows()) {
                    String entryName = "slots/slot-" + row.slot() + ".bin";
                    manifest.append("slot=").append(row.slot())
                            .append(" representation=").append(row.representation())
                            .append(" sha256=").append(row.payloadSha256())
                            .append(" entry=").append(entryName)
                            .append(" failure=").append(row.failure())
                            .append('\n');
                    zip.putNextEntry(new ZipEntry(entryName));
                    zip.write(row.payload());
                    zip.closeEntry();
                }

                zip.putNextEntry(new ZipEntry("manifest.txt"));
                zip.write(manifest.toString().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException failure) {
            try {
                Files.deleteIfExists(archive);
            } catch (IOException ignored) {
                // Preserve the original archive failure.
            }
            throw new IllegalStateException("Could not write backpack quarantine archive; no rows were deleted.", failure);
        }

        return archive;
    }

    private static String summarizeFailure(@Nonnull Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return root.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + bounded(message));
    }

    private static String bounded(@Nonnull String text) {
        String clean = text.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
        return clean.length() <= 180 ? clean : clean.substring(0, 177) + "...";
    }

    private static String sha256Hex(@Nonnull byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    @Nullable public PlayerBackpack getBackpack(String uuid) {
        checkDestroy();
        return backpackCache.getOrLoad(uuid, () -> loadBackpackByUuid(uuid));
    }

    @Nullable private PlayerBackpack loadBackpackByUuid(String uuid) {
        var key = new RecordKey(DataScope.BACKPACK_PROFILE);
        key.addField(FieldKey.BACKPACK_ID);
        key.addField(FieldKey.BACKPACK_SIZE);
        key.addField(FieldKey.BACKPACK_NAME);
        key.addField(FieldKey.BACKPACK_NUMBER);
        key.addField(FieldKey.PLAYER_UUID);
        key.addCondition(FieldKey.BACKPACK_ID, uuid);

        var resultSet = getData(key);
        if (resultSet.isEmpty()) {
            return null;
        }

        var result = resultSet.get(0);
        var idStr = result.getString(FieldKey.BACKPACK_ID);
        var size = result.getInt(FieldKey.BACKPACK_SIZE);
        return new PlayerBackpack(
                Bukkit.getOfflinePlayer(UUID.fromString(result.getString(FieldKey.PLAYER_UUID))),
                UUID.fromString(idStr),
                DataUtils.profileDataDebase64(result.getOrDef(FieldKey.BACKPACK_NAME, "")),
                result.getInt(FieldKey.BACKPACK_NUMBER),
                size,
                getBackpackInv(idStr, size));
    }

    @Nonnull
    private ItemStack[] getBackpackInv(String uuid, int size) {
        var key = new RecordKey(DataScope.BACKPACK_INVENTORY);
        key.addField(FieldKey.INVENTORY_SLOT);
        key.addField(FieldKey.INVENTORY_ITEM);
        key.addCondition(FieldKey.BACKPACK_ID, uuid);

        incompleteInventoryLoads.add(uuid);
        try {
            ItemStack[] inventory = StoredInventoryReader.read(getData(key), size, "backpack " + uuid);
            incompleteInventoryLoads.remove(uuid);
            return inventory;
        } catch (RuntimeException | LinkageError failure) {
            incompleteInventoryLoads.add(uuid);
            throw failure;
        }
    }

    @Nonnull
    private Set<NamespacedKey> getUnlockedResearchKeys(String uuid) {
        var key = new RecordKey(DataScope.PLAYER_RESEARCH);
        key.addField(FieldKey.RESEARCH_ID);
        key.addCondition(FieldKey.PLAYER_UUID, uuid);

        var result = getData(key);
        if (result.isEmpty()) {
            return Collections.emptySet();
        }

        return result.stream()
                .map(record -> NamespacedKey.fromString(record.getString(FieldKey.RESEARCH_ID)))
                .collect(Collectors.toSet());
    }

    @Deprecated
    public void getBackpackAsync(OfflinePlayer owner, int num, IAsyncReadCallback<PlayerBackpack> callback) {
        scheduleReadTask(() -> invokeCallback(callback, getBackpack(owner, num)));
    }

    @Deprecated
    public void getBackpackAsync(String uuid, IAsyncReadCallback<PlayerBackpack> callback) {
        scheduleReadTask(() -> invokeCallback(callback, getBackpack(uuid)));
    }

    @Nonnull
    public Set<PlayerBackpack> getBackpacks(String pUuid) {
        checkDestroy();
        var key = new RecordKey(DataScope.BACKPACK_PROFILE);
        key.addField(FieldKey.BACKPACK_ID);
        key.addCondition(FieldKey.PLAYER_UUID, pUuid);

        var result = getData(key);
        if (result.isEmpty()) {
            return Collections.emptySet();
        }

        var re = new HashSet<PlayerBackpack>();
        result.forEach(bUuid -> re.add(getBackpack(bUuid.getString(FieldKey.BACKPACK_ID))));
        return re;
    }

    public void getBackpacksAsync(String pUuid, IAsyncReadCallback<Set<PlayerBackpack>> callback) {
        scheduleReadTask(() -> {
            var re = getBackpacks(pUuid);
            invokeCallback(callback, re.isEmpty() ? null : re);
        });
    }

    @Nonnull
    public PlayerProfile createProfile(OfflinePlayer p) {
        checkDestroy();
        PlayerProfile re;
        var uid = p.getUniqueId();
        var uuid = uid.toString();
        var cache = profileCache.get(uuid);
        if (cache != null) {
            return cache;
        }

        re = new PlayerProfile(p, 0);
        re = registerProfile(re, uid);
        profileCache.put(uuid, re);

        var key = new RecordKey(DataScope.PLAYER_PROFILE);
        key.addCondition(FieldKey.PLAYER_UUID, uuid);
        var data = getRecordSet(re);
        // UUID-only offline lookups can lack a name for an unregistered machine player.
        // Retain the caller's known name so the NOT NULL parent row is not silently ignored.
        // When no name was supplied, preserve the existing profile-owner lookup behavior.
        var suppliedName = p.getName();
        if (suppliedName != null) {
            data.put(FieldKey.PLAYER_NAME, suppliedName);
        }
        scheduleWriteTask(new UUIDKey(DataScope.NONE, p.getUniqueId()), key, data, true);
        return re;
    }

    private PlayerProfile registerProfile(PlayerProfile profile, UUID uid) {
        AsyncProfileLoadEvent event = new AsyncProfileLoadEvent(profile);
        Bukkit.getPluginManager().callEvent(event);

        Slimefun.getRegistry().getPlayerProfiles().put(uid, event.getProfile());
        return event.getProfile();
    }

    public void setResearch(String uuid, NamespacedKey researchKey, boolean unlocked) {
        var key = new RecordKey(DataScope.PLAYER_RESEARCH);
        key.addCondition(FieldKey.PLAYER_UUID, uuid);
        key.addCondition(FieldKey.RESEARCH_ID, researchKey.toString());
        if (unlocked) {
            var data = new RecordSet();
            data.put(FieldKey.PLAYER_UUID, uuid);
            data.put(FieldKey.RESEARCH_ID, researchKey.toString());
            scheduleWriteTask(new UUIDKey(DataScope.NONE, uuid), key, data, false);
        } else {
            scheduleDeleteTask(new UUIDKey(DataScope.NONE, uuid), key, false);
        }
    }

    @Nonnull
    public PlayerBackpack createBackpack(OfflinePlayer p, String name, int num, int size) {
        var re = new PlayerBackpack(p, UUID.randomUUID(), name, num, size, null);
        var key = new RecordKey(DataScope.BACKPACK_PROFILE);
        key.addCondition(FieldKey.BACKPACK_ID, re.getUniqueId().toString());
        scheduleWriteTask(new UUIDKey(DataScope.NONE, p.getUniqueId()), key, getRecordSet(re), true);
        return re;
    }

    public void saveWaypoints(PlayerProfile profile) {
        scheduleWriteTask(profile::save);
    }

    public void saveBackpackInfo(PlayerBackpack bp) {
        var key = new RecordKey(DataScope.BACKPACK_PROFILE);
        key.addCondition(FieldKey.BACKPACK_ID, bp.getUniqueId().toString());
        key.addField(FieldKey.BACKPACK_SIZE);
        key.addField(FieldKey.BACKPACK_NAME);
        scheduleWriteTask(new UUIDKey(DataScope.NONE, bp.getOwner().getUniqueId()), key, getRecordSet(bp), false);
    }

    public void saveProfileBackpackCount(PlayerProfile profile) {
        var key = new RecordKey(DataScope.PLAYER_PROFILE);
        key.addField(FieldKey.PLAYER_BACKPACK_NUM);
        var uuid = profile.getUUID();
        key.addCondition(FieldKey.PLAYER_UUID, uuid.toString());
        scheduleWriteTask(new UUIDKey(DataScope.NONE, uuid), key, getRecordSet(profile), false);
    }

    @Deprecated(forRemoval = true)
    public void saveBackpackInventory(PlayerBackpack bp, Set<Integer> slotsIgnored) {
        // we decided to compute slots internal, and the argument is ignored to avoid potential data desync
        saveBackpackInventory(bp);
    }

    public void saveBackpackInventory(@Nonnull PlayerBackpack bp) {
        // Preserve the long-standing void API for binary compatibility. New
        // lifecycle code should use saveBackpackInventoryAsync so reservations
        // can remain held until persistence actually finishes.
        saveBackpackInventoryAsync(bp).whenComplete((ignored, failure) -> {
            if (failure != null) {
                Slimefun.logger().log(Level.SEVERE, "An Exception occurred while saving a backpack", failure);
            }
        });
    }

    /**
     * Saves a fully staged backpack state and completes only after the database
     * queues carrying that state have drained successfully.
     *
     * <p>Save attempts for one backing UUID are serialized. This prevents two
     * callers from acknowledging snapshots out of order and makes queue
     * compaction safe: completion is tied to the accepting queue, not to an
     * individual runnable that may be replaced by a newer write for the same key.
     *
     * <p>If a batch fails after some slots were already written, the persisted
     * baseline becomes uncertain. The next save therefore rewrites all 54
     * possible backpack slots before acknowledging a new snapshot.
     *
     * @param bp backpack to persist
     * @return completion for this exact staged backpack state
     */
    public CompletableFuture<Void> saveBackpackInventoryAsync(@Nonnull PlayerBackpack bp) {
        final String backpackId = bp.getUniqueId().toString();
        final ItemStack[] contents;
        final InvSnapshot stagedSnapshot;
        final Map<Integer, BackpackWrite> stagedWrites;

        try {
            requireCompleteInventoryLoad(backpackId);
            synchronized (bp) {
                contents = copyBackpackContents(bp.getInventory().getContents());
                stagedSnapshot = new InvSnapshot(contents);
                stagedWrites = stageBackpackWrites(backpackId, contents);
            }
        } catch (RuntimeException | LinkageError failure) {
            logger.log(Level.WARNING, "Could not stage backpack " + backpackId + " for persistence", failure);
            return CompletableFuture.failedFuture(failure);
        }

        return chainBackpackSave(
                backpackId, () -> persistBackpackStage(bp, backpackId, contents, stagedSnapshot, stagedWrites));
    }

    private void requireCompleteInventoryLoad(String owner) {
        if (incompleteInventoryLoads.contains(owner)) {
            throw StoredInventoryReader.refused("backpack " + owner, "read not completed", null);
        }
    }

    private CompletableFuture<Void> chainBackpackSave(
            @Nonnull String backpackId, @Nonnull Supplier<CompletableFuture<Void>> saveAttempt) {
        synchronized (backpackSaveChains) {
            CompletableFuture<Void> previous = backpackSaveChains.get(backpackId);
            CompletableFuture<Void> start = previous == null
                    ? CompletableFuture.completedFuture(null)
                    : previous.handle((ignored, failure) -> null);
            CompletableFuture<Void> next = start.thenCompose(ignored -> saveAttempt.get());
            backpackSaveChains.put(backpackId, next);
            next.whenComplete((ignored, failure) -> {
                synchronized (backpackSaveChains) {
                    backpackSaveChains.remove(backpackId, next);
                }
            });
            return next;
        }
    }

    private CompletableFuture<Void> persistBackpackStage(
            @Nonnull PlayerBackpack backpack,
            @Nonnull String backpackId,
            @Nonnull ItemStack[] contents,
            @Nonnull InvSnapshot stagedSnapshot,
            @Nonnull Map<Integer, BackpackWrite> stagedWrites) {
        requireCompleteInventoryLoad(backpackId);
        final Set<Integer> changed;

        if (uncertainBackpackBaselines.contains(backpackId)) {
            changed = new HashSet<>(stagedWrites.keySet());
        } else {
            synchronized (backpack) {
                changed = backpack.getSnapshot().getChangedSlots(contents);
            }
        }

        if (changed.isEmpty()) {
            synchronized (backpack) {
                backpack.acknowledgeSnapshot(stagedSnapshot);
            }
            uncertainBackpackBaselines.remove(backpackId);
            return CompletableFuture.completedFuture(null);
        }

        UUIDKey ownerScope = new UUIDKey(DataScope.NONE, backpack.getOwner().getUniqueId());
        var completions = new ArrayList<CompletableFuture<Void>>(changed.size());

        try {
            for (int slot : changed) {
                BackpackWrite write = stagedWrites.get(slot);
                if (write == null) {
                    throw new IllegalStateException("Missing staged backpack write for slot " + slot);
                }

                CompletableFuture<Void> completion = write.data() == null
                        ? scheduleDeleteTaskWithCompletion(ownerScope, write.key(), false)
                        : scheduleWriteTaskWithCompletion(ownerScope, write.key(), write.data(), false);
                completions.add(completion);
            }
        } catch (RuntimeException | LinkageError failure) {
            completions.add(CompletableFuture.failedFuture(failure));
        }

        CompletableFuture<Void> batch = CompletableFuture.allOf(completions.toArray(CompletableFuture[]::new));
        return batch.whenComplete((ignored, failure) -> {
            if (failure == null) {
                synchronized (backpack) {
                    backpack.acknowledgeSnapshot(stagedSnapshot);
                }
                uncertainBackpackBaselines.remove(backpackId);
            } else {
                uncertainBackpackBaselines.add(backpackId);
            }
        });
    }

    private Map<Integer, BackpackWrite> stageBackpackWrites(@Nonnull String backpackId, @Nonnull ItemStack[] contents) {
        Map<Integer, BackpackWrite> staged = new HashMap<>(54);

        // Stage the full legal backpack slot range. Normal saves submit only the
        // changed subset, while a recovery after a partial database failure can
        // safely rewrite/delete every slot, including slots removed by a resize.
        for (int slot = 0; slot < 54; slot++) {
            var key = new RecordKey(DataScope.BACKPACK_INVENTORY);
            key.addCondition(FieldKey.BACKPACK_ID, backpackId);
            key.addCondition(FieldKey.INVENTORY_SLOT, slot + "");
            key.addField(FieldKey.INVENTORY_ITEM);

            ItemStack item = slot < contents.length ? contents[slot] : null;
            if (item == null || item.isEmpty()) {
                staged.put(slot, new BackpackWrite(key, null));
            } else {
                var data = new RecordSet();
                data.put(FieldKey.BACKPACK_ID, backpackId);
                data.put(FieldKey.INVENTORY_SLOT, slot + "");
                // RecordSet serializes the ItemStack immediately on the caller
                // thread, before the database hand-off.
                data.put(FieldKey.INVENTORY_ITEM, item);
                staged.put(slot, new BackpackWrite(key, data));
            }
        }

        return staged;
    }

    private ItemStack[] copyBackpackContents(@Nonnull ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int slot = 0; slot < contents.length; slot++) {
            copy[slot] = contents[slot] == null ? null : contents[slot].clone();
        }
        return copy;
    }

    private record BackpackWrite(
            @Nonnull RecordKey key, @Nullable RecordSet data) {}

    @Deprecated(forRemoval = true)
    public void saveBackpackInventory(PlayerBackpack bp, Integer... slots) {
        // we decided to compute slots internal, and the argument is ignored to avoid potential data desync
        saveBackpackInventory(bp);
    }

    public UUID getPlayerUuid(String pName) {
        checkDestroy();
        var key = new RecordKey(DataScope.PLAYER_PROFILE);
        key.addField(FieldKey.PLAYER_UUID);
        key.addCondition(FieldKey.PLAYER_NAME, pName);

        var result = getData(key);
        if (result.isEmpty()) {
            return null;
        }

        return UUID.fromString(result.get(0).getString(FieldKey.PLAYER_UUID));
    }

    public CompletableFuture<UUID> getPlayerUuidAsync(String pName) {
        checkDestroy();
        return CompletableFuture.supplyAsync(() -> getPlayerUuid(pName), readExecutor);
    }

    public void getPlayerUuidAsync(String pName, IAsyncReadCallback<UUID> callback) {
        scheduleReadTask(() -> invokeCallback(callback, getPlayerUuid(pName)));
    }

    public void updateUsername(String uuid, String newName) {
        var key = new RecordKey(DataScope.PLAYER_PROFILE);
        key.addField(FieldKey.PLAYER_NAME);
        key.addCondition(FieldKey.PLAYER_UUID, uuid);

        var data = new RecordSet();
        data.put(FieldKey.PLAYER_NAME, newName);
        data.put(FieldKey.PLAYER_UUID, uuid);

        scheduleWriteTask(new UUIDKey(DataScope.NONE, uuid), key, data, false);
    }

    private static RecordSet getRecordSet(PlayerBackpack bp) {
        var re = new RecordSet();
        re.put(FieldKey.PLAYER_UUID, bp.getOwner().getUniqueId().toString());
        re.put(FieldKey.BACKPACK_ID, bp.getUniqueId().toString());
        re.put(FieldKey.BACKPACK_NUMBER, bp.getId() + "");
        re.put(FieldKey.BACKPACK_SIZE, bp.getSize() + "");
        re.put(FieldKey.BACKPACK_NAME, DataUtils.profileDataBase64(bp.getName()));
        return re;
    }

    private static RecordSet getRecordSet(PlayerProfile profile) {
        var re = new RecordSet();
        re.put(FieldKey.PLAYER_UUID, profile.getUUID().toString());
        re.put(FieldKey.PLAYER_NAME, profile.getOwner().getName());
        re.put(FieldKey.PLAYER_BACKPACK_NUM, profile.getBackpackCount() + "");
        return re;
    }

    public void invalidateCache(String pUuid) {
        invalidateProfileCache(pUuid);

        var task = new Runnable() {
            @Override
            public void run() {
                if (invalidingBackpackTasks.remove(pUuid) != this) {
                    return;
                }

                backpackCache.invalidate(pUuid);
            }
        };
        invalidingBackpackTasks.put(pUuid, task);
        scheduleWriteTask(task);
    }

    /**
     * Removes a disconnected player's profile immediately but evicts their
     * cached backpacks only after every currently registered backpack persistence
     * chain has completed successfully.
     *
     * <p>If a save failed or left the persisted baseline uncertain, the canonical
     * in-memory backpack stays cached for the rest of the server uptime. This
     * prevents another physical copy from reloading partially persisted storage.
     */
    public void invalidateCacheAfterBackpackPersistence(@Nonnull String pUuid) {
        invalidateProfileCache(pUuid);

        Set<String> backpackIds = backpackCache.getOwnerBackpackIds(pUuid);
        if (backpackIds.isEmpty()) {
            return;
        }

        var pending = new ArrayList<CompletableFuture<Void>>();
        synchronized (backpackSaveChains) {
            for (String backpackId : backpackIds) {
                CompletableFuture<Void> future = backpackSaveChains.get(backpackId);
                if (future != null) {
                    pending.add(future);
                }
            }
        }

        if (hasUncertainBackpackBaseline(backpackIds)) {
            logger.log(
                    Level.WARNING,
                    "Keeping {0} backpack cache entry/entries for disconnected owner {1} because persistence is uncertain.",
                    new Object[] {backpackIds.size(), pUuid});
            return;
        }

        if (pending.isEmpty()) {
            backpackCache.invalidateAfterPersistence(pUuid);
            return;
        }

        CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).whenComplete((ignored, failure) -> {
            if (failure != null || hasUncertainBackpackBaseline(backpackIds)) {
                logger.log(
                        Level.WARNING,
                        "Keeping backpack cache for disconnected owner " + pUuid
                                + " because a persistence barrier failed.",
                        failure);
                return;
            }

            backpackCache.invalidateAfterPersistence(pUuid);
        });
    }

    public int getPendingBackpackSaveChainCount() {
        synchronized (backpackSaveChains) {
            return backpackSaveChains.size();
        }
    }

    public int getUncertainBackpackBaselineCount() {
        return uncertainBackpackBaselines.size();
    }

    private boolean hasUncertainBackpackBaseline(@Nonnull Set<String> backpackIds) {
        for (String backpackId : backpackIds) {
            if (uncertainBackpackBaselines.contains(backpackId)) {
                return true;
            }
        }
        return false;
    }

    private void invalidateProfileCache(@Nonnull String pUuid) {
        var removed = profileCache.remove(pUuid);
        if (removed != null) {
            removed.markForDeletion();
        }
    }

    @Override
    public void shutdown() {
        awaitBackpackSaveChains();
        super.shutdown();
        backpackCache.clean();
        profileCache.clear();
    }

    private void awaitBackpackSaveChains() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);

        while (System.nanoTime() < deadline) {
            CompletableFuture<?>[] snapshot;
            synchronized (backpackSaveChains) {
                if (backpackSaveChains.isEmpty()) {
                    return;
                }

                snapshot = backpackSaveChains.values().stream()
                        .map(future -> future.handle((ignored, failure) -> null))
                        .toArray(CompletableFuture<?>[]::new);
            }

            try {
                long remainingNanos = deadline - System.nanoTime();
                if (remainingNanos <= 0) {
                    break;
                }
                CompletableFuture.allOf(snapshot).get(remainingNanos, TimeUnit.NANOSECONDS);
            } catch (Exception failure) {
                logger.log(Level.WARNING, "Interrupted while waiting for backpack persistence chains", failure);
                if (failure instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                break;
            }
        }

        if (!backpackSaveChains.isEmpty()) {
            logger.log(
                    Level.SEVERE,
                    "Timed out with {0} acknowledgement-aware backpack save chain(s) still pending.",
                    backpackSaveChains.size());
        }
    }
    public boolean runWhileMaintenanceBackpackOwned(@Nonnull PlayerBackpack backpack, @Nonnull Runnable action) {
        return this.backpackCache.runWhileMaintenanceOwned(backpack, action);
    }

}
