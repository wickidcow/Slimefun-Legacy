package io.github.thebusybiscuit.slimefun4.core.ticker;

/**
 * Pure policy shared by the optional chunk ticker governor and its operator
 * diagnostics. No block, inventory, Bukkit world or thread access is needed.
 */
public final class ChunkTickerBudgetPolicy {

    public static final int DEFAULT_MAX_TICKERS_PER_CHUNK_PER_CYCLE = 512;

    private ChunkTickerBudgetPolicy() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * A corrupted or unset budget must not accidentally become unlimited.
     */
    public static int effectiveLimit(int configured) {
        return configured > 0 ? configured : DEFAULT_MAX_TICKERS_PER_CHUNK_PER_CYCLE;
    }

    /**
     * Number of Slimefun cycles required to select every unique registered
     * machine at least once when the chunk topology remains unchanged.
     *
     * <p>This is not a wall-clock latency, whole-server MSPT or throughput
     * guarantee; paused, unloaded and delayed callbacks have other gates.</p>
     */
    public static int cyclesToVisitAll(int registered, int configuredLimit) {
        if (registered <= 0) {
            return 0;
        }

        final int maximum = effectiveLimit(configuredLimit);
        // Overflow-safe ceiling division for large machine registration counts.
        return 1 + (registered - 1) / maximum;
    }
}
