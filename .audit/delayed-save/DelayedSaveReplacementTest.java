package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.FieldKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.ScopeKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.task.DelayedSavingLooperTask;
import com.xzavier0722.mc.plugin.slimefun4.storage.task.DelayedTask;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** Real queue/task tests; the recorded runnable effects are not an external database fixture. */
class DelayedSaveReplacementTest {
    @Test
    void controllerDueCompletionPreservesReplacement() {
        controllerReplacement(Integer.MAX_VALUE);
    }

    @Test
    void controllerForcedCompletionPreservesReplacement() {
        controllerReplacement(0);
    }

    private static void controllerReplacement(int forcePeriod) {
        var controller = new BlockDataController();
        var key = key("world;0:64:0", "owner");
        var calls = new ArrayList<String>();
        enqueue(controller, key, () -> {
            calls.add("old");
            // This controlled interleaving puts the replacement after execution starts but
            // before the old snapshot's completion callback. No timing sleeps are needed.
            enqueue(controller, key, () -> calls.add("new"));
        });
        var looper = controller.createDelayedSavingLooper(forcePeriod);
        looper.run();
        assertEquals(List.of("old"), calls);
        assertEquals(1, controller.getPendingDelayedWriteTaskCount());
        looper.run();
        assertEquals(List.of("old", "new"), calls);
        assertEquals(0, controller.getPendingDelayedWriteTaskCount());
        looper.run();
        assertEquals(List.of("old", "new"), calls);
    }

    @Test
    void controllerRetainsQueuedDeletionAfterAnEarlierWrite() {
        var controller = new BlockDataController();
        var key = key("world;0:64:0", "obsolete-setting");
        var effects = new HashMap<String, String>();
        enqueue(controller, key, () -> {
            effects.put("obsolete-setting", "old-value");
            enqueue(controller, key, () -> effects.remove("obsolete-setting"));
        });
        var looper = controller.createDelayedSavingLooper(0);
        looper.run();
        assertEquals("old-value", effects.get("obsolete-setting"));
        assertEquals(1, controller.getPendingDelayedWriteTaskCount());
        looper.run();
        assertFalse(effects.containsKey("obsolete-setting"));
        assertEquals(0, controller.getPendingDelayedWriteTaskCount());
    }

    @Test
    void controllerStillCoalescesChangesBeforeExecution() {
        var controller = new BlockDataController();
        var key = key("world;0:64:0", "charge");
        var current = new AtomicReference<>("first");
        var observed = new ArrayList<String>();
        var ignoredCallback = new AtomicInteger();
        enqueue(controller, key, () -> observed.add(current.get()));
        current.set("latest");
        enqueue(controller, key, ignoredCallback::incrementAndGet);
        assertEquals(1, controller.getPendingDelayedWriteTaskCount());
        controller.createDelayedSavingLooper(0).run();
        assertEquals(List.of("latest"), observed);
        assertEquals(0, ignoredCallback.get());
        assertEquals(0, controller.getPendingDelayedWriteTaskCount());
    }

