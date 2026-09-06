package io.github.thebusybiscuit.slimefun4.api.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TestStorageIntegritySnapshot {

    @Test
    void explicitEmptyWriteQueuesAreQuiet() {
        StorageIntegritySnapshot snapshot = snapshot(0, 0, 0, 0, true);

        assertTrue(snapshot.wasStorageQuietAtBoundaries());
        assertFalse(snapshot.hadPendingWritesDuringScan());
        assertFalse(snapshot.hadPendingDelayedWritesDuringScan());
    }

    @Test
    void activeWriteQueueMakesScanNoisy() {
        StorageIntegritySnapshot snapshot = snapshot(1, 0, 0, 0, false);

        assertFalse(snapshot.wasStorageQuietAtBoundaries());
        assertTrue(snapshot.hadPendingWritesDuringScan());
    }

    @Test
    void unknownDelayedQueueFailsClosed() {
        StorageIntegritySnapshot snapshot = snapshot(0, 0, -1, -1, true);

        assertFalse(snapshot.wasStorageQuietAtBoundaries());
        assertTrue(snapshot.hadPendingDelayedWritesDuringScan());
    }

    @Test
    void legacyConstructorTreatsEnabledDelayedSavingAsUnknown() {
        StorageIntegritySnapshot snapshot = new StorageIntegritySnapshot(
                1L,
                2L,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                true,
                List.of(),
                List.of(),
                List.of(),
                List.of());

        assertEquals(-1, snapshot.getPendingDelayedWritesAtStart());
        assertEquals(-1, snapshot.getPendingDelayedWritesAtEnd());
        assertFalse(snapshot.wasStorageQuietAtBoundaries());
    }

    private StorageIntegritySnapshot snapshot(
            int pendingStart, int pendingEnd, int delayedStart, int delayedEnd, boolean delayedSavingEnabled) {
        return new StorageIntegritySnapshot(
                1L,
                2L,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                pendingStart,
                pendingEnd,
                delayedStart,
                delayedEnd,
                delayedSavingEnabled,
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }
}
