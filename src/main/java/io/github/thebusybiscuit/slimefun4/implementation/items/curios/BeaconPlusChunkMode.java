package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import java.util.Locale;

/** Bounded Activator coverage profiles for the Resonance Beacon. */
public enum BeaconPlusChunkMode {
    OFF("Off", 0, false),
    SINGLE("This Chunk", 0, true),
    AREA_3X3("3x3 Area", 1, true),
    AREA_5X5("5x5 Area", 2, true);

    private final String displayName;
    private final int radius;
    private final boolean active;

    BeaconPlusChunkMode(String displayName, int radius, boolean active) {
        this.displayName = displayName;
        this.radius = radius;
        this.active = active;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getRadius() {
        return radius;
    }

    public boolean isActive() {
        return active;
    }

    public static BeaconPlusChunkMode fromStored(String value) {
        if (value == null || value.isBlank()) {
            return OFF;
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.equals("KEEP_CHUNK_LOADED") || normalized.equals("CHUNK_ACTIVATOR")) {
            return SINGLE;
        }

        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return OFF;
        }
    }
}
