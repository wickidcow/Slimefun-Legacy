package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * A field compass that can be retuned to the player's last death location.
 */
public final class WayfindersCompass extends SimpleSlimefunItem<ItemUseHandler> {

    @ParametersAreNonnullByDefault
    public WayfindersCompass(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    @Override
    public @Nonnull ItemUseHandler getItemHandler() {
        return event -> {
            event.cancel();

            Player player = event.getPlayer();
            if (player.hasCooldown(Material.COMPASS)) {
                return;
            }

            ItemStack item = event.getItem();
            ItemMeta itemMeta = item.getItemMeta();
            if (!(itemMeta instanceof CompassMeta compassMeta)) {
                return;
            }

            Location deathLocation = player.getLastDeathLocation();
            boolean usingWorldSpawn = deathLocation == null || deathLocation.getWorld() == null;
            Location target = usingWorldSpawn ? player.getWorld().getSpawnLocation() : deathLocation;

            compassMeta.setLodestone(target);
            compassMeta.setLodestoneTracked(false);
            item.setItemMeta(compassMeta);

            player.setCooldown(Material.COMPASS, 40);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8F, 1.35F);

            String targetName = usingWorldSpawn ? "world spawn" : "your last death";
            player.sendMessage(ChatColor.GOLD + "Wayfinder tuned to " + targetName + ChatColor.GRAY + " ["
                    + target.getWorld().getName() + " " + target.getBlockX() + ", " + target.getBlockY() + ", "
                    + target.getBlockZ() + "]");
        };
    }
}
