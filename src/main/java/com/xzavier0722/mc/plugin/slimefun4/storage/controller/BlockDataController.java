package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import city.norain.slimefun4.api.menu.UniversalMenu;
import city.norain.slimefun4.api.menu.UniversalMenuPreset;
import city.norain.slimefun4.utils.InventoryUtil;
import city.norain.slimefun4.utils.StringUtil;
import com.xzavier0722.mc.plugin.slimefun4.storage.adapter.IDataSourceAdapter;
import com.xzavier0722.mc.plugin.slimefun4.storage.callback.IAsyncReadCallback;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.BlockStorageMigration;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataType;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.FieldKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordSet;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.ScopeKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.attributes.UniversalBlock;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.attributes.UniversalDataTrait;
import com.xzavier0722.mc.plugin.slimefun4.storage.event.SlimefunChunkDataLoadEvent;
import com.xzavier0722.mc.plugin.slimefun4.storage.task.DelayedSavingLooperTask;
import com.xzavier0722.mc.plugin.slimefun4.storage.task.DelayedTask;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.DataUtils;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.InvSnapshot;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.InvStorageUtils;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.LocationUtils;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.services.scheduling.TaskHandle;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.inventory.DirtyChestMenu;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * 方块数据控制器
 * <p>
 * 用于管理区块中的 Slimefun 方块数据
 * <p>
 * {@link SlimefunBlockData}
 * {@link SlimefunUniversalData}
 *
 * @author Xzavier0722
 * @author NoRainCity
 */
public class BlockDataController extends ADataController {
    /**
     * 延迟写数据任务队列
     */
    private final Map<LinkedKey, DelayedTask> delayedWriteTasks;
    /**
     * 区块数据缓存
     */
    private final Map<String, SlimefunChunkData> loadedChunk;
    /**
     * 通用数据缓存
     */
    private final Map<UUID, SlimefunUniversalData> loadedUniversalData;
    /**
     * 方块物品栏快照
     */
    private final Map<String, InvSnapshot> invSnapshots;
    /** Serializes acknowledgement-aware inventory save batches per block/universal inventory. */
    private final Map<String, CompletableFuture<Void>> inventorySaveChains;
    /** Marks persisted inventory baselines that may be partially applied after a failed batch. */
    private final Set<String> uncertainInventoryBaselines;
    /** Retained until a complete retry succeeds; never cleared by an attempted save. */
    private final Set<String> incompleteInventoryLoads = ConcurrentHashMap.newKeySet();
    /** Maps known universal positions while their UUID's inventory is incomplete; never loads chunks. */
    private final InventoryRecoveryLocations inventoryRecoveryLocations = new InventoryRecoveryLocations();
    /** Retains a destination UUID when commit acknowledgement or activation needs a retry. */
    private final Map<String, PendingUniversalMigration> pendingUniversalMigrations = new ConcurrentHashMap<>();
    /**
     * 全局控制器加载数据锁
     *
     * {@link ScopedLock}
     */
    private final ScopedLock lock;
    /**
     * 延时加载模式标志
     */
    private boolean enableDelayedSaving = false;

    private int delayedSecond = 0;
    private TaskHandle looperTask;
    /**
     * 区块数据加载模式
     * {@link ChunkDataLoadMode}
     */
    private ChunkDataLoadMode chunkDataLoadMode;

    /**
     * 初始化加载中标志
     */
    BlockDataController() {
        super(DataType.BLOCK_STORAGE);
        delayedWriteTasks = new ConcurrentHashMap<>();
        loadedChunk = new ConcurrentHashMap<>();
        loadedUniversalData = new ConcurrentHashMap<>();
        invSnapshots = new ConcurrentHashMap<>();
        inventorySaveChains = new ConcurrentHashMap<>();
        uncertainInventoryBaselines = ConcurrentHashMap.newKeySet();
        lock = new ScopedLock();
    }

    /**
     * 初始化数据控制器
     *
     * @param dataAdapter    使用的 {@link IDataSourceAdapter}
     * @param maxReadThread  最大数据库读线程数
     * @param maxWriteThread 最大数据库写线程数
     */
    @Override
    public void init(IDataSourceAdapter<?> dataAdapter, int maxReadThread, int maxWriteThread) {
        super.init(dataAdapter, maxReadThread, maxWriteThread);
        this.chunkDataLoadMode = Slimefun.getDatabaseManager().getChunkDataLoadMode();
        initLoadData();
    }

    /**
     * 初始化加载数据
     */
    private void initLoadData() {
        switch (chunkDataLoadMode) {
            case LOAD_WITH_CHUNK -> loadLoadedChunks();
            case LOAD_ON_STARTUP -> loadLoadedWorlds();
        }

        Slimefun.getSchedulerService().runLater(this::loadUniversalRecord, 1L);
    }

    /**
     * 加载所有服务器已加载的世界中的数据
     */
    private void loadLoadedWorlds() {
        Slimefun.getSchedulerService()
                .runLater(
                        () -> {
                            for (var world : Bukkit.getWorlds()) {
                                loadWorld(world);
                            }
                        },
                        1L);
    }

    /**
     * 加载所有服务器已加载的世界区块中的数据
     */
    private void loadLoadedChunks() {
        Slimefun.getSchedulerService()
                .runLater(
                        () -> {
                            for (var world : Bukkit.getWorlds()) {
                                if (Slimefun.getSchedulerService().isFolia()) {
                                    scheduleExistingLoadedChunks(world);
                                } else {
                                    for (var chunk : world.getLoadedChunks()) {
                                        loadChunk(chunk, false, true);
                                    }
                                }
                            }
                        },
                        1L);
    }

    private void scheduleExistingLoadedChunks(World world) {
        var chunkKeys = new HashSet<String>();
        var key = new RecordKey(DataScope.CHUNK_DATA);
        key.addField(FieldKey.CHUNK);
        key.addCondition(FieldKey.CHUNK, world.getName() + ";%");
        getData(key, true).forEach(data -> chunkKeys.add(data.getString(FieldKey.CHUNK)));

        key = new RecordKey(DataScope.BLOCK_RECORD);
        key.addField(FieldKey.CHUNK);
        key.addCondition(FieldKey.CHUNK, world.getName() + ";%");
        getData(key, true).forEach(data -> chunkKeys.add(data.getString(FieldKey.CHUNK)));

        chunkKeys.forEach(cKey -> scheduleExistingLoadedChunk(world, cKey));
    }

    private void scheduleExistingLoadedChunk(World world, String cKey) {
        try {
            var coordinates = cKey.split(";")[1].split(":");
            int chunkX = Integer.parseInt(coordinates[0]);
            int chunkZ = Integer.parseInt(coordinates[1]);
            var anchor = new Location(world, chunkX << 4, 0, chunkZ << 4);
            var task = Slimefun.getSchedulerService().runAt(anchor, () -> {
                if (world.isChunkLoaded(chunkX, chunkZ)) {
                    loadChunk(world.getChunkAt(chunkX, chunkZ, false), false, true);
                }
            });
            if (task.isCancelled()) {
                logger.log(
                        Level.WARNING,
                        "Skipped loaded-chunk Slimefun data bootstrap because scheduling was rejected: {0}",
                        cKey);
            }
        } catch (RuntimeException failure) {
            logger.log(
                    Level.WARNING,
                    "Unable to resolve loaded Slimefun chunk key during Folia startup: " + cKey,
                    failure);
        }
    }

    /**
     * 初始化延时加载任务
     *
     * @param p               插件实例
     * @param delayedSecond   首次执行延时
     * @param forceSavePeriod 强制保存周期
     */
    public void initDelayedSaving(Plugin p, int delayedSecond, int forceSavePeriod) {
        checkDestroy();
        if (delayedSecond < 1 || forceSavePeriod < 1) {
            throw new IllegalArgumentException("save period second must be greater than 0!");
        }
        enableDelayedSaving = true;
        this.delayedSecond = delayedSecond;
        looperTask = Slimefun.getSchedulerService()
                .runAsyncAtFixedRate(createDelayedSavingLooper(forceSavePeriod), 20L, 20L);
    }

    /**
     * Observes known read holds and pending migrations without loading worlds/items or changing
     * guards. The bounded sample is weakly consistent with concurrent loading, not a safety barrier.
     */
    public InventoryRecoverySnapshot getInventoryRecoverySnapshot() {
        var snapshot = new InventoryRecoverySnapshot.Collector();
        incompleteInventoryLoads.forEach(owner -> snapshot.add(
                owner.indexOf(';') >= 0
                        ? InventoryRecoverySnapshot.Kind.BLOCK_LOAD
                        : InventoryRecoverySnapshot.Kind.UNIVERSAL_LOAD,
                owner,
                null));
        pendingUniversalMigrations.forEach((owner, pending) -> snapshot.add(
                InventoryRecoverySnapshot.Kind.UNIVERSAL_MIGRATION,
                owner,
                pending.plan.destination().toString()));
        return snapshot.build();
    }

    public boolean isDelayedSavingEnabled() {
        return enableDelayedSaving;
    }

    /** Creates the same looper used by delayed saving, with replacement-safe queue completion. */
    DelayedSavingLooperTask createDelayedSavingLooper(int forceSavePeriod) {
        return DelayedSavingLooperTask.withTaskCompletion(
                forceSavePeriod,
                () -> new HashMap<>(delayedWriteTasks),
                (key, completedTask) -> delayedWriteTasks.remove(key, completedTask));
    }

    /**
     * Returns how many delayed-saving mutations are currently waiting to enter the normal write queue.
     *
     * <p>This is a read-only point-in-time diagnostic value. It is intended for storage health checks and must not be
     * treated as a transaction barrier.
     *
     * @return the number of currently deferred write tasks
     */
    public int getPendingDelayedWriteTaskCount() {
        return delayedWriteTasks.size();
    }

    public void setDelayedSavingEnable(boolean isEnable) {
        enableDelayedSaving = isEnable;
    }

    /**
     * 在指定位置创建一个新的 Slimefun 方块数据
     *
     * @param l    Slimefun 方块位置 {@link Location}
     * @param sfId Slimefun 物品 ID {@link SlimefunItem#getId()}
     * @return 方块数据, {@link SlimefunBlockData}
     */
    @Nonnull
    public SlimefunBlockData createBlock(Location l, String sfId) {
        checkDestroy();
        requireMutableBlockLocation(l);
        var sfItem = SlimefunItem.getById(sfId);

        if (sfItem instanceof UniversalBlock) {
            throw new IllegalArgumentException("Cannot create normal block data on UniversalBlock!");
        }

        var re = getChunkDataCache(l.getChunk(), true).createBlockData(l, sfId);
        if (Slimefun.getRegistry().getTickerBlocks().contains(sfId)) {
            Slimefun.getTickerTask().enableTicker(l);
        }
        return re;
    }

