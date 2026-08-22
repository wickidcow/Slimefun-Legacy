package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import com.xzavier0722.mc.plugin.slimefun4.storage.task.DelayedTask;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.InvSnapshot;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.LocationUtils;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Releases normal Slimefun block-storage caches after a world chunk unloads.
 *
 * <p>Eviction is intentionally conservative. Dirty inventories and delayed writes are flushed first, current reads and
 * matching writes are allowed to drain without blocking the owning region, and the final cache removal runs only while
 * new storage reads/writes are gated. Any uncertainty leaves the cache intact.
 */
public final class ChunkCacheEvictionService {
    private static final int MAX_FINAL_RETRIES = 40;
    private static final Set<String> pendingEvictions = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean accessWarningLogged = new AtomicBoolean();

    private static final Field loadedChunkField = findControllerField("loadedChunk");
    private static final Field delayedWriteTasksField = findControllerField("delayedWriteTasks");
    private static final Field invSnapshotsField = findControllerField("invSnapshots");

    private ChunkCacheEvictionService() {}

    /**
     * Starts a non-blocking, fail-closed eviction attempt for the supplied chunk.
     *
     * @param controller active block-data controller
     * @param chunk chunk that is unloading
     */
    public static void requestEviction(BlockDataController controller, Chunk chunk) {
        if (controller == null || chunk == null || controller.isShuttingDown() || controller.isDestroyed()) {
            return;
        }

        var loadedChunks = loadedChunks(controller);
        if (loadedChunks == null) {
            warnAccessUnavailable();
            return;
        }

        String chunkKey = LocationUtils.getChunkKey(chunk);
        SlimefunChunkData expectedCache = loadedChunks.get(chunkKey);
        if (expectedCache == null || !pendingEvictions.add(chunkKey)) {
            return;
        }

        try {
            var worldRef = new WeakReference<>(chunk.getWorld());
            int chunkX = chunk.getX();
            int chunkZ = chunk.getZ();

            if (!prepareCurrentCache(controller, expectedCache, chunkKey)) {
                finish(chunkKey);
                return;
            }

            awaitCurrentIo(controller, worldRef, chunkX, chunkZ, chunkKey, expectedCache, 0);
        } catch (RuntimeException | LinkageError failure) {
            Slimefun.logger()
                    .log(Level.WARNING, "Keeping Slimefun chunk cache after unload preparation failed: " + chunkKey, failure);
            finish(chunkKey);
        }
    }

    static boolean canAccessControllerState(BlockDataController controller) {
        return loadedChunks(controller) != null && delayedWrites(controller) != null && snapshots(controller) != null;
    }

    private static boolean prepareCurrentCache(
            BlockDataController controller, SlimefunChunkData expectedCache, String chunkKey) {
        try {
            saveDirtyInventories(controller, expectedCache);
            return flushDelayedWrites(controller, chunkKey);
        } catch (RuntimeException | LinkageError failure) {
            Slimefun.logger()
                    .log(Level.WARNING, "Keeping Slimefun chunk cache after save preparation failed: " + chunkKey, failure);
            return false;
        }
    }

    private static void saveDirtyInventories(BlockDataController controller, SlimefunChunkData cache) {
        for (SlimefunBlockData blockData : cache.getAllCacheInternal()) {
            if (blockData.isPendingRemove() || !blockData.isDataLoaded()) {
                continue;
            }

            var menu = blockData.getBlockMenu();
            if (menu != null && menu.isDirty()) {
                controller.saveBlockInventory(blockData);
            }
        }
    }

    private static boolean flushDelayedWrites(BlockDataController controller, String chunkKey) {
        var delayedWrites = delayedWrites(controller);
        if (delayedWrites == null) {
            warnAccessUnavailable();
            return false;
        }

        synchronized (delayedWrites) {
            for (var entry : new ArrayList<>(delayedWrites.entrySet())) {
                LinkedKey linkedKey = entry.getKey();
                if (!ChunkScopeMatcher.matches(linkedKey.getParent(), chunkKey)) {
                    continue;
                }

                DelayedTask task = entry.getValue();
                if (!task.runNow()) {
                    return false;
                }
                delayedWrites.remove(linkedKey, task);
            }
        }
        return true;
    }

