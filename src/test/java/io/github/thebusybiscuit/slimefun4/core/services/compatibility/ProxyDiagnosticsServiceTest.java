package io.github.thebusybiscuit.slimefun4.core.services.compatibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProxyDiagnosticsServiceTest {

    @Test
    void shouldRecognizeVelocityModernForwarding() {
        ProxyDiagnosticsSnapshot snapshot =
                ProxyDiagnosticsService.analyze(false, true, true, true, false, true, true, true, false);

        assertEquals(ProxyDiagnosticsSnapshot.ForwardingMode.VELOCITY_MODERN, snapshot.getForwardingMode());
        assertTrue(snapshot.isHealthy());
        assertTrue(snapshot.isVelocityEnabled());
        assertTrue(snapshot.isVelocitySecretConfigured());
        assertFalse(snapshot.isBungeeEnabled());
    }

    @Test
    void shouldRecognizeBungeeCompatibleForwarding() {
        ProxyDiagnosticsSnapshot snapshot =
                ProxyDiagnosticsService.analyze(false, false, true, false, true, true, true, true, false);

        assertEquals(ProxyDiagnosticsSnapshot.ForwardingMode.BUNGEE_COMPATIBLE, snapshot.getForwardingMode());
        assertTrue(snapshot.isHealthy());
        assertTrue(snapshot.isBungeeEnabled());
    }

    @Test
    void shouldRejectConflictingForwardingModes() {
        ProxyDiagnosticsSnapshot snapshot =
                ProxyDiagnosticsService.analyze(false, true, true, true, true, true, true, true, false);

        assertEquals(ProxyDiagnosticsSnapshot.ForwardingMode.CONFLICTING, snapshot.getForwardingMode());
        assertFalse(snapshot.isHealthy());
        assertTrue(snapshot.getFailures().stream().anyMatch(message -> message.contains("both enabled")));
    }

    @Test
    void shouldRejectOfflineBackendWithoutForwarding() {
        ProxyDiagnosticsSnapshot snapshot =
                ProxyDiagnosticsService.analyze(false, false, true, false, false, true, true, true, false);

        assertEquals(ProxyDiagnosticsSnapshot.ForwardingMode.UNSAFE_OFFLINE, snapshot.getForwardingMode());
        assertFalse(snapshot.isHealthy());
        assertTrue(snapshot.getFailures().stream().anyMatch(message -> message.contains("offline mode")));
    }

    @Test
    void shouldRejectVelocityWithoutSecret() {
        ProxyDiagnosticsSnapshot snapshot =
                ProxyDiagnosticsService.analyze(false, true, true, false, false, true, true, true, false);

        assertEquals(ProxyDiagnosticsSnapshot.ForwardingMode.VELOCITY_MODERN, snapshot.getForwardingMode());
        assertFalse(snapshot.isHealthy());
        assertTrue(snapshot.getFailures().stream().anyMatch(message -> message.contains("secret")));
    }

    @Test
    void shouldRejectProxyForwardingWithBackendOnlineMode() {
        ProxyDiagnosticsSnapshot snapshot =
                ProxyDiagnosticsService.analyze(true, false, true, false, true, true, true, true, false);

        assertEquals(ProxyDiagnosticsSnapshot.ForwardingMode.BUNGEE_COMPATIBLE, snapshot.getForwardingMode());
        assertFalse(snapshot.isHealthy());
        assertTrue(snapshot.getFailures().stream().anyMatch(message -> message.contains("online-mode=true")));
    }
}
