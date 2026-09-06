package io.github.thebusybiscuit.slimefun4.api.storage;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;

/**
 * Immutable result of an explicit Slimefun block-storage integrity scan.
 *
 * <p>The scan compares record owners against data and inventory owners in the active storage backend. It is
 * observational only and does not delete, migrate, repair, force-load, or otherwise mutate stored data.
 */
@SlimefunAPI
public final class StorageIntegritySnapshot {

    private final long startedAtMillis;
    private final long completedAtMillis;
    private final int blockRecords;
    private final int blockDataOwners;
    private final int blockInventoryOwners;
    private final int orphanBlockDataOwners;
    private final int orphanBlockInventoryOwners;
    private final int universalRecords;
    private final int universalDataOwners;
    private final int universalInventoryOwners;
    private final int orphanUniversalDataOwners;
    private final int orphanUniversalInventoryOwners;
    private final int pendingWritesAtStart;
    private final int delayedWritesAtStart;

    public StorageIntegritySnapshot(
            long startedAtMillis,
            long completedAtMillis,
            int blockRecords,
            int blockDataOwners,
            int blockInventoryOwners,
            int orphanBlockDataOwners,
            int orphanBlockInventoryOwners,
            int universalRecords,
            int universalDataOwners,
            int universalInventoryOwners,
            int orphanUniversalDataOwners,
            int orphanUniversalInventoryOwners,
            int pendingWritesAtStart,
            int delayedWritesAtStart) {
        this.startedAtMillis = startedAtMillis;
        this.completedAtMillis = completedAtMillis;
        this.blockRecords = blockRecords;
        this.blockDataOwners = blockDataOwners;
        this.blockInventoryOwners = blockInventoryOwners;
        this.orphanBlockDataOwners = orphanBlockDataOwners;
        this.orphanBlockInventoryOwners = orphanBlockInventoryOwners;
        this.universalRecords = universalRecords;
        this.universalDataOwners = universalDataOwners;
        this.universalInventoryOwners = universalInventoryOwners;
        this.orphanUniversalDataOwners = orphanUniversalDataOwners;
        this.orphanUniversalInventoryOwners = orphanUniversalInventoryOwners;
        this.pendingWritesAtStart = pendingWritesAtStart;
        this.delayedWritesAtStart = delayedWritesAtStart;
    }

    public long getStartedAtMillis() {
        return startedAtMillis;
    }

    public long getCompletedAtMillis() {
        return completedAtMillis;
    }

    public long getDurationMillis() {
        return Math.max(0L, completedAtMillis - startedAtMillis);
    }

    public int getBlockRecords() {
        return blockRecords;
    }

    public int getBlockDataOwners() {
        return blockDataOwners;
    }

    public int getBlockInventoryOwners() {
        return blockInventoryOwners;
    }

    public int getOrphanBlockDataOwners() {
        return orphanBlockDataOwners;
    }

    public int getOrphanBlockInventoryOwners() {
        return orphanBlockInventoryOwners;
    }

    public int getUniversalRecords() {
        return universalRecords;
    }

    public int getUniversalDataOwners() {
        return universalDataOwners;
    }

    public int getUniversalInventoryOwners() {
        return universalInventoryOwners;
    }

    public int getOrphanUniversalDataOwners() {
        return orphanUniversalDataOwners;
    }

    public int getOrphanUniversalInventoryOwners() {
        return orphanUniversalInventoryOwners;
    }

    public int getPendingWritesAtStart() {
        return pendingWritesAtStart;
    }

    public int getDelayedWritesAtStart() {
        return delayedWritesAtStart;
    }

    public int getTotalOrphanOwners() {
        return orphanBlockDataOwners
                + orphanBlockInventoryOwners
                + orphanUniversalDataOwners
                + orphanUniversalInventoryOwners;
    }

    public boolean isClean() {
        return getTotalOrphanOwners() == 0;
    }

    /**
     * Returns whether writes were already queued when the scan began.
     *
     * <p>A scan with concurrent writes is still useful diagnostically, but orphan candidates should be rescanned during
     * a quiet period before any future repair operation is considered.
     */
    public boolean hadPendingWritesAtStart() {
        return pendingWritesAtStart > 0 || delayedWritesAtStart > 0;
    }
}
