package io.github.thebusybiscuit.slimefun4.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class CraftingRestrictionServiceTest {

    @Test
    void blocksOnlyExplicitlyDisabledItemIdsRegardlessOfCase() throws InvalidConfigurationException {
        CraftingRestrictionService.Snapshot policy = parse("""
                enabled: true
                default-message: "&cNo crafting %item% (%id%)"
                items:
                  FLIGHT_GEM:
                    crafting-enabled: false
                    message: "&6Reward only: %item%"
                  ADDON_ITEM:
                    crafting-enabled: false
                  ALLOWED_ITEM:
                    crafting-enabled: true
                """);

        assertTrue(policy.isBlocked("FLIGHT_GEM"));
        assertTrue(policy.isBlocked("flight_gem"));
        assertTrue(policy.isBlocked("addon_item"));
        assertFalse(policy.isBlocked("ALLOWED_ITEM"));
        assertFalse(policy.isBlocked("ANOTHER_ITEM"));
        assertFalse(policy.isBlocked(null));
        assertEquals("&6Reward only: Flight Gem", policy.denialMessage("FLIGHT_GEM", "Flight Gem"));
        assertEquals("&cNo crafting Addon Item (ADDON_ITEM)", policy.denialMessage("ADDON_ITEM", "Addon Item"));
    }

    @Test
    void globalSwitchDisablesAllRestrictions() throws InvalidConfigurationException {
        CraftingRestrictionService.Snapshot policy = parse("""
                enabled: false
                items:
                  FLIGHT_GEM:
                    crafting-enabled: false
                """);

        assertFalse(policy.isBlocked("FLIGHT_GEM"));
    }

    @Test
    void malformedItemDefinitionsAreRejectedInsteadOfSilentlyIgnored() {
        assertThrows(IllegalArgumentException.class, () -> parse("""
                items:
                  FLIGHT_GEM: false
                """));
        assertThrows(IllegalArgumentException.class, () -> parse("""
                items: [FLIGHT_GEM]
                """));
    }

    private static CraftingRestrictionService.Snapshot parse(String text) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return CraftingRestrictionService.parse(yaml);
    }
}
