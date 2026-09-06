package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.FieldKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordKey;
import io.github.thebusybiscuit.slimefun4.api.storage.StorageIntegrityConfirmationSnapshot;
import io.github.thebusybiscuit.slimefun4.api.storage.StorageIntegritySnapshot;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
    private static volatile CompletableFuture<StorageIntegritySnapshot> activeScan;
    private static volatile StorageIntegritySnapshot lastSnapshot;

    private StorageIntegrityScanner() {}

    /**
     * Starts a storage integrity scan on the controller's existing read executor.
     *
     * @param controller active block data controller
     * @return the new scan future, or {@code null} if another scan is already active
     */
    public static @Nullable CompletableFuture<StorageIntegritySnapshot> startScan(@Nonnull BlockDataController controller) {
        CompletableFuture<StorageIntegritySnapshot> future;
        synchronized (SCAN_LOCK) {
            if (activeScan != null && !activeScan.isDone()) {
                return null;
            }

            future = new CompletableFuture<>();
            activeScan = future;
        }

        try {
            controller.scheduleReadTask(() -> runScan(controller, future));
        } catch (RuntimeException failure) {
            CONFIRMATION_TRACKER.invalidate(System.currentTimeMillis());
            future.completeExceptionally(failure);
            clearActiveScan(future);
        }
        return future;
    }

    public static boolean isScanRunning() {
        CompletableFuture<StorageIntegritySnapshot> current = activeScan;
        return current != null && !current.isDone();
    }

    public static @Nullable StorageIntegritySnapshot getLastSnapshot() {
        return lastSnapshot;
    }

    public static @Nonnull StorageIntegrityConfirmationSnapshot getConfirmationSnapshot() {
        return CONFIRMATION_TRACKER.getSnapshot();
    }

    private static void runScan(BlockDataController controller, CompletableFuture<StorageIntegritySnapshot> future) {
        long startedAt = System.currentTimeMillis();
        int pendingWritesAtStart = controller.getPendingWriteTaskCount();
        int pendingDelayedWritesAtStart = controller.getPendingDelayedWriteTaskCount();
        boolean delayedSavingEnabled = controller.isDelayedSavingEnabled();

        try {
            Set<String> blockRecords = readOwners(controller, DataScope.BLOCK_RECORD, FieldKey.LOCATION);
            Set<String> blockDataOwners = readOwners(controller, DataScope.BLOCK_DATA, FieldKey.LOCATION);
            Set<String> blockInventoryOwners = readOwners(controller, DataScope.BLOCK_INVENTORY, FieldKey.LOCATION);
            Set<String> orphanBlockData = findOrphans(blockDataOwners, blockRecords);
            Set<String> orphanBlockInventories = findOrphans(blockInventoryOwners, blockRecords);

            Set<String> universalRecords =
                    readOwners(controller, DataScope.UNIVERSAL_RECORD, FieldKey.UNIVERSAL_UUID);
            Set<String> universalDataOwners =
                    readOwners(controller, DataScope.UNIVERSAL_DATA, FieldKey.UNIVERSAL_UUID);
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
            CONFIRMATION_TRACKER.record(candidates, snapshot.wasStorageQuietAtBoundaries(), completedAt);
            lastSnapshot = snapshot;
            future.complete(snapshot);
        } catch (Throwable failure) {
            CONFIRMATION_TRACKER.invalidate(System.currentTimeMillis());
            future.completeExceptionally(failure);
        } finally {
            clearActiveScan(future);
        }
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

    private static void clearActiveScan(CompletableFuture<StorageIntegritySnapshot> future) {
        synchronized (SCAN_LOCK) {
            if (activeScan == future) {
                activeScan = null;
            }
        }
    }
}
