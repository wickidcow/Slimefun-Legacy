package io.github.thebusybiscuit.slimefun4.api.integrations;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import java.util.Objects;
import javax.annotation.Nonnull;

/** Immutable runtime diagnostic for a failing external integration provider operation. */
@SlimefunAPI
public final class ExternalIntegrationFailureSnapshot {

    private final String integrationId;
    private final String displayName;
    private final String pluginName;
    private final String operation;
    private final String failureType;
    private final String failureMessage;
    private final int consecutiveFailures;
    private final long failuresObserved;
    private final long suppressedReports;
    private final long firstFailureMillis;
    private final long lastFailureMillis;
    private final long pausedUntilMillis;

    public ExternalIntegrationFailureSnapshot(
            @Nonnull String integrationId,
            @Nonnull String displayName,
            @Nonnull String pluginName,
            @Nonnull String operation,
            @Nonnull String failureType,
            @Nonnull String failureMessage,
            int consecutiveFailures,
            long failuresObserved,
            long suppressedReports,
            long firstFailureMillis,
            long lastFailureMillis,
            long pausedUntilMillis) {
        this.integrationId = Objects.requireNonNull(integrationId, "integrationId");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.pluginName = Objects.requireNonNull(pluginName, "pluginName");
        this.operation = Objects.requireNonNull(operation, "operation");
        this.failureType = Objects.requireNonNull(failureType, "failureType");
        this.failureMessage = Objects.requireNonNull(failureMessage, "failureMessage");
        this.consecutiveFailures = consecutiveFailures;
        this.failuresObserved = failuresObserved;
        this.suppressedReports = suppressedReports;
        this.firstFailureMillis = firstFailureMillis;
        this.lastFailureMillis = lastFailureMillis;
        this.pausedUntilMillis = pausedUntilMillis;
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

    public @Nonnull String getOperation() {
        return operation;
    }

    public @Nonnull String getFailureType() {
        return failureType;
    }

    public @Nonnull String getFailureMessage() {
        return failureMessage;
    }

    public int getConsecutiveFailures() {
        return consecutiveFailures;
    }

    public long getFailuresObserved() {
        return failuresObserved;
    }

    public long getSuppressedReports() {
        return suppressedReports;
    }

    public long getFirstFailureMillis() {
        return firstFailureMillis;
    }

    public long getLastFailureMillis() {
        return lastFailureMillis;
    }

    public long getPausedUntilMillis() {
        return pausedUntilMillis;
    }

    public boolean isPaused(long nowMillis) {
        return pausedUntilMillis > nowMillis;
    }

    public long getRetrySeconds(long nowMillis) {
        return isPaused(nowMillis) ? Math.max(1L, (pausedUntilMillis - nowMillis + 999L) / 1000L) : 0L;
    }
}
