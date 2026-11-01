package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Non-authoritative, bounded location-to-UUID hints for already loaded
 * universal Slimefun blocks. Does not read or write world/storage data.
 *
 * <p>Cache hits MUST be revalidated against the live loaded record and exact
 * Location.equals comparison. Cold/stale hits run the old linear scan, which
 * remains the fallback for old-world records, failed hydration and migrations.
 * This is a safe adaptation of upstream GuGu's indexed lookup, not a port of
 * its unchecked UUID map.
 */
final class UniversalBlockLocationHints {

    private static final int DEFAULT_LIMIT = 65_536;
    private final int maxSize;
    private final ConcurrentMap<String, UUID> hints = new ConcurrentHashMap<>();

    UniversalBlockLocationHints() {
        this(DEFAULT_LIMIT);
    }

    UniversalBlockLocationHints(int maxSize) {
        if (maxSize < 1) {
            throw new IllegalArgumentException("Hint cache capacity must be positive");
        }
        this.maxSize = maxSize;
    }

    /**
     * A cached UUID is merely a candidate. If it no longer points at a fully
     * loaded, exactly matching block, invalidate that UUID atomically and
     * defer to the historical scan without changing any storage records.
     */
    <T> Optional<T> resolve(
            String locationKey,
            Function<UUID, Optional<T>> currentRecord,
            Supplier<Optional<T>> originalScan,
            Function<T, UUID> recordId) {
        Objects.requireNonNull(currentRecord, "currentRecord");
        Objects.requireNonNull(originalScan, "originalScan");
        Objects.requireNonNull(recordId, "recordId");

        if (locationKey != null) {
            UUID cached = hints.get(locationKey);
            if (cached != null) {
                Optional<T> candidate = currentRecord.apply(cached);
                if (candidate.isPresent()) {
                    return candidate;
                }
                hints.remove(locationKey, cached);
            }
        }

        Optional<T> found = Objects.requireNonNull(originalScan.get(), "originalScan result");
        if (locationKey != null && found.isPresent()) {
            remember(locationKey, Objects.requireNonNull(recordId.apply(found.get()), "record ID"));
        }
        return found;
    }

    synchronized void remember(String locationKey, UUID uuid) {
        if (locationKey == null || uuid == null) {
            return;
        }
        // Avoid clearing every hint at once when a large base reaches capacity.
        // Only a missing key needs a spare slot; replacing a known identity does
        // not increase the cache size. Reads and invalidations remain lock-free.
        if (!hints.containsKey(locationKey) && hints.size() >= maxSize) {
            var entries = hints.entrySet().iterator();
            if (entries.hasNext()) {
                var victim = entries.next();
                // Identity-sensitive removal never deletes a concurrent update.
                hints.remove(victim.getKey(), victim.getValue());
            }
        }
        hints.put(locationKey, uuid);
    }

    void forget(String locationKey, UUID uuid) {
        if (locationKey != null && uuid != null) {
            hints.remove(locationKey, uuid);
        }
    }

    void clear() {
        hints.clear();
    }

    int size() {
        return hints.size();
    }
}
