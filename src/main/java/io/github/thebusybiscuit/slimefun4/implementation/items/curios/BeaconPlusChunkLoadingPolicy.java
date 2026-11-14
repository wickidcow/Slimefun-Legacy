package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import java.util.Locale;
import javax.annotation.Nullable;

/**
 * Global runtime policy, separate from each beacon's saved Activator coverage.
 * ONLINE uses the presence of any connected player, not a specific beacon owner.
 */
enum BeaconPlusChunkLoadingPolicy {
    ONLINE,
    ALWAYS,
    OFF;

    static BeaconPlusChunkLoadingPolicy fromConfig(@Nullable String configured, boolean legacyEnabled) {
        if (configured == null || configured.isBlank()) {
            return legacyEnabled ? ONLINE : OFF;
        }
        try {
            return valueOf(configured.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            // Invalid config values must never turn on unattended chunk loaders.
            return OFF;
        }
    }

    /** Regionized runtimes must not execute cross-region chunk-ticket mutations. */
    boolean shouldHoldTickets(boolean anyPlayerOnline, boolean regionOwned) {
        return !regionOwned && shouldHoldTickets(anyPlayerOnline);
    }

    boolean shouldHoldTickets(boolean anyPlayerOnline) {
        return switch (this) {
            case ONLINE -> anyPlayerOnline;
            case ALWAYS -> true;
            case OFF -> false;
        };
    }
}
