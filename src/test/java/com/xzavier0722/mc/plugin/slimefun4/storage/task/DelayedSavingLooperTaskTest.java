package com.xzavier0722.mc.plugin.slimefun4.storage.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.FieldKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.ScopeKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.LinkedKey;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DelayedSavingLooperTaskTest {

    @Test
    void forcedRunKeepsFailedTaskForRetry() throws Exception {
        var calls = new AtomicInteger();
        var parent = new ScopeKey(DataScope.NONE);
        var record = new RecordKey(DataScope.BLOCK_DATA);
        record.addCondition(FieldKey.LOCATION, "world;0:64:0");
        record.addCondition(FieldKey.DATA_KEY, "value");
        var linked = new LinkedKey(parent, record);
        var delayed = new DelayedTask(30, TimeUnit.SECONDS, () -> {
            if (calls.getAndIncrement() == 0) {
                throw new IllegalStateException("expected first-attempt failure");
            }
        });
        Map<LinkedKey, DelayedTask> tasks = new HashMap<>();
        tasks.put(linked, delayed);

        var looper = new DelayedSavingLooperTask(0, () -> new HashMap<>(tasks), tasks::remove);
        Thread.sleep(2L);
        looper.run();

        assertTrue(tasks.containsKey(linked));
        assertEquals(1, calls.get());

        looper.run();

        assertFalse(tasks.containsKey(linked));
        assertEquals(2, calls.get());
    }
}