    /**
     * 创建一个新的 Slimefun 通用数据
     * <br/>
     * 提供一个可供读写的 KV 存储 Map
     *
     * @param sfId Slimefun 物品 ID {@link SlimefunItem#getId()}
     * @return 通用数据, {@link SlimefunUniversalData}
     */
    @Nonnull
    public SlimefunUniversalData createUniversalData(String sfId) {
        return createUniversalData(UUID.randomUUID(), sfId);
    }

    /**
     * 创建一个新的 Slimefun 通用数据
     * 提供一个可供读写的 KV 存储 Map
     *
     * @param uuid 通用数据的识别 UUID
     * @param sfId Slimefun 物品 ID {@link SlimefunItem#getId()}
     * @return 通用数据, {@link SlimefunUniversalData}
     */
    @Nonnull
    public SlimefunUniversalData createUniversalData(UUID uuid, String sfId) {
        checkDestroy();
        requireCompleteInventoryLoad(uuid.toString());

        if (getUniversalDataFromCache(uuid) != null || getUniversalData(uuid) != null) {
            throw new IllegalArgumentException("A universal data with this UUID already exists: " + uuid);
        }

        var uniData = new SlimefunUniversalData(uuid, sfId);

        uniData.setIsDataLoaded(true);

        loadedUniversalData.put(uuid, uniData);

        Slimefun.getDatabaseManager().getBlockDataController().saveUniversalData(uniData);

        return uniData;
    }

    /**
     * 在指定位置创建一个新的 Slimefun 通用方块数据
     *
     * @param l    Slimefun 方块位置 {@link Location}
     * @param sfId Slimefun 物品 ID {@link SlimefunItem#getId()}
     * @return 通用方块数据, {@link SlimefunUniversalBlockData}
     */
    @Nonnull
    @ParametersAreNonnullByDefault
    public SlimefunUniversalBlockData createUniversalBlock(Location l, String sfId) {
        checkDestroy();
        requireMutableBlockLocation(l);
        return createUniversalBlockAfterPreflight(l, sfId);
    }

    // Private to the controller: the normal API checks recovery state first. The migration
    // caller has separately decoded its source and must keep that source's read guard active.
    private SlimefunUniversalBlockData createUniversalBlockAfterPreflight(Location l, String sfId) {
        var uuid = UUID.randomUUID();
        var uniData = new SlimefunUniversalBlockData(uuid, sfId, l);

        uniData.setIsDataLoaded(true);

        uniData.initTraits();

        loadedUniversalData.put(uuid, uniData);

        var preset = UniversalMenuPreset.getPreset(sfId);
        if (preset != null) {
            uniData.setMenu(new UniversalMenu(preset, uuid, l));
        }

        if (Slimefun.getRegistry().getTickerBlocks().contains(sfId)) {
            Slimefun.getTickerTask().enableTicker(l, uuid);
        }

        Slimefun.getDatabaseManager().getBlockDataController().saveUniversalData(uniData);

        if (Slimefun.getBlockDataService().isTileEntity(l.getBlock().getType())) {
            Slimefun.getBlockDataService().updateUniversalDataUUID(l.getBlock(), uniData.getKey());
        }

        uniData.initLastPresent();

        return uniData;
    }

    void saveNewBlock(Location l, String sfId) {
        requireMutableBlockLocation(l);
        var lKey = LocationUtils.getLocKey(l);

        var key = new RecordKey(DataScope.BLOCK_RECORD);
        // key.addCondition(FieldKey.LOCATION, lKey);

        var data = new RecordSet();
        data.put(FieldKey.LOCATION, lKey);
        data.put(FieldKey.CHUNK, LocationUtils.getChunkKey(l.getChunk()));
        data.put(FieldKey.SLIMEFUN_ID, sfId);

        var scopeKey = new LocationKey(DataScope.NONE, l);
        removeDelayedDataUpdates(scopeKey); // Shouldn't have.. But for safe..
        scheduleWriteTask(scopeKey, key, data, true);
    }

    /**
     * 立即计划保存一个通用数据
     *
     * @param universalData 欲写入数据库保存的通用数据
     */
    void saveUniversalData(SlimefunUniversalData universalData) {
        requireCompleteInventoryLoad(universalData.getKey());
        var uuid = universalData.getKey();
        var sfId = universalData.getSfId();
        var traitsStr = StringUtil.getTraitsStr(universalData.getTraits());

        var key = new RecordKey(DataScope.UNIVERSAL_RECORD);

        var data = new RecordSet();
        data.put(FieldKey.UNIVERSAL_UUID, uuid);
        data.put(FieldKey.SLIMEFUN_ID, sfId);
        data.put(FieldKey.UNIVERSAL_TRAITS, traitsStr);

        var scopeKey = new UUIDKey(DataScope.NONE, uuid);
        removeDelayedDataUpdates(scopeKey); // Shouldn't have.. But for safe..
        scheduleWriteTask(scopeKey, key, data, true);
    }

    /**
     * 移除指定位置上的 Slimefun 数据
     *
     * @param l 位置 {@link Location}
     */
    public void removeBlock(Location l) {
        checkDestroy();
        requireMutableBlockLocation(l);

        var removed = getChunkDataCache(l.getChunk(), true).removeBlockData(l);

        if (removed == null) {
            removeUniversalBlockData(l);

            return;
        }
        // fix issue # 992 # 1099
        invSnapshots.remove(removed.getKey());

        if (!removed.isDataLoaded()) {
            return;
        }

        if (Slimefun.getRegistry().getTickerBlocks().contains(removed.getSfId())) {
            Slimefun.getTickerTask().disableTicker(l);
        }

        var menu = removed.getBlockMenu();
        if (menu != null) {
            menu.lock();
        }
    }

    /**
     * 移除指定位置上的 Slimefun 方块数据
     *
     * @param l 位置 {@link Location}
     */
    public void removeBlockData(Location l) {
        checkDestroy();
        requireMutableBlockLocation(l);

        var removed = getChunkDataCache(l.getChunk(), true).removeBlockData(l);
        finishBlockDataRemoval(l, removed);
    }

    private void finishBlockDataRemoval(Location l, @Nullable SlimefunBlockData removed) {
        if (removed == null || !removed.isDataLoaded()) {
            return;
        }

        var menu = removed.getBlockMenu();
        if (menu != null) {
            InventoryUtil.closeInventory(menu.toInventory());
        }

        if (Slimefun.getRegistry().getTickerBlocks().contains(removed.getSfId())) {
            Slimefun.getTickerTask().disableTicker(l);
        }
    }

    /**
     * 移除指定位置对应的可能存在的 Slimefun 通用方块数据
     *
     * @param l {@link Location} 位置
     */
    public void removeUniversalBlockData(Location l) {
        checkDestroy();
        requireMutableBlockLocation(l);

        var toRemove = getUniversalBlockDataFromCache(l);

        if (toRemove.isEmpty()) {
            return;
        }

        removeUniversalBlockData(toRemove.get().getUUID());
    }

    /**
     * 移除指定 UUID 对应的 Slimefun 通用方块数据
     *
     * @param uuid 通用方块数据识别符
     */
    public void removeUniversalBlockData(UUID uuid) {
        checkDestroy();
        requireCompleteInventoryLoad(uuid.toString());

        var toRemove = loadedUniversalData.get(uuid);

        if (toRemove == null) {
            return;
        }

        if (!toRemove.isDataLoaded()) {
            return;
        }

        toRemove.setPendingRemove(true);

        if (toRemove instanceof SlimefunUniversalBlockData ubd) {
            ubd.setPendingRemove(true);

            var menu = ubd.getMenu();
            if (menu != null) {
                menu.lock();
            }

            removeUniversalBlockDirectly(uuid);

            var lastPresent = ubd.getLastPresent();
            if (lastPresent != null && Slimefun.getRegistry().getTickerBlocks().contains(toRemove.getSfId())) {
                Slimefun.getTickerTask().disableTicker(lastPresent.toLocation());
            }
        }

        loadedUniversalData.remove(uuid);
    }

    void removeBlockDirectly(Location l) {
        checkDestroy();
        requireMutableBlockLocation(l);
        deleteBlockRecordAfterPreflight(l);
    }

    private void deleteBlockRecordAfterPreflight(Location l) {
        var scopeKey = new LocationKey(DataScope.NONE, l);
        removeDelayedDataUpdates(scopeKey);

        var key = new RecordKey(DataScope.BLOCK_RECORD);
        key.addCondition(FieldKey.LOCATION, LocationUtils.getLocKey(l));
        scheduleDeleteTask(scopeKey, key, true);
    }

    void removeUniversalBlockDirectly(UUID uuid) {
        checkDestroy();
        requireCompleteInventoryLoad(uuid.toString());
        var scopeKey = new UUIDKey(DataScope.NONE, uuid);
        removeDelayedDataUpdates(scopeKey);

        var key = new RecordKey(DataScope.UNIVERSAL_RECORD);
        key.addCondition(FieldKey.UNIVERSAL_UUID, uuid.toString());
        scheduleDeleteTask(scopeKey, key, true);
    }

    SlimefunBlockData removeChunkBlockData(SlimefunChunkData chunk, Location location) {
        checkDestroy();
        requireMutableBlockLocation(location);
        return removeChunkBlockDataAfterPreflight(chunk, location);
    }

    private SlimefunBlockData removeChunkBlockDataAfterPreflight(SlimefunChunkData chunk, Location location) {
        var removed = chunk.removeBlockDataCacheInternal(LocationUtils.getLocKey(location));
        if (removed == null && chunk.isDataLoaded()) {
            return null;
        }
        // Preserve the existing cache tombstone and record-cascade behavior. Only a
        // private, fully preflighted migration may reach this with its source guard set.
        deleteBlockRecordAfterPreflight(location);
        return removed;
    }

    /**
     * Get slimefun block data at specific location
     *
     * @param l slimefun block location {@link Location}
     * @return {@link SlimefunBlockData}
     */
    @Nullable @ParametersAreNonnullByDefault
    public SlimefunBlockData getBlockData(Location l) {
        checkDestroy();
        if (chunkDataLoadMode.readCacheOnly()) {
            return getBlockDataFromCache(l);
        }
        var chunkData = getChunkDataCache(l, false);
        // fix issue #935
        if (chunkData != null) {
            var lKey = LocationUtils.getLocKey(l);
            var re = chunkData.getBlockCacheInternal(lKey);
            if (re != null || chunkData.hasBlockCache(lKey) || chunkData.isDataLoaded()) {
                return re;
            }
        }

        return loadBlockData(l);
    }

