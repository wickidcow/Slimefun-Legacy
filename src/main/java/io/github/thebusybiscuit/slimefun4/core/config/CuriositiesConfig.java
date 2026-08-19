package io.github.thebusybiscuit.slimefun4.core.config;

import io.github.bakedlibs.dough.config.Config;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import javax.annotation.Nonnull;

/**
 * Dedicated configuration access for Adventurer's Curios and related Slimefun Legacy addon-style features.
 *
 * <p>This deliberately stays separate from Slimefun's generic {@code config.yml} so Legacy-only gameplay additions
 * do not leak their settings into the core configuration surface.
 */
public final class CuriositiesConfig {

    public static final String FILE_NAME = "configSFLAddons.yml";

    private static Config config;

    private CuriositiesConfig() {}

    /**
     * Returns the lazily loaded Slimefun Legacy addons configuration.
     *
     * @return the Slimefun Legacy addons configuration
     */
    public static synchronized @Nonnull Config getConfig() {
        if (config == null) {
            Slimefun plugin = Slimefun.instance();
            if (plugin == null) {
                throw new IllegalStateException("Cannot load " + FILE_NAME + " while Slimefun is disabled.");
            }
            config = new Config(plugin, FILE_NAME);
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
