package io.github.thebusybiscuit.slimefun4.api.integrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class TestExternalIntegrationStatus {

    @Test
    void copiesCapabilitiesAndExposesDetectionState() {
        var capabilities = EnumSet.of(
                ExternalIntegrationCapability.INVENTORY,
                ExternalIntegrationCapability.CARGO);
        var status = new ExternalIntegrationStatus(
                "rebar",
                "Rebar",
                "Rebar",
                "0.40.0",
                true,
                true,
                true,
                capabilities,
                "Bridge active");

        capabilities.clear();

        assertEquals("rebar", status.getIntegrationId());
        assertEquals("0.40.0", status.getPluginVersion());
        assertTrue(status.isDetected());
        assertTrue(status.isEnabled());
        assertTrue(status.isProviderRegistered());
        assertEquals(
                EnumSet.of(ExternalIntegrationCapability.INVENTORY, ExternalIntegrationCapability.CARGO),
                status.getCapabilities());
        assertThrows(
                UnsupportedOperationException.class,
                () -> status.getCapabilities().add(ExternalIntegrationCapability.ENERGY));
    }
}
