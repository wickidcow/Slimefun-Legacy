package io.github.thebusybiscuit.slimefun4.core.services.compatibility;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunInternal;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;

/** Immutable read-only view of one loaded plugin's dependency metadata. */
@SlimefunInternal
public final class PluginDependencySnapshot {

    private final String pluginName;
    private final String pluginVersion;
    private final boolean enabled;
    private final List<PluginDependencyResolution> requiredDependencies;
    private final List<PluginDependencyResolution> softDependencies;
    private final List<String> providedPlugins;

    PluginDependencySnapshot(
            @Nonnull String pluginName,
            @Nonnull String pluginVersion,
            boolean enabled,
            @Nonnull List<PluginDependencyResolution> requiredDependencies,
            @Nonnull List<PluginDependencyResolution> softDependencies,
            @Nonnull List<String> providedPlugins) {
        this.pluginName = Objects.requireNonNull(pluginName, "pluginName");
        this.pluginVersion = Objects.requireNonNull(pluginVersion, "pluginVersion");
        this.enabled = enabled;
        this.requiredDependencies = List.copyOf(Objects.requireNonNull(requiredDependencies, "requiredDependencies"));
        this.softDependencies = List.copyOf(Objects.requireNonNull(softDependencies, "softDependencies"));
        this.providedPlugins = List.copyOf(Objects.requireNonNull(providedPlugins, "providedPlugins"));
    }

    public @Nonnull String getPluginName() {
        return pluginName;
    }

    public @Nonnull String getPluginVersion() {
        return pluginVersion;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public @Nonnull List<PluginDependencyResolution> getRequiredDependencies() {
        return requiredDependencies;
    }

    public @Nonnull List<PluginDependencyResolution> getSoftDependencies() {
        return softDependencies;
    }

    public @Nonnull List<String> getProvidedPlugins() {
        return providedPlugins;
    }

    public boolean hasDeclaredDependencies() {
        return !requiredDependencies.isEmpty() || !softDependencies.isEmpty();
    }

    public boolean hasRequiredDependencyProblems() {
        return requiredDependencies.stream().anyMatch(PluginDependencyResolution::isProblem);
    }

    public long getRequiredDependencyProblemCount() {
        return requiredDependencies.stream().filter(PluginDependencyResolution::isProblem).count();
    }
}
