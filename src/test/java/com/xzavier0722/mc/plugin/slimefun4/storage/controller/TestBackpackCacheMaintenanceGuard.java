package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestBackpackCacheMaintenanceGuard {

    @Test
    void executesUncachedBatchExactlyOnce() {
        BackpackCache cache = new BackpackCache();
        AtomicInteger executions = new AtomicInteger();

        boolean executed = cache.runIfAllUncached(
                List.of("backpack-a", "backpack-b", "backpack-a"), executions::incrementAndGet);

        Assertions.assertTrue(executed);
        Assertions.assertEquals(1, executions.get());
        Assertions.assertTrue(BackpackCache.hasActiveControllerCache());
        Assertions.assertFalse(BackpackCache.isCachedInActiveController("backpack-a"));
    }
}
