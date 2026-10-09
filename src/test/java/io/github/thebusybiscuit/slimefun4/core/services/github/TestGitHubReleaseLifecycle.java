package io.github.thebusybiscuit.slimefun4.core.services.github;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.InventoryReadTestPlugin;
import io.github.thebusybiscuit.slimefun4.core.services.lifecycle.DefaultCoreLifecycleService;
import io.github.thebusybiscuit.slimefun4.core.services.scheduling.SchedulerSnapshot;
import io.github.thebusybiscuit.slimefun4.core.services.scheduling.SlimefunScheduler;
import io.github.thebusybiscuit.slimefun4.core.services.scheduling.TaskHandle;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.MessageTarget;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** Replays real connector callbacks at explicit lifecycle boundaries, without HTTP requests or timing races. */
class TestGitHubReleaseLifecycle {

    private static final String REPOSITORY = "wickidcow/Slimefun-Legacy";
    private static final String RELEASES_URL = "https://github.com/" + REPOSITORY + "/releases/latest";
    private static final String PERMISSION = "slimefun.update-notifications";
    private final List<InventoryReadTestPlugin> fixtures = new ArrayList<>();

    private ServerMock server;
    private Session session;

    @BeforeEach
    void setUp() throws Exception {
        server = MockBukkit.mock();
        session = createSession();
    }

