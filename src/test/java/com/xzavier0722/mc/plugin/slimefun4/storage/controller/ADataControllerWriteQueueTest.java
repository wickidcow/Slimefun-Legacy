package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataType;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.FieldKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.ScopeKey;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class ADataControllerWriteQueueTest {

    @Test
    void currentScopeCompletionWaitsForRegisteredQueue() throws Exception {
        var controller = new TestController(1);
        var release = new CountDownLatch(1);

        try {
            var started = new CountDownLatch(1);
            var scope = new LocationKey(DataScope.NONE, "world;0:64:0");
            var record = record("inventory");

            controller.schedule(scope, record, () -> {
                started.countDown();
                awaitRelease(release);
            });

            assertTrue(started.await(5, TimeUnit.SECONDS));
            CompletableFuture<Void> completion = controller.currentCompletion(scope);
            assertFalse(completion.isDone());

            release.countDown();
            completion.get(5, TimeUnit.SECONDS);

            assertEquals(0, controller.getPendingWriteTaskCount());
            assertTrue(controller.currentCompletion(scope).isDone());
        } finally {
            release.countDown();
            controller.closeExecutors();
        }
    }

    @Test
    void filteredCompletionWaitsOnlyForMatchingWriteScopes() throws Exception {
        var controller = new TestController(2);
        var releaseFirst = new CountDownLatch(1);
        var releaseSecond = new CountDownLatch(1);

        try {
            var firstStarted = new CountDownLatch(1);
            var secondStarted = new CountDownLatch(1);
            var firstScope = new LocationKey(DataScope.NONE, "world;0:64:0");
            var secondScope = new LocationKey(DataScope.NONE, "world;32:64:0");

            controller.schedule(firstScope, record("first"), () -> {
                firstStarted.countDown();
                awaitRelease(releaseFirst);
            });
            controller.schedule(secondScope, record("second"), () -> {
                secondStarted.countDown();
                awaitRelease(releaseSecond);
            });

            assertTrue(firstStarted.await(5, TimeUnit.SECONDS));
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS));

            CompletableFuture<Void> firstOnly = controller.currentCompletion(firstScope::equals);
            assertFalse(firstOnly.isDone());

            releaseFirst.countDown();
            firstOnly.get(5, TimeUnit.SECONDS);

            assertEquals(1, controller.getPendingWriteTaskCount());
            assertFalse(controller.currentCompletion(secondScope).isDone());

            releaseSecond.countDown();
            controller.currentCompletion(scope -> true).get(5, TimeUnit.SECONDS);
            assertEquals(0, controller.getPendingWriteTaskCount());
        } finally {
            releaseFirst.countDown();
            releaseSecond.countDown();
            controller.closeExecutors();
        }
    }

    private static void awaitRelease(CountDownLatch release) {
        try {
            if (!release.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting to release controller write");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static RecordKey record(String value) {
        var key = new RecordKey(DataScope.BLOCK_DATA);
        key.addCondition(FieldKey.DATA_KEY, value);
        return key;
    }

    private static final class TestController extends ADataController {
        private TestController(int writeThreads) {
            super(DataType.BLOCK_STORAGE);
            readExecutor = Executors.newSingleThreadExecutor();
            writeExecutor = Executors.newFixedThreadPool(writeThreads);
            callbackExecutor = Executors.newSingleThreadExecutor();
        }

        private void schedule(ScopeKey scope, RecordKey key, Runnable task) {
            scheduleWriteTask(scope, key, task, true);
        }

        private CompletableFuture<Void> currentCompletion(ScopeKey scope) {
            return getCurrentWriteCompletion(scope);
        }

        private CompletableFuture<Void> currentCompletion(Predicate<ScopeKey> filter) {
            return getCurrentWriteCompletion(filter);
        }

        private void closeExecutors() {
            readExecutor.shutdownNow();
            writeExecutor.shutdownNow();
            callbackExecutor.shutdownNow();
        }
    }
}
