package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import io.github.thebusybiscuit.slimefun4.api.storage.StorageIntegrityConfirmationSnapshot;
import java.util.Objects;
import java.util.Set;

/**
 * Tracks repeated quiet storage-integrity observations in memory.
 *
 * <p>The tracker deliberately requires two exactly matching candidate sets separated by a minimum interval. Any noisy
 * scan or changed candidate set invalidates prior confirmation. State is runtime-only and is not persisted across
 * restarts.
 */
final class StorageIntegrityConfirmationTracker {

    static final long MIN_CONFIRMATION_INTERVAL_MILLIS = 30_000L;

    private CandidateSet baseline;
    private long firstPassCompletedAtMillis;
    private long confirmedAtMillis;
    private StorageIntegrityConfirmationSnapshot snapshot = new StorageIntegrityConfirmationSnapshot(
            0, false, false, false, 0, 0L, 0L, 0L, MIN_CONFIRMATION_INTERVAL_MILLIS);

    synchronized StorageIntegrityConfirmationSnapshot record(
            CandidateSet candidates, boolean storageQuiet, long completedAtMillis) {
        Objects.requireNonNull(candidates, "candidates");

        if (!storageQuiet) {
            baseline = null;
            firstPassCompletedAtMillis = 0L;
            confirmedAtMillis = 0L;
            snapshot = new StorageIntegrityConfirmationSnapshot(
                    0,
                    false,
                    false,
                    false,
                    candidates.totalOwners(),
                    0L,
                    completedAtMillis,
                    0L,
                    MIN_CONFIRMATION_INTERVAL_MILLIS);
            return snapshot;
        }

        boolean changed = baseline != null && !baseline.equals(candidates);
        if (baseline == null || changed) {
            baseline = candidates;
            firstPassCompletedAtMillis = completedAtMillis;
            confirmedAtMillis = 0L;
            snapshot = new StorageIntegrityConfirmationSnapshot(
                    1,
                    false,
                    true,
                    changed,
                    candidates.totalOwners(),
                    firstPassCompletedAtMillis,
                    completedAtMillis,
                    0L,
                    MIN_CONFIRMATION_INTERVAL_MILLIS);
            return snapshot;
        }

        long elapsed = Math.max(0L, completedAtMillis - firstPassCompletedAtMillis);
        if (elapsed < MIN_CONFIRMATION_INTERVAL_MILLIS) {
            snapshot = new StorageIntegrityConfirmationSnapshot(
                    1,
                    false,
                    true,
                    false,
                    candidates.totalOwners(),
                    firstPassCompletedAtMillis,
                    completedAtMillis,
                    0L,
                    MIN_CONFIRMATION_INTERVAL_MILLIS);
            return snapshot;
        }

        if (confirmedAtMillis == 0L) {
            confirmedAtMillis = completedAtMillis;
        }
        snapshot = new StorageIntegrityConfirmationSnapshot(
                2,
                true,
                true,
                false,
                candidates.totalOwners(),
                firstPassCompletedAtMillis,
                completedAtMillis,
                confirmedAtMillis,
                MIN_CONFIRMATION_INTERVAL_MILLIS);
        return snapshot;
    }

    synchronized void invalidate(long completedAtMillis) {
        baseline = null;
        firstPassCompletedAtMillis = 0L;
        confirmedAtMillis = 0L;
        snapshot = new StorageIntegrityConfirmationSnapshot(
                0, false, false, false, 0, 0L, completedAtMillis, 0L, MIN_CONFIRMATION_INTERVAL_MILLIS);
    }

    synchronized StorageIntegrityConfirmationSnapshot getSnapshot() {
        return snapshot;
    }

    /**
     * Builds an immutable read-only plan from the exact candidate set that reached pass 2/2.
     *
     * @return a repair plan, or {@code null} when confirmation is not currently valid
     */
    synchronized StorageIntegrityRepairPlan createRepairPlan(long generatedAtMillis) {
        if (!snapshot.isConfirmed() || baseline == null) {
            return null;
        }

        return baseline.toRepairPlan(
                generatedAtMillis, snapshot.getConfirmedAtMillis(), snapshot.getLastScanCompletedAtMillis());
    }

    static final class CandidateSet {

        private final Set<String> blockData;
        private final Set<String> blockInventory;
        private final Set<String> universalData;
        private final Set<String> universalInventory;

        CandidateSet(
                Set<String> blockData,
                Set<String> blockInventory,
                Set<String> universalData,
                Set<String> universalInventory) {
            this.blockData = Set.copyOf(blockData);
            this.blockInventory = Set.copyOf(blockInventory);
            this.universalData = Set.copyOf(universalData);
            this.universalInventory = Set.copyOf(universalInventory);
        }

        int totalOwners() {
            return blockData.size() + blockInventory.size() + universalData.size() + universalInventory.size();
        }

        StorageIntegrityRepairPlan toRepairPlan(
                long generatedAtMillis, long confirmedAtMillis, long sourceScanCompletedAtMillis) {
            return new StorageIntegrityRepairPlan(
                    generatedAtMillis,
                    confirmedAtMillis,
                    sourceScanCompletedAtMillis,
                    blockData,
                    blockInventory,
                    universalData,
                    universalInventory);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof CandidateSet that)) {
                return false;
            }
            return blockData.equals(that.blockData)
                    && blockInventory.equals(that.blockInventory)
                    && universalData.equals(that.universalData)
                    && universalInventory.equals(that.universalInventory);
        }

        @Override
        public int hashCode() {
            return Objects.hash(blockData, blockInventory, universalData, universalInventory);
        }
    }
}
