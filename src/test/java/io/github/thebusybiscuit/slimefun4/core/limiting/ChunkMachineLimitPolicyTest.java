package io.github.thebusybiscuit.slimefun4.core.limiting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChunkMachineLimitPolicyTest {

    @Test
    void disabledByDefaultNeverRejectsExistingOrNewMachinePlacements() {
        assertFalse(ChunkMachineLimitPolicy.rejects(false, true, false, 7_454, 256));
        assertFalse(ChunkMachineLimitPolicy.rejects(false, true, false, Integer.MAX_VALUE, 1));
    }

    @Test
    void admitsUpToLimitButRejectsMachineNumberLimitPlusOne() {
        assertFalse(ChunkMachineLimitPolicy.rejects(true, true, false, 255, 256));
        assertTrue(ChunkMachineLimitPolicy.rejects(true, true, false, 256, 256));
        assertTrue(ChunkMachineLimitPolicy.rejects(true, true, false, 8_000, 256));
    }

    @Test
    void neverCountsNonTickerItemsSuchAsDecorativeBlocks() {
        assertFalse(ChunkMachineLimitPolicy.rejects(true, false, false, 8_000, 256));
    }

    @Test
    void explicitOperatorBypassSkipsOnlyTheAdmissionCheck() {
        assertTrue(ChunkMachineLimitPolicy.rejects(true, true, false, 256, 256));
        assertFalse(ChunkMachineLimitPolicy.rejects(true, true, true, 8_000, 256));
    }

    @Test
    void invalidLimitsCannotAccidentallySwitchOffTheProtection() {
        assertEquals(256, ChunkMachineLimitPolicy.effectiveLimit(0));
        assertEquals(256, ChunkMachineLimitPolicy.effectiveLimit(-10));
        assertEquals(256, ChunkMachineLimitPolicy.effectiveLimit(Integer.MIN_VALUE));
        assertEquals(1, ChunkMachineLimitPolicy.effectiveLimit(1));
        assertEquals(2048, ChunkMachineLimitPolicy.effectiveLimit(2048));
        assertTrue(ChunkMachineLimitPolicy.rejects(true, true, false, 256, 0));
        assertFalse(ChunkMachineLimitPolicy.rejects(true, true, false, 255, 0));
    }
}
