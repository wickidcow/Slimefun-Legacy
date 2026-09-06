package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.FieldKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordKey;
import io.github.thebusybiscuit.slimefun4.api.storage.StorageIntegrityConfirmationSnapshot;
import io.github.thebusybiscuit.slimefun4.api.storage.StorageIntegritySnapshot;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Performs an explicit, read-only integrity scan of Slimefun's block-storage backend.
 *
 * <p>This scanner deliberately compares database ownership records only. It does not scan physical world blocks,
 * force-load chunks, mutate caches, delete rows, or attempt repair. This keeps the diagnostic useful on live servers
 * while avoiding another always-on storage task.
 */
public final class StorageIntegrityScanner {

    static final int SAMPLE_LIMIT = 10;

    private static final Object SCAN_LOCK = new Object();
    private static final StorageIntegrityConfirmationTracker CONFIRMATION_TRACKER =
            new StorageIntegrityConfirmationTracker();
    private static volatile CompletableFuture<?> activeOperation;
    private static volatile StorageIntegritySnapshot lastSnapshot;
    private static volatile StorageIntegrityRepairVerification lastRepairVerification;

    private StorageIntegrityScanner() {}

    /**
     * Starts a storage integrity scan on the controller's existing read executor.
     *
     * @param controller active block data controller
     * @return the new scan future, or {@code null} if another scan or verification is already active
     */
    public static @Nullable CompletableFuture<StorageIntegritySnapshot> startScan(@Nonnull BlockDataController controller) {
        CompletableFuture<StorageIntegritySnapshot> future;
        synchronized (SCAN_LOCK) {
            if (hasActiveOperation()) {
                return null;
            }

            future = new CompletableFuture<>();
            activeOperation = future;
            lastRepairVerification = null;
        }

        try {
            controller.scheduleReadTask(() -> runScan(controller, future));
        } catch (RuntimeException failure) {
            CONFIRMATION_TRACKER.invalidate(System.currentTimeMillis());
            future.completeExceptionally(failure);
            clearActiveOperation(future);
        }
        return future;
    }

    public static boolean isScanRunning() {
        return hasActiveOperation();
    }

    public static @Nullable StorageIntegritySnapshot getLastSnapshot() {
        return lastSnapshot;
    }

    public static @Nonnull StorageIntegrityConfirmationSnapshot getConfirmationSnapshot() {
        return CONFIRMATION_TRACKER.getSnapshot();
    }

    /**
     * Returns a read-only plan for the exact candidate set that most recently reached two-pass confirmation.
     *
     * <p>No plan is returned while a new scan is active or when confirmation is not currently valid. Generating this
     * object does not touch the database or mutate the confirmation state.
     */
    public static @Nullable StorageIntegrityRepairPlan getConfirmedRepairPlan() {
        synchronized (SCAN_LOCK) {
            if (hasActiveOperation()) {
                return null;
            }
            return CONFIRMATION_TRACKER.createRepairPlan(System.currentTimeMillis());
        }
    }

    /**
     * Starts the final read-only revalidation for a confirmed repair plan fingerprint.
     *
     * <p>The supplied fingerprint must exactly match the currently confirmed plan before the backend is scanned again.
     * The fresh scan then has to observe the same exact candidate set with both active and deferred write queues empty at
     * both boundaries. No storage row is changed by this operation.
     *
     * @param controller active block data controller
     * @param expectedFingerprint full SHA-256 fingerprint printed by {@code /sf doctor storage plan}
     * @return verification future, or {@code null} if another storage diagnostic operation is already active
     */
    public static @Nullable CompletableFuture<StorageIntegrityRepairVerification> startRepairVerification(
            @Nonnull BlockDataController controller, @Nonnull String expectedFingerprint) {
        String normalizedFingerprint = expectedFingerprint.trim().toLowerCase(Locale.ROOT);
        CompletableFuture<StorageIntegrityRepairVerification> future;

        synchronized (SCAN_LOCK) {
            if (hasActiveOperation()) {
                return null;
            }

            long now = System.currentTimeMillis();
            StorageIntegrityRepairPlan plan = CONFIRMATION_TRACKER.createRepairPlan(now);
            if (plan == null) {
                StorageIntegrityRepairVerification result = new StorageIntegrityRepairVerification(
                        StorageIntegrityRepairVerification.Status.CONFIRMATION_INVALIDATED,
                        normalizedFingerprint,
                        null,
                        null,
                        now);
                lastRepairVerification = result;
                return CompletableFuture.completedFuture(result);
            }
            if (plan.isEmpty()) {
                StorageIntegrityRepairVerification result = new StorageIntegrityRepairVerification(
                        StorageIntegrityRepairVerification.Status.EMPTY_PLAN,
                        normalizedFingerprint,
                        plan.getFingerprint(),
                        null,
                        now);
                lastRepairVerification = result;
                return CompletableFuture.completedFuture(result);
            }
            if (!plan.getFingerprint().equals(normalizedFingerprint)) {
                StorageIntegrityRepairVerification result = new StorageIntegrityRepairVerification(
                        StorageIntegrityRepairVerification.Status.FINGERPRINT_REJECTED,
                        normalizedFingerprint,
                        plan.getFingerprint(),
                        null,
                        now);
                lastRepairVerification = result;
                return CompletableFuture.completedFuture(result);
            }

            future = new CompletableFuture<>();
            activeOperation = future;
            lastRepairVerification = null;
        }

        try {
            controller.scheduleReadTask(() -> runRepairVerification(controller, normalizedFingerprint, future));
        } catch (RuntimeException failure) {
            CONFIRMATION_TRACKER.invalidate(System.currentTimeMillis());
            future.completeExceptionally(failure);
            clearActiveOperation(future);
        }
        return future;
    }

