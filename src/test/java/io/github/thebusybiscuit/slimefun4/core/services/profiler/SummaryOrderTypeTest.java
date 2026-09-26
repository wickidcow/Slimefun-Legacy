package io.github.thebusybiscuit.slimefun4.core.services.profiler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SummaryOrderTypeTest {

    @Test
    void averageSortUsesSectionCountsAndPreservesOriginalTotals() {
        Map<String, Long> timings = new LinkedHashMap<>();
        timings.put("many-fast-machines", 1_000L);
        timings.put("one-slow-machine", 900L);

        List<Map.Entry<String, Long>> results = SummaryOrderType.AVERAGE.sort(
                timings.entrySet(),
                key -> key.equals("many-fast-machines") ? 100 : 1);

        assertEquals("one-slow-machine", results.get(0).getKey());
        assertEquals(900L, results.get(0).getValue());
        assertEquals("many-fast-machines", results.get(1).getKey());
        assertEquals(1_000L, results.get(1).getValue());
    }

    @Test
    void averageSortTreatsMissingCountsAsSingleSample() {
        Map<String, Long> timings = new LinkedHashMap<>();
        timings.put("missing-count", 500L);
        timings.put("known-count", 600L);

        List<Map.Entry<String, Long>> results =
                SummaryOrderType.AVERAGE.sort(timings.entrySet(), key -> key.equals("known-count") ? 2 : 0);

        assertEquals("missing-count", results.get(0).getKey());
        assertEquals(500L, results.get(0).getValue());
        assertEquals("known-count", results.get(1).getKey());
        assertEquals(600L, results.get(1).getValue());
    }
}
