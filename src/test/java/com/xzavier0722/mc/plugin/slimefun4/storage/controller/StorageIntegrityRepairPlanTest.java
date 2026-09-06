package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StorageIntegrityRepairPlanTest {

    private static final long START = 1_000_000L;

    @Test
    void planIsUnavailableUntilExactTwoPassConfirmation() {
        var tracker = new StorageIntegrityConfirmationTracker();
        var candidates = candidates();

        tracker.record(candidates, true, START);
        assertNull(tracker.createRepairPlan(START + 1L));

        tracker.record(
                candidates,
                true,
                START + StorageIntegrityConfirmationTracker.MIN_CONFIRMATION_INTERVAL_MILLIS);

        StorageIntegrityRepairPlan plan = tracker.createRepairPlan(START + 40_000L);
        assertNotNull(plan);
        assertEquals(4, plan.getTotalCandidateReferences());
        assertEquals(START + StorageIntegrityConfirmationTracker.MIN_CONFIRMATION_INTERVAL_MILLIS,
                plan.getConfirmedAtMillis());
        assertFalse(plan.isEmpty());
    }

    @Test
    void planKeepsExactScopeQualifiedCandidatesSortedAndImmutable() {
        var tracker = new StorageIntegrityConfirmationTracker();
        var candidates = candidates();
        tracker.record(candidates, true, START);
        tracker.record(
                candidates,
                true,
                START + StorageIntegrityConfirmationTracker.MIN_CONFIRMATION_INTERVAL_MILLIS);

        StorageIntegrityRepairPlan plan = tracker.createRepairPlan(START + 40_000L);
        assertNotNull(plan);
        assertEquals(java.util.List.of("block-a", "block-z"), plan.getBlockDataOwners());
        assertEquals(java.util.List.of("block-inventory"), plan.getBlockInventoryOwners());
        assertEquals(java.util.List.of("universal-data"), plan.getUniversalDataOwners());
        assertTrue(plan.getUniversalInventoryOwners().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> plan.getBlockDataOwners().add("mutate"));
    }

    @Test
    void fingerprintIsStableForSameExactCandidateSet() {
        var tracker = new StorageIntegrityConfirmationTracker();
        var candidates = candidates();
        tracker.record(candidates, true, START);
        tracker.record(
                candidates,
                true,
                START + StorageIntegrityConfirmationTracker.MIN_CONFIRMATION_INTERVAL_MILLIS);

        StorageIntegrityRepairPlan first = tracker.createRepairPlan(START + 40_000L);
        StorageIntegrityRepairPlan second = tracker.createRepairPlan(START + 50_000L);
        assertNotNull(first);
        assertNotNull(second);
        assertEquals(first.getFingerprint(), second.getFingerprint());
        assertEquals(64, first.getFingerprint().length());
        assertEquals(12, first.getShortFingerprint().length());
    }

    @Test
    void noisyScanInvalidatesExistingPlan() {
        var tracker = new StorageIntegrityConfirmationTracker();
        var candidates = candidates();
        tracker.record(candidates, true, START);
        tracker.record(
                candidates,
                true,
                START + StorageIntegrityConfirmationTracker.MIN_CONFIRMATION_INTERVAL_MILLIS);
        assertNotNull(tracker.createRepairPlan(START + 40_000L));

        tracker.record(candidates, false, START + 50_000L);

        assertNull(tracker.createRepairPlan(START + 50_001L));
    }

    private StorageIntegrityConfirmationTracker.CandidateSet candidates() {
        Set<String> blockData = new LinkedHashSet<>();
        blockData.add("block-z");
        blockData.add("block-a");
        return new StorageIntegrityConfirmationTracker.CandidateSet(
                blockData, Set.of("block-inventory"), Set.of("universal-data"), Set.of());
    }
}
