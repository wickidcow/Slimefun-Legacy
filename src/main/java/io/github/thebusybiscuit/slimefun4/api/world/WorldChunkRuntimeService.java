package io.github.thebusybiscuit.slimefun4.api.world;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import javax.annotation.Nonnull;
import org.bukkit.Location;

/**
 * Read-only view of world and chunk lifecycle state observed by Slimefun Legacy.
 *
 * <p>This service never loads, unloads, generates, pins, or otherwise changes chunks.
 */
@SlimefunAPI
public interface WorldChunkRuntimeService {

    @Nonnull
    WorldChunkRuntimeSnapshot getSnapshot();

    @Nonnull
    ChunkRuntimeState getChunkState(@Nonnull Location location);

    @Nonnull
    ChunkRuntimeState getChunkState(@Nonnull String worldName, int chunkX, int chunkZ);

    default boolean isChunkReady(@Nonnull Location location) {
        return getChunkState(location) == ChunkRuntimeState.READY;
    }

    default boolean isChunkReady(@Nonnull String worldName, int chunkX, int chunkZ) {
        return getChunkState(worldName, chunkX, chunkZ) == ChunkRuntimeState.READY;
    }

    boolean isWorldTracked(@Nonnull String worldName);
}
