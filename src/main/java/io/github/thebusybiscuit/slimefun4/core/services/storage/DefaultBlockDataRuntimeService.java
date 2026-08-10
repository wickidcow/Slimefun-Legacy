package io.github.thebusybiscuit.slimefun4.core.services.storage;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.BlockDataController;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.ChunkDataLoadMode;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunChunkData;
import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunInternal;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.storage.BlockDataRuntimeService;
import io.github.thebusybiscuit.slimefun4.api.storage.BlockDataRuntimeSnapshot;
import io.github.thebusybiscuit.slimefun4.api.world.ChunkRuntimeState;
import io.github.thebusybiscuit.slimefun4.api.world.WorldChunkRuntimeService;
import io.github.thebusybiscuit.slimefun4.core.config.SlimefunDatabaseManager;
import io.github.thebusybiscuit.slimefun4.core.services.scheduling.SlimefunScheduler;
import io.github.thebusybiscuit.slimefun4.core.services.scheduling.TaskHandle;
import io.github.thebusybiscuit.slimefun4.core.services.world.DefaultWorldChunkRuntimeService;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;

/**
 * Ownership-aware bridge between Bukkit chunk lifecycle events and Slimefun's existing block-data controller.
 *
 * <p>Normal Paper chunk-load behavior remains synchronous. If a lifecycle callback is ever delivered outside the
 * owning region, the storage load is marshalled to Slimefun's location scheduler instead of touching chunk state from
 * the wrong execution context.
 */
@SlimefunInternal
public final class DefaultBlockDataRuntimeService implements BlockDataRuntimeService, Listener {

    private static final int MAX_FAILURE_MESSAGE = 180;

    private final SlimefunDatabaseManager databaseManager;
    private final SlimefunScheduler scheduler;
    private final WorldChunkRuntimeService worldChunks;
    private final Logger logger;
    private final LongAdder chunkLoadAttempts = new LongAdder();
    private final LongAdder deferredChunkLoads = new LongAdder();
    private final LongAdder chunkLoadFailures = new LongAdder();
    private final AtomicLong lastFailureAt = new AtomicLong();
    private final AtomicReference<String> lastFailureMessage = new AtomicReference<>();

