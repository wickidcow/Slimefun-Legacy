package io.github.thebusybiscuit.slimefun4.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CraftingRestrictionServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void adminChangesPersistAndTakeEffectWithoutRestart() throws IOException {
        Path file = tempDir.resolve("CraftingRestrictions.yml");
        Files.writeString(file, "enabled: true\nitems: {}\n");
        Slimefun plugin = mock(Slimefun.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("crafting-restriction-test"));
        CraftingRestrictionService service = new CraftingRestrictionService(plugin);

        assertTrue(service.reload());
        assertTrue(service.setCraftingEnabled("addon_item", false));
        assertTrue(service.isBlocked("ADDON_ITEM"));
        assertTrue(Files.readString(file).contains("crafting-enabled: false"));

        CraftingRestrictionService nextBoot = new CraftingRestrictionService(plugin);
        assertTrue(nextBoot.reload());
        assertTrue(nextBoot.isBlocked("addon_item"));
        assertTrue(nextBoot.setCraftingEnabled("ADDON_ITEM", true));
        assertFalse(nextBoot.isBlocked("addon_item"));
    }

    @Test
    void missingConfigurationIsGeneratedAndUpdated() throws IOException {
        Slimefun plugin = mock(Slimefun.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("crafting-restriction-test"));
        doAnswer(invocation -> {
                    Files.writeString(tempDir.resolve("CraftingRestrictions.yml"), "enabled: true\nitems: {}\n");
                    return null;
                })
                .when(plugin)
                .saveResource("CraftingRestrictions.yml", false);

        CraftingRestrictionService service = new CraftingRestrictionService(plugin);
        assertTrue(service.setCraftingEnabled("SOME_ADDON_ITEM", false));
        assertTrue(Files.isRegularFile(tempDir.resolve("CraftingRestrictions.yml")));
        assertTrue(service.isBlocked("some_addon_item"));
    }

    @Test
    void commandEditsExistingCaseInsensitiveYamlKey() throws IOException {
        Path file = tempDir.resolve("CraftingRestrictions.yml");
        Files.writeString(file, "enabled: true\\nitems:\\n  addon_item:\\n    crafting-enabled: false\n");
        Slimefun plugin = mock(Slimefun.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("crafting-restriction-test"));
        CraftingRestrictionService service = new CraftingRestrictionService(plugin);

        assertTrue(service.reload());
        assertTrue(service.isBlocked("ADDON_ITEM"));
        assertTrue(service.setCraftingEnabled("ADDON_ITEM", true));
        assertFalse(service.isBlocked("addon_item"));
        assertFalse(Files.readString(file).contains("ADDON_ITEM:"));
    }

    @Test
    void invalidConfigurationIsNotOverwritten() throws IOException {
        Path file = tempDir.resolve("CraftingRestrictions.yml");
        String invalid = "items: [broken]\n";
        Files.writeString(file, invalid);
        Slimefun plugin = mock(Slimefun.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("crafting-restriction-test"));
        CraftingRestrictionService service = new CraftingRestrictionService(plugin);

        assertFalse(service.setCraftingEnabled("FLIGHT_GEM", false));
        assertEquals(invalid, Files.readString(file));
        assertFalse(service.isBlocked("FLIGHT_GEM"));
    }

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
