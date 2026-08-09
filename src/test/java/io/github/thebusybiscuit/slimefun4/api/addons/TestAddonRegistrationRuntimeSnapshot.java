package io.github.thebusybiscuit.slimefun4.api.addons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TestAddonRegistrationRuntimeSnapshot {

    @Test
    void testSnapshotValues() {
        AddonRegistrationRuntimeSnapshot snapshot = new AddonRegistrationRuntimeSnapshot(true, 10L, 2, 3L, 4L, 5L, 6);

        assertTrue(snapshot.isInitialRegistrationFinalized());
        assertEquals(10L, snapshot.getFinalizedAtMillis());
        assertEquals(2, snapshot.getPendingCallbacks());
        assertEquals(3L, snapshot.getExecutedCallbacks());
        assertEquals(4L, snapshot.getFailedCallbacks());
        assertEquals(5L, snapshot.getSkippedDisabledCallbacks());
        assertEquals(6, snapshot.getRuntimeRegisteredItems());
    }
}
