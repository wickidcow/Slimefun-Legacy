package io.github.thebusybiscuit.slimefun4.core.config;

import io.github.bakedlibs.dough.config.Config;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Dedicated configuration access for Adventurer's Curios and related Slimefun Legacy addon-style features.
 *
 * <p>This deliberately stays separate from Slimefun's generic {@code config.yml} so Legacy-only gameplay additions
 * do not leak their settings into the core configuration surface.
 */
public final class CuriositiesConfig {

    public static final String FILE_NAME = "configSFLAddons.yml";

    private static final String RETIRED_FILE_NAME = "curiosities.yml";
    private static final String LEGACY_MODULE_TOGGLE = "options.enable-non-original-slimefun-additions";
    private static final String LEGACY_BEACON_ROOT = "SlimefunLegacyAddition.PoweredBeacon";

    private static Config config;

    private CuriositiesConfig() {}

    /**
     * Returns the lazily loaded Slimefun Legacy addons configuration.
     *
     * <p>Fresh installations receive the bundled {@code configSFLAddons.yml}. Existing installations are migrated
     * from the retired {@code curiosities.yml} file first, or from the former generic {@code config.yml} keys when
     * those keys are still present.
     *
     * @return the Slimefun Legacy addons configuration
     */
    public static synchronized @Nonnull Config getConfig() {
        if (config == null) {
            Slimefun plugin = Slimefun.instance();
            if (plugin == null) {
                throw new IllegalStateException("Cannot load " + FILE_NAME + " while Slimefun is disabled.");
            }

            File target = new File(plugin.getDataFolder(), FILE_NAME);
            File retired = new File(plugin.getDataFolder(), RETIRED_FILE_NAME);
            boolean copiedRetiredConfig = false;
            boolean createdFromBundledResource = false;

            if (!target.isFile() && retired.isFile()) {
                try {
                    Files.copy(retired.toPath(), target.toPath());
                    copiedRetiredConfig = true;
                    plugin.getLogger().info("Migrated " + RETIRED_FILE_NAME + " to " + FILE_NAME + ".");
                } catch (IOException exception) {
                    plugin.getLogger()
                            .log(
                                    Level.WARNING,
                                    "Could not copy " + RETIRED_FILE_NAME + " to " + FILE_NAME
                                            + "; falling back to the bundled addons configuration.",
                                    exception);
                }
            }

            if (!target.isFile()) {
                plugin.saveResource(FILE_NAME, false);
                createdFromBundledResource = true;
            }

            config = new Config(plugin, FILE_NAME);

            if (createdFromBundledResource || (!copiedRetiredConfig && !config.contains("enabled"))) {
                boolean migrated = migrateLegacyCoreSettings(plugin, config);
                if (!migrated && !config.contains("enabled")) {
                    config.setValue("enabled", false);
                    config.save();
                }
            }
        }

        return config;
    }

    private static boolean migrateLegacyCoreSettings(@Nonnull Slimefun plugin, @Nonnull Config target) {
        Config core = Slimefun.getCfg();
        boolean hasLegacyToggle = core.contains(LEGACY_MODULE_TOGGLE);
        ConfigurationSection legacyBeacon = core.getConfiguration().getConfigurationSection(LEGACY_BEACON_ROOT);

        if (!hasLegacyToggle && legacyBeacon == null) {
            return false;
        }

        if (hasLegacyToggle) {
            target.setValue("enabled", core.getBoolean(LEGACY_MODULE_TOGGLE));
        } else {
            // The old Beacon tree only existed on Curios-enabled development builds.
            target.setValue("enabled", true);
        }

        if (legacyBeacon != null) {
            for (var entry : legacyBeacon.getValues(true).entrySet()) {
                if (!(entry.getValue() instanceof ConfigurationSection)) {
                    target.setValue(LEGACY_BEACON_ROOT + "." + entry.getKey(), entry.getValue());
                }
            }
        }

        target.save();
        plugin.getLogger().info("Migrated existing Adventurer's Curios settings from config.yml to " + FILE_NAME + ".");
        return true;
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
