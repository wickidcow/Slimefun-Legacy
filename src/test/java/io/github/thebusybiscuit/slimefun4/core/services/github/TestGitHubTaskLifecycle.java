package io.github.thebusybiscuit.slimefun4.core.services.github;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.InventoryReadTestPlugin;
import io.github.bakedlibs.dough.config.Config;
import io.github.bakedlibs.dough.skins.CustomGameProfile;
import io.github.bakedlibs.dough.skins.PlayerSkin;
import io.github.thebusybiscuit.slimefun4.core.services.lifecycle.DefaultCoreLifecycleService;
import io.github.thebusybiscuit.slimefun4.core.services.scheduling.SlimefunScheduler;
import io.github.thebusybiscuit.slimefun4.core.services.scheduling.TaskHandle;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/** Runs the real contributor worker against controlled profile futures, never Mojang/GitHub endpoints. */
class TestGitHubTaskLifecycle {

    private static final UUID PLAYER_ID = UUID.fromString("54de7d90-4d1e-41c8-941a-fde8f7f8a999");
    private static final String PLAYER_NAME = "FixturePlayer";
    private static final byte[] ORIGINAL_CACHE = "fixture-sentinel: preserved\n".getBytes(StandardCharsets.UTF_8);
    private final List<InventoryReadTestPlugin> fixtures = new ArrayList<>();
    private final List<DelayedTask> delayed = new ArrayList<>();
    private final List<LogRecord> logs = new ArrayList<>();

    @TempDir
    Path directory;

