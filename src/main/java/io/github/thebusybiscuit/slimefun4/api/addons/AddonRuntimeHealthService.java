package io.github.thebusybiscuit.slimefun4.api.addons;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
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

    /**
     * Executes an addon callback behind Slimefun's standard runtime failure boundary.
     * A failed callback is recorded and returns {@code false}; the plugin is never disabled.
     */
    default boolean runGuarded(@Nonnull Plugin plugin, @Nonnull String operation, @Nonnull Runnable callback) {
        return runGuarded(plugin, operation, callback, ignored -> {});
    }

    /**
     * Executes an addon callback behind the standard failure boundary and invokes a local failure handler.
     */
    default boolean runGuarded(
            @Nonnull Plugin plugin,
            @Nonnull String operation,
            @Nonnull Runnable callback,
            @Nonnull Consumer<Throwable> onFailure) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(callback, "callback");
        Objects.requireNonNull(onFailure, "onFailure");
        try {
            callback.run();
            return true;
        } catch (RuntimeException | LinkageError failure) {
            recordFailure(plugin, operation, failure);
            try {
                onFailure.accept(failure);
            } catch (RuntimeException | LinkageError handlerFailure) {
                recordFailure(plugin, operation + ":failure-handler", handlerFailure);
            }
            return false;
        }
    }

    /**
     * Calls an addon callback behind Slimefun's standard runtime failure boundary.
     * Failures are recorded and represented by an empty {@link Optional}.
     */
    default <T> @Nonnull Optional<T> callGuarded(
            @Nonnull Plugin plugin, @Nonnull String operation, @Nonnull Supplier<T> callback) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(callback, "callback");
        try {
            return Optional.ofNullable(callback.get());
        } catch (RuntimeException | LinkageError failure) {
            recordFailure(plugin, operation, failure);
            return Optional.empty();
        }
    }
}
