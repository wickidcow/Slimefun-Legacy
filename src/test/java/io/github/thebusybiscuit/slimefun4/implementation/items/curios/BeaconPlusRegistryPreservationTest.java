package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * A partially corrupt registry must not restore partial beacon owners,
 * overwrite saved Activator modes, or turn future values into OFF.
 */
class BeaconPlusRegistryPreservationTest {

    private static String location(UUID world, String x, String y, String z) {
        return world + ";" + x + ";" + y + ";" + z;
    }

    @Test
    void savedOwnersAndKnownActivatorModesParseWithoutLoss() {
        UUID world = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        Properties saved = new Properties();
        saved.setProperty(location(world, "12", "64", "-4"), owner + ";SINGLE;OFF");
        saved.setProperty(location(world, "16", "65", "-4"), owner + ";AREA_5X5;SPEED");
        saved.setProperty(location(world, "21", "66", "-4"), owner + ";CHUNK_ACTIVATOR;HASTE");
        assertEquals(3, BeaconPlusManager.validatedRegistryEntryCount(saved));
    }

    @Test
    void mirroredLegacyBeaconDataCannotOverrideNativeSavedOwnerAndMode() {
        UUID nativeOwner = UUID.fromString("984c4ff4-6ff7-4d36-99a9-64d3e15400bf");
        org.junit.jupiter.api.Assertions.assertFalse(BeaconPlusLegacyDataStore.mayImportForOwner(nativeOwner));
        org.junit.jupiter.api.Assertions.assertTrue(BeaconPlusLegacyDataStore.mayImportForOwner(null));
        org.junit.jupiter.api.Assertions.assertTrue(
                BeaconPlusLegacyDataStore.mayImportForOwner(BeaconPlusLegacyDataStore.LEGACY_IMPORTED_OWNER));
    }

    @Test
    void oneMalformedEntryRejectsTheEntireSavedRegistry() {
        UUID world = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        Properties saved = new Properties();
        saved.setProperty(location(world, "12", "64", "-4"), owner + ";SINGLE;OFF");
        saved.setProperty(location(world, "16", "65", "-4"), "corrupt-user-uuid;OFF;OFF");
        assertThrows(IllegalArgumentException.class,
                () -> BeaconPlusManager.validatedRegistryEntryCount(saved));
        saved.remove(location(world, "16", "65", "-4"));
        assertEquals(1, BeaconPlusManager.validatedRegistryEntryCount(saved));
    }

    @Test
    void unknownFutureModeMustNeverBeSilentlySavedAsOff() {
        UUID world = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        Properties saved = new Properties();
        saved.setProperty(location(world, "12", "64", "-4"), owner + ";AREA_7X7;OFF");
        assertThrows(IllegalArgumentException.class,
                () -> BeaconPlusManager.validatedRegistryEntryCount(saved));
        saved.setProperty(location(world, "12", "64", "-4"), owner + ";SINGLE;FUTURE_SUPPORT");
        assertThrows(IllegalArgumentException.class,
                () -> BeaconPlusManager.validatedRegistryEntryCount(saved));
    }

    @Test
    void twoKeysNormalizingToOneBlockCannotOverwriteOneAnother() {
        UUID world = UUID.randomUUID();
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        Properties saved = new Properties();
        saved.setProperty(location(world, "12", "64", "-4"), ownerA + ";SINGLE;OFF");
        saved.setProperty(location(world, "012", "64", "-4"), ownerB + ";OFF;OFF");
        assertThrows(IllegalArgumentException.class,
                () -> BeaconPlusManager.validatedRegistryEntryCount(saved));
    }
}
