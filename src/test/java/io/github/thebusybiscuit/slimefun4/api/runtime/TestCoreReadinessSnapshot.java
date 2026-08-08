package io.github.thebusybiscuit.slimefun4.api.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.thebusybiscuit.slimefun4.api.lifecycle.CoreLifecycleState;
import java.util.List;
import org.junit.jupiter.api.Test;

class TestCoreReadinessSnapshot {

    @Test
    void preservesReadinessEvidenceImmutably() {
        var reasons = new java.util.ArrayList<String>();
        reasons.add("Storage runtime is not ready");
        var snapshot = new CoreReadinessSnapshot(
                CoreReadinessState.DEGRADED,
                CoreLifecycleState.RUNNING,
                true,
                true,
                false,
                true,
                0,
                2,
                reasons);
        reasons.clear();

        assertEquals(CoreReadinessState.DEGRADED, snapshot.getState());
        assertEquals(CoreLifecycleState.RUNNING, snapshot.getLifecycleState());
        assertTrue(snapshot.isRegistryFinalized());
        assertTrue(snapshot.isSchedulerAcceptingTasks());
        assertFalse(snapshot.isStorageReady());
        assertTrue(snapshot.isMachineRuntimeOperational());
        assertEquals(0, snapshot.getActiveMachineFailures());
        assertEquals(2, snapshot.getAddonFailureRecords());
        assertEquals(List.of("Storage runtime is not ready"), snapshot.getReasons());
    }
}
