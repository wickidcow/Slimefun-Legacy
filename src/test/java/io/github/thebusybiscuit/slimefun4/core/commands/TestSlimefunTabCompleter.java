package io.github.thebusybiscuit.slimefun4.core.commands;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.SlimefunRegistry;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class TestSlimefunTabCompleter {

    @Test
    void adminCraftingTabCompletionIsPermissionGated() {
        SlimefunCommand command = mock(SlimefunCommand.class);
        when(command.getSubCommandNames()).thenReturn(List.of("give", "admin"));
        CommandSender sender = mock(CommandSender.class);
        Command bukkitCommand = mock(Command.class);
        SlimefunTabCompleter completer = new SlimefunTabCompleter(command);

        Assertions.assertEquals(
                List.of("give"), completer.onTabComplete(sender, bukkitCommand, "sf", new String[] {""}));
        Assertions.assertEquals(
                List.of(), completer.onTabComplete(sender, bukkitCommand, "sf", new String[] {"admin", ""}));

        when(sender.hasPermission("slimefun.command.admin")).thenReturn(true);
        Assertions.assertEquals(
                List.of("crafting"), completer.onTabComplete(sender, bukkitCommand, "sf", new String[] {"admin", ""}));
        Assertions.assertEquals(
                List.of("disable", "enable", "status"),
                completer.onTabComplete(
                        sender, bukkitCommand, "sf", new String[] {"admin", "crafting", "AN_ITEM", ""}));
    }

    @Test
    void adminCraftingSuggestionsIncludeAddonRegisteredItemIds() {
        SlimefunCommand command = mock(SlimefunCommand.class);
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("slimefun.command.admin")).thenReturn(true);
        Command bukkitCommand = mock(Command.class);
        SlimefunItem addonItem = mock(SlimefunItem.class);
        when(addonItem.getId()).thenReturn("ADDON_REWARD");
        SlimefunRegistry registry = mock(SlimefunRegistry.class);
        when(registry.getAllSlimefunItems()).thenReturn(List.of(addonItem));

        try (MockedStatic<Slimefun> slimefun = mockStatic(Slimefun.class)) {
            slimefun.when(Slimefun::getRegistry).thenReturn(registry);
            Assertions.assertEquals(
                    List.of("ADDON_REWARD"),
                    new SlimefunTabCompleter(command)
                            .onTabComplete(
                                    sender,
                                    bukkitCommand,
                                    "sf",
                                    new String[] {"admin", "crafting", "addon_"}));
        }
    }

    @Test
    @DisplayName("Doctor migration aliases should share tab-completion routing")
    void testDoctorMigrationAliases() {
        Assertions.assertTrue(SlimefunTabCompleter.isDoctorMigrationRoute("migrate"));
        Assertions.assertTrue(SlimefunTabCompleter.isDoctorMigrationRoute("migration"));
        Assertions.assertTrue(SlimefunTabCompleter.isDoctorMigrationRoute("migrations"));
        Assertions.assertTrue(SlimefunTabCompleter.isDoctorMigrationRoute("MIGRATE"));

        Assertions.assertFalse(SlimefunTabCompleter.isDoctorMigrationRoute("migrated"));
        Assertions.assertFalse(SlimefunTabCompleter.isDoctorMigrationRoute("upgrade"));
    }
}
