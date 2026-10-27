package io.github.thebusybiscuit.slimefun4.core.ticker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ChunkTickerBudgetPolicyTest {

    @Test
    void invalidOrUnsetConfiguredBudgetsUseTheDocumentedBound() {
        assertEquals(512, ChunkTickerBudgetPolicy.effectiveLimit(0));
        assertEquals(512, ChunkTickerBudgetPolicy.effectiveLimit(-1));
        assertEquals(512, ChunkTickerBudgetPolicy.effectiveLimit(Integer.MIN_VALUE));
        assertEquals(1, ChunkTickerBudgetPolicy.effectiveLimit(1));
        assertEquals(2048, ChunkTickerBudgetPolicy.effectiveLimit(2048));
    }

    @Test
    void realIndustrialChunkRequiresFourSlimefunCyclesForCompleteRotation() {
        assertEquals(4, ChunkTickerBudgetPolicy.cyclesToVisitAll(7454, 2048));
        assertEquals(15, ChunkTickerBudgetPolicy.cyclesToVisitAll(7454, 512));
        assertEquals(1, ChunkTickerBudgetPolicy.cyclesToVisitAll(512, 512));
        assertEquals(2, ChunkTickerBudgetPolicy.cyclesToVisitAll(513, 512));
    }

    @Test
    void unloadedEmptyAndCorruptBudgetsAreReportedSafely() {
        assertEquals(0, ChunkTickerBudgetPolicy.cyclesToVisitAll(0, 0));
        assertEquals(0, ChunkTickerBudgetPolicy.cyclesToVisitAll(-3, 2));
        assertEquals(1, ChunkTickerBudgetPolicy.cyclesToVisitAll(512, 0));
        assertEquals(2, ChunkTickerBudgetPolicy.cyclesToVisitAll(513, -1));
    }

    @Test
    void estimatesCannotOverflowEvenForExtremeRegistrationCounts() {
        assertEquals(Integer.MAX_VALUE, ChunkTickerBudgetPolicy.cyclesToVisitAll(Integer.MAX_VALUE, 1));
        assertEquals(1, ChunkTickerBudgetPolicy.cyclesToVisitAll(Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(2, ChunkTickerBudgetPolicy.cyclesToVisitAll(Integer.MAX_VALUE, Integer.MAX_VALUE - 1));
    }
}
