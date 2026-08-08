package io.github.thebusybiscuit.slimefun4.core.services.compatibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TestDefaultAddonRuntimeHealthService {

    @Test
    void aggregatesFailuresWithoutDisablingOrMutatingTheAddon() {
        DefaultAddonRuntimeHealthService service = new DefaultAddonRuntimeHealthService();

        service.recordFailure("TestAddon", "1.0.0", "item-load:TEST", new IllegalStateException("first"));
        service.recordFailure("TestAddon", "1.0.1", "integration-hook:Test", new LinkageError("second"));

        var snapshot = service.getFailure("testaddon").orElseThrow();
        assertEquals("TestAddon", snapshot.getPluginName());
        assertEquals("1.0.1", snapshot.getPluginVersion());
        assertEquals("integration-hook:Test", snapshot.getOperation());
        assertEquals(LinkageError.class.getName(), snapshot.getExceptionClass());
        assertEquals(2L, snapshot.getObservedFailures());
        assertEquals(2L, service.getObservedFailureCount());
        assertTrue(service.clear("TESTADDON"));
        assertTrue(service.getFailures().isEmpty());
    }
}
