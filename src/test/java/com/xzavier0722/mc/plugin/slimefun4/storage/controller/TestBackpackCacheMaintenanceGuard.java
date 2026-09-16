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
    void inFlightCacheMissDefersMaintenanceWithoutBlocking() throws Exception {
        BackpackCache cache = new BackpackCache();
        CountDownLatch loaderEntered = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);
        CountDownLatch batchRan = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> loader = executor.submit(() -> {
                cache.getOrLoad("backpack-a", () -> {
                    loaderEntered.countDown();
                    await(releaseLoader);
                    return null;
                });
            });

            Assertions.assertTrue(loaderEntered.await(2, TimeUnit.SECONDS));
            Future<Boolean> maintenance = executor.submit(
                    () -> cache.runIfAllUncached(List.of("backpack-a"), batchRan::countDown));

            Assertions.assertFalse(maintenance.get(500, TimeUnit.MILLISECONDS));
            Assertions.assertEquals(1L, batchRan.getCount());

            releaseLoader.countDown();
            loader.get(2, TimeUnit.SECONDS);
        } finally {
            releaseLoader.countDown();
            executor.shutdownNow();
            cache.clean();
        }
    }

    @Test
    void independentCacheMissLoadsMayOverlap() throws Exception {
        BackpackCache cache = new BackpackCache();
        CountDownLatch bothEntered = new CountDownLatch(2);
        CountDownLatch releaseLoads = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> {
                cache.getOrLoad("backpack-a", () -> {
                    bothEntered.countDown();
                    await(releaseLoads);
                    return null;
                });
            });
            Future<?> second = executor.submit(() -> {
                cache.getOrLoad("backpack-b", () -> {
                    bothEntered.countDown();
                    await(releaseLoads);
                    return null;
                });
            });

            Assertions.assertTrue(bothEntered.await(2, TimeUnit.SECONDS));
            releaseLoads.countDown();
            first.get(2, TimeUnit.SECONDS);
            second.get(2, TimeUnit.SECONDS);
        } finally {
            releaseLoads.countDown();
            executor.shutdownNow();
            cache.clean();
        }
    }

    @Test
    void maintenanceDoesNotBlockUnrelatedCacheReads() throws Exception {
        BackpackCache cache = new BackpackCache();
        CountDownLatch maintenanceEntered = new CountDownLatch(1);
        CountDownLatch releaseMaintenance = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> maintenance = executor.submit(() -> cache.runIfAllUncached(List.of("backpack-a"), () -> {
                maintenanceEntered.countDown();
                await(releaseMaintenance);
            }));

            Assertions.assertTrue(maintenanceEntered.await(2, TimeUnit.SECONDS));
            Future<Boolean> unrelatedRead = executor.submit(() -> cache.isCached("backpack-b"));
            Assertions.assertFalse(unrelatedRead.get(500, TimeUnit.MILLISECONDS));

            releaseMaintenance.countDown();
            Assertions.assertTrue(maintenance.get(2, TimeUnit.SECONDS));
        } finally {
            releaseMaintenance.countDown();
            executor.shutdownNow();
            cache.clean();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test latch timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
