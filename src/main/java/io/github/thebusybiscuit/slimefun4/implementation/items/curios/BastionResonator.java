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
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.generator.structure.StructureType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.StructureSearchResult;

/**
 * A Nether-only compass that tunes itself to the closest Bastion Remnant without forcing unexplored terrain generation.
 */
@SuppressWarnings("deprecation")
public final class BastionResonator extends SimpleSlimefunItem<ItemUseHandler> {

    static final int SEARCH_RADIUS_CHUNKS = 128;
    static final long COOLDOWN_MILLIS = 30_000L;

    private final NamespacedKey cooldownKey =
            new NamespacedKey(Slimefun.instance(), "bastion_resonator_cooldown_until");

    @ParametersAreNonnullByDefault
    public BastionResonator(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    @Override
    public @Nonnull ItemUseHandler getItemHandler() {
        return event -> {
            event.cancel();
            Player player = event.getPlayer();
            if (player.getWorld().getEnvironment() != World.Environment.NETHER) {
                player.sendMessage(ChatColor.RED + "The Bastion Resonator only answers in the Nether.");
                return;
            }

            long remaining = getCooldownRemainingMillis(player);
            if (remaining > 0L) {
                player.sendMessage(ChatColor.RED + "Bastion Resonator is retuning for " + formatSeconds(remaining)
                        + " more seconds.");
                return;
            }

            ItemStack item = event.getItem();
            ItemMeta itemMeta = item.getItemMeta();
            if (!(itemMeta instanceof CompassMeta compassMeta)) {
                return;
            }

            player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.7F, 1.25F);
            player.sendMessage(
                    ChatColor.GOLD + "Bastion Resonator: " + ChatColor.GRAY + "listening for Piglin stonework...");
            startCooldown(player);

            Slimefun.getSchedulerService().runFor(player, () -> locate(player, item, compassMeta), () -> {});
        };
    }

    private void locate(Player player, ItemStack item, CompassMeta compassMeta) {
        World world = player.getWorld();
        StructureType bastion = Registry.STRUCTURE_TYPE.get(NamespacedKey.minecraft("bastion_remnant"));
        if (bastion == null) {
            player.sendMessage(ChatColor.RED + "This server does not expose the Bastion Remnant structure type.");
            return;
        }

        Location origin = player.getLocation();
        StructureSearchResult result = world.locateNearestStructure(origin, bastion, SEARCH_RADIUS_CHUNKS, false);
        if (result == null) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.65F, 0.75F);
            player.sendMessage(ChatColor.RED + "No Bastion Remnant responded within " + SEARCH_RADIUS_CHUNKS
                    + " chunks. Move deeper into the Nether and try again.");
            return;
        }

        Location target = result.getLocation();
        compassMeta.setLodestone(target);
        compassMeta.setLodestoneTracked(false);
        item.setItemMeta(compassMeta);

        int distance = (int) Math.round(Math.hypot(target.getX() - origin.getX(), target.getZ() - origin.getZ()));
        String direction = cardinalDirection(origin, target);
        player.playSound(player.getLocation(), Sound.BLOCK_LODESTONE_PLACE, 0.8F, 1.4F);
        player.sendMessage(ChatColor.GOLD + "Bastion Resonator tuned: " + ChatColor.AQUA + direction + ChatColor.GRAY
                + " • about " + ChatColor.YELLOW + distance + ChatColor.GRAY + " blocks • " + ChatColor.DARK_GRAY
                + "target " + target.getBlockX() + ", " + target.getBlockZ());
    }

    private long getCooldownRemainingMillis(Player player) {
        Long until = player.getPersistentDataContainer().get(cooldownKey, PersistentDataType.LONG);
        return until == null ? 0L : Math.max(0L, until - System.currentTimeMillis());
    }

    private void startCooldown(Player player) {
        player.getPersistentDataContainer()
                .set(cooldownKey, PersistentDataType.LONG, System.currentTimeMillis() + COOLDOWN_MILLIS);
    }

    private static long formatSeconds(long millis) {
        return Math.max(1L, (millis + 999L) / 1_000L);
    }

    private static String cardinalDirection(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double angle = Math.toDegrees(Math.atan2(-dx, dz));
        int octant = Math.floorMod((int) Math.round(angle / 45.0D), 8);
        return switch (octant) {
            case 0 -> "South";
            case 1 -> "Southwest";
            case 2 -> "West";
            case 3 -> "Northwest";
            case 4 -> "North";
            case 5 -> "Northeast";
            case 6 -> "East";
            default -> "Southeast";
        };
    }
}
