package io.github.thebusybiscuit.slimefun4.core.services.lifecycle;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunInternal;
import io.github.thebusybiscuit.slimefun4.api.lifecycle.CoreLifecyclePhase;
import io.github.thebusybiscuit.slimefun4.api.lifecycle.CoreLifecycleService;
import io.github.thebusybiscuit.slimefun4.api.lifecycle.CoreLifecycleSnapshot;
import io.github.thebusybiscuit.slimefun4.api.lifecycle.CoreLifecycleState;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;

/** Internal lifecycle coordinator. It preserves startup order and isolates independent shutdown failures. */
@SlimefunInternal
public final class DefaultCoreLifecycleService implements CoreLifecycleService {

    private static final int MAX_FAILURE_MESSAGE_LENGTH = 240;

    private final Logger logger;
    private final AtomicReference<CoreLifecycleState> state = new AtomicReference<>(CoreLifecycleState.NEW);
    private final AtomicReference<CoreLifecyclePhase> phase = new AtomicReference<>(CoreLifecyclePhase.BOOTSTRAP);
    private final AtomicLong startupFailures = new AtomicLong();
    private final AtomicLong shutdownFailures = new AtomicLong();

    private volatile long startedAtMillis;
    private volatile long stateChangedAtMillis = System.currentTimeMillis();
    private volatile String lastFailureComponent;
    private volatile String lastFailureType;
    private volatile String lastFailureMessage;

    public DefaultCoreLifecycleService(@Nonnull Logger logger) {
        this.logger = java.util.Objects.requireNonNull(logger, "logger");
    }

    public void beginStart() {
        CoreLifecycleState current = state.get();
        if (current != CoreLifecycleState.NEW && current != CoreLifecycleState.STOPPED) {
            return;
        }

        startedAtMillis = System.currentTimeMillis();
        phase.set(CoreLifecyclePhase.BOOTSTRAP);
        setState(CoreLifecycleState.STARTING);
    }

    public void enterPhase(@Nonnull CoreLifecyclePhase nextPhase) {
        java.util.Objects.requireNonNull(nextPhase, "nextPhase");
        CoreLifecycleState current = state.get();
        if (current == CoreLifecycleState.STARTING || current == CoreLifecycleState.RUNNING) {
            phase.set(nextPhase);
            stateChangedAtMillis = System.currentTimeMillis();
        }
    }

    public void markRunning() {
        phase.set(CoreLifecyclePhase.RUNNING);
        setState(CoreLifecycleState.RUNNING);
    }

    public void markStartupFailed(@Nonnull String component, @Nonnull Throwable failure) {
        startupFailures.incrementAndGet();
        rememberFailure(component, failure);
        setState(CoreLifecycleState.FAILED);
    }

    public void beginShutdown() {
        phase.set(CoreLifecyclePhase.SHUTDOWN);
        setState(CoreLifecycleState.STOPPING);
    }

    /**
     * Executes one shutdown operation without allowing that operation to prevent later cleanup steps.
     *
     * @return whether the operation completed without a runtime/linkage failure
     */
    public boolean runShutdownStep(@Nonnull String component, @Nonnull Runnable action) {
        java.util.Objects.requireNonNull(component, "component");
        java.util.Objects.requireNonNull(action, "action");
        try {
            action.run();
            return true;
        } catch (RuntimeException | LinkageError failure) {
            shutdownFailures.incrementAndGet();
            rememberFailure(component, failure);
            logger.log(Level.SEVERE, "Slimefun shutdown step failed: " + component, failure);
            return false;
        }
    }

    public void markStopped() {
        phase.set(CoreLifecyclePhase.COMPLETE);
        setState(CoreLifecycleState.STOPPED);
    }

    @Override
    public @Nonnull CoreLifecycleSnapshot getSnapshot() {
        return new CoreLifecycleSnapshot(
                state.get(),
                phase.get(),
                startedAtMillis,
                stateChangedAtMillis,
                startupFailures.get(),
                shutdownFailures.get(),
                lastFailureComponent,
                lastFailureType,
                lastFailureMessage);
    }

    private void setState(CoreLifecycleState next) {
        state.set(next);
        stateChangedAtMillis = System.currentTimeMillis();
    }

    private void rememberFailure(String component, Throwable failure) {
        lastFailureComponent = component;
        lastFailureType = failure.getClass().getName();
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            lastFailureMessage = failure.getClass().getSimpleName();
        } else if (message.length() > MAX_FAILURE_MESSAGE_LENGTH) {
            lastFailureMessage = message.substring(0, MAX_FAILURE_MESSAGE_LENGTH - 3) + "...";
        } else {
            lastFailureMessage = message;
        }
    }
}
