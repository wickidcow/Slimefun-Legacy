package io.github.thebusybiscuit.slimefun4.api.runtime;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;

/** Immutable read-only view of machine ticker registrations grouped by observed chunk lifecycle state. */
@SlimefunAPI
public final class MachineChunkCoordinationSnapshot {

    private final int tickerChunks;
    private final int tickerLocations;
    private final int readyLocations;
    private final int unsafeLocations;
    private final int untrackedLocations;

    public MachineChunkCoordinationSnapshot(
            int tickerChunks, int tickerLocations, int readyLocations, int unsafeLocations, int untrackedLocations) {
        this.tickerChunks = tickerChunks;
        this.tickerLocations = tickerLocations;
        this.readyLocations = readyLocations;
        this.unsafeLocations = unsafeLocations;
        this.untrackedLocations = untrackedLocations;
    }

    public int getTickerChunks() {
        return tickerChunks;
    }

    public int getTickerLocations() {
        return tickerLocations;
    }

    public int getReadyLocations() {
        return readyLocations;
    }

    public int getUnsafeLocations() {
        return unsafeLocations;
    }

    public int getUntrackedLocations() {
        return untrackedLocations;
    }
}