    private SlimefunBlockData loadBlockData(Location l) {
        var lKey = LocationUtils.getLocKey(l);
        var key = new RecordKey(DataScope.BLOCK_RECORD);
        key.addCondition(FieldKey.LOCATION, lKey);
        key.addField(FieldKey.SLIMEFUN_ID);

        var result = getData(key);
        var re =
                result.isEmpty() ? null : new SlimefunBlockData(l, result.get(0).getString(FieldKey.SLIMEFUN_ID));
        if (re != null) {
            // fix issue #935
            SlimefunChunkData chunkData = getChunkDataCache(l, true);
            chunkData.addBlockCacheInternal(re, false);
            re = chunkData.getBlockCacheInternal(lKey);
        }
        return re;
    }

    public CompletableFuture<SlimefunBlockData> getBlockDataAsync(Location l) {
        checkDestroy();
        if (chunkDataLoadMode.readCacheOnly()) {
            return CompletableFuture.completedFuture(getBlockDataFromCache(l));
        }

        var chunkData = getChunkDataCache(l, false);
        // fix issue #935
        if (chunkData != null) {
            var lKey = LocationUtils.getLocKey(l);
            var re = chunkData.getBlockCacheInternal(lKey);
            if (re != null || chunkData.hasBlockCache(lKey) || chunkData.isDataLoaded()) {
                return CompletableFuture.completedFuture(re);
            }
        }
        return CompletableFuture.supplyAsync(() -> loadBlockData(l), this.readExecutor);
    }

    /**
     * Get slimefun block data at specific location asynchronous
     *
     * @param l        slimefun block location {@link Location}
     * @param callback operation when block data fetched {@link IAsyncReadCallback}
     */
    public void getBlockDataAsync(Location l, IAsyncReadCallback<SlimefunBlockData> callback) {
        scheduleReadTask(() -> invokeCallback(callback, getBlockData(l)));
    }

    /**
     * Get slimefun block data at specific location from cache
     *
     * @param l slimefun block location {@link Location}
     * @return {@link SlimefunBlockData}
     */
    public SlimefunBlockData getBlockDataFromCache(Location l) {
        return getBlockDataFromCache(LocationUtils.getChunkKey(l), LocationUtils.getLocKey(l));
    }

    /**
     * 从数据库中获取 {@link SlimefunUniversalData}
     */
    @Nullable public SlimefunUniversalData getUniversalData(@Nonnull UUID uuid) {
        checkDestroy();

        var key = new RecordKey(DataScope.UNIVERSAL_RECORD);
        key.addCondition(FieldKey.UNIVERSAL_UUID, uuid.toString());
        key.addField(FieldKey.SLIMEFUN_ID);
        key.addField(FieldKey.UNIVERSAL_TRAITS);

        var result = getData(key);

        if (result.isEmpty()) {
            return null;
        }

        var traits = StringUtil.getTraitsFromStr(result.get(0).getString(FieldKey.UNIVERSAL_TRAITS));

        if (traits.contains(UniversalDataTrait.BLOCK)) {
            var ubd = new SlimefunUniversalBlockData(uuid, result.get(0).getString(FieldKey.SLIMEFUN_ID));
            traits.forEach(ubd::addTrait);
            return ubd;
        } else {
            return new SlimefunUniversalData(uuid, result.get(0).getString(FieldKey.SLIMEFUN_ID), traits);
        }
    }

    /**
     * Get slimefun universal data
     *
     * @param uuid universal data uuid {@link UUID}
     */
    @Nullable public SlimefunUniversalBlockData getUniversalBlockData(@Nonnull UUID uuid) {
        SlimefunUniversalData universalData = getUniversalData(uuid);

        if (universalData instanceof SlimefunUniversalBlockData ubd) {
            return ubd;
        } else {
            return null;
        }
    }

    /**
     * Get slimefun universal data asynchronous
     *
     * @param uuid     universal data uuid {@link UUID}
     * @param callback operation when block data fetched {@link IAsyncReadCallback}
     */
    public void getUniversalBlockData(@Nonnull UUID uuid, IAsyncReadCallback<SlimefunUniversalBlockData> callback) {
        scheduleReadTask(() -> invokeCallback(callback, getUniversalBlockData(uuid)));
    }

    /**
     * 从缓存中获取 {@link SlimefunUniversalData}
     *
     * @param uuid 通用数据 UUID
     * @return {@link SlimefunUniversalData}
     */
    @Nullable public SlimefunUniversalData getUniversalDataFromCache(@Nonnull UUID uuid) {
        checkDestroy();

        return loadedUniversalData.get(uuid);
    }

    /**
     * Get slimefun universal data from cache
     *
     * @param uuid universal data uuid {@link UUID}
     */
    @Nullable public SlimefunUniversalBlockData getUniversalBlockDataFromCache(@Nonnull UUID uuid) {
        var cache = getUniversalDataFromCache(uuid);

        if (cache instanceof SlimefunUniversalBlockData ubd) {
            return ubd;
        } else {
            return null;
        }
    }

    /**
     * Get slimefun universal data from cache by location
     *
     * @param l Slimefun block location {@link Location}
     */
    public Optional<SlimefunUniversalBlockData> getUniversalBlockDataFromCache(@Nonnull Location l) {
        checkDestroy();

        for (SlimefunUniversalData uniData : loadedUniversalData.values()) {
            if (uniData instanceof SlimefunUniversalBlockData ubd) {
                if (!ubd.isDataLoaded() || ubd.getLastPresent() == null) {
                    continue;
                }

                if (l.equals(ubd.getLastPresent().toLocation())) {
                    return Optional.of(ubd);
                }
            }
        }

        return Optional.empty();
    }

    /**
     * @deprecated use {@link #move(SlimefunBlockData, Location)} instead
     */
    @Deprecated(forRemoval = true)
    public void setBlockDataLocation(SlimefunBlockData blockData, Location target) {
        move(blockData, target);
    }

    /**
     * Move block data to specific location
     * <p>
     * Similar to original BlockStorage#move.
     *
     * @param data   the block data {@link SlimefunBlockData} need to move
     * @param target move target {@link Location}
     */
    public void move(ASlimefunDataContainer data, Location target) {
        if (data instanceof SlimefunBlockData blockData) {
            move(blockData, target);
        } else if (data instanceof SlimefunUniversalBlockData universalBlockData) {
            move(universalBlockData, target);
        }
    }

    private void move(SlimefunBlockData blockData, Location target) {
        checkDestroy();
        requireMutableBlockLocation(blockData.getLocation());
        requireMutableBlockLocation(target);
        if (LocationUtils.isSameLoc(blockData.getLocation(), target)) {
            return;
        }

        var hasTicker = false;

        if (blockData.isDataLoaded() && Slimefun.getRegistry().getTickerBlocks().contains(blockData.getSfId())) {
            Slimefun.getTickerTask().disableTicker(blockData.getLocation());
            hasTicker = true;
        }

        BlockMenu menu = null;

        if (blockData.isDataLoaded() && blockData.getBlockMenu() != null) {
            menu = blockData.getBlockMenu();
            menu.lock();
        }

        try {
            var chunk = blockData.getLocation().getChunk();
            var chunkData = getChunkDataCache(chunk, false);
            if (chunkData != null) {
                chunkData.removeBlockDataCacheInternal(blockData.getKey());
            }

            var newBlockData = new SlimefunBlockData(target, blockData);
            var key = new RecordKey(DataScope.BLOCK_RECORD);
            if (LocationUtils.isSameChunk(blockData.getLocation().getChunk(), target.getChunk())) {
                if (chunkData == null) {
                    chunkData = getChunkDataCache(chunk, true);
                }
                key.addField(FieldKey.CHUNK);
            } else {
                chunkData = getChunkDataCache(target.getChunk(), true);
            }

            chunkData.addBlockCacheInternal(newBlockData, true);

            if (menu != null) {
                newBlockData.setBlockMenu(new BlockMenu(menu.getPreset(), target, menu.getInventory()));
            }

            key.addField(FieldKey.LOCATION);
            key.addCondition(FieldKey.LOCATION, blockData.getKey());

            var data = new RecordSet();
            data.put(FieldKey.LOCATION, newBlockData.getKey());
            data.put(FieldKey.CHUNK, chunkData.getKey());
            data.put(FieldKey.SLIMEFUN_ID, blockData.getSfId());
            var scopeKey = new LocationKey(DataScope.NONE, blockData.getLocation());
            synchronized (delayedWriteTasks) {
                var it = delayedWriteTasks.entrySet().iterator();
                while (it.hasNext()) {
                    var next = it.next();
                    if (scopeKey.equals(next.getKey().getParent())) {
                        next.getValue().runUnsafely();
                        it.remove();
                    }
                }
            }

            scheduleWriteTask(scopeKey, key, data, true);

            if (hasTicker) {
                Slimefun.getTickerTask().enableTicker(target);
            }
        } finally {
            if (menu != null) {
                menu.unlock();
            }
        }
    }

    private void move(SlimefunUniversalBlockData uniData, Location target) {
        checkDestroy();
        requireCompleteInventoryLoad(uniData.getKey());
        requireMutableBlockLocation(target);
        var lastPresent = uniData.getLastPresent();
        if (lastPresent == null) {
            uniData.setLastPresent(target);
            return;
        }
        var loc = lastPresent.toLocation();
        requireMutableBlockLocation(loc);

        if (LocationUtils.isSameLoc(loc, target)) {
            return;
        }

        var hasTicker = false;

        if (uniData.isDataLoaded() && Slimefun.getRegistry().getTickerBlocks().contains(uniData.getSfId())) {
            Slimefun.getTickerTask().disableTicker(loc);
            hasTicker = true;
        }

        UniversalMenu menu = null;

        if (uniData.isDataLoaded() && uniData.getMenu() != null) {
            menu = uniData.getMenu();
            menu.lock();
        }

        try {
            uniData.setLastPresent(target);

            Slimefun.getBlockDataService()
                    .updateUniversalDataUUID(
                            target.getBlock(), uniData.getUUID().toString());

            if (menu != null) {
                menu.update(target);
            }

            if (hasTicker) {
                Slimefun.getTickerTask().enableTicker(target, uniData.getUUID());
            }
        } finally {
            if (menu != null) {
                menu.unlock();
            }
        }
    }

    private SlimefunBlockData getBlockDataFromCache(String cKey, String lKey) {
        checkDestroy();
        var chunkData = loadedChunk.get(cKey);
        return chunkData == null ? null : chunkData.getBlockCacheInternal(lKey);
    }

    public void loadChunk(Chunk chunk, boolean isNewChunk) {
        loadChunk(chunk, isNewChunk, false);
    }