    private static void awaitCurrentIo(
            BlockDataController controller,
            WeakReference<World> worldRef,
            int chunkX,
            int chunkZ,
            String chunkKey,
            SlimefunChunkData expectedCache,
            int retry) {
        final CompletableFuture<Void> reads;
        final CompletableFuture<Void> writes;
        try {
            reads = controller.getCurrentReadCompletion();
            writes = controller.getCurrentWriteCompletion(scope -> ChunkScopeMatcher.matches(scope, chunkKey));
        } catch (RuntimeException | LinkageError failure) {
            finish(chunkKey);
            return;
        }

        CompletableFuture.allOf(reads, writes).whenComplete((ignored, failure) -> {
            if (failure != null) {
                Slimefun.logger()
                        .log(
                                Level.WARNING,
                                "Keeping Slimefun chunk cache after database I/O failed: " + chunkKey,
                                unwrap(failure));
                finish(chunkKey);
                return;
            }
            scheduleFinalCheck(controller, worldRef, chunkX, chunkZ, chunkKey, expectedCache, retry);
        });
    }

    private static void scheduleFinalCheck(
            BlockDataController controller,
            WeakReference<World> worldRef,
            int chunkX,
            int chunkZ,
            String chunkKey,
            SlimefunChunkData expectedCache,
            int retry) {
        World world = worldRef.get();
        if (world == null || controller.isShuttingDown() || controller.isDestroyed()) {
            finish(chunkKey);
            return;
        }

        try {
            Location anchor = new Location(world, chunkX << 4, 0, chunkZ << 4);
            Slimefun.runSyncAt(
                    anchor,
                    () -> finalCheckOnOwner(
                            controller, worldRef, chunkX, chunkZ, chunkKey, expectedCache, retry),
                    1L);
        } catch (RuntimeException | LinkageError failure) {
            Slimefun.logger()
                    .log(Level.WARNING, "Keeping Slimefun chunk cache after scheduler handoff failed: " + chunkKey, failure);
            finish(chunkKey);
        }
    }

    private static void finalCheckOnOwner(
            BlockDataController controller,
            WeakReference<World> worldRef,
            int chunkX,
            int chunkZ,
            String chunkKey,
            SlimefunChunkData expectedCache,
            int retry) {
        try {
            doFinalCheckOnOwner(controller, worldRef, chunkX, chunkZ, chunkKey, expectedCache, retry);
        } catch (RuntimeException | LinkageError failure) {
            Slimefun.logger()
                    .log(Level.WARNING, "Keeping Slimefun chunk cache after final eviction check failed: " + chunkKey, failure);
            finish(chunkKey);
        }
    }

    private static void doFinalCheckOnOwner(
            BlockDataController controller,
            WeakReference<World> worldRef,
            int chunkX,
            int chunkZ,
            String chunkKey,
            SlimefunChunkData expectedCache,
            int retry) {
        World world = worldRef.get();
        var loadedChunks = loadedChunks(controller);
        if (world == null
                || loadedChunks == null
                || controller.isShuttingDown()
                || controller.isDestroyed()
                || world.isChunkLoaded(chunkX, chunkZ)
                || loadedChunks.get(chunkKey) != expectedCache) {
            finish(chunkKey);
            return;
        }

        if (!prepareCurrentCache(controller, expectedCache, chunkKey)) {
            finish(chunkKey);
            return;
        }

        CompletableFuture<Void> reads = controller.getCurrentReadCompletion();
        CompletableFuture<Void> writes =
                controller.getCurrentWriteCompletion(scope -> ChunkScopeMatcher.matches(scope, chunkKey));
        if (!reads.isDone() || !writes.isDone()) {
            awaitCurrentIo(controller, worldRef, chunkX, chunkZ, chunkKey, expectedCache, retry);
            return;
        }

        try {
            reads.join();
            writes.join();
        } catch (RuntimeException failure) {
            Slimefun.logger()
                    .log(
                            Level.WARNING,
                            "Keeping Slimefun chunk cache after database I/O failed: " + chunkKey,
                            unwrap(failure));
            finish(chunkKey);
            return;
        }

        AtomicBoolean evicted = new AtomicBoolean();
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicBoolean retryNeeded = new AtomicBoolean();
        var delayedWrites = delayedWrites(controller);
        if (delayedWrites == null) {
            warnAccessUnavailable();
            finish(chunkKey);
            return;
        }

        synchronized (delayedWrites) {
            if (hasMatchingDelayedWrite(delayedWrites, chunkKey)) {
                retryNeeded.set(true);
            } else {
                boolean writesIdle = controller.runIfWriteScopesIdle(
                        scope -> ChunkScopeMatcher.matches(scope, chunkKey),
                        () -> {
                            boolean readsIdle = controller.runIfReadExecutorIdle(() -> {
                                if (world.isChunkLoaded(chunkX, chunkZ) || loadedChunks.get(chunkKey) != expectedCache) {
                                    cancelled.set(true);
                                    return;
                                }
                                if (hasMatchingDelayedWrite(delayedWrites, chunkKey)) {
                                    retryNeeded.set(true);
                                    return;
                                }
                                evicted.set(releaseCache(controller, loadedChunks, expectedCache, chunkKey));
                            });
                            if (!readsIdle) {
                                retryNeeded.set(true);
                            }
                        });
                if (!writesIdle) {
                    retryNeeded.set(true);
                }
            }
        }

        if (evicted.get() || cancelled.get()) {
            finish(chunkKey);
        } else if (retryNeeded.get() && retry < MAX_FINAL_RETRIES) {
            scheduleFinalCheck(controller, worldRef, chunkX, chunkZ, chunkKey, expectedCache, retry + 1);
        } else {
            finish(chunkKey);
        }
    }

