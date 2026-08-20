package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/** Renders the optional powered Resonance Beacon yellow beam without placing any world blocks. */
final class BeaconPlusBeam {

    /** Kept for one-way cleanup of the old 4.1.31 stained-glass beam implementation. */
    static final String OWNED_FILTER_KEY = "beacon_plus_yellow_beam_filter";
    static final String VISUALS_ENABLED_KEY = "beacon_plus_visuals_enabled";
    static final Material LEGACY_FILTER_MATERIAL = Material.YELLOW_STAINED_GLASS_PANE;

    private static final Particle.DustOptions GOLD_BEAM = new Particle.DustOptions(Color.fromRGB(255, 215, 0), 1.65F);
    private static final Particle.DustOptions GOLD_AURA = new Particle.DustOptions(Color.fromRGB(255, 196, 32), 1.25F);
    private static final double BEAM_STEP = 1.75D;

    private BeaconPlusBeam() {}

    static void markPowered(Block beaconBlock) {
        Location location = beaconBlock.getLocation();
        cleanupLegacyFilter(location);

        if (!isVisualsEnabled(location)) {
            return;
        }

        renderPoweredVisual(beaconBlock);
    }

    static void markUnpowered(Location location) {
        // Particle visuals naturally expire. This only cleans up panes left by the previous implementation.
        cleanupLegacyFilter(location);
    }

    static boolean isVisualsEnabled(Location location) {
        String stored = StorageCacheUtils.getData(location, VISUALS_ENABLED_KEY);
        return stored == null || !"false".equalsIgnoreCase(stored);
    }

    static void setVisualsEnabled(Location location, boolean enabled) {
        StorageCacheUtils.setData(location, VISUALS_ENABLED_KEY, Boolean.toString(enabled));
        if (!enabled) {
            cleanupLegacyFilter(location);
        }
    }

    private static void renderPoweredVisual(Block beaconBlock) {
        World world = beaconBlock.getWorld();
        double x = beaconBlock.getX() + 0.5D;
        double z = beaconBlock.getZ() + 0.5D;
        double startY = beaconBlock.getY() + 1.05D;
        double endY = world.getMaxHeight() - 0.25D;

        int segment = 0;
        for (double y = startY; y < endY; y += BEAM_STEP) {
            Location point = new Location(world, x, y, z);
            world.spawnParticle(Particle.DUST, point, 2, 0.025D, 0.20D, 0.025D, 0.0D, GOLD_BEAM);

            // A sparse electric halo keeps the shaft lively without turning every pulse into particle spam.
            if ((segment++ & 3) == 0) {
                world.spawnParticle(Particle.ELECTRIC_SPARK, point, 2, 0.22D, 0.35D, 0.22D, 0.015D);
            }
        }

        Location core = beaconBlock.getLocation().add(0.5D, 1.05D, 0.5D);
        world.spawnParticle(Particle.DUST, core, 14, 0.38D, 0.18D, 0.38D, 0.0D, GOLD_AURA);
        world.spawnParticle(Particle.ELECTRIC_SPARK, core, 9, 0.48D, 0.24D, 0.48D, 0.025D);
    }

    private static void cleanupLegacyFilter(Location location) {
        boolean owned = Boolean.parseBoolean(StorageCacheUtils.getData(location, OWNED_FILTER_KEY));
        if (!owned) {
            return;
        }

        World world = location.getWorld();
        if (world == null || !world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return;
        }

        Block filterBlock = location.getBlock().getRelative(BlockFace.UP);
        if (filterBlock.getType() == LEGACY_FILTER_MATERIAL) {
            filterBlock.setType(Material.AIR, false);
        }
        StorageCacheUtils.setData(location, OWNED_FILTER_KEY, "false");
    }
}
