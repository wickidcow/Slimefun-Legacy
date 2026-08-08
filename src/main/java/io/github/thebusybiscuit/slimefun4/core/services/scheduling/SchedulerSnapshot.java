package io.github.thebusybiscuit.slimefun4.core.services.scheduling;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;

/** Immutable scheduler health snapshot for diagnostics and addon capability checks. */
@SlimefunAPI
public final class SchedulerSnapshot {

    private final boolean acceptingTasks;
    private final int activeTaskCount;
    private final boolean regionOwnedExecution;

    public SchedulerSnapshot(boolean acceptingTasks, int activeTaskCount, boolean regionOwnedExecution) {
        this.acceptingTasks = acceptingTasks;
        this.activeTaskCount = activeTaskCount;
        this.regionOwnedExecution = regionOwnedExecution;
    }

    public boolean isAcceptingTasks() {
        return acceptingTasks;
    }

    /**
     * Returns the number of tasks tracked by the scheduler, or {@code -1} when the implementation does not expose it.
     */
    public int getActiveTaskCount() {
        return activeTaskCount;
    }

    public boolean isRegionOwnedExecution() {
        return regionOwnedExecution;
    }
}
