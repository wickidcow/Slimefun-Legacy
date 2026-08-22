package com.xzavier0722.mc.plugin.slimefun4.storage.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.FieldKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordKey;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class QueuedWriteTaskTest {

    @Test
    void completionWaitsForQueuedWork() throws Exception {
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var task = new QueuedWriteTask();
        task.queue(key("wait"), () -> {
            started.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("Timed out waiting to release queued write");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        });

        var thread = new Thread(task, "queued-write-completion-test");
        thread.start();
        assertTrue(started.await(5, TimeUnit.SECONDS));
        assertFalse(task.getCompletionFuture().isDone());

        release.countDown();
        thread.join(5000);

        assertFalse(thread.isAlive());
        task.getCompletionFuture().join();
        assertTrue(task.getCompletionFuture().isDone());
    }

    @Test
    void failedWriteStillDrainsRemainingQueueAndCompletesExceptionally() {
        var completedWrites = new AtomicInteger();
        var reportedErrors = new AtomicInteger();
        var task = new QueuedWriteTask() {
            @Override
            protected void onError(Throwable error) {
                reportedErrors.incrementAndGet();
            }
        };

        task.queue(key("failure"), () -> {
            throw new IllegalStateException("expected write failure");
        });
        task.queue(key("after-failure"), completedWrites::incrementAndGet);
        task.run();

        assertEquals(1, completedWrites.get());
        assertEquals(1, reportedErrors.get());
        assertTrue(task.getCompletionFuture().isCompletedExceptionally());
        var failure = assertThrows(CompletionException.class, () -> task.getCompletionFuture().join());
        assertTrue(failure.getCause() instanceof IllegalStateException);
    }

    @Test
    void abortBeforeExecutionCompletesAndSkipsQueuedWork() {
        var ran = new AtomicBoolean();
        var task = new QueuedWriteTask();
        task.queue(key("abort"), () -> ran.set(true));

        task.abort();
        task.run();

        assertFalse(ran.get());
        assertTrue(task.getCompletionFuture().isCompletedExceptionally());
        var failure = assertThrows(CompletionException.class, () -> task.getCompletionFuture().join());
        assertTrue(failure.getCause() instanceof CancellationException);
    }

    @Test
    void repeatedRecordKeyKeepsLatestQueuedWrite() {
        var value = new AtomicInteger();
        var task = new QueuedWriteTask();
        var key = key("coalesced");

        task.queue(key, () -> value.set(1));
        task.queue(key, () -> value.set(2));
        task.run();

        assertEquals(2, value.get());
        task.getCompletionFuture().join();
    }

    private static RecordKey key(String value) {
        var key = new RecordKey(DataScope.BLOCK_DATA);
        key.addCondition(FieldKey.DATA_KEY, value);
        return key;
    }
}
