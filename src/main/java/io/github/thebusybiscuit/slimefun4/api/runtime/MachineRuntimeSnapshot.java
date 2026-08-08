package io.github.thebusybiscuit.slimefun4.api.runtime;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;

/** Immutable diagnostic snapshot of Slimefun's machine ticker runtime. */
@SlimefunAPI
public final class MachineRuntimeSnapshot {

    private final boolean paused;
    private final boolean halted;
    private final int tickRate;
    private final int tickingChunks;
    private final int tickingLocations;
    private final int pausedMachineCircuits;
    private final int activeMachineFailures;
    private final long observedMachineFailures;
    private final long suppressedMachineReports;

    public MachineRuntimeSnapshot(
            boolean paused,
            boolean halted,
            int tickRate,
            int tickingChunks,
            int tickingLocations,
            int pausedMachineCircuits,
            int activeMachineFailures,
            long observedMachineFailures,
            long suppressedMachineReports) {
        this.paused = paused;
        this.halted = halted;
        this.tickRate = tickRate;
        this.tickingChunks = tickingChunks;
        this.tickingLocations = tickingLocations;
        this.pausedMachineCircuits = pausedMachineCircuits;
        this.activeMachineFailures = activeMachineFailures;
        this.observedMachineFailures = observedMachineFailures;
        this.suppressedMachineReports = suppressedMachineReports;
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean isHalted() {
        return halted;
    }

    public int getTickRate() {
        return tickRate;
    }

    public int getTickingChunks() {
        return tickingChunks;
    }

    public int getTickingLocations() {
        return tickingLocations;
    }

    public int getPausedMachineCircuits() {
        return pausedMachineCircuits;
    }

    public int getActiveMachineFailures() {
        return activeMachineFailures;
    }

    public long getObservedMachineFailures() {
        return observedMachineFailures;
    }

    public long getSuppressedMachineReports() {
        return suppressedMachineReports;
    }
}
