package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xzavier0722.mc.plugin.slimefun4.storage.adapter.IDataSourceAdapter;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataType;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.FieldKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordSet;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.ScopeKey;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ADataControllerWriteQueueTest {

    @Test
    void currentScopeCompletionWaitsForRegisteredQueue() throws Exception {
        var controller = new TestController();
        controller.init(new NoOpAdapter(), 1, 1);

        try {
            var started = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var scope = new LocationKey(DataScope.NONE, "world;0:64:0");
            var record = record("inventory");

            controller.schedule(scope, record, () -> {
                started.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("Timed out waiting to release controller write");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
            });

            assertTrue(started.await(5, TimeUnit.SECONDS));
            CompletableFuture<Void> completion = controller.currentCompletion(scope);
            assertFalse(completion.isDone());

            release.countDown();
            completion.get(5, TimeUnit.SECONDS);

            assertEquals(0, controller.getPendingWriteTaskCount());
            assertTrue(controller.currentCompletion(scope).isDone());
        } finally {
            controller.shutdown();
        }
    }

    private static RecordKey record(String value) {
        var key = new RecordKey(DataScope.BLOCK_DATA);
        key.addCondition(FieldKey.DATA_KEY, value);
        return key;
    }

    private static final class TestController extends ADataController {
        private TestController() {
            super(DataType.BLOCK_STORAGE);
        }

        private void schedule(ScopeKey scope, RecordKey key, Runnable task) {
            scheduleWriteTask(scope, key, task, true);
        }

        private CompletableFuture<Void> currentCompletion(ScopeKey scope) {
            return getCurrentWriteCompletion(scope);
        }
    }

    private static final class NoOpAdapter implements IDataSourceAdapter<Object> {
        @Override
        public void prepare(Object config) {}

        @Override
        public void initStorage(DataType type) {}

        @Override
        public void shutdown() {}

        @Override
        public void setData(RecordKey key, RecordSet item) {}

        @Override
        public List<RecordSet> getData(RecordKey key, boolean distinct) {
            return Collections.emptyList();
        }

        @Override
        public void deleteData(RecordKey key) {}

        @Override
        public void patch() {}
    }
}
