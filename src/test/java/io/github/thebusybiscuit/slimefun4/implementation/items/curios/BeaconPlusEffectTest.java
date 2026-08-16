package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class BeaconPlusEffectTest {

    @Test
    void registryStillContainsExactlyThirtyEffects() {
        assertEquals(30, BeaconPlusEffect.values().length);
    }

    @Test
    void allEffectsRoundTripThroughPersistentStorage() {
        EnumSet<BeaconPlusEffect> all = EnumSet.allOf(BeaconPlusEffect.class);
        assertEquals(all, BeaconPlusEffect.parse(BeaconPlusEffect.serialize(all)));
    }

    @Test
    void everyEffectHasRenderableMenuMetadata() {
        for (BeaconPlusEffect effect : BeaconPlusEffect.values()) {
            assertFalse(effect.getId().isBlank(), effect.name());
            assertFalse(effect.getDisplayName().isBlank(), effect.name());
            assertFalse(effect.getDescription().isBlank(), effect.name());
            assertTrue(effect.getIcon().isItem(), effect.name());
        }
    }

    @Test
    void gravityWellIsFiveTimesStrongerThanLegacyNormalPull() {
        assertEquals(1.50D, BeaconPlusGravity.getPullStrength(0));
        assertEquals(2.10D, BeaconPlusGravity.getPullStrength(1));
    }

    @Test
    void electricityCostRulesRemainBoundedAndPredictable() {
        assertEquals(16, BeaconPlus.calculateFieldEnergyCost(EnumSet.of(BeaconPlusEffect.STRENGTH)));
        assertEquals(0, BeaconPlus.calculateFieldEnergyCost(EnumSet.of(BeaconPlusEffect.ACTIVATOR)));
        assertEquals(0, BeaconPlus.calculateFieldEnergyCost(EnumSet.of(BeaconPlusEffect.EXTRA_POWER)));
        assertEquals(
                24,
                BeaconPlus.calculateFieldEnergyCost(
                        EnumSet.of(BeaconPlusEffect.STRENGTH, BeaconPlusEffect.EXTRA_POWER)));
        assertEquals(
                48,
                BeaconPlus.calculateFieldEnergyCost(
                        EnumSet.of(BeaconPlusEffect.STRENGTH, BeaconPlusEffect.SPEED, BeaconPlusEffect.EXTRA_POWER)));
    }
}
