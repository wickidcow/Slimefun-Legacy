package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * A surveyor's spyglass that reports useful exploration information when used.
 */
public final class ExplorersSpyglass extends SimpleSlimefunItem<ItemUseHandler> {

    private static final String[] DIRECTIONS = {
        "South", "Southwest", "West", "Northwest", "North", "Northeast", "East", "Southeast"
    };

    @ParametersAreNonnullByDefault
    public ExplorersSpyglass(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    @Override
    public @Nonnull ItemUseHandler getItemHandler() {
        return event -> {
            Player player = event.getPlayer();
            Location location = player.getLocation();

            String biome = humanize(location.getBlock().getBiome().getKey().getKey());
            String direction = getDirection(location.getYaw());

            message(player, "&6" + "Explorer's Spyglass " + "&7" + "• " + "&e"
                    + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + "&7"
                    + " • " + "&b" + biome + "&7" + " • " + "&a" + direction);
        };
    }

    private static String getDirection(float yaw) {
        int index = Math.floorMod((int) Math.floor((yaw + 22.5F) / 45.0F), DIRECTIONS.length);
        return DIRECTIONS[index];
    }

    private static String humanize(String key) {
        String[] parts = key.toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();

        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }

        return result.toString();
    }
    private static void message(Player player, String value) {
        player.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(value));
    }

}
