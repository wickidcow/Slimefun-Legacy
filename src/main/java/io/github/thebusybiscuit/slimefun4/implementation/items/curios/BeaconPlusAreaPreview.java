package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.EnumSet;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;

/** Renders a lightweight, chunk-aligned preview of the effective Beacon Plus field area. */
final class BeaconPlusAreaPreview {

    static final String ENABLED_KEY = "beacon_plus_area_preview_enabled";

    private static final Particle.DustOptions FIELD_EDGE =
            new Particle.DustOptions(Color.fromRGB(72, 220, 255), 1.15F);
    private static final double EDGE_INSET = 0.08D;
    private static final double PARTICLE_STEP = 4.0D;
    private static final double BASE_Y_OFFSET = 1.20D;
    private static final int CORNER_HEIGHT = 4;

    private BeaconPlusAreaPreview() {}

    static boolean isEnabled(Location location) {
        String stored = StorageCacheUtils.getData(location, ENABLED_KEY);
        return stored == null || !"false".equalsIgnoreCase(stored);
    }

    static void setEnabled(Location location, boolean enabled) {
        StorageCacheUtils.setData(location, ENABLED_KEY, Boolean.toString(enabled));
    }

    static void render(Location location) {
        World world = location.getWorld();
        if (world == null
                || !world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)
                || !StorageCacheUtils.isBlock(location, BeaconPlusManager.ITEM_ID)) {
            return;
        }
        render(location.getBlock());
    }

    static void render(Block beaconBlock) {
        Location location = beaconBlock.getLocation();
        if (!isEnabled(location)) {
            return;
        }

        EnumSet<BeaconPlusEffect> effects = BeaconPlusRuntime.getConfiguredEffects(location);
        BeaconPlusFieldArea area = BeaconPlusRuntime.getEffectiveFieldArea(location, effects);
        render(beaconBlock, area);
    }

    private static void render(Block beaconBlock, BeaconPlusFieldArea area) {
        World world = beaconBlock.getWorld();
        int centerChunkX = beaconBlock.getX() >> 4;
        int centerChunkZ = beaconBlock.getZ() >> 4;
        int radius = area.getRadius();
        int minChunkX = centerChunkX - radius;
        int maxChunkX = centerChunkX + radius;
        int minChunkZ = centerChunkZ - radius;
        int maxChunkZ = centerChunkZ + radius;
        double y = Math.max(
                world.getMinHeight() + 1.0D,
                Math.min(world.getMaxHeight() - CORNER_HEIGHT - 1.0D, beaconBlock.getY() + BASE_Y_OFFSET));

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                boolean west = chunkX == minChunkX;
                boolean east = chunkX == maxChunkX;
                boolean north = chunkZ == minChunkZ;
                boolean south = chunkZ == maxChunkZ;
                if (!west && !east && !north && !south) {
                    continue;
                }
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    continue;
                }

                int x = chunkX;
                int z = chunkZ;
                Runnable render = () -> renderChunkEdges(world, x, z, y, west, east, north, south);
                if (Slimefun.getSchedulerService().isFolia()) {
                    Location anchor = new Location(world, (x << 4) + 8.0D, y, (z << 4) + 8.0D);
                    Slimefun.runSyncAt(anchor, render);
                } else {
                    render.run();
                }
            }
        }
    }

    private static void renderChunkEdges(
            World world, int chunkX, int chunkZ, double y, boolean west, boolean east, boolean north, boolean south) {
        double minX = (chunkX << 4) + EDGE_INSET;
        double maxX = ((chunkX + 1) << 4) - EDGE_INSET;
        double minZ = (chunkZ << 4) + EDGE_INSET;
        double maxZ = ((chunkZ + 1) << 4) - EDGE_INSET;

        if (north) {
            drawXEdge(world, minX, maxX, minZ, y);
        }
        if (south) {
            drawXEdge(world, minX, maxX, maxZ, y);
        }
        if (west) {
            drawZEdge(world, minZ, maxZ, minX, y);
        }
        if (east) {
            drawZEdge(world, minZ, maxZ, maxX, y);
        }

        if (west && north) {
            drawCorner(world, minX, y, minZ);
        }
        if (west && south) {
            drawCorner(world, minX, y, maxZ);
        }
        if (east && north) {
            drawCorner(world, maxX, y, minZ);
        }
        if (east && south) {
            drawCorner(world, maxX, y, maxZ);
        }
    }

    private static void drawXEdge(World world, double minX, double maxX, double z, double y) {
        for (double x = minX + 0.5D; x < maxX; x += PARTICLE_STEP) {
            world.spawnParticle(Particle.DUST, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D, FIELD_EDGE);
        }
    }

    private static void drawZEdge(World world, double minZ, double maxZ, double x, double y) {
        for (double z = minZ + 0.5D; z < maxZ; z += PARTICLE_STEP) {
            world.spawnParticle(Particle.DUST, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D, FIELD_EDGE);
        }
    }

    private static void drawCorner(World world, double x, double y, double z) {
        for (int offset = 0; offset < CORNER_HEIGHT; offset++) {
            world.spawnParticle(
                    Particle.DUST, x, y + offset, z, 1, 0.0D, 0.0D, 0.0D, 0.0D, FIELD_EDGE);
        }
        world.spawnParticle(Particle.ELECTRIC_SPARK, x, y + 1.5D, z, 3, 0.18D, 0.55D, 0.18D, 0.01D);
    }
}
