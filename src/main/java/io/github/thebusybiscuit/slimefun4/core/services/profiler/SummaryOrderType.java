package io.github.thebusybiscuit.slimefun4.core.services.profiler;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Holds the different types of ordering for summaries.
 *
 * @author Walshy
 */
public enum SummaryOrderType {

    /**
     * Sort by highest to the lowest total timings
     */
    HIGHEST,
    /**
     * Sort by lowest to the highest total timings
     */
    LOWEST,
    /**
     * Sort by average timings (highest to lowest)
     */
    AVERAGE;

    @ParametersAreNonnullByDefault
    List<Map.Entry<String, Long>> sort(
            Set<Map.Entry<String, Long>> entrySet, ToIntFunction<String> sampleCountResolver) {
        switch (this) {
            case HIGHEST:
                return entrySet.stream()
                        .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                        .collect(Collectors.toList());
            case LOWEST:
                return entrySet.stream()
                        .sorted(Comparator.comparingLong(Map.Entry::getValue))
                        .collect(Collectors.toList());
            default:
                /*
                 * Sort by the section-specific average without replacing the original entries.
                 * The formatter still needs the total timing value to display both total and
                 * average correctly. Different summary sections also use different sample counts
                 * (item IDs, chunks, addons), so the caller supplies the matching resolver.
                 */
                return entrySet.stream()
                        .sorted(Comparator.<Map.Entry<String, Long>>comparingDouble(entry -> {
                                    int count = Math.max(1, sampleCountResolver.applyAsInt(entry.getKey()));
                                    return (double) entry.getValue() / count;
                                })
                                .reversed())
                        .collect(Collectors.toList());
        }
    }
}
