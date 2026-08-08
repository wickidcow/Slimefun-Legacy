package io.github.thebusybiscuit.slimefun4.core.services.runtime;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunInternal;
import io.github.thebusybiscuit.slimefun4.api.runtime.MachineChunkCoordinationService;
import io.github.thebusybiscuit.slimefun4.api.runtime.MachineChunkCoordinationSnapshot;
import io.github.thebusybiscuit.slimefun4.api.world.ChunkRuntimeState;
import io.github.thebusybiscuit.slimefun4.api.world.WorldChunkRuntimeService;
import io.github.thebusybiscuit.slimefun4.implementation.tasks.TickerTask;
import java.util.Objects;
import javax.annotation.Nonnull;

/** Observational machine/chunk correlation service. It never alters {@link TickerTask}. */
@SlimefunInternal
public final class DefaultMachineChunkCoordinationService implements MachineChunkCoordinationService {

    private final TickerTask ticker;
    private final WorldChunkRuntimeService worldChunks;

    public DefaultMachineChunkCoordinationService(
            @Nonnull TickerTask ticker, @Nonnull WorldChunkRuntimeService worldChunks) {
        this.ticker = Objects.requireNonNull(ticker, "ticker");
        this.worldChunks = Objects.requireNonNull(worldChunks, "worldChunks");
    }

    @Override
    public @Nonnull MachineChunkCoordinationSnapshot getSnapshot() {
        int total = 0;
        int ready = 0;
        int unsafe = 0;
        int untracked = 0;

        var locationsByChunk = ticker.getTickLocations();
        for (var locations : locationsByChunk.values()) {
            for (var tickLocation : locations) {
                total++;
                ChunkRuntimeState state = worldChunks.getChunkState(tickLocation.getLocation());
                switch (state) {
                    case READY -> ready++;
                    case LOADING, UNLOADING, FAILED -> unsafe++;
                    case UNTRACKED -> untracked++;
                }
            }
        }

        return new MachineChunkCoordinationSnapshot(locationsByChunk.size(), total, ready, unsafe, untracked);
    }
}
