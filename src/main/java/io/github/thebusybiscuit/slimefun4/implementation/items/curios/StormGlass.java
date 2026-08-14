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
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Reports local weather, time of day and lunar phase without changing the world.
 */
public final class StormGlass extends SimpleSlimefunItem<ItemUseHandler> {

    private static final String[] MOON_PHASES = {
        "Full Moon", "Waning Gibbous", "Last Quarter", "Waning Crescent",
        "New Moon", "Waxing Crescent", "First Quarter", "Waxing Gibbous"
    };

    @ParametersAreNonnullByDefault
    public StormGlass(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    @Override
    public @Nonnull ItemUseHandler getItemHandler() {
        return event -> {
            event.cancel();
            Player player = event.getPlayer();
            if (player.hasCooldown(Material.AMETHYST_SHARD)) {
                return;
            }

            World world = player.getWorld();
            long time = world.getTime();
            String weather = world.isThundering() ? "Thunderstorm" : world.hasStorm() ? "Rain" : "Clear";
            String dayPart = time < 1000 ? "Dawn" : time < 6000 ? "Morning" : time < 12000 ? "Afternoon"
                    : time < 14000 ? "Dusk" : time < 22000 ? "Night" : "Late Night";
            int moonIndex = (int) ((world.getFullTime() / 24000L) & 7L);
            long weatherSeconds = Math.max(0, world.getWeatherDuration()) / 20L;

            player.setCooldown(Material.AMETHYST_SHARD, 20);
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.65F, 1.45F);
            player.sendMessage(ChatColor.AQUA + "Storm Glass " + ChatColor.GRAY + "• " + ChatColor.WHITE + weather
                    + ChatColor.GRAY + " • " + ChatColor.YELLOW + dayPart + ChatColor.GRAY + " • " + ChatColor.LIGHT_PURPLE
                    + MOON_PHASES[moonIndex]);
            player.sendMessage(ChatColor.GRAY + "Current weather cycle has about " + ChatColor.WHITE + weatherSeconds
                    + ChatColor.GRAY + " seconds remaining.");
        };
    }
}
