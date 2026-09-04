package io.github.thebusybiscuit.slimefun4.implementation.listeners;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class MultiBlockListenerTest {

    @Test
    void prefersSpecificAddonStructureOverGenericGrindStone() {
        Material[] grindStone = {
            null,
            null,
            null,
            null,
            Material.OAK_FENCE,
            null,
            null,
            Material.DISPENSER,
            null
        };
        Material[] fnAssemblyStation = {
            null,
            null,
            null,
            null,
            Material.ACACIA_FENCE,
            null,
            Material.CRAFTING_TABLE,
            Material.DISPENSER,
            Material.CRAFTING_TABLE
        };

        assertTrue(MultiBlockListener.isAtLeastAsSpecific(fnAssemblyStation, grindStone));
        assertFalse(MultiBlockListener.isAtLeastAsSpecific(grindStone, fnAssemblyStation));
    }

    @Test
    void keepsLaterRegistrationPrecedenceForEqualSpecificity() {
        Material[] firstMatch = {
            null,
            null,
            null,
            null,
            Material.OAK_FENCE,
            null,
            Material.CRAFTING_TABLE,
            Material.DISPENSER,
            null
        };
        Material[] laterMatch = {
            null,
            null,
            null,
            null,
            Material.BIRCH_FENCE,
            null,
            Material.ANVIL,
            Material.DISPENSER,
            null
        };

        assertTrue(MultiBlockListener.isAtLeastAsSpecific(laterMatch, firstMatch));
    }
}
