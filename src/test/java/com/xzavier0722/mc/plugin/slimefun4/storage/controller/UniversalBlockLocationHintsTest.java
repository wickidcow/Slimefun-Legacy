package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Every hint must be validated; historical scan is always authoritative. */
class UniversalBlockLocationHintsTest {

    @Test
    void coldLookupWarmsAndSubsequentValidLookupAvoidsTheFullScan() {
        var hints = new UniversalBlockLocationHints();
        UUID id = UUID.randomUUID();
        AtomicInteger scanCalls = new AtomicInteger();
        String key = "world;12:64:19";
        for (int i = 0; i < 3; i++) {
            assertEquals("loaded", hints.resolve(
                    key, candidate -> candidate.equals(id) ? Optional.of("loaded") : Optional.empty(),
                    () -> {
                        scanCalls.incrementAndGet();
                        return Optional.of("loaded");
                    },
                    record -> id).orElseThrow());
        }
        assertEquals(1, scanCalls.get(), "Only the first cold lookup should scan");
    }

    @Test
    void missingAndUnloadedHintNeverTurnsIntoFalseSuccessfulBlock() {
        var hints = new UniversalBlockLocationHints();
        UUID old = UUID.randomUUID();
        String key = "world;0:64:0";
        hints.remember(key, old);
        AtomicInteger scans = new AtomicInteger();
        assertTrue(hints.resolve(key, ignored -> Optional.empty(), () -> {
            scans.incrementAndGet();
            return Optional.empty();
        }, ignored -> old).isEmpty());
        assertEquals(1, scans.get());
        assertEquals(0, hints.size());
    }

    @Test
    void staleOldUuidFallbackFindsReplacementAndUpdatesCache() {
        var hints = new UniversalBlockLocationHints();
        UUID previous = UUID.randomUUID();
        UUID current = UUID.randomUUID();
        String key = "world;3:72:7";
        hints.remember(key, previous);
        AtomicInteger scans = new AtomicInteger();
        assertEquals("new", hints.resolve(
                key, candidate -> candidate.equals(current) ? Optional.of("new") : Optional.empty(),
                () -> {
                    scans.incrementAndGet();
                    return Optional.of("new");
                }, ignored -> current).orElseThrow());
        assertEquals("new", hints.resolve(
                key, candidate -> candidate.equals(current) ? Optional.of("new") : Optional.empty(),
                () -> {
                    scans.incrementAndGet();
                    return Optional.empty();
                }, ignored -> current).orElseThrow());
        assertEquals(1, scans.get());
    }

    @Test
    void failedLoadOrDeletionMayInvalidateWithoutRemovingNewerIdentity() {
        var hints = new UniversalBlockLocationHints();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        String key = "world;3:72:7";
        hints.remember(key, first);
        hints.remember(key, second);
        hints.forget(key, first);
        AtomicInteger scans = new AtomicInteger();
        assertTrue(hints.resolve(
                key, id -> id.equals(second) ? Optional.of(true) : Optional.empty(),
                () -> {
                    scans.incrementAndGet();
                    return Optional.empty();
                }, ignored -> second).orElseThrow());
        assertEquals(0, scans.get());
        hints.forget(key, second);
        assertFalse(hints.resolve(
                key, id -> Optional.of(true),
                Optional::empty,
                ignored -> second).isPresent());
    }

    @Test
    void crossWorldCoordinatesAndNullWorldAlwaysUseCorrectFallback() {
        var hints = new UniversalBlockLocationHints();
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        hints.remember("world;0:64:0", a);
        hints.remember("nether;0:64:0", b);
        assertEquals(a, hints.resolve("world;0:64:0", Optional::of, Optional::empty, id -> id).orElseThrow());
        assertEquals(b, hints.resolve("nether;0:64:0", Optional::of, Optional::empty, id -> id).orElseThrow());
        AtomicInteger cold = new AtomicInteger();
        assertEquals(a, hints.resolve(
                null, Optional::of, () -> {
                    cold.incrementAndGet();
                    return Optional.of(a);
                }, id -> id).orElseThrow());
        assertEquals(1, cold.get());
    }

    @Test
    void boundedHintsCanEvictWithoutLosingTheOriginalLookup() {
        var hints = new UniversalBlockLocationHints(2);
        UUID id = UUID.randomUUID();
        hints.remember("world;1:64:1", id);
        hints.remember("world;2:64:2", id);
        hints.remember("world;3:64:3", id);
        assertTrue(hints.size() <= 2);
        AtomicInteger scans = new AtomicInteger();
        assertEquals(id, hints.resolve(
                "world;1:64:1", Optional::of, () -> {
                    scans.incrementAndGet();
                    return Optional.of(id);
                }, ignored -> id).orElseThrow());
        assertEquals(1, scans.get());
        hints.clear();
        assertEquals(0, hints.size());
    }
}
