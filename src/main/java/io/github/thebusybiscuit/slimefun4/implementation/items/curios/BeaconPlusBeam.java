package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/** Synchronizes the vanilla Beacon Plus beam tint with successful field power pulses. */
final class BeaconPlusBeam {

    static final String OWNED_FILTER_KEY = "beacon_plus_yellow_beam_filter";
    static final Material FILTER_MATERIAL = Material.YELLOW_STAINED_GLASS_PANE;

    private BeaconPlusBeam() {}

    static void markPowered(Block beaconBlock) {
        Location location = beaconBlock.getLocation();
        Block filterBlock = beaconBlock.getRelative(BlockFace.UP);
        boolean owned = Boolean.parseBoolean(StorageCacheUtils.getData(location, OWNED_FILTER_KEY));

        if (owned && filterBlock.getType() != FILTER_MATERIAL) {
            StorageCacheUtils.setData(location, OWNED_FILTER_KEY, "false");
            owned = false;
        }

        if (owned || filterBlock.getType() == FILTER_MATERIAL) {
            return;
        }

        if (filterBlock.isEmpty()) {
            filterBlock.setType(FILTER_MATERIAL, false);
            StorageCacheUtils.setData(location, OWNED_FILTER_KEY, "true");
        }
    }

    static void markUnpowered(Location location) {
        if (!Boolean.parseBoolean(StorageCacheUtils.getData(location, OWNED_FILTER_KEY))) {
            return;
        }

        if (location.getWorld() == null
                || !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return;
        }

        Block filterBlock = location.getBlock().getRelative(BlockFace.UP);
        if (filterBlock.getType() == FILTER_MATERIAL) {
            filterBlock.setType(Material.AIR, false);
        }
        StorageCacheUtils.setData(location, OWNED_FILTER_KEY, "false");
    }
}
