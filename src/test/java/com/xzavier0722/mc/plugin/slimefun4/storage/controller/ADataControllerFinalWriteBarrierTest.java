package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataType;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class ADataControllerFinalWriteBarrierTest {

    @Test
    void finalBarrierRefusesAnActiveGenericWrite() throws Exception {
        var controller = new TestController();
        var release = new CountDownLatch(1);
        var started = new CountDownLatch(1);
        var finished = new CountDownLatch(1);

        try {
            controller.scheduleGeneric(() -> {
                started.countDown();
                await(release);
                finished.countDown();
            });

            assertTrue(started.await(5, TimeUnit.SECONDS));
            var actionRan = new AtomicBoolean();
            assertFalse(controller.runFinalWriteGate(() -> actionRan.set(true)));
            assertFalse(actionRan.get());

            release.countDown();
            assertTrue(finished.await(5, TimeUnit.SECONDS));
            assertTrue(waitForFinalGate(controller, actionRan));
            assertTrue(actionRan.get());
        } finally {
            release.countDown();
            controller.closeExecutors();
        }
    }

    @Test
    void genericWriteSubmissionWaitsUntilFinalBarrierReleases() throws Exception {
        var controller = new TestController();
        var gateEntered = new CountDownLatch(1);
        var releaseGate = new CountDownLatch(1);
        var writeRan = new CountDownLatch(1);

        try {
            CompletableFuture<Boolean> gate = CompletableFuture.supplyAsync(() -> controller.runFinalWriteGate(() -> {
                gateEntered.countDown();
                await(releaseGate);
            }));
            assertTrue(gateEntered.await(5, TimeUnit.SECONDS));

            CompletableFuture<Void> submit =
                    CompletableFuture.runAsync(() -> controller.scheduleGeneric(writeRan::countDown));
            assertFalse(writeRan.await(150, TimeUnit.MILLISECONDS));

            releaseGate.countDown();
            assertTrue(gate.get(5, TimeUnit.SECONDS));
            submit.get(5, TimeUnit.SECONDS);
            assertTrue(writeRan.await(5, TimeUnit.SECONDS));
        } finally {
            releaseGate.countDown();
            controller.closeExecutors();
        }
    }

    private static boolean waitForFinalGate(TestController controller, AtomicBoolean actionRan) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (controller.runFinalWriteGate(() -> actionRan.set(true))) {
                return true;
            }
            Thread.sleep(10L);
        }
        return false;
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for test latch");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

    private static final class TestController extends ADataController {
        private TestController() {
            super(DataType.BLOCK_STORAGE);
            readExecutor = Executors.newSingleThreadExecutor();
            writeExecutor = new ThreadPoolExecutor(
                    1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
            callbackExecutor = Executors.newSingleThreadExecutor();
        }

        private void scheduleGeneric(Runnable task) {
            scheduleWriteTask(task);
        }

        private boolean runFinalWriteGate(Runnable action) {
            return runIfAllWriteWorkIdle(action);
        }

        private void closeExecutors() {
            readExecutor.shutdownNow();
            writeExecutor.shutdownNow();
            callbackExecutor.shutdownNow();
        }
    }
}
