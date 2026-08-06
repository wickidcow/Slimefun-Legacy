package io.github.thebusybiscuit.slimefun4.api.addons;

import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;
import org.bukkit.plugin.Plugin;

/** Addon-facing registry and runtime diagnostics for cross-core compatibility declarations. */
@SlimefunAPI
public interface AddonCompatibilityService {

    @Nonnull
    SlimefunCoreVariant getRunningCoreVariant();

    void register(@Nonnull Plugin plugin, @Nonnull AddonCompatibilityDeclaration declaration);

    default void register(@Nonnull SlimefunAddon addon, @Nonnull AddonCompatibilityDeclaration declaration) {
        Objects.requireNonNull(addon, "addon");
        register(addon.getJavaPlugin(), declaration);
    }

    void unregister(@Nonnull Plugin plugin);

    /** Rebuilds the immutable compatibility snapshot for every installed Slimefun addon. */
    void refresh();

    @Nonnull
    List<AddonCompatibilityResult> getResults();

    @Nonnull
    AddonCompatibilityResult inspect(@Nonnull Plugin plugin);

    default @Nonnull Optional<AddonCompatibilityResult> getResult(@Nonnull String pluginName) {
        Objects.requireNonNull(pluginName, "pluginName");
        return getResults().stream()
                .filter(result -> result.getPluginName().equalsIgnoreCase(pluginName))
                .findFirst();
    }

    default @Nonnull AddonCompatibilitySummary getSummary() {
        return AddonCompatibilitySummary.from(getResults());
    }
}
