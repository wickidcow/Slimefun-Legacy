package io.github.thebusybiscuit.slimefun4.api.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TestBlockDataRuntimeSnapshot {

    @Test
    void exposesLoadedStorageDiagnostics() {
        var snapshot = new BlockDataRuntimeSnapshot(true, 12, 87, 2, 7, 3, 2, 30L, 4L, 1L, 123L, "boom");

        assertTrue(snapshot.isStorageReady());
        assertEquals(12, snapshot.getLoadedChunkRecords());
        assertEquals(87, snapshot.getLoadedBlockRecords());
        assertEquals(2, snapshot.getUnknownSlimefunIds());
        assertEquals(7, snapshot.getReadyLifecycleChunks());
        assertEquals(3, snapshot.getUnsafeLifecycleChunks());
        assertEquals(2, snapshot.getUntrackedLifecycleChunks());
        assertEquals(30L, snapshot.getChunkLoadAttempts());
        assertEquals(4L, snapshot.getDeferredChunkLoads());
        assertEquals(1L, snapshot.getChunkLoadFailures());
        assertEquals("7 ready / 3 unsafe / 2 untracked", snapshot.getLifecycleSummary());
        assertEquals("boom", snapshot.getLastFailureMessage());
    }
}
