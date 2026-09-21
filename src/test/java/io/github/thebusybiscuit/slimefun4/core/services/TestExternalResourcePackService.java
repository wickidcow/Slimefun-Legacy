package io.github.thebusybiscuit.slimefun4.core.services;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class TestExternalResourcePackService {

    private static final String OFFICIAL =
            "https://github.com/wickidcow/SFL_RP_Official/releases/latest/download/SlimefunLegacyRP.zip";

    @Test
    void testLegacyDefaultPackUrlsMigrateToOfficialRelease() {
        Assertions.assertEquals(
                OFFICIAL,
                ExternalResourcePackService.normalizeLegacyResourcePackUrl(
                        "https://cdn.modrinth.com/data/TznkVJky/versions/nwij66MR/Slimefun-ResourcePack.zip"));
        Assertions.assertEquals(
                OFFICIAL,
                ExternalResourcePackService.normalizeLegacyResourcePackUrl(
                        "http://overlord.kicks-ass.org:8163/SlimefunLegacyRP.zip"));
        Assertions.assertEquals(
                OFFICIAL,
                ExternalResourcePackService.normalizeLegacyResourcePackUrl(
                        "https://github.com/wickidcow/SFL_ResourePack_UnOfficial/releases/latest/download/SlimefunLegacyRP.zip"));
        Assertions.assertEquals(
                OFFICIAL,
                ExternalResourcePackService.normalizeLegacyResourcePackUrl(
                        "https://github.com/wickidcow/Slimefun-Legacy/releases/latest/download/SlimefunLegacy-ResourcePack-1.21.11-26.3.zip"));
    }

    @Test
    void testCustomResourcePackUrlIsPreserved() {
        String custom = "https://example.com/custom-slimefun-pack.zip";
        Assertions.assertEquals(custom, ExternalResourcePackService.normalizeLegacyResourcePackUrl(custom));
    }

    @Test
    void testResourcePackOwnershipParsingIsBackwardsCompatible() {
        Assertions.assertEquals(ResourcePackOwnershipMode.AUTO, ResourcePackOwnershipMode.parse(null));
        Assertions.assertEquals(ResourcePackOwnershipMode.AUTO, ResourcePackOwnershipMode.parse(""));
        Assertions.assertEquals(ResourcePackOwnershipMode.AUTO, ResourcePackOwnershipMode.parse("unknown"));
        Assertions.assertEquals(ResourcePackOwnershipMode.AUTO, ResourcePackOwnershipMode.parse("auto"));
    }

    @Test
    void testResourcePackOwnershipParsingAcceptsExplicitModes() {
        Assertions.assertEquals(ResourcePackOwnershipMode.LEGACY, ResourcePackOwnershipMode.parse("legacy"));
        Assertions.assertEquals(ResourcePackOwnershipMode.EXTERNAL, ResourcePackOwnershipMode.parse("EXTERNAL"));
        Assertions.assertEquals(ResourcePackOwnershipMode.NONE, ResourcePackOwnershipMode.parse("none"));
        Assertions.assertEquals("external", ResourcePackOwnershipMode.EXTERNAL.configValue());
    }
}
