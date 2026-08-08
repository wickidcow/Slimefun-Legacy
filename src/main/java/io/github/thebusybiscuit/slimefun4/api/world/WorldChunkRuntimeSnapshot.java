package io.github.thebusybiscuit.slimefun4.api.world;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;

/** Immutable aggregate snapshot of Slimefun's observed world and chunk lifecycle. */
@SlimefunAPI
public final class WorldChunkRuntimeSnapshot {

    private final int trackedWorlds;
    private final int trackedChunks;
    private final int readyChunks;
    private final int loadingChunks;
    private final int unloadingChunks;
    private final int failedChunks;
    private final long chunkLoadEvents;
    private final long chunkUnloadEvents;
    private final long worldLoadEvents;
    private final long worldUnloadEvents;
    private final long deferredStorageLoads;
    private final long storageLoadFailures;

    public WorldChunkRuntimeSnapshot(
            int trackedWorlds,
            int trackedChunks,
            int readyChunks,
            int loadingChunks,
            int unloadingChunks,
            int failedChunks,
            long chunkLoadEvents,
            long chunkUnloadEvents,
            long worldLoadEvents,
            long worldUnloadEvents,
            long deferredStorageLoads,
            long storageLoadFailures) {
        this.trackedWorlds = trackedWorlds;
        this.trackedChunks = trackedChunks;
        this.readyChunks = readyChunks;
        this.loadingChunks = loadingChunks;
        this.unloadingChunks = unloadingChunks;
        this.failedChunks = failedChunks;
        this.chunkLoadEvents = chunkLoadEvents;
        this.chunkUnloadEvents = chunkUnloadEvents;
        this.worldLoadEvents = worldLoadEvents;
        this.worldUnloadEvents = worldUnloadEvents;
        this.deferredStorageLoads = deferredStorageLoads;
        this.storageLoadFailures = storageLoadFailures;
    }

    public int getTrackedWorlds() {
        return trackedWorlds;
    }

    public int getTrackedChunks() {
        return trackedChunks;
    }

    public int getReadyChunks() {
        return readyChunks;
    }

    public int getLoadingChunks() {
        return loadingChunks;
    }

    public int getUnloadingChunks() {
        return unloadingChunks;
    }

    public int getFailedChunks() {
        return failedChunks;
    }

    public int getUnsafeChunks() {
        return loadingChunks + unloadingChunks + failedChunks;
    }

    public long getChunkLoadEvents() {
        return chunkLoadEvents;
    }

    public long getChunkUnloadEvents() {
        return chunkUnloadEvents;
    }

    public long getWorldLoadEvents() {
        return worldLoadEvents;
    }

    public long getWorldUnloadEvents() {
        return worldUnloadEvents;
    }

    public long getDeferredStorageLoads() {
        return deferredStorageLoads;
    }

    public long getStorageLoadFailures() {
        return storageLoadFailures;
    }
}
