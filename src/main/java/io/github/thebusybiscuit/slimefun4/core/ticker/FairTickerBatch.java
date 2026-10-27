package io.github.thebusybiscuit.slimefun4.core.ticker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A bounded, fair, rotating view of one immutable chunk-ticker snapshot.
 *
 * <p>Each registration appears once in every complete rotation. A changed
 * snapshot starts a new rotation; no block metadata or inventory is touched.
 * This helper deliberately has no Bukkit or scheduler dependencies.</p>
 *
 * @param <T> the ticker registration type
 */
public final class FairTickerBatch<T> {
    private final List<T> entries;
    private int cursor;

    public FairTickerBatch(Collection<? extends T> entries) {
        this(entries, 0);
    }

    /**
     * Starts near an earlier cursor after the registered ticker set changes.
     * Topology changes therefore do not repeatedly reset every overloaded
     * chunk to machine number one and starve later registrations.
     */
    public FairTickerBatch(Collection<? extends T> entries, int initialCursor) {
        this.entries = List.copyOf(entries);
        this.cursor = this.entries.isEmpty() ? 0 : Math.floorMod(initialCursor, this.entries.size());
    }

    public synchronized int nextCursor() {
        return cursor;
    }

    /**
     * Selects a contiguous circular window and advances the cursor.
     * The caller determines whether to execute these tickers.
     */
    public synchronized List<T> next(int maximum) {
        if (maximum < 1) {
            throw new IllegalArgumentException("Ticker budget must be positive");
        }
        if (entries.isEmpty()) {
            return List.of();
        }

        int amount = Math.min(maximum, entries.size());
        List<T> result = new ArrayList<>(amount);
        for (int i = 0; i < amount; i++) {
            result.add(entries.get((cursor + i) % entries.size()));
        }

        cursor = (cursor + amount) % entries.size();
        return result;
    }

    public int size() {
        return entries.size();
    }
}
