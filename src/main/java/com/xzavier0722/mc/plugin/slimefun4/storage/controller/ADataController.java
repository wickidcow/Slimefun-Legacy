package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import city.norain.slimefun4.utils.SlimefunPoolExecutor;
import city.norain.slimefun4.utils.TaskTimer;
import com.xzavier0722.mc.plugin.slimefun4.storage.adapter.IDataSourceAdapter;
import com.xzavier0722.mc.plugin.slimefun4.storage.callback.IAsyncReadCallback;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataType;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordSet;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.ScopeKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.task.DatabaseThreadFactory;
import com.xzavier0722.mc.plugin.slimefun4.storage.task.QueuedWriteTask;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.OverridingMethodsMustInvokeSuper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * {@link ADataController} 是 Slimefun 数据库控制器的抽象类，
 * 提供了对数据源适配器的访问和数据操作的基本方法。
 * <br/>
 * 该类提供了对数据库的增删查改操作以及异步读写的支持。
 */
@Slf4j
public abstract class ADataController {
    private final DataType dataType;
    private final Map<ScopeKey, QueuedWriteTask> scheduledWriteTasks;
    private final ScopedLock lock;
    private final Object writeSubmissionLock;

    private volatile IDataSourceAdapter<?> dataAdapter;
    /**
     * 数据库读取调度器
     */
    protected ExecutorService readExecutor;
    /**
     * 数据库写入调度器
     */
    protected ExecutorService writeExecutor;

    protected ExecutorService serialWriteExecutor;

    /**
     * 数据库回调调度器
     */
    @Getter
    protected ExecutorService callbackExecutor;
    /**
     * 标记当前控制器是否已被关闭
     */
    private volatile boolean destroyed = false;

    private volatile boolean shuttingDown = false;
    private volatile boolean lastShutdownClean = true;

    /**
     * The logger for this data controller.
     */
    protected final Logger logger;

    /**
     * Constructs a new ADataController.
     *
     * @param dataType The data type this controller manages
     */
    protected ADataController(DataType dataType) {
        this.dataType = dataType;
        scheduledWriteTasks = new ConcurrentHashMap<>();
        lock = new ScopedLock();
        writeSubmissionLock = new Object();
        logger = Logger.getLogger("SF-" + dataType.name() + "-Controller");
    }

    /**
     * 初始化 {@link ADataController}
     *
     * @param dataAdapter The data source adapter
     * @param maxReadThread Maximum number of read threads
     * @param maxWriteThread Maximum number of write threads
     */
    @OverridingMethodsMustInvokeSuper
    public void init(IDataSourceAdapter<?> dataAdapter, int maxReadThread, int maxWriteThread) {
        this.dataAdapter = dataAdapter;
        dataAdapter.initStorage(dataType);
        dataAdapter.patch();
        readExecutor = new TrackedReadExecutor(
                "SF-" + dataType.name() + "-Read-Executor",
                maxReadThread,
                maxReadThread,
                10,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                new DatabaseThreadFactory("SF-" + dataType.name() + "-Read-Thread #"));

        writeExecutor = new SlimefunPoolExecutor(
                "SF-" + dataType.name() + "-Write-Executor",
                Math.max(maxWriteThread - 1, 1),
                Math.max(maxWriteThread - 1, 1),
                10,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                new DatabaseThreadFactory("SF-" + dataType.name() + "-Write-Thread #"));

        if (maxWriteThread > 1) {
            serialWriteExecutor = new SlimefunPoolExecutor(
                    "SF-" + dataType.name() + "-SerialWrite-Executor",
                    1,
                    1,
                    10,
                    TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(),
                    new DatabaseThreadFactory("SF-" + dataType.name() + "-SerialWrite-Thread #"));
        }

        callbackExecutor = new SlimefunPoolExecutor(
                "SF-" + dataType.name() + "-Callback-Executor",
                1,
                Math.max(1, Runtime.getRuntime().availableProcessors() / 2),
                10,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                new DatabaseThreadFactory("SF-" + dataType.name() + "-Callback-Thread #"));
    }

