package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

/** Shared chunk-aligned horizontal footprint for Resonance Beacon field powers. */
final class BeaconPlusField {

    private static final double CHUNK_SIZE = 16.0D;

    private BeaconPlusField() {}

    static int chunkRadius(double range) {
        if (!Double.isFinite(range) || range <= 0.0D) {
            return 0;
        }
        return Math.max(0, (int) Math.ceil(range / CHUNK_SIZE) - 1);
    }

    static ChunkFootprint footprint(int beaconBlockX, int beaconBlockZ, double range) {
        int radius = chunkRadius(range);
        int centerChunkX = beaconBlockX >> 4;
        int centerChunkZ = beaconBlockZ >> 4;
        return new ChunkFootprint(
                centerChunkX - radius, centerChunkX + radius, centerChunkZ - radius, centerChunkZ + radius);
    }

    static boolean contains(int beaconBlockX, int beaconBlockZ, double range, int targetBlockX, int targetBlockZ) {
        return range > 0.0D
                && footprint(beaconBlockX, beaconBlockZ, range).containsChunk(targetBlockX >> 4, targetBlockZ >> 4);
    }

    record ChunkFootprint(int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {

        int widthChunks() {
            return maxChunkX - minChunkX + 1;
        }

        int minBlockX() {
            return minChunkX << 4;
        }

        int maxBlockXExclusive() {
            return (maxChunkX + 1) << 4;
        }

        int minBlockZ() {
            return minChunkZ << 4;
        }

        int maxBlockZExclusive() {
            return (maxChunkZ + 1) << 4;
        }

        boolean containsChunk(int chunkX, int chunkZ) {
            return chunkX >= minChunkX && chunkX <= maxChunkX && chunkZ >= minChunkZ && chunkZ <= maxChunkZ;
        }
    }
}
