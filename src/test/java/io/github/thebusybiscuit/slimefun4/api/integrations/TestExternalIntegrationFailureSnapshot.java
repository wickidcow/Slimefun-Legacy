package io.github.thebusybiscuit.slimefun4.api.integrations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TestExternalIntegrationFailureSnapshot {

    @Test
    void testRetryState() {
        ExternalIntegrationFailureSnapshot snapshot = new ExternalIntegrationFailureSnapshot(
                "rebar",
                "Rebar",
                "Rebar",
                "block-inspection",
                IllegalStateException.class.getName(),
                "boom",
                3,
                5L,
                2L,
                1000L,
                2000L,
                4500L);

        assertEquals("rebar", snapshot.getIntegrationId());
        assertEquals(3, snapshot.getConsecutiveFailures());
        assertEquals(5L, snapshot.getFailuresObserved());
        assertEquals(2L, snapshot.getSuppressedReports());
        assertTrue(snapshot.isPaused(2500L));
        assertEquals(2L, snapshot.getRetrySeconds(2500L));
        assertFalse(snapshot.isPaused(4500L));
        assertEquals(0L, snapshot.getRetrySeconds(4500L));
    }
}