    public void loadChunk(Chunk chunk, boolean isNewChunk, boolean forceReadData) {
        checkDestroy();
        var chunkData = getChunkDataCache(chunk, true);
        // what if the database already contains data here but the WORLD CHUNK is newly generated

        // escape all return if forceRead Flag is true
        if (forceReadData) {
            if (chunkDataLoadMode.readCacheOnly()) {
                // if readCache only , then all the chunkData get from cache is DataLoaded
                // since we removed initLoading, so we set DataLoad false here, so it will trigger the loadChunkData
                chunkData.setIsDataLoaded(false);
            }
            // else force loading, escape all returns
        } else {
            // not force loading
            if (isNewChunk) {
                chunkData.setIsDataLoaded(true);
                Bukkit.getPluginManager().callEvent(new SlimefunChunkDataLoadEvent(chunkData));
                return;
            }

            if (chunkData.isDataLoaded()) {
                return;
            }
        }

        loadChunkData(chunkData);

        // 按区块加载方块数据

        var key = new RecordKey(DataScope.BLOCK_RECORD);
        key.addField(FieldKey.LOCATION);
        key.addField(FieldKey.SLIMEFUN_ID);
        key.addCondition(FieldKey.CHUNK, chunkData.getKey());

        getData(key).forEach(block -> {
            var lKey = block.getString(FieldKey.LOCATION);
            var sfId = block.getString(FieldKey.SLIMEFUN_ID);
            var sfItem = SlimefunItem.getById(sfId);
            if (sfItem == null) {
                return;
            }

            var cache = getBlockDataFromCache(chunkData.getKey(), lKey);
            var blockData = cache == null ? new SlimefunBlockData(LocationUtils.toLocation(lKey), sfId) : cache;
            chunkData.addBlockCacheInternal(blockData, false);

            if (sfItem.loadDataByDefault()) {
                scheduleReadTask(() -> loadBlockData(blockData));
            }
        });

        Bukkit.getPluginManager().callEvent(new SlimefunChunkDataLoadEvent(chunkData));
    }

    public void loadWorld(World world) {
        var start = System.currentTimeMillis();
        var worldName = world.getName();
        logger.log(Level.INFO, "Loading Slimefun block data for world {0}...", worldName);
        var chunkKeys = new HashSet<String>();
        var key = new RecordKey(DataScope.CHUNK_DATA);
        key.addField(FieldKey.CHUNK);
        key.addCondition(FieldKey.CHUNK, worldName + ";%");
        getData(key, true).forEach(data -> chunkKeys.add(data.getString(FieldKey.CHUNK)));

        key = new RecordKey(DataScope.BLOCK_RECORD);
        key.addField(FieldKey.CHUNK);
        key.addCondition(FieldKey.CHUNK, world.getName() + ";%");
        getData(key, true).forEach(data -> chunkKeys.add(data.getString(FieldKey.CHUNK)));

        if (Slimefun.getSchedulerService().isFolia()) {
            // Folia's global region must not directly touch chunk state. Resolve each stored chunk on the
            // scheduler for the region that owns its coordinates, while Paper keeps the legacy synchronous path.
            chunkKeys.forEach(cKey -> scheduleWorldChunkLoad(world, cKey));
            logger.log(Level.INFO, "World {0} Slimefun data scheduled across owning regions in {1}ms", new Object[] {
                worldName, (System.currentTimeMillis() - start)
            });
        } else {
            chunkKeys.forEach(cKey -> loadChunk(LocationUtils.toChunk(world, cKey), false, true));
            logger.log(Level.INFO, "World {0} data loaded in {1}ms", new Object[] {
                worldName, (System.currentTimeMillis() - start)
            });
        }
    }

    private void scheduleWorldChunkLoad(World world, String cKey) {
        try {
            var coordinates = cKey.split(";")[1].split(":");
            int chunkX = Integer.parseInt(coordinates[0]);
            int chunkZ = Integer.parseInt(coordinates[1]);
            var anchor = new Location(world, chunkX << 4, 0, chunkZ << 4);
            var task = Slimefun.getSchedulerService().runAt(anchor, () -> {
                try {
                    loadChunk(world.getChunkAt(chunkX, chunkZ, false), false, true);
                } catch (RuntimeException | LinkageError failure) {
                    logger.log(
                            Level.SEVERE,
                            "Failed to load Slimefun startup block data for " + cKey + " on its owning region.",
                            failure);
                }
            });
            if (task.isCancelled()) {
                logger.log(
                        Level.WARNING,
                        "Skipped Slimefun startup block-data load because scheduling was rejected: {0}",
                        cKey);
            }
        } catch (RuntimeException failure) {
            logger.log(
                    Level.WARNING,
                    "Unable to resolve stored Slimefun chunk key during world startup: " + cKey,
                    failure);
        }
    }

    public void loadUniversalRecord() {
        var uniKey = new RecordKey(DataScope.UNIVERSAL_RECORD);
        uniKey.addField(FieldKey.UNIVERSAL_UUID);
        uniKey.addField(FieldKey.SLIMEFUN_ID);
        uniKey.addField(FieldKey.UNIVERSAL_TRAITS);

        var uniRecord = getData(uniKey);

        uniRecord.forEach(data -> {
            var sfId = data.getString(FieldKey.SLIMEFUN_ID);
            var sfItem = SlimefunItem.getById(sfId);

            if (sfItem == null) {
                return;
            }

            var uuid = data.getUUID(FieldKey.UNIVERSAL_UUID);
            var traitsData = data.getString(FieldKey.UNIVERSAL_TRAITS);
            var traits = new HashSet<UniversalDataTrait>();

            // Read trait(s) of universal data
            if (traitsData != null && !traitsData.isBlank()) {
                for (String traitStr : traitsData.split(",")) {
                    try {
                        traits.add(UniversalDataTrait.valueOf(traitStr.toUpperCase()));
                    } catch (IllegalArgumentException e) {
                        logger.log(Level.WARNING, "Invalid trait '{0}' for universal data {1}.", new Object[] {
                            traitStr, uuid
                        });
                    }
                }
            }

            var uniData = traits.contains(UniversalDataTrait.BLOCK)
                    ? new SlimefunUniversalBlockData(uuid, sfId)
                    : new SlimefunUniversalData(uuid, sfId);

            traits.forEach(uniData::addTrait);

            scheduleReadTask(() -> loadUniversalData(uniData));
        });
    }

    private void loadChunkData(SlimefunChunkData chunkData) {
        if (chunkData.isDataLoaded()) {
            return;
        }
        var key = new RecordKey(DataScope.CHUNK_DATA);
        key.addField(FieldKey.DATA_KEY);
        key.addField(FieldKey.DATA_VALUE);
        key.addCondition(FieldKey.CHUNK, chunkData.getKey());

        lock.lock(key);
        try {
            if (chunkData.isDataLoaded()) {
                return;
            }
            getData(key)
                    .forEach(data -> chunkData.setCacheInternal(
                            data.getString(FieldKey.DATA_KEY),
                            DataUtils.blockDataDebase64(data.getString(FieldKey.DATA_VALUE)),
                            false));
            chunkData.setIsDataLoaded(true);
        } finally {
            lock.unlock(key);
        }
    }

    public void loadBlockData(SlimefunBlockData blockData) {
        if (blockData.isDataLoaded()) {
            return;
        }
        var key = new RecordKey(DataScope.BLOCK_DATA);
        key.addCondition(FieldKey.LOCATION, blockData.getKey());
        key.addField(FieldKey.DATA_KEY);
        key.addField(FieldKey.DATA_VALUE);

        lock.lock(key);
        try {
            if (blockData.isDataLoaded()) {
                return;
            }
            incompleteInventoryLoads.add(blockData.getKey());
            var kvData = getData(key);
            var menuKey = new RecordKey(DataScope.BLOCK_INVENTORY);
            menuKey.addCondition(FieldKey.LOCATION, blockData.getKey());
            menuKey.addField(FieldKey.INVENTORY_SLOT);
            menuKey.addField(FieldKey.INVENTORY_ITEM);
            var invData = getData(menuKey);
            // Decode before changing cached values, loaded flags, menus or ticker registration.
            var inv = StoredInventoryReader.read(invData, 54, "block " + blockData.getKey());
            var sfItem = SlimefunItem.getById(blockData.getSfId());
            if (sfItem instanceof UniversalBlock) {
                migrateUniversalData(blockData.getLocation(), blockData.getSfId(), kvData, invData);
                incompleteInventoryLoads.remove(blockData.getKey());
                return;
            }

            var menuPreset = BlockMenuPreset.getPreset(blockData.getSfId());
            if (menuPreset == null && StoredInventoryReader.hasItems(inv)) {
                throw StoredInventoryReader.refused(blockData.getKey(), "inventory preset is unavailable", null);
            }
            kvData.forEach(recordSet -> blockData.setCacheInternal(
                    recordSet.getString(FieldKey.DATA_KEY),
                    DataUtils.blockDataDebase64(recordSet.getString(FieldKey.DATA_VALUE)),
                    false));
            // Historical presets may read their KV state while constructing the menu.
            // Inventory saves remain blocked until construction and snapshotting complete.
            blockData.setIsDataLoaded(true);
            if (menuPreset != null) {
                blockData.setBlockMenu(new BlockMenu(menuPreset, blockData.getLocation(), inv));
                var content = blockData.getMenuContents();
                if (content != null) {
                    invSnapshots.put(blockData.getKey(), new InvSnapshot(content));
                }
            }
            incompleteInventoryLoads.remove(blockData.getKey());
            if (sfItem != null && sfItem.isTicking()) {
                Slimefun.getTickerTask().enableTicker(blockData.getLocation());
            }
        } catch (Exception | LinkageError failure) {
            incompleteInventoryLoads.add(blockData.getKey());
            blockData.setIsDataLoaded(false);
            var menu = blockData.getBlockMenu();
            if (menu != null) {
                menu.lock();
            }
            blockData.setBlockMenu(null);
            invSnapshots.remove(blockData.getKey());
            throw StoredInventoryReader.refused("block " + blockData.getKey(), "load did not complete", failure);
        } finally {
            lock.unlock(key);
        }
    }

    public void loadDataAsync(ASlimefunDataContainer container, IAsyncReadCallback<ASlimefunDataContainer> callback) {
        scheduleReadTask(() -> {
            if (container instanceof SlimefunBlockData blockData) {
                loadBlockData(blockData);
            } else if (container instanceof SlimefunUniversalData uniData) {
                loadUniversalData(uniData);
            }

            invokeCallback(callback, container);
        });
    }

    public void loadBlockDataAsync(SlimefunBlockData blockData, IAsyncReadCallback<SlimefunBlockData> callback) {
        scheduleReadTask(() -> {
            loadBlockData(blockData);
            invokeCallback(callback, blockData);
        });
    }

    public void loadBlockDataAsync(
            List<SlimefunBlockData> blockDataList, IAsyncReadCallback<List<SlimefunBlockData>> callback) {
        scheduleReadTask(() -> {
            blockDataList.forEach(this::loadBlockData);
            invokeCallback(callback, blockDataList);
        });
    }

