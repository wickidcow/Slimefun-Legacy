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
        assertTrue(registry.find("DankTech2").isPresent());
        assertTrue(registry.find("Cultivation").isPresent());
        assertTrue(registry.find("ElectricSpawners").isPresent());
        assertTrue(registry.find("ExtraTools").isPresent());
        assertTrue(registry.find("GeneticChickengineering-Reborn").isPresent());
        assertTrue(registry.find("HotbarPets").isPresent());
        assertTrue(registry.find("MagicBall 8").isPresent());
        assertTrue(registry.find("MobCapturer").isPresent());
        assertTrue(registry.find("SFMobDrops").isPresent());
        assertTrue(registry.find("SlimefunAdvancements").isPresent());
        assertTrue(registry.find("SlimeGlue").isPresent());
        assertTrue(registry.find("SimpleMaterialGenerators").isPresent());
        assertTrue(registry.find("SoulJars").isPresent());
        assertTrue(registry.find("BetterFarming").isPresent());
        assertFalse(registry.find("CompletelyUnknownAddon").isPresent());
    }

    @Test
    void testRecognitionOnlyDoesNotClaimCiCoverage() {
        KnownAddonCompatibilityRegistry registry =
                KnownAddonCompatibilityRegistry.load(getClass().getClassLoader());
        KnownAddonCompatibilityRegistry.KnownAddonSupport dankTech =
                registry.find("DankTech2").orElseThrow();

        assertTrue(dankTech.isRecognizedOnly());
        assertFalse(dankTech.isCiMonitored());
        assertEquals("danktech2", dankTech.slug());
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
