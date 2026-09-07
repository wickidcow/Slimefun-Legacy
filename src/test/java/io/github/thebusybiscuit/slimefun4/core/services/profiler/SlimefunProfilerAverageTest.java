package io.github.thebusybiscuit.slimefun4.core.services.profiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class SlimefunProfilerAverageTest {

    @Test
    void returnsZeroWhenNoSamplesExist() {
        SlimefunProfiler profiler = new SlimefunProfiler();

        assertEquals(0L, profiler.getAndResetAverageTimings());
        assertEquals(0.0, profiler.getAndResetAverageNanosecondTimings());

        profiler.kill();
    }

    @Test
    void resetsMillisecondAndNanosecondSamplesIndependently() throws ReflectiveOperationException {
        SlimefunProfiler profiler = new SlimefunProfiler();
        atomicLong(profiler, "totalMsTicked").set(20L);
        atomicInteger(profiler, "millisecondSamples").set(2);
        atomicLong(profiler, "totalNsTicked").set(900L);
        atomicInteger(profiler, "nanosecondSamples").set(3);

        assertEquals(10L, profiler.getAndResetAverageTimings());
        assertEquals(300.0, profiler.getAndResetAverageNanosecondTimings());
        assertEquals(0L, profiler.getAndResetAverageTimings());
        assertEquals(0.0, profiler.getAndResetAverageNanosecondTimings());

        profiler.kill();
    }

    @Test
    void idleTickerCycleDoesNotStartProfiler() {
        SlimefunProfiler profiler = new SlimefunProfiler();

        assertFalse(profiler.startIfRequested());
        assertFalse(profiler.isProfiling());
        assertEquals(0L, profiler.newEntry());

        profiler.kill();
    }

    @Test
    void queuedSummaryStartsRequestedTickerCycle() {
        SlimefunProfiler profiler = new SlimefunProfiler();
        profiler.requestSummary(inspector(new AtomicInteger()));

        assertTrue(profiler.startIfRequested());
        assertTrue(profiler.isProfiling());
        assertTrue(profiler.newEntry() > 0L);

        profiler.kill();
    }

    @Test
    void explicitStartRemainsUnconditional() {
        SlimefunProfiler profiler = new SlimefunProfiler();

        profiler.start();

        assertTrue(profiler.isProfiling());
        assertTrue(profiler.newEntry() > 0L);

        profiler.kill();
    }

    @Test
    void suppressesSupersededCycleReport() throws ReflectiveOperationException {
        SlimefunProfiler profiler = new SlimefunProfiler();
        AtomicInteger messages = new AtomicInteger();
        profiler.requestSummary(inspector(messages));

        profiler.start();
        Method finishReport = SlimefunProfiler.class.getDeclaredMethod("finishReport");
        finishReport.setAccessible(true);
        finishReport.invoke(profiler);

        assertEquals(0, messages.get());
        assertEquals(1, queue(profiler, "requests").size());
        profiler.kill();
    }

    private PerformanceInspector inspector(AtomicInteger messages) {
        return new PerformanceInspector() {
            @Override
            public boolean isValid() {
                return true;
            }

            @Override
            public void sendMessage(String message) {
                messages.incrementAndGet();
            }

            @Override
            public boolean isVerbose() {
                return false;
            }

            @Override
            public SummaryOrderType getOrderType() {
                return SummaryOrderType.HIGHEST;
            }
        };
    }

    private AtomicLong atomicLong(SlimefunProfiler profiler, String name) throws ReflectiveOperationException {
        Field field = SlimefunProfiler.class.getDeclaredField(name);
        field.setAccessible(true);
        return (AtomicLong) field.get(profiler);
    }

    private AtomicInteger atomicInteger(SlimefunProfiler profiler, String name) throws ReflectiveOperationException {
        Field field = SlimefunProfiler.class.getDeclaredField(name);
        field.setAccessible(true);
        return (AtomicInteger) field.get(profiler);
    }

    @SuppressWarnings("unchecked")
    private Queue<PerformanceInspector> queue(SlimefunProfiler profiler, String name)
            throws ReflectiveOperationException {
        Field field = SlimefunProfiler.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Queue<PerformanceInspector>) field.get(profiler);
    }
}
