package io.github.thebusybiscuit.slimefun4.core.services.compatibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TestKnownAddonCompatibilityRegistry {

    @Test
    void testCommonRuntimeNamesAreRecognized() {
        KnownAddonCompatibilityRegistry registry =
                KnownAddonCompatibilityRegistry.load(getClass().getClassLoader());

        assertTrue(registry.find("Networks").isPresent());
        assertTrue(registry.find("InfinityExpansion2").isPresent());
        assertTrue(registry.find("MagicExpansion").isPresent());
        assertTrue(registry.find("FoxyMachines").isPresent());
        assertTrue(registry.find("NetworksExpansion").isPresent());
        assertTrue(registry.find("BetterChests").isPresent());
        assertFalse(registry.find("CompletelyUnknownAddon").isPresent());
    }

    @Test
    void testLegacyRequiredAliasWinsOverAdvisoryAlias() {
        KnownAddonCompatibilityRegistry registry =
                KnownAddonCompatibilityRegistry.load(getClass().getClassLoader());
        KnownAddonCompatibilityRegistry.KnownAddonSupport fastMachines =
                registry.find("FastMachines").orElseThrow();

        assertTrue(fastMachines.isRequired());
        assertEquals("legacy-fastmachines", fastMachines.slug());
    }
}
