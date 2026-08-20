package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class BeaconPlusEffectTest {

    @Test
    void registryContainsTwentyNineConfigurablePowersAndOneMigrationTombstone() {
        assertEquals(30, BeaconPlusEffect.values().length);
        assertEquals(29, BeaconPlusEffect.configurableValues().length);
        assertFalse(BeaconPlusEffect.SCALE.isConfigurable());
        assertTrue(BeaconPlusEffect.RADIATION_ABSORBER.isConfigurable());
    }

    @Test
    void configurableEffectsRoundTripThroughPersistentStorage() {
        EnumSet<BeaconPlusEffect> configurable = EnumSet.allOf(BeaconPlusEffect.class);
        configurable.remove(BeaconPlusEffect.SCALE);

        assertEquals(configurable, BeaconPlusEffect.parse(BeaconPlusEffect.serialize(configurable)));
        assertTrue(BeaconPlusEffect.parse("scale").isEmpty());
    }

    @Test
    void radiationAbsorberUsesStablePersistentId() {
        assertEquals("radiation_absorber", BeaconPlusEffect.RADIATION_ABSORBER.getId());
        assertEquals(
                EnumSet.of(BeaconPlusEffect.RADIATION_ABSORBER),
                BeaconPlusEffect.parse("radiation_absorber"));
    }

    @Test
    void everyConfigurableEffectHasRenderableMenuMetadata() {
        for (BeaconPlusEffect effect : BeaconPlusEffect.configurableValues()) {
            assertFalse(effect.getId().isBlank(), effect.name());
            assertFalse(effect.getDisplayName().isBlank(), effect.name());
            assertFalse(effect.getDescription().isBlank(), effect.name());
            assertTrue(effect.getIcon().isItem(), effect.name());
        }
    }
}
