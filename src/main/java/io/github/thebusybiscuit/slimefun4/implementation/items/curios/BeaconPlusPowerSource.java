package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import org.bukkit.Location;
import org.bukkit.block.Beacon;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

/** Shared dual-power rules for Beacon Plus. */
final class BeaconPlusPowerSource {

    static final String POWER_MODE_KEY = "beacon_plus_power_mode";
    static final double ENERGY_MODE_BASE_RANGE = 10.0D;

    private BeaconPlusPowerSource() {}

    static BeaconPlusPowerMode getMode(Location location) {
        return BeaconPlusPowerMode.fromStored(StorageCacheUtils.getData(location, POWER_MODE_KEY));
    }

    static void setMode(Location location, BeaconPlusPowerMode mode) {
        StorageCacheUtils.setData(location, POWER_MODE_KEY, mode.name());
    }

    static boolean isSourceReady(Block block, BeaconPlusPowerMode mode) {
        return mode == BeaconPlusPowerMode.SLIMEFUN_ENERGY || getPyramidTier(block) > 0;
    }

    static int getPyramidTier(Block block) {
        BlockState state = block.getState();
        return state instanceof Beacon beacon ? beacon.getTier() : 0;
    }

    static double getBaseRange(Block block) {
        BeaconPlusPowerMode mode = getMode(block.getLocation());
        if (mode == BeaconPlusPowerMode.SLIMEFUN_ENERGY) {
            return ENERGY_MODE_BASE_RANGE;
        }

        BlockState state = block.getState();
        if (!(state instanceof Beacon beacon) || beacon.getTier() <= 0) {
            return 0.0D;
        }
        return Math.max(0.0D, beacon.getEffectRange());
    }
}
