package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BeaconPlusChunkModeTest {

    @Test
    void legacyChunkLoaderNamesResolveToSingleChunkMode() {
        assertEquals(BeaconPlusChunkMode.SINGLE, BeaconPlusChunkMode.fromStored("KEEP_CHUNK_LOADED"));
        assertEquals(BeaconPlusChunkMode.SINGLE, BeaconPlusChunkMode.fromStored("CHUNK_ACTIVATOR"));
        assertEquals(BeaconPlusChunkMode.SINGLE, BeaconPlusChunkMode.fromStored("this chunk"));
        assertEquals(BeaconPlusChunkMode.SINGLE, BeaconPlusChunkMode.fromStored("local"));
    }

    @Test
    void areaAliasesResolveToBoundedThreeByThreeMode() {
        assertEquals(BeaconPlusChunkMode.AREA_3X3, BeaconPlusChunkMode.fromStored("area"));
        assertEquals(BeaconPlusChunkMode.AREA_3X3, BeaconPlusChunkMode.fromStored("3x3"));
        assertEquals(BeaconPlusChunkMode.AREA_3X3, BeaconPlusChunkMode.fromStored("area-3x3"));
    }

    @Test
    void chunkModeCycleRemainsBounded() {
        assertEquals(BeaconPlusChunkMode.SINGLE, BeaconPlusChunkMode.OFF.next());
        assertEquals(BeaconPlusChunkMode.AREA_3X3, BeaconPlusChunkMode.SINGLE.next());
        assertEquals(BeaconPlusChunkMode.OFF, BeaconPlusChunkMode.AREA_3X3.next());
    }

    @Test
    void unknownStoredModeFailsClosed() {
        assertEquals(BeaconPlusChunkMode.OFF, BeaconPlusChunkMode.fromStored("not-a-real-mode"));
        assertEquals(BeaconPlusChunkMode.OFF, BeaconPlusChunkMode.fromStored(null));
    }
}
