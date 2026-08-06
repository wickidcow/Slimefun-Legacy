package io.github.thebusybiscuit.slimefun4.core.services.compatibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.thebusybiscuit.slimefun4.api.addons.SlimefunCoreVariant;
import io.github.thebusybiscuit.slimefun4.api.platform.PlatformCapability;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TestAddonCompatibilityManifestReader {

    @Test
    void testCompleteManifest() throws Exception {
        String json = """
                {
                  "schema": 1,
                  "tested_core_variants": ["legacy", "gugu", "united"],
                  "minimum_minecraft": "1.21.11",
                  "minimum_java": 21,
                  "required_capabilities": ["PAPER_API", "DATA_COMPONENT_API"],
                  "accepted_platform_families": ["PAPER", "PURPUR"],
                  "required_plugins": ["InfinityExpansion2"],
                  "optional_plugins": ["Networks"],
                  "notes": "Storage integration"
                }
                """;

        var result = new AddonCompatibilityManifestReader().parse(stream(json));
        assertTrue(result.present());
        assertNull(result.error());
        assertNotNull(result.declaration());
        assertTrue(result.declaration().isTestedOn(SlimefunCoreVariant.LEGACY));
        assertEquals(3, result.declaration().getTestedCoreVariants().size());
        assertTrue(result.declaration()
                .getPlatformRequirements()
                .getRequiredCapabilities()
                .contains(PlatformCapability.DATA_COMPONENT_API));
        assertEquals("Storage integration", result.declaration().getNotes());
    }

    @Test
    void testInvalidManifestIsReportedWithoutThrowing() throws Exception {
        String json = """
                {
                  "schema": 1,
                  "tested_core_variants": ["imaginary-core"]
                }
                """;

        var result = new AddonCompatibilityManifestReader().parse(stream(json));
        assertTrue(result.present());
        assertNull(result.declaration());
        assertTrue(result.error().contains("Unknown core variant"));
    }

    @Test
    void testMissingSchemaIsRejected() throws Exception {
        var result = new AddonCompatibilityManifestReader().parse(stream("{}"));
        assertTrue(result.present());
        assertNull(result.declaration());
        assertTrue(result.error().contains("schema -1"));
    }

    private static ByteArrayInputStream stream(String json) {
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }
}
