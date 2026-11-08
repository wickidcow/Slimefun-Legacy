package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Runtime ticket rules must never rewrite per-beacon selections or world data. */
class BeaconPlusChunkLoadingPolicyTest {

    @Test
    void defaultAndLegacyEnabledUseOnlineOnly() {
        assertEquals(BeaconPlusChunkLoadingPolicy.ONLINE,
                BeaconPlusChunkLoadingPolicy.fromConfig(null, true));
        assertEquals(BeaconPlusChunkLoadingPolicy.ONLINE,
                BeaconPlusChunkLoadingPolicy.fromConfig(" ", true));
        assertFalse(BeaconPlusChunkLoadingPolicy.ONLINE.shouldHoldTickets(false));
        assertTrue(BeaconPlusChunkLoadingPolicy.ONLINE.shouldHoldTickets(true));
    }

    @Test
    void historicalDisabledChoiceRemainsOff() {
        assertEquals(BeaconPlusChunkLoadingPolicy.OFF,
                BeaconPlusChunkLoadingPolicy.fromConfig(null, false));
        assertFalse(BeaconPlusChunkLoadingPolicy.OFF.shouldHoldTickets(true));
        assertFalse(BeaconPlusChunkLoadingPolicy.OFF.shouldHoldTickets(false));
    }

    @Test
    void explicitAlwaysHoldsTicketsWithoutPlayers() {
        assertEquals(BeaconPlusChunkLoadingPolicy.ALWAYS,
                BeaconPlusChunkLoadingPolicy.fromConfig(" always ", false));
        assertTrue(BeaconPlusChunkLoadingPolicy.ALWAYS.shouldHoldTickets(false));
        assertTrue(BeaconPlusChunkLoadingPolicy.ALWAYS.shouldHoldTickets(true));
    }

    @Test
    void invalidValuesFailClosedWithoutAnOfflineTicket() {
        assertEquals(BeaconPlusChunkLoadingPolicy.OFF,
                BeaconPlusChunkLoadingPolicy.fromConfig("true", true));
        assertEquals(BeaconPlusChunkLoadingPolicy.OFF,
                BeaconPlusChunkLoadingPolicy.fromConfig("whatever", true));
    }

    @Test
    void explicitModeOverridesLegacyBoolean() {
        assertEquals(BeaconPlusChunkLoadingPolicy.ONLINE,
                BeaconPlusChunkLoadingPolicy.fromConfig("ONLINE", false));
        assertEquals(BeaconPlusChunkLoadingPolicy.OFF,
                BeaconPlusChunkLoadingPolicy.fromConfig("off", true));
    }
}
