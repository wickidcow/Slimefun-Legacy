package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.ASlimefunDataContainer;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.block.Block;

/** Optional native Slimefun-energy operating mode for Resonance Beacons. */
final class BeaconPlusEnergy {

    static final String ELECTRIC_MODE_KEY = "beacon_plus_electric_mode";
    private static final String ENERGY_CHARGE_KEY = "energy-charge";
    private static final long PAID_WINDOW_TICKS = 20L * 15L;
    private static final long PER_PULSE_ENERGY_SCALE = PAID_WINDOW_TICKS / 20L;
    private static final Map<BeaconKey, Long> PAID_UNTIL = new ConcurrentHashMap<>();

    private BeaconPlusEnergy() {}

    static boolean isElectricModeSelected(Location location) {
        return Boolean.parseBoolean(StorageCacheUtils.getData(location, ELECTRIC_MODE_KEY));
    }

    static boolean requiresEnergy(Location location) {
        return BeaconPlusConfig.isElectricOperationEnabled() && isElectricModeSelected(location);
    }

    static void setElectricMode(Location location, boolean enabled) {
        StorageCacheUtils.setData(location, ELECTRIC_MODE_KEY, Boolean.toString(enabled));
        PAID_UNTIL.remove(BeaconKey.from(location));
    }

    static long getStoredCharge(Location location) {
        return parseCharge(StorageCacheUtils.getData(location, ENERGY_CHARGE_KEY));
    }

    static long getDemand(Map<BeaconPlusEffect, Integer> tiers) {
        if (tiers == null || tiers.isEmpty()) {
            return 0L;
        }

        long tierSum = 0L;
        for (int tier : tiers.values()) {
            tierSum += Math.max(0, tier);
        }

        long demand =
                BeaconPlusConfig.getEnergyBaseCostPerPulse() + tierSum * BeaconPlusConfig.getEnergyTierCostPerPulse();
        int activatorTier = Math.max(0, tiers.getOrDefault(BeaconPlusEffect.ACTIVATOR, 0));
        demand += (long) activatorTier * BeaconPlusConfig.getEnergyActivatorTierSurchargePerPulse();
        if (demand <= 0L) {
            return 0L;
        }
        return demand > Long.MAX_VALUE / PER_PULSE_ENERGY_SCALE
                ? Long.MAX_VALUE
                : demand * PER_PULSE_ENERGY_SCALE;
    }

    static boolean hasOperationalPower(Block block, Map<BeaconPlusEffect, Integer> tiers) {
        Location location = block.getLocation();
        if (!requiresEnergy(location)) {
            return true;
        }

        long demand = getDemand(tiers);
        if (demand <= 0L) {
            return true;
        }

        BeaconKey key = BeaconKey.from(location);
        Long paidUntil = PAID_UNTIL.get(key);
        long gameTime = block.getWorld().getGameTime();
        if (paidUntil != null) {
            if (gameTime <= paidUntil) {
                return true;
            }
            PAID_UNTIL.remove(key, paidUntil);
        }

        return getStoredCharge(location) >= demand;
    }

    static boolean consumePulse(Block block, ASlimefunDataContainer data, Map<BeaconPlusEffect, Integer> tiers) {
        Location location = block.getLocation();
        BeaconKey key = BeaconKey.from(location);
        if (!requiresEnergy(location)) {
            PAID_UNTIL.remove(key);
            return true;
        }

        long demand = getDemand(tiers);
        if (demand <= 0L) {
            PAID_UNTIL.put(key, block.getWorld().getGameTime() + PAID_WINDOW_TICKS);
            return true;
        }

        long charge = parseCharge(data.getData(ENERGY_CHARGE_KEY));
        if (charge < demand) {
            PAID_UNTIL.remove(key);
            return false;
        }

        data.setData(ENERGY_CHARGE_KEY, Long.toString(charge - demand));
        PAID_UNTIL.put(key, block.getWorld().getGameTime() + PAID_WINDOW_TICKS);
        return true;
    }

    static void forget(Location location) {
        PAID_UNTIL.remove(BeaconKey.from(location));
    }

    static void shutdown() {
        PAID_UNTIL.clear();
    }

    private static long parseCharge(String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Math.max(0L, Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private record BeaconKey(UUID worldId, int x, int y, int z) {
        private static BeaconKey from(Location location) {
            return new BeaconKey(
                    location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }
    }
}
