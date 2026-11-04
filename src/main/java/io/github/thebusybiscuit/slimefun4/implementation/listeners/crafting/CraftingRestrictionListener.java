package io.github.thebusybiscuit.slimefun4.implementation.listeners.crafting;

import io.github.thebusybiscuit.slimefun4.api.events.AncientAltarCraftEvent;
import io.github.thebusybiscuit.slimefun4.api.events.MultiBlockCraftEvent;
import io.github.thebusybiscuit.slimefun4.core.services.CraftingRestrictionService;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Denies creation of configured Slimefun outputs, rather than disabling the items or rejecting
 * them as ingredients. Recipes stay registered so the guide and existing inventories remain intact.
 */
public final class CraftingRestrictionListener implements Listener {

    private final CraftingRestrictionService restrictions;

    public CraftingRestrictionListener(Slimefun plugin) {
        restrictions = Slimefun.getCraftingRestrictionService();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMultiblockCraft(MultiBlockCraftEvent event) {
        if (restrictions.denyIfBlocked(event.getPlayer(), event.getOutput())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAltarCraft(AncientAltarCraftEvent event) {
        if (restrictions.denyIfBlocked(event.getPlayer(), event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onWorkbenchCraft(CraftItemEvent event) {
        ItemStack output = event.getInventory().getResult();
        if (output == null) {
            output = event.getRecipe().getResult();
        }
        Player player = event.getWhoClicked() instanceof Player p ? p : null;
        if (restrictions.denyIfBlocked(player, output)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVanillaCrafter(CrafterCraftEvent event) {
        if (restrictions.isBlocked(event.getResult())) {
            event.setCancelled(true);
        }
    }
}
