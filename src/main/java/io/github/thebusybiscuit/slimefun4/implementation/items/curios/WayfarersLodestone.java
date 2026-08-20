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
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/** A personal expedition anchor that points back to a bound location without teleporting the player. */
public final class WayfarersLodestone extends SimpleSlimefunItem<ItemUseHandler> {

    private final NamespacedKey worldKey = new NamespacedKey(Slimefun.instance(), "wayfarers_lodestone_world");
    private final NamespacedKey xKey = new NamespacedKey(Slimefun.instance(), "wayfarers_lodestone_x");
    private final NamespacedKey yKey = new NamespacedKey(Slimefun.instance(), "wayfarers_lodestone_y");
    private final NamespacedKey zKey = new NamespacedKey(Slimefun.instance(), "wayfarers_lodestone_z");

    @ParametersAreNonnullByDefault
    public WayfarersLodestone(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    @Override
    public @Nonnull ItemUseHandler getItemHandler() {
        return event -> {
            event.cancel();
            Player player = event.getPlayer();
            ItemStack item = event.getItem();
            ItemMeta rawMeta = item.getItemMeta();
            if (!(rawMeta instanceof CompassMeta meta)) {
                return;
            }

            if (player.isSneaking()) {
                bind(player, item, meta);
            } else {
                pointHome(player, item, meta);
            }
        };
    }

    private void bind(Player player, ItemStack item, CompassMeta meta) {
        Location location = player.getLocation();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(worldKey, PersistentDataType.STRING, location.getWorld().getUID().toString());
        data.set(xKey, PersistentDataType.DOUBLE, location.getX());
        data.set(yKey, PersistentDataType.DOUBLE, location.getY());
        data.set(zKey, PersistentDataType.DOUBLE, location.getZ());
        meta.setLodestone(location);
        meta.setLodestoneTracked(false);
        item.setItemMeta(meta);

        player.playSound(location, Sound.BLOCK_LODESTONE_PLACE, 0.8F, 1.25F);
        player.sendMessage(ChatColor.GOLD + "Wayfarer's Lodestone bound to this expedition point at "
                + ChatColor.YELLOW + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ()
                + ChatColor.GRAY + ". It does not create a home or teleport point.");
    }

    private void pointHome(Player player, ItemStack item, CompassMeta meta) {
        PersistentDataContainer data = meta.getPersistentDataContainer();
        String worldValue = data.get(worldKey, PersistentDataType.STRING);
        Double x = data.get(xKey, PersistentDataType.DOUBLE);
        Double y = data.get(yKey, PersistentDataType.DOUBLE);
        Double z = data.get(zKey, PersistentDataType.DOUBLE);
        if (worldValue == null || x == null || y == null || z == null) {
            player.sendMessage(ChatColor.YELLOW + "Sneak-right-click to bind the Wayfarer's Lodestone to an expedition point.");
            return;
        }

        UUID worldId;
        try {
            worldId = UUID.fromString(worldValue);
        } catch (IllegalArgumentException ignored) {
            player.sendMessage(ChatColor.RED + "The Wayfarer's Lodestone binding is damaged. Bind it again.");
            return;
        }

        World targetWorld = Bukkit.getWorld(worldId);
        if (targetWorld == null) {
            player.sendMessage(ChatColor.RED + "The bound expedition world is not currently available.");
            return;
        }

        Location target = new Location(targetWorld, x, y, z);
        meta.setLodestone(target);
        meta.setLodestoneTracked(false);
        item.setItemMeta(meta);

        if (!player.getWorld().getUID().equals(worldId)) {
            player.sendMessage(ChatColor.GOLD + "Wayfarer's Lodestone: " + ChatColor.GRAY
                    + "your expedition point is in " + ChatColor.AQUA + targetWorld.getName() + ChatColor.GRAY + ".");
            return;
        }

        Location origin = player.getLocation();
        int distance = (int) Math.round(Math.sqrt(origin.distanceSquared(target)));
        player.playSound(origin, Sound.BLOCK_LODESTONE_PLACE, 0.6F, 1.45F);
        player.sendMessage(ChatColor.GOLD + "Wayfarer's Lodestone: " + ChatColor.GRAY + "about "
                + ChatColor.YELLOW + distance + ChatColor.GRAY + " blocks " + ChatColor.AQUA
                + cardinalDirection(origin, target) + ChatColor.GRAY + ". Follow the compass needle back.");
    }

    private static String cardinalDirection(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double angle = Math.toDegrees(Math.atan2(-dx, dz));
        int octant = Math.floorMod((int) Math.round(angle / 45.0D), 8);
        return switch (octant) {
            case 0 -> "south";
            case 1 -> "southwest";
            case 2 -> "west";
            case 3 -> "northwest";
            case 4 -> "north";
            case 5 -> "northeast";
            case 6 -> "east";
            default -> "southeast";
        };
    }
}
