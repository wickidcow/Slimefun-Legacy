package io.github.thebusybiscuit.slimefun4.api.storage;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;

/**
 * Immutable runtime status for Slimefun's two-pass storage-integrity confirmation guard.
 *
 * <p>Confirmation is diagnostic evidence only. It does not authorize or perform deletion, migration, or repair.
 */
@SlimefunAPI
public final class StorageIntegrityConfirmationSnapshot {

    private final int quietPasses;
    private final boolean confirmed;
    private final boolean lastScanQuiet;
    private final boolean candidateSetChanged;
    private final int candidateOwners;
    private final long firstPassCompletedAtMillis;
    private final long lastScanCompletedAtMillis;
    private final long confirmedAtMillis;
    private final long minimumIntervalMillis;

    public StorageIntegrityConfirmationSnapshot(
            int quietPasses,
            boolean confirmed,
            boolean lastScanQuiet,
            boolean candidateSetChanged,
            int candidateOwners,
            long firstPassCompletedAtMillis,
            long lastScanCompletedAtMillis,
            long confirmedAtMillis,
            long minimumIntervalMillis) {
        this.quietPasses = quietPasses;
        this.confirmed = confirmed;
        this.lastScanQuiet = lastScanQuiet;
        this.candidateSetChanged = candidateSetChanged;
        this.candidateOwners = candidateOwners;
        this.firstPassCompletedAtMillis = firstPassCompletedAtMillis;
        this.lastScanCompletedAtMillis = lastScanCompletedAtMillis;
        this.confirmedAtMillis = confirmedAtMillis;
        this.minimumIntervalMillis = minimumIntervalMillis;
    }

    public int getQuietPasses() {
        return quietPasses;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public boolean wasLastScanQuiet() {
        return lastScanQuiet;
    }

    public boolean didCandidateSetChange() {
        return candidateSetChanged;
    }

    public int getCandidateOwners() {
        return candidateOwners;
    }

    public long getFirstPassCompletedAtMillis() {
        return firstPassCompletedAtMillis;
    }

    public long getLastScanCompletedAtMillis() {
        return lastScanCompletedAtMillis;
    }

    public long getConfirmedAtMillis() {
        return confirmedAtMillis;
    }

    public long getMinimumIntervalMillis() {
        return minimumIntervalMillis;
    }

    public boolean hasQuietBaseline() {
        return quietPasses > 0 && firstPassCompletedAtMillis > 0L;
    }

    /**
     * Returns how much longer a matching quiet scan must wait before it can become pass two.
     */
    public long getRemainingWaitMillis(long nowMillis) {
        if (!hasQuietBaseline() || confirmed) {
            return 0L;
        }
        long eligibleAt = firstPassCompletedAtMillis + minimumIntervalMillis;
        return Math.max(0L, eligibleAt - nowMillis);
    }
}
