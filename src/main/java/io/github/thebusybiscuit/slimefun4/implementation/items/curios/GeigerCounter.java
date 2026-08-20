package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.Radioactive;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import io.github.thebusybiscuit.slimefun4.utils.RadiationUtils;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** A handheld radiation meter for expeditions and hazardous-material cleanup. */
public final class GeigerCounter extends SimpleSlimefunItem<ItemUseHandler> {

    private static final double SCAN_RADIUS = 12.0D;

    @ParametersAreNonnullByDefault
    public GeigerCounter(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    @Override
    public @Nonnull ItemUseHandler getItemHandler() {
        return event -> {
            event.cancel();
            Player player = event.getPlayer();
            int exposure = RadiationUtils.getExposure(player);
            int carriedStrength = strongestCarriedRadiation(player);

            Item strongest = null;
            int strongestStrength = 0;
            double strongestDistance = Double.MAX_VALUE;
            Location origin = player.getLocation();

            for (Entity entity : player.getNearbyEntities(SCAN_RADIUS, SCAN_RADIUS, SCAN_RADIUS)) {
                if (!(entity instanceof Item dropped)) {
                    continue;
                }

                SlimefunItem slimefunItem = SlimefunItem.getByItem(dropped.getItemStack());
                if (!(slimefunItem instanceof Radioactive radioactive)) {
                    continue;
                }

                int strength = radioactive.getRadioactivity().getExposureModifier();
                double distance = dropped.getLocation().distanceSquared(origin);
                if (strength > strongestStrength || (strength == strongestStrength && distance < strongestDistance)) {
                    strongest = dropped;
                    strongestStrength = strength;
                    strongestDistance = distance;
                }
            }

            player.sendMessage(ChatColor.GOLD + "Geiger Counter " + ChatColor.GRAY + "• exposure "
                    + exposureColor(exposure) + exposure + ChatColor.GRAY + "/100"
                    + ChatColor.GRAY + " • carried source "
                    + (carriedStrength > 0 ? ChatColor.YELLOW + Integer.toString(carriedStrength) : ChatColor.GREEN + "none"));

            if (strongest == null) {
                player.sendMessage(ChatColor.GREEN + "No dropped radioactive source detected within 12 blocks.");
                return;
            }

            Location target = strongest.getLocation();
            int distance = (int) Math.round(Math.sqrt(strongestDistance));
            player.sendMessage(ChatColor.GRAY + "Strongest nearby source: " + ChatColor.RED + strongestStrength
                    + ChatColor.GRAY + " • about " + ChatColor.YELLOW + distance + ChatColor.GRAY + " blocks "
                    + ChatColor.AQUA + cardinalDirection(origin, target));
        };
    }

    private static int strongestCarriedRadiation(Player player) {
        int strongest = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            SlimefunItem item = SlimefunItem.getByItem(stack);
            if (item instanceof Radioactive radioactive) {
                strongest = Math.max(strongest, radioactive.getRadioactivity().getExposureModifier());
            }
        }
        return strongest;
    }

    private static ChatColor exposureColor(int exposure) {
        if (exposure >= 75) {
            return ChatColor.DARK_RED;
        }
        if (exposure >= 50) {
            return ChatColor.RED;
        }
        if (exposure >= 25) {
            return ChatColor.GOLD;
        }
        if (exposure > 0) {
            return ChatColor.YELLOW;
        }
        return ChatColor.GREEN;
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