    private ServerMock server;
    private Slimefun plugin;
    private RecordingService github;
    private Config config;
    private Contributor contributor;
    private ControlledTask task;
    private ExecutorService workers;
    private Path uuidCache;
    private Path skinCache;
    private Logger logger;
    private Handler handler;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        plugin = createPlugin();
        workers = Executors.newFixedThreadPool(2);
        SlimefunScheduler scheduler = (SlimefunScheduler) Proxy.newProxyInstance(
                SlimefunScheduler.class.getClassLoader(),
                new Class<?>[] {SlimefunScheduler.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isAcceptingTasks" -> true;
                    case "isFolia" -> false;
                    // Never execute startup network tasks: only the explicitly controlled worker runs below.
                    case "runAsync", "runAsyncAtFixedRate" -> new CapturedHandle();
                    case "runAsyncLater" -> {
                        CapturedHandle handle = new CapturedHandle();
                        delayed.add(new DelayedTask((Runnable) args[0], (long) args[1], handle));
                        yield handle;
                    }
                    default -> throw new AssertionError("Unexpected scheduler call: " + method.getName());
                });
        setField(Slimefun.class, plugin, "schedulerService", scheduler);
        config = Slimefun.getCfg();
        config.setValue("guide.contributor-heads.resolve-online", true);
        config.setValue("guide.contributor-heads.lookup-timeout-seconds", 30);
        config.setValue("guide.contributor-heads.blocked-names", List.of());
        github = new RecordingService();
        uuidCache = directory.resolve("uuids.yml");
        skinCache = directory.resolve("skins.yml");
        Files.write(uuidCache, ORIGINAL_CACHE);
        Files.write(skinCache, ORIGINAL_CACHE);
        setField(GitHubService.class, github, "uuidCache", new Config(uuidCache.toFile()));
        setField(GitHubService.class, github, "texturesCache", new Config(skinCache.toFile()));
        github.start(plugin);
        // Both services are actual implementations; clear real connector inputs so no endpoint can be contacted.
        github.getConnectors().clear();
        github.getContributors().clear();
        contributor = github.addContributor(PLAYER_NAME, "&aFixture", 1);
        task = new ControlledTask(github);
        logger = plugin.getLogger();
        handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                logs.add(record);
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        logger.addHandler(handler);
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            if (task != null) {
                task.uuid.completeExceptionally(new IOException("Fixture teardown"));
                task.skin.completeExceptionally(new IOException("Fixture teardown"));
            }
            if (workers != null) {
                workers.shutdownNow();
                assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS), "Controlled profile workers must terminate");
            }
        } finally {
            if (logger != null && handler != null) {
                logger.removeHandler(handler);
            }
            try {
                for (int i = fixtures.size() - 1; i >= 0; i--) {
                    fixtures.get(i).close();
                }
            } finally {
                MockBukkit.unmock();
            }
        }
    }

    @Test
    void activeUuidAndSkinResultsStillUpdateContributorAndRealCaches() throws Exception {
        PlayerSkin skin = syntheticSkin();
        String expectedTexture = CustomGameProfile.getBase64Texture(skin.getProfile());
        task.uuid.complete(PLAYER_ID);
        task.skin.complete(skin);

        workers.submit(task).get(5, TimeUnit.SECONDS);

        assertEquals(1, task.uuidRequests.get());
        assertEquals(1, task.skinRequests.get());
        assertEquals(PLAYER_ID, contributor.getUniqueId().orElseThrow());
        assertTrue(contributor.hasTexture());
        assertEquals(expectedTexture, contributor.getTexture(github));
        assertEquals(1, github.cacheSaves.get());
        assertEquals(
                PLAYER_ID.toString(),
                YamlConfiguration.loadConfiguration(uuidCache.toFile()).getString(PLAYER_NAME));
        assertEquals(
                expectedTexture,
                YamlConfiguration.loadConfiguration(skinCache.toFile()).getString(PLAYER_NAME));
        assertTrue(delayed.isEmpty());
        assertTrue(logs.isEmpty());
    }

    @Test
    void disabledOnlineLookupOptionStillAvoidsProfileRequests() throws Exception {
        config.setValue("guide.contributor-heads.resolve-online", false);

        workers.submit(task).get(5, TimeUnit.SECONDS);

        assertEquals(0, task.uuidRequests.get());
        assertEquals(0, task.skinRequests.get());
        assertTrue(contributor.getUniqueId().isEmpty());
        assertFalse(contributor.hasTexture());
    }

    @Test
    void configuredBlockedNamesStillAvoidProfileRequests() throws Exception {
        config.setValue("guide.contributor-heads.blocked-names", List.of(" fixtureplayer "));

        workers.submit(task).get(5, TimeUnit.SECONDS);

        assertEquals(0, task.uuidRequests.get());
        assertEquals(0, task.skinRequests.get());
        assertTrue(contributor.getUniqueId().isEmpty());
        assertFalse(contributor.hasTexture());
    }

    @ParameterizedTest
    @EnumSource(Boundary.class)
    void pendingUuidResultCannotPublishOrStartSkinLookupAfterItsOwnerEnds(Boundary boundary) throws Exception {
        Future<?> run = workers.submit(task);
        assertTrue(task.uuidStarted.await(5, TimeUnit.SECONDS));

        try {
            applyBoundary(boundary);
            assertFalse(run.isDone(), "Profile completion remains controlled after the lifecycle boundary");
        } finally {
            task.uuid.complete(PLAYER_ID);
        }
        run.get(5, TimeUnit.SECONDS);

        assertTrue(contributor.getUniqueId().isEmpty());
        assertFalse(contributor.hasTexture());
        assertEquals(1, task.uuidRequests.get());
        assertEquals(0, task.skinRequests.get());
        assertNoLateEffects();
    }

    @ParameterizedTest
    @EnumSource(Boundary.class)
    void pendingSkinResultCannotPublishAfterItsOwnerEnds(Boundary boundary) throws Exception {
        contributor.setUniqueId(PLAYER_ID);
        PlayerSkin skin = syntheticSkin();
        Future<?> run = workers.submit(task);
        assertTrue(task.skinStarted.await(5, TimeUnit.SECONDS));

        try {
            applyBoundary(boundary);
            assertFalse(run.isDone(), "Profile completion remains controlled after the lifecycle boundary");
        } finally {
            task.skin.complete(skin);
        }
        run.get(5, TimeUnit.SECONDS);

        assertEquals(PLAYER_ID, contributor.getUniqueId().orElseThrow());
        assertFalse(contributor.hasTexture());
        assertEquals(0, task.uuidRequests.get());
        assertEquals(1, task.skinRequests.get());
        assertNoLateEffects();
    }

    @Test
    void lateRateLimitFailureDoesNotLogRetryOrSaveCache() throws Exception {
        Future<?> run = workers.submit(task);
        assertTrue(task.uuidStarted.await(5, TimeUnit.SECONDS));
        try {
            workers.submit(github::shutdown).get(5, TimeUnit.SECONDS);
        } finally {
            task.uuid.completeExceptionally(new IOException("429 controlled rate limit"));
        }
        run.get(5, TimeUnit.SECONDS);

        assertNoLateEffects();
    }

    @Test
    void activeRateLimitRetryIsOwnedAndCannotExecuteAfterShutdown() throws Exception {
        task.uuid.completeExceptionally(new IOException("429 controlled rate limit"));

        workers.submit(task).get(5, TimeUnit.SECONDS);

        assertEquals(1, delayed.size());
        DelayedTask retry = delayed.getFirst();
        assertEquals(5L * 60L * 20L, retry.delayTicks());
        assertFalse(retry.handle().isCancelled());
        assertEquals(1, logs.size());
        int requestsBeforeStop = task.uuidRequests.get();
        int savesBeforeStop = github.cacheSaves.get();
        github.shutdown();

        assertTrue(retry.handle().isCancelled());
        workers.submit(retry.callback()).get(5, TimeUnit.SECONDS);

        assertEquals(requestsBeforeStop, task.uuidRequests.get());
        assertEquals(savesBeforeStop, github.cacheSaves.get());
        assertEquals(1, delayed.size());
        assertEquals(1, logs.size());
    }

    @Test
    void interruptedUuidWaitPreservesInterruptWithoutPublishingOrRetrying() throws Exception {
        Future<Boolean> run = workers.submit(() -> {
            try {
                task.run();
                return Thread.currentThread().isInterrupted();
            } finally {
                Thread.interrupted();
            }
        });
        assertTrue(task.uuidStarted.await(5, TimeUnit.SECONDS));

        task.lookupThread.interrupt();

        assertTrue(run.get(5, TimeUnit.SECONDS));
        assertFalse(task.uuid.isDone(), "Discarding a result does not imply its underlying transport was cancelled");
        assertTrue(contributor.getUniqueId().isEmpty());
        assertFalse(contributor.hasTexture());
        assertEquals(0, task.skinRequests.get());
        assertNoLateEffects();
    }

    @Test
    void alreadyStoppedWorkerDoesNotStartProfileLookupOrSaveCache() throws Exception {
        github.shutdown();

        workers.submit(task).get(5, TimeUnit.SECONDS);

        assertEquals(0, task.uuidRequests.get());
        assertEquals(0, task.skinRequests.get());
        assertNoLateEffects();
    }

    private void applyBoundary(Boundary boundary) throws Exception {
        if (boundary == Boundary.SHUTDOWN) {
            // If the profile wait holds the publication lock, this bounded deadlock guard fails.
            workers.submit(github::shutdown).get(5, TimeUnit.SECONDS);
        } else {
            createPlugin();
        }
    }

    private Slimefun createPlugin() throws Exception {
        fixtures.add(new InventoryReadTestPlugin(server));
        Slimefun created = Slimefun.instance();
        setField(JavaPlugin.class, created, "isEnabled", true);
        DefaultCoreLifecycleService lifecycle = (DefaultCoreLifecycleService) Slimefun.getCoreLifecycleService();
        lifecycle.beginStart();
        lifecycle.markRunning();
        return created;
    }

    private static PlayerSkin syntheticSkin() {
        // Dough's fromURL only constructs the encoded profile; it does not open the supplied URL.
        return PlayerSkin.fromURL(PLAYER_ID, "https://textures.minecraft.net/texture/github-lifecycle-fixture");
    }

    private void assertNoLateEffects() throws IOException {
        assertEquals(0, github.cacheSaves.get());
        assertArrayEquals(ORIGINAL_CACHE, Files.readAllBytes(uuidCache));
        assertArrayEquals(ORIGINAL_CACHE, Files.readAllBytes(skinCache));
        assertTrue(delayed.isEmpty());
        assertTrue(logs.isEmpty());
    }

    private static void setField(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private enum Boundary {
        SHUTDOWN,
        REPLACEMENT
    }

    private static final class RecordingService extends GitHubService {

        private final AtomicInteger cacheSaves = new AtomicInteger();

        private RecordingService() {
            super("wickidcow/Slimefun-Legacy");
        }

        @Override
        protected void saveCache() {
            cacheSaves.incrementAndGet();
            super.saveCache();
        }
    }

    private static final class ControlledTask extends GitHubTask {

        private final CompletableFuture<UUID> uuid = new CompletableFuture<>();
        private final CompletableFuture<PlayerSkin> skin = new CompletableFuture<>();
        private final CountDownLatch uuidStarted = new CountDownLatch(1);
        private final CountDownLatch skinStarted = new CountDownLatch(1);
        private final AtomicInteger uuidRequests = new AtomicInteger();
        private final AtomicInteger skinRequests = new AtomicInteger();
        private volatile Thread lookupThread;

        private ControlledTask(GitHubService service) {
            super(service);
        }

        @Override
        CompletableFuture<UUID> lookupUuid(String name) {
            assertEquals(PLAYER_NAME, name);
            uuidRequests.incrementAndGet();
            lookupThread = Thread.currentThread();
            uuidStarted.countDown();
            return uuid;
        }

        @Override
        CompletableFuture<PlayerSkin> lookupSkin(UUID playerId) {
            assertEquals(PLAYER_ID, playerId);
            skinRequests.incrementAndGet();
            lookupThread = Thread.currentThread();
            skinStarted.countDown();
            return skin;
        }
    }

    private record DelayedTask(Runnable callback, long delayTicks, CapturedHandle handle) {}

    private static final class CapturedHandle implements TaskHandle {

        private boolean cancelled;

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
