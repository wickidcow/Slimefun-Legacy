package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class InventoryRecoveryLocationsTest {
    @Test
    void oneOwnersRecoveryDoesNotReleaseAnotherOwnerAtTheSamePosition() {
        var guard = new InventoryRecoveryLocations();
        guard.remember("first", "old;10:64:-20");
        guard.remember("second", "old;10:64:-20");
        guard.remember("first", "old;11:64:-20");
        guard.clear("first");
        assertTrue(guard.containsLocation("old;10:64:-20"));
        assertFalse(guard.containsLocation("old;11:64:-20"));
        guard.clear("second");
        assertTrue(guard.locationKeys().isEmpty());
    }

    @Test
    void repeatedAndUnknownObservationsDoNotEraseKnownLocations() {
        var guard = new InventoryRecoveryLocations();
        guard.remember("owner", "world;1:2:3");
        guard.remember("owner", "world;1:2:3");
        guard.remember("owner", null);
        guard.clear("another-owner");
        assertEquals(1, guard.locationKeys().size());
        var snapshot = guard.locationKeys();
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        guard.clear("owner");
        assertTrue(guard.locationKeys().isEmpty());
        assertEquals(1, snapshot.size());
    }

    @Test
    void recognizesExistingLocationFormatsWithoutWorldResolutionOrRewrite() {
        assertEquals(
                "unloaded_world;10:64:-21",
                InventoryRecoveryLocations.canonicalLocationKey("unloaded_world;10.9:64.1:-20.5"));
        assertEquals(
                "unloaded_world;10:64:-20",
                InventoryRecoveryLocations.canonicalLocationKey("[world=unloaded_world,x=10.9,y=64.1,z=-20.5]"));
        assertEquals("world;10:64:-20", InventoryRecoveryLocations.canonicalLocationKey("world;10:64:-20"));
        assertEquals("world;100:64:2", InventoryRecoveryLocations.canonicalLocationKey("world;1e2:64:2"));
    }

    @Test
    void malformedLocationsNeverAcquireInventedAliases() {
        assertNull(InventoryRecoveryLocations.canonicalLocationKey(null));
        for (String invalid : List.of(
                "",
                "world",
                ";1:2:3",
                "world;1:2",
                "world;1:2:3:4",
                "world;NaN:2:3",
                "world;Infinity:2:3",
                "world;1e50:2:3",
                "[world=w,x=1,z=2,y=3]",
                "[world=w,x=oops,y=2,z=3]")) {
            assertNull(InventoryRecoveryLocations.canonicalLocationKey(invalid), invalid);
        }
    }

    @Test
    void chunkChecksRespectNegativeBoundariesAndRejectMalformedRecoveryKeysConservatively() {
        assertTrue(InventoryRecoveryLocations.isInChunk("w;-1:64:-17", "w;", -1, -2));
        assertFalse(InventoryRecoveryLocations.isInChunk("w;-1:64:-17", "w;", 0, -2));
        assertTrue(InventoryRecoveryLocations.isInChunk("w;16:64:31", "w;", 1, 1));
        assertFalse(InventoryRecoveryLocations.isInChunk("w;16:64:31", "w;", 0, 1));
        assertTrue(InventoryRecoveryLocations.isInChunk("w;bad", "w;", 0, 0));
        assertTrue(InventoryRecoveryLocations.isInChunk("w;bad:64:1", "w;", 0, 0));
    }
}
