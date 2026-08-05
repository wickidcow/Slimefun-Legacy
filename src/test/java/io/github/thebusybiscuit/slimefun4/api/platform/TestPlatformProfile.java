package io.github.thebusybiscuit.slimefun4.api.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class TestPlatformProfile {

    @Test
    void testCapabilitiesAreDefensivelyCopied() {
        EnumSet<PlatformCapability> source = EnumSet.of(PlatformCapability.PAPER_API);
        PlatformProfile profile = new PlatformProfile(
                "Paper",
                "Paper test",
                "1.21.11",
                new MinecraftVersionNumber(1, 21, 11),
                25,
                PlatformFamily.PAPER,
                PlatformSupportLevel.SUPPORTED,
                source);

        source.add(PlatformCapability.DATA_COMPONENT_API);

        assertTrue(profile.supports(PlatformCapability.PAPER_API));
        assertFalse(profile.supports(PlatformCapability.DATA_COMPONENT_API));
        assertThrows(
                UnsupportedOperationException.class,
                () -> profile.getCapabilities().add(PlatformCapability.DATA_COMPONENT_API));
        assertEquals(new MinecraftVersionNumber(1, 21, 11), profile.getMinecraftVersion().orElseThrow());
    }

    @Test
    void testUnknownProfileIsSafe() {
        PlatformProfile profile = PlatformProfile.unknown();
        assertEquals(PlatformFamily.UNKNOWN, profile.getFamily());
        assertTrue(profile.getMinecraftVersion().isEmpty());
        assertTrue(profile.getCapabilities().isEmpty());
    }
}