    public DefaultBlockDataRuntimeService(
            @Nonnull SlimefunDatabaseManager databaseManager,
            @Nonnull SlimefunScheduler scheduler,
            @Nonnull WorldChunkRuntimeService worldChunks,
            @Nonnull Logger logger) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.worldChunks = Objects.requireNonNull(worldChunks, "worldChunks");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onChunkLoad(@Nonnull ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        chunkLoadAttempts.increment();
        Location anchor = anchor(chunk);

        if (scheduler.isOwnedByCurrentRegion(anchor)) {
            loadChunk(chunk, event.isNewChunk());
            return;
        }

        deferredChunkLoads.increment();
        recordDeferredStorageLoad();
        TaskHandle handle = scheduler.runAt(anchor, () -> loadChunk(chunk, event.isNewChunk()));
        if (handle.isCancelled()) {
            recordFailure(chunk, new IllegalStateException("Scheduler rejected deferred Slimefun chunk-data load"));
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onWorldLoad(@Nonnull WorldLoadEvent event) {
        if (databaseManager.getChunkDataLoadMode() == ChunkDataLoadMode.LOAD_ON_STARTUP) {
            databaseManager.getBlockDataController().loadWorld(event.getWorld());
        }
    }

    @Override
    public @Nonnull BlockDataRuntimeSnapshot getSnapshot() {
        try {
            BlockDataController controller = databaseManager.getBlockDataController();
            if (controller == null) {
                return emptySnapshot(false);
            }

            Set<SlimefunChunkData> loadedChunks = controller.getAllLoadedChunkData();
            int loadedBlocks = 0;
            int unknownIds = 0;
            int ready = 0;
            int unsafe = 0;
            int untracked = 0;

            for (SlimefunChunkData chunkData : loadedChunks) {
                var blocks = chunkData.getAllBlockData();
                loadedBlocks += blocks.size();
                unknownIds += (int) blocks.stream()
                        .filter(block -> SlimefunItem.getById(block.getSfId()) == null)
                        .count();

                ChunkCoordinates coordinates = parseChunkKey(chunkData.getKey());
                if (coordinates == null) {
                    untracked++;
                    continue;
                }

                ChunkRuntimeState state =
                        worldChunks.getChunkState(coordinates.worldName, coordinates.chunkX, coordinates.chunkZ);
                switch (state) {
                    case READY -> ready++;
                    case LOADING, UNLOADING, FAILED -> unsafe++;
                    case UNTRACKED -> untracked++;
                }
            }

            return new BlockDataRuntimeSnapshot(
                    true,
                    loadedChunks.size(),
                    loadedBlocks,
                    unknownIds,
                    ready,
                    unsafe,
                    untracked,
                    chunkLoadAttempts.sum(),
                    deferredChunkLoads.sum(),
                    chunkLoadFailures.sum(),
                    lastFailureAt.get(),
                    lastFailureMessage.get());
        } catch (RuntimeException | LinkageError failure) {
            rememberFailure(failure);
            return emptySnapshot(false);
        }
    }

    @Override
    public boolean isChunkAccessReady(@Nonnull Location location) {
        Objects.requireNonNull(location, "location");
        ChunkRuntimeState state = worldChunks.getChunkState(location);
        return state == ChunkRuntimeState.READY || state == ChunkRuntimeState.UNTRACKED;
    }

    private void loadChunk(Chunk chunk, boolean newChunk) {
        try {
            databaseManager.getBlockDataController().loadChunk(chunk, newChunk);
            recordStorageLoadSuccess(chunk);
        } catch (RuntimeException | LinkageError failure) {
            recordFailure(chunk, failure);
            logger.log(
                    Level.SEVERE,
                    "Failed to load Slimefun block data for chunk "
                            + chunk.getWorld().getName() + " " + chunk.getX() + "," + chunk.getZ(),
                    failure);
        }
    }

    private void recordDeferredStorageLoad() {
        if (worldChunks instanceof DefaultWorldChunkRuntimeService runtime) {
            runtime.recordDeferredStorageLoad();
        }
    }

    private void recordStorageLoadSuccess(Chunk chunk) {
        if (worldChunks instanceof DefaultWorldChunkRuntimeService runtime) {
            runtime.recordStorageLoadSuccess(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        }
    }

    private void recordFailure(Chunk chunk, Throwable failure) {
        chunkLoadFailures.increment();
        rememberFailure(failure);
        if (worldChunks instanceof DefaultWorldChunkRuntimeService runtime) {
            runtime.recordStorageLoadFailure(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        }
    }

    private void rememberFailure(Throwable failure) {
        lastFailureAt.set(System.currentTimeMillis());
        String message = failure.getClass().getSimpleName();
        if (failure.getMessage() != null && !failure.getMessage().isBlank()) {
            message += ": " + failure.getMessage();
        }
        if (message.length() > MAX_FAILURE_MESSAGE) {
            message = message.substring(0, MAX_FAILURE_MESSAGE - 3) + "...";
        }
        lastFailureMessage.set(message);
    }

    private BlockDataRuntimeSnapshot emptySnapshot(boolean ready) {
        return new BlockDataRuntimeSnapshot(
                ready,
                0,
                0,
                0,
                0,
                0,
                0,
                chunkLoadAttempts.sum(),
                deferredChunkLoads.sum(),
                chunkLoadFailures.sum(),
                lastFailureAt.get(),
                lastFailureMessage.get());
    }

    private static Location anchor(Chunk chunk) {
        return new Location(chunk.getWorld(), chunk.getX() << 4, 0, chunk.getZ() << 4);
    }

    private static ChunkCoordinates parseChunkKey(String key) {
        try {
            int semicolon = key.lastIndexOf(';');
            int colon = key.indexOf(':', semicolon + 1);
            if (semicolon <= 0 || colon <= semicolon + 1) {
                return null;
            }
            String worldName = key.substring(0, semicolon);
            int x = Integer.parseInt(key.substring(semicolon + 1, colon));
            int z = Integer.parseInt(key.substring(colon + 1));
            return new ChunkCoordinates(worldName, x, z);
        } catch (RuntimeException failure) {
            return null;
        }
    }

    private record ChunkCoordinates(String worldName, int chunkX, int chunkZ) {}
}