    @ParametersAreNonnullByDefault
    public void loadUniversalData(SlimefunUniversalData uniData) {
        if (uniData.isDataLoaded()) {
            return;
        }
        var key = new RecordKey(DataScope.UNIVERSAL_DATA);
        key.addCondition(FieldKey.UNIVERSAL_UUID, uniData.getKey());
        key.addField(FieldKey.DATA_KEY);
        key.addField(FieldKey.DATA_VALUE);
        lock.lock(key);
        try {
            if (uniData.isDataLoaded()) {
                return;
            }
            incompleteInventoryLoads.add(uniData.getKey());
            if (uniData instanceof SlimefunUniversalBlockData block) {
                inventoryRecoveryLocations.remember(uniData.getKey(), block.getKnownLocationKey());
            }
            var kvData = getData(key);
            if (uniData.hasTrait(UniversalDataTrait.BLOCK)) {
                for (RecordSet row : kvData) {
                    if (UniversalDataTrait.BLOCK.getReservedKey().equals(row.getString(FieldKey.DATA_KEY))) {
                        inventoryRecoveryLocations.remember(
                                uniData.getKey(),
                                InventoryRecoveryLocations.canonicalLocationKey(
                                        DataUtils.blockDataDebase64(row.getString(FieldKey.DATA_VALUE))));
                    }
                }
            }
            var menuKey = new RecordKey(DataScope.UNIVERSAL_INVENTORY);
            menuKey.addCondition(FieldKey.UNIVERSAL_UUID, uniData.getKey());
            menuKey.addField(FieldKey.INVENTORY_SLOT);
            menuKey.addField(FieldKey.INVENTORY_ITEM);
            var inv = StoredInventoryReader.read(getData(menuKey), 54, "universal " + uniData.getKey());
            var menuPreset = uniData.hasTrait(UniversalDataTrait.INVENTORY)
                    ? UniversalMenuPreset.getPreset(uniData.getSfId())
                    : null;
            if (menuPreset == null && StoredInventoryReader.hasItems(inv)) {
                throw StoredInventoryReader.refused(uniData.getKey(), "inventory trait or preset is unavailable", null);
            }
            kvData.forEach(recordSet -> uniData.setCacheInternal(
                    recordSet.getString(FieldKey.DATA_KEY),
                    DataUtils.blockDataDebase64(recordSet.getString(FieldKey.DATA_VALUE)),
                    false));
            uniData.setIsDataLoaded(true);
            loadedUniversalData.putIfAbsent(uniData.getUUID(), uniData);
            if (menuPreset != null) {
                Location location = null;
                if (uniData instanceof SlimefunUniversalBlockData ubd
                        && ubd.hasTrait(UniversalDataTrait.BLOCK)
                        && ubd.getLastPresent() != null) {
                    location = ubd.getLastPresent().toLocation();
                }
                uniData.setMenu(new UniversalMenu(menuPreset, uniData.getUUID(), location, inv));
                var content = uniData.getMenuContents();
                if (content != null) {
                    invSnapshots.put(uniData.getKey(), new InvSnapshot(content));
                }
            }
            // Publish ticking only after the entire stored inventory was decoded and installed.
            if (uniData instanceof SlimefunUniversalBlockData ubd && ubd.hasTrait(UniversalDataTrait.BLOCK)) {
                var sfItem = SlimefunItem.getById(ubd.getSfId());
                if (sfItem != null && sfItem.isTicking() && ubd.getLastPresent() != null) {
                    Slimefun.getTickerTask().enableTicker(ubd.getLastPresent().toLocation(), ubd.getUUID());
                }
            }
            incompleteInventoryLoads.remove(uniData.getKey());
            inventoryRecoveryLocations.clear(uniData.getKey());
        } catch (Exception | LinkageError failure) {
            incompleteInventoryLoads.add(uniData.getKey());
            uniData.setIsDataLoaded(false);
            var menu = uniData.getMenu();
            if (menu != null) {
                menu.lock();
            }
            uniData.setMenu(null);
            invSnapshots.remove(uniData.getKey());
            loadedUniversalData.remove(uniData.getUUID(), uniData);
            throw StoredInventoryReader.refused("universal " + uniData.getKey(), "load did not complete", failure);
        } finally {
            lock.unlock(key);
        }
    }

    @ParametersAreNonnullByDefault
    public void loadUniversalDataAsync(
            SlimefunUniversalData uniData, IAsyncReadCallback<SlimefunUniversalData> callback) {
        scheduleReadTask(() -> {
            loadUniversalData(uniData);
            invokeCallback(callback, uniData);
        });
    }

    public SlimefunChunkData getChunkData(Chunk chunk) {
        checkDestroy();
        loadChunk(chunk, false);
        return getChunkDataCache(chunk, false);
    }

    public CompletableFuture<SlimefunChunkData> getChunkDataAsync(Chunk chunk) {
        checkDestroy();
        SlimefunChunkData chunkData = getChunkDataCache(chunk, true);
        if (chunkData.isDataLoaded()) {
            return CompletableFuture.completedFuture(chunkData);
        }

        // loadChunk fires SlimefunChunkDataLoadEvent and touches Bukkit chunk state. Route the load through the
        // scheduler that owns this chunk without reading a block from an arbitrary caller thread first.
        CompletableFuture<SlimefunChunkData> future = new CompletableFuture<>();
        var task = Slimefun.runSyncAt(chunkSchedulerAnchor(chunk), () -> {
            try {
                future.complete(getChunkData(chunk));
            } catch (Throwable error) {
                future.completeExceptionally(error);
            }
        });

        // Unit tests execute runSync immediately and return null. A null task with an incomplete
        // future means the plugin was disabled before the load could be scheduled.
        if (task == null && !future.isDone()) {
            future.completeExceptionally(
                    new IllegalStateException("Cannot load Slimefun chunk data because the plugin is disabled."));
        }
        return future;
    }

    static Location chunkSchedulerAnchor(Chunk chunk) {
        return new Location(chunk.getWorld(), chunk.getX() << 4, 0, chunk.getZ() << 4);
    }

    public void getChunkDataAsync(Chunk chunk, IAsyncReadCallback<SlimefunChunkData> callback) {
        getChunkDataAsync(chunk).whenComplete((result, error) -> {
            if (error != null) {
                logger.log(Level.SEVERE, "Exception thrown while loading Slimefun chunk data.", error);
            } else {
                invokeCallback(callback, result);
            }
        });
    }

    public void saveAllBlockInventories() {
        var chunks = new HashSet<>(loadedChunk.values());
        chunks.forEach(chunk -> chunk.getAllCacheInternal().forEach(block -> {
            if (block.isPendingRemove() || !block.isDataLoaded()) {
                return;
            }
            var menu = block.getBlockMenu();
            if (menu == null || !menu.isDirty()) {
                return;
            }

            saveBlockInventory(block);
        }));
    }

    public void saveAllUniversalInventories() {
        var uniData = new HashSet<>(loadedUniversalData.values());
        uniData.forEach(data -> {
            if (data.isPendingRemove() || !data.isDataLoaded()) {
                return;
            }
            var menu = data.getMenu();
            if (menu == null || !menu.isDirty()) {
                return;
            }

            saveUniversalInventory(data);
        });
    }

    private void requireCompleteInventoryLoad(String owner) {
        if (incompleteInventoryLoads.contains(owner)) {
            throw StoredInventoryReader.refused(owner, "read not completed", null);
        }
    }

    /**
     * Reports whether a known incomplete inventory load protects this block position.
     * This read-only check does not fetch data, resolve chunks, or clear recovery state.
     * Callers handling player events should cancel before producing drops or changing blocks.
     */
    public boolean isInventoryMutationBlocked(@Nonnull Location location) {
        if (incompleteInventoryLoads.isEmpty() && inventoryRecoveryLocations.isEmpty()) {
            return false;
        }
        String key = LocationUtils.getLocKey(location);
        return incompleteInventoryLoads.contains(key) || inventoryRecoveryLocations.containsLocation(key);
    }

    void requireMutableBlockLocation(Location location) {
        requireCompleteInventoryLoad(LocationUtils.getLocKey(location));
        requireNoIncompleteUniversalAt(location);
    }

    private void requireNoIncompleteUniversalAt(Location location) {
        String key = LocationUtils.getLocKey(location);
        if (inventoryRecoveryLocations.containsLocation(key)) {
            throw StoredInventoryReader.refused(key, "a universal inventory at this location needs recovery", null);
        }
    }

    private void requireMutableScope(World world, @Nullable Chunk chunk) {
        String prefix = world.getName() + ";";
        Set<String> protectedLocations = new HashSet<>(incompleteInventoryLoads);
        protectedLocations.addAll(inventoryRecoveryLocations.locationKeys());
        for (String key : protectedLocations) {
            if (key.startsWith(prefix)
                    && (chunk == null
                            || InventoryRecoveryLocations.isInChunk(key, prefix, chunk.getX(), chunk.getZ()))) {
                throw StoredInventoryReader.refused(key, "bulk removal includes an incomplete inventory", null);
            }
        }
    }

    public void saveBlockInventory(SlimefunBlockData blockData) {
        saveBlockInventoryAsync(blockData).whenComplete((ignored, failure) -> {
            if (failure != null) {
                logger.log(Level.SEVERE, "Failed to persist Slimefun block inventory " + blockData.getKey(), failure);
            }
        });
    }

    /**
     * Stages an immutable block-inventory state and completes only after its exact
     * changed-slot write batch has reached the database queue completion boundary.
     */
    public CompletableFuture<Void> saveBlockInventoryAsync(@Nonnull SlimefunBlockData blockData) {
        try {
            requireCompleteInventoryLoad(blockData.getKey());
            BlockMenu menu = blockData.getBlockMenu();
            long changeSequence = menu == null ? 0L : menu.captureChangeSequence();
            ItemStack[] contents = copyInventoryContents(blockData.getMenuContents());
            String snapshotKey = blockData.getKey();
            String chainKey = "block:" + snapshotKey;
            InvSnapshot stagedSnapshot = contents == null ? null : new InvSnapshot(contents);
            Map<Integer, InventoryWrite> stagedWrites =
                    stageInventoryWrites(DataScope.BLOCK_INVENTORY, FieldKey.LOCATION, snapshotKey, contents);

            return chainInventorySave(
                    chainKey,
                    () -> persistInventoryStage(
                            snapshotKey,
                            new LocationKey(DataScope.NONE, blockData.getLocation()),
                            contents,
                            stagedSnapshot,
                            stagedWrites,
                            menu,
                            changeSequence));
        } catch (RuntimeException | LinkageError failure) {
            // No write has been submitted and no dirty token/snapshot was acknowledged.
            // Keep the previous persisted baseline and let a later save retry this state.
            return CompletableFuture.failedFuture(failure);
        }
    }

