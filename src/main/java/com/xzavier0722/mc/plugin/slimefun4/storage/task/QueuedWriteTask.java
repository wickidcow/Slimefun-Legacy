package com.xzavier0722.mc.plugin.slimefun4.storage.task;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordKey;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class QueuedWriteTask implements Runnable {
    private final Queue<RecordKey> queue = new LinkedList<>();
    private final Map<RecordKey, Runnable> tasks = new HashMap<>();
    private final CompletableFuture<Void> completion = new CompletableFuture<>();
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean finished = new AtomicBoolean();
    private volatile boolean done = false;
    private volatile boolean aborted = false;
    private volatile Throwable firstFailure;

    @Override
    public final void run() {
        if (!started.compareAndSet(false, true)) {
            return;
        }

        try {
            if (aborted) {
                return;
            }

            var task = next();
            while (!aborted && task != null) {
                try {
                    task.run();
                } catch (Throwable e) {
                    recordFailure(e);
                    try {
                        onError(e);
                    } catch (Throwable callbackFailure) {
                        e.addSuppressed(callbackFailure);
                        recordFailure(callbackFailure);
                    }
                }
                task = next();
            }

            try {
                onSuccess();
            } catch (Throwable e) {
                recordFailure(e);
                e.printStackTrace();
            }
        } finally {
            finish();
        }
    }

    protected void onSuccess() {}

    protected void onError(Throwable e) {}

    public synchronized boolean queue(RecordKey key, Runnable next) {
        if (done || aborted) {
            return false;
        }

        if (tasks.put(key, next) == null) {
            return queue.offer(key);
        }
        return true;
    }

    public void abort() {
        aborted = true;
        if (!started.get()) {
            finish();
        }
    }

    /**
     * Returns a dependent future that completes when this queued write batch has fully drained.
     *
     * <p>The returned future cannot be used to complete the queue's internal completion signal. Aborted queues and
     * batches that observed a write failure complete exceptionally.
     *
     * @return a future representing completion of this queued write batch
     */
    public CompletableFuture<Void> getCompletionFuture() {
        return completion.copy();
    }

    private synchronized Runnable next() {
        var key = queue.poll();
        if (key == null) {
            done = true;
            return null;
        }
        return tasks.remove(key);
    }

    private void recordFailure(Throwable failure) {
        if (firstFailure == null) {
            synchronized (this) {
                if (firstFailure == null) {
                    firstFailure = failure;
                }
            }
        }
    }

    private void finish() {
        if (!finished.compareAndSet(false, true)) {
            return;
        }

        if (aborted) {
            completion.completeExceptionally(new CancellationException("Queued write task was aborted"));
        } else if (firstFailure != null) {
            completion.completeExceptionally(firstFailure);
        } else {
            completion.complete(null);
        }
    }

    @Override
    public String toString() {
        return "QueuedWriteTask{" + "queue=" + queue + '}';
    }
}
