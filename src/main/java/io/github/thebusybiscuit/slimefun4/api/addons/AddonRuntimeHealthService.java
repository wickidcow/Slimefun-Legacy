package io.github.thebusybiscuit.slimefun4.api.addons;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nonnull;
import org.bukkit.plugin.Plugin;

/** Runtime compatibility telemetry for addon and optional-integration callback failures. */
@SlimefunAPI
public interface AddonRuntimeHealthService {

    /** Records a callback failure without disabling the owning plugin. */
    void recordFailure(@Nonnull Plugin plugin, @Nonnull String operation, @Nonnull Throwable failure);

    @Nonnull
    List<AddonRuntimeFailureSnapshot> getFailures();

    long getObservedFailureCount();

    boolean clear(@Nonnull String pluginName);

    int clearAll();

    default @Nonnull Optional<AddonRuntimeFailureSnapshot> getFailure(@Nonnull String pluginName) {
        Objects.requireNonNull(pluginName, "pluginName");
        return getFailures().stream()
                .filter(snapshot -> snapshot.getPluginName().equalsIgnoreCase(pluginName))
                .findFirst();
    }
}
