package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import io.github.thebusybiscuit.slimefun4.utils.RadiationUtils;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** A bounded field threat scanner for expedition camps and hazardous terrain. */
public final class BastionResonator extends SimpleSlimefunItem<ItemUseHandler> {

    private static final long COOLDOWN_MILLIS = 5_000L;
    private final NamespacedKey cooldownKey = new NamespacedKey(Slimefun.instance(), "bastion_resonator_cooldown_until");

    @ParametersAreNonnullByDefault
    public BastionResonator(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    @Override
    public @Nonnull ItemUseHandler getItemHandler() {
        return event -> {
            event.cancel();
            Player player = event.getPlayer();
            long remaining = remainingCooldown(player);
            if (remaining > 0L) {
                player.sendMessage(ChatColor.RED + "Bastion Resonator is settling for " + seconds(remaining) + " more seconds.");
                return;
            }
            player.getPersistentDataContainer().set(
                    cooldownKey, PersistentDataType.LONG, System.currentTimeMillis() + COOLDOWN_MILLIS);
            scan(player);
        };
    }

    private void scan(Player player) {
        Chunk chunk = player.getChunk();
        Location origin = player.getLocation();
        int monsters = 0;
        double sumX = 0.0D;
        double sumZ = 0.0D;
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Monster) {
                monsters++;
                sumX += entity.getLocation().getX();
                sumZ += entity.getLocation().getZ();
            }
        }

        int spawners = 0;
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof CreatureSpawner) {
                spawners++;
            }
        }

        int hazards = countLocalHazards(player, chunk);
        int exposure = RadiationUtils.getExposure(player);
        int score = monsters * 4 + spawners * 20 + Math.min(hazards, 40) + exposure / 4;
        ThreatBand band = ThreatBand.fromScore(score);

        player.playSound(origin, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.55F, band == ThreatBand.CRITICAL ? 0.65F : 1.15F);
        player.sendMessage(ChatColor.GOLD + "Bastion Resonator " + ChatColor.GRAY + "• " + band.color
                + band.label + ChatColor.GRAY + " • threat score " + ChatColor.WHITE + score);
        player.sendMessage(ChatColor.GRAY + "Hostiles: " + ChatColor.YELLOW + monsters + ChatColor.GRAY
                + " • Spawners: " + ChatColor.YELLOW + spawners + ChatColor.GRAY + " • Local hazards: "
                + ChatColor.YELLOW + hazards + ChatColor.GRAY + " • Radiation: " + ChatColor.YELLOW + exposure + "/100");

        if (monsters > 0) {
            Location center = new Location(origin.getWorld(), sumX / monsters, origin.getY(), sumZ / monsters);
            player.sendMessage(ChatColor.GRAY + "The strongest hostile pressure is roughly " + ChatColor.AQUA
                    + cardinalDirection(origin, center) + ChatColor.GRAY + ".");
        }
    }

    private static int countLocalHazards(Player player, Chunk chunk) {
        Location origin = player.getLocation();
        int centerX = origin.getBlockX() & 15;
        int centerZ = origin.getBlockZ() & 15;
        int minY = Math.max(origin.getWorld().getMinHeight(), origin.getBlockY() - 3);
        int maxY = Math.min(origin.getWorld().getMaxHeight() - 1, origin.getBlockY() + 3);
        int hazards = 0;

        for (int x = Math.max(0, centerX - 4); x <= Math.min(15, centerX + 4); x++) {
            for (int z = Math.max(0, centerZ - 4); z <= Math.min(15, centerZ + 4); z++) {
                for (int y = minY; y <= maxY; y++) {
                    Block block = chunk.getBlock(x, y, z);
                    Material type = block.getType();
                    if (type == Material.LAVA || type == Material.MAGMA_BLOCK || type == Material.FIRE || type == Material.SOUL_FIRE) {
                        hazards++;
                    }
                }
            }
        }
        return hazards;
    }

    private long remainingCooldown(Player player) {
        Long until = player.getPersistentDataContainer().get(cooldownKey, PersistentDataType.LONG);
        return until == null ? 0L : Math.max(0L, until - System.currentTimeMillis());
    }

    private static long seconds(long millis) {
        return Math.max(1L, (millis + 999L) / 1_000L);
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

    private enum ThreatBand {
        CALM("CALM", ChatColor.GREEN),
        GUARDED("GUARDED", ChatColor.YELLOW),
        DANGEROUS("DANGEROUS", ChatColor.GOLD),
        CRITICAL("CRITICAL", ChatColor.RED);

        private final String label;
        private final ChatColor color;

        ThreatBand(String label, ChatColor color) {
            this.label = label;
            this.color = color;
        }

        static ThreatBand fromScore(int score) {
            if (score >= 60) {
                return CRITICAL;
            }
            if (score >= 30) {
                return DANGEROUS;
            }
            if (score >= 10) {
                return GUARDED;
            }
            return CALM;
        }
    }
}
