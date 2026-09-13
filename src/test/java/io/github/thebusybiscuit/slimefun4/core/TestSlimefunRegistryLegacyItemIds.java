package io.github.thebusybiscuit.slimefun4.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TestSlimefunRegistryLegacyItemIds {

    @Test
    void registersLegacyIdsWithoutPollutingLiveItemRegistry() {
        SlimefunRegistry registry = new SlimefunRegistry();

        registry.registerLegacySlimefunItemId("INFINITE_INGOT", "IE_INFINITY_INGOT");

        assertEquals("IE_INFINITY_INGOT", registry.getLegacySlimefunItemIdTarget("INFINITE_INGOT").orElseThrow());
        assertEquals(Map.of("INFINITE_INGOT", "IE_INFINITY_INGOT"), registry.getLegacySlimefunItemIds());
        assertFalse(registry.getSlimefunItemIds().containsKey("INFINITE_INGOT"));
    }

    @Test
    void resolvesLegacyIdChainsToFinalTarget() {
        SlimefunRegistry registry = new SlimefunRegistry();

        registry.registerLegacySlimefunItemId("VERY_OLD_MACHINE", "OLD_MACHINE");
        registry.registerLegacySlimefunItemId("OLD_MACHINE", "CURRENT_MACHINE");

        assertEquals("OLD_MACHINE", registry.getLegacySlimefunItemIdTarget("VERY_OLD_MACHINE").orElseThrow());
        assertEquals("CURRENT_MACHINE", registry.resolveLegacySlimefunItemId("VERY_OLD_MACHINE").orElseThrow());
        assertEquals("CURRENT_MACHINE", registry.resolveLegacySlimefunItemId("OLD_MACHINE").orElseThrow());
        assertFalse(registry.resolveLegacySlimefunItemId("UNKNOWN_MACHINE").isPresent());
    }

    @Test
    void allowsIdempotentRegistrationButRejectsConflictingTargets() {
        SlimefunRegistry registry = new SlimefunRegistry();

        registry.registerLegacySlimefunItemId("OLD_MACHINE", "NEW_MACHINE");
        registry.registerLegacySlimefunItemId("OLD_MACHINE", "NEW_MACHINE");

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.registerLegacySlimefunItemId("OLD_MACHINE", "DIFFERENT_MACHINE"));
        assertThrows(
                IllegalArgumentException.class,
                () -> registry.registerLegacySlimefunItemId("SAME_ID", "SAME_ID"));
    }

    @Test
    void rejectsDirectAndTransitiveAliasCycles() {
        SlimefunRegistry registry = new SlimefunRegistry();
        registry.registerLegacySlimefunItemId("OLD_A", "OLD_B");

        assertThrows(
                IllegalArgumentException.class,
                () -> registry.registerLegacySlimefunItemId("OLD_B", "OLD_A"));

        registry.registerLegacySlimefunItemId("OLD_B", "OLD_C");
        assertThrows(
                IllegalArgumentException.class,
                () -> registry.registerLegacySlimefunItemId("OLD_C", "OLD_A"));
        assertFalse(registry.getLegacySlimefunItemIdTarget("OLD_C").isPresent());
    }

    @Test
    void rejectsBlankLegacyMappings() {
        SlimefunRegistry registry = new SlimefunRegistry();

        assertThrows(IllegalArgumentException.class, () -> registry.registerLegacySlimefunItemId("", "NEW"));
        assertThrows(IllegalArgumentException.class, () -> registry.registerLegacySlimefunItemId("OLD", ""));
        assertThrows(IllegalArgumentException.class, () -> registry.registerLegacySlimefunItemId("   ", "NEW"));
        assertThrows(IllegalArgumentException.class, () -> registry.registerLegacySlimefunItemId("OLD", "   "));
    }

    @Test
    void exposesLegacyMappingsAsReadOnly() {
        SlimefunRegistry registry = new SlimefunRegistry();
        registry.registerLegacySlimefunItemId("OLD", "NEW");

        assertThrows(UnsupportedOperationException.class, () -> registry.getLegacySlimefunItemIds().put("X", "Y"));
    }
}
