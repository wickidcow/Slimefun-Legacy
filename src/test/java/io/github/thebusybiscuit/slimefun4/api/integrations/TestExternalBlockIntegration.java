package io.github.thebusybiscuit.slimefun4.api.integrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.EnumSet;
import org.bukkit.NamespacedKey;
import org.junit.jupiter.api.Test;

class TestExternalBlockIntegration {

    @Test
    void copiesMappedCapabilities() {
        var capabilities = EnumSet.of(
                ExternalIntegrationCapability.INVENTORY,
                ExternalIntegrationCapability.CARGO);
        var integration = new ExternalBlockIntegration(
                "pylon",
                "Pylon",
                "Pylon",
                "io.github.pylonmc.pylon.content.machine.TestMachine",
                new NamespacedKey("pylon", "test_machine"),
                capabilities,
                "Mapped from Rebar markers");

        capabilities.clear();

        assertEquals("pylon", integration.getIntegrationId());
        assertEquals("pylon:test_machine", integration.getContentKey().toString());
        assertEquals(
                EnumSet.of(ExternalIntegrationCapability.INVENTORY, ExternalIntegrationCapability.CARGO),
                integration.getCapabilities());
        assertThrows(
                UnsupportedOperationException.class,
                () -> integration.getCapabilities().add(ExternalIntegrationCapability.ENERGY));
    }
}