    @Test
    void controllerKeepsFailedSubmissionForRetry() {
        var controller = new BlockDataController();
        var attempts = new AtomicInteger();
        enqueue(controller, key("world;0:64:0", "charge"), () -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("Expected first submission failure");
            }
        });
        var looper = controller.createDelayedSavingLooper(0);
        looper.run();
        assertEquals(1, controller.getPendingDelayedWriteTaskCount());
        looper.run();
        assertEquals(2, attempts.get());
        assertEquals(0, controller.getPendingDelayedWriteTaskCount());
    }

    @Test
    void sameFieldInDifferentLocationsRemainsIndependent() {
        var controller = new BlockDataController();
        var first = new AtomicInteger();
        var second = new AtomicInteger();
        enqueue(controller, key("world;0:64:0", "owner"), first::incrementAndGet);
        enqueue(controller, key("world;1:64:0", "owner"), second::incrementAndGet);
        assertEquals(2, controller.getPendingDelayedWriteTaskCount());
        controller.createDelayedSavingLooper(0).run();
        assertEquals(1, first.get());
        assertEquals(1, second.get());
        assertEquals(0, controller.getPendingDelayedWriteTaskCount());
    }

    @Test
    void snapshotTakenBeforeReplacementCannotEraseIt() {
        var key = key("world;0:64:0", "owner");
        var calls = new ArrayList<String>();
        Map<LinkedKey, DelayedTask> tasks = new ConcurrentHashMap<>();
        var old = due(() -> calls.add("old"));
        var next = due(() -> calls.add("next"));
        tasks.put(key, old);
        var firstSnapshot = new AtomicInteger();
        var looper = DelayedSavingLooperTask.withTaskCompletion(0, () -> {
            var snapshot = new HashMap<>(tasks);
            if (firstSnapshot.getAndIncrement() == 0) tasks.put(key, next);
            return snapshot;
        }, tasks::remove);
        looper.run();
        assertSame(next, tasks.get(key));
        looper.run();
        assertEquals(List.of("old", "next"), calls);
        assertTrue(tasks.isEmpty());
    }

    @Test
    void concurrentReplacementSurvivesDueAndForcedCompletion() throws Exception {
        for (int period : new int[] {0, Integer.MAX_VALUE}) {
            Map<LinkedKey, DelayedTask> tasks = new ConcurrentHashMap<>();
            var key = key("world;0:64:0", "owner");
            var started = new CountDownLatch(1);
            var replaced = new CountDownLatch(1);
            var effects = new AtomicInteger();
            tasks.put(key, due(() -> {
                started.countDown();
                await(replaced);
                effects.incrementAndGet();
            }));
            var next = due(effects::incrementAndGet);
            var looper = DelayedSavingLooperTask.withTaskCompletion(period, () -> new HashMap<>(tasks), tasks::remove);
            var worker = Executors.newSingleThreadExecutor();
            try {
                var running = worker.submit(looper);
                assertTrue(started.await(5, TimeUnit.SECONDS));
                tasks.put(key, next);
                replaced.countDown();
                running.get(5, TimeUnit.SECONDS);
                assertSame(next, tasks.get(key));
                looper.run();
                assertTrue(tasks.isEmpty());
                assertEquals(2, effects.get());
            } finally {
                replaced.countDown();
                worker.shutdownNow();
                assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void callbackReceivesTheExactSnapshotEntry() {
        var key = key("world;0:64:0", "owner");
        var task = due(() -> {});
        var seen = new AtomicInteger();
        DelayedSavingLooperTask.withTaskCompletion(0, () -> Map.of(key, task), (completedKey, completedTask) -> {
            assertSame(key, completedKey);
            assertSame(task, completedTask);
            seen.incrementAndGet();
        }).run();
        assertEquals(1, seen.get());
    }

    @Test
    void notYetDueTaskIsNotRemoved() {
        var key = key("world;0:64:0", "owner");
        var effects = new AtomicInteger();
        Map<LinkedKey, DelayedTask> tasks = new ConcurrentHashMap<>();
        var task = new DelayedTask(1, TimeUnit.DAYS, effects::incrementAndGet);
        tasks.put(key, task);
        DelayedSavingLooperTask.withTaskCompletion(Integer.MAX_VALUE, () -> new HashMap<>(tasks), tasks::remove).run();
        assertSame(task, tasks.get(key));
        assertFalse(task.isExecuted());
        assertEquals(0, effects.get());
    }

    @Test
    void forcePeriodStillRunsFutureTask() {
        var effects = new AtomicInteger();
        Map<LinkedKey, DelayedTask> tasks = new ConcurrentHashMap<>();
        tasks.put(key("world;0:64:0", "owner"), new DelayedTask(1, TimeUnit.DAYS, effects::incrementAndGet));
        DelayedSavingLooperTask.withTaskCompletion(0, () -> new HashMap<>(tasks), tasks::remove).run();
        assertEquals(1, effects.get());
        assertTrue(tasks.isEmpty());
    }

    @Test
    void oldCompletedSnapshotCannotRemoveANewSaveOrRunTwice() {
        var key = key("world;0:64:0", "owner");
        var effects = new AtomicInteger();
        Map<LinkedKey, DelayedTask> tasks = new ConcurrentHashMap<>();
        var old = due(effects::incrementAndGet);
        tasks.put(key, old);
        var looper = DelayedSavingLooperTask.withTaskCompletion(0, () -> Map.of(key, old), tasks::remove);
        looper.run();
        var next = due(effects::incrementAndGet);
        tasks.put(key, next);
        looper.run();
        assertSame(next, tasks.get(key));
        assertEquals(1, effects.get());
    }

    @Test
    void nullAndEmptySnapshotsProduceNoCallback() {
        var calls = new AtomicInteger();
        DelayedSavingLooperTask.withTaskCompletion(0, () -> null, (key, task) -> calls.incrementAndGet()).run();
        DelayedSavingLooperTask.withTaskCompletion(0, Map::of, (key, task) -> calls.incrementAndGet()).run();
        assertEquals(0, calls.get());
    }

    @Test
    void historicalConstructorAndMethodReferenceRemainCompatible() throws Exception {
        assertNotNull(DelayedSavingLooperTask.class.getConstructor(int.class, Supplier.class, Consumer.class));
        Map<LinkedKey, DelayedTask> tasks = new ConcurrentHashMap<>();
        var calls = new AtomicInteger();
        tasks.put(key("world;0:64:0", "owner"), due(calls::incrementAndGet));
        new DelayedSavingLooperTask(0, () -> new HashMap<>(tasks), tasks::remove).run();
        assertEquals(1, calls.get());
        assertTrue(tasks.isEmpty());
    }

    @Test
    void repeatedReplacementsDoNotAccumulateOrDisappear() {
        var controller = new BlockDataController();
        var key = key("world;0:64:0", "charge");
        var calls = new AtomicInteger();
        Runnable[] callback = new Runnable[1];
        callback[0] = () -> {
            if (calls.incrementAndGet() < 1000) enqueue(controller, key, callback[0]);
        };
        enqueue(controller, key, callback[0]);
        var looper = controller.createDelayedSavingLooper(0);
        for (int index = 1; index <= 1000; index++) {
            looper.run();
            assertEquals(index, calls.get());
            assertEquals(index == 1000 ? 0 : 1, controller.getPendingDelayedWriteTaskCount());
        }
        looper.run();
        assertEquals(1000, calls.get());
    }

    private static DelayedTask due(Runnable callback) {
        return new DelayedTask(-1, TimeUnit.DAYS, callback);
    }

    private static LinkedKey key(String location, String field) {
        var record = new RecordKey(DataScope.BLOCK_DATA);
        record.addCondition(FieldKey.LOCATION, location);
        record.addCondition(FieldKey.DATA_KEY, field);
        return new LinkedKey(new ScopeKey(DataScope.NONE), record);
    }

    private static void enqueue(BlockDataController controller, LinkedKey key, Runnable callback) {
        try {
            var method = BlockDataController.class.getDeclaredMethod("scheduleDelayedUpdateTask", LinkedKey.class, Runnable.class);
            method.setAccessible(true);
            method.invoke(controller, key, callback);
        } catch (InvocationTargetException failure) {
            throw new AssertionError("Controller rejected the test submission", failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Controller delayed-save boundary changed", failure);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("Timed out awaiting controlled replacement");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }
}
