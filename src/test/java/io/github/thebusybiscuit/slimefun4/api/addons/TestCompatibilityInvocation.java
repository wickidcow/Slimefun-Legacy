package io.github.thebusybiscuit.slimefun4.api.addons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TestCompatibilityInvocation {

    @Test
    void testSuccessUnavailableAndFailure() {
        CompatibilityInvocation<String> success = CompatibilityInvocation.success("ok");
        assertTrue(success.isSuccess());
        assertEquals("ok", success.getValue().orElseThrow());

        CompatibilityInvocation<String> unavailable = CompatibilityInvocation.unavailable();
        assertEquals(CompatibilityInvocationStatus.UNAVAILABLE, unavailable.getStatus());
        assertFalse(unavailable.getValue().isPresent());

        IllegalStateException failure = new IllegalStateException("broken integration");
        CompatibilityInvocation<String> failed = CompatibilityInvocation.failed(failure);
        assertEquals(CompatibilityInvocationStatus.FAILED, failed.getStatus());
        assertEquals(failure, failed.getFailure().orElseThrow());
    }
}
