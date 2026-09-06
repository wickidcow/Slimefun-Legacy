package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.FieldKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordKey;
import io.github.thebusybiscuit.slimefun4.api.storage.StorageIntegrityConfirmationSnapshot;
import io.github.thebusybiscuit.slimefun4.api.storage.StorageIntegritySnapshot;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Performs explicit storage-integrity diagnostics and guarded orphan-secondary repair for Slimefun's block backend.
 *
 * <p>Normal scans and verification remain read-only. Destructive repair is available only after the complete two-pass
 * confirmation, plan fingerprint and short-lived verification flow has succeeded. Repair never deletes primary block or
 * universal records.
 */
public final class StorageIntegrityScanner {

    static final int SAMPLE_LIMIT = 10;

    private static final Object SCAN_LOCK = new Object();
    private static final StorageIntegrityConfirmationTracker CONFIRMATION_TRACKER =
            new StorageIntegrityConfirmationTracker();
    private static volatile CompletableFuture<?> activeOperation;
    private static volatile StorageIntegritySnapshot lastSnapshot;
    private static volatile StorageIntegrityRepairVerification lastRepairVerification;
    private static volatile StorageIntegrityRepairExecution lastRepairExecution;

    private StorageIntegrityScanner() {}

    /**
     * Starts a storage integrity scan on the controller's existing read executor.
     *
     * @param controller active block data controller
     * @return the new scan future, or {@code null} if another scan, verification or repair is already active
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
     * <p>No plan is returned while another storage-integrity operation is active or when confirmation is not currently
     * valid. Generating this object does not touch the database or mutate the confirmation state.
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
     * <p>The returned plan is not authority to mutate storage without the immediate read/write barriers used by
     * {@link #startRepairExecution(BlockDataController, String, Path)}.
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

    /**
     * Starts the destructive orphan-secondary repair after all earlier safety gates have passed.
     *
     * <p>The successful verification is single-use: once a repair execution is accepted, it is consumed before the
     * critical section starts. The critical section requires the tracked read executor and every write executor to be
     * idle, re-scans the exact candidate set, refuses cached/live candidates, writes a durable backup, deletes only
     * secondary rows, and verifies the targeted owners are gone before releasing the gates.
     *
     * <p>For 4.1.46 this final destructive step deliberately requires delayed writing to be disabled at startup. This
     * avoids allowing a deferred mutation to be created outside the normal write-submission barrier while rows are being
     * deleted. Read-only scan, plan and verification continue to support delayed writing normally.
     *
     * @param controller active block data controller
     * @param expectedFingerprint full verified SHA-256 fingerprint
     * @param backupDirectory directory where the mandatory lossless backup will be written
     * @return repair future, or {@code null} if another integrity operation is active
     */
    public static @Nullable CompletableFuture<StorageIntegrityRepairExecution> startRepairExecution(
            @Nonnull BlockDataController controller,
            @Nonnull String expectedFingerprint,
            @Nonnull Path backupDirectory) {
        String normalizedFingerprint = expectedFingerprint.trim().toLowerCase(Locale.ROOT);
        CompletableFuture<StorageIntegrityRepairExecution> future;
        StorageIntegrityRepairPlan plan;

        synchronized (SCAN_LOCK) {
            if (hasActiveOperation()) {
                return null;
            }

            long now = System.currentTimeMillis();
            StorageIntegrityRepairVerification verification = lastRepairVerification;
            plan = CONFIRMATION_TRACKER.createRepairPlan(now);
            if (verification == null
                    || !verification.isCurrent(now)
                    || !verification.getExpectedFingerprint().equals(normalizedFingerprint)
                    || plan == null
                    || !plan.getFingerprint().equals(normalizedFingerprint)) {
                StorageIntegrityRepairExecution result = execution(
                        StorageIntegrityRepairExecution.Status.VERIFICATION_REQUIRED,
                        normalizedFingerprint,
                        null,
                        "Run the final fingerprint verification again before repair.",
                        0);
                lastRepairExecution = result;
                return CompletableFuture.completedFuture(result);
            }
            if (plan.isEmpty()) {
                StorageIntegrityRepairExecution result = execution(
                        StorageIntegrityRepairExecution.Status.EMPTY_PLAN,
                        normalizedFingerprint,
                        null,
                        "The verified repair plan is empty.",
                        0);
                lastRepairExecution = result;
                return CompletableFuture.completedFuture(result);
            }
            if (controller.isDelayedSavingEnabled()) {
                StorageIntegrityRepairExecution result = execution(
                        StorageIntegrityRepairExecution.Status.DELAYED_SAVING_ENABLED,
                        normalizedFingerprint,
                        null,
                        "Disable delayedWriting.enable in block-storage.yml and restart before destructive repair.",
                        0);
                lastRepairExecution = result;
                return CompletableFuture.completedFuture(result);
            }
            if (controller.getPendingDelayedWriteTaskCount() != 0) {
                StorageIntegrityRepairExecution result = execution(
                        StorageIntegrityRepairExecution.Status.STORAGE_BUSY,
                        normalizedFingerprint,
                        null,
                        "Deferred write tasks are still present.",
                        0);
                lastRepairExecution = result;
                return CompletableFuture.completedFuture(result);
            }

            future = new CompletableFuture<>();
            activeOperation = future;
            lastRepairExecution = null;
            // Successful final verification is intentionally single-use once a destructive attempt is accepted.
            lastRepairVerification = null;
        }

        StorageIntegrityRepairPlan acceptedPlan = plan;
        CompletableFuture.runAsync(
                () -> runRepairExecution(controller, acceptedPlan, normalizedFingerprint, backupDirectory, future));
        return future;
    }

