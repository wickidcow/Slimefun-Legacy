package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.ScopeKey;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class BlockDataControllerDelayedWriteCountTest {

    @Test
    void reportsDeferredWritesWithoutMutatingTheQueue() throws Exception {
        BlockDataController controller = new BlockDataController();
        assertEquals(0, controller.getPendingDelayedWriteTaskCount());

        Method schedule = BlockDataController.class.getDeclaredMethod(
                "scheduleDelayedUpdateTask", LinkedKey.class, Runnable.class);
        schedule.setAccessible(true);
        schedule.invoke(
                controller,
                new LinkedKey(new ScopeKey(DataScope.BLOCK_DATA)),
                (Runnable) () -> {});

        assertEquals(1, controller.getPendingDelayedWriteTaskCount());
        assertEquals(1, controller.getPendingDelayedWriteTaskCount());
    }
}
