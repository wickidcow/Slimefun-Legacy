package io.github.thebusybiscuit.slimefun4.api.storage;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * Immutable result of an explicit Slimefun block-storage integrity scan.
 *
 * <p>The scan compares primary storage record owners against secondary data and inventory owners in the active storage
 * backend. It is observational only and does not delete, migrate, repair, force-load, or otherwise mutate stored data.
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
    private final int pendingWritesAtEnd;
    private final int pendingDelayedWritesAtStart;
    private final int pendingDelayedWritesAtEnd;
    private final boolean delayedSavingEnabled;
    private final List<String> orphanBlockDataOwnerSamples;
    private final List<String> orphanBlockInventoryOwnerSamples;
    private final List<String> orphanUniversalDataOwnerSamples;
    private final List<String> orphanUniversalInventoryOwnerSamples;

    /**
     * Creates a storage integrity snapshot using the original 4.1.46 constructor shape.
     *
     * <p>When delayed saving is enabled, this constructor cannot prove how many deferred mutations existed at the scan
     * boundaries, so the delayed-write counts are recorded as unknown ({@code -1}). Callers that can observe those
     * counts should use the extended constructor.
     */
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
            int pendingWritesAtEnd,
            boolean delayedSavingEnabled,
            @Nonnull List<String> orphanBlockDataOwnerSamples,
            @Nonnull List<String> orphanBlockInventoryOwnerSamples,
            @Nonnull List<String> orphanUniversalDataOwnerSamples,
            @Nonnull List<String> orphanUniversalInventoryOwnerSamples) {
        this(
                startedAtMillis,
                completedAtMillis,
                blockRecords,
                blockDataOwners,
                blockInventoryOwners,
                orphanBlockDataOwners,
                orphanBlockInventoryOwners,
                universalRecords,
                universalDataOwners,
                universalInventoryOwners,
                orphanUniversalDataOwners,
                orphanUniversalInventoryOwners,
                pendingWritesAtStart,
                pendingWritesAtEnd,
                delayedSavingEnabled ? -1 : 0,
                delayedSavingEnabled ? -1 : 0,
                delayedSavingEnabled,
                orphanBlockDataOwnerSamples,
                orphanBlockInventoryOwnerSamples,
                orphanUniversalDataOwnerSamples,
                orphanUniversalInventoryOwnerSamples);
    }

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
            int pendingWritesAtEnd,
            int pendingDelayedWritesAtStart,
            int pendingDelayedWritesAtEnd,
            boolean delayedSavingEnabled,
            @Nonnull List<String> orphanBlockDataOwnerSamples,
            @Nonnull List<String> orphanBlockInventoryOwnerSamples,
            @Nonnull List<String> orphanUniversalDataOwnerSamples,
            @Nonnull List<String> orphanUniversalInventoryOwnerSamples) {
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
        this.pendingWritesAtEnd = pendingWritesAtEnd;
        this.pendingDelayedWritesAtStart = pendingDelayedWritesAtStart;
        this.pendingDelayedWritesAtEnd = pendingDelayedWritesAtEnd;
        this.delayedSavingEnabled = delayedSavingEnabled;
        this.orphanBlockDataOwnerSamples = List.copyOf(orphanBlockDataOwnerSamples);
        this.orphanBlockInventoryOwnerSamples = List.copyOf(orphanBlockInventoryOwnerSamples);
        this.orphanUniversalDataOwnerSamples = List.copyOf(orphanUniversalDataOwnerSamples);
        this.orphanUniversalInventoryOwnerSamples = List.copyOf(orphanUniversalInventoryOwnerSamples);
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

    public int getPendingWritesAtEnd() {
        return pendingWritesAtEnd;
    }

    /**
     * Returns the number of deferred delayed-saving mutations observed when the scan started.
     *
     * @return delayed mutation count, or {@code -1} if it was not observable
     */
    public int getPendingDelayedWritesAtStart() {
        return pendingDelayedWritesAtStart;
    }

    /**
     * Returns the number of deferred delayed-saving mutations observed when the scan completed.
     *
     * @return delayed mutation count, or {@code -1} if it was not observable
     */
    public int getPendingDelayedWritesAtEnd() {
        return pendingDelayedWritesAtEnd;
    }

    public boolean isDelayedSavingEnabled() {
        return delayedSavingEnabled;
    }

    public @Nonnull List<String> getOrphanBlockDataOwnerSamples() {
        return orphanBlockDataOwnerSamples;
    }

    public @Nonnull List<String> getOrphanBlockInventoryOwnerSamples() {
        return orphanBlockInventoryOwnerSamples;
    }

    public @Nonnull List<String> getOrphanUniversalDataOwnerSamples() {
        return orphanUniversalDataOwnerSamples;
    }

    public @Nonnull List<String> getOrphanUniversalInventoryOwnerSamples() {
        return orphanUniversalInventoryOwnerSamples;
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
     * Returns whether queued writes were visible at either scan boundary.
     */
    public boolean hadPendingWritesDuringScan() {
        return pendingWritesAtStart > 0 || pendingWritesAtEnd > 0;
    }

    /**
     * Returns whether delayed-saving mutations were visible at either scan boundary.
     *
     * <p>An unknown delayed-write count is treated conservatively as pending work.
     */
    public boolean hadPendingDelayedWritesDuringScan() {
        return pendingDelayedWritesAtStart != 0 || pendingDelayedWritesAtEnd != 0;
    }

    /**
     * Returns whether both active and delayed write queues were observed empty at both scan boundaries.
     *
     * <p>This is intentionally stricter than checking only the normal write executor. A future repair workflow must not
     * rely on a scan while deferred delayed-saving mutations are still outstanding or their count is unknown.
     */
    public boolean wasStorageQuietAtBoundaries() {
        return !hadPendingWritesDuringScan() && !hadPendingDelayedWritesDuringScan();
    }
}
