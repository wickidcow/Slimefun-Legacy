package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import city.norain.slimefun4.utils.SlimefunPoolExecutor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nonnull;

/**
 * Slimefun database read executor with an explicit drain snapshot and a submission-gated idle check.
 *
 * <p>The parent executor still owns task execution, profiling and exception reporting. This class only wraps submitted
 * commands so storage cache eviction can observe queued and running reads, including CompletableFuture submissions.
 */
final class TrackedReadExecutor extends SlimefunPoolExecutor {

    private final Object submissionLock = new Object();
    private final Set<CompletableFuture<Void>> activeTasks = ConcurrentHashMap.newKeySet();

    TrackedReadExecutor(
            String name,
            int corePoolSize,
            int maximumPoolSize,
            long keepAliveTime,
            @Nonnull TimeUnit unit,
            @Nonnull BlockingQueue<Runnable> workQueue,
            @Nonnull ThreadFactory threadFactory) {
        super(name, corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
    }

    @Override
    public void execute(@Nonnull Runnable command) {
        Objects.requireNonNull(command, "Command cannot be null");
        var tracked = new TrackedRunnable(command);

        synchronized (submissionLock) {
            activeTasks.add(tracked.completion);
            try {
                super.execute(tracked);
            } catch (RuntimeException | Error failure) {
                tracked.finish();
                throw failure;
            }
        }
    }

    @Override
    protected void afterExecute(Runnable runnable, Throwable failure) {
        Runnable reportedTask = runnable instanceof TrackedRunnable tracked ? tracked.delegate : runnable;
        super.afterExecute(reportedTask, failure);
    }

    CompletableFuture<Void> snapshot() {
        if (activeTasks.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.allOf(activeTasks.toArray(CompletableFuture[]::new));
    }

    /**
     * Executes a short critical section only when no queued or running task exists and prevents new submissions until
     * the critical section returns.
     *
     * @param action work that must not submit another read to this executor
     * @return whether the action ran while the executor was idle
     */
    boolean runIfIdle(Runnable action) {
        Objects.requireNonNull(action, "Action cannot be null");
        synchronized (submissionLock) {
            if (!activeTasks.isEmpty()) {
                return false;
            }
            action.run();
            return true;
        }
    }

    int activeTaskCount() {
        return activeTasks.size();
    }

    @Override
    public List<Runnable> shutdownNow() {
        synchronized (submissionLock) {
            List<Runnable> pending = super.shutdownNow();
            if (pending.isEmpty()) {
                return pending;
            }

            var unwrapped = new ArrayList<Runnable>(pending.size());
            for (Runnable task : pending) {
                if (task instanceof TrackedRunnable tracked) {
                    tracked.finish();
                    unwrapped.add(tracked.delegate);
                } else {
                    unwrapped.add(task);
                }
            }
            return unwrapped;
        }
    }

    private final class TrackedRunnable implements Runnable {
        private final Runnable delegate;
        private final CompletableFuture<Void> completion = new CompletableFuture<>();
        private final AtomicBoolean finished = new AtomicBoolean();

        private TrackedRunnable(Runnable delegate) {
            this.delegate = delegate;
        }

        @Override
        public void run() {
            try {
                delegate.run();
            } finally {
                finish();
            }
        }

        private void finish() {
            if (finished.compareAndSet(false, true)) {
                activeTasks.remove(completion);
                completion.complete(null);
            }
        }
    }
}
