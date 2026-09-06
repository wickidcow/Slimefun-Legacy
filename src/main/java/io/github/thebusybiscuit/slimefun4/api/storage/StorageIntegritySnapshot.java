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
    private final boolean delayedSavingEnabled;
    private final List<String> orphanBlockDataOwnerSamples;
    private final List<String> orphanBlockInventoryOwnerSamples;
    private final List<String> orphanUniversalDataOwnerSamples;
    private final List<String> orphanUniversalInventoryOwnerSamples;

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
     *
     * <p>A scan with writes at a boundary is still useful diagnostically, but orphan candidates should be rescanned
     * during a quiet period before any future repair operation is considered. Delayed-saving mode is reported
     * separately because delayed mutations may not yet have entered the controller's active write queue.
     */
    public boolean hadPendingWritesDuringScan() {
        return pendingWritesAtStart > 0 || pendingWritesAtEnd > 0;
    }
}
