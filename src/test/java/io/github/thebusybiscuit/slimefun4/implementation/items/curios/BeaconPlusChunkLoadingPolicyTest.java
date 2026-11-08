package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void yamlModeUpdatesPreserveAdjacentUserCommentsAndUnrelatedSettings() {
        String original = "  PoweredBeacon:\n"
                + "    chunk-loading-mode: online # keep this operator comment\n"
                + "    chunk-loading-enabled: true # historic setting\n"
                + "    # preserve this next line\n"
                + "    electric-operation:\n"
                + "      capacity: 4096\n";
        String updated = BeaconPlusChunkLoadingControl.rewriteMode(
                original, BeaconPlusChunkLoadingPolicy.OFF);
        assertTrue(updated.contains("chunk-loading-mode: OFF # keep this operator comment"));
        assertTrue(updated.contains("chunk-loading-enabled: false # historic setting"));
        assertTrue(updated.contains("    # preserve this next line\n    electric-operation:"));
        assertTrue(updated.contains("capacity: 4096"));
        String restored = BeaconPlusChunkLoadingControl.rewriteMode(
                updated, BeaconPlusChunkLoadingPolicy.ALWAYS);
        assertTrue(restored.contains("chunk-loading-mode: ALWAYS # keep this operator comment"));
        assertTrue(restored.contains("chunk-loading-enabled: true # historic setting"));
    }

    @Test
    void missingExplicitModeDoesNotRewriteLegacyConfig() {
        String oldConfig = "  PoweredBeacon:\n    chunk-loading-enabled: false\n";
        assertNull(BeaconPlusChunkLoadingControl.rewriteMode(
                oldConfig, BeaconPlusChunkLoadingPolicy.ALWAYS));
    }
}
