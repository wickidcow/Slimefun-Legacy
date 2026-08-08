package io.github.thebusybiscuit.slimefun4.api.addons;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import javax.annotation.Nonnull;

/** Immutable record of a runtime callback failure associated with a plugin. */
@SlimefunAPI
public final class AddonRuntimeFailureSnapshot {

    private final String pluginName;
    private final String pluginVersion;
    private final String operation;
    private final String exceptionClass;
    private final String message;
    private final long observedFailures;
    private final long firstFailureMillis;
    private final long lastFailureMillis;

    public AddonRuntimeFailureSnapshot(
            @Nonnull String pluginName,
            @Nonnull String pluginVersion,
            @Nonnull String operation,
            @Nonnull String exceptionClass,
            @Nonnull String message,
            long observedFailures,
            long firstFailureMillis,
            long lastFailureMillis) {
        this.pluginName = pluginName;
        this.pluginVersion = pluginVersion;
        this.operation = operation;
        this.exceptionClass = exceptionClass;
        this.message = message;
        this.observedFailures = observedFailures;
        this.firstFailureMillis = firstFailureMillis;
        this.lastFailureMillis = lastFailureMillis;
    }

    public @Nonnull String getPluginName() {
        return pluginName;
    }

    public @Nonnull String getPluginVersion() {
        return pluginVersion;
    }

    public @Nonnull String getOperation() {
        return operation;
    }

    public @Nonnull String getExceptionClass() {
        return exceptionClass;
    }

    public @Nonnull String getMessage() {
        return message;
    }

    public long getObservedFailures() {
        return observedFailures;
    }

    public long getFirstFailureMillis() {
        return firstFailureMillis;
    }

    public long getLastFailureMillis() {
        return lastFailureMillis;
    }
}
