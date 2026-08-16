package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BeaconPlusFieldAreaTest {

    @Test
    void missingAndUnknownAreaDefaultsToThreeByThree() {
        assertEquals(BeaconPlusFieldArea.AREA_3X3, BeaconPlusFieldArea.fromStored(null));
        assertEquals(BeaconPlusFieldArea.AREA_3X3, BeaconPlusFieldArea.fromStored(""));
        assertEquals(BeaconPlusFieldArea.AREA_3X3, BeaconPlusFieldArea.fromStored("not-real"));
    }

    @Test
    void areaAliasesAndCycleSupportOneThreeAndFiveByFive() {
        assertEquals(BeaconPlusFieldArea.CHUNK_1X1, BeaconPlusFieldArea.fromStored("1x1"));
        assertEquals(BeaconPlusFieldArea.AREA_3X3, BeaconPlusFieldArea.fromStored("3x3"));
        assertEquals(BeaconPlusFieldArea.AREA_5X5, BeaconPlusFieldArea.fromStored("5x5"));
        assertEquals(BeaconPlusFieldArea.AREA_3X3, BeaconPlusFieldArea.CHUNK_1X1.next());
        assertEquals(BeaconPlusFieldArea.AREA_5X5, BeaconPlusFieldArea.AREA_3X3.next());
        assertEquals(BeaconPlusFieldArea.CHUNK_1X1, BeaconPlusFieldArea.AREA_5X5.next());
    }

    @Test
    void extraRangeExpandsOneTierButNeverBeyondFiveByFive() {
        assertEquals(BeaconPlusFieldArea.AREA_3X3, BeaconPlusFieldArea.CHUNK_1X1.expand());
        assertEquals(BeaconPlusFieldArea.AREA_5X5, BeaconPlusFieldArea.AREA_3X3.expand());
        assertEquals(BeaconPlusFieldArea.AREA_5X5, BeaconPlusFieldArea.AREA_5X5.expand());
    }

    @Test
    void threeByThreeContainsAdjacentChunksButNotTwoChunksAway() {
        assertTrue(BeaconPlusFieldArea.AREA_3X3.containsChunk(0, 0, 16, 0));
        assertTrue(BeaconPlusFieldArea.AREA_3X3.containsChunk(0, 0, -16, 16));
        assertFalse(BeaconPlusFieldArea.AREA_3X3.containsChunk(0, 0, 32, 0));
    }
}