    /**
     * Returns the most recent fingerprint verification result, including failed attempts.
     */
    public static @Nullable StorageIntegrityRepairVerification getRepairVerificationSnapshot() {
        return lastRepairVerification;
    }

    /**
     * Returns a currently verified plan only when the short-lived final revalidation gate is still valid.
     *
     * <p>This is the gate a future destructive repair must pass before it acquires its final read/write safety barriers.
     * The returned plan is still not authority to delete rows without those immediate barriers.
     */
    public static @Nullable StorageIntegrityRepairPlan getVerifiedRepairPlan(@Nonnull String fingerprint) {
        String normalizedFingerprint = fingerprint.trim().toLowerCase(Locale.ROOT);
        synchronized (SCAN_LOCK) {
            if (hasActiveOperation()) {
                return null;
            }

            long now = System.currentTimeMillis();
            StorageIntegrityRepairVerification verification = lastRepairVerification;
            if (verification == null
                    || !verification.isCurrent(now)
                    || !verification.getExpectedFingerprint().equals(normalizedFingerprint)) {
                return null;
            }

            StorageIntegrityRepairPlan plan = CONFIRMATION_TRACKER.createRepairPlan(now);
            if (plan == null || !plan.getFingerprint().equals(normalizedFingerprint)) {
                return null;
            }
            return plan;
        }
    }

    private static void runScan(BlockDataController controller, CompletableFuture<StorageIntegritySnapshot> future) {
        try {
            ScanResult result = scanBackend(controller);
            StorageIntegritySnapshot snapshot = result.snapshot();
            CONFIRMATION_TRACKER.record(
                    result.candidates(), snapshot.wasStorageQuietAtBoundaries(), snapshot.getCompletedAtMillis());
            lastSnapshot = snapshot;
            future.complete(snapshot);
        } catch (Throwable failure) {
            CONFIRMATION_TRACKER.invalidate(System.currentTimeMillis());
            future.completeExceptionally(failure);
        } finally {
            clearActiveOperation(future);
        }
    }

    private static void runRepairVerification(
            BlockDataController controller,
            String expectedFingerprint,
            CompletableFuture<StorageIntegrityRepairVerification> future) {
        try {
            ScanResult result = scanBackend(controller);
            StorageIntegritySnapshot snapshot = result.snapshot();
            StorageIntegrityConfirmationSnapshot confirmation = CONFIRMATION_TRACKER.record(
                    result.candidates(), snapshot.wasStorageQuietAtBoundaries(), snapshot.getCompletedAtMillis());
            lastSnapshot = snapshot;

            StorageIntegrityRepairPlan observedPlan = result.candidates()
                    .toRepairPlan(
                            snapshot.getCompletedAtMillis(),
                            confirmation.getConfirmedAtMillis(),
                            snapshot.getCompletedAtMillis());
            String observedFingerprint = observedPlan.getFingerprint();

            StorageIntegrityRepairVerification.Status status;
            if (!snapshot.wasStorageQuietAtBoundaries()) {
                status = StorageIntegrityRepairVerification.Status.STORAGE_NOT_QUIET;
            } else if (!observedFingerprint.equals(expectedFingerprint)) {
                status = StorageIntegrityRepairVerification.Status.CANDIDATE_SET_CHANGED;
            } else if (!confirmation.isConfirmed()
                    || confirmation.getLastScanCompletedAtMillis() != snapshot.getCompletedAtMillis()) {
                status = StorageIntegrityRepairVerification.Status.CONFIRMATION_INVALIDATED;
            } else {
                status = StorageIntegrityRepairVerification.Status.VERIFIED;
            }

            StorageIntegrityRepairVerification verification = new StorageIntegrityRepairVerification(
                    status,
                    expectedFingerprint,
                    observedFingerprint,
                    snapshot,
                    snapshot.getCompletedAtMillis());
            lastRepairVerification = verification;
            future.complete(verification);
        } catch (Throwable failure) {
            CONFIRMATION_TRACKER.invalidate(System.currentTimeMillis());
            future.completeExceptionally(failure);
        } finally {
            clearActiveOperation(future);
        }
    }

