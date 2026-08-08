package io.github.thebusybiscuit.slimefun4.core.services.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.thebusybiscuit.slimefun4.api.lifecycle.CoreLifecyclePhase;
import io.github.thebusybiscuit.slimefun4.api.lifecycle.CoreLifecycleState;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

class TestDefaultCoreLifecycleService {

    @Test
    void preservesLifecycleOrderAndIsolatesShutdownFailures() {
        DefaultCoreLifecycleService service =
                new DefaultCoreLifecycleService(Logger.getLogger("TestDefaultCoreLifecycleService"));

        service.beginStart();
        service.enterPhase(CoreLifecyclePhase.STORAGE);
        service.markRunning();
        assertEquals(CoreLifecycleState.RUNNING, service.getSnapshot().getState());
        assertEquals(CoreLifecyclePhase.RUNNING, service.getSnapshot().getPhase());

        service.beginShutdown();
        AtomicBoolean laterCleanupRan = new AtomicBoolean();
        assertFalse(service.runShutdownStep("failing-step", () -> {
            throw new IllegalStateException("boom");
        }));
        assertTrue(service.runShutdownStep("later-step", () -> laterCleanupRan.set(true)));
        assertTrue(laterCleanupRan.get());

        service.markStopped();
        var snapshot = service.getSnapshot();
        assertEquals(CoreLifecycleState.STOPPED, snapshot.getState());
        assertEquals(CoreLifecyclePhase.COMPLETE, snapshot.getPhase());
        assertEquals(0L, snapshot.getStartupFailures());
        assertEquals(1L, snapshot.getShutdownFailures());
        assertEquals("failing-step", snapshot.getLastFailureComponent());
        assertEquals(IllegalStateException.class.getName(), snapshot.getLastFailureType());
        assertEquals("boom", snapshot.getLastFailureMessage());
    }

    @Test
    void recordsStartupFailureWithoutThrowingAwayDiagnostics() {
        DefaultCoreLifecycleService service =
                new DefaultCoreLifecycleService(Logger.getLogger("TestDefaultCoreLifecycleService"));

        service.beginStart();
        service.markStartupFailed("bootstrap", new LinkageError("missing dependency"));

        var snapshot = service.getSnapshot();
        assertEquals(CoreLifecycleState.FAILED, snapshot.getState());
        assertEquals(1L, snapshot.getStartupFailures());
        assertEquals("bootstrap", snapshot.getLastFailureComponent());
    }
}
