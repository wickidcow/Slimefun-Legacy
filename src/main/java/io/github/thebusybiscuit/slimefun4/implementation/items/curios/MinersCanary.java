package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * A hand-held lava detector for cautious miners.
 */
public final class MinersCanary extends SimpleSlimefunItem<ItemUseHandler> {

    private static final int RANGE = 7;
    private static final int COOLDOWN_TICKS = 5 * 20;

    @ParametersAreNonnullByDefault
    public MinersCanary(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    @Override
    public @Nonnull ItemUseHandler getItemHandler() {
        return event -> {
            event.cancel();
            Player player = event.getPlayer();
            if (player.hasCooldown(Material.YELLOW_DYE)) {
                return;
            }

            Location origin = player.getLocation();
            World world = origin.getWorld();
            int originChunkX = origin.getBlockX() >> 4;
            int originChunkZ = origin.getBlockZ() >> 4;
            int found = 0;
            double nearestSquared = Double.MAX_VALUE;

            for (int x = origin.getBlockX() - RANGE; x <= origin.getBlockX() + RANGE; x++) {
                for (int y = Math.max(world.getMinHeight(), origin.getBlockY() - RANGE);
                        y <= Math.min(world.getMaxHeight() - 1, origin.getBlockY() + RANGE);
                        y++) {
                    for (int z = origin.getBlockZ() - RANGE; z <= origin.getBlockZ() + RANGE; z++) {
                        int chunkX = x >> 4;
                        int chunkZ = z >> 4;
                        if (!world.isChunkLoaded(chunkX, chunkZ)) {
                            continue;
                        }
                        if (Slimefun.getSchedulerService().isFolia()
                                && (chunkX != originChunkX || chunkZ != originChunkZ)) {
                            continue;
                        }

                        if (world.getBlockAt(x, y, z).getType() == Material.LAVA) {
                            found++;
                            double dx = x + 0.5D - origin.getX();
                            double dy = y + 0.5D - origin.getY();
                            double dz = z + 0.5D - origin.getZ();
                            nearestSquared = Math.min(nearestSquared, dx * dx + dy * dy + dz * dz);
                        }
                    }
                }
            }

            player.setCooldown(Material.YELLOW_DYE, COOLDOWN_TICKS);
            if (found == 0) {
                player.playSound(player.getLocation(), Sound.ENTITY_PARROT_AMBIENT, 0.55F, 1.35F);
                player.sendMessage(ChatColor.YELLOW + "The Miner's Canary is calm. " + ChatColor.GRAY
                        + "No exposed lava detected within " + RANGE + " blocks.");
            } else {
                int nearest = (int) Math.ceil(Math.sqrt(nearestSquared));
                player.playSound(player.getLocation(), Sound.ENTITY_PARROT_HURT, 0.85F, 1.6F);
                player.sendMessage(ChatColor.GOLD + "The Miner's Canary squawks! " + ChatColor.RED + found
                        + ChatColor.GRAY + " lava blocks detected; nearest is about " + ChatColor.WHITE + nearest
                        + ChatColor.GRAY + " blocks away.");
            }
        };
    }
}
