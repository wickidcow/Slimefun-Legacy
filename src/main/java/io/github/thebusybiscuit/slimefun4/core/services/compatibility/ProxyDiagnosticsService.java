package io.github.thebusybiscuit.slimefun4.core.services.compatibility;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunInternal;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nonnull;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * Read-only proxy-forwarding diagnostics for Paper backends.
 *
 * <p>The backend cannot reliably identify whether a Bungee-compatible connection came from BungeeCord, Waterfall, or
 * Velocity legacy forwarding. This service therefore reports configuration evidence rather than guessing the proxy
 * brand.
 */
@SlimefunInternal
public final class ProxyDiagnosticsService {

    private final Plugin owner;

    public ProxyDiagnosticsService(@Nonnull Plugin owner) {
        this.owner = Objects.requireNonNull(owner, "owner");
    }

    public @Nonnull ProxyDiagnosticsSnapshot inspect() {
        Path serverRoot = locateServerRoot();
        Path paperGlobal = serverRoot.resolve("config").resolve("paper-global.yml");
        Path legacyPaper = serverRoot.resolve("paper.yml");
        Path spigot = serverRoot.resolve("spigot.yml");

        boolean paperGlobalFound = Files.isRegularFile(paperGlobal);
        boolean legacyPaperFound = !paperGlobalFound && Files.isRegularFile(legacyPaper);
        boolean paperConfigFound = paperGlobalFound || legacyPaperFound;
        boolean spigotConfigFound = Files.isRegularFile(spigot);

        YamlConfiguration paper = load(paperGlobalFound ? paperGlobal : legacyPaper);
        YamlConfiguration spigotConfig = load(spigot);

        boolean velocityEnabled = paperGlobalFound
                ? paper.getBoolean("proxies.velocity.enabled", false)
                : paper.getBoolean("settings.velocity-support.enabled", false);
        boolean velocityOnlineMode = paperGlobalFound
                ? paper.getBoolean("proxies.velocity.online-mode", true)
                : paper.getBoolean("settings.velocity-support.online-mode", true);
        String configuredSecret = paperGlobalFound
                ? paper.getString("proxies.velocity.secret", "")
                : paper.getString("settings.velocity-support.secret", "");
        boolean velocitySecretConfigured = !configuredSecret.isBlank() || hasVelocitySecretEnvironmentOverride();

        boolean bungeeEnabled = spigotConfig.getBoolean("settings.bungeecord", false);
        boolean bungeeOnlineMode = paperGlobalFound
                ? paper.getBoolean("proxies.bungee-cord.online-mode", true)
                : true;

        return analyze(
                owner.getServer().getOnlineMode(),
                velocityEnabled,
                velocityOnlineMode,
                velocitySecretConfigured,
                bungeeEnabled,
                bungeeOnlineMode,
                paperConfigFound,
                spigotConfigFound,
                legacyPaperFound);
    }

    static @Nonnull ProxyDiagnosticsSnapshot analyze(
            boolean backendOnlineMode,
            boolean velocityEnabled,
            boolean velocityOnlineMode,
            boolean velocitySecretConfigured,
            boolean bungeeEnabled,
            boolean bungeeOnlineMode,
            boolean paperConfigFound,
            boolean spigotConfigFound,
            boolean legacyPaperConfig) {
        List<String> warnings = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        ProxyDiagnosticsSnapshot.ForwardingMode mode;
        if (velocityEnabled && bungeeEnabled) {
            mode = ProxyDiagnosticsSnapshot.ForwardingMode.CONFLICTING;
            failures.add("Velocity modern forwarding and Bungee-compatible forwarding are both enabled.");
        } else if (velocityEnabled) {
            mode = ProxyDiagnosticsSnapshot.ForwardingMode.VELOCITY_MODERN;
        } else if (bungeeEnabled) {
            mode = ProxyDiagnosticsSnapshot.ForwardingMode.BUNGEE_COMPATIBLE;
        } else if (backendOnlineMode) {
            mode = ProxyDiagnosticsSnapshot.ForwardingMode.STANDALONE_OR_UNKNOWN;
        } else {
            mode = ProxyDiagnosticsSnapshot.ForwardingMode.UNSAFE_OFFLINE;
            failures.add("The backend is in offline mode but no supported player-information forwarding is enabled.");
        }

        if ((velocityEnabled || bungeeEnabled) && backendOnlineMode) {
            failures.add("Proxy forwarding is enabled while the backend still has online-mode=true.");
        }

        if (velocityEnabled && !velocitySecretConfigured) {
            failures.add("Velocity modern forwarding is enabled but no forwarding secret was found.");
        }

        if (!paperConfigFound) {
            warnings.add("Paper forwarding configuration was not found at config/paper-global.yml or paper.yml.");
        }

        if (!spigotConfigFound) {
            warnings.add("spigot.yml was not found, so Bungee-compatible forwarding could not be verified.");
        }

        if (legacyPaperConfig) {
            warnings.add("Legacy paper.yml Velocity settings were detected; current Paper uses config/paper-global.yml.");
        }

        if (bungeeEnabled) {
            warnings.add(
                    "Bungee-compatible forwarding cannot distinguish BungeeCord, Waterfall, or Velocity legacy mode from the backend.");
        }

        if (velocityEnabled && !velocityOnlineMode) {
            warnings.add("Paper expects the Velocity proxy itself to use online-mode=false.");
        }

        if (bungeeEnabled && !bungeeOnlineMode) {
            warnings.add("Paper expects the Bungee-compatible proxy itself to use online-mode=false.");
        }

        return new ProxyDiagnosticsSnapshot(
                mode,
                backendOnlineMode,
                velocityEnabled,
                velocityOnlineMode,
                velocitySecretConfigured,
                bungeeEnabled,
                bungeeOnlineMode,
                paperConfigFound,
                spigotConfigFound,
                legacyPaperConfig,
                warnings,
                failures);
    }

    private Path locateServerRoot() {
        Set<Path> candidates = new LinkedHashSet<>();
        candidates.add(Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize());

        File dataFolder = owner.getDataFolder();
        File pluginsFolder = dataFolder.getParentFile();
        if (pluginsFolder != null && pluginsFolder.getParentFile() != null) {
            candidates.add(pluginsFolder.getParentFile().toPath().toAbsolutePath().normalize());
        }

        candidates.add(owner.getServer().getWorldContainer().toPath().toAbsolutePath().normalize());

        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate.resolve("spigot.yml"))
                    || Files.isRegularFile(candidate.resolve("config").resolve("paper-global.yml"))
                    || Files.isRegularFile(candidate.resolve("paper.yml"))) {
                return candidate;
            }
        }

        return candidates.iterator().next();
    }

    private static YamlConfiguration load(Path path) {
        if (!Files.isRegularFile(path)) {
            return new YamlConfiguration();
        }
        return YamlConfiguration.loadConfiguration(path.toFile());
    }

    private static boolean hasVelocitySecretEnvironmentOverride() {
        try {
            String value = System.getenv("PAPER_VELOCITY_SECRET");
            return value != null && !value.isBlank();
        } catch (SecurityException ignored) {
            return false;
        }
    }
}
