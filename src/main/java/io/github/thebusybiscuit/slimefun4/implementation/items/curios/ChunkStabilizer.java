package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** A tightly bounded, fuelled one-chunk expedition ticket with automatic expiry. */
public final class ChunkStabilizer extends SimpleSlimefunItem<ItemUseHandler> {

    static final int MAX_ACTIVE_STABILIZERS = 16;
    static final long DURATION_TICKS = 5L * 60L * 20L;
    static final long DURATION_MILLIS = 5L * 60L * 1_000L;
    private static final Map<UUID, Stabilization> ACTIVE = new ConcurrentHashMap<>();

    @ParametersAreNonnullByDefault
    public ChunkStabilizer(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    @Override
    public @Nonnull ItemUseHandler getItemHandler() {
        return event -> {
            event.cancel();
            Player player = event.getPlayer();
            if (player.isSneaking()) {
                release(player);
            } else {
                activate(player);
            }
        };
    }

    private void activate(Player player) {
        UUID playerId = player.getUniqueId();
        Chunk chunk = player.getChunk();
        UUID worldId = player.getWorld().getUID();
        Stabilization stabilization = new Stabilization(
                worldId,
                chunk.getX(),
                chunk.getZ(),
                System.currentTimeMillis() + DURATION_MILLIS);

        synchronized (ACTIVE) {
            Stabilization existing = ACTIVE.get(playerId);
            if (existing != null) {
                long remaining = Math.max(1L, (existing.expiresAt() - System.currentTimeMillis() + 999L) / 1_000L);
                player.sendMessage(ChatColor.YELLOW + "You already have a stabilized chunk for another " + remaining
                        + " seconds. Sneak-right-click to release it first.");
                return;
            }
            if (ACTIVE.size() >= MAX_ACTIVE_STABILIZERS) {
                player.sendMessage(ChatColor.RED + "The global Chunk Stabilizer limit of " + MAX_ACTIVE_STABILIZERS
                        + " is currently reached.");
                return;
            }
            if (ACTIVE.values().stream().anyMatch(stabilization::sameChunk)) {
                player.sendMessage(ChatColor.YELLOW + "That chunk is already stabilized by another expedition.");
                return;
            }
            ACTIVE.put(playerId, stabilization);
        }

        Map<Integer, ItemStack> missing = player.getInventory().removeItem(new ItemStack(Material.REDSTONE_BLOCK, 1));
        if (!missing.isEmpty()) {
            ACTIVE.remove(playerId, stabilization);
            player.sendMessage(ChatColor.RED + "Chunk Stabilizer activation requires 1 Redstone Block as field fuel.");
            return;
        }

        boolean added = chunk.addPluginChunkTicket(Slimefun.instance());
        if (!added) {
            ACTIVE.remove(playerId, stabilization);
            refundFuel(player);
            player.sendMessage(ChatColor.RED + "The server refused the chunk ticket. No stabilization was started.");
            return;
        }

        Location center = chunkCenter(player.getWorld(), chunk.getX(), chunk.getZ());
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6F, 1.4F);
        player.sendMessage(ChatColor.AQUA + "Chunk Stabilizer active for 5 minutes on chunk " + chunk.getX() + ", "
                + chunk.getZ() + ChatColor.GRAY + ". Only this chunk is retained; no neighboring chunks are loaded.");
        Slimefun.getSchedulerService().runAtLater(center, () -> expire(playerId, stabilization), DURATION_TICKS);
    }

    private void release(Player player) {
        UUID playerId = player.getUniqueId();
        Stabilization stabilization = ACTIVE.remove(playerId);
        if (stabilization == null) {
            player.sendMessage(ChatColor.GRAY + "You do not currently have a stabilized chunk.");
            return;
        }

        removeTicket(stabilization);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.6F, 1.2F);
        player.sendMessage(ChatColor.YELLOW + "Your stabilized chunk has been released.");
    }

    private void expire(UUID playerId, Stabilization expected) {
        if (!ACTIVE.remove(playerId, expected)) {
            return;
        }
        removeTicket(expected);
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            Slimefun.getSchedulerService().runFor(player,
                    () -> player.sendMessage(ChatColor.YELLOW + "Your Chunk Stabilizer's five-minute field has expired."),
                    () -> {});
        }
    }

    private void removeTicket(Stabilization stabilization) {
        World world = Bukkit.getWorld(stabilization.worldId());
        if (world == null) {
            return;
        }
        Location center = chunkCenter(world, stabilization.chunkX(), stabilization.chunkZ());
        Slimefun.getSchedulerService().runAt(center, () -> {
            if (world.isChunkLoaded(stabilization.chunkX(), stabilization.chunkZ())) {
                world.getChunkAt(stabilization.chunkX(), stabilization.chunkZ())
                        .removePluginChunkTicket(Slimefun.instance());
            }
        });
    }

    private static void refundFuel(Player player) {
        Map<Integer, ItemStack> remainder = player.getInventory().addItem(new ItemStack(Material.REDSTONE_BLOCK, 1));
        for (ItemStack stack : remainder.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), stack);
        }
    }

    private static Location chunkCenter(World world, int chunkX, int chunkZ) {
        return new Location(world, (chunkX << 4) + 8.0D, world.getMinHeight() + 1.0D, (chunkZ << 4) + 8.0D);
    }

    private record Stabilization(UUID worldId, int chunkX, int chunkZ, long expiresAt) {
        boolean sameChunk(Stabilization other) {
            return worldId.equals(other.worldId) && chunkX == other.chunkX && chunkZ == other.chunkZ;
        }
    }
}
