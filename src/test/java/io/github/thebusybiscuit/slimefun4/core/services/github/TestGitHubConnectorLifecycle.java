package io.github.thebusybiscuit.slimefun4.core.services.github;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.InventoryReadTestPlugin;
import io.github.thebusybiscuit.slimefun4.core.services.lifecycle.DefaultCoreLifecycleService;
import io.github.thebusybiscuit.slimefun4.core.services.scheduling.SlimefunScheduler;
import io.github.thebusybiscuit.slimefun4.core.services.scheduling.TaskHandle;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLSession;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.MessageTarget;

/** Exercises the actual download/cache branches using local transport responses and isolated cache files. */
class TestGitHubConnectorLifecycle {

    private static final String REPOSITORY = "wickidcow/Slimefun-Legacy";
    private static final String RELEASE_JSON =
            "{\"tag_name\":\"v4.1.63\",\"html_url\":\"https://github.com/wickidcow/Slimefun-Legacy/releases/tag/v4.1.63\"}";
    private final List<InventoryReadTestPlugin> fixtures = new ArrayList<>();
    private final List<PendingTransport> pending = new ArrayList<>();

    @TempDir
    Path directory;

    private ServerMock server;
    private GitHubService github;
    private ExecutorService workers;
    private Path cache;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        github = createService();
        workers = Executors.newFixedThreadPool(2);
        cache = directory.resolve("latest-release.json");
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            pending.forEach(transport -> transport.complete.countDown());
            if (workers != null) {
                workers.shutdownNow();
                assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS), "Controlled workers must terminate");
            }
        } finally {
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
    void activeSuccessfulResponsePublishesThroughTheRealReleaseConnectorAndWritesCache() throws Exception {
        ControlledConnector connector = connector(Outcome.SUCCESS);

        connector.download();

        assertEquals(1, connector.requests.get());
        assertEquals(1, connector.successes.get());
        assertEquals(0, connector.failures.get());
        assertEquals("v4.1.63", github.getLatestReleaseTag().orElseThrow());
        assertEquals(JsonParser.parseString(RELEASE_JSON), JsonParser.parseString(Files.readString(cache)));
        assertConsoleNotice();
    }

    @ParameterizedTest
    @EnumSource(
            value = Outcome.class,
            names = {"UNAVAILABLE", "IO_FAILURE", "INVALID_JSON"})
    void activeFetchFailureStillUsesValidCachedReleaseWithoutRewritingIt(Outcome outcome) throws Exception {
        byte[] original = seedCache();
        ControlledConnector connector = connector(outcome);

        connector.download();

        assertEquals(1, connector.successes.get());
        assertEquals(0, connector.failures.get());
        assertEquals("v4.1.63", github.getLatestReleaseTag().orElseThrow());
        assertArrayEquals(original, Files.readAllBytes(cache));
        assertConsoleNotice();
    }

    @Test
    void activeIOExceptionWithoutCacheStillInvokesFailureCallback() {
        ControlledConnector connector = connector(Outcome.IO_FAILURE);

        connector.download();

        assertEquals(0, connector.successes.get());
        assertEquals(1, connector.failures.get());
        assertTrue(github.getLatestReleaseTag().isEmpty());
        assertFalse(Files.exists(cache));
    }

    @Test
    void nonSuccessStatusWithoutCacheKeepsItsExistingNoFailureCallbackBehavior() {
        ControlledConnector connector = connector(Outcome.UNAVAILABLE);

        connector.download();

        assertEquals(0, connector.successes.get());
        assertEquals(0, connector.failures.get());
        assertTrue(github.getLatestReleaseTag().isEmpty());
        assertFalse(Files.exists(cache));
    }

    @ParameterizedTest
    @EnumSource(Outcome.class)
    void shutdownCanCompleteWhileTransportWaitsAndLateCompletionCannotPublishOrRewriteCache(Outcome outcome)
            throws Exception {
        byte[] original = seedCache();
        PendingTransport transport = pending(outcome);
        ControlledConnector connector = new ControlledConnector(github, cache, transport);
        Future<?> download = workers.submit(connector::download);
        assertTrue(transport.entered.await(5, TimeUnit.SECONDS), "Controlled request did not enter transport");

        try {
            // This bounded wait is a deadlock guard: shutdown must not wait for a remote response.
            workers.submit(github::shutdown).get(5, TimeUnit.SECONDS);
            assertFalse(download.isDone(), "The controlled response is still pending at the shutdown boundary");
        } finally {
            transport.complete.countDown();
        }
        download.get(5, TimeUnit.SECONDS);

        assertEquals(1, connector.requests.get());
        assertEquals(0, connector.successes.get());
        assertEquals(0, connector.failures.get());
        assertTrue(github.getLatestReleaseTag().isEmpty());
        assertArrayEquals(original, Files.readAllBytes(cache));
        assertSilent(server.getConsoleSender());
    }

    @Test
    void lateResponseDoesNotPublishIntoAReplacementEnabledPlugin() throws Exception {
        byte[] original = seedCache();
        PendingTransport transport = pending(Outcome.SUCCESS);
        ControlledConnector connector = new ControlledConnector(github, cache, transport);
        Future<?> download = workers.submit(connector::download);
        assertTrue(transport.entered.await(5, TimeUnit.SECONDS));

        GitHubService replacement = createService();
        transport.complete.countDown();
        download.get(5, TimeUnit.SECONDS);

        assertEquals(0, connector.successes.get());
        assertEquals(0, connector.failures.get());
        assertTrue(github.getLatestReleaseTag().isEmpty());
        assertTrue(replacement.getLatestReleaseTag().isEmpty());
        assertArrayEquals(original, Files.readAllBytes(cache));
        assertSilent(server.getConsoleSender());
    }

    @Test
    void stoppedServiceDoesNotEnterTransportOrCreateCache() {
        ControlledConnector connector = connector(Outcome.SUCCESS);
        github.shutdown();

        connector.download();

        assertEquals(0, connector.requests.get());
        assertEquals(0, connector.successes.get());
        assertEquals(0, connector.failures.get());
        assertFalse(Files.exists(cache));
    }

    @Test
    void interruptedTransportRestoresInterruptAndDoesNotUseCacheAsFallback() throws Exception {
        byte[] original = seedCache();
        ControlledConnector connector = new ControlledConnector(github, cache, request -> {
            throw new InterruptedException("Controlled transport interruption");
        });

        boolean interrupted = workers.submit(() -> {
                    try {
                        connector.download();
                        return Thread.currentThread().isInterrupted();
                    } finally {
                        Thread.interrupted();
                    }
                })
                .get(5, TimeUnit.SECONDS);

        assertTrue(interrupted);
        assertEquals(1, connector.requests.get());
        assertEquals(0, connector.successes.get());
        assertEquals(0, connector.failures.get());
        assertTrue(github.getLatestReleaseTag().isEmpty());
        assertArrayEquals(original, Files.readAllBytes(cache));
        assertSilent(server.getConsoleSender());
    }

    @Test
    void alreadyInterruptedWorkerDoesNotStartAnotherRequest() throws Exception {
        ControlledConnector connector = connector(Outcome.SUCCESS);

        boolean interrupted = workers.submit(() -> {
                    Thread.currentThread().interrupt();
                    try {
                        connector.download();
                        return Thread.currentThread().isInterrupted();
                    } finally {
                        Thread.interrupted();
                    }
                })
                .get(5, TimeUnit.SECONDS);

        assertTrue(interrupted);
        assertEquals(0, connector.requests.get());
        assertEquals(0, connector.successes.get());
        assertEquals(0, connector.failures.get());
        assertFalse(Files.exists(cache));
    }

    @Test
    void callbackThatStopsItsServiceDoesNotCommitCacheAfterTheStop() throws Exception {
        byte[] original = seedCache();
        ControlledConnector connector = connector(Outcome.SUCCESS);
        connector.afterSuccess = github::shutdown;

        connector.download();

        assertEquals(1, connector.successes.get());
        assertEquals("v4.1.63", github.getLatestReleaseTag().orElseThrow());
        assertFalse(github.isUpdateAvailable());
        assertArrayEquals(original, Files.readAllBytes(cache));
        assertConsoleNotice();
    }

    private GitHubService createService() throws Exception {
        fixtures.add(new InventoryReadTestPlugin(server));
        Slimefun plugin = Slimefun.instance();
        setField(JavaPlugin.class, plugin, "isEnabled", true);
        SlimefunScheduler scheduler = (SlimefunScheduler) Proxy.newProxyInstance(
                SlimefunScheduler.class.getClassLoader(),
                new Class<?>[] {SlimefunScheduler.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isAcceptingTasks" -> true;
                    case "isFolia" -> false;
                    // Capture by omission: these handles never invoke any startup network callbacks.
                    case "run", "runAsync", "runAsyncAtFixedRate" -> new CapturedHandle();
                    default -> throw new AssertionError("Unexpected scheduler call: " + method.getName());
                });
        setField(Slimefun.class, plugin, "schedulerService", scheduler);
        DefaultCoreLifecycleService lifecycle = (DefaultCoreLifecycleService) Slimefun.getCoreLifecycleService();
        lifecycle.beginStart();
        GitHubService service = Slimefun.getGitHubService();
        service.start(plugin);
        lifecycle.markRunning();
        return service;
    }

    private byte[] seedCache() throws IOException {
        byte[] original = (RELEASE_JSON + "\n").getBytes(StandardCharsets.UTF_8);
        Files.write(cache, original);
        return original;
    }

    private ControlledConnector connector(Outcome outcome) {
        return new ControlledConnector(github, cache, outcome::respond);
    }

    private PendingTransport pending(Outcome outcome) {
        PendingTransport transport = new PendingTransport(outcome);
        pending.add(transport);
        return transport;
    }

    private void assertConsoleNotice() {
        assertEquals(
                LegacyComponentSerializer.legacySection()
                        .deserialize("§6[Slimefun Legacy] §eUpdate available: §f4.1.62 §7→ §a4.1.63"),
                server.getConsoleSender().nextComponentMessage());
        assertEquals(
                LegacyComponentSerializer.legacySection()
                        .deserialize("§7https://github.com/" + REPOSITORY + "/releases/latest"),
                server.getConsoleSender().nextComponentMessage());
        assertSilent(server.getConsoleSender());
    }

    private static void assertSilent(MessageTarget recipient) {
        assertNull(recipient.nextComponentMessage(), "No additional notification should be sent");
    }

    private static void setField(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private enum Outcome {
        SUCCESS,
        UNAVAILABLE,
        IO_FAILURE,
        INVALID_JSON;

        private HttpResponse<String> respond(HttpRequest request) throws IOException {
            return switch (this) {
                case SUCCESS -> new Response(request, 200, RELEASE_JSON);
                case UNAVAILABLE -> new Response(request, 503, "{\"message\":\"Unavailable\"}");
                case IO_FAILURE -> throw new IOException("Controlled transport failure");
                case INVALID_JSON -> new Response(request, 200, "not valid JSON {");
            };
        }
    }

    @FunctionalInterface
    private interface Transport {
        HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException;
    }

    private static final class PendingTransport implements Transport {

        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch complete = new CountDownLatch(1);
        private final Outcome outcome;

        private PendingTransport(Outcome outcome) {
            this.outcome = outcome;
        }

        @Override
        public HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
            entered.countDown();
            if (!complete.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Controlled response was never released");
            }
            return outcome.respond(request);
        }
    }

    private static final class ControlledConnector extends GitHubConnector {

        private final Path cache;
        private final Transport transport;
        private final GitHubReleaseConnector release;
        private final AtomicInteger requests = new AtomicInteger();
        private final AtomicInteger successes = new AtomicInteger();
        private final AtomicInteger failures = new AtomicInteger();
        private Runnable afterSuccess = () -> {};

        private ControlledConnector(GitHubService service, Path cache, Transport transport) {
            super(service, REPOSITORY);
            this.cache = cache;
            this.transport = transport;
            this.release = new GitHubReleaseConnector(service, REPOSITORY);
        }

        @Override
        public String getFileName() {
            return "latest-release";
        }

        @Override
        public String getEndpoint() {
            return "/releases/latest";
        }

        @Override
        public Map<String, Object> getParameters() {
            return Map.of();
        }

        @Override
        File getCacheFile() {
            return cache.toFile();
        }

        @Override
        HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
            requests.incrementAndGet();
            return transport.send(request);
        }

        @Override
        public void onSuccess(JsonElement response) {
            successes.incrementAndGet();
            release.onSuccess(response);
            afterSuccess.run();
        }

        @Override
        public void onFailure() {
            failures.incrementAndGet();
        }
    }

    private record Response(HttpRequest request, int statusCode, String body) implements HttpResponse<String> {

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Map.of(), (name, value) -> true);
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_2;
        }
    }

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
