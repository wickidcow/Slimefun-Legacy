package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestBackpackCacheMaintenanceGuard {

    @Test
    void executesUncachedBatchExactlyOnce() {
        BackpackCache cache = new BackpackCache();
        AtomicInteger executions = new AtomicInteger();
        try {
            boolean executed = cache.runIfAllUncached(
                    List.of("backpack-a", "backpack-b", "backpack-a"), executions::incrementAndGet);

            Assertions.assertTrue(executed);
            Assertions.assertEquals(1, executions.get());
            Assertions.assertTrue(BackpackCache.hasActiveControllerCache());
            Assertions.assertFalse(BackpackCache.isCachedInActiveController("backpack-a"));
        } finally {
            cache.clean();
        }
        Assertions.assertFalse(BackpackCache.hasActiveControllerCache());
    }

    @Test
    void cacheMissLoadBlocksMaintenanceUntilLoadCompletes() throws Exception {
        BackpackCache cache = new BackpackCache();
        CountDownLatch loaderEntered = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);
        CountDownLatch batchRan = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> loader = executor.submit(() -> cache.getOrLoad("backpack-a", () -> {
                loaderEntered.countDown();
                try {
                    if (!releaseLoader.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("test loader timed out");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                return null;
            }));

            Assertions.assertTrue(loaderEntered.await(2, TimeUnit.SECONDS));
            Future<Boolean> maintenance = executor.submit(
                    () -> cache.runIfAllUncached(List.of("backpack-a"), batchRan::countDown));

            Assertions.assertFalse(batchRan.await(150, TimeUnit.MILLISECONDS));
            releaseLoader.countDown();

            loader.get(2, TimeUnit.SECONDS);
            Assertions.assertTrue(maintenance.get(2, TimeUnit.SECONDS));
            Assertions.assertTrue(batchRan.await(2, TimeUnit.SECONDS));
        } finally {
            releaseLoader.countDown();
            executor.shutdownNow();
            cache.clean();
        }
    }
}
