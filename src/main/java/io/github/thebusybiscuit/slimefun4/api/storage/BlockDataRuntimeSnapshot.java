package io.github.thebusybiscuit.slimefun4.api.storage;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Immutable diagnostic snapshot of loaded Slimefun block-data state. */
@SlimefunAPI
public final class BlockDataRuntimeSnapshot {

    private final boolean storageReady;
    private final int loadedChunkRecords;
    private final int loadedBlockRecords;
    private final int unknownSlimefunIds;
    private final int readyLifecycleChunks;
    private final int unsafeLifecycleChunks;
    private final int untrackedLifecycleChunks;
    private final long chunkLoadAttempts;
    private final long deferredChunkLoads;
    private final long chunkLoadFailures;
    private final long lastFailureAtMillis;
    private final String lastFailureMessage;

    public BlockDataRuntimeSnapshot(
            boolean storageReady,
            int loadedChunkRecords,
            int loadedBlockRecords,
            int unknownSlimefunIds,
            int readyLifecycleChunks,
            int unsafeLifecycleChunks,
            int untrackedLifecycleChunks,
            long chunkLoadAttempts,
            long deferredChunkLoads,
            long chunkLoadFailures,
            long lastFailureAtMillis,
            @Nullable String lastFailureMessage) {
        this.storageReady = storageReady;
        this.loadedChunkRecords = loadedChunkRecords;
        this.loadedBlockRecords = loadedBlockRecords;
        this.unknownSlimefunIds = unknownSlimefunIds;
        this.readyLifecycleChunks = readyLifecycleChunks;
        this.unsafeLifecycleChunks = unsafeLifecycleChunks;
        this.untrackedLifecycleChunks = untrackedLifecycleChunks;
        this.chunkLoadAttempts = chunkLoadAttempts;
        this.deferredChunkLoads = deferredChunkLoads;
        this.chunkLoadFailures = chunkLoadFailures;
        this.lastFailureAtMillis = lastFailureAtMillis;
        this.lastFailureMessage = lastFailureMessage;
    }

    public boolean isStorageReady() {
        return storageReady;
    }

    public int getLoadedChunkRecords() {
        return loadedChunkRecords;
    }

    public int getLoadedBlockRecords() {
        return loadedBlockRecords;
    }

    public int getUnknownSlimefunIds() {
        return unknownSlimefunIds;
    }

    public int getReadyLifecycleChunks() {
        return readyLifecycleChunks;
    }

    public int getUnsafeLifecycleChunks() {
        return unsafeLifecycleChunks;
    }

    public int getUntrackedLifecycleChunks() {
        return untrackedLifecycleChunks;
    }

    public long getChunkLoadAttempts() {
        return chunkLoadAttempts;
    }

    public long getDeferredChunkLoads() {
        return deferredChunkLoads;
    }

    public long getChunkLoadFailures() {
        return chunkLoadFailures;
    }

    public long getLastFailureAtMillis() {
        return lastFailureAtMillis;
    }

    public @Nullable String getLastFailureMessage() {
        return lastFailureMessage;
    }

    public @Nonnull String getLifecycleSummary() {
        return readyLifecycleChunks + " ready / " + unsafeLifecycleChunks + " unsafe / " + untrackedLifecycleChunks
                + " untracked";
    }
}
