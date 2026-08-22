package com.xzavier0722.mc.plugin.slimefun4.storage.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DelayedTaskTest {

    @Test
    void immediateRunReportsSuccess() {
        var calls = new AtomicInteger();
        var task = new DelayedTask(30, TimeUnit.SECONDS, calls::incrementAndGet);

        assertTrue(task.runNow());
        assertEquals(1, calls.get());
        assertTrue(task.isExecuted());
        assertTrue(task.runNow());
        assertEquals(1, calls.get());
    }

    @Test
    void failedImmediateRunCanBeRetried() {
        var calls = new AtomicInteger();
        var task = new DelayedTask(30, TimeUnit.SECONDS, () -> {
            if (calls.getAndIncrement() == 0) {
                throw new IllegalStateException("expected first-attempt failure");
            }
        });

        assertFalse(task.runNow());
        assertTrue(task.isExecuted());
        assertTrue(task.runNow());
        assertEquals(2, calls.get());
    }
}
