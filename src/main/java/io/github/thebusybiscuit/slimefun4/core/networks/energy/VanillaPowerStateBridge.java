package io.github.thebusybiscuit.slimefun4.core.networks.energy;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Powerable;

/**
 * Mirrors Slimefun's live EnergyNet transport state into vanilla player-head
 * {@link Powerable} block data.
 *
 * <p>This bridge never treats vanilla redstone as the source of truth for Slimefun energy.
 * It only exposes Slimefun's state on player heads, where Minecraft already provides the
 * {@code powered} block-state property. Physics updates are deliberately suppressed to avoid
 * creating redstone-update storms from energy ticks.
 */
final class VanillaPowerStateBridge {

    private static final long REVALIDATE_INTERVAL_TICKS = 20L;
    private static final int MAX_CACHED_LOCATIONS = 32_768;

    private static final Map<Location, CachedState> LAST_APPLIED_STATE = new ConcurrentHashMap<>();

    private VanillaPowerStateBridge() {}

    static void sync(@Nonnull Location location, boolean powered) {
        long gameTime = location.getWorld().getGameTime();
        CachedState cached = LAST_APPLIED_STATE.get(location);

        if (cached != null
                && cached.powered == powered
                && gameTime >= 0
                && gameTime < cached.nextValidationTick) {
            return;
        }

        Block block = location.getBlock();
        Material type = block.getType();
        if (type != Material.PLAYER_HEAD && type != Material.PLAYER_WALL_HEAD) {
            if (cached != null) {
                LAST_APPLIED_STATE.remove(location, cached);
            }
            return;
        }

        BlockData data = block.getBlockData();
        if (data instanceof Powerable powerable) {
            if (powerable.isPowered() != powered) {
                powerable.setPowered(powered);
                block.setBlockData(powerable, false);
            }

            cache(location, powered, gameTime, cached);
        }
    }

    private static void cache(
            @Nonnull Location location, boolean powered, long gameTime, CachedState cached) {
        long phase = Math.floorMod(location.hashCode(), REVALIDATE_INTERVAL_TICKS);
        long nextValidationTick = gameTime + 1L;
        long offset = Math.floorMod(
                phase - Math.floorMod(nextValidationTick, REVALIDATE_INTERVAL_TICKS),
                REVALIDATE_INTERVAL_TICKS);
        nextValidationTick += offset;

        /*
         * This path is reached every periodic self-heal pass for every energy-network player head.
         * Reuse the cache value already found by sync() instead of allocating both a new CachedState
         * and a cloned Location key every 20 ticks when the location remains in the cache.
         */
        if (cached != null) {
            cached.powered = powered;
            cached.nextValidationTick = nextValidationTick;
            return;
        }

        if (LAST_APPLIED_STATE.size() >= MAX_CACHED_LOCATIONS) {
            LAST_APPLIED_STATE.clear();
        }

        CachedState created = new CachedState(powered, nextValidationTick);
        CachedState raced = LAST_APPLIED_STATE.putIfAbsent(location.clone(), created);
        if (raced != null) {
            raced.powered = powered;
            raced.nextValidationTick = nextValidationTick;
        }
    }

    private static final class CachedState {
        private volatile boolean powered;
        private volatile long nextValidationTick;

        private CachedState(boolean powered, long nextValidationTick) {
            this.powered = powered;
            this.nextValidationTick = nextValidationTick;
        }
    }
}
