package io.github.thebusybiscuit.slimefun4.api.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.Test;

class TestPlatformRequirements {

    @Test
    void testCompatibleRequirements() {
        PlatformCompatibilityService service = service(new PlatformProfile(
                "Purpur",
                "Purpur test",
                "1.21.11",
                new MinecraftVersionNumber(1, 21, 11),
                25,
                PlatformFamily.PURPUR,
                PlatformSupportLevel.SUPPORTED,
                EnumSet.of(PlatformCapability.PAPER_API, PlatformCapability.DATA_COMPONENT_API)));

        PlatformRequirements requirements = PlatformRequirements.builder()
                .minimumMinecraftVersion(1, 21, 10)
                .minimumJavaVersion(21)
                .requireCapabilities(PlatformCapability.PAPER_API, PlatformCapability.DATA_COMPONENT_API)
                .acceptFamilies(PlatformFamily.PAPER, PlatformFamily.PURPUR)
                .build();

        PlatformCompatibilityReport report = service.check(requirements);
        assertTrue(report.isCompatible());
        assertEquals("Compatible", report.describe());
    }

    @Test
    void testEveryIncompatibilityIsReported() {
        PlatformCompatibilityService service = service(new PlatformProfile(
                "Unknown",
                "Unknown test",
                "snapshot",
                null,
                17,
                PlatformFamily.UNKNOWN,
                PlatformSupportLevel.UNSUPPORTED,
                EnumSet.noneOf(PlatformCapability.class)));

        PlatformRequirements requirements = PlatformRequirements.builder()
                .minimumMinecraftVersion(1, 21, 0)
                .minimumJavaVersion(21)
                .requireCapability(PlatformCapability.PAPER_API)
                .acceptFamily(PlatformFamily.PAPER)
                .build();

        PlatformCompatibilityReport report = service.check(requirements);
        assertFalse(report.isCompatible());
        assertEquals(4, report.getIncompatibilities().size());
        assertTrue(report.describe().contains("Minecraft version could not be parsed"));
        assertTrue(report.describe().contains("Requires Java 21"));
        assertTrue(report.describe().contains("Missing capability: Paper API"));
        assertTrue(report.describe().contains("Unsupported platform family: Unknown"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> report.getIncompatibilities().add("mutation"));
    }

    @Test
    void testBuilderValidationAndDefensiveCopies() {
        assertThrows(
                IllegalArgumentException.class,
                () -> PlatformRequirements.builder().minimumJavaVersion(0));

        PlatformRequirements requirements = PlatformRequirements.builder()
                .requireCapability(PlatformCapability.PAPER_API)
                .acceptFamily(PlatformFamily.PAPER)
                .build();

        assertThrows(
                UnsupportedOperationException.class,
                () -> requirements.getRequiredCapabilities().add(PlatformCapability.DATA_COMPONENT_API));
        assertThrows(
                UnsupportedOperationException.class,
                () -> requirements.getAcceptedFamilies().add(PlatformFamily.PURPUR));
    }

    private static PlatformCompatibilityService service(PlatformProfile profile) {
        return new PlatformCompatibilityService() {
            @Override
            public @Nonnull PlatformProfile getProfile() {
                return profile;
            }

            @Override
            public boolean supports(@Nonnull PlatformCapability capability) {
                return profile.supports(capability);
            }

            @Override
            public boolean isMinecraftVersionAtLeast(int major, int minor, int patch) {
                return profile.getMinecraftVersion()
                        .map(version -> version.isAtLeast(new MinecraftVersionNumber(major, minor, patch)))
                        .orElse(false);
            }
        };
    }
}
