package io.github.thebusybiscuit.slimefun4.core.config;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Set;
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

    private static final int CURRENT_CONFIG_VERSION = 1;
    private static final String CONFIG_VERSION_PATH = "config-version";
    private static final String RESOURCE_PACK_GUIDE_MARKER =
            "# Slimefun Legacy resource-pack safety guide (config-version 1)";
    private static final String RETIRED_FILE_NAME = "curiosities.yml";
    private static final String LEGACY_MODULE_TOGGLE = "options.enable-non-original-slimefun-additions";
    private static final String LEGACY_ADDITIONS_ROOT = "SlimefunLegacyAddition";
    private static final String LEGACY_BEACON_ROOT = LEGACY_ADDITIONS_ROOT + ".PoweredBeacon";
    private static final String LEGACY_RESOURCE_PACK_ROOT = "resource-pack";
    private static final String DEFAULT_RESOURCE_PACK_URL =
            "https://github.com/wickidcow/SFL_RP_Official/releases/latest/download/SlimefunLegacyRP.zip";
    private static final Set<String> RETIRED_RESOURCE_PACK_URLS = Set.of(
            "https://cdn.modrinth.com/data/TznkVJky/versions/nwij66MR/Slimefun-ResourcePack.zip",
            "http://overlord.kicks-ass.org:8163/SlimefunLegacyRP.zip",
            "https://github.com/wickidcow/SFL_ResourePack_UnOfficial/releases/latest/download/SlimefunLegacyRP.zip",
            "https://github.com/wickidcow/Slimefun-Legacy/releases/latest/download/SlimefunLegacy-ResourcePack-1.21.11-26.3.zip");

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
     * <p>Fresh installations receive the bundled {@code configSFLAddons.yml} with Curiosities disabled by default.
     * Existing installations are migrated from the retired {@code curiosities.yml} file first, or from the former
     * generic {@code config.yml} keys when those keys are still present. An existing explicit enabled value is
     * preserved, but the migration never opts a server into Curiosities merely because Slimefun was already installed.
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

        // Do not restore the former Slimefun.isNewlyInstalled() auto-enable path here.
        // Creating configSFLAddons.yml must never opt a server into Curiosities by itself.
        if (createdFromBundledResource || (!copiedRetiredConfig && !contains("enabled"))) {
            boolean migrated = migrateLegacyCoreSettings();
            if (!migrated && !contains("enabled")) {
                setValue("enabled", false);
                save();
            }
        }

        migrateLegacyResourcePackSettings(createdFromBundledResource);
        migrateRetiredResourcePackUrl();
        ensureResourcePackDefaults();
        migrateConfigVersion();
    }

    /**
     * Applies one-time text-preserving migrations to configSFLAddons.yml.
     *
     * <p>The file version is intentionally independent from the plugin version. A normal Slimefun Legacy
     * update does not rewrite this file. Operator comments are only injected when this config schema version
     * advances, so server-owner formatting and notes are not churned on every startup.</p>
     */
    private void migrateConfigVersion() {
        int existingVersion = yaml.getInt(CONFIG_VERSION_PATH, 0);
        if (existingVersion >= CURRENT_CONFIG_VERSION) {
            return;
        }

        try {
            String contents = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            contents = writeConfigVersion(contents);
            contents = writeResourcePackSafetyGuide(contents);
            Files.writeString(file.toPath(), contents, StandardCharsets.UTF_8);

            yaml = YamlConfiguration.loadConfiguration(file);
            dirty = false;
            plugin.getLogger()
                    .info("Updated " + FILE_NAME + " config-version from " + existingVersion + " to "
                            + CURRENT_CONFIG_VERSION + " with the resource-pack safety steps.");
        } catch (IOException exception) {
            plugin.getLogger()
                    .log(
                            Level.WARNING,
                            "Could not apply the one-time " + FILE_NAME + " config-version migration. "
                                    + "Existing settings were left unchanged.",
                            exception);
        }
    }

    private String writeConfigVersion(@Nonnull String contents) {
        String versionLine = CONFIG_VERSION_PATH + ": " + CURRENT_CONFIG_VERSION;
        var versionPattern = java.util.regex.Pattern.compile("(?m)^" + CONFIG_VERSION_PATH + "\\s*:\\s*.*$");
        var versionMatcher = versionPattern.matcher(contents);
        if (versionMatcher.find()) {
            return versionMatcher.replaceFirst(java.util.regex.Matcher.quoteReplacement(versionLine));
        }

        int enabledIndex = contents.indexOf("\nenabled:");
        if (enabledIndex >= 0) {
            int insertAt = enabledIndex + 1;
            return contents.substring(0, insertAt) + versionLine + "\n\n" + contents.substring(insertAt);
        }
        return versionLine + "\n\n" + contents;
    }

    private String writeResourcePackSafetyGuide(@Nonnull String contents) {
        if (contents.contains(RESOURCE_PACK_GUIDE_MARKER)) {
            return contents;
        }

        int resourcePackIndex = contents.indexOf("\nresource-pack:");
        if (resourcePackIndex < 0) {
            resourcePackIndex = contents.indexOf("resource-pack:");
        }
        if (resourcePackIndex < 0) {
            return contents;
        }

        String guide = """
                # ---------------------------------------------------------------------------
                # Slimefun Legacy resource-pack safety guide (config-version 1)
                #
                # ENABLING THE LEGACY PACK ON AN EXISTING SERVER:
                # 1) Make a full backup and keep players offline / use maintenance mode.
                # 2) Leave resource-pack.enabled: false while auditing.
                # 3) Run: /sf doctor item-models enable-pack scan
                # 4) If the audit is correct, run: /sf doctor item-models enable-pack confirm
                # 5) Run /sf doctor status and wait for pending database writes to reach 0.
                # 6) Stop the server normally.
                # 7) Set resource-pack.enabled: true, then start the server.
                # 8) Verify with: /sf doctor item-models enable-pack scan
                #
                # DISABLING THE LEGACY PACK SENDER:
                # 1) Set resource-pack.enabled: false and restart normally.
                # 2) STOP THERE if another pack manager (ItemsAdder/Oraxen/etc.) still supplies
                #    the matching Slimefun models. Do NOT strip model data just because this sender is off.
                # 3) The sender toggle NEVER rewrites item-models.yml or stored ItemStacks.
                # 4) Servers specifically recovering from the historical v4.1.52 forced model migration
                #    should use /sf doctor item-models rollback-v52 and follow Doctor's printed steps.
                # 5) Never manually zero model mappings and mass-edit items without a backup and Doctor audit.
                # ---------------------------------------------------------------------------
                """;

        int insertAt = resourcePackIndex;
        if (contents.charAt(resourcePackIndex) == '\n') {
            insertAt++;
        }
        return contents.substring(0, insertAt) + guide + contents.substring(insertAt);
    }

    private void migrateRetiredResourcePackUrl() {
        String configuredUrl = getString(LEGACY_RESOURCE_PACK_ROOT + ".url");
        if (configuredUrl == null || !RETIRED_RESOURCE_PACK_URLS.contains(configuredUrl.trim())) {
            return;
        }

        setValue(LEGACY_RESOURCE_PACK_ROOT + ".url", DEFAULT_RESOURCE_PACK_URL);
        // A hash for the retired ZIP cannot be trusted for the replacement pack.
        setValue(LEGACY_RESOURCE_PACK_ROOT + ".sha1", "");

        if (save()) {
            plugin.getLogger()
                    .info("Updated a retired Slimefun Legacy resource-pack URL in " + FILE_NAME
                            + " to the recommended GitHub release URL.");
        } else {
            plugin.getLogger()
                    .warning("Could not persist the recommended Slimefun Legacy resource-pack URL in " + FILE_NAME
                            + "; the retired URL will be normalized at send time.");
        }
    }

    private void ensureResourcePackDefaults() {
        setDefaultValue(LEGACY_RESOURCE_PACK_ROOT + ".enabled", false);
        setDefaultValue(LEGACY_RESOURCE_PACK_ROOT + ".url", DEFAULT_RESOURCE_PACK_URL);
        setDefaultValue(LEGACY_RESOURCE_PACK_ROOT + ".sha1", "");
        setDefaultValue(LEGACY_RESOURCE_PACK_ROOT + ".required", false);
        setDefaultValue(LEGACY_RESOURCE_PACK_ROOT + ".prompt", "Slimefun Legacy resource pack");
        save();
    }

    private void migrateLegacyResourcePackSettings(boolean replaceBundledDefaults) {
        var core = plugin.getConfig();
        ConfigurationSection legacyResourcePack = core.getConfigurationSection(LEGACY_RESOURCE_PACK_ROOT);
        if (legacyResourcePack == null) {
            return;
        }

        for (var entry : legacyResourcePack.getValues(true).entrySet()) {
            if (!(entry.getValue() instanceof ConfigurationSection)) {
                String target = LEGACY_RESOURCE_PACK_ROOT + "." + entry.getKey();
                if (replaceBundledDefaults || !contains(target)) {
                    setValue(target, entry.getValue());
                }
            }
        }

        if (!save()) {
            plugin.getLogger()
                    .warning("Kept legacy resource-pack settings in config.yml because " + FILE_NAME
                            + " could not be saved successfully.");
            return;
        }

        core.set(LEGACY_RESOURCE_PACK_ROOT, null);
        plugin.saveConfig();
        plugin.getLogger().info("Migrated resource-pack settings from config.yml to " + FILE_NAME + ".");
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
