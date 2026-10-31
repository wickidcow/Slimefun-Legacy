package io.github.thebusybiscuit.slimefun4.implementation.listeners.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/**
 * Regression coverage for passive registered-mob drops. Unlike actively
 * wielded Slimefun items, checks for these drops must never message players.
 */
class MobDropListenerTest {

    private MobDropListener listener() {
        Slimefun plugin = mock(Slimefun.class);
        Server server = mock(Server.class);
        PluginManager manager = mock(PluginManager.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(manager);
        return new MobDropListener(plugin);
    }

    @Test
    void lockedRegisteredItemIsWithheldWithoutRequestingPlayerMessages() {
        Player player = mock(Player.class);
        SlimefunItem item = mock(SlimefunItem.class);
        ItemStack drop = new ItemStack(Material.ROTTEN_FLESH);

        try (MockedStatic<SlimefunItem> items = mockStatic(SlimefunItem.class)) {
            items.when(() -> SlimefunItem.getByItem(drop)).thenReturn(item);
            when(item.canUse(player, false)).thenReturn(false);

            assertFalse(listener().canDrop(player, drop));

            verify(item).canUse(player, false);
            verify(item, never()).canUse(player, true);
        }
    }

    @Test
    void unlockedOrdinaryRegisteredItemIsStillEligibleToDrop() {
        Player player = mock(Player.class);
        SlimefunItem item = mock(SlimefunItem.class);
        ItemStack drop = new ItemStack(Material.ROTTEN_FLESH);

        try (MockedStatic<SlimefunItem> items = mockStatic(SlimefunItem.class)) {
            items.when(() -> SlimefunItem.getByItem(drop)).thenReturn(item);
            when(item.canUse(player, false)).thenReturn(true);

            assertTrue(listener().canDrop(player, drop));

            verify(item).canUse(player, false);
            verify(item, never()).canUse(player, true);
        }
    }

    @Test
    void vanillaRegisteredMobDropDoesNotRequireSlimefunPermission() {
        Player player = mock(Player.class);
        ItemStack drop = new ItemStack(Material.ROTTEN_FLESH);

        try (MockedStatic<SlimefunItem> items = mockStatic(SlimefunItem.class)) {
            items.when(() -> SlimefunItem.getByItem(drop)).thenReturn(null);

            assertTrue(listener().canDrop(player, drop));
        }
    }
}
