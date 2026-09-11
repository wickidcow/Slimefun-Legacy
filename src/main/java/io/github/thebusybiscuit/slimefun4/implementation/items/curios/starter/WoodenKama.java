package io.github.thebusybiscuit.slimefun4.implementation.items.curios.starter;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

/**
 * A simple leaf-cutting starter tool that preserves normal leaf drops while adding a little early utility.
 */
public final class WoodenKama extends SlimefunItem implements Listener {

    private static final double STRING_CHANCE = 0.08D;

    @ParametersAreNonnullByDefault
    public WoodenKama(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    public void registerListener(Slimefun plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onLeafBreak(BlockBreakEvent event) {
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE
                || !Tag.LEAVES.isTagged(event.getBlock().getType())
                || !isItem(event.getPlayer().getInventory().getItemInMainHand())) {
            return;
        }

        event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), new ItemStack(Material.STICK));

        if (ThreadLocalRandom.current().nextDouble() < STRING_CHANCE) {
            event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), new ItemStack(Material.STRING));
        }
    }
}
