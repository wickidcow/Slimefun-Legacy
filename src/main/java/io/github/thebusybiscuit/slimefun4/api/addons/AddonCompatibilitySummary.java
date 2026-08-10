package io.github.thebusybiscuit.slimefun4.api.addons;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;

/** Immutable count summary for a set of addon compatibility results. */
@SlimefunAPI
public final class AddonCompatibilitySummary {

    private final Map<AddonCompatibilityStatus, Integer> counts;
    private final int total;

    private AddonCompatibilitySummary(Map<AddonCompatibilityStatus, Integer> counts, int total) {
        this.counts = Map.copyOf(counts);
        this.total = total;
    }

    public static @Nonnull AddonCompatibilitySummary from(@Nonnull List<AddonCompatibilityResult> results) {
        EnumMap<AddonCompatibilityStatus, Integer> counts = new EnumMap<>(AddonCompatibilityStatus.class);
        for (AddonCompatibilityStatus status : AddonCompatibilityStatus.values()) {
            counts.put(status, 0);
        }
        for (AddonCompatibilityResult result : List.copyOf(results)) {
            counts.compute(result.getStatus(), (status, count) -> count == null ? 1 : count + 1);
        }
        return new AddonCompatibilitySummary(counts, results.size());
    }

    public int getTotal() {
        return total;
    }

    public int getCount(@Nonnull AddonCompatibilityStatus status) {
        return counts.getOrDefault(status, 0);
    }

    public boolean hasProblems() {
        return getCount(AddonCompatibilityStatus.INCOMPATIBLE) > 0 || getCount(AddonCompatibilityStatus.DISABLED) > 0;
    }

    public @Nonnull Map<AddonCompatibilityStatus, Integer> getCounts() {
        return counts;
    }
}
