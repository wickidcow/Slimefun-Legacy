package io.github.thebusybiscuit.slimefun4.core.services.stability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TestMachineFailureSnapshot {

    @Test
    void reportsPausedStateAndRoundsRetrySecondsUp() {
        long now = 10_000L;
        var snapshot = new MachineFailureSnapshot(
                "world",
                1,
                64,
                -2,
                "TEST_MACHINE",
                "TestAddon",
                IllegalStateException.class.getName(),
                "boom",
                4,
                4L,
                3L,
                1_000L,
                now,
                now + 1_001L);

        assertTrue(snapshot.isPaused(now));
        assertEquals(2L, snapshot.getRetrySeconds(now));
        assertFalse(snapshot.isPaused(now + 1_001L));
        assertEquals(0L, snapshot.getRetrySeconds(now + 1_001L));
        assertEquals("TEST_MACHINE", snapshot.getItemId());
        assertEquals("TestAddon", snapshot.getAddonName());
    }
}
