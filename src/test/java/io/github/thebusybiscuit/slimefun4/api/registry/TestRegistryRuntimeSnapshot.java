package io.github.thebusybiscuit.slimefun4.api.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TestRegistryRuntimeSnapshot {

    @Test
    void exposesReadOnlyRegistryHealth() {
        var snapshot = new RegistryRuntimeSnapshot(true, 123L, 100, 4, 104, 96, 8, 20, 11, 15, 6);

        assertTrue(snapshot.isInitialRegistrationFinalized());
        assertEquals(123L, snapshot.getFinalizedAtMillis());
        assertEquals(100, snapshot.getFinalizedItemCount());
        assertEquals(4, snapshot.getRuntimeRegisteredItems());
        assertEquals(104, snapshot.getTotalItems());
        assertEquals(96, snapshot.getEnabledItems());
        assertEquals(8, snapshot.getDisabledItems());
        assertEquals(20, snapshot.getItemGroups());
        assertEquals(11, snapshot.getResearches());
        assertEquals(15, snapshot.getTickerBlocks());
        assertEquals(6, snapshot.getRepresentedPlugins());
    }
}
