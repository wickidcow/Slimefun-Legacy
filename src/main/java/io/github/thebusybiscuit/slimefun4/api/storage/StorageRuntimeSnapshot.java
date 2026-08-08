package io.github.thebusybiscuit.slimefun4.api.storage;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import javax.annotation.Nonnull;

/** Immutable read-only snapshot of Slimefun's storage runtime. */
@SlimefunAPI
public final class StorageRuntimeSnapshot {

    private final boolean ready;
    private final boolean previousShutdownClean;
    private final int pendingWrites;
    private final String blockStorageType;
    private final String profileStorageType;
    private final int loadedChunks;
    private final int loadedUniversalData;

    public StorageRuntimeSnapshot(
            boolean ready,
            boolean previousShutdownClean,
            int pendingWrites,
            @Nonnull String blockStorageType,
            @Nonnull String profileStorageType,
            int loadedChunks,
            int loadedUniversalData) {
        this.ready = ready;
        this.previousShutdownClean = previousShutdownClean;
        this.pendingWrites = pendingWrites;
        this.blockStorageType = blockStorageType;
        this.profileStorageType = profileStorageType;
        this.loadedChunks = loadedChunks;
        this.loadedUniversalData = loadedUniversalData;
    }

    public boolean isReady() {
        return ready;
    }

    public boolean wasPreviousShutdownClean() {
        return previousShutdownClean;
    }

    public int getPendingWrites() {
        return pendingWrites;
    }

    public @Nonnull String getBlockStorageType() {
        return blockStorageType;
    }

    public @Nonnull String getProfileStorageType() {
        return profileStorageType;
    }

    public int getLoadedChunks() {
        return loadedChunks;
    }

    public int getLoadedUniversalData() {
        return loadedUniversalData;
    }
}