    public void saveBlockInventorySlot(SlimefunBlockData blockData, int slot) {
        // Route legacy slot-save callers through the acknowledgement-aware batch.
        // Persisting all currently changed slots avoids creating an untracked
        // per-slot write that can race the inventory snapshot.
        saveBlockInventory(blockData);
    }

    public Set<SlimefunChunkData> getAllLoadedChunkData() {
        return new HashSet<>(loadedChunk.values());
    }

    public Set<SlimefunUniversalData> getAllLoadedUniversalData() {
        return new HashSet<>(loadedUniversalData.values());
    }

    public void removeAllDataInChunk(Chunk chunk) {
        checkDestroy();
        requireMutableScope(chunk.getWorld(), chunk);
        var cKey = LocationUtils.getChunkKey(chunk);
        var cache = loadedChunk.remove(cKey);

        if (cache != null && cache.isDataLoaded()) {
            cache.getAllBlockData().forEach(this::clearBlockCacheAndTasks);
        }
        deleteChunkAndBlockDataDirectly(cKey);
    }

    public void removeAllDataInChunkAsync(Chunk chunk, Runnable onFinishedCallback) {
        scheduleWriteTask(() -> {
            removeAllDataInChunk(chunk);
            onFinishedCallback.run();
        });
    }

    public void removeAllDataInWorld(World world) {
        checkDestroy();
        requireMutableScope(world, null);
        // 1. remove block cache
        var loadedBlockData = new HashSet<SlimefunBlockData>();
        for (var chunkData : getAllLoadedChunkData(world)) {
            loadedBlockData.addAll(chunkData.getAllBlockData());
            chunkData.removeAllCacheInternal();
        }

        // 2. remove ticker and delayed tasks
        loadedBlockData.forEach(this::clearBlockCacheAndTasks);

        // 3. remove from database
        var prefix = world.getName() + ";";
        deleteChunkAndBlockDataDirectly(prefix + "%");

        // 4. remove chunk cache
        loadedChunk.entrySet().removeIf(entry -> entry.getKey().startsWith(prefix));
    }

    public void removeAllDataInWorldAsync(World world, Runnable onFinishedCallback) {
        scheduleWriteTask(() -> {
            removeAllDataInWorld(world);
            onFinishedCallback.run();
        });
    }

    public void saveUniversalInventory(@Nonnull SlimefunUniversalData universalData) {
        saveUniversalInventoryAsync(universalData).whenComplete((ignored, failure) -> {
            if (failure != null) {
                logger.log(
                        Level.SEVERE,
                        "Failed to persist Slimefun universal inventory " + universalData.getKey(),
                        failure);
            }
        });
    }

    /**
     * Stages an immutable universal-inventory state and serializes save attempts
     * for the same UUID so acknowledgements cannot complete out of order.
     */
    public CompletableFuture<Void> saveUniversalInventoryAsync(@Nonnull SlimefunUniversalData universalData) {
        try {
            requireCompleteInventoryLoad(universalData.getKey());
            UniversalMenu menu = universalData.getMenu();
            long changeSequence = menu == null ? 0L : menu.captureChangeSequence();
            ItemStack[] contents = copyInventoryContents(universalData.getMenuContents());
            String snapshotKey = universalData.getKey();
            String chainKey = "universal:" + snapshotKey;
            InvSnapshot stagedSnapshot = contents == null ? null : new InvSnapshot(contents);
            Map<Integer, InventoryWrite> stagedWrites = stageInventoryWrites(
                    DataScope.UNIVERSAL_INVENTORY, FieldKey.UNIVERSAL_UUID, universalData.getKey(), contents);

            return chainInventorySave(
                    chainKey,
                    () -> persistInventoryStage(
                            snapshotKey,
                            new UUIDKey(DataScope.NONE, universalData.getKey()),
                            contents,
                            stagedSnapshot,
                            stagedWrites,
                            menu,
                            changeSequence));
        } catch (RuntimeException | LinkageError failure) {
            // No write has been submitted and no dirty token/snapshot was acknowledged.
            // Keep the previous persisted baseline and let a later save retry this state.
            return CompletableFuture.failedFuture(failure);
        }
    }

    private CompletableFuture<Void> chainInventorySave(
            @Nonnull String chainKey, @Nonnull java.util.function.Supplier<CompletableFuture<Void>> saveAttempt) {
        synchronized (inventorySaveChains) {
            CompletableFuture<Void> previous = inventorySaveChains.get(chainKey);
            CompletableFuture<Void> start = previous == null
                    ? CompletableFuture.completedFuture(null)
                    : previous.handle((ignored, failure) -> null);
            CompletableFuture<Void> next = start.thenCompose(ignored -> saveAttempt.get());
            inventorySaveChains.put(chainKey, next);
            next.whenComplete((ignored, failure) -> {
                synchronized (inventorySaveChains) {
                    inventorySaveChains.remove(chainKey, next);
                }
            });
            return next;
        }
    }

    private CompletableFuture<Void> persistInventoryStage(
            @Nonnull String snapshotKey,
            @Nonnull ScopeKey scopeKey,
            @Nullable ItemStack[] contents,
            @Nullable InvSnapshot stagedSnapshot,
            @Nonnull Map<Integer, InventoryWrite> stagedWrites,
            @Nullable DirtyChestMenu menu,
            long changeSequence) {
        requireCompleteInventoryLoad(snapshotKey);
        InvSnapshot acknowledged = invSnapshots.get(snapshotKey);
        Set<Integer> changed = uncertainInventoryBaselines.contains(snapshotKey)
                ? new HashSet<>(stagedWrites.keySet())
                : InvStorageUtils.getChangedSlots(acknowledged, contents);

        if (changed.isEmpty()) {
            acknowledgeInventoryStage(snapshotKey, stagedSnapshot, menu, changeSequence);
            uncertainInventoryBaselines.remove(snapshotKey);
            return CompletableFuture.completedFuture(null);
        }

        var completions = new ArrayList<CompletableFuture<Void>>(changed.size());
        try {
            for (int slot : changed) {
                InventoryWrite write = stagedWrites.get(slot);
                if (write == null) {
                    throw new IllegalStateException("Missing staged inventory write for slot " + slot);
                }

                CompletableFuture<Void> completion = write.data() == null
                        ? scheduleDeleteTaskWithCompletion(scopeKey, write.key(), true)
                        : scheduleWriteTaskWithCompletion(scopeKey, write.key(), write.data(), true);
                completions.add(completion);
            }
        } catch (RuntimeException | LinkageError failure) {
            completions.add(CompletableFuture.failedFuture(failure));
        }

        CompletableFuture<Void> batch = CompletableFuture.allOf(completions.toArray(CompletableFuture[]::new));
        return batch.whenComplete((ignored, failure) -> {
            if (failure == null) {
                acknowledgeInventoryStage(snapshotKey, stagedSnapshot, menu, changeSequence);
                uncertainInventoryBaselines.remove(snapshotKey);
            } else {
                // A failed/partially submitted batch has an unknown database
                // baseline. Force the next retry to rewrite/delete every staged
                // slot, including the contents == null deletion case.
                invSnapshots.remove(snapshotKey);
                uncertainInventoryBaselines.add(snapshotKey);
            }
        });
    }

    private void acknowledgeInventoryStage(
            @Nonnull String snapshotKey,
            @Nullable InvSnapshot stagedSnapshot,
            @Nullable DirtyChestMenu menu,
            long changeSequence) {
        if (stagedSnapshot == null) {
            invSnapshots.remove(snapshotKey);
        } else {
            invSnapshots.put(snapshotKey, stagedSnapshot);
        }

        if (menu != null) {
            menu.acknowledgeChanges(changeSequence);
        }
    }

    private Map<Integer, InventoryWrite> stageInventoryWrites(
            @Nonnull DataScope inventoryScope,
            @Nonnull FieldKey ownerField,
            @Nonnull String ownerValue,
            @Nullable ItemStack[] contents) {
        // Block and universal menus are chest-style inventories with a
        // maximum persisted slot range of 0..53. Always stage that complete
        // range so menu-size shrinkage and failed deletion retries can remove
        // stale higher slots deterministically.
        int size = 54;
        Map<Integer, InventoryWrite> staged = new HashMap<>(size);

        for (int slot = 0; slot < size; slot++) {
            var key = new RecordKey(inventoryScope);
            key.addCondition(ownerField, ownerValue);
            key.addCondition(FieldKey.INVENTORY_SLOT, slot + "");
            key.addField(FieldKey.INVENTORY_ITEM);

            ItemStack item = contents == null || slot >= contents.length ? null : contents[slot];
            if (item == null || item.isEmpty()) {
                staged.put(slot, new InventoryWrite(key, null));
            } else {
                var data = new RecordSet();
                data.put(ownerField, ownerValue);
                data.put(FieldKey.INVENTORY_SLOT, slot + "");
                // RecordSet serializes the ItemStack immediately. No Bukkit/Paper
                // inventory serialization is deferred to a database completion thread.
                data.put(FieldKey.INVENTORY_ITEM, item);
                staged.put(slot, new InventoryWrite(key, data));
            }
        }

        return staged;
    }

