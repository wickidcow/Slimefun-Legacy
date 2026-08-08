package io.github.thebusybiscuit.slimefun4.api.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TestWorldChunkRuntimeSnapshot {

    @Test
    void exposesAggregateLifecycleState() {
        var snapshot = new WorldChunkRuntimeSnapshot(3, 9, 5, 1, 2, 1, 20L, 11L, 4L, 1L, 2L, 3L);

        assertEquals(3, snapshot.getTrackedWorlds());
        assertEquals(9, snapshot.getTrackedChunks());
        assertEquals(5, snapshot.getReadyChunks());
        assertEquals(1, snapshot.getLoadingChunks());
        assertEquals(2, snapshot.getUnloadingChunks());
        assertEquals(1, snapshot.getFailedChunks());
        assertEquals(4, snapshot.getUnsafeChunks());
        assertEquals(20L, snapshot.getChunkLoadEvents());
        assertEquals(11L, snapshot.getChunkUnloadEvents());
        assertEquals(4L, snapshot.getWorldLoadEvents());
        assertEquals(1L, snapshot.getWorldUnloadEvents());
        assertEquals(2L, snapshot.getDeferredStorageLoads());
        assertEquals(3L, snapshot.getStorageLoadFailures());
    }
}
