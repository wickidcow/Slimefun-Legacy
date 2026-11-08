package io.github.thebusybiscuit.slimefun4.core.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Migration must preserve an explicit historical off state, comments and
 * unrelated owner settings rather than rewriting an entire addons config.
 */
class CuriositiesBeaconPolicyMigrationTest {

    private static final String OLD_PREFIX = """
            config-version: 2
            enabled: true
            SlimefunLegacyAddition:
              PoweredBeacon:
            """;

    @Test
    void oldDisabledBeaconStaysDisabled() {
        String before = OLD_PREFIX
                + "    chunk-loading-enabled: false # owner's existing choice\n"
                + "    electric-operation:\n"
                + "      enabled: true\n";
        String upgraded = CuriositiesConfig.writeBeaconChunkLoadingGuide(before);
        assertTrue(upgraded.contains("chunk-loading-mode: OFF"));
        assertTrue(upgraded.contains("chunk-loading-enabled: false # owner's existing choice"));
        assertTrue(upgraded.contains("    electric-operation:\n      enabled: true\n"));
        assertEquals(upgraded, CuriositiesConfig.writeBeaconChunkLoadingGuide(upgraded));
    }

    @Test
    void oldEnabledBeaconDefaultsToOnlineRatherThanAlways() {
        String before = OLD_PREFIX + "    chunk-loading-enabled: true\n";
        String upgraded = CuriositiesConfig.writeBeaconChunkLoadingGuide(before);
        assertTrue(upgraded.contains("chunk-loading-mode: ONLINE"));
        assertTrue(upgraded.contains("chunk-loading-enabled: true"));
    }

    @Test
    void oldCustomBeaconWithoutBooleanGetsOnlineDefault() {
        String upgraded = CuriositiesConfig.writeBeaconChunkLoadingGuide(
                OLD_PREFIX + "    enabled: true\n");
        assertTrue(upgraded.contains("chunk-loading-mode: ONLINE"));
        assertTrue(upgraded.contains("  PoweredBeacon:"));
    }

    @Test
    void absentBeaconAndExplicitModeAreLeftUntouched() {
        String unrelated = "SlimefunLegacyAddition:\n  ExtraGear:\n    enabled: true\n";
        assertEquals(unrelated, CuriositiesConfig.writeBeaconChunkLoadingGuide(unrelated));
        String explicit = OLD_PREFIX
                + "    chunk-loading-mode: ALWAYS\n"
                + "    chunk-loading-enabled: false\n";
        assertEquals(explicit, CuriositiesConfig.writeBeaconChunkLoadingGuide(explicit));
    }
}
