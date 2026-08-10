package io.github.thebusybiscuit.slimefun4.api.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TestMinecraftVersionNumber {

    @Test
    void testReleaseVersionParsing() {
        assertEquals(
                new MinecraftVersionNumber(1, 21, 11),
                MinecraftVersionNumber.parse("1.21.11").orElseThrow());
        assertEquals(
                new MinecraftVersionNumber(26, 1, 0),
                MinecraftVersionNumber.parse("26.1").orElseThrow());
        assertEquals(
                new MinecraftVersionNumber(1, 21, 2),
                MinecraftVersionNumber.parse("1.21.2-pre2").orElseThrow());
    }

    @Test
    void testSnapshotAndMalformedVersionsAreNotGuessed() {
        assertTrue(MinecraftVersionNumber.parse("23w31a").isEmpty());
        assertTrue(MinecraftVersionNumber.parse("1").isEmpty());
        assertTrue(MinecraftVersionNumber.parse("").isEmpty());
        assertTrue(MinecraftVersionNumber.parse(null).isEmpty());
    }

    @Test
    void testSemanticOrdering() {
        MinecraftVersionNumber current = new MinecraftVersionNumber(1, 21, 11);
        assertTrue(current.isAtLeast(new MinecraftVersionNumber(1, 21, 10)));
        assertTrue(current.isAtLeast(new MinecraftVersionNumber(1, 21, 11)));
        assertFalse(current.isAtLeast(new MinecraftVersionNumber(26, 1, 0)));
        assertTrue(current.isBefore(new MinecraftVersionNumber(26, 1, 0)));
    }

    @Test
    void testNegativeComponentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MinecraftVersionNumber(1, 21, -1));
    }
}
