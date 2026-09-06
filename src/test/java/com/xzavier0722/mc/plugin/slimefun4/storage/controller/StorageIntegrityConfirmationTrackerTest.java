package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class StorageIntegrityConfirmationTrackerTest {

    private static final long START = 1_000_000L;

    @Test
    void firstQuietScanStartsPassOne() {
        var tracker = new StorageIntegrityConfirmationTracker();

        var status = tracker.record(candidates("block-a"), true, START);

        assertEquals(1, status.getQuietPasses());
        assertFalse(status.isConfirmed());
        assertTrue(status.wasLastScanQuiet());
        assertEquals(1, status.getCandidateOwners());
        assertEquals(StorageIntegrityConfirmationTracker.MIN_CONFIRMATION_INTERVAL_MILLIS,
                status.getRemainingWaitMillis(START));
    }

    @Test
    void matchingScanTooSoonDoesNotConfirm() {
        var tracker = new StorageIntegrityConfirmationTracker();
        var candidates = candidates("block-a");
        tracker.record(candidates, true, START);

        var status = tracker.record(
                candidates,
                true,
                START + StorageIntegrityConfirmationTracker.MIN_CONFIRMATION_INTERVAL_MILLIS - 1L);

        assertEquals(1, status.getQuietPasses());
        assertFalse(status.isConfirmed());
        assertEquals(1L, status.getRemainingWaitMillis(status.getLastScanCompletedAtMillis()));
    }

    @Test
    void secondMatchingQuietScanAfterIntervalConfirms() {
        var tracker = new StorageIntegrityConfirmationTracker();
        var candidates = candidates("block-a");
        tracker.record(candidates, true, START);

        var status = tracker.record(
                candidates,
                true,
                START + StorageIntegrityConfirmationTracker.MIN_CONFIRMATION_INTERVAL_MILLIS);

        assertEquals(2, status.getQuietPasses());
        assertTrue(status.isConfirmed());
        assertEquals(0L, status.getRemainingWaitMillis(Long.MAX_VALUE));
    }

    @Test
    void exactCandidateChangeRestartsPassOneEvenWhenCountsMatch() {
        var tracker = new StorageIntegrityConfirmationTracker();
        tracker.record(candidates("block-a"), true, START);

        var status = tracker.record(
                candidates("block-b"),
                true,
                START + StorageIntegrityConfirmationTracker.MIN_CONFIRMATION_INTERVAL_MILLIS);

        assertEquals(1, status.getQuietPasses());
        assertFalse(status.isConfirmed());
        assertTrue(status.didCandidateSetChange());
        assertEquals(1, status.getCandidateOwners());
        assertEquals(START + StorageIntegrityConfirmationTracker.MIN_CONFIRMATION_INTERVAL_MILLIS,
                status.getFirstPassCompletedAtMillis());
    }

    @Test
    void noisyScanInvalidatesPriorConfirmation() {
        var tracker = new StorageIntegrityConfirmationTracker();
        var candidates = candidates("block-a");
        tracker.record(candidates, true, START);
        tracker.record(
                candidates,
                true,
                START + StorageIntegrityConfirmationTracker.MIN_CONFIRMATION_INTERVAL_MILLIS);

        var status = tracker.record(candidates, false, START + 60_000L);

        assertEquals(0, status.getQuietPasses());
        assertFalse(status.isConfirmed());
        assertFalse(status.wasLastScanQuiet());
        assertFalse(status.hasQuietBaseline());
    }

    private StorageIntegrityConfirmationTracker.CandidateSet candidates(String blockDataOwner) {
        return new StorageIntegrityConfirmationTracker.CandidateSet(
                Set.of(blockDataOwner), Set.of(), Set.of(), Set.of());
    }
}
