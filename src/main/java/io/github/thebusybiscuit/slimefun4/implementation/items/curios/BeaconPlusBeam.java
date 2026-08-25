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
    private static final int MAX_BEAM_SEGMENTS = 64;

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
        double height = endY - startY;

        if (height > 0.0D) {
            int naturalSegments = Math.max(1, (int) Math.ceil(height / BEAM_STEP));
            int segments = Math.min(MAX_BEAM_SEGMENTS, naturalSegments);
            double segmentHeight = height / segments;
            double verticalSpread = Math.max(0.20D, segmentHeight * 0.30D);
            int dustCount = naturalSegments <= MAX_BEAM_SEGMENTS ? 2 : 4;

            // Keep the effect full-height, but cap server-side particle dispatches. On tall/custom-height worlds the
            // old fixed 1.75-block loop could issue thousands of spawnParticle calls per beacon every second.
            for (int segment = 0; segment < segments; segment++) {
                double y = startY + (segment + 0.5D) * segmentHeight;
                Location point = new Location(world, x, y, z);
                world.spawnParticle(
                        Particle.DUST, point, dustCount, 0.025D, verticalSpread, 0.025D, 0.0D, GOLD_BEAM);

                if ((segment & 3) == 0) {
                    world.spawnParticle(
                            Particle.ELECTRIC_SPARK,
                            point,
                            2,
                            0.22D,
                            Math.min(1.5D, verticalSpread),
                            0.22D,
                            0.015D);
                }
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
