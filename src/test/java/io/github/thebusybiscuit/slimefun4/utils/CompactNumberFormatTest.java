package io.github.thebusybiscuit.slimefun4.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** Ensure ROOT-locale output survives concurrent formatter calls on Folia. */
class CompactNumberFormatTest {

    @Test
    void preservesCompactValuesAndLegacyDecimalRounding() {
        assertEquals("0", NumberUtils.getCompactDouble(0));
        assertEquals("1.23", NumberUtils.getCompactDouble(1.234));
        assertEquals("1K", NumberUtils.getCompactDouble(1000));
        assertEquals("1.25K", NumberUtils.getCompactDouble(1250));
        assertEquals("-1.25K", NumberUtils.getCompactDouble(-1250));
        assertEquals("1.25M", NumberUtils.getCompactDouble(1_250_000));
        assertEquals("1.25B", NumberUtils.getCompactDouble(1_250_000_000));
        assertEquals("1.25T", NumberUtils.getCompactDouble(1_250_000_000_000.0));
        assertEquals("1.25Q", NumberUtils.getCompactDouble(1_250_000_000_000_000.0));
        assertEquals("1.23", NumberUtils.roundDecimalNumber(1.234));
        assertEquals("1.23ms", NumberUtils.getAsMillis(1_234_000L));
    }

    @Test
    void concurrentRegionsPreserveCompactNumbersAndProfilerDecimals() throws Exception {
        List<Double> values = List.of(1.234, 1250.0, 1_250_000.0, 1_250_000_000.0, 1_250_000_000_000.0);
        List<String> expected = List.of("1.23", "1.25K", "1.25M", "1.25B", "1.25T");
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> jobs = new ArrayList<>();
            for (int worker = 0; worker < 8; worker++) {
                jobs.add(pool.submit(() -> {
                    start.await();
                    for (int pass = 0; pass < 400; pass++) {
                        for (int i = 0; i < values.size(); i++) {
                            assertEquals(expected.get(i), NumberUtils.getCompactDouble(values.get(i)));
                        }
                        assertEquals("1.23", NumberUtils.roundDecimalNumber(1.234));
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> job : jobs) {
                job.get(20, TimeUnit.SECONDS);
            }
        } finally {
            start.countDown();
            pool.shutdownNow();
        }
    }
}
