package io.github.thebusybiscuit.slimefun4.core.config;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

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
    private static final String LEGACY_ADDITIONS_ROOT = "SlimefunLegacyAddition";
    private static final String LEGACY_BEACON_ROOT = LEGACY_ADDITIONS_ROOT + ".PoweredBeacon";

    private static CuriositiesConfig config;

    private final Slimefun plugin;
    private final File file;
    private YamlConfiguration yaml;
    private boolean dirty;

    private CuriositiesConfig(@Nonnull Slimefun plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), FILE_NAME);
        initialize();
    }

    /**
     * Returns the lazily loaded Slimefun Legacy addons configuration.
     *
     * <p>Fresh installations receive the bundled {@code configSFLAddons.yml}. Existing installations are migrated
     * from the retired {@code curiosities.yml} file first, or from the former generic {@code config.yml} keys when
     * those keys are still present.
     *
     * @return the Slimefun Legacy addons configuration
     */
    public static synchronized @Nonnull CuriositiesConfig getConfig() {
        if (config == null) {
            Slimefun plugin = Slimefun.instance();
            if (plugin == null) {
                throw new IllegalStateException("Cannot load " + FILE_NAME + " while Slimefun is disabled.");
            }
            config = new CuriositiesConfig(plugin);
        }

        return config;
    }

    private void initialize() {
        File retired = new File(plugin.getDataFolder(), RETIRED_FILE_NAME);
        boolean copiedRetiredConfig = false;
        boolean createdFromBundledResource = false;

        if (!file.isFile() && retired.isFile()) {
            try {
                Files.copy(retired.toPath(), file.toPath());
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

        if (!file.isFile()) {
            plugin.saveResource(FILE_NAME, false);
            createdFromBundledResource = true;
        }

        yaml = YamlConfiguration.loadConfiguration(file);
        dirty = false;

        if (createdFromBundledResource || (!copiedRetiredConfig && !contains("enabled"))) {
            boolean migrated = migrateLegacyCoreSettings();
            if (!migrated && !contains("enabled")) {
                setValue("enabled", false);
                save();
            }
        }
    }

    private boolean migrateLegacyCoreSettings() {
        var core = plugin.getConfig();
        boolean hasLegacyToggle = core.contains(LEGACY_MODULE_TOGGLE);
        ConfigurationSection legacyBeacon = core.getConfigurationSection(LEGACY_BEACON_ROOT);

        if (!hasLegacyToggle && legacyBeacon == null) {
            return false;
        }

        if (hasLegacyToggle) {
            setValue("enabled", core.getBoolean(LEGACY_MODULE_TOGGLE));
        } else {
            // The old Beacon tree only existed on Curios-enabled development builds.
            setValue("enabled", true);
        }

        if (legacyBeacon != null) {
            for (var entry : legacyBeacon.getValues(true).entrySet()) {
                if (!(entry.getValue() instanceof ConfigurationSection)) {
                    setValue(LEGACY_BEACON_ROOT + "." + entry.getKey(), entry.getValue());
                }
            }
        }

        if (!save()) {
            plugin.getLogger()
                    .warning("Kept legacy Adventurer's Curios settings in config.yml because " + FILE_NAME
                            + " could not be saved successfully.");
            return true;
        }

        cleanupLegacyCoreSettings();
        plugin.getLogger().info("Migrated existing Adventurer's Curios settings from config.yml to " + FILE_NAME + ".");
        return true;
    }

    /**
     * Removes only the retired Curiosities keys after the replacement file has been written successfully.
     * Other generic Slimefun settings and unrelated Slimefun Legacy additions are preserved.
     */
    private void cleanupLegacyCoreSettings() {
        var core = plugin.getConfig();
        core.set(LEGACY_MODULE_TOGGLE, null);
        core.set(LEGACY_BEACON_ROOT, null);

        ConfigurationSection additions = core.getConfigurationSection(LEGACY_ADDITIONS_ROOT);
        if (additions != null && additions.getKeys(false).isEmpty()) {
            core.set(LEGACY_ADDITIONS_ROOT, null);
        }

        plugin.saveConfig();
        plugin.getLogger().info("Removed migrated Adventurer's Curios keys from config.yml.");
    }

    public boolean contains(@Nonnull String path) {
        return yaml.contains(path);
    }

    public boolean getBoolean(@Nonnull String path) {
        return yaml.getBoolean(path);
    }

    public int getInt(@Nonnull String path) {
        return yaml.getInt(path);
    }

    public double getDouble(@Nonnull String path) {
        return yaml.getDouble(path);
    }

    public @Nullable String getString(@Nonnull String path) {
        return yaml.getString(path);
    }

    public void setDefaultValue(@Nonnull String path, @Nullable Object value) {
        if (!contains(path)) {
            setValue(path, value);
        }
    }

    private void setValue(@Nonnull String path, @Nullable Object value) {
        yaml.set(path, value);
        dirty = true;
    }

    /** Saves pending default or migration changes, leaving an unchanged bundled file untouched. */
    public synchronized boolean save() {
        if (!dirty) {
            return true;
        }

        try {
            yaml.save(file);
            dirty = false;
            return true;
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save " + FILE_NAME + ".", exception);
            return false;
        }
    }

    /** Reloads the addons configuration after an intentional direct file edit. */
    public synchronized void reload() {
        yaml = YamlConfiguration.loadConfiguration(file);
        dirty = false;
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
