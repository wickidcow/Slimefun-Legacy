package com.xzavier0722.mc.plugin.slimefun4.storage.task;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.LinkedKey;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DelayedSavingLooperTask implements Runnable {
    private final long forceSavePeriodInMillis;
    private final Supplier<Map<LinkedKey, DelayedTask>> taskGetter;
    private final BiConsumer<LinkedKey, DelayedTask> executeCallback;
    private long nextForceRun;

    /**
     * Retains the historical key-only callback for existing callers.
     *
     * @param forceSavePeriod force save period in seconds
     */
    public DelayedSavingLooperTask(
            int forceSavePeriod,
            Supplier<Map<LinkedKey, DelayedTask>> taskGetter,
            Consumer<LinkedKey> executeCallback) {
        this(forceSavePeriod, taskGetter, (key, completedTask) -> executeCallback.accept(key));
    }

    private DelayedSavingLooperTask(
            int forceSavePeriod,
            Supplier<Map<LinkedKey, DelayedTask>> taskGetter,
            BiConsumer<LinkedKey, DelayedTask> executeCallback) {
        this.forceSavePeriodInMillis = forceSavePeriod * 1000L;
        this.executeCallback = executeCallback;
        this.taskGetter = taskGetter;
        updateNextForceRunTime();
    }

    /**
     * Creates a looper whose completion callback receives the exact task from the snapshot.
     * The callback can conditionally remove that task without discarding a newer queued save
     * for the same key. Successful execution here means the runnable returned, not that a
     * separately scheduled database write has been acknowledged.
     *
     * <p>The named factory avoids overloading the public key-only constructor and making
     * existing method references such as {@code tasks::remove} ambiguous.
     */
    public static DelayedSavingLooperTask withTaskCompletion(
            int forceSavePeriod,
            Supplier<Map<LinkedKey, DelayedTask>> taskGetter,
            BiConsumer<LinkedKey, DelayedTask> executeCallback) {
        return new DelayedSavingLooperTask(forceSavePeriod, taskGetter, executeCallback);
    }

    @Override
    public void run() {
        var tasks = taskGetter.get();
        if (tasks == null || tasks.isEmpty()) {
            return;
        }

        if (nextForceRun > System.currentTimeMillis()) {
            tasks.forEach((key, task) -> {
                if (task.tryRun()) {
                    executeCallback.accept(key, task);
                }
            });
        } else {
            updateNextForceRunTime();
            tasks.forEach((key, task) -> {
                if (task.runNow()) {
                    executeCallback.accept(key, task);
                }
            });
        }
    }

    private void updateNextForceRunTime() {
        nextForceRun = System.currentTimeMillis() + forceSavePeriodInMillis;
    }
}
