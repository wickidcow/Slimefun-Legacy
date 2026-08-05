package io.github.thebusybiscuit.slimefun4.api.platform;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import javax.annotation.Nonnull;

/** Describes the support policy for the detected server platform. */
@SlimefunAPI
public enum PlatformSupportLevel {
    SUPPORTED("Supported"),
    EXPERIMENTAL("Experimental"),
    BEST_EFFORT("Best effort"),
    UNSUPPORTED("Unsupported"),
    UNKNOWN("Unknown");

    private final String displayName;

    PlatformSupportLevel(String displayName) {
        this.displayName = displayName;
    }

    public @Nonnull String getDisplayName() {
        return displayName;
    }
}
