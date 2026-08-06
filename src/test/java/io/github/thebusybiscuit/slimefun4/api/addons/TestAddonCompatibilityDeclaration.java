package io.github.thebusybiscuit.slimefun4.api.addons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.thebusybiscuit.slimefun4.api.platform.PlatformCapability;
import io.github.thebusybiscuit.slimefun4.api.platform.PlatformRequirements;
import org.junit.jupiter.api.Test;

class TestAddonCompatibilityDeclaration {

    @Test
    void testDeclarationIsImmutable() {
        AddonCompatibilityDeclaration declaration = AddonCompatibilityDeclaration.builder()
                .testCores(SlimefunCoreVariant.LEGACY, SlimefunCoreVariant.GUGU)
                .platformRequirements(PlatformRequirements.builder()
                        .requireCapability(PlatformCapability.PAPER_API)
                        .build())
                .requirePlugin("InfinityExpansion2")
                .optionalPlugin("Networks")
                .notes("Cross-core storage integration")
                .build();

        assertTrue(declaration.isTestedOn(SlimefunCoreVariant.LEGACY));
        assertEquals(2, declaration.getTestedCoreVariants().size());
        assertEquals("Cross-core storage integration", declaration.getNotes());
        assertThrows(
                UnsupportedOperationException.class,
                () -> declaration.getRequiredPlugins().add("DynaTech"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> declaration.getTestedCoreVariants().add(SlimefunCoreVariant.UNITED));
    }

    @Test
    void testBuilderRejectsInvalidDependencyDeclarations() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AddonCompatibilityDeclaration.builder().requirePlugin(" "));
        assertThrows(
                IllegalStateException.class,
                () -> AddonCompatibilityDeclaration.builder()
                        .requirePlugin("Networks")
                        .optionalPlugin("Networks")
                        .build());
    }

    @Test
    void testDependencyOverlapIsCaseInsensitive() {
        var builder = AddonCompatibilityDeclaration.builder()
                .requirePlugin("Networks")
                .optionalPlugin("networks");
        assertThrows(IllegalStateException.class, builder::build);
    }
}
