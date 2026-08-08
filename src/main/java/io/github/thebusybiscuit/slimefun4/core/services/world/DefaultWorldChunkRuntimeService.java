package io.github.thebusybiscuit.slimefun4.core.services.world;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunInternal;
import io.github.thebusybiscuit.slimefun4.api.world.ChunkRuntimeState;
import io.github.thebusybiscuit.slimefun4.api.world.WorldChunkRuntimeService;
import io.github.thebusybiscuit.slimefun4.api.world.WorldChunkRuntimeSnapshot;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import javax.annotation.Nonnull;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

/**
 * Event-backed implementation of Slimefun's read-only world/chunk runtime observer.
 *
 * <p>The observer stores coordinates and names only. It intentionally does not retain Bukkit {@link Chunk} objects,
 * request chunk tickets, or affect unload decisions.
 */
@SlimefunInternal
public final class DefaultWorldChunkRuntimeService implements WorldChunkRuntimeService, Listener {

    private final Set<String> worlds = ConcurrentHashMap.newKeySet();
    private final Map<ChunkKey, ChunkRuntimeState> chunks = new ConcurrentHashMap<>();
    private final LongAdder chunkLoadEvents = new LongAdder();
    private final LongAdder chunkUnloadEvents = new LongAdder();
    private final LongAdder worldLoadEvents = new LongAdder();
    private final LongAdder worldUnloadEvents = new LongAdder();
    private final LongAdder deferredStorageLoads = new LongAdder();
    private final LongAdder storageLoadFailures = new LongAdder();

    @EventHandler(priority = EventPriority.LOWEST)
    public void onWorldLoad(@Nonnull WorldLoadEvent event) {
        worldLoadEvents.increment();
        worlds.add(normalize(event.getWorld().getName()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(@Nonnull WorldUnloadEvent event) {
        worldUnloadEvents.increment();
        String world = normalize(event.getWorld().getName());
        worlds.remove(world);
        chunks.keySet().removeIf(key -> key.worldName.equals(world));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChunkLoad(@Nonnull ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        String world = normalize(chunk.getWorld().getName());
        worlds.add(world);
        chunkLoadEvents.increment();
        chunks.put(new ChunkKey(world, chunk.getX(), chunk.getZ()), ChunkRuntimeState.LOADING);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void afterChunkLoad(@Nonnull ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        ChunkKey key = key(chunk);
        chunks.computeIfPresent(key, (ignored, state) -> state == ChunkRuntimeState.FAILED ? state : ChunkRuntimeState.READY);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChunkUnload(@Nonnull ChunkUnloadEvent event) {
        chunkUnloadEvents.increment();
        chunks.put(key(event.getChunk()), ChunkRuntimeState.UNLOADING);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void afterChunkUnload(@Nonnull ChunkUnloadEvent event) {
        chunks.remove(key(event.getChunk()));
    }

    public void recordDeferredStorageLoad() {
        deferredStorageLoads.increment();
    }

    public void recordStorageLoadFailure(@Nonnull String worldName, int chunkX, int chunkZ) {
        storageLoadFailures.increment();
        String world = normalize(worldName);
        worlds.add(world);
        chunks.put(new ChunkKey(world, chunkX, chunkZ), ChunkRuntimeState.FAILED);
    }

    public void recordStorageLoadSuccess(@Nonnull String worldName, int chunkX, int chunkZ) {
        String world = normalize(worldName);
        worlds.add(world);
        chunks.compute(new ChunkKey(world, chunkX, chunkZ), (ignored, state) -> {
            if (state == ChunkRuntimeState.UNLOADING) {
                return state;
            }
            return ChunkRuntimeState.READY;
        });
    }

    @Override
    public @Nonnull WorldChunkRuntimeSnapshot getSnapshot() {
        int ready = 0;
        int loading = 0;
        int unloading = 0;
        int failed = 0;
        for (ChunkRuntimeState state : chunks.values()) {
            switch (state) {
                case READY -> ready++;
                case LOADING -> loading++;
                case UNLOADING -> unloading++;
                case FAILED -> failed++;
                case UNTRACKED -> {
                    // UNTRACKED is represented by absence from the map.
                }
            }
        }

        return new WorldChunkRuntimeSnapshot(
                worlds.size(),
                chunks.size(),
                ready,
                loading,
                unloading,
                failed,
                chunkLoadEvents.sum(),
                chunkUnloadEvents.sum(),
                worldLoadEvents.sum(),
                worldUnloadEvents.sum(),
                deferredStorageLoads.sum(),
                storageLoadFailures.sum());
    }

    @Override
    public @Nonnull ChunkRuntimeState getChunkState(@Nonnull Location location) {
        World world = location.getWorld();
        if (world == null) {
            return ChunkRuntimeState.UNTRACKED;
        }
        return getChunkState(world.getName(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    @Override
    public @Nonnull ChunkRuntimeState getChunkState(@Nonnull String worldName, int chunkX, int chunkZ) {
        return chunks.getOrDefault(new ChunkKey(normalize(worldName), chunkX, chunkZ), ChunkRuntimeState.UNTRACKED);
    }

    @Override
    public boolean isWorldTracked(@Nonnull String worldName) {
        return worlds.contains(normalize(worldName));
    }

    private static ChunkKey key(Chunk chunk) {
        return new ChunkKey(normalize(chunk.getWorld().getName()), chunk.getX(), chunk.getZ());
    }

    private static String normalize(String worldName) {
        return worldName.toLowerCase(Locale.ROOT);
    }

    private record ChunkKey(String worldName, int x, int z) {}
}
