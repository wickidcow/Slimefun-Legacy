package io.github.thebusybiscuit.slimefun4.implementation.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TestGuideSearchIndex {

    @Test
    void idFilterDoesNotResolveDynamicFields() {
        AtomicInteger groupCalls = new AtomicInteger();
        AtomicInteger recipeCalls = new AtomicInteger();

        boolean matched = GuideSearchIndex.matchesSmart(
                GuideSearchIndex.SearchQuery.parse("id:carbonado"),
                "carbonado edged capacitor",
                "carbonado_edged_capacitor",
                "slimefun",
                "energy_tech",
                "stores energy",
                () -> {
                    groupCalls.incrementAndGet();
                    return "energy and electricity";
                },
                () -> {
                    recipeCalls.incrementAndGet();
                    return "enhanced crafting table";
                });

        assertTrue(matched);
        assertEquals(0, groupCalls.get());
        assertEquals(0, recipeCalls.get());
    }

    @Test
    void categoryFilterDoesNotResolveDynamicFields() {
        AtomicInteger groupCalls = new AtomicInteger();
        AtomicInteger recipeCalls = new AtomicInteger();

        boolean matched = GuideSearchIndex.matchesSmart(
                GuideSearchIndex.SearchQuery.parse("category:energy"),
                "carbonado edged capacitor",
                "carbonado_edged_capacitor",
                "slimefun",
                "energy_tech",
                "stores energy",
                () -> {
                    groupCalls.incrementAndGet();
                    return "energy and electricity";
                },
                () -> {
                    recipeCalls.incrementAndGet();
                    return "enhanced crafting table";
                });

        assertTrue(matched);
        assertEquals(0, groupCalls.get());
        assertEquals(0, recipeCalls.get());
    }

    @Test
    void cachedCategoryMatchDoesNotResolveDynamicFields() {
        AtomicInteger groupCalls = new AtomicInteger();
        AtomicInteger recipeCalls = new AtomicInteger();

        boolean matched = GuideSearchIndex.matchesSmart(
                GuideSearchIndex.SearchQuery.parse("energy"),
                "capacitor",
                "basic_capacitor",
                "slimefun",
                "energy_tech",
                "stores power",
                () -> {
                    groupCalls.incrementAndGet();
                    return "energy and electricity";
                },
                () -> {
                    recipeCalls.incrementAndGet();
                    return "enhanced crafting table";
                });

        assertTrue(matched);
        assertEquals(0, groupCalls.get());
        assertEquals(0, recipeCalls.get());
    }

    @Test
    void cachedFieldMatchDoesNotResolveDynamicFields() {
        AtomicInteger groupCalls = new AtomicInteger();
        AtomicInteger recipeCalls = new AtomicInteger();

        boolean matched = GuideSearchIndex.matchesSmart(
                GuideSearchIndex.SearchQuery.parse("carbonado capacitor"),
                "carbonado edged capacitor",
                "carbonado_edged_capacitor",
                "slimefun",
                "energy_tech",
                "stores energy",
                () -> {
                    groupCalls.incrementAndGet();
                    return "energy and electricity";
                },
                () -> {
                    recipeCalls.incrementAndGet();
                    return "enhanced crafting table";
                });

        assertTrue(matched);
        assertEquals(0, groupCalls.get());
        assertEquals(0, recipeCalls.get());
    }

    @Test
    void genericSearchFallsThroughToGroupBeforeRecipe() {
        AtomicInteger groupCalls = new AtomicInteger();
        AtomicInteger recipeCalls = new AtomicInteger();

        boolean matched = GuideSearchIndex.matchesSmart(
                GuideSearchIndex.SearchQuery.parse("electricity"),
                "capacitor",
                "basic_capacitor",
                "slimefun",
                "energy_tech",
                "stores power",
                () -> {
                    groupCalls.incrementAndGet();
                    return "energy and electricity";
                },
                () -> {
                    recipeCalls.incrementAndGet();
                    return "enhanced crafting table";
                });

        assertTrue(matched);
        assertEquals(1, groupCalls.get());
        assertEquals(0, recipeCalls.get());
    }

    @Test
    void genericSearchUsesRecipeOnlyWhenNeeded() {
        AtomicInteger groupCalls = new AtomicInteger();
        AtomicInteger recipeCalls = new AtomicInteger();

        boolean matched = GuideSearchIndex.matchesSmart(
                GuideSearchIndex.SearchQuery.parse("enhanced"),
                "capacitor",
                "basic_capacitor",
                "slimefun",
                "energy_tech",
                "stores power",
                () -> {
                    groupCalls.incrementAndGet();
                    return "energy and electricity";
                },
                () -> {
                    recipeCalls.incrementAndGet();
                    return "enhanced crafting table";
                });

        assertTrue(matched);
        assertEquals(1, groupCalls.get());
        assertEquals(1, recipeCalls.get());
    }

    @Test
    void unmatchedQueryStopsAfterOneDynamicResolutionEach() {
        AtomicInteger groupCalls = new AtomicInteger();
        AtomicInteger recipeCalls = new AtomicInteger();

        boolean matched = GuideSearchIndex.matchesSmart(
                GuideSearchIndex.SearchQuery.parse("missing token"),
                "capacitor",
                "basic_capacitor",
                "slimefun",
                "energy_tech",
                "stores power",
                () -> {
                    groupCalls.incrementAndGet();
                    return "energy and electricity";
                },
                () -> {
                    recipeCalls.incrementAndGet();
                    return "enhanced crafting table";
                });

        assertFalse(matched);
        assertEquals(1, groupCalls.get());
        assertEquals(1, recipeCalls.get());
    }
}
