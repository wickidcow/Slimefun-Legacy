package io.github.thebusybiscuit.slimefun4.implementation.tasks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.ASlimefunDataContainer;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.InventoryReadTestPlugin;
import io.github.bakedlibs.dough.blocks.BlockPosition;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.services.profiler.SlimefunProfiler;
import io.github.thebusybiscuit.slimefun4.core.services.scheduling.SlimefunScheduler;
import io.github.thebusybiscuit.slimefun4.core.services.scheduling.TaskHandle;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Exercises the real ticker and profiler with controlled scheduler acceptance and callback ordering. */
class TestTickerTaskDispatch {

    private static final String ID = "TICK_DISPATCH_FIXTURE";
    private final List<Runnable> callbacks = new ArrayList<>();
    private final AtomicInteger ticks = new AtomicInteger();
    private final AtomicInteger tickerLookups = new AtomicInteger();
    private InventoryReadTestPlugin fixture;
    private TickerTask task;
    private Location location;
    private BlockPosition position;
    private TestData data;
    private SlimefunProfiler profiler;
    private Set<BlockPosition> queued;
    private AtomicInteger profilerQueued;
    private Method tickData;
    private Function<Runnable, TaskHandle> dispatch;
    private boolean synchronizedTicker = true;

    @BeforeEach
    void setUp() throws Exception {
        var server = MockBukkit.mock();
        fixture = new InventoryReadTestPlugin(server);
        location = new Location(server.addSimpleWorld("world"), 17, 64, 17);
        position = new BlockPosition(location);
        task = new TickerTask();
        data = new TestData();
        profiler = Slimefun.getProfiler();
        queued = field(TickerTask.class, task, "queuedSynchronousTicks");
        profilerQueued = field(SlimefunProfiler.class, profiler, "telemetryQueued");
        tickData = TickerTask.class.getDeclaredMethod(
                "tickData",
                Set.class,
                Location.class,
                BlockPosition.class,
                ASlimefunDataContainer.class,
                boolean.class);
        tickData.setAccessible(true);
        Slimefun.getRegistry().getSlimefunItemIds().put(ID, new TestItem());
        SlimefunScheduler scheduler = (SlimefunScheduler) Proxy.newProxyInstance(
                SlimefunScheduler.class.getClassLoader(),
                new Class<?>[] {SlimefunScheduler.class},
                (proxy, method, args) -> {
                    if (!method.getName().equals("runAt")) {
                        throw new AssertionError("Unexpected scheduler call: " + method.getName());
                    }
                    assertEquals(location, args[0]);
                    Runnable callback = (Runnable) args[1];
                    callbacks.add(callback);
                    return dispatch.apply(callback);
                });
        Field schedulerField = Slimefun.class.getDeclaredField("schedulerService");
        schedulerField.setAccessible(true);
        schedulerField.set(Slimefun.instance(), scheduler);
    }