    /**
     * 正常关闭 {@link ADataController}
     */
    @OverridingMethodsMustInvokeSuper
    public void shutdown() {
        if (destroyed || shuttingDown) {
            return;
        }
        shuttingDown = true;
        readExecutor.shutdownNow();
        callbackExecutor.shutdownNow();

        try {
            int totalTask = scheduledWriteTasks.size();
            int pendingTask = totalTask;
            var stalledTimer = new TaskTimer();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);

            while (pendingTask > 0 && System.nanoTime() < deadline) {
                double completedPercent = totalTask == 0 ? 100.0 : (totalTask - pendingTask) * 100.0 / totalTask;
                logger.log(Level.INFO, "Saving data, please wait... Remaining tasks: {0} ({1}%)", new Object[] {
                    pendingTask, String.format("%.1f", completedPercent)
                });
                TimeUnit.SECONDS.sleep(1);
                int currentTask = scheduledWriteTasks.size();

                if (pendingTask == currentTask && stalledTimer.peek() / 1000 > 10) {
                    Slimefun.logger().warning("Detected a long-running save task. Thread dump follows:");
                    Slimefun.logger().log(Level.WARNING, Slimefun.getProfiler().snapshotThreads());
                    stalledTimer.reset();
                } else if (pendingTask != currentTask) {
                    stalledTimer.reset();
                }

                pendingTask = currentTask;
            }

            lastShutdownClean = scheduledWriteTasks.isEmpty();
            if (lastShutdownClean) {
                logger.info("Data save completed.");
            } else {
                logger.log(
                        Level.SEVERE, "Timed out with {0} pending database write task(s).", scheduledWriteTasks.size());
            }
        } catch (InterruptedException e) {
            lastShutdownClean = false;
            Thread.currentThread().interrupt();
            logger.log(Level.WARNING, "Interrupted while saving data", e);
        }
        writeExecutor.shutdown();
        if (serialWriteExecutor != null) {
            serialWriteExecutor.shutdown();
        }
        try {
            if (!writeExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                lastShutdownClean = false;
                writeExecutor.shutdownNow();
            }
            if (serialWriteExecutor != null && !serialWriteExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                lastShutdownClean = false;
                serialWriteExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            lastShutdownClean = false;
            Thread.currentThread().interrupt();
            logger.log(Level.WARNING, "Interrupted while waiting for database writers to stop", e);
        } finally {
            dataAdapter = null;
            destroyed = true;
        }
    }

    protected void scheduleDeleteTask(ScopeKey scopeKey, RecordKey key, boolean forceScopeKey) {
        scheduleWriteTask(
                scopeKey,
                key,
                () -> {
                    dataAdapter.deleteData(key);
                },
                forceScopeKey);
    }

    protected void scheduleWriteTask(ScopeKey scopeKey, RecordKey key, RecordSet data, boolean forceScopeKey) {
        scheduleWriteTask(scopeKey, key, () -> dataAdapter.setData(key, data), forceScopeKey);
    }

    protected void scheduleWriteTask(ScopeKey scopeKey, RecordKey key, Runnable task, boolean forceScopeKey) {
        checkDestroy();
        lock.lock(scopeKey);

        // log.info("schedule write scope [{}], key [{}]", scopeKey, key);

        try {
            checkDestroy();
            synchronized (writeSubmissionLock) {
                var scopeToUse = forceScopeKey ? scopeKey : key;
                var queuedTask = scheduledWriteTasks.get(scopeKey);
                if (queuedTask == null && scopeKey != scopeToUse) {
                    queuedTask = scheduledWriteTasks.get(scopeToUse);
                }

                if (queuedTask != null && queuedTask.queue(key, task)) {
                    return;
                }

                queuedTask = new QueuedWriteTask() {
                    @Override
                    protected void onSuccess() {
                        scheduledWriteTasks.remove(scopeToUse, this);
                    }

                    @Override
                    protected void onError(Throwable e) {
                        Slimefun.logger()
                                .log(
                                        Level.SEVERE,
                                        "[" + Thread.currentThread().getName()
                                                + "] Exception thrown while executing write task: ",
                                        e);
                    }
                };
                queuedTask.queue(key, task);
                scheduledWriteTasks.put(scopeToUse, queuedTask);

                if (serialWriteExecutor != null && key.getScope().isSerial()) {
                    serialWriteExecutor.submit(queuedTask);
                } else {
                    writeExecutor.submit(queuedTask);
                }
            }
        } finally {
            lock.unlock(scopeKey);
        }
    }

    /**
     * Returns the completion state for the write queue currently registered to the supplied scope.
     *
     * <p>This is a snapshot, not a permanent barrier: writes scheduled after this method releases the scope lock are
     * represented by a later queue and must be checked again before destructive cache eviction.
     *
     * @param scopeKey the write scope to inspect
     * @return a future that completes when the currently registered queue drains
     */
    protected CompletableFuture<Void> getCurrentWriteCompletion(ScopeKey scopeKey) {
        checkDestroy();
        lock.lock(scopeKey);
        try {
            var task = scheduledWriteTasks.get(scopeKey);
            return task == null ? CompletableFuture.completedFuture(null) : task.getCompletionFuture();
        } finally {
            lock.unlock(scopeKey);
        }
    }

