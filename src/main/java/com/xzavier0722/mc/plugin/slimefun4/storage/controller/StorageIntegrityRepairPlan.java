package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Immutable, read-only repair plan derived from a confirmed two-pass storage-integrity candidate set.
 *
 * <p>The plan contains owner keys only. It does not authorize repair, open a transaction, delete rows, or mutate any
 * storage state. A future repair implementation must perform fresh validation before acting on a plan.
 */
public final class StorageIntegrityRepairPlan {

    private final long generatedAtMillis;
    private final long confirmedAtMillis;
    private final long sourceScanCompletedAtMillis;
    private final List<String> blockDataOwners;
    private final List<String> blockInventoryOwners;
    private final List<String> universalDataOwners;
    private final List<String> universalInventoryOwners;
    private final String fingerprint;

    StorageIntegrityRepairPlan(
            long generatedAtMillis,
            long confirmedAtMillis,
            long sourceScanCompletedAtMillis,
            Set<String> blockDataOwners,
            Set<String> blockInventoryOwners,
            Set<String> universalDataOwners,
            Set<String> universalInventoryOwners) {
        this.generatedAtMillis = generatedAtMillis;
        this.confirmedAtMillis = confirmedAtMillis;
        this.sourceScanCompletedAtMillis = sourceScanCompletedAtMillis;
        this.blockDataOwners = sortedCopy(blockDataOwners);
        this.blockInventoryOwners = sortedCopy(blockInventoryOwners);
        this.universalDataOwners = sortedCopy(universalDataOwners);
        this.universalInventoryOwners = sortedCopy(universalInventoryOwners);
        this.fingerprint = createFingerprint();
    }

    public long getGeneratedAtMillis() {
        return generatedAtMillis;
    }

    public long getConfirmedAtMillis() {
        return confirmedAtMillis;
    }

    public long getSourceScanCompletedAtMillis() {
        return sourceScanCompletedAtMillis;
    }

    public @Nonnull List<String> getBlockDataOwners() {
        return blockDataOwners;
    }

    public @Nonnull List<String> getBlockInventoryOwners() {
        return blockInventoryOwners;
    }

    public @Nonnull List<String> getUniversalDataOwners() {
        return universalDataOwners;
    }

    public @Nonnull List<String> getUniversalInventoryOwners() {
        return universalInventoryOwners;
    }

    public int getBlockDataOwnerCount() {
        return blockDataOwners.size();
    }

    public int getBlockInventoryOwnerCount() {
        return blockInventoryOwners.size();
    }

    public int getUniversalDataOwnerCount() {
        return universalDataOwners.size();
    }

    public int getUniversalInventoryOwnerCount() {
        return universalInventoryOwners.size();
    }

    public int getTotalCandidateReferences() {
        return blockDataOwners.size()
                + blockInventoryOwners.size()
                + universalDataOwners.size()
                + universalInventoryOwners.size();
    }

    public boolean isEmpty() {
        return getTotalCandidateReferences() == 0;
    }

    /**
     * Stable SHA-256 fingerprint of the exact, scope-qualified candidate owner set.
     *
     * <p>Generation time is deliberately excluded, so repeatedly rendering the same confirmed plan produces the same
     * fingerprint.
     */
    public @Nonnull String getFingerprint() {
        return fingerprint;
    }

    public @Nonnull String getShortFingerprint() {
        return fingerprint.substring(0, 12);
    }

    private List<String> sortedCopy(Set<String> values) {
        List<String> sorted = new ArrayList<>(values);
        sorted.sort(String::compareTo);
        return List.copyOf(sorted);
    }

    private String createFingerprint() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateDigest(digest, "BLOCK_DATA", blockDataOwners);
            updateDigest(digest, "BLOCK_INVENTORY", blockInventoryOwners);
            updateDigest(digest, "UNIVERSAL_DATA", universalDataOwners);
            updateDigest(digest, "UNIVERSAL_INVENTORY", universalInventoryOwners);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private void updateDigest(MessageDigest digest, String scope, List<String> owners) {
        digest.update(scope.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        for (String owner : owners) {
            digest.update(owner.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) '\n');
        }
        digest.update((byte) 0);
    }
}
