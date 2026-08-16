package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CuriosExpeditionUtilityTest {

    @Test
    void wayfarerAndBastionSearchesStayBounded() {
        assertEquals(4_096, WayfarersLodestone.SEARCH_RADIUS_BLOCKS);
        assertEquals(10_000, WayfarersLodestone.RANDOM_PROBE_RADIUS_BLOCKS);
        assertEquals(3, WayfarersLodestone.MAX_SEARCH_PROBES);
        assertEquals(180_000L, WayfarersLodestone.COOLDOWN_MILLIS);
        assertEquals(128, BastionResonator.SEARCH_RADIUS_CHUNKS);
        assertEquals(30_000L, BastionResonator.COOLDOWN_MILLIS);
    }

    @Test
    void flareModesCycleAndDefaultSafely() {
        assertEquals(EmergencyFlare.FlareMode.HELP, EmergencyFlare.FlareMode.fromStored(null));
        assertEquals(EmergencyFlare.FlareMode.RALLY, EmergencyFlare.FlareMode.HELP.next());
        assertEquals(EmergencyFlare.FlareMode.DANGER, EmergencyFlare.FlareMode.RALLY.next());
        assertEquals(EmergencyFlare.FlareMode.HELP, EmergencyFlare.FlareMode.DANGER.next());
        assertEquals(EmergencyFlare.FlareMode.HELP, EmergencyFlare.FlareMode.fromStored("not-real"));
    }

    @Test
    void chunkStabilityBandsAndArmorStandWeightAreProtected() {
        assertEquals(ChunkStabilizer.StabilityBand.STABLE, ChunkStabilizer.StabilityBand.fromScore(119));
        assertEquals(ChunkStabilizer.StabilityBand.BUSY, ChunkStabilizer.StabilityBand.fromScore(120));
        assertEquals(ChunkStabilizer.StabilityBand.HEAVY, ChunkStabilizer.StabilityBand.fromScore(300));
        assertEquals(ChunkStabilizer.StabilityBand.CRITICAL, ChunkStabilizer.StabilityBand.fromScore(800));

        int runawayArmorStandScore = ChunkStabilizer.calculateScore(5_000, 5_000, 0, 0, 0, 0, 0, 0);
        assertTrue(runawayArmorStandScore >= ChunkStabilizer.CRITICAL_SCORE);
    }
}
