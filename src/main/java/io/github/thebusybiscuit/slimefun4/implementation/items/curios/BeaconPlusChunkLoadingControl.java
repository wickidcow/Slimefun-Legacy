package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import io.github.thebusybiscuit.slimefun4.core.config.CuriositiesConfig;
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
import org.bukkit.Bukkit;

/**
 * Global operator policy for the Resonance Beacon Activator's Paper chunk tickets.
 * Configured beacon modes are never erased when ticket holding is suspended.
 */
final class BeaconPlusChunkLoadingControl {

    static final String CONFIG_PATH = BeaconPlusConfig.ROOT + ".chunk-loading-mode";
    static final String LEGACY_CONFIG_PATH = BeaconPlusConfig.ROOT + ".chunk-loading-enabled";

    private static final Pattern MODE_LINE = Pattern.compile(
            "(?m)^(\\s{4})chunk-loading-mode\\s*:\\s*(?:ONLINE|ALWAYS|OFF)\\s*(?:#.*)?$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LEGACY_LINE = Pattern.compile(
            "(?m)^(\\s{4})chunk-loading-enabled\\s*:\\s*(?:true|false)\\s*(?:#.*)?$",
            Pattern.CASE_INSENSITIVE);

    private static volatile BeaconPlusChunkLoadingPolicy mode = BeaconPlusChunkLoadingPolicy.ONLINE;

    private BeaconPlusChunkLoadingControl() {}

    static void initialize(@Nonnull Slimefun plugin) {
        CuriositiesConfig config = CuriositiesConfig.getConfig();
        mode = BeaconPlusChunkLoadingPolicy.fromConfig(
                config.getString(CONFIG_PATH), config.getBoolean(LEGACY_CONFIG_PATH));
        plugin.getLogger().info("Resonance Beacon chunk-loading policy: " + mode + ".");
    }

    static BeaconPlusChunkLoadingPolicy getMode() {
        return mode;
    }

    static boolean isEnabled() {
        return mode != BeaconPlusChunkLoadingPolicy.OFF;
    }

    static boolean shouldHoldTickets() {
        return mode.shouldHoldTickets(!Bukkit.getOnlinePlayers().isEmpty());
    }

    /** Legacy administrator command: enable now means the safer ONLINE policy. */
    static synchronized boolean setEnabled(@Nonnull Slimefun plugin, boolean enabled) {
        return setMode(plugin, enabled ? BeaconPlusChunkLoadingPolicy.ONLINE : BeaconPlusChunkLoadingPolicy.OFF);
    }

    /**
     * Persists the mode without reserializing the whole YAML document. The
     * retired boolean is retained as a compatibility mirror, not a second
     * active gate. Explicit mode always takes precedence over it.
     */
    static synchronized boolean setMode(@Nonnull Slimefun plugin, @Nonnull BeaconPlusChunkLoadingPolicy requested) {
        if (mode == requested) {
            return true;
        }

        Path path = plugin.getDataFolder().toPath().resolve(CuriositiesConfig.FILE_NAME);
        try {
            String contents = Files.readString(path, StandardCharsets.UTF_8);
            Matcher modeMatcher = MODE_LINE.matcher(contents);
            if (!modeMatcher.find()) {
                plugin.getLogger().severe("Cannot find '" + CONFIG_PATH + "' in "
                        + CuriositiesConfig.FILE_NAME + "; beacon policy unchanged.");
                return false;
            }
            String updated = modeMatcher.replaceFirst(Matcher.quoteReplacement(
                    modeMatcher.group(1) + "chunk-loading-mode: " + requested.name()));

            Matcher legacy = LEGACY_LINE.matcher(updated);
            if (legacy.find()) {
                String legacyLine = legacy.group(1) + "chunk-loading-enabled: "
                        + (requested != BeaconPlusChunkLoadingPolicy.OFF);
                updated = legacy.replaceFirst(Matcher.quoteReplacement(legacyLine));
            }

            Files.writeString(
                    path,
                    updated,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            CuriositiesConfig.getConfig().reload();
            mode = requested;
            return true;
        } catch (IOException exception) {
            plugin.getLogger().log(
                    Level.SEVERE, "Could not persist Resonance Beacon chunk policy to "
                            + CuriositiesConfig.FILE_NAME + ".", exception);
            return false;
        }
    }

    static String stateWord(boolean enabled) {
        return enabled ? "ENABLED" : "DISABLED";
    }
}
