package io.github.thebusybiscuit.slimefun4.api.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TestNetworkDiscoveryBudget {

    @Test
    void emptyAndSmallFrontiersPreserveInitialBatchSize() {
        var queue = new ConcurrentLinkedQueue<Integer>();
        assertEquals(0, Network.boundedDiscoverySteps(queue, 32));
        queue.add(1);
        queue.add(2);
        assertEquals(2, Network.boundedDiscoverySteps(queue, 32));
        assertEquals(2, queue.size());
        assertEquals(1, queue.poll());
        assertEquals(2, queue.poll());
    }

    @Test
    void largeFrontierVisitsOnlyConfiguredBudgetAndDoesNotDrainQueue() {
        var visits = new AtomicInteger();
        var queue = new ConcurrentLinkedQueue<Integer>() {
            @Override
            public int size() {
                throw new AssertionError("Discovery must not traverse the whole frontier");
            }

            @Override
            public Iterator<Integer> iterator() {
                Iterator<Integer> delegate = super.iterator();
                return new Iterator<>() {
                    @Override
                    public boolean hasNext() {
                        return delegate.hasNext();
                    }

                    @Override
                    public Integer next() {
                        visits.incrementAndGet();
                        return delegate.next();
                    }
                };
            }
        };
        for (int i = 0; i < 10000; i++) {
            queue.add(i);
        }
        assertEquals(32, Network.boundedDiscoverySteps(queue, 32));
        assertEquals(32, visits.get());

        // Counting cannot consume or reorder the original frontier.
        for (int i = 0; i < 10000; i++) {
            assertEquals(i, queue.poll());
        }
        assertEquals(0, Network.boundedDiscoverySteps(queue, 32));
        assertEquals(32, visits.get());
    }
}
