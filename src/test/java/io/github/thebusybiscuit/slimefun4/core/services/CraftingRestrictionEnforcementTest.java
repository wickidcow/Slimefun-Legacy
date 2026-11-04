package io.github.thebusybiscuit.slimefun4.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

class CraftingRestrictionEnforcementTest {

    @TempDir
    Path pluginFolder;

    @Test
    void rejectsConfiguredAddonOutputButLeavesOtherItemsAndRewardsAlone() throws IOException {
        Slimefun plugin = mock(Slimefun.class);
        when(plugin.getDataFolder()).thenReturn(pluginFolder.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("CraftingRestrictionEnforcementTest"));
        Files.writeString(
                pluginFolder.resolve("CraftingRestrictions.yml"),
                """
                enabled: true
                default-message: "&cCrafting denied for %item% (%id%)"
                items:
                  FLIGHT_GEM:
                    crafting-enabled: false
                    message: "&6Reward only: %item%"
                """);

        CraftingRestrictionService service = new CraftingRestrictionService(plugin);
        assertTrue(service.reload());

        SlimefunItem addonGem = mock(SlimefunItem.class);
        when(addonGem.getId()).thenReturn("FLIGHT_GEM");
        when(addonGem.getItemName()).thenReturn("Flight Gem");

        ItemStack gemOutput = mock(ItemStack.class);
        ItemStack unrelatedOutput = mock(ItemStack.class);
        Player player = mock(Player.class);

        try (MockedStatic<SlimefunItem> registry = mockStatic(SlimefunItem.class)) {
            registry.when(() -> SlimefunItem.getByItem(gemOutput)).thenReturn(addonGem);

            assertTrue(service.isBlocked(gemOutput));
            assertFalse(service.isBlocked(unrelatedOutput));
            assertTrue(service.isBlockedAny(new ItemStack[] {unrelatedOutput, gemOutput}));
            assertFalse(service.isBlockedAny(new ItemStack[] {unrelatedOutput}));
            assertTrue(service.denyIfBlocked(player, gemOutput));
            assertFalse(service.denyIfBlocked(player, unrelatedOutput));
        }

        ArgumentCaptor<Component> message = ArgumentCaptor.forClass(Component.class);
        verify(player).sendMessage(message.capture());
        assertEquals(
                "Reward only: Flight Gem",
                PlainTextComponentSerializer.plainText().serialize(message.getValue()));
    }

    @Test
    void keepsPreviouslyLoadedRestrictionsAfterMalformedReload() throws IOException {
        Slimefun plugin = mock(Slimefun.class);
        when(plugin.getDataFolder()).thenReturn(pluginFolder.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("CraftingRestrictionEnforcementTest"));

        Path policy = pluginFolder.resolve("CraftingRestrictions.yml");
        Files.writeString(
                policy,
                """
                items:
                  ADDON_ITEM:
                    crafting-enabled: false
                """);

        CraftingRestrictionService service = new CraftingRestrictionService(plugin);
        assertTrue(service.reload());
        assertTrue(service.isBlocked("ADDON_ITEM"));

        Files.writeString(policy, "items: [invalid]");
        assertFalse(service.reload());
        assertTrue(service.isBlocked("ADDON_ITEM"));
    }
}