    @Nullable private ItemStack[] copyInventoryContents(@Nullable ItemStack[] contents) {
        if (contents == null) {
            return null;
        }

        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = contents[i] == null ? null : contents[i].clone();
        }
        return copy;
    }

    @Nullable private ItemStack snapshotInventoryItem(@Nullable ItemStack[] contents, int slot) {
        if (contents == null || slot < 0 || slot >= contents.length) {
            return null;
        }

        ItemStack item = contents[slot];
        return item == null ? null : item.clone();
    }

    private record InventoryWrite(
            @Nonnull RecordKey key, @Nullable RecordSet data) {}

    public Set<SlimefunChunkData> getAllLoadedChunkData(World world) {
        var prefix = world.getName() + ";";
        var re = new HashSet<SlimefunChunkData>();
        loadedChunk.forEach((k, v) -> {
            if (k.startsWith(prefix)) {
                re.add(v);
            }
        });
        return re;
    }

    public void removeFromAllChunkInWorld(World world, String key) {
        var req = new RecordKey(DataScope.CHUNK_DATA);
        req.addCondition(FieldKey.CHUNK, world.getName() + ";%");
        req.addCondition(FieldKey.DATA_KEY, key);
        deleteData(req);
        getAllLoadedChunkData(world).forEach(data -> data.removeData(key));
    }

    public void removeFromAllChunkInWorldAsync(World world, String key, Runnable onFinishedCallback) {
        scheduleWriteTask(() -> {
            removeFromAllChunkInWorld(world, key);
            onFinishedCallback.run();
        });
    }

    private void scheduleDelayedBlockInvUpdate(SlimefunBlockData blockData, int slot) {
        var scopeKey = new LocationKey(DataScope.NONE, blockData.getLocation());
        var reqKey = new RecordKey(DataScope.BLOCK_INVENTORY);
        reqKey.addCondition(FieldKey.LOCATION, blockData.getKey());
        reqKey.addCondition(FieldKey.INVENTORY_SLOT, slot + "");
        reqKey.addField(FieldKey.INVENTORY_ITEM);

        if (enableDelayedSaving) {
            scheduleDelayedUpdateTask(
                    new LinkedKey(scopeKey, reqKey),
                    () -> scheduleBlockInvUpdate(
                            scopeKey, reqKey, blockData.getKey(), blockData.getMenuContents(), slot));
        } else {
            scheduleBlockInvUpdate(scopeKey, reqKey, blockData.getKey(), blockData.getMenuContents(), slot);
        }
    }

    private void scheduleBlockInvUpdate(ScopeKey scopeKey, RecordKey reqKey, String lKey, ItemStack[] inv, int slot) {
        requireCompleteInventoryLoad(lKey);
        ItemStack item = snapshotInventoryItem(inv, slot);

        if (item == null || item.isEmpty()) {
            scheduleDeleteTask(scopeKey, reqKey, true);
        } else {
            try {
                var data = new RecordSet();
                data.put(FieldKey.LOCATION, lKey);
                data.put(FieldKey.INVENTORY_SLOT, slot + "");
                data.put(FieldKey.INVENTORY_ITEM, item);
                scheduleWriteTask(scopeKey, reqKey, data, true);
            } catch (RuntimeException | LinkageError failure) {
                logger.log(
                        Level.WARNING,
                        "Could not serialize inventory slot " + lKey + ":" + slot
                                + "; the existing stored value was retained.",
                        failure);
            }
        }
    }

    /**
     * Save universal inventory by async way
     *
     * @param ubd  {@link SlimefunUniversalBlockData}
     * @param slot updated item slot
     */
    private void scheduleDelayedUniversalInvUpdate(SlimefunUniversalData ubd, int slot) {
        var scopeKey = new UUIDKey(DataScope.NONE, ubd.getKey());
        var reqKey = new RecordKey(DataScope.UNIVERSAL_INVENTORY);
        reqKey.addCondition(FieldKey.UNIVERSAL_UUID, ubd.getKey());
        reqKey.addCondition(FieldKey.INVENTORY_SLOT, slot + "");
        reqKey.addField(FieldKey.INVENTORY_ITEM);

        if (enableDelayedSaving) {
            scheduleDelayedUpdateTask(
                    new LinkedKey(scopeKey, reqKey),
                    () -> scheduleUniversalInvUpdate(scopeKey, reqKey, ubd.getKey(), ubd.getMenuContents(), slot));
        } else {
            scheduleUniversalInvUpdate(scopeKey, reqKey, ubd.getKey(), ubd.getMenuContents(), slot);
        }
    }

    private void scheduleUniversalInvUpdate(
            ScopeKey scopeKey, RecordKey reqKey, String uuid, ItemStack[] inv, int slot) {
        requireCompleteInventoryLoad(uuid);
        ItemStack item = snapshotInventoryItem(inv, slot);

        if (item == null || item.isEmpty()) {
            scheduleDeleteTask(scopeKey, reqKey, true);
        } else {
            try {
                var data = new RecordSet();
                data.put(FieldKey.UNIVERSAL_UUID, uuid);
                data.put(FieldKey.INVENTORY_SLOT, slot + "");
                data.put(FieldKey.INVENTORY_ITEM, item);
                scheduleWriteTask(scopeKey, reqKey, data, true);
            } catch (RuntimeException | LinkageError failure) {
                logger.log(
                        Level.WARNING,
                        "Could not serialize inventory slot " + uuid + ":" + slot
                                + "; the existing stored value was retained.",
                        failure);
            }
        }
    }

    @Override
    public void shutdown() {
        saveAllBlockInventories();
        saveAllUniversalInventories();
        if (enableDelayedSaving) {
            looperTask.cancel();
            executeAllDelayedTasks();
        }
        awaitInventorySaveChains();
        super.shutdown();
    }

    private void awaitInventorySaveChains() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);

        while (System.nanoTime() < deadline) {
            CompletableFuture<?>[] snapshot;
            synchronized (inventorySaveChains) {
                if (inventorySaveChains.isEmpty()) {
                    return;
                }

                snapshot = inventorySaveChains.values().stream()
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
                logger.log(Level.WARNING, "Interrupted while waiting for inventory persistence chains", failure);
                if (failure instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                break;
            }
        }

        if (!inventorySaveChains.isEmpty()) {
            logger.log(
                    Level.SEVERE,
                    "Timed out with {0} acknowledgement-aware inventory save chain(s) still pending.",
                    inventorySaveChains.size());
        }
    }

    void scheduleDelayedBlockDataUpdate(SlimefunBlockData blockData, String key) {
        requireNoPendingUniversalMigration(blockData.getKey());
        var scopeKey = new LocationKey(DataScope.NONE, blockData.getLocation());
        var reqKey = new RecordKey(DataScope.BLOCK_DATA);
        reqKey.addCondition(FieldKey.LOCATION, blockData.getKey());
        reqKey.addCondition(FieldKey.DATA_KEY, key);
        if (enableDelayedSaving) {
            scheduleDelayedUpdateTask(
                    new LinkedKey(scopeKey, reqKey),
                    () -> scheduleBlockDataUpdate(scopeKey, reqKey, blockData.getKey(), key, blockData.getData(key)));
        } else {
            scheduleBlockDataUpdate(scopeKey, reqKey, blockData.getKey(), key, blockData.getData(key));
        }
    }

    void scheduleDelayedUniversalDataUpdate(SlimefunUniversalData universalData, String key) {
        var scopeKey = new UUIDKey(DataScope.NONE, universalData.getKey());
        var reqKey = new RecordKey(DataScope.UNIVERSAL_DATA);
        reqKey.addCondition(FieldKey.UNIVERSAL_UUID, universalData.getKey());
        reqKey.addCondition(FieldKey.DATA_KEY, key);

        if (enableDelayedSaving) {
            scheduleDelayedUpdateTask(
                    new LinkedKey(scopeKey, reqKey),
                    () -> scheduleUniversalDataUpdate(
                            scopeKey, reqKey, universalData.getKey(), key, universalData.getData(key)));
        } else {
            scheduleUniversalDataUpdate(scopeKey, reqKey, universalData.getKey(), key, universalData.getData(key));
        }
    }

    private void removeDelayedDataUpdates(ScopeKey scopeKey) {
        synchronized (delayedWriteTasks) {
            delayedWriteTasks
                    .entrySet()
                    .removeIf(each -> scopeKey.equals(each.getKey().getParent()));
        }
    }

    private void scheduleBlockDataUpdate(ScopeKey scopeKey, RecordKey reqKey, String lKey, String key, String val) {
        requireNoPendingUniversalMigration(lKey);
        if (val == null) {
            scheduleDeleteTask(scopeKey, reqKey, false);
        } else {
            var data = new RecordSet();
            reqKey.addField(FieldKey.DATA_VALUE);
            data.put(FieldKey.LOCATION, lKey);
            data.put(FieldKey.DATA_KEY, key);
            data.put(FieldKey.DATA_VALUE, DataUtils.blockDataBase64(val));
            scheduleWriteTask(scopeKey, reqKey, data, true);
        }
    }

    private void scheduleUniversalDataUpdate(ScopeKey scopeKey, RecordKey reqKey, String uuid, String key, String val) {
        if (val == null) {
            scheduleDeleteTask(scopeKey, reqKey, false);
        } else {
            var data = new RecordSet();
            reqKey.addField(FieldKey.DATA_VALUE);
            data.put(FieldKey.UNIVERSAL_UUID, uuid);
            data.put(FieldKey.DATA_KEY, key);
            data.put(FieldKey.DATA_VALUE, DataUtils.blockDataBase64(val));
            scheduleWriteTask(scopeKey, reqKey, data, true);
        }
    }

    void scheduleDelayedChunkDataUpdate(SlimefunChunkData chunkData, String key) {
        var scopeKey = new ChunkKey(DataScope.NONE, chunkData.getKey());
        var reqKey = new RecordKey(DataScope.CHUNK_DATA);
        reqKey.addCondition(FieldKey.CHUNK, chunkData.getKey());
        reqKey.addCondition(FieldKey.DATA_KEY, key);

        if (enableDelayedSaving) {
            scheduleDelayedUpdateTask(
                    new LinkedKey(scopeKey, reqKey),
                    () -> scheduleChunkDataUpdate(scopeKey, reqKey, chunkData.getKey(), key, chunkData.getData(key)));
        } else {
            scheduleChunkDataUpdate(scopeKey, reqKey, chunkData.getKey(), key, chunkData.getData(key));
        }
    }

    private void scheduleDelayedUpdateTask(LinkedKey key, Runnable run) {
        synchronized (delayedWriteTasks) {
            var task = delayedWriteTasks.get(key);

            if (task != null && !task.isExecuted()) {
                task.setRunAfter(delayedSecond, TimeUnit.SECONDS);
                return;
            }

            task = new DelayedTask(delayedSecond, TimeUnit.SECONDS, run);
            delayedWriteTasks.put(key, task);
        }
    }

    private void scheduleChunkDataUpdate(ScopeKey scopeKey, RecordKey reqKey, String cKey, String key, String val) {
        if (val == null) {
            scheduleDeleteTask(scopeKey, reqKey, false);
        } else {
            var data = new RecordSet();
            reqKey.addField(FieldKey.DATA_VALUE);
            data.put(FieldKey.CHUNK, cKey);
            data.put(FieldKey.DATA_KEY, key);
            data.put(FieldKey.DATA_VALUE, DataUtils.blockDataBase64(val));
            scheduleWriteTask(scopeKey, reqKey, data, false);
        }
    }

    private void executeAllDelayedTasks() {
        synchronized (delayedWriteTasks) {
            delayedWriteTasks.values().forEach(DelayedTask::runUnsafely);
        }
    }

    public SlimefunChunkData getChunkDataFromCache(Location chunk) {
        return getChunkDataCache(chunk, false);
    }

    public SlimefunChunkData getChunkDataFromCache(Chunk chunk) {
        return getChunkDataCache(chunk, false);
    }

    private SlimefunChunkData getChunkDataCache(Chunk chunk, boolean createOnNotExists) {
        return createOnNotExists
                ? loadedChunk.computeIfAbsent(LocationUtils.getChunkKey(chunk), k -> {
                    var re = new SlimefunChunkData(chunk);
                    if (chunkDataLoadMode.readCacheOnly()) {
                        re.setIsDataLoaded(true);
                    }
                    return re;
                })
                : loadedChunk.get(LocationUtils.getChunkKey(chunk));
    }

    // Fixed #935: use cache chunk data to generate chunkKey by location first.
    private SlimefunChunkData getChunkDataCache(Location loc, boolean createOnNotExists) {
        var re = loadedChunk.get(LocationUtils.getChunkKey(loc));
        if (re != null) {
            return re;
        } else {
            // If cache not exists, use `getChunkDataCache` and trigger chunk loading
            return getChunkDataCache(loc.getChunk(), createOnNotExists);
        }
    }

    private void deleteChunkAndBlockDataDirectly(String cKey) {
        var req = new RecordKey(DataScope.BLOCK_RECORD);
        req.addCondition(FieldKey.CHUNK, cKey);
        deleteData(req);

        req = new RecordKey(DataScope.CHUNK_DATA);
        req.addCondition(FieldKey.CHUNK, cKey);
        deleteData(req);
    }

    private void clearBlockCacheAndTasks(SlimefunBlockData blockData) {
        var l = blockData.getLocation();
        if (blockData.isDataLoaded() && Slimefun.getRegistry().getTickerBlocks().contains(blockData.getSfId())) {
            Slimefun.getTickerTask().disableTicker(l);
        }
        Slimefun.getNetworkManager().updateAllNetworks(l);

        var scopeKey = new LocationKey(DataScope.NONE, l);
        removeDelayedDataUpdates(scopeKey);
        abortScopeTask(scopeKey);
    }

    /**
     * 迁移旧 Slimefun 机器数据至通用数据
     */
    private void migrateUniversalData(
            @Nonnull Location l,
            @Nonnull String sfId,
            @Nonnull List<RecordSet> kvData,
            @Nonnull List<RecordSet> invData) {
        String source = LocationUtils.getLocKey(l);
        PendingUniversalMigration pending = pendingUniversalMigrations.get(source);
        try {
            if (pending == null) {
                var inv = StoredInventoryReader.read(invData, 54, "migration " + source);
                var preset = UniversalMenuPreset.getPreset(sfId);
                if (preset == null && StoredInventoryReader.hasItems(inv)) {
                    throw StoredInventoryReader.refused(sfId, "migration inventory preset is unavailable", null);
                }
                Map<String, String> sourceData = new HashMap<>();
                for (RecordSet record : kvData) {
                    String key = java.util.Objects.requireNonNull(record.getString(FieldKey.DATA_KEY));
                    String encoded = java.util.Objects.requireNonNull(record.getString(FieldKey.DATA_VALUE));
                    java.util.Objects.requireNonNull(DataUtils.blockDataDebase64(encoded));
                    if (sourceData.putIfAbsent(key, encoded) != null) {
                        throw new IllegalStateException("Migration source contains duplicated custom-data keys");
                    }
                }
                Map<Integer, byte[]> items = new HashMap<>();
                for (RecordSet row : invData) {
                    Object raw = row.getValue(FieldKey.INVENTORY_ITEM);
                    items.put(
                            row.getInt(FieldKey.INVENTORY_SLOT),
                            raw instanceof byte[] bytes
                                    ? bytes.clone()
                                    : raw == null
                                            ? null
                                            : ((String) raw).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                }
                requireNoIncompleteUniversalAt(l);
                String storedLocation = sourceData.get(UniversalDataTrait.BLOCK.getReservedKey());
                if (storedLocation != null
                        && !source.equals(InventoryRecoveryLocations.canonicalLocationKey(
                                DataUtils.blockDataDebase64(storedLocation)))) {
                    throw StoredInventoryReader.refused(
                            source, "stored universal location conflicts with source", null);
                }
                // Preserve a valid historical location representation byte-for-byte; only add
                // the reserved location value when the source does not already contain it.
                var plan = new BlockStorageMigration(
                        source,
                        LocationUtils.getChunkKey(l),
                        sfId,
                        UUID.randomUUID(),
                        storedLocation == null ? DataUtils.blockDataBase64(source) : storedLocation,
                        sourceData,
                        items);
                pending = new PendingUniversalMigration(plan);
                pendingUniversalMigrations.put(source, pending);
            }
            var plan = pending.plan;
            if (!sfId.equals(plan.slimefunId())
                    || inventoryRecoveryLocations.containsOtherOwner(
                            source, plan.destination().toString())) {
                throw StoredInventoryReader.refused(source, "migration identity or recovery ownership changed", null);
            }
            incompleteInventoryLoads.add(source);
            if (!pending.activated) {
                incompleteInventoryLoads.add(plan.destination().toString());
                inventoryRecoveryLocations.remember(plan.destination().toString(), source);
            }
            if (!pending.committed) {
                // Queue completion is checked; merely scheduling the destination is not durable storage.
                persistUniversalMigration(plan).join();
                pending.committed = true;
            }

            // Neither a menu nor ticker is published until the database atomically contains the
            // complete destination and no source. The ordinary strict reader installs that state.
            if (!pending.activated) {
                activateCommittedUniversalMigration(plan, l);
                pending.activated = true;
            }

            var chunk = getChunkDataCache(l, false);
            if (chunk != null) {
                var removed = chunk.removeBlockDataCacheInternal(source);
                if (removed != null) {
                    removed.setPendingRemove(true);
                    removed.setIsDataLoaded(false);
                    var menu = removed.getBlockMenu();
                    if (menu != null) menu.lock();
                }
            }
            invSnapshots.remove(source);
            uncertainInventoryBaselines.remove(source);
            // This is cache retirement only. The SQL transaction already removed the source;
            // another queued delete could otherwise erase a later replacement at this position.
            pendingUniversalMigrations.remove(source, pending);
            Location anchor = l.clone();
            try {
                Slimefun.runSyncAt(anchor, () -> {
                    try {
                        if (Slimefun.getBlockDataService()
                                .isTileEntity(anchor.getBlock().getType())) {
                            Slimefun.getBlockDataService()
                                    .updateUniversalDataUUID(
                                            anchor.getBlock(),
                                            plan.destination().toString());
                        }
                    } catch (RuntimeException | LinkageError metadataFailure) {
                        logger.log(
                                Level.SEVERE,
                                "Committed universal data was retained, but block UUID refresh failed at " + source,
                                metadataFailure);
                    }
                });
            } catch (RuntimeException | LinkageError schedulingFailure) {
                // A world/scheduler failure after commit must not be reported as an undone database move.
                logger.log(
                        Level.SEVERE,
                        "Committed universal data was retained, but block UUID refresh could not be scheduled at "
                                + source,
                        schedulingFailure);
            }
        } catch (Exception | LinkageError failure) {
            Throwable cause =
                    failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
            // Only a confirmed pre-commit rollback permits taking a fresh snapshot/new identity.
            // An ambiguous commit or post-commit activation error keeps the original plan for retry.
            if (pending != null
                    && !pending.committed
                    && cause instanceof BlockStorageMigration.Failure storageFailure
                    && storageFailure.isRestagingSafe()
                    && pendingUniversalMigrations.remove(source, pending)) {
                incompleteInventoryLoads.remove(pending.plan.destination().toString());
                inventoryRecoveryLocations.clear(pending.plan.destination().toString());
            }
            throw StoredInventoryReader.refused("migration " + source, "migration did not complete", failure);
        }
    }

    private void activateCommittedUniversalMigration(BlockStorageMigration migration, Location location) {
        var key = new RecordKey(DataScope.UNIVERSAL_DATA);
        key.addCondition(FieldKey.UNIVERSAL_UUID, migration.destination().toString());
        key.addField(FieldKey.DATA_KEY);
        key.addField(FieldKey.DATA_VALUE);
        lock.lock(key);
        try {
            var existing = loadedUniversalData.get(migration.destination());
            if (existing != null && existing.isDataLoaded()) {
                // The ordinary record loader may already have activated this committed UUID.
                // Retain that live menu (including unsaved changes), not a second copy of it.
                if (!(existing instanceof SlimefunUniversalBlockData block)
                        || !migration.slimefunId().equals(existing.getSfId())
                        || !migration.location().equals(block.getKnownLocationKey())) {
                    throw StoredInventoryReader.refused(
                            migration.location(), "committed destination cache conflicts", null);
                }
                incompleteInventoryLoads.remove(existing.getKey());
                inventoryRecoveryLocations.clear(existing.getKey());
                return;
            }
            var destination = new SlimefunUniversalBlockData(migration.destination(), migration.slimefunId(), location);
            destination.initTraits();
            loadUniversalData(destination);
        } finally {
            lock.unlock(key);
        }
    }

    /** Tracks the transaction on the source's existing writer scope before activating a destination. */
    protected CompletableFuture<Void> persistUniversalMigration(BlockStorageMigration migration) {
        var scope = new LocationKey(DataScope.NONE, migration.location());
        synchronized (delayedWriteTasks) {
            if (delayedWriteTasks.keySet().stream().anyMatch(key -> scope.equals(key.getParent()))) {
                return CompletableFuture.failedFuture(new BlockStorageMigration.Failure(
                        "Migration source still has delayed custom-data writes; retry after they drain", null, true));
            }
        }
        var inventorySave = inventorySaveChains.get("block:" + migration.location());
        if (inventorySave != null && !inventorySave.isDone()) {
            return CompletableFuture.failedFuture(new BlockStorageMigration.Failure(
                    "Migration source still has an inventory save in progress", null, true));
        }
        // Coordination-only key: never passed to an SQL adapter. Ordinary BLOCK_RECORD writes
        // must not compact away this transaction while sharing the same source queue.
        var record = new RecordKey(DataScope.NONE);
        record.addCondition(FieldKey.LOCATION, migration.location());
        record.addCondition(FieldKey.UNIVERSAL_UUID, migration.destination().toString());
        var confirmed = new java.util.concurrent.atomic.AtomicBoolean();
        return scheduleWriteTaskWithCompletion(
                        scope,
                        record,
                        () -> {
                            migrateBlockToUniversal(migration);
                            confirmed.set(true);
                        },
                        true)
                .thenRun(() -> {
                    if (!confirmed.get()) {
                        throw new BlockStorageMigration.Failure(
                                "Migration queue drained without confirming the exact transaction", null, false);
                    }
                });
    }

    private void requireNoPendingUniversalMigration(String source) {
        if (pendingUniversalMigrations.containsKey(source)) {
            throw StoredInventoryReader.refused(source, "universal migration has not completed", null);
        }
    }

    private static final class PendingUniversalMigration {
        private final BlockStorageMigration plan;
        private boolean committed;
        private boolean activated;

        private PendingUniversalMigration(BlockStorageMigration plan) {
            this.plan = plan;
        }
    }
}
