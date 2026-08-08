package io.github.thebusybiscuit.slimefun4.api.lifecycle;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Immutable diagnostic snapshot of the Slimefun core lifecycle. */
@SlimefunAPI
public final class CoreLifecycleSnapshot {

    private final CoreLifecycleState state;
    private final CoreLifecyclePhase phase;
    private final long startedAtMillis;
    private final long stateChangedAtMillis;
    private final long startupFailures;
    private final long shutdownFailures;
    private final String lastFailureComponent;
    private final String lastFailureType;
    private final String lastFailureMessage;

    public CoreLifecycleSnapshot(
            @Nonnull CoreLifecycleState state,
            @Nonnull CoreLifecyclePhase phase,
            long startedAtMillis,
            long stateChangedAtMillis,
            long startupFailures,
            long shutdownFailures,
            @Nullable String lastFailureComponent,
            @Nullable String lastFailureType,
            @Nullable String lastFailureMessage) {
        this.state = state;
        this.phase = phase;
        this.startedAtMillis = startedAtMillis;
        this.stateChangedAtMillis = stateChangedAtMillis;
        this.startupFailures = startupFailures;
        this.shutdownFailures = shutdownFailures;
        this.lastFailureComponent = lastFailureComponent;
        this.lastFailureType = lastFailureType;
        this.lastFailureMessage = lastFailureMessage;
    }

    public @Nonnull CoreLifecycleState getState() {
        return state;
    }

    public @Nonnull CoreLifecyclePhase getPhase() {
        return phase;
    }

    public long getStartedAtMillis() {
        return startedAtMillis;
    }

    public long getStateChangedAtMillis() {
        return stateChangedAtMillis;
    }

    public long getStartupFailures() {
        return startupFailures;
    }

    public long getShutdownFailures() {
        return shutdownFailures;
    }

    public @Nullable String getLastFailureComponent() {
        return lastFailureComponent;
    }

    public @Nullable String getLastFailureType() {
        return lastFailureType;
    }

    public @Nullable String getLastFailureMessage() {
        return lastFailureMessage;
    }
}
