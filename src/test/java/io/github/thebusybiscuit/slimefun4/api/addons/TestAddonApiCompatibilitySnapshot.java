package io.github.thebusybiscuit.slimefun4.api.addons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class TestAddonApiCompatibilitySnapshot {

    @Test
    void testDefensiveEnumSets() {
        var targets = EnumSet.of(SlimefunCoreVariant.ORIGINAL, SlimefunCoreVariant.LEGACY);
        var capabilities = EnumSet.of(CrossForkApiCapability.ITEM_REGISTRATION);
        AddonApiCompatibilitySnapshot snapshot = new AddonApiCompatibilitySnapshot(
                SlimefunCoreVariant.LEGACY, targets, capabilities, true, 2, 3, 4L);

        targets.clear();
        capabilities.clear();

        assertEquals(2, snapshot.getCompatibilityTargets().size());
        assertTrue(snapshot.getCapabilities().contains(CrossForkApiCapability.ITEM_REGISTRATION));
        assertThrows(
                UnsupportedOperationException.class,
                () -> snapshot.getCompatibilityTargets().add(SlimefunCoreVariant.GUGU));
        assertTrue(snapshot.isInitialRegistrationFinalized());
        assertEquals(2, snapshot.getPendingRegistrationCallbacks());
        assertEquals(3, snapshot.getRuntimeRegisteredItems());
        assertEquals(4L, snapshot.getObservedAddonFailures());
    }
}
