package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.thebusybiscuit.slimefun4.api.storage.StorageIntegritySnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class StorageIntegrityRepairVerificationTest {

    private static final long COMPLETED = 1_000_000L;
    private static final String FINGERPRINT = "a".repeat(64);

    @Test
    void successfulVerificationIsShortLived() {
        StorageIntegritySnapshot scan = quietSnapshot();
        var verification = new StorageIntegrityRepairVerification(
                StorageIntegrityRepairVerification.Status.VERIFIED,
                FINGERPRINT,
                FINGERPRINT,
                scan,
                COMPLETED);

        assertTrue(verification.isVerified());
        assertTrue(verification.isCurrent(COMPLETED));
        assertTrue(verification.isCurrent(COMPLETED + StorageIntegrityRepairVerification.VALIDITY_MILLIS - 1L));
        assertFalse(verification.isCurrent(COMPLETED + StorageIntegrityRepairVerification.VALIDITY_MILLIS));
        assertEquals(
                StorageIntegrityRepairVerification.VALIDITY_MILLIS,
                verification.getRemainingValidityMillis(COMPLETED));
        assertEquals(0L, verification.getRemainingValidityMillis(
                COMPLETED + StorageIntegrityRepairVerification.VALIDITY_MILLIS));
        assertSame(scan, verification.getVerificationScan());
    }

    @Test
    void failedVerificationNeverOpensTheGate() {
        var verification = new StorageIntegrityRepairVerification(
                StorageIntegrityRepairVerification.Status.CANDIDATE_SET_CHANGED,
                FINGERPRINT,
                "b".repeat(64),
                quietSnapshot(),
                COMPLETED);

        assertFalse(verification.isVerified());
        assertFalse(verification.isCurrent(COMPLETED));
        assertEquals(0L, verification.getRemainingValidityMillis(COMPLETED));
        assertEquals(StorageIntegrityRepairVerification.Status.CANDIDATE_SET_CHANGED, verification.getStatus());
    }

    @Test
    void rejectedFingerprintRetainsExpectedAndObservedValues() {
        String observed = "c".repeat(64);
        var verification = new StorageIntegrityRepairVerification(
                StorageIntegrityRepairVerification.Status.FINGERPRINT_REJECTED,
                FINGERPRINT,
                observed,
                null,
                COMPLETED);

        assertEquals(FINGERPRINT, verification.getExpectedFingerprint());
        assertEquals(observed, verification.getObservedFingerprint());
        assertFalse(verification.isCurrent(Long.MAX_VALUE));
    }

    private StorageIntegritySnapshot quietSnapshot() {
        return new StorageIntegritySnapshot(
                COMPLETED - 10L,
                COMPLETED,
                1,
                1,
                1,
                1,
                0,
                1,
                1,
                1,
                0,
                0,
                0,
                0,
                0,
                0,
                true,
                List.of("block-owner"),
                List.of(),
                List.of(),
                List.of());
    }
}
