package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import java.nio.file.Path;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Immutable result of an explicit orphan-secondary storage repair attempt.
 *
 * <p>A successful execution means only secondary rows represented by the verified plan were removed. Primary block and
 * universal records are never repair targets.
 */
public final class StorageIntegrityRepairExecution {

    public enum Status {
        REPAIRED,
        VERIFICATION_REQUIRED,
        EMPTY_PLAN,
        DELAYED_SAVING_ENABLED,
        STORAGE_BUSY,
        CACHED_CANDIDATE,
        CANDIDATE_SET_CHANGED,
        BACKUP_FAILED,
        DELETE_FAILED
    }

    private final Status status;
    private final String fingerprint;
    private final Path backupPath;
    private final int blockDataRows;
    private final int blockInventoryRows;
    private final int universalDataRows;
    private final int universalInventoryRows;
    private final int cachedCandidateOwners;
    private final String detail;

    StorageIntegrityRepairExecution(
            @Nonnull Status status,
            @Nonnull String fingerprint,
            @Nullable Path backupPath,
            int blockDataRows,
            int blockInventoryRows,
            int universalDataRows,
            int universalInventoryRows,
            int cachedCandidateOwners,
            @Nullable String detail) {
        this.status = status;
        this.fingerprint = fingerprint;
        this.backupPath = backupPath;
        this.blockDataRows = blockDataRows;
        this.blockInventoryRows = blockInventoryRows;
        this.universalDataRows = universalDataRows;
        this.universalInventoryRows = universalInventoryRows;
        this.cachedCandidateOwners = cachedCandidateOwners;
        this.detail = detail;
    }

    public @Nonnull Status getStatus() {
        return status;
    }

    public boolean isRepaired() {
        return status == Status.REPAIRED;
    }

    public @Nonnull String getFingerprint() {
        return fingerprint;
    }

    public @Nullable Path getBackupPath() {
        return backupPath;
    }

    public int getBlockDataRows() {
        return blockDataRows;
    }

    public int getBlockInventoryRows() {
        return blockInventoryRows;
    }

    public int getUniversalDataRows() {
        return universalDataRows;
    }

    public int getUniversalInventoryRows() {
        return universalInventoryRows;
    }

    public int getTotalRows() {
        return blockDataRows + blockInventoryRows + universalDataRows + universalInventoryRows;
    }

    public int getCachedCandidateOwners() {
        return cachedCandidateOwners;
    }

    public @Nullable String getDetail() {
        return detail;
    }
}
