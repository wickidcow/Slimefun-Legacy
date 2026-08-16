package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BeaconPlusPowerModeTest {

    @Test
    void legacyAndUnknownBlocksDefaultToSlimefunElectricity() {
        assertEquals(BeaconPlusPowerMode.SLIMEFUN_ENERGY, BeaconPlusPowerMode.fromStored(null));
        assertEquals(BeaconPlusPowerMode.SLIMEFUN_ENERGY, BeaconPlusPowerMode.fromStored(""));
        assertEquals(BeaconPlusPowerMode.SLIMEFUN_ENERGY, BeaconPlusPowerMode.fromStored("not-a-real-mode"));
    }

    @Test
    void persistedAliasesResolveToExpectedPowerSources() {
        assertEquals(BeaconPlusPowerMode.SLIMEFUN_ENERGY, BeaconPlusPowerMode.fromStored("electricity"));
        assertEquals(BeaconPlusPowerMode.SLIMEFUN_ENERGY, BeaconPlusPowerMode.fromStored("slimefun energy"));
        assertEquals(BeaconPlusPowerMode.BEACON_BLOCKS, BeaconPlusPowerMode.fromStored("beacon blocks"));
        assertEquals(BeaconPlusPowerMode.BEACON_BLOCKS, BeaconPlusPowerMode.fromStored("vanilla"));
        assertEquals(BeaconPlusPowerMode.BEACON_BLOCKS, BeaconPlusPowerMode.fromStored("pyramid"));
    }

    @Test
    void powerSourceToggleIsTwoWay() {
        assertEquals(BeaconPlusPowerMode.BEACON_BLOCKS, BeaconPlusPowerMode.SLIMEFUN_ENERGY.next());
        assertEquals(BeaconPlusPowerMode.SLIMEFUN_ENERGY, BeaconPlusPowerMode.BEACON_BLOCKS.next());
    }
}
