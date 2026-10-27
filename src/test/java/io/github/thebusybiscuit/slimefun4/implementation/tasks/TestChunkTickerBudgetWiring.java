package io.github.thebusybiscuit.slimefun4.implementation.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.InventoryReadTestPlugin;
import io.github.bakedlibs.dough.blocks.ChunkPosition;
import io.github.thebusybiscuit.slimefun4.core.ticker.TickLocation;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * Exercises the core TickerTask window-selection wiring rather than testing
 * FairTickerBatch alone. All calls are selection-only: no machine inventories
 * or world-facing BlockTicker callbacks execute.
 */
class TestChunkTickerBudgetWiring {

    private ServerMock server;
    private InventoryReadTestPlugin fixture;
    private Method snapshotMethod;
    private Method selectMethod;

    @BeforeEach
    void startServer() throws Exception {
        server = MockBukkit.mock();
        fixture = new InventoryReadTestPlugin(server);
        snapshotMethod = TickerTask.class.getDeclaredMethod("snapshotTickingLocations");
        snapshotMethod.setAccessible(true);
        selectMethod = TickerTask.class.getDeclaredMethod(
                "selectTickersForChunk", ChunkPosition.class, Set.class);
        selectMethod.setAccessible(true);
        Slimefun.getCfg().setValue("stability.chunk-ticker-budget.enabled", false);
        Slimefun.getCfg().setValue("stability.chunk-ticker-budget.max-tickers-per-chunk-per-cycle", 1);
    }

    @AfterEach
    void closeServer() {
        try {
            if (fixture != null) {
                fixture.close();
            }
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void coreRespectsDisabledDefaultAndRoundRobinRebuildAcrossRegistrationChanges() throws Exception {
        World world = server.addSimpleWorld("world");
        TickerTask task = new TickerTask();
        ChunkPosition chunk = new ChunkPosition(world, 1, 1);
        Location first = new Location(world, 17, 64, 17);
        Location second = first.clone().add(1, 0, 0);
        Location third = first.clone().add(2, 0, 0);
        Location fourth = first.clone().add(3, 0, 0);

        task.enableTicker(first);
        task.enableTicker(second);
        task.enableTicker(third);

        Set<TickLocation> registered = locations(task, chunk);
        // When disabled, the original source set is returned unchanged.
        assertSame(registered, select(task, chunk, registered));

        Slimefun.getCfg().setValue("stability.chunk-ticker-budget.enabled", true);
        assertEquals(registered, collectCycles(task, chunk, registered, 3));

        // The actual coordinator refreshes its immutable per-chunk snapshot
        // whenever a machine enters or leaves. The cached fair selector must
        // not keep iterating stale registrations after such a refresh.
        task.enableTicker(fourth);
        registered = locations(task, chunk);
        assertEquals(4, registered.size());
        assertEquals(registered, collectCycles(task, chunk, registered, 4));

        task.disableTicker(second);
        registered = locations(task, chunk);
        assertEquals(3, registered.size());
        assertEquals(registered, collectCycles(task, chunk, registered, 3));

        Slimefun.getCfg().setValue("stability.chunk-ticker-budget.enabled", false);
        assertSame(registered, select(task, chunk, registered));
    }

    @Test
    void existingIndustrialChunkOf7454MachinesIsServedFairlyInFourCycles() throws Exception {
        World world = server.addSimpleWorld("dense-industrial");
        TickerTask task = new TickerTask();
        ChunkPosition chunk = new ChunkPosition(world, -333, -348);

        // Recreate the registration density in the reported industrial
        // hotspot, with a separate stable block position for every machine.
        // No actual block, inventory or addon ticker callback is created.
        for (int i = 0; i < 7_454; i++) {
            int x = -5328 + i % 16;
            int z = -5568 + (i / 16) % 16;
            int y = -64 + i / 256;
            task.enableTicker(new Location(world, x, y, z));
        }

        Set<TickLocation> registered = locations(task, chunk);
        assertEquals(7_454, registered.size(), "This fixture must represent unique industrial machines");
        Slimefun.getCfg().setValue("stability.chunk-ticker-budget.max-tickers-per-chunk-per-cycle", 2048);
        Slimefun.getCfg().setValue("stability.chunk-ticker-budget.enabled", true);

        Map<TickLocation, Integer> visits = new HashMap<>();
        for (int cycle = 0; cycle < 4; cycle++) {
            int selections = 0;
            for (TickLocation location : select(task, chunk, registered)) {
                visits.merge(location, 1, Integer::sum);
                selections++;
            }
            assertEquals(2048, selections, "A busy chunk cannot exceed its per-cycle callback quota");
        }

        assertEquals(7_454, visits.size(), "Every machine must receive at least one callback window");
        for (int served : visits.values()) {
            org.junit.jupiter.api.Assertions.assertTrue(
                    served == 1 || served == 2, "No machine may starve or run three times in four cycles");
        }

        // Turning the governor off immediately restores the unfiltered
        // registrations without modifying their inventories or identities.
        Slimefun.getCfg().setValue("stability.chunk-ticker-budget.enabled", false);
        assertSame(registered, select(task, chunk, registered));
    }

    private Set<TickLocation> collectCycles(
            TickerTask task, ChunkPosition chunk, Set<TickLocation> registered, int count)
            throws Exception {
        Set<TickLocation> served = new HashSet<>();
        for (int i = 0; i < count; i++) {
            Iterable<TickLocation> selection = select(task, chunk, registered);
            int selectedCount = 0;
            for (TickLocation location : selection) {
                selectedCount++;
                served.add(location);
            }
            assertEquals(1, selectedCount, "Quota one must dispatch one machine per Slimefun cycle");
        }
        return served;
    }

    @SuppressWarnings("unchecked")
    private Set<TickLocation> locations(TickerTask task, ChunkPosition chunk) throws Exception {
        Set<Map.Entry<ChunkPosition, Set<TickLocation>>> snapshot =
                (Set<Map.Entry<ChunkPosition, Set<TickLocation>>>) snapshotMethod.invoke(task);
        return snapshot.stream()
                .filter(entry -> entry.getKey().equals(chunk))
                .findFirst()
                .orElseThrow()
                .getValue();
    }

    @SuppressWarnings("unchecked")
    private Iterable<TickLocation> select(
            TickerTask task, ChunkPosition chunk, Set<TickLocation> locations) throws Exception {
        return (Iterable<TickLocation>) selectMethod.invoke(task, chunk, locations);
    }
}
