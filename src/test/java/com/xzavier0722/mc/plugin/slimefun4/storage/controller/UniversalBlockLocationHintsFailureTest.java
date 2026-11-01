package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/** Pure helper regressions; these do not claim database recovery or world-thread ownership coverage. */
class UniversalBlockLocationHintsFailureTest {

    @RepeatedTest(25)
    void staleValidatorCannotEvictAReplacementInstalledByAnotherThread() throws Exception {
        var hints = new UniversalBlockLocationHints();
        UUID previous = UUID.randomUUID();
        UUID replacement = UUID.randomUUID();
        String key = "world;3:64:5";
        hints.remember(key, previous);
        var validating = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var lookup = executor.submit(() -> hints.resolve(
                    key,
                    id -> {
                        assertEquals(previous, id);
                        validating.countDown();
                        await(release);
                        return Optional.<UUID>empty();
                    },
                    Optional::<UUID>empty,
                    id -> id));
            await(validating);
            hints.remember(key, replacement);
            release.countDown();
            assertTrue(lookup.get(5, TimeUnit.SECONDS).isEmpty());
            assertEquals(replacement, hints.resolve(
                    key,
                    id -> id.equals(replacement) ? Optional.of(id) : Optional.empty(),
                    () -> fail("The old validator evicted the newer identity"),
                    id -> id).orElseThrow());
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Lookup worker leaked");
        }
    }

    @Test
    void validatorFailurePropagatesInsteadOfPretendingTheBlockIsAbsent() {
        var hints = new UniversalBlockLocationHints();
        UUID id = UUID.randomUUID();
        hints.remember("key", id);
        var expected = new IllegalStateException("Live validation failed");
        var actual = assertThrows(IllegalStateException.class, () -> hints.resolve(
                "key",
                candidate -> { throw expected; },
                () -> fail("A validation failure must not be replaced by a successful fallback"),
                record -> id));
        assertSame(expected, actual);
    }

    @Test
    void failedOriginalReadDoesNotWarmTheCacheAndAllowsARealRetry() {
        var hints = new UniversalBlockLocationHints();
        UUID id = UUID.randomUUID();
        var attempts = new AtomicInteger();
        var expected = new IllegalStateException("Original read failed");
        var actual = assertThrows(IllegalStateException.class, () -> hints.resolve(
                "key",
                candidate -> Optional.empty(),
                () -> { attempts.incrementAndGet(); throw expected; },
                record -> id));
        assertSame(expected, actual);
        assertEquals(0, hints.size());
        assertEquals(id, hints.resolve(
                "key",
                candidate -> Optional.empty(),
                () -> { attempts.incrementAndGet(); return Optional.of(id); },
                record -> record).orElseThrow());
        assertEquals(2, attempts.get());
    }

    @Test
    void nullLocationAlwaysUsesTheOriginalReadWithoutProducingAHint() {
        var hints = new UniversalBlockLocationHints();
        UUID id = UUID.randomUUID();
        var scans = new AtomicInteger();
        for (int i = 0; i < 3; i++) {
            assertEquals(id, hints.resolve(
                    null,
                    candidate -> fail("A null-world lookup must not validate a cached identity"),
                    () -> { scans.incrementAndGet(); return Optional.of(id); },
                    record -> fail("A null-world lookup must not construct an acceleration hint")).orElseThrow());
        }
        assertEquals(3, scans.get());
        assertEquals(0, hints.size());
    }

    @Test
    void simultaneousWarmupsStayWithinTheConfiguredCapacity() throws Exception {
        var hints = new UniversalBlockLocationHints(4);
        var pool = Executors.newFixedThreadPool(6);
        var start = new CountDownLatch(1);
        var results = new ArrayList<Future<?>>();
        try {
            for (int i = 0; i < 96; i++) {
                int index = i;
                results.add(pool.submit(() -> {
                    await(start);
                    hints.remember("world;" + index + ":64:1", UUID.randomUUID());
                }));
            }
            start.countDown();
            for (var task : results) {
                task.get(10, TimeUnit.SECONDS);
            }
            assertTrue(hints.size() <= 4,
                    "Concurrent cache warmups must not overshoot the configured hint cap");
            assertTrue(hints.size() > 0);
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS),
                    "Concurrent hint warmup workers leaked");
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS), "Lookup barrier timed out");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }
}
