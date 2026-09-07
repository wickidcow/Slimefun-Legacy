package io.github.thebusybiscuit.slimefun4.implementation.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.bakedlibs.dough.blocks.BlockPosition;
import io.github.thebusybiscuit.slimefun4.core.services.stability.MachineCircuitBreaker;
import io.github.thebusybiscuit.slimefun4.core.services.stability.MachineFailureTracker;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class TestTickerTaskRemovalCleanup {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void locationRemovalClearsFailureDiagnostics() throws Exception {
        TickerTask task = new TickerTask();
        World world = server.addSimpleWorld("world");
        Location location = new Location(world, 17, 64, 17);
        BlockPosition position = new BlockPosition(location);

        task.enableTicker(location);
        seedFailureTracker(task, position);

        assertEquals(1, task.getFailingMachineCount());
        assertFalse(task.getTickLocations().isEmpty());

        task.disableTicker(location);

        assertEquals(0, task.getFailingMachineCount());
        assertTrue(task.getTickLocations().isEmpty());
    }

    @Test
    void uuidRemovalClearsRuntimeStateAndEmptyChunkRegistration() throws Exception {
        TickerTask task = new TickerTask();
        World world = server.addSimpleWorld("world");
        Location location = new Location(world, 17, 64, 17);
        UUID uuid = UUID.randomUUID();
        BlockPosition position = new BlockPosition(location);

        task.enableTicker(location, uuid);
        task.pauseMachineTicker(location);
        task.setInventoryViewed(location, true);

        Map<BlockPosition, Integer> bugs = field(task, "bugs");
        bugs.put(position, 2);

        Set<BlockPosition> queuedSynchronousTicks = field(task, "queuedSynchronousTicks");
        queuedSynchronousTicks.add(position);

        MachineCircuitBreaker<BlockPosition> circuitBreaker = field(task, "circuitBreaker");
        circuitBreaker.open(position, Long.MAX_VALUE);
        seedFailureTracker(task, position);

        assertFalse(task.getTickLocations().isEmpty());
        assertEquals(1, task.getTargetedPausedMachineCount());
        assertEquals(1, task.getPausedMachineCount());
        assertEquals(1, task.getFailingMachineCount());
        assertTrue(task.isInventoryViewed(location));

        task.disableTicker(uuid);

        assertTrue(task.getTickLocations().isEmpty());
        assertEquals(0, task.getTargetedPausedMachineCount());
        assertEquals(0, task.getPausedMachineCount());
        assertEquals(0, task.getFailingMachineCount());
        assertFalse(task.isInventoryViewed(location));
        assertTrue(bugs.isEmpty());
        assertTrue(queuedSynchronousTicks.isEmpty());
    }

    private static void seedFailureTracker(TickerTask task, BlockPosition position) throws Exception {
        MachineFailureTracker<BlockPosition> tracker = field(task, "failureTracker");
        Map<BlockPosition, Object> active = field(tracker, "active");
        active.put(position, new Object());
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(target);
    }
}
