package io.github.thebusybiscuit.slimefun4.core.services.compatibility;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TestLegacyMaintainedAddonCatalog {

    @Test
    void testMaintainedSfForkRuntimeNamesResolveAsCompatibleFallbacks() {
        KnownAddonCompatibilityRegistry registry =
                KnownAddonCompatibilityRegistry.load(getClass().getClassLoader());

        List<String> maintainedRuntimeNames = List.of(
                "BetterFarming",
                "ExtraTools",
                "FNAmplifications",
                "Galactifun",
                "HotbarPets",
                "Magic8Ball",
                "RykenSlimefunCustomizer",
                "SimpleMaterialGenerators",
                "SlimefunLuckyBlocks",
                "SoulJars",
                "WeaponsAddon");

        for (String pluginName : maintainedRuntimeNames) {
            KnownAddonCompatibilityRegistry.KnownAddonSupport support = registry.find(pluginName)
                    .orElseThrow(() -> new AssertionError("Missing maintained addon: " + pluginName));
            assertTrue(support.isLegacyMaintained(), () -> pluginName + " should be marked Legacy-maintained");
        }
    }

    @Test
    void testRepositoryAliasesResolveToMaintainedForks() {
        KnownAddonCompatibilityRegistry registry =
                KnownAddonCompatibilityRegistry.load(getClass().getClassLoader());

        List<String> maintainedRepositoryAliases = List.of(
                "SF_BetterFarming",
                "SF_ExtraTools",
                "SF_FNAmplifications",
                "SF_Galactifun",
                "SF_HotbarPets",
                "SF_Magic8Ball",
                "SF_MilitaryArsenal",
                "SF_RykenSlimeCustomizer",
                "SF_SMG",
                "SF_LuckyBlocks",
                "SF_SoulJars");

        for (String pluginName : maintainedRepositoryAliases) {
            KnownAddonCompatibilityRegistry.KnownAddonSupport support = registry.find(pluginName)
                    .orElseThrow(() -> new AssertionError("Missing maintained alias: " + pluginName));
            assertTrue(
                    support.isLegacyMaintained(), () -> pluginName + " should resolve to a Legacy-maintained fork");
        }
    }

    @Test
    void testUnrelatedPluginsRemainOutsideMaintainedCatalog() {
        KnownAddonCompatibilityRegistry registry =
                KnownAddonCompatibilityRegistry.load(getClass().getClassLoader());

        for (String pluginName :
                List.of("ItemsAdder", "ShopGUIPlus", "BetterStructures", "ODailyQuests", "Brewery", "zMenu")) {
            assertFalse(
                    registry.find(pluginName)
                            .map(KnownAddonCompatibilityRegistry.KnownAddonSupport::isLegacyMaintained)
                            .orElse(false),
                    () -> pluginName + " must not be treated as a Slimefun Legacy-maintained addon");
        }
    }
}
