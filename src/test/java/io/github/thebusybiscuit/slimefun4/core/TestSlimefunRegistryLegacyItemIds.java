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
