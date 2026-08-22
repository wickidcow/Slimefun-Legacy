package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataType;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.FieldKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordKey;
import java.lang.reflect.Field;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ADataControllerSubmissionOrderTest {

    @Test
    void scopedWriterWaitsForSubmissionGateBeforeAcquiringScopeLock() throws Exception {
        var controller = new TestController();
        var scope = new LocationKey(DataScope.NONE, "world;0:64:0");
        var started = new AtomicBoolean();
        Thread writer;

        Object submissionGate = getField(controller, "writeSubmissionLock");
        ScopedLock scopedLock = (ScopedLock) getField(controller, "lock");

        try {
            synchronized (submissionGate) {
                writer = new Thread(
                        () -> {
                            started.set(true);
                            controller.schedule(scope, record("ordering"), () -> {});
                        },
                        "storage-submission-order-test");
                writer.start();

                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while ((!started.get() || writer.getState() != Thread.State.BLOCKED)
                        && System.nanoTime() < deadline) {
                    Thread.onSpinWait();
                }

                assertTrue(started.get());
                assertTrue(writer.getState() == Thread.State.BLOCKED);
                assertFalse(scopedLock.hasLock(scope));
            }

            writer.join(5000L);
            assertFalse(writer.isAlive());
            controller.currentCompletion(scope).get(5, TimeUnit.SECONDS);
        } finally {
            controller.closeExecutors();
        }
    }

    private static Object getField(ADataController controller, String name) throws Exception {
        Field field = ADataController.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(controller);
    }

    private static RecordKey record(String value) {
        var key = new RecordKey(DataScope.BLOCK_DATA);
        key.addCondition(FieldKey.DATA_KEY, value);
        return key;
    }

    private static final class TestController extends ADataController {
        private TestController() {
            super(DataType.BLOCK_STORAGE);
            readExecutor = Executors.newSingleThreadExecutor();
            writeExecutor = Executors.newSingleThreadExecutor();
            callbackExecutor = Executors.newSingleThreadExecutor();
        }

        private void schedule(LocationKey scope, RecordKey key, Runnable task) {
            scheduleWriteTask(scope, key, task, true);
        }

        private java.util.concurrent.CompletableFuture<Void> currentCompletion(LocationKey scope) {
            return getCurrentWriteCompletion(scope);
        }

        private void closeExecutors() {
            readExecutor.shutdownNow();
            writeExecutor.shutdownNow();
            callbackExecutor.shutdownNow();
        }
    }
}
