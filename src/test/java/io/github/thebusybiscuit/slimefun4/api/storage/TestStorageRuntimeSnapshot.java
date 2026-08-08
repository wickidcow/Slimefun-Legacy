package io.github.thebusybiscuit.slimefun4.api.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TestStorageRuntimeSnapshot {

    @Test
    void exposesReadOnlyStorageHealth() {
        var snapshot = new StorageRuntimeSnapshot(true, true, 7, "SQLITE", "SQLITE", 11, 2);

        assertTrue(snapshot.isReady());
        assertTrue(snapshot.wasPreviousShutdownClean());
        assertEquals(7, snapshot.getPendingWrites());
        assertEquals("SQLITE", snapshot.getBlockStorageType());
        assertEquals("SQLITE", snapshot.getProfileStorageType());
        assertEquals(11, snapshot.getLoadedChunks());
        assertEquals(2, snapshot.getLoadedUniversalData());
    }
}
