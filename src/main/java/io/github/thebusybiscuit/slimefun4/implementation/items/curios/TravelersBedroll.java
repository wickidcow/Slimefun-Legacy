package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * A lightweight portable rest tool for expeditions.
 *
 * <p>The bedroll deliberately does not place a bed, change world time, or move the
 * player's respawn point. It provides a bounded personal rest action instead.</p>
 */
public final class TravelersBedroll extends SimpleSlimefunItem<ItemUseHandler> {

    private static final int REST_COOLDOWN_TICKS = 20 * 60 * 5;
    private static final double HOSTILE_CHECK_RADIUS = 8.0D;

    @ParametersAreNonnullByDefault
    public TravelersBedroll(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    @Override
    public @Nonnull ItemUseHandler getItemHandler() {
        return event -> {
            event.cancel();
            Player player = event.getPlayer();

            if (player.hasCooldown(Material.BROWN_BED)) {
                return;
            }

            World world = player.getWorld();
            if (world.getEnvironment() != World.Environment.NORMAL) {
                player.sendMessage(ChatColor.RED + "The Traveler's Bedroll can only be used in the Overworld.");
                return;
            }

            long time = world.getTime();
            if (time < 12_542L || time > 23_460L) {
                player.sendMessage(ChatColor.GRAY + "You can only settle into the bedroll at night.");
                return;
            }

            for (Entity entity : player.getNearbyEntities(HOSTILE_CHECK_RADIUS, 5.0D, HOSTILE_CHECK_RADIUS)) {
                if (entity instanceof Monster) {
                    player.sendMessage(ChatColor.RED + "It is too dangerous to rest while hostile creatures are nearby.");
                    return;
                }
            }

            player.setStatistic(Statistic.TIME_SINCE_REST, 0);
            player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + 4.0D));
            player.setFoodLevel(Math.min(20, player.getFoodLevel() + 4));
            player.setSaturation(Math.min(player.getFoodLevel(), player.getSaturation() + 2.0F));
            player.setCooldown(Material.BROWN_BED, REST_COOLDOWN_TICKS);
            player.playSound(player.getLocation(), Sound.BLOCK_WOOL_PLACE, 0.8F, 0.75F);
            player.sendMessage(ChatColor.GOLD + "Traveler's Bedroll " + ChatColor.GRAY
                    + "• You rest for a moment and feel ready to travel again.");
            player.sendMessage(ChatColor.DARK_GRAY
                    + "Your phantom-rest timer was reset. This does not change time or your respawn point.");
        };
    }
}
