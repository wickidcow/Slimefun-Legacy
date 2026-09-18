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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
        if (currentPlayerName != null && !currentPlayerName.equals(result.get(0).get(FieldKey.PLAYER_NAME))) {
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
            var size = Integer.parseInt(bResult.get(0).get(FieldKey.BACKPACK_SIZE));
            var idStr = result.get(FieldKey.BACKPACK_ID);

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
                            .map(record -> record.get(FieldKey.BACKPACK_ID))
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
        var idStr = result.get(FieldKey.BACKPACK_ID);
        var size = result.getInt(FieldKey.BACKPACK_SIZE);
        return new PlayerBackpack(
                Bukkit.getOfflinePlayer(UUID.fromString(result.get(FieldKey.PLAYER_UUID))),
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

        var invResult = getData(key);
        var re = new ItemStack[size];
        for (RecordSet each : invResult) {
            var slot = each.getInt(FieldKey.INVENTORY_SLOT);
            if (slot < 0 || slot >= re.length) {
                logger.log(
                        Level.WARNING,
                        "Ignoring out-of-range stored backpack slot [{0}:{1}] for inventory size {2}",
                        new Object[] {uuid, slot, size});
                continue;
            }

            try {
                re[slot] = each.getItemStack(FieldKey.INVENTORY_ITEM);
            } catch (Exception e) {
                re[slot] = null;
                logger.log(
                        Level.SEVERE,
                        "Could not deserialize a player backpack item; replaced it with air [" + uuid + ":" + slot
                                + "]",
                        e);
            }
        }

        return re;
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
                .map(record -> NamespacedKey.fromString(record.get(FieldKey.RESEARCH_ID)))
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
        result.forEach(bUuid -> re.add(getBackpack(bUuid.get(FieldKey.BACKPACK_ID))));
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
        scheduleWriteTask(new UUIDKey(DataScope.NONE, p.getUniqueId()), key, getRecordSet(re), true);
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
            synchronized (bp) {
                contents = copyBackpackContents(bp.getInventory().getContents());
                stagedSnapshot = new InvSnapshot(contents);
                stagedWrites = stageBackpackWrites(backpackId, contents);
            }
        } catch (RuntimeException | LinkageError failure) {
            Slimefun.logger()
                    .log(Level.WARNING, "Could not stage backpack " + backpackId + " for persistence", failure);
            return CompletableFuture.failedFuture(failure);
        }

        return chainBackpackSave(
                backpackId, () -> persistBackpackStage(bp, backpackId, contents, stagedSnapshot, stagedWrites));
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

        CompletableFuture<Void> batch =
                CompletableFuture.allOf(completions.toArray(CompletableFuture[]::new));
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

    private Map<Integer, BackpackWrite> stageBackpackWrites(
            @Nonnull String backpackId, @Nonnull ItemStack[] contents) {
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
            if (item == null) {
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

    private record BackpackWrite(@Nonnull RecordKey key, @Nullable RecordSet data) {}

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

        return UUID.fromString(result.get(0).get(FieldKey.PLAYER_UUID));
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

        CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new))
                .whenComplete((ignored, failure) -> {
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
}
