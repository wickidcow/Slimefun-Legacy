package io.github.thebusybiscuit.slimefun4.api.integrations;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Immutable runtime status for a known or registered external integration. */
@SlimefunAPI
public final class ExternalIntegrationStatus {

    private final String integrationId;
    private final String displayName;
    private final String pluginName;
    private final String pluginVersion;
    private final boolean detected;
    private final boolean enabled;
    private final boolean providerRegistered;
    private final Set<ExternalIntegrationCapability> capabilities;
    private final String detail;

    public ExternalIntegrationStatus(
            @Nonnull String integrationId,
            @Nonnull String displayName,
            @Nonnull String pluginName,
            @Nullable String pluginVersion,
            boolean detected,
            boolean enabled,
            boolean providerRegistered,
            @Nonnull Set<ExternalIntegrationCapability> capabilities,
            @Nonnull String detail) {
        this.integrationId = Objects.requireNonNull(integrationId, "integrationId");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.pluginName = Objects.requireNonNull(pluginName, "pluginName");
        this.pluginVersion = pluginVersion;
        this.detected = detected;
        this.enabled = enabled;
        this.providerRegistered = providerRegistered;
        EnumSet<ExternalIntegrationCapability> copied = capabilities.isEmpty()
                ? EnumSet.noneOf(ExternalIntegrationCapability.class)
                : EnumSet.copyOf(capabilities);
        this.capabilities = Collections.unmodifiableSet(copied);
        this.detail = Objects.requireNonNull(detail, "detail");
    }

    public @Nonnull String getIntegrationId() {
        return integrationId;
    }

    public @Nonnull String getDisplayName() {
        return displayName;
    }

    public @Nonnull String getPluginName() {
        return pluginName;
    }

    public @Nullable String getPluginVersion() {
        return pluginVersion;
    }

    public boolean isDetected() {
        return detected;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isProviderRegistered() {
        return providerRegistered;
    }

    public @Nonnull Set<ExternalIntegrationCapability> getCapabilities() {
        return capabilities;
    }

    public @Nonnull String getDetail() {
        return detail;
    }
}