    public static @Nullable StorageIntegrityRepairExecution getRepairExecutionSnapshot() {
        return lastRepairExecution;
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

    private static void runRepairExecution(
            BlockDataController controller,
            StorageIntegrityRepairPlan plan,
            String fingerprint,
            Path backupDirectory,
            CompletableFuture<StorageIntegrityRepairExecution> future) {
        try {
            StorageIntegrityRepairExecution result = attemptRepairWithBarriers(
                    controller, plan, fingerprint, backupDirectory);
            lastRepairExecution = result;
            future.complete(result);
        } catch (Throwable failure) {
            CONFIRMATION_TRACKER.invalidate(System.currentTimeMillis());
            Slimefun.logger().log(
                    Level.SEVERE,
                    "Storage integrity repair failed unexpectedly for fingerprint " + fingerprint,
                    failure);
            future.completeExceptionally(failure);
        } finally {
            clearActiveOperation(future);
        }
    }

    private static StorageIntegrityRepairExecution attemptRepairWithBarriers(
            BlockDataController controller, StorageIntegrityRepairPlan plan, String fingerprint, Path backupDirectory) {
        AtomicReference<StorageIntegrityRepairExecution> result = new AtomicReference<>();

        boolean readIdle = controller.runIfReadExecutorIdle(() -> {
            boolean writesIdle = controller.runIfAllWriteWorkIdle(() -> result.set(
                    executeRepairCriticalSection(controller, plan, fingerprint, backupDirectory)));
            if (!writesIdle) {
                result.set(execution(
                        StorageIntegrityRepairExecution.Status.STORAGE_BUSY,
                        fingerprint,
                        null,
                        "A database write was queued or executing when the final barrier was acquired.",
                        0));
            }
        });

        if (!readIdle) {
            return execution(
                    StorageIntegrityRepairExecution.Status.STORAGE_BUSY,
                    fingerprint,
                    null,
                    "A database read was queued or executing when the final barrier was acquired.",
                    0);
        }
        StorageIntegrityRepairExecution execution = result.get();
        return execution == null
                ? execution(
                        StorageIntegrityRepairExecution.Status.STORAGE_BUSY,
                        fingerprint,
                        null,
                        "The final storage barrier could not be acquired.",
                        0)
                : execution;
    }

    private static StorageIntegrityRepairExecution executeRepairCriticalSection(
            BlockDataController controller, StorageIntegrityRepairPlan plan, String fingerprint, Path backupDirectory) {
        if (controller.isDelayedSavingEnabled()) {
            return execution(
                    StorageIntegrityRepairExecution.Status.DELAYED_SAVING_ENABLED,
                    fingerprint,
                    null,
                    "Delayed writing became enabled before the final repair barrier.",
                    0);
        }
        if (controller.getPendingDelayedWriteTaskCount() != 0) {
            return execution(
                    StorageIntegrityRepairExecution.Status.STORAGE_BUSY,
                    fingerprint,
                    null,
                    "Deferred writes appeared before the final repair barrier.",
                    0);
        }

        ScanResult preDelete = scanBackend(controller);
        lastSnapshot = preDelete.snapshot();
        StorageIntegrityRepairPlan observedPlan = preDelete.candidates()
                .toRepairPlan(
                        preDelete.snapshot().getCompletedAtMillis(),
                        plan.getConfirmedAtMillis(),
                        preDelete.snapshot().getCompletedAtMillis());
        if (!preDelete.snapshot().wasStorageQuietAtBoundaries()
                || !observedPlan.getFingerprint().equals(fingerprint)) {
            CONFIRMATION_TRACKER.invalidate(preDelete.snapshot().getCompletedAtMillis());
            return execution(
                    StorageIntegrityRepairExecution.Status.CANDIDATE_SET_CHANGED,
                    fingerprint,
                    null,
                    "The exact orphan candidate set changed at the final mutation barrier.",
                    0);
        }

        int cachedCandidates = countCachedCandidateOwners(controller, plan);
        if (cachedCandidates > 0) {
            CONFIRMATION_TRACKER.invalidate(preDelete.snapshot().getCompletedAtMillis());
            return execution(
                    StorageIntegrityRepairExecution.Status.CACHED_CANDIDATE,
                    fingerprint,
                    null,
                    "One or more orphan owners are still represented by loaded Slimefun data. Repair was refused.",
                    cachedCandidates);
        }

        StorageIntegrityRepairBackup.BackupSnapshot backup;
        try {
            backup = StorageIntegrityRepairBackup.create(controller, plan, backupDirectory);
        } catch (IOException | RuntimeException failure) {
            CONFIRMATION_TRACKER.invalidate(System.currentTimeMillis());
            Slimefun.logger().log(
                    Level.SEVERE,
                    "Storage integrity repair backup failed for fingerprint " + fingerprint,
                    failure);
            return execution(
                    StorageIntegrityRepairExecution.Status.BACKUP_FAILED,
                    fingerprint,
                    null,
                    failure.getMessage(),
                    0);
        }

        try {
            deleteOwnerRows(controller, DataScope.BLOCK_DATA, FieldKey.LOCATION, plan.getBlockDataOwners());
            deleteOwnerRows(controller, DataScope.BLOCK_INVENTORY, FieldKey.LOCATION, plan.getBlockInventoryOwners());
            deleteOwnerRows(controller, DataScope.UNIVERSAL_DATA, FieldKey.UNIVERSAL_UUID, plan.getUniversalDataOwners());
            deleteOwnerRows(
                    controller,
                    DataScope.UNIVERSAL_INVENTORY,
                    FieldKey.UNIVERSAL_UUID,
                    plan.getUniversalInventoryOwners());

            if (plannedOwnersRemain(controller, plan)) {
                CONFIRMATION_TRACKER.invalidate(System.currentTimeMillis());
                Slimefun.logger().severe("Storage integrity repair left one or more targeted rows behind. Backup: "
                        + backup.path());
                return execution(
                        StorageIntegrityRepairExecution.Status.DELETE_FAILED,
                        fingerprint,
                        backup,
                        "At least one targeted secondary owner still exists after deletion.",
                        0);
            }

            ScanResult postDelete = scanBackend(controller);
            lastSnapshot = postDelete.snapshot();
            CONFIRMATION_TRACKER.invalidate(postDelete.snapshot().getCompletedAtMillis());

            Slimefun.logger().info("Storage integrity repair completed for fingerprint " + fingerprint + ". Removed "
                    + backup.totalRows() + " secondary row(s). Backup: " + backup.path());
            return execution(
                    StorageIntegrityRepairExecution.Status.REPAIRED,
                    fingerprint,
                    backup,
                    null,
                    0);
        } catch (Throwable failure) {
            CONFIRMATION_TRACKER.invalidate(System.currentTimeMillis());
            Slimefun.logger().log(
                    Level.SEVERE,
                    "Storage integrity repair may have partially modified secondary rows. Mandatory backup: "
                            + backup.path(),
                    failure);
            return execution(
                    StorageIntegrityRepairExecution.Status.DELETE_FAILED,
                    fingerprint,
                    backup,
                    failure.getMessage(),
                    0);
        }
    }

    private static int countCachedCandidateOwners(BlockDataController controller, StorageIntegrityRepairPlan plan) {
        Set<String> blockOwners = new HashSet<>(plan.getBlockDataOwners());
        blockOwners.addAll(plan.getBlockInventoryOwners());
        Set<String> universalOwners = new HashSet<>(plan.getUniversalDataOwners());
        universalOwners.addAll(plan.getUniversalInventoryOwners());
        Set<String> cached = new HashSet<>();

        controller.getAllLoadedChunkData().forEach(chunk -> chunk.getAllBlockData().forEach(data -> {
            if (blockOwners.contains(data.getKey())) {
                cached.add("block:" + data.getKey());
            }
        }));
        controller.getAllLoadedUniversalData().forEach(data -> {
            if (universalOwners.contains(data.getKey())) {
                cached.add("universal:" + data.getKey());
            }
        });
        return cached.size();
    }

    private static void deleteOwnerRows(
            BlockDataController controller, DataScope scope, FieldKey ownerField, List<String> owners) {
        for (String owner : owners) {
            RecordKey key = new RecordKey(scope);
            key.addCondition(ownerField, owner);
            controller.deleteData(key);
        }
    }

    private static boolean plannedOwnersRemain(BlockDataController controller, StorageIntegrityRepairPlan plan) {
        return intersects(readOwners(controller, DataScope.BLOCK_DATA, FieldKey.LOCATION), plan.getBlockDataOwners())
                || intersects(
                        readOwners(controller, DataScope.BLOCK_INVENTORY, FieldKey.LOCATION),
                        plan.getBlockInventoryOwners())
                || intersects(
                        readOwners(controller, DataScope.UNIVERSAL_DATA, FieldKey.UNIVERSAL_UUID),
                        plan.getUniversalDataOwners())
                || intersects(
                        readOwners(controller, DataScope.UNIVERSAL_INVENTORY, FieldKey.UNIVERSAL_UUID),
                        plan.getUniversalInventoryOwners());
    }

    private static boolean intersects(Set<String> storedOwners, List<String> plannedOwners) {
        for (String owner : plannedOwners) {
            if (storedOwners.contains(owner)) {
                return true;
            }
        }
        return false;
    }

    private static StorageIntegrityRepairExecution execution(
            StorageIntegrityRepairExecution.Status status,
            String fingerprint,
            @Nullable StorageIntegrityRepairBackup.BackupSnapshot backup,
            @Nullable String detail,
            int cachedCandidates) {
        return new StorageIntegrityRepairExecution(
                status,
                fingerprint,
                backup == null ? null : backup.path(),
                backup == null ? 0 : backup.blockDataRows(),
                backup == null ? 0 : backup.blockInventoryRows(),
                backup == null ? 0 : backup.universalDataRows(),
                backup == null ? 0 : backup.universalInventoryRows(),
                cachedCandidates,
                detail);
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
