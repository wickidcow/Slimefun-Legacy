package io.github.thebusybiscuit.slimefun4.core.config;

import io.github.bakedlibs.dough.config.Config;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import javax.annotation.Nonnull;

/**
 * Dedicated configuration for Adventurer's Curios and its related Legacy-only features.
 *
 * <p>This deliberately stays separate from Slimefun's generic {@code config.yml} so addon-style gameplay additions
 * do not leak their settings into the core configuration surface.
 */
public final class CuriositiesConfig {

    private static Config config;

    private CuriositiesConfig() {}

    /**
     * Returns the lazily loaded {@code curiosities.yml} configuration.
     *
     * @return the Curiosities configuration
     */
    public static synchronized @Nonnull Config getConfig() {
        if (config == null) {
            Slimefun plugin = Slimefun.instance();
            if (plugin == null) {
                throw new IllegalStateException("Cannot load curiosities.yml while Slimefun is disabled.");
            }
            config = new Config(plugin, "curiosities.yml");
        }

        return config;
    }

    /**
     * Returns whether the Adventurer's Curios module is enabled.
     *
     * @return whether Curiosities content should be registered
     */
    public static boolean isEnabled() {
        return getConfig().getBoolean("enabled");
    }
}
