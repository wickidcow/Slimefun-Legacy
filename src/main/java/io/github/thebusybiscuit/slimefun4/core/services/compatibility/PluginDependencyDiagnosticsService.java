package io.github.thebusybiscuit.slimefun4.core.services.compatibility;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunInternal;
import io.papermc.paper.plugin.configuration.PluginMeta;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

/**
 * Read-only diagnostics for Bukkit/Paper plugin dependency metadata.
 *
 * <p>This service deliberately does not install, enable, disable, replace, or emulate dependencies. A provider alias is
 * reported as descriptor-level resolution only; it is not treated as proof that another plugin's expected classes or
 * runtime behavior are compatible.
 */
@SlimefunInternal
public final class PluginDependencyDiagnosticsService {

    private final Plugin owner;

    public PluginDependencyDiagnosticsService(@Nonnull Plugin owner) {
        this.owner = Objects.requireNonNull(owner, "owner");
    }

    public @Nonnull List<PluginDependencySnapshot> getSnapshots() {
        PluginManager pluginManager = pluginManager();
        return Arrays.stream(pluginManager.getPlugins())
                .map(this::inspect)
                .sorted(Comparator.comparing(PluginDependencySnapshot::getPluginName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public @Nonnull Optional<PluginDependencySnapshot> findPlugin(@Nonnull String pluginName) {
        String normalized = normalize(pluginName);
        return Arrays.stream(pluginManager().getPlugins())
                .filter(plugin -> plugin.getPluginMeta().getName().equalsIgnoreCase(normalized))
                .findFirst()
                .map(this::inspect);
    }

    public @Nonnull List<PluginDependencySnapshot> getRequiredConsumers(@Nonnull String dependencyName) {
        String normalized = normalize(dependencyName);
        return getSnapshots().stream()
                .filter(snapshot -> snapshot.getRequiredDependencies().stream()
                        .anyMatch(dependency -> matchesDependency(dependency, normalized)))
                .toList();
    }

    public @Nonnull List<PluginDependencySnapshot> getSoftConsumers(@Nonnull String dependencyName) {
        String normalized = normalize(dependencyName);
        return getSnapshots().stream()
                .filter(snapshot -> snapshot.getSoftDependencies().stream()
                        .anyMatch(dependency -> matchesDependency(dependency, normalized)))
                .toList();
    }

    public @Nonnull PluginDependencyResolution resolveDependency(@Nonnull String dependencyName) {
        return resolve(normalize(dependencyName), true);
    }

    private PluginDependencySnapshot inspect(Plugin plugin) {
        PluginMeta meta = plugin.getPluginMeta();
        List<PluginDependencyResolution> required = meta.getPluginDependencies().stream()
                .map(name -> resolve(name, true))
                .toList();
        List<PluginDependencyResolution> soft = meta.getPluginSoftDependencies().stream()
                .map(name -> resolve(name, false))
                .toList();
        return new PluginDependencySnapshot(
                meta.getName(),
                meta.getVersion(),
                pluginManager().isPluginEnabled(plugin),
                required,
                soft,
                meta.getProvidedPlugins());
    }

    private PluginDependencyResolution resolve(String declaredName, boolean required) {
        PluginManager pluginManager = pluginManager();
        Plugin resolved = pluginManager.getPlugin(declaredName);
        if (resolved == null) {
            resolved = findActualPlugin(declaredName).orElse(null);
        }
        if (resolved == null) {
            resolved = findProvider(declaredName).orElse(null);
        }
        if (resolved == null) {
            return new PluginDependencyResolution(
                    declaredName, required, PluginDependencyResolution.State.MISSING, null, null, false);
        }

        PluginMeta meta = resolved.getPluginMeta();
        boolean enabled = pluginManager.isPluginEnabled(resolved);
        boolean providerAlias = !meta.getName().equalsIgnoreCase(declaredName);
        return new PluginDependencyResolution(
                declaredName,
                required,
                enabled ? PluginDependencyResolution.State.ENABLED : PluginDependencyResolution.State.DISABLED,
                meta.getName(),
                meta.getVersion(),
                providerAlias);
    }

    private Optional<Plugin> findActualPlugin(String pluginName) {
        return Arrays.stream(pluginManager().getPlugins())
                .filter(plugin -> plugin.getPluginMeta().getName().equalsIgnoreCase(pluginName))
                .findFirst();
    }

    private Optional<Plugin> findProvider(String dependencyName) {
        List<Plugin> candidates = new ArrayList<>();
        for (Plugin plugin : pluginManager().getPlugins()) {
            if (plugin.getPluginMeta().getProvidedPlugins().stream()
                    .anyMatch(provided -> provided.equalsIgnoreCase(dependencyName))) {
                candidates.add(plugin);
            }
        }
        return candidates.stream()
                .sorted(Comparator.comparing((Plugin plugin) -> !pluginManager().isPluginEnabled(plugin))
                        .thenComparing(plugin -> plugin.getPluginMeta().getName(), String.CASE_INSENSITIVE_ORDER))
                .findFirst();
    }

    private static boolean matchesDependency(PluginDependencyResolution dependency, String target) {
        if (dependency.getDeclaredName().equalsIgnoreCase(target)) {
            return true;
        }
        String resolvedName = dependency.getResolvedPluginName();
        return resolvedName != null && resolvedName.equalsIgnoreCase(target);
    }

    private PluginManager pluginManager() {
        return owner.getServer().getPluginManager();
    }

    private static String normalize(String value) {
        String normalized = Objects.requireNonNull(value, "value").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Plugin/dependency name cannot be blank");
        }
        return normalized;
    }
}