    private static ScanResult scanBackend(BlockDataController controller) {
        long startedAt = System.currentTimeMillis();
        int pendingWritesAtStart = controller.getPendingWriteTaskCount();
        int pendingDelayedWritesAtStart = controller.getPendingDelayedWriteTaskCount();
        boolean delayedSavingEnabled = controller.isDelayedSavingEnabled();

        Set<String> blockRecords = readOwners(controller, DataScope.BLOCK_RECORD, FieldKey.LOCATION);
        Set<String> blockDataOwners = readOwners(controller, DataScope.BLOCK_DATA, FieldKey.LOCATION);
        Set<String> blockInventoryOwners = readOwners(controller, DataScope.BLOCK_INVENTORY, FieldKey.LOCATION);
        Set<String> orphanBlockData = findOrphans(blockDataOwners, blockRecords);
        Set<String> orphanBlockInventories = findOrphans(blockInventoryOwners, blockRecords);

        Set<String> universalRecords = readOwners(controller, DataScope.UNIVERSAL_RECORD, FieldKey.UNIVERSAL_UUID);
        Set<String> universalDataOwners = readOwners(controller, DataScope.UNIVERSAL_DATA, FieldKey.UNIVERSAL_UUID);
        Set<String> universalInventoryOwners =
                readOwners(controller, DataScope.UNIVERSAL_INVENTORY, FieldKey.UNIVERSAL_UUID);
        Set<String> orphanUniversalData = findOrphans(universalDataOwners, universalRecords);
        Set<String> orphanUniversalInventories = findOrphans(universalInventoryOwners, universalRecords);

        long completedAt = System.currentTimeMillis();
        int pendingWritesAtEnd = controller.getPendingWriteTaskCount();
        int pendingDelayedWritesAtEnd = controller.getPendingDelayedWriteTaskCount();
        StorageIntegritySnapshot snapshot = new StorageIntegritySnapshot(
                startedAt,
                completedAt,
                blockRecords.size(),
                blockDataOwners.size(),
                blockInventoryOwners.size(),
                orphanBlockData.size(),
                orphanBlockInventories.size(),
                universalRecords.size(),
                universalDataOwners.size(),
                universalInventoryOwners.size(),
                orphanUniversalData.size(),
                orphanUniversalInventories.size(),
                pendingWritesAtStart,
                pendingWritesAtEnd,
                pendingDelayedWritesAtStart,
                pendingDelayedWritesAtEnd,
                delayedSavingEnabled,
                sample(orphanBlockData),
                sample(orphanBlockInventories),
                sample(orphanUniversalData),
                sample(orphanUniversalInventories));

        StorageIntegrityConfirmationTracker.CandidateSet candidates =
                new StorageIntegrityConfirmationTracker.CandidateSet(
                        orphanBlockData,
                        orphanBlockInventories,
                        orphanUniversalData,
                        orphanUniversalInventories);
        return new ScanResult(snapshot, candidates);
    }

    private static Set<String> readOwners(BlockDataController controller, DataScope scope, FieldKey ownerField) {
        RecordKey key = new RecordKey(scope);
        key.addField(ownerField);
        Set<String> owners = new HashSet<>();
        controller.getData(key, true).forEach(record -> {
            String owner = record.get(ownerField);
            if (owner != null && !owner.isBlank()) {
                owners.add(owner);
            }
        });
        return owners;
    }

    static Set<String> findOrphans(Set<String> owners, Set<String> roots) {
        Set<String> orphans = new HashSet<>(owners);
        orphans.removeAll(roots);
        return orphans;
    }

    static List<String> sample(Set<String> owners) {
        List<String> sorted = new ArrayList<>(owners);
        sorted.sort(String::compareTo);
        return List.copyOf(sorted.subList(0, Math.min(SAMPLE_LIMIT, sorted.size())));
    }

    private static boolean hasActiveOperation() {
        CompletableFuture<?> current = activeOperation;
        return current != null && !current.isDone();
    }

    private static void clearActiveOperation(CompletableFuture<?> future) {
        synchronized (SCAN_LOCK) {
            if (activeOperation == future) {
                activeOperation = null;
            }
        }
    }

    private record ScanResult(
            StorageIntegritySnapshot snapshot, StorageIntegrityConfirmationTracker.CandidateSet candidates) {}
}
