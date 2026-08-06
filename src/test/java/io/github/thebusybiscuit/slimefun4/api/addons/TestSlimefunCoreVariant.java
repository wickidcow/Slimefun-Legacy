package io.github.thebusybiscuit.slimefun4.api.addons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class TestSlimefunCoreVariant {

    @Test
    void testStableIdentifiers() {
        assertEquals(SlimefunCoreVariant.LEGACY, SlimefunCoreVariant.fromId("legacy").orElseThrow());
        assertEquals(
                SlimefunCoreVariant.SLIMEFUN_CORE,
                SlimefunCoreVariant.fromId("SLIMEFUN_CORE").orElseThrow());
        assertFalse(SlimefunCoreVariant.fromId("not-a-core").isPresent());
    }
}