    @AfterEach
    void tearDown() {
        try {
            for (int i = fixtures.size() - 1; i >= 0; i--) {
                fixtures.get(i).close();
            }
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void newerReleaseNotifiesOperatorsAndExplicitlyAuthorizedAdministrators() {
        PlayerMock operator = operator("Operator");
        PlayerMock administrator = server.addPlayer("Administrator");
        administrator.setOp(false);
        administrator.addAttachment(session.plugin(), PERMISSION, true);
        PlayerMock ordinaryPlayer = server.addPlayer("OrdinaryPlayer");
        ordinaryPlayer.setOp(false);
        ordinaryPlayer.addAttachment(session.plugin(), PERMISSION, false);

        completeRelease(session, "v4.1.63");
        session.scheduler().runNotifications();

        assertEquals("v4.1.63", session.github().getLatestReleaseTag().orElseThrow());
        assertTrue(session.github().isUpdateAvailable());
        assertNotice(server.getConsoleSender(), "4.1.63");
        assertNotice(operator, "4.1.63");
        assertNotice(administrator, "4.1.63");
        assertSilent(ordinaryPlayer);
        assertTrue(session.scheduler().async.size() >= 2, "Startup work was captured without executing HTTP");
    }

    @Test
    void notificationsRemainOncePerReleaseTagForConsoleAndEachPlayer() {
        PlayerMock player = operator("Operator");
        completeRelease(session, "v4.1.63");
        session.scheduler().runNotifications();
        assertNotice(server.getConsoleSender(), "4.1.63");
        assertNotice(player, "4.1.63");

        completeRelease(session, "v4.1.63");
        session.github().notifyUpdateIfAvailable(player);
        session.scheduler().runNotifications();
        assertSilent(server.getConsoleSender());
        assertSilent(player);

        completeRelease(session, "v4.1.64");
        session.scheduler().runNotifications();
        assertNotice(server.getConsoleSender(), "4.1.64");
        assertNotice(player, "4.1.64");
    }

    @Test
    void authorizedPlayerWhoJoinsAfterReleaseCheckStillReceivesOneNotice() {
        completeRelease(session, "v4.1.63");
        session.scheduler().runNotifications();
        assertNotice(server.getConsoleSender(), "4.1.63");
        PlayerMock player = operator("LaterOperator");

        session.github().notifyUpdateIfAvailable(player);
        session.github().notifyUpdateIfAvailable(player);

        assertNotice(player, "4.1.63");
        assertSilent(server.getConsoleSender());
    }

    @ParameterizedTest
    @ValueSource(strings = {"v4.1.62", "4.1.62.0", "v4.1.61", "4.0.999", "development", "4.1.", ""})
    void equalOlderAndUnparseableReleaseTagsDoNotAnnounceAnUpdate(String tag) {
        PlayerMock player = operator("Operator");

        completeRelease(session, tag);
        session.scheduler().runNotifications();
        session.github().notifyUpdateIfAvailable(player);

        assertFalse(session.github().isUpdateAvailable());
        assertSilent(server.getConsoleSender());
        assertSilent(player);
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "null", "{}", "{\"tag_name\":null}", "{\"tag_name\":{}}"})
    void malformedReleasePayloadDoesNotPublishOrScheduleNotifications(String response) {
        assertDoesNotThrow(() -> connector(session).onSuccess(JsonParser.parseString(response)));

        assertTrue(session.github().getLatestReleaseTag().isEmpty());
        assertTrue(session.scheduler().global.isEmpty());
        assertTrue(session.scheduler().entity.isEmpty());
        assertSilent(server.getConsoleSender());
    }

    @Test
    void notificationKeepsTheOfficialReleaseLinkWhenResponseUrlIsUntrusted() {
        PlayerMock player = operator("Operator");
        JsonObject response = release("v4.1.63");
        response.addProperty("html_url", "https://example.invalid/untrusted-download");

        connector(session).onSuccess(response);
        session.scheduler().runNotifications();

        assertNotice(server.getConsoleSender(), "4.1.63");
        assertNotice(player, "4.1.63");
    }

    @Test
    void responseAfterSingletonTeardownDoesNotThrowOrPublish() throws Exception {
        GitHubReleaseConnector connector = connector(session);
        clearSingleton();

        assertDoesNotThrow(() -> connector.onSuccess(release("v4.1.63")));

        assertNoPublication(session);
    }

    @Test
    void responseAfterOwnerIsDisabledDoesNotPublish() throws Exception {
        GitHubReleaseConnector connector = connector(session);
        setEnabled(session.plugin(), false);

        assertDoesNotThrow(() -> connector.onSuccess(release("v4.1.63")));

        assertNoPublication(session);
    }

    @Test
    void oldResponseDoesNotAttachToAReplacementEnabledPlugin() throws Exception {
        GitHubReleaseConnector connector = connector(session);
        Session replacement = createSession();

        assertDoesNotThrow(() -> connector.onSuccess(release("v4.1.63")));

        assertNoPublication(session);
        assertNoPublication(replacement);
    }

    @Test
    void queuedGlobalNotificationAfterSingletonTeardownDoesNotThrowOrSubmitPlayerWork() throws Exception {
        operator("Operator");
        Runnable notification = queueGlobalNotification();
        clearSingleton();

        assertDoesNotThrow(notification::run);

        assertTrue(session.scheduler().entity.isEmpty());
        assertSilent(server.getConsoleSender());
    }

    @Test
    void queuedGlobalNotificationAfterOwnerIsDisabledDoesNotSubmitPlayerWork() throws Exception {
        operator("Operator");
        Runnable notification = queueGlobalNotification();
        setEnabled(session.plugin(), false);

        assertDoesNotThrow(notification::run);

        assertTrue(session.scheduler().entity.isEmpty());
        assertSilent(server.getConsoleSender());
    }

    @Test
    void queuedGlobalNotificationDoesNotUseAReplacementPluginsScheduler() throws Exception {
        operator("Operator");
        Runnable notification = queueGlobalNotification();
        Session replacement = createSession();

        assertDoesNotThrow(notification::run);

        assertTrue(session.scheduler().entity.isEmpty());
        assertTrue(replacement.scheduler().entity.isEmpty());
        assertNoPublication(replacement);
    }

    @Test
    void queuedPlayerNotificationAfterSingletonTeardownDoesNotThrowOrSend() throws Exception {
        PlayerMock player = operator("Operator");
        Runnable notification = queuePlayerNotification();
        clearSingleton();

        assertDoesNotThrow(notification::run);

        assertSilent(player);
        assertSilent(server.getConsoleSender());
    }

    @Test
    void queuedPlayerNotificationAfterOwnerIsDisabledDoesNotSend() throws Exception {
        PlayerMock player = operator("Operator");
        Runnable notification = queuePlayerNotification();
        setEnabled(session.plugin(), false);

        assertDoesNotThrow(notification::run);

        assertSilent(player);
        assertSilent(server.getConsoleSender());
    }

    @Test
    void queuedPlayerNotificationDoesNotRebindToAReplacementEnabledPlugin() throws Exception {
        PlayerMock player = operator("Operator");
        Runnable notification = queuePlayerNotification();
        Session replacement = createSession();

        assertDoesNotThrow(notification::run);

        assertSilent(player);
        assertNoPublication(replacement);
    }

    @Test
    void publicNotificationEntryPointIsHarmlessAfterSingletonTeardown() throws Exception {
        completeRelease(session, "v4.1.63");
        session.scheduler().runNotifications();
        assertNotice(server.getConsoleSender(), "4.1.63");
        PlayerMock player = operator("LaterOperator");
        clearSingleton();

        assertDoesNotThrow(() -> session.github().notifyUpdateIfAvailable(player));

        assertSilent(player);
    }

    @Test
    void updateAvailabilityIsFalseAfterSingletonTeardown() throws Exception {
        completeRelease(session, "v4.1.63");
        session.scheduler().runNotifications();
        assertTrue(session.github().isUpdateAvailable());
        clearSingleton();

        assertFalse(assertDoesNotThrow(() -> session.github().isUpdateAvailable()));
    }

    @Test
    void releaseChecksRemainEligibleDuringNormalStartup() throws Exception {
        Session starting = createSession(false);
        PlayerMock player = operator("StartupOperator");

        completeRelease(starting, "v4.1.63");
        starting.scheduler().runNotifications();

        assertTrue(starting.github().isUpdateAvailable());
        assertNotice(server.getConsoleSender(), "4.1.63");
        assertNotice(player, "4.1.63");
    }

    @Test
    void explicitServiceShutdownRejectsCompletionWhileOriginalPluginRemainsEnabled() {
        GitHubReleaseConnector connector = connector(session);
        session.github().shutdown();
        assertTrue(session.plugin().isEnabled());
        assertSame(session.plugin(), Slimefun.instance());

        assertDoesNotThrow(() -> connector.onSuccess(release("v4.1.63")));

        assertNoPublication(session);
    }

    @Test
    void coreDisableStopsGithubBeforeTheUnitTestEarlyReturn() {
        GitHubReleaseConnector connector = connector(session);

        session.plugin().onDisable();
        assertDoesNotThrow(() -> connector.onSuccess(release("v4.1.63")));

        assertNoPublication(session);
        assertTrue(session.scheduler().handles.stream().allMatch(RecordedHandle::isCancelled));
    }

    @Test
    void repeatedStartDoesNotDuplicateOwnedTasksOrConnectors() {
        int handles = session.scheduler().handles.size();
        int connectors = session.github().getConnectors().size();

        session.github().start(session.plugin());

        assertEquals(handles, session.scheduler().handles.size());
        assertEquals(connectors, session.github().getConnectors().size());
    }

    @Test
    void stoppedServiceCannotRestartAndRepeatedShutdownDoesNotRecancelHandles() {
        List<RecordedHandle> owned = List.copyOf(session.scheduler().handles);
        session.github().shutdown();
        List<Integer> cancellations =
                owned.stream().map(handle -> handle.cancelCalls).toList();

        session.github().shutdown();
        session.github().start(session.plugin());
        completeRelease(session, "v4.1.63");

        assertEquals(owned.size(), session.scheduler().handles.size());
        assertEquals(
                cancellations, owned.stream().map(handle -> handle.cancelCalls).toList());
        assertTrue(owned.stream().allMatch(RecordedHandle::isCancelled));
        assertNoPublication(session);
    }

    @Test
    void shutdownBeforeStartIsTerminalWithoutSchedulingAnyWork() {
        GitHubService neverStarted = new GitHubService(REPOSITORY);
        int existingTasks = session.scheduler().handles.size();

        neverStarted.shutdown();
        neverStarted.start(session.plugin());
        new GitHubReleaseConnector(neverStarted, REPOSITORY).onSuccess(release("v4.1.63"));

        assertTrue(neverStarted.getLatestReleaseTag().isEmpty());
        assertTrue(neverStarted.getConnectors().isEmpty());
        assertEquals(existingTasks, session.scheduler().handles.size());
        assertSilent(server.getConsoleSender());
    }

    @Test
    void staleServiceCannotRebindWhenStartedWithAReplacementPlugin() throws Exception {
        Session replacement = createSession();
        int replacementTasks = replacement.scheduler().handles.size();

        session.github().start(replacement.plugin());
        completeRelease(session, "v4.1.63");

        assertNoPublication(session);
        assertNoPublication(replacement);
        assertEquals(replacementTasks, replacement.scheduler().handles.size());
    }

    @Test
    void serviceShutdownCancelsOnlyPendingOwnedTasks() {
        PlayerMock player = operator("Operator");
        List<RecordedHandle> startupTasks = List.copyOf(session.scheduler().handles);
        completeRelease(session, "v4.1.63");
        session.scheduler().runNotifications();
        List<RecordedHandle> completedNotifications = List.copyOf(session.scheduler()
                .handles
                .subList(startupTasks.size(), session.scheduler().handles.size()));
        List<Integer> completedCancellationCounts = completedNotifications.stream()
                .map(handle -> handle.cancelCalls)
                .toList();
        TaskHandle unrelatedTask = session.scheduler().scheduler.runAsync(() -> {});
        assertFalse(completedNotifications.isEmpty());
        assertNotice(server.getConsoleSender(), "4.1.63");
        assertNotice(player, "4.1.63");

        session.github().shutdown();

        assertTrue(startupTasks.stream().allMatch(RecordedHandle::isCancelled));
        assertEquals(
                completedCancellationCounts,
                completedNotifications.stream()
                        .map(handle -> handle.cancelCalls)
                        .toList(),
                "Completed one-shot callbacks must be removed from service ownership");
        assertFalse(unrelatedTask.isCancelled(), "Stopping GitHub must not cancel another core service's work");
    }

    @Test
    void queuedGlobalNotificationCannotPublishAfterExplicitServiceShutdown() {
        PlayerMock player = operator("Operator");
        Runnable notification = queueGlobalNotification();
        session.github().shutdown();

        assertDoesNotThrow(notification::run);

        assertTrue(session.scheduler().entity.isEmpty());
        assertSilent(player);
        assertSilent(server.getConsoleSender());
    }

    @Test
    void queuedPlayerNotificationCannotPublishAfterExplicitServiceShutdown() {
        PlayerMock player = operator("Operator");
        Runnable notification = queuePlayerNotification();
        session.github().shutdown();

        assertDoesNotThrow(notification::run);

        assertSilent(player);
        assertSilent(server.getConsoleSender());
    }

    @Test
    void lateResponseCannotReplaceTheLastAcceptedReleaseAfterShutdown() {
        PlayerMock player = operator("Operator");
        completeRelease(session, "v4.1.63");
        session.scheduler().runNotifications();
        assertNotice(server.getConsoleSender(), "4.1.63");
        assertNotice(player, "4.1.63");
        session.github().shutdown();

        completeRelease(session, "v4.1.64");
        session.github().notifyUpdateIfAvailable(player);

        assertEquals("v4.1.63", session.github().getLatestReleaseTag().orElseThrow());
        assertFalse(session.github().isUpdateAvailable());
        assertSilent(player);
        assertSilent(server.getConsoleSender());
    }

    @Test
    void responseIsRejectedAsSoonAsCoreShutdownBegins() {
        lifecycle().beginShutdown();
        assertTrue(session.plugin().isEnabled());

        completeRelease(session, "v4.1.63");

        assertNoPublication(session);
    }

    @Test
    void responseIsRejectedAfterStartupHasFailed() {
        lifecycle().markStartupFailed("fixture", new IllegalStateException("Startup failed"));

        completeRelease(session, "v4.1.63");

        assertNoPublication(session);
    }

    @Test
    void responseIsRejectedWhenTheOriginalSchedulerHasQuiesced() {
        session.scheduler().scheduler.quiesce();

        completeRelease(session, "v4.1.63");

        assertNoPublication(session);
    }

    @Test
    void permissionChecksAndMessagesExecuteInsideThePlayersScheduledCallback() {
        OwnershipCheckingPlayer player = new OwnershipCheckingPlayer(server, session.scheduler());
        server.addPlayer(player);
        player.setOp(false);
        player.addAttachment(session.plugin(), PERMISSION, true);
        player.enforceOwnership = true;

        completeRelease(session, "v4.1.63");
        session.scheduler().runNotifications();

        assertNotice(server.getConsoleSender(), "4.1.63");
        assertNotice(player, "4.1.63");
    }

    @Test
    void activeSchedulerFailuresAreNotSilentlyTreatedAsShutdown() {
        session.scheduler().globalRejection = new IllegalPluginAccessException("Active submission failure");

        assertThrows(IllegalPluginAccessException.class, () -> completeRelease(session, "v4.1.63"));
    }

    @Test
    void schedulerRejectionAfterOwnerDisableIsHarmless() {
        session.scheduler().beforeGlobalSubmission =
                () -> assertDoesNotThrow(() -> setEnabled(session.plugin(), false));
        session.scheduler().globalRejection = new IllegalPluginAccessException("Plugin became disabled");

        assertDoesNotThrow(() -> completeRelease(session, "v4.1.63"));

        assertTrue(session.scheduler().global.isEmpty());
        assertTrue(session.scheduler().entity.isEmpty());
    }

    @Test
    void globalCallbackCompletingBeforeItsHandleIsReturnedDoesNotRemainOwned() {
        session.scheduler().completeGlobalDuringSubmission = true;
        int startupHandles = session.scheduler().handles.size();

        completeRelease(session, "v4.1.63");

        assertEquals(startupHandles + 1, session.scheduler().handles.size());
        RecordedHandle completed = session.scheduler().handles.getLast();
        int cancellationsBeforeStop = completed.cancelCalls;
        assertTrue(session.scheduler().global.isEmpty());
        assertNotice(server.getConsoleSender(), "4.1.63");

        session.github().shutdown();

        assertEquals(cancellationsBeforeStop, completed.cancelCalls);
    }

    @Test
    void entityRetiringBeforeItsHandleIsReturnedDoesNotRemainOwnedOrSend() {
        PlayerMock player = operator("Operator");
        session.scheduler().retireEntityDuringSubmission = true;
        Runnable globalNotification = queueGlobalNotification();

        globalNotification.run();

        RecordedHandle retired = session.scheduler().handles.getLast();
        int cancellationsBeforeStop = retired.cancelCalls;
        assertTrue(session.scheduler().entity.isEmpty());
        assertSilent(player);

        session.github().shutdown();

        assertEquals(cancellationsBeforeStop, retired.cancelCalls);
    }

    @Test
    void shutdownDuringSubmissionStillCancelsTheHandleReturnedAfterward() {
        PlayerMock player = operator("Operator");
        session.scheduler().beforeGlobalSubmission = session.github()::shutdown;

        completeRelease(session, "v4.1.63");

        assertTrue(session.scheduler().handles.stream().allMatch(RecordedHandle::isCancelled));
        assertEquals(1, session.scheduler().global.size());
        assertNotice(server.getConsoleSender(), "4.1.63");

        // A cancelled scheduler handle is not itself evidence that an already captured callback cannot run.
        session.scheduler().runNotifications();

        assertTrue(session.scheduler().entity.isEmpty());
        assertSilent(player);
        assertSilent(server.getConsoleSender());
    }

    private DefaultCoreLifecycleService lifecycle() {
        return (DefaultCoreLifecycleService) Slimefun.getCoreLifecycleService();
    }

    private Session createSession() throws Exception {
        return createSession(true);
    }

    private Session createSession(boolean running) throws Exception {
        fixtures.add(new InventoryReadTestPlugin(server));
        Slimefun plugin = Slimefun.instance();
        assertNotNull(plugin);
        setEnabled(plugin, true);
        RecordingScheduler scheduler = new RecordingScheduler();
        setField(Slimefun.class, plugin, "schedulerService", scheduler.scheduler);
        GitHubService github = Slimefun.getGitHubService();
        DefaultCoreLifecycleService lifecycle = (DefaultCoreLifecycleService) Slimefun.getCoreLifecycleService();
        lifecycle.beginStart();
        github.start(plugin);
        if (running) {
            lifecycle.markRunning();
        }
        return new Session(plugin, github, scheduler);
    }

    private PlayerMock operator(String name) {
        PlayerMock player = server.addPlayer(name);
        player.setOp(true);
        return player;
    }

    private Runnable queueGlobalNotification() {
        completeRelease(session, "v4.1.63");
        assertNotice(server.getConsoleSender(), "4.1.63");
        assertEquals(1, session.scheduler().global.size());
        return session.scheduler().global.removeFirst();
    }

    private Runnable queuePlayerNotification() {
        queueGlobalNotification().run();
        assertEquals(1, session.scheduler().entity.size());
        return session.scheduler().entity.removeFirst();
    }

    private void assertNoPublication(Session target) {
        assertTrue(target.github().getLatestReleaseTag().isEmpty());
        assertTrue(target.scheduler().global.isEmpty());
        assertTrue(target.scheduler().entity.isEmpty());
        assertSilent(server.getConsoleSender());
    }

    private static void completeRelease(Session target, String tag) {
        connector(target).onSuccess(release(tag));
    }

    private static GitHubReleaseConnector connector(Session target) {
        return new GitHubReleaseConnector(target.github(), REPOSITORY);
    }

    private static JsonObject release(String tag) {
        JsonObject response = new JsonObject();
        response.addProperty("tag_name", tag);
        response.addProperty("html_url", "https://github.com/" + REPOSITORY + "/releases/tag/" + tag);
        return response;
    }

    private static void assertNotice(MessageTarget recipient, String tag) {
        assertEquals(
                LegacyComponentSerializer.legacySection()
                        .deserialize("§6[Slimefun Legacy] §eUpdate available: §f4.1.62 §7→ §a" + tag),
                recipient.nextComponentMessage());
        assertEquals(
                LegacyComponentSerializer.legacySection().deserialize("§7" + RELEASES_URL),
                recipient.nextComponentMessage());
        assertSilent(recipient);
    }

    private static void clearSingleton() throws Exception {
        setField(Slimefun.class, null, "instance", null);
    }

    private static void setEnabled(Slimefun plugin, boolean enabled) throws Exception {
        // The retained unit-test constructor deliberately avoids real service startup/shutdown. Set only the lifecycle
        // boundary needed for this callback regression; invoking onEnable would initialize unrelated world services.
        setField(JavaPlugin.class, plugin, "isEnabled", enabled);
    }

    private static void assertSilent(MessageTarget recipient) {
        assertNull(recipient.nextComponentMessage(), "No additional notification should be sent");
    }

    private static void setField(Class<?> owner, Object target, String name, Object value) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private record Session(Slimefun plugin, GitHubService github, RecordingScheduler scheduler) {}

    /** Captures real callbacks for manual replay; async callbacks are intentionally never run. */
    private static final class RecordingScheduler {

        private final List<Runnable> global = new ArrayList<>();
        private final List<Runnable> entity = new ArrayList<>();
        private final List<Runnable> async = new ArrayList<>();
        private final List<RecordedHandle> handles = new ArrayList<>();
        private boolean accepting = true;
        private Object entityOwner;
        private Runnable beforeGlobalSubmission = () -> {};
        private RuntimeException globalRejection;
        private boolean completeGlobalDuringSubmission;
        private boolean retireEntityDuringSubmission;
        private final SlimefunScheduler scheduler = (SlimefunScheduler) Proxy.newProxyInstance(
                SlimefunScheduler.class.getClassLoader(),
                new Class<?>[] {SlimefunScheduler.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "run" -> {
                        beforeGlobalSubmission.run();
                        if (globalRejection != null) {
                            throw globalRejection;
                        }
                        if (completeGlobalDuringSubmission) {
                            RecordedHandle completed = newHandle();
                            ((Runnable) args[0]).run();
                            yield completed;
                        }
                        yield capture(global, (Runnable) args[0]);
                    }
                    case "runFor" -> {
                        if (retireEntityDuringSubmission) {
                            RecordedHandle retired = newHandle();
                            ((Runnable) args[2]).run();
                            yield retired;
                        }
                        yield capture(entity, withEntityOwner(args[0], (Runnable) args[1]));
                    }
                    case "runAsync", "runAsyncAtFixedRate" -> capture(async, (Runnable) args[0]);
                    case "isAcceptingTasks" -> accepting;
                    case "isFolia" -> false;
                    case "isOwnedByCurrentRegion" -> true;
                    case "getActiveTaskCount" ->
                        (int) handles.stream()
                                .filter(handle -> !handle.isCancelled())
                                .count();
                    case "getSnapshot" -> new SchedulerSnapshot(accepting, handles.size(), false);
                    case "quiesce" -> {
                        accepting = false;
                        yield null;
                    }
                    case "cancelAll" -> {
                        handles.forEach(RecordedHandle::cancel);
                        yield null;
                    }
                    case "toString" -> "RecordingScheduler";
                    default -> throw new AssertionError("Unexpected scheduler call: " + method.getName());
                });

        private TaskHandle capture(List<Runnable> queue, Runnable callback) {
            RecordedHandle handle = newHandle();
            if (accepting) {
                queue.add(callback);
            } else {
                handle.cancel();
            }
            return handle;
        }

        private RecordedHandle newHandle() {
            RecordedHandle handle = new RecordedHandle();
            handles.add(handle);
            return handle;
        }

        private Runnable withEntityOwner(Object owner, Runnable callback) {
            return () -> {
                Object previousOwner = entityOwner;
                entityOwner = owner;
                try {
                    callback.run();
                } finally {
                    entityOwner = previousOwner;
                }
            };
        }

        private void runNotifications() {
            while (!global.isEmpty()) {
                global.removeFirst().run();
            }
            while (!entity.isEmpty()) {
                entity.removeFirst().run();
            }
        }
    }

    private static final class RecordedHandle implements TaskHandle {

        private boolean cancelled;
        private int cancelCalls;

        @Override
        public void cancel() {
            cancelled = true;
            cancelCalls++;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }

    private static final class OwnershipCheckingPlayer extends PlayerMock {

        private final RecordingScheduler scheduler;
        private boolean enforceOwnership;

        private OwnershipCheckingPlayer(ServerMock server, RecordingScheduler scheduler) {
            super(server, "EntityOwnedAdministrator");
            this.scheduler = scheduler;
        }

        @Override
        public boolean isOp() {
            assertOwner();
            return super.isOp();
        }

        @Override
        public boolean hasPermission(String permission) {
            assertOwner();
            return super.hasPermission(permission);
        }

        @Override
        public void sendMessage(String message) {
            assertOwner();
            super.sendMessage(message);
        }

        private void assertOwner() {
            if (enforceOwnership) {
                assertSame(this, scheduler.entityOwner, "Player access must run in its entity-owned callback");
            }
        }
    }
}
