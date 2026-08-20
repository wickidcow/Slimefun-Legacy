package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BeaconPlusFieldTest {

    @Test
    void chunkRadiusTracksChunkAlignedFieldExpansion() {
        assertEquals(0, BeaconPlusField.chunkRadius(0.0D));
        assertEquals(0, BeaconPlusField.chunkRadius(16.0D));
        assertEquals(1, BeaconPlusField.chunkRadius(17.0D));
        assertEquals(1, BeaconPlusField.chunkRadius(32.0D));
        assertEquals(2, BeaconPlusField.chunkRadius(33.0D));
        assertEquals(0, BeaconPlusField.chunkRadius(Double.NaN));
    }

    @Test
    void threeByThreeFootprintContainsAdjacentChunksOnly() {
        BeaconPlusField.ChunkFootprint footprint = BeaconPlusField.footprint(0, 0, 32.0D);

        assertEquals(3, footprint.widthChunks());
        assertTrue(footprint.containsChunk(0, 0));
        assertTrue(footprint.containsChunk(1, -1));
        assertFalse(footprint.containsChunk(2, 0));
    }

    @Test
    void blockContainmentUsesTheSameChunkAlignedFootprint() {
        assertTrue(BeaconPlusField.contains(0, 0, 32.0D, 16, 0));
        assertTrue(BeaconPlusField.contains(0, 0, 32.0D, -16, 16));
        assertFalse(BeaconPlusField.contains(0, 0, 32.0D, 32, 0));
        assertFalse(BeaconPlusField.contains(0, 0, 0.0D, 0, 0));
    }
}
