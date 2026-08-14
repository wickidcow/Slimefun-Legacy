package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import java.util.Locale;
import javax.annotation.Nonnull;

/**
 * Defines the bounded chunk-loading profiles available to Beacon Plus.
 */
public enum BeaconPlusChunkMode {
    OFF("Off", 0),
    SINGLE("This Chunk", 0),
    AREA_3X3("3x3 Area", 1);

    private final String displayName;
    private final int radius;

    BeaconPlusChunkMode(String displayName, int radius) {
        this.displayName = displayName;
        this.radius = radius;
    }

    public @Nonnull String getDisplayName() {
        return displayName;
    }

    public int getRadius() {
        return radius;
    }

    public @Nonnull BeaconPlusChunkMode next() {
        BeaconPlusChunkMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static @Nonnull BeaconPlusChunkMode fromStored(String value) {
        if (value == null || value.isBlank()) {
            return OFF;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            // Public BeaconPlus effect/config identifiers retained as migration aliases.
            case "KEEP_CHUNK_LOADED", "CHUNK_ACTIVATOR", "LOCAL", "THIS_CHUNK", "SINGLE" -> SINGLE;
            case "AREA", "AREA_3X3", "3X3" -> AREA_3X3;
            default -> OFF;
        };
    }
}
