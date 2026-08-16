package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.ASlimefunDataContainer;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Tracks Beacon Plus locations that successfully completed their most recent powered field pulse.
 *
 * <p>This keeps event-driven effects tied to the currently selected and valid Beacon Plus power source.
 */
final class BeaconPlusPowerState {

    private static final long POWERED_TTL_MILLIS = 2_500L;
    private static final int INVISIBILITY_DURATION_TICKS = 70;

    private static final Map<BeaconKey, PoweredBeacon> POWERED_BEACONS = new ConcurrentHashMap<>();

    private BeaconPlusPowerState() {}

    static void markPowered(Block block, ASlimefunDataContainer data) {
        EnumSet<BeaconPlusEffect> effects = BeaconPlusEffect.parse(data.getData(BeaconPlusRuntime.EFFECTS_KEY));
        effects.remove(BeaconPlusEffect.ACTIVATOR);
        if (effects.isEmpty()) {
            markUnpowered(block.getLocation());
            return;
        }

        BeaconPlusFieldArea area = BeaconPlusRuntime.getEffectiveFieldArea(block.getLocation(), effects);
        int power = effects.contains(BeaconPlusEffect.EXTRA_POWER) ? 1 : 0;
        POWERED_BEACONS.put(
                BeaconKey.from(block.getLocation()),
                new PoweredBeacon(System.currentTimeMillis(), EnumSet.copyOf(effects), area, power));
    }

    static void markUnpowered(Location location) {
        POWERED_BEACONS.remove(BeaconKey.from(location));
    }

    static boolean hasPoweredEffect(Location target, BeaconPlusEffect effect) {
        return getPowerForEffect(target, effect) >= 0;
    }

    /**
     * @return -1 if no powered Beacon Plus currently provides the effect, otherwise 0 or 1 for normal/extra power.
     */
    static int getPowerForEffect(Location target, BeaconPlusEffect effect) {
        World world = target.getWorld();
        if (world == null) {
            return -1;
        }

        purgeExpired();
        int bestPower = -1;
        long now = System.currentTimeMillis();
        for (Map.Entry<BeaconKey, PoweredBeacon> entry : POWERED_BEACONS.entrySet()) {
            BeaconKey key = entry.getKey();
            PoweredBeacon powered = entry.getValue();
            if (now - powered.paidAtMillis() > POWERED_TTL_MILLIS
                    || !key.worldId().equals(world.getUID())
                    || !powered.effects().contains(effect)) {
                continue;
            }

            if (!world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) {
                continue;
            }
            if (!powered.area().containsChunk(key.x(), key.z(), target.getBlockX(), target.getBlockZ())) {
                continue;
            }

            bestPower = Math.max(bestPower, powered.power());
            if (bestPower == 1) {
                break;
            }
        }
        return bestPower;
    }

    static void applyInvisibility(Block block) {
        PoweredBeacon powered = POWERED_BEACONS.get(BeaconKey.from(block.getLocation()));
        if (powered == null
                || System.currentTimeMillis() - powered.paidAtMillis() > POWERED_TTL_MILLIS
                || !powered.effects().contains(BeaconPlusEffect.INVISIBLE)) {
            return;
        }

        for (Entity entity : getEntitiesInArea(block, powered.area())) {
            if (entity instanceof Player player) {
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.INVISIBILITY, INVISIBILITY_DURATION_TICKS, 0, true, false, true));
            }
        }
    }

    static void reconcileNearbyPlayerStates(Block block, double range) {
        Location center = block.getLocation().add(0.5D, 0.5D, 0.5D);
        for (Entity entity : getEntities(block, center, range)) {
            if (!(entity instanceof Player player)) {
                continue;
            }

            boolean hasPoweredPersistentState = hasPoweredEffect(player.getLocation(), BeaconPlusEffect.FLYING)
                    || hasPoweredEffect(player.getLocation(), BeaconPlusEffect.SCALE);
            if (hasPoweredPersistentState) {
                BeaconPlusRuntime.refreshPlayerState(player);
            } else {
                BeaconPlusRuntime.clearPlayerState(player);
            }
        }
    }

    static void shutdown() {
        POWERED_BEACONS.clear();
    }

    private static Collection<Entity> getEntitiesInArea(Block block, BeaconPlusFieldArea area) {
        if (Slimefun.getSchedulerService().isFolia()) {
            return List.of(block.getChunk().getEntities());
        }

        List<Entity> result = new ArrayList<>();
        World world = block.getWorld();
        int centerChunkX = block.getX() >> 4;
        int centerChunkZ = block.getZ() >> 4;
        int radius = area.getRadius();
        for (int x = centerChunkX - radius; x <= centerChunkX + radius; x++) {
            for (int z = centerChunkZ - radius; z <= centerChunkZ + radius; z++) {
                if (!world.isChunkLoaded(x, z)) {
                    continue;
                }
                for (Entity entity : world.getChunkAt(x, z).getEntities()) {
                    result.add(entity);
                }
            }
        }
        return result;
    }

    private static Collection<Entity> getEntities(Block block, Location center, double range) {
        if (Slimefun.getSchedulerService().isFolia()) {
            List<Entity> result = new ArrayList<>();
            double rangeSquared = range * range;
            for (Entity entity : block.getChunk().getEntities()) {
                if (entity.getLocation().distanceSquared(center) <= rangeSquared) {
                    result.add(entity);
                }
            }
            return result;
        }
        return block.getWorld().getNearbyEntities(center, range, range, range);
    }

    private static void purgeExpired() {
        long cutoff = System.currentTimeMillis() - POWERED_TTL_MILLIS;
        POWERED_BEACONS.entrySet().removeIf(entry -> entry.getValue().paidAtMillis() < cutoff);
    }

    private record PoweredBeacon(
            long paidAtMillis, EnumSet<BeaconPlusEffect> effects, BeaconPlusFieldArea area, int power) {}

    private record BeaconKey(UUID worldId, int x, int y, int z) {
        private static BeaconKey from(Location location) {
            return new BeaconKey(
                    location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }
    }
}
