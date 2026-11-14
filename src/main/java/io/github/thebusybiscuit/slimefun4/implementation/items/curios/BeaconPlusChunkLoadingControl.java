package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import io.github.thebusybiscuit.slimefun4.core.config.CuriositiesConfig;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
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
            "(?mi)^([ \t]{4}chunk-loading-mode[ \t]*:[ \t]*)(ONLINE|ALWAYS|OFF)([ \t]*(?:#.*)?)$");
    private static final Pattern LEGACY_LINE = Pattern.compile(
            "(?mi)^([ \t]{4}chunk-loading-enabled[ \t]*:[ \t]*)(true|false)([ \t]*(?:#.*)?)$");

    private static volatile BeaconPlusChunkLoadingPolicy mode = BeaconPlusChunkLoadingPolicy.ONLINE;

    private BeaconPlusChunkLoadingControl() {}

    static void initialize(@Nonnull Slimefun plugin) {
        CuriositiesConfig config = CuriositiesConfig.getConfig();
        mode = BeaconPlusChunkLoadingPolicy.fromConfig(
                config.getString(CONFIG_PATH), config.getBoolean(LEGACY_CONFIG_PATH));
        plugin.getLogger().info("Resonance Beacon chunk-loading policy: " + mode + ".");
        if (Slimefun.getSchedulerService().isFolia()) {
            plugin.getLogger().warning("Resonance Beacon chunk tickets are disabled on region-owned runtimes. "
                    + "The configured mode and saved Activator selections are preserved; "
                    + "Paper-style cross-region ticket calls will not be attempted.");
        }
    }

    static BeaconPlusChunkLoadingPolicy getMode() {
        return mode;
    }

    static boolean isEnabled() {
        return mode != BeaconPlusChunkLoadingPolicy.OFF && !Slimefun.getSchedulerService().isFolia();
    }

    static boolean shouldHoldTickets() {
        return mode.shouldHoldTickets(
                !Bukkit.getOnlinePlayers().isEmpty(), Slimefun.getSchedulerService().isFolia());
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
        if (requested != BeaconPlusChunkLoadingPolicy.OFF && Slimefun.getSchedulerService().isFolia()) {
            plugin.getLogger().warning("Refusing region-unsafe Resonance Beacon chunk ticket mode " + requested
                    + "; no saved beacon or configuration data was changed.");
            return false;
        }
        if (mode == requested) {
            return true;
        }

        Path path = plugin.getDataFolder().toPath().resolve(CuriositiesConfig.FILE_NAME);
        try {
            String contents = Files.readString(path, StandardCharsets.UTF_8);
            String updated = rewriteMode(contents, requested);
            if (updated == null) {
                plugin.getLogger().severe("Cannot find '" + CONFIG_PATH + "' in "
                        + CuriositiesConfig.FILE_NAME + "; beacon policy unchanged.");
                return false;
            }

            // A failure must not truncate the operator's existing YAML.
            Path pending = Files.createTempFile(path.getParent(), "configSFLAddons-", ".tmp");
            try {
                Files.writeString(pending, updated, StandardCharsets.UTF_8);
                try {
                    Files.move(pending, path, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException unsupported) {
                    Files.move(pending, path, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(pending);
            }
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

    /**
     * Pure YAML line rewrite used by the operator command and its unit tests.
     * Match spaces/tabs only: Java regex \\s can consume an adjacent newline
     * and erase the operator's next comment or setting.
     */
    static String rewriteMode(String contents, BeaconPlusChunkLoadingPolicy requested) {
        Matcher modeMatcher = MODE_LINE.matcher(contents);
        if (!modeMatcher.find()) {
            return null;
        }
        String updated = modeMatcher.replaceFirst(Matcher.quoteReplacement(
                modeMatcher.group(1) + requested.name() + modeMatcher.group(3)));

        Matcher legacy = LEGACY_LINE.matcher(updated);
        if (legacy.find()) {
            updated = legacy.replaceFirst(Matcher.quoteReplacement(
                    legacy.group(1) + (requested != BeaconPlusChunkLoadingPolicy.OFF) + legacy.group(3)));
        }
        return updated;
    }

    static String stateWord(boolean enabled) {
        return enabled ? "ENABLED" : "DISABLED";
    }
}
