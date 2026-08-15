package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;

/** Global operator safety switch for Resonance Beacon Activator chunk tickets. */
final class BeaconPlusChunkLoadingControl {

    static final String CONFIG_PATH = BeaconPlusConfig.ROOT + ".chunk-loading-enabled";

    private static final Pattern CONFIG_LINE = Pattern.compile(
            "(?m)^(\\s*)chunk-loading-enabled\\s*:\\s*(?:true|false)\\s*(?:#.*)?$",
            Pattern.CASE_INSENSITIVE);

    private static volatile boolean enabled = true;

    private BeaconPlusChunkLoadingControl() {}

    static void initialize(@Nonnull Slimefun plugin) {
        enabled = Slimefun.getCfg().getBoolean(CONFIG_PATH);
        plugin.getLogger().info("Resonance Beacon chunk loading is " + stateWord(enabled).toLowerCase(Locale.ROOT) + ".");
    }

    static boolean isEnabled() {
        return enabled;
    }

    /**
     * Persists the runtime switch without reserializing the whole YAML file, preserving existing comments and layout.
     */
    static synchronized boolean setEnabled(@Nonnull Slimefun plugin, boolean newValue) {
        if (enabled == newValue) {
            return true;
        }

        Path configPath = plugin.getDataFolder().toPath().resolve("config.yml");
        try {
            String current = Files.readString(configPath, StandardCharsets.UTF_8);
            Matcher matcher = CONFIG_LINE.matcher(current);
            if (!matcher.find()) {
                plugin.getLogger().severe("Could not find '" + CONFIG_PATH
                        + "' in config.yml. Resonance Beacon chunk-loading state was not changed.");
                return false;
            }

            String replacement = matcher.group(1) + "chunk-loading-enabled: " + newValue;
            String updated = matcher.replaceFirst(Matcher.quoteReplacement(replacement));
            Files.writeString(
                    configPath,
                    updated,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            enabled = newValue;
            return true;
        } catch (IOException exception) {
            plugin.getLogger().log(
                    Level.SEVERE,
                    "Could not persist Resonance Beacon chunk-loading state to config.yml.",
                    exception);
            return false;
        }
    }

    static String stateWord(boolean value) {
        return value ? "ENABLED" : "DISABLED";
    }
}
