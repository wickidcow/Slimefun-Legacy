package io.github.thebusybiscuit.slimefun4.implementation.tasks;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TestTickerTaskSnapshotCache {

    @Test
    void reusesCleanSnapshotAndInvalidatesRegistrationChanges() throws Exception {
        TickerTask task = new TickerTask();

        Method snapshotMethod = TickerTask.class.getDeclaredMethod("snapshotTickingLocations");
        snapshotMethod.setAccessible(true);
        Field dirtyField = TickerTask.class.getDeclaredField("tickingLocationsSnapshotDirty");
        dirtyField.setAccessible(true);

        assertTrue(dirtyField.getBoolean(task));

        Object first = snapshotMethod.invoke(task);
        assertFalse(dirtyField.getBoolean(task));
        assertSame(first, snapshotMethod.invoke(task));

        task.disableTicker(UUID.randomUUID());
        assertTrue(dirtyField.getBoolean(task));

        Object rebuilt = snapshotMethod.invoke(task);
        assertNotSame(first, rebuilt);
        assertFalse(dirtyField.getBoolean(task));
        assertSame(rebuilt, snapshotMethod.invoke(task));
    }
}
