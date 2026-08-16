package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import java.util.Locale;
import javax.annotation.Nonnull;

/**
 * Bounded Activator coverage profiles for Beacon Plus.
 */
public enum BeaconPlusChunkMode {
    OFF("Off", 0, false),
    SINGLE("This Chunk", 0, true),
    AREA_3X3("3x3 Area", 1, true);

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
        BeaconPlusChunkMode[] values = values();
        return values[(ordinal() + 1) % values.length];
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
            default -> OFF;
        };
    }
}
