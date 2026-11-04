package io.github.thebusybiscuit.slimefun4.implementation.listeners.crafting;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.thebusybiscuit.slimefun4.api.events.AncientAltarCraftEvent;
import io.github.thebusybiscuit.slimefun4.api.events.MultiBlockCraftEvent;
import io.github.thebusybiscuit.slimefun4.core.services.CraftingRestrictionService;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class CraftingRestrictionListenerTest {

    @Test
    void cancelsHumanAndAutomatedCraftEventsUsingTheSamePolicy() {
        Slimefun plugin = mock(Slimefun.class);
        Server server = mock(Server.class);
        PluginManager pluginManager = mock(PluginManager.class);
        CraftingRestrictionService policy = mock(CraftingRestrictionService.class);
        Player player = mock(Player.class);
        ItemStack output = mock(ItemStack.class);

        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(policy.denyIfBlocked(player, output)).thenReturn(true);
        when(policy.isBlocked(output)).thenReturn(true);

        try (MockedStatic<Slimefun> slimefun = mockStatic(Slimefun.class)) {
            slimefun.when(Slimefun::getCraftingRestrictionService).thenReturn(policy);
            CraftingRestrictionListener listener = new CraftingRestrictionListener(plugin);

            MultiBlockCraftEvent multiblock = mock(MultiBlockCraftEvent.class);
            when(multiblock.getPlayer()).thenReturn(player);
            when(multiblock.getOutput()).thenReturn(output);
            listener.onMultiblockCraft(multiblock);
            verify(multiblock).setCancelled(true);

            AncientAltarCraftEvent altar = mock(AncientAltarCraftEvent.class);
            when(altar.getPlayer()).thenReturn(player);
            when(altar.getItem()).thenReturn(output);
            listener.onAltarCraft(altar);
            verify(altar).setCancelled(true);

            CraftItemEvent workbench = mock(CraftItemEvent.class);
            CraftingInventory inventory = mock(CraftingInventory.class);
            when(workbench.getInventory()).thenReturn(inventory);
            when(inventory.getResult()).thenReturn(output);
            when(workbench.getWhoClicked()).thenReturn(player);
            listener.onWorkbenchCraft(workbench);
            verify(workbench).setCancelled(true);

            CrafterCraftEvent crafter = mock(CrafterCraftEvent.class);
            when(crafter.getResult()).thenReturn(output);
            listener.onVanillaCrafter(crafter);
            verify(crafter).setCancelled(true);
        }

        verify(pluginManager).registerEvents(org.mockito.ArgumentMatchers.any(CraftingRestrictionListener.class),
                org.mockito.ArgumentMatchers.eq(plugin));
    }
}