    @AfterEach
    void tearDown() {
        try {
            if (profiler != null) profiler.kill();
            if (fixture != null) fixture.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejectedTickReleasesQueueWithOrWithoutProfiling(boolean profiling) throws Exception {
        if (profiling) {
            profiler.startTelemetry();
            profiler.scheduleEntries(1); // Another machine's entry must survive cleanup.
        }
        dispatch = callback -> new Handle(true);

        tick();

        assertFalse(queued.contains(position));
        assertEquals(profiling ? 1 : 0, profilerQueued.get());
        assertEquals(0, ticks.get());
        assertEquals(0, task.getFailingMachineCount());
        assertEquals("retained", data.getData("sentinel"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void eachMachineResolvesItsTickerOnceEvenWhenDispatched(boolean synchronizedMachine) throws Exception {
        synchronizedTicker = synchronizedMachine;
        dispatch = callback -> new Handle(false);

        tick();
        if (synchronizedMachine) {
            assertEquals(1, callbacks.size());
            callbacks.getFirst().run();
        }

        assertEquals(1, ticks.get());
        assertEquals(1, tickerLookups.get(),
                "Resolve the handler once and use that same instance for update and execution");
        assertFalse(queued.contains(position));
    }

    @Test
    void rejectedViewedInventoryTickAlsoReleasesQueue() throws Exception {
        synchronizedTicker = false;
        task.setInventoryViewed(location, true);
        dispatch = callback -> new Handle(true);

        tick();

        assertFalse(queued.contains(position));
        assertEquals(0, ticks.get());
        assertTrue(task.isInventoryViewed(location));
    }

    @Test
    void lateRejectedCallbackCannotRunOrClearTheNextTick() throws Exception {
        profiler.startTelemetry();
        dispatch = callback -> new Handle(true);
        tick();
        dispatch = callback -> new Handle(false);
        tick();

        assertEquals(2, callbacks.size());
        assertTrue(queued.contains(position));
        assertEquals(1, profilerQueued.get());
        callbacks.getFirst().run();
        assertTrue(queued.contains(position));
        assertEquals(1, profilerQueued.get());
        assertEquals(0, ticks.get());

        callbacks.getLast().run();
        assertFalse(queued.contains(position));
        assertEquals(0, profilerQueued.get());
        assertEquals(1, ticks.get());
    }

    @Test
    void acceptedTickSuppressesDuplicatesAndCompletesOnce() throws Exception {
        profiler.startTelemetry();
        dispatch = callback -> new Handle(false);
        tick();
        tick();

        assertEquals(1, callbacks.size());
        assertTrue(queued.contains(position));
        assertEquals(1, profilerQueued.get());
        callbacks.getFirst().run();
        callbacks.getFirst().run();
        assertFalse(queued.contains(position));
        assertEquals(0, profilerQueued.get());
        assertEquals(1, ticks.get());
    }

    @Test
    void completedCallbackAndCancelledHandleDoNotCancelAnotherProfilerEntry() throws Exception {
        profiler.startTelemetry();
        profiler.scheduleEntries(1);
        dispatch = callback -> {
            callback.run();
            return new Handle(true);
        };

        tick();

        assertFalse(queued.contains(position));
        assertEquals(1, profilerQueued.get());
        assertEquals(1, ticks.get());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void schedulingFailureCleansUpOnlyWhenCallbackHasNotClaimedTheTick(boolean completed) throws Exception {
        profiler.startTelemetry();
        profiler.scheduleEntries(1);
        Map<BlockPosition, Integer> bugs = field(TickerTask.class, task, "bugs");
        dispatch = callback -> {
            if (completed) callback.run();
            // Use the existing rate-limited diagnostic path, avoiding a full on-disk error report.
            bugs.put(position, 1);
            throw new IllegalStateException("Injected scheduling failure");
        };

        tick();
        callbacks.getFirst().run();

        assertFalse(queued.contains(position));
        assertEquals(1, profilerQueued.get());
        assertEquals(completed ? 1 : 0, ticks.get());
        assertEquals(1, task.getFailingMachineCount());
    }

    @Test
    void runningCallbackRetainsQueueAndProfilerOwnershipWhenHandleIsCancelled() throws Exception {
        profiler.startTelemetry();
        profiler.scheduleEntries(1);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        data.beforeRead = () -> {
            started.countDown();
            await(finish);
        };
        ExecutorService worker = Executors.newSingleThreadExecutor();
        List<Future<?>> runs = new ArrayList<>();
        dispatch = callback -> {
            runs.add(worker.submit(callback));
            await(started);
            return new Handle(true);
        };

        try {
            tick();
            assertTrue(queued.contains(position));
            assertEquals(2, profilerQueued.get());
            tick();
            assertEquals(1, callbacks.size());
            data.setPendingRemove(true);
            finish.countDown();
            runs.getFirst().get(5, TimeUnit.SECONDS);
            assertFalse(queued.contains(position));
            assertEquals(1, profilerQueued.get());
            assertEquals(0, ticks.get());
        } finally {
            finish.countDown();
            worker.shutdownNow();
            assertTrue(worker.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void acceptedTickStillCleansUpWhenMachineIsPausedOrRemoved(boolean removed) throws Exception {
        profiler.startTelemetry();
        dispatch = callback -> new Handle(false);
        tick();
        if (removed) {
            data.setPendingRemove(true);
        } else {
            task.pauseMachineTicker(location);
        }

        callbacks.getFirst().run();

        assertFalse(queued.contains(position));
        assertEquals(0, profilerQueued.get());
        assertEquals(0, ticks.get());
    }

    private void tick() throws Exception {
        tickData.invoke(task, new HashSet<BlockTicker>(), location, position, data, false);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Class<?> type, Object target, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(target);
    }

    private final class TestItem extends SlimefunItem {
        private final BlockTicker ticker = new BlockTicker() {
            @Override
            public boolean isSynchronized() {
                return synchronizedTicker;
            }

            @Override
            public void tick(Block block, SlimefunItem item, ASlimefunDataContainer container) {
                assertEquals(data, container);
                ticks.incrementAndGet();
            }
        };

        private TestItem() {
            super(
                    new ItemGroup(
                            new NamespacedKey("slimefun", "tick_dispatch_fixture"), new ItemStack(Material.CHEST)),
                    new SlimefunItemStack(ID, Material.CHEST, "Dispatch fixture"),
                    RecipeType.ENHANCED_CRAFTING_TABLE,
                    new ItemStack[9]);
        }

        @Override
        public BlockTicker getBlockTicker() {
            tickerLookups.incrementAndGet();
            return ticker;
        }

        @Override
        public boolean isDisabledIn(World world) {
            return false;
        }
    }

    private static final class TestData extends ASlimefunDataContainer {
        private Runnable beforeRead = () -> {};

        private TestData() {
            super("dispatch-fixture", ID);
            setIsDataLoaded(true);
            setCacheInternal("sentinel", "retained", true);
        }

        @Override
        public boolean isDataLoaded() {
            beforeRead.run();
            return super.isDataLoaded();
        }

        @Override
        public void scheduleUpdateData(String key) {
            throw new AssertionError("Tick dispatch must not write stored data");
        }
    }

    private static final class Handle implements TaskHandle {
        private boolean cancelled;

        private Handle(boolean cancelled) {
            this.cancelled = cancelled;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }
}