    private static boolean releaseCache(
            BlockDataController controller,
            Map<String, SlimefunChunkData> loadedChunks,
            SlimefunChunkData expectedCache,
            String chunkKey) {
        var snapshots = snapshots(controller);
        if (snapshots == null) {
            warnAccessUnavailable();
            return false;
        }

        for (SlimefunBlockData blockData : expectedCache.getAllCacheInternal()) {
            if (blockData.isDataLoaded()
                    && Slimefun.getRegistry().getTickerBlocks().contains(blockData.getSfId())) {
                try {
                    Slimefun.getTickerTask().disableTicker(blockData.getLocation());
                } catch (RuntimeException | LinkageError failure) {
                    Slimefun.logger()
                            .log(
                                    Level.WARNING,
                                    "Keeping Slimefun chunk cache because a ticker could not be detached: " + chunkKey,
                                    failure);
                    return false;
                }
            }
        }

        for (SlimefunBlockData blockData : expectedCache.getAllCacheInternal()) {
            snapshots.remove(blockData.getKey());
        }
        return loadedChunks.remove(chunkKey, expectedCache);
    }

    private static boolean hasMatchingDelayedWrite(Map<LinkedKey, DelayedTask> tasks, String chunkKey) {
        for (LinkedKey key : tasks.keySet()) {
            if (ChunkScopeMatcher.matches(key.getParent(), chunkKey)) {
                return true;
            }
        }
        return false;
    }

    private static Throwable unwrap(Throwable failure) {
        return failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
    }

    private static void finish(String chunkKey) {
        pendingEvictions.remove(chunkKey);
    }

    private static Field findControllerField(String name) {
        try {
            Field field = BlockDataController.class.getDeclaredField(name);
            return field.trySetAccessible() ? field : null;
        } catch (NoSuchFieldException | SecurityException failure) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, SlimefunChunkData> loadedChunks(BlockDataController controller) {
        return (Map<String, SlimefunChunkData>) readMap(loadedChunkField, controller);
    }

    @SuppressWarnings("unchecked")
    private static Map<LinkedKey, DelayedTask> delayedWrites(BlockDataController controller) {
        return (Map<LinkedKey, DelayedTask>) readMap(delayedWriteTasksField, controller);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, InvSnapshot> snapshots(BlockDataController controller) {
        return (Map<String, InvSnapshot>) readMap(invSnapshotsField, controller);
    }

    private static Map<?, ?> readMap(Field field, BlockDataController controller) {
        if (field == null) {
            return null;
        }
        try {
            return (Map<?, ?>) field.get(controller);
        } catch (IllegalAccessException | ClassCastException failure) {
            return null;
        }
    }

    private static void warnAccessUnavailable() {
        if (accessWarningLogged.compareAndSet(false, true)) {
            Slimefun.logger()
                    .warning(
                            "Chunk cache eviction is disabled because the block-storage controller layout could not be accessed safely.");
        }
    }
}
