package io.github.thebusybiscuit.slimefun4.core.services.scheduling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TestSchedulerSnapshot {

    @Test
    void exposesSchedulerHealth() {
        var snapshot = new SchedulerSnapshot(true, 13, true);

        assertTrue(snapshot.isAcceptingTasks());
        assertEquals(13, snapshot.getActiveTaskCount());
        assertTrue(snapshot.isRegionOwnedExecution());
    }
}
