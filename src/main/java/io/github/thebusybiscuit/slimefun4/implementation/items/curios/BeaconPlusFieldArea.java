package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import java.util.Locale;
import javax.annotation.Nonnull;
import org.bukkit.Location;

/** Shared chunk-aligned coverage used by every Beacon Plus effect. */
enum BeaconPlusFieldArea {
    CHUNK_1X1("1x1 Chunks", 0),
    AREA_3X3("3x3 Chunks", 1),
    AREA_5X5("5x5 Chunks", 2);

    static final BeaconPlusFieldArea DEFAULT = AREA_3X3;

    private final String displayName;
    private final int radius;

    BeaconPlusFieldArea(String displayName, int radius) {
        this.displayName = displayName;
        this.radius = radius;
    }

    @Nonnull
    String getDisplayName() {
        return displayName;
    }

    int getRadius() {
        return radius;
    }

    int getChunkCount() {
        int width = radius * 2 + 1;
        return width * width;
    }

    @Nonnull
    BeaconPlusFieldArea next() {
        return switch (this) {
            case CHUNK_1X1 -> AREA_3X3;
            case AREA_3X3 -> AREA_5X5;
            case AREA_5X5 -> CHUNK_1X1;
        };
    }

    @Nonnull
    BeaconPlusFieldArea expand() {
        return switch (this) {
            case CHUNK_1X1 -> AREA_3X3;
            case AREA_3X3, AREA_5X5 -> AREA_5X5;
        };
    }

    boolean contains(@Nonnull Location beacon, @Nonnull Location target) {
        if (beacon.getWorld() == null
                || target.getWorld() == null
                || !beacon.getWorld().equals(target.getWorld())) {
            return false;
        }
        return containsChunk(beacon.getBlockX(), beacon.getBlockZ(), target.getBlockX(), target.getBlockZ());
    }

    boolean containsChunk(int beaconBlockX, int beaconBlockZ, int targetBlockX, int targetBlockZ) {
        int beaconChunkX = beaconBlockX >> 4;
        int beaconChunkZ = beaconBlockZ >> 4;
        int targetChunkX = targetBlockX >> 4;
        int targetChunkZ = targetBlockZ >> 4;
        return Math.abs(targetChunkX - beaconChunkX) <= radius && Math.abs(targetChunkZ - beaconChunkZ) <= radius;
    }

    static @Nonnull BeaconPlusFieldArea fromStored(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }

        String normalized =
                value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "CHUNK_1X1", "AREA_1X1", "1X1", "SINGLE", "THIS_CHUNK" -> CHUNK_1X1;
            case "AREA_3X3", "3X3", "AREA", "DEFAULT" -> AREA_3X3;
            case "AREA_5X5", "5X5", "LARGE" -> AREA_5X5;
            default -> DEFAULT;
        };
    }
}
