package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Bounded, detached diagnostics for observed inventory guards. This is not a transaction barrier
 * or a scan of persisted items. In-flight loads may appear alongside failed loads.
 */
public final class InventoryRecoverySnapshot {
    public static final int MAX_ENTRIES = 200;
    public static final int PAGE_SIZE = 20;

    public enum Kind {
        BLOCK_LOAD,
        UNIVERSAL_LOAD,
        BACKPACK_LOAD,
        UNIVERSAL_MIGRATION
    }

    public record Entry(Kind kind, String owner, String destination) {
        public Entry {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(owner, "owner");
        }
    }

    private static final Comparator<Entry> ORDER = Comparator.comparing(Entry::kind)
            .thenComparing(Entry::owner)
            .thenComparing(entry -> entry.destination() == null ? "" : entry.destination());
    private final Map<Kind, Long> counts;
    private final List<Entry> entries;

    private InventoryRecoverySnapshot(Map<Kind, Long> counts, List<Entry> entries) {
        this.counts = Collections.unmodifiableMap(new EnumMap<>(counts));
        this.entries = List.copyOf(entries);
    }

    public long count(Kind kind) {
        return counts.getOrDefault(Objects.requireNonNull(kind, "kind"), 0L);
    }

    public long totalEntries() {
        return counts.values().stream().mapToLong(Long::longValue).sum();
    }

    public boolean hasEntries() {
        return totalEntries() != 0;
    }

    public List<Entry> entries() {
        return entries;
    }

    public int pageCount() {
        return Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    public List<Entry> page(int page) {
        if (page < 1 || page > pageCount()) {
            throw new IllegalArgumentException("Page is outside the captured diagnostic sample");
        }
        int first = (page - 1) * PAGE_SIZE;
        return entries.subList(first, Math.min(first + PAGE_SIZE, entries.size()));
    }

    /** Combine snapshots from disjoint controller ownership scopes, retaining the same output cap. */
    public static InventoryRecoverySnapshot combine(
            InventoryRecoverySnapshot blocks, InventoryRecoverySnapshot profiles) {
        Objects.requireNonNull(blocks, "blocks");
        Objects.requireNonNull(profiles, "profiles");
        Collector result = new Collector();
        for (Kind kind : Kind.values()) {
            result.counts.put(kind, blocks.count(kind) + profiles.count(kind));
        }
        blocks.entries.forEach(result::sample);
        profiles.entries.forEach(result::sample);
        return result.build();
    }

    // Controller-only construction. No live maps, guard-clearing methods or mutable views escape.
    static final class Collector {
        private final EnumMap<Kind, Long> counts = new EnumMap<>(Kind.class);
        private final TreeSet<Entry> entries = new TreeSet<>(ORDER);

        void add(Kind kind, String owner, String destination) {
            Entry entry = new Entry(kind, owner, destination);
            counts.merge(kind, 1L, Long::sum);
            sample(entry);
        }

        private void sample(Entry entry) {
            entries.add(entry);
            if (entries.size() > MAX_ENTRIES) {
                entries.pollLast();
            }
        }

        InventoryRecoverySnapshot build() {
            return new InventoryRecoverySnapshot(counts, new ArrayList<>(entries));
        }
    }
}
