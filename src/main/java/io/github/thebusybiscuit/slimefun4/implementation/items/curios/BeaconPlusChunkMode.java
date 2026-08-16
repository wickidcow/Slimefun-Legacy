package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import java.util.Locale;
import javax.annotation.Nonnull;

/**
 * Bounded Activator coverage profiles for Beacon Plus.
 */
public enum BeaconPlusChunkMode {
    OFF("Off", 0, false),
    SINGLE("1x1 Chunks", 0, true),
    AREA_3X3("3x3 Chunks", 1, true),
    AREA_5X5("5x5 Chunks", 2, true);

    private final String displayName;
    private final int radius;
    private final boolean active;

    BeaconPlusChunkMode(String displayName, int radius, boolean active) {
        this.displayName = displayName;
        this.radius = radius;
        this.active = active;
    }

    public @Nonnull String getDisplayName() {
        return displayName;
    }

    public int getRadius() {
        return radius;
    }

    public boolean isActive() {
        return active;
    }

    public @Nonnull BeaconPlusChunkMode next() {
        return switch (this) {
            case OFF -> SINGLE;
            case SINGLE -> AREA_3X3;
            case AREA_3X3 -> AREA_5X5;
            case AREA_5X5 -> OFF;
        };
    }

    static @Nonnull BeaconPlusChunkMode forFieldArea(@Nonnull BeaconPlusFieldArea area) {
        return switch (area) {
            case CHUNK_1X1 -> SINGLE;
            case AREA_3X3 -> AREA_3X3;
            case AREA_5X5 -> AREA_5X5;
        };
    }

    public static @Nonnull BeaconPlusChunkMode fromStored(String value) {
        if (value == null || value.isBlank()) {
            return OFF;
        }

        String normalized =
                value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            // Historical/public identifiers retained as safe migration aliases.
            case "KEEP_CHUNK_LOADED", "CHUNK_ACTIVATOR", "LOCAL", "THIS_CHUNK", "SINGLE" -> SINGLE;
            case "AREA", "AREA_3X3", "3X3" -> AREA_3X3;
            case "AREA_5X5", "5X5", "LARGE" -> AREA_5X5;
            default -> OFF;
        };
    }
}
