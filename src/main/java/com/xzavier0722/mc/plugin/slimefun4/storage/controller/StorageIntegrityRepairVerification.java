package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import io.github.thebusybiscuit.slimefun4.api.storage.StorageIntegritySnapshot;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Immutable result of the final read-only fingerprint revalidation that precedes any future storage repair.
 *
 * <p>A verified result is deliberately short-lived and is not permission to mutate storage by itself. A destructive
 * repair implementation must still acquire the storage write/read safety gates and re-check the exact plan immediately
 * before changing any rows.
 */
public final class StorageIntegrityRepairVerification {

    public static final long VALIDITY_MILLIS = 60_000L;

    public enum Status {
        VERIFIED,
        FINGERPRINT_REJECTED,
        EMPTY_PLAN,
        STORAGE_NOT_QUIET,
        CANDIDATE_SET_CHANGED,
        CONFIRMATION_INVALIDATED
    }

    private final Status status;
    private final String expectedFingerprint;
    private final String observedFingerprint;
    private final StorageIntegritySnapshot verificationScan;
    private final long completedAtMillis;

    StorageIntegrityRepairVerification(
            @Nonnull Status status,
            @Nonnull String expectedFingerprint,
            @Nullable String observedFingerprint,
            @Nullable StorageIntegritySnapshot verificationScan,
            long completedAtMillis) {
        this.status = status;
        this.expectedFingerprint = expectedFingerprint;
        this.observedFingerprint = observedFingerprint;
        this.verificationScan = verificationScan;
        this.completedAtMillis = completedAtMillis;
    }

    public @Nonnull Status getStatus() {
        return status;
    }

    public boolean isVerified() {
        return status == Status.VERIFIED;
    }

    public @Nonnull String getExpectedFingerprint() {
        return expectedFingerprint;
    }

    public @Nullable String getObservedFingerprint() {
        return observedFingerprint;
    }

    public @Nullable StorageIntegritySnapshot getVerificationScan() {
        return verificationScan;
    }

    public long getCompletedAtMillis() {
        return completedAtMillis;
    }

    /**
     * Returns whether this successful verification remains inside its short runtime validity window.
     */
    public boolean isCurrent(long nowMillis) {
        return isVerified() && getRemainingValidityMillis(nowMillis) > 0L;
    }

    public long getRemainingValidityMillis(long nowMillis) {
        if (!isVerified()) {
            return 0L;
        }
        long expiresAt = completedAtMillis + VALIDITY_MILLIS;
        return Math.max(0L, expiresAt - nowMillis);
    }
}
