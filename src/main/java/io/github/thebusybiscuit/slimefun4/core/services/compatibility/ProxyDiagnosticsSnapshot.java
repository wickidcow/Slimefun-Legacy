package io.github.thebusybiscuit.slimefun4.core.services.compatibility;

import java.util.List;
import javax.annotation.Nonnull;

/** Read-only snapshot of the backend's proxy-forwarding configuration. */
public final class ProxyDiagnosticsSnapshot {

    public enum ForwardingMode {
        VELOCITY_MODERN,
        BUNGEE_COMPATIBLE,
        CONFLICTING,
        STANDALONE_OR_UNKNOWN,
        UNSAFE_OFFLINE
    }

    private final ForwardingMode forwardingMode;
    private final boolean backendOnlineMode;
    private final boolean velocityEnabled;
    private final boolean velocityOnlineMode;
    private final boolean velocitySecretConfigured;
    private final boolean bungeeEnabled;
    private final boolean bungeeOnlineMode;
    private final boolean paperConfigFound;
    private final boolean spigotConfigFound;
    private final boolean legacyPaperConfig;
    private final List<String> warnings;
    private final List<String> failures;

    ProxyDiagnosticsSnapshot(
            @Nonnull ForwardingMode forwardingMode,
            boolean backendOnlineMode,
            boolean velocityEnabled,
            boolean velocityOnlineMode,
            boolean velocitySecretConfigured,
            boolean bungeeEnabled,
            boolean bungeeOnlineMode,
            boolean paperConfigFound,
            boolean spigotConfigFound,
            boolean legacyPaperConfig,
            @Nonnull List<String> warnings,
            @Nonnull List<String> failures) {
        this.forwardingMode = forwardingMode;
        this.backendOnlineMode = backendOnlineMode;
        this.velocityEnabled = velocityEnabled;
        this.velocityOnlineMode = velocityOnlineMode;
        this.velocitySecretConfigured = velocitySecretConfigured;
        this.bungeeEnabled = bungeeEnabled;
        this.bungeeOnlineMode = bungeeOnlineMode;
        this.paperConfigFound = paperConfigFound;
        this.spigotConfigFound = spigotConfigFound;
        this.legacyPaperConfig = legacyPaperConfig;
        this.warnings = List.copyOf(warnings);
        this.failures = List.copyOf(failures);
    }

    public @Nonnull ForwardingMode getForwardingMode() {
        return forwardingMode;
    }

    public boolean isBackendOnlineMode() {
        return backendOnlineMode;
    }

    public boolean isVelocityEnabled() {
        return velocityEnabled;
    }

    public boolean isVelocityOnlineMode() {
        return velocityOnlineMode;
    }

    public boolean isVelocitySecretConfigured() {
        return velocitySecretConfigured;
    }

    public boolean isBungeeEnabled() {
        return bungeeEnabled;
    }

    public boolean isBungeeOnlineMode() {
        return bungeeOnlineMode;
    }

    public boolean isPaperConfigFound() {
        return paperConfigFound;
    }

    public boolean isSpigotConfigFound() {
        return spigotConfigFound;
    }

    public boolean isLegacyPaperConfig() {
        return legacyPaperConfig;
    }

    public @Nonnull List<String> getWarnings() {
        return warnings;
    }

    public @Nonnull List<String> getFailures() {
        return failures;
    }

    public boolean isHealthy() {
        return failures.isEmpty();
    }
}
