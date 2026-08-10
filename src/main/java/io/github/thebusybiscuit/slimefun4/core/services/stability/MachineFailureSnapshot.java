package io.github.thebusybiscuit.slimefun4.core.services.stability;

/** Immutable admin-facing snapshot of one currently failing machine location. */
public final class MachineFailureSnapshot {
    private final String worldName;
    private final int x;
    private final int y;
    private final int z;
    private final String itemId;
    private final String addonName;
    private final String failureType;
    private final String failureMessage;
    private final int consecutiveFailures;
    private final long failuresObserved;
    private final long suppressedReports;
    private final long firstFailureMillis;
    private final long lastFailureMillis;
    private final long pausedUntilMillis;

    MachineFailureSnapshot(
            String worldName,
            int x,
            int y,
            int z,
            String itemId,
            String addonName,
            String failureType,
            String failureMessage,
            int consecutiveFailures,
            long failuresObserved,
            long suppressedReports,
            long firstFailureMillis,
            long lastFailureMillis,
            long pausedUntilMillis) {
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.itemId = itemId;
        this.addonName = addonName;
        this.failureType = failureType;
        this.failureMessage = failureMessage;
        this.consecutiveFailures = consecutiveFailures;
        this.failuresObserved = failuresObserved;
        this.suppressedReports = suppressedReports;
        this.firstFailureMillis = firstFailureMillis;
        this.lastFailureMillis = lastFailureMillis;
        this.pausedUntilMillis = pausedUntilMillis;
    }

    public String getWorldName() {
        return worldName;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public String getItemId() {
        return itemId;
    }

    public String getAddonName() {
        return addonName;
    }

    public String getFailureType() {
        return failureType;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public long getFailuresObserved() {
        return failuresObserved;
    }

    public long getSuppressedReports() {
        return suppressedReports;
    }

    public long getFirstFailureMillis() {
        return firstFailureMillis;
    }

    public long getLastFailureMillis() {
        return lastFailureMillis;
    }

    public long getPausedUntilMillis() {
        return pausedUntilMillis;
    }

    public boolean isPaused(long nowMillis) {
        return pausedUntilMillis > nowMillis;
    }

    public long getRetrySeconds(long nowMillis) {
        return isPaused(nowMillis) ? Math.max(1L, (pausedUntilMillis - nowMillis + 999L) / 1000L) : 0L;
    }
}