    /**
     * Returns a completion snapshot for all currently registered write queues whose scope matches the supplied filter.
     *
     * <p>The returned future represents only queues visible during this snapshot. Callers performing destructive cache
     * work must rescan before eviction so a write queued concurrently after this snapshot cannot be missed.
     *
     * @param scopeFilter selects write scopes to include in the snapshot
     * @return a future that completes after all matching queues from this snapshot have drained
     */
    protected CompletableFuture<Void> getCurrentWriteCompletion(Predicate<ScopeKey> scopeFilter) {
        checkDestroy();
        var completions = new ArrayList<CompletableFuture<Void>>();
        scheduledWriteTasks.forEach((scope, task) -> {
            if (scopeFilter.test(scope)) {
                completions.add(task.getCompletionFuture());
            }
        });
        return CompletableFuture.allOf(completions.toArray(CompletableFuture[]::new));
    }

    /**
     * Runs a short critical section only if no currently registered write queue matches the supplied scope filter.
     * New write registration is held until the action returns.
     *
     * @param scopeFilter selects write scopes that would make the action unsafe
     * @param action cache work that must not schedule another write
     * @return whether the action ran with matching write scopes idle
     */
    protected boolean runIfWriteScopesIdle(Predicate<ScopeKey> scopeFilter, Runnable action) {
        checkDestroy();
        synchronized (writeSubmissionLock) {
            if (scheduledWriteTasks.keySet().stream().anyMatch(scopeFilter)) {
                return false;
            }
            action.run();
            return true;
        }
    }

    /**
     * Returns a completion snapshot for every read currently queued or running on this controller's read executor.
     *
     * <p>This is a snapshot rather than a permanent barrier. Use {@link #runIfReadExecutorIdle(Runnable)} for the short
     * destructive critical section after the snapshot has completed.
     *
     * @return a future that completes when the currently visible read tasks have finished
     */
    protected CompletableFuture<Void> getCurrentReadCompletion() {
        checkDestroy();
        return readExecutor instanceof TrackedReadExecutor tracked
                ? tracked.snapshot()
                : CompletableFuture.completedFuture(null);
    }

    /**
     * Runs a short critical section only when the tracked read executor is idle and prevents new read submissions until
     * that section returns.
     *
     * <p>If a subclass replaced the standard read executor, this method fails closed and does not run the action.
     *
     * @param action cache work that must not submit another read task
     * @return whether the action ran while read submissions were gated
     */
    protected boolean runIfReadExecutorIdle(Runnable action) {
        checkDestroy();
        return readExecutor instanceof TrackedReadExecutor tracked && tracked.runIfIdle(action);
    }

    protected void checkDestroy() {
        if (destroyed || shuttingDown) {
            throw new IllegalStateException("Controller cannot be accessed while shutting down or after destruction.");
        }
    }

    protected <T> void invokeCallback(IAsyncReadCallback<T> callback, T result) {
        if (callback == null) {
            return;
        }

        Runnable cb;
        if (result == null) {
            cb = callback::onResultNotFound;
        } else {
            cb = () -> callback.onResult(result);
        }

        if (callback.runOnMainThread()) {
            Slimefun.runSync(cb);
        } else {
            callbackExecutor.submit(cb);
        }
    }

    protected void scheduleReadTask(Runnable run) {
        checkDestroy();
        readExecutor.submit(run);
    }

    protected void scheduleWriteTask(Runnable run) {
        checkDestroy();
        writeExecutor.submit(run);
    }

    protected List<RecordSet> getData(RecordKey key) {
        return getData(key, false);
    }

    protected List<RecordSet> getData(RecordKey key, boolean distinct) {
        return dataAdapter.getData(key, distinct);
    }

    protected void setData(RecordKey key, RecordSet data) {
        dataAdapter.setData(key, data);
    }

    protected void deleteData(RecordKey key) {
        dataAdapter.deleteData(key);
    }

    protected void abortScopeTask(ScopeKey key) {
        var task = scheduledWriteTasks.remove(key);
        if (task != null) {
            task.abort();
        }
    }

    public int getPendingWriteTaskCount() {
        return scheduledWriteTasks.size();
    }

    public boolean isShuttingDown() {
        return shuttingDown;
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    public boolean wasLastShutdownClean() {
        return lastShutdownClean;
    }

    public final DataType getDataType() {
        return dataType;
    }
}
