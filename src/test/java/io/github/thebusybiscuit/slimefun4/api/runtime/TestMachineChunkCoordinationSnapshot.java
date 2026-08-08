package io.github.thebusybiscuit.slimefun4.api.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TestMachineChunkCoordinationSnapshot {

    @Test
    void exposesTickerLifecycleCorrelation() {
        var snapshot = new MachineChunkCoordinationSnapshot(8, 20, 14, 2, 4);

        assertEquals(8, snapshot.getTickerChunks());
        assertEquals(20, snapshot.getTickerLocations());
        assertEquals(14, snapshot.getReadyLocations());
        assertEquals(2, snapshot.getUnsafeLocations());
        assertEquals(4, snapshot.getUntrackedLocations());
    }
}
