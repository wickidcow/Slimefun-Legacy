package io.github.thebusybiscuit.slimefun4.implementation.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.bakedlibs.dough.blocks.BlockPosition;
import io.github.thebusybiscuit.slimefun4.core.ticker.TickLocation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class TestTickerTaskRegistrationDedup {

    private ServerMock server;
    private World world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void repeatedRegistrationOfSameMachineHasOnlyOneTickLocation() {
        TickerTask task = new TickerTask();
        Location at = new Location(world, -5314, -57, -5569);

        for (int i = 0; i < 500; i++) {
            task.enableTicker(at.clone());
        }

        assertEquals(1, task.getTickLocations(at.getChunk()).size(),
                "Re-registering one machine must not multiply its processing callbacks");
        assertEquals(1, task.getLocations(at.getChunk()).size());
        assertEquals(1, task.getTickLocations().size());
    }

    @Test
    void repeatUniversalRegistrationPreservesUuidAndDoesNotDuplicate() {
        TickerTask task = new TickerTask();
        Location at = new Location(world, -5314, -57, -5569);
        UUID uuid = UUID.randomUUID();

        task.enableTicker(at, uuid);
        task.enableTicker(at.clone(), uuid);
        task.enableTicker(at.clone(), uuid);

        assertEquals(1, task.getTickLocations(at.getChunk()).size());
        TickLocation only = task.getTickLocations(at.getChunk()).iterator().next();
        assertEquals(uuid, only.getUuid());
    }

    @Test
    void distinctMachinesAndUniversalIdentitiesRemainDistinct() {
        Location at = new Location(world, 17, 64, 17);
        Location next = new Location(world, 18, 64, 17);
        UUID uuidA = UUID.randomUUID();
        UUID uuidB = UUID.randomUUID();

        TickLocation normalA = new TickLocation(new BlockPosition(at));
        TickLocation normalACopy = new TickLocation(new BlockPosition(at.clone()));
        TickLocation normalB = new TickLocation(new BlockPosition(next));
        TickLocation universalA = new TickLocation(new BlockPosition(at), uuidA);
        TickLocation universalACopy = new TickLocation(new BlockPosition(at.clone()), uuidA);
        TickLocation universalB = new TickLocation(new BlockPosition(at.clone()), uuidB);

        assertEquals(normalA, normalACopy);
        assertEquals(normalA.hashCode(), normalACopy.hashCode());
        assertEquals(universalA, universalACopy);
        assertEquals(universalA.hashCode(), universalACopy.hashCode());
        assertNotEquals(normalA, normalB);
        assertNotEquals(normalA, universalA);
        assertNotEquals(universalA, universalB);

        Set<TickLocation> set = new HashSet<>();
        for (TickLocation registration : new TickLocation[] {
                normalA, normalACopy, normalB, universalA, universalACopy, universalB
        }) {
            set.add(registration);
        }
        assertEquals(4, set.size());
    }

    @Test
    void duplicateRegistrationDoesNotRebuildExpensiveTickerSnapshot() throws Exception {
        TickerTask task = new TickerTask();
        Location at = new Location(world, 17, 64, 17);
        task.enableTicker(at);

        Method snapshot = TickerTask.class.getDeclaredMethod("snapshotTickingLocations");
        snapshot.setAccessible(true);
        Field dirty = TickerTask.class.getDeclaredField("tickingLocationsSnapshotDirty");
        dirty.setAccessible(true);

        Object first = snapshot.invoke(task);
        assertFalse(dirty.getBoolean(task));

        for (int i = 0; i < 30; i++) {
            task.enableTicker(at.clone());
        }
        assertFalse(dirty.getBoolean(task), "An idempotent registration must not schedule a snapshot rebuild");
        assertSame(first, snapshot.invoke(task));

        task.enableTicker(new Location(world, 18, 64, 17));
        assertTrue(dirty.getBoolean(task));
        assertNotSame(first, snapshot.invoke(task));
    }

    @Test
    void blockCenterAndRotationStillRemoveTheOriginalTicker() {
        TickerTask task = new TickerTask();
        Location original = new Location(world, -5314, -57, -5569);
        task.enableTicker(original);

        // Bukkit Location.equals compares exact doubles/yaw/pitch. A block
        // registry must instead use the integer coordinates it owns.
        Location centerWithRotation = new Location(
                world, -5313.5, -56.5, -5568.5, 139.0F, -20.0F);
        task.disableTicker(centerWithRotation);

        assertTrue(task.getTickLocations().isEmpty(),
                "A different yaw or center offset cannot leave a ghost machine ticker");
    }

    @Test
    void unknownRemovalsDoNotRebuildSnapshotsButRealRemovalsDo() throws Exception {
        TickerTask task = new TickerTask();
        Location existing = new Location(world, -5314, -57, -5569);
        task.enableTicker(existing);

        Method snapshot = TickerTask.class.getDeclaredMethod("snapshotTickingLocations");
        snapshot.setAccessible(true);
        Field dirty = TickerTask.class.getDeclaredField("tickingLocationsSnapshotDirty");
        dirty.setAccessible(true);

        Object first = snapshot.invoke(task);
        assertFalse(dirty.getBoolean(task));

        task.disableTicker(new Location(world, 800, 60, 800));
        task.disableTicker(UUID.randomUUID());
        assertFalse(dirty.getBoolean(task), "Absent blocks or UUIDs must not dirty global ticker state");
        assertSame(first, snapshot.invoke(task));

        task.disableTicker(existing);
        assertTrue(dirty.getBoolean(task), "Removing a real ticker must invalidate the cached snapshot");
        assertNotSame(first, snapshot.invoke(task));
        assertTrue(task.getTickLocations().isEmpty());
    }

    @Test
    void unregisterThenRegisterDoesNotLeaveStaleDuplicates() {
        TickerTask task = new TickerTask();
        Location at = new Location(world, 17, 64, 17);
        task.enableTicker(at);
        task.enableTicker(at);
        task.disableTicker(at);
        assertTrue(task.getTickLocations().isEmpty());

        task.enableTicker(at);
        task.enableTicker(at);
        assertEquals(1, task.getTickLocations(at.getChunk()).size());
    }
}
