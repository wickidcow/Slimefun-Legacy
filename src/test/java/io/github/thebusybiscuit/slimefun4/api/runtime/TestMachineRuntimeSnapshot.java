package io.github.thebusybiscuit.slimefun4.api.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class TestMachineRuntimeSnapshot {

    @Test
    void exposesRuntimeHealthWithoutMutableTickerState() {
        var snapshot = new MachineRuntimeSnapshot(false, false, 1, 4, 12, 2, 3, 8L, 5L);

        assertFalse(snapshot.isPaused());
        assertFalse(snapshot.isHalted());
        assertEquals(1, snapshot.getTickRate());
        assertEquals(4, snapshot.getTickingChunks());
        assertEquals(12, snapshot.getTickingLocations());
        assertEquals(2, snapshot.getPausedMachineCircuits());
        assertEquals(3, snapshot.getActiveMachineFailures());
        assertEquals(8L, snapshot.getObservedMachineFailures());
        assertEquals(5L, snapshot.getSuppressedMachineReports());
    }
}
