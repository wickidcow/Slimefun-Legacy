package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Personal reusable chalk that records one breadcrumb without changing the world.
 */
public final class DungeonChalk extends SimpleSlimefunItem<ItemUseHandler> {

    private final NamespacedKey markerKey = new NamespacedKey(Slimefun.instance(), "dungeon_chalk_marker");

    @ParametersAreNonnullByDefault
    public DungeonChalk(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    @Override
    public @Nonnull ItemUseHandler getItemHandler() {
        return event -> {
            event.cancel();
            Player player = event.getPlayer();
            ItemStack item = event.getItem();
            ItemMeta meta = item.getItemMeta();

            if (player.isSneaking()) {
                meta.getPersistentDataContainer().remove(markerKey);
                item.setItemMeta(meta);
                player.playSound(player.getLocation(), Sound.BLOCK_CALCITE_BREAK, 0.6F, 1.2F);
                player.sendMessage(ChatColor.WHITE + "Dungeon Chalk breadcrumb cleared.");
                return;
            }

            Block clicked = event.getClickedBlock().orElse(null);
            if (clicked != null) {
                Location location = clicked.getLocation();
                String marker = location.getWorld().getUID() + ";" + location.getBlockX() + ";" + location.getBlockY()
                        + ";" + location.getBlockZ();
                meta.getPersistentDataContainer().set(markerKey, PersistentDataType.STRING, marker);
                item.setItemMeta(meta);
                player.playSound(player.getLocation(), Sound.BLOCK_CALCITE_PLACE, 0.7F, 1.25F);
                player.sendMessage(ChatColor.WHITE + "Breadcrumb marked at " + ChatColor.YELLOW + format(location));
                return;
            }

            String marker = meta.getPersistentDataContainer().get(markerKey, PersistentDataType.STRING);
            Location location = parse(marker);
            if (location == null) {
                player.sendMessage(ChatColor.GRAY + "No breadcrumb is marked. Right click a block to mark one.");
                return;
            }

            if (!location.getWorld().equals(player.getWorld())) {
                player.sendMessage(ChatColor.WHITE + "Breadcrumb: " + ChatColor.YELLOW + format(location)
                        + ChatColor.GRAY + " in another world.");
                return;
            }

            int distance = (int) Math.round(player.getLocation().distance(location));
            player.sendMessage(ChatColor.WHITE + "Breadcrumb: " + ChatColor.YELLOW + format(location) + ChatColor.GRAY
                    + " • about " + ChatColor.AQUA + distance + ChatColor.GRAY + " blocks away.");
        };
    }

    private static Location parse(String value) {
        if (value == null) {
            return null;
        }

        try {
            String[] parts = value.split(";", -1);
            if (parts.length != 4) {
                return null;
            }
            World world = Bukkit.getWorld(UUID.fromString(parts[0]));
            if (world == null) {
                return null;
            }
            return new Location(
                    world, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static String format(Location location) {
        return location.getWorld().getName() + " " + location.getBlockX() + ", " + location.getBlockY() + ", "
                + location.getBlockZ();
    }
}
