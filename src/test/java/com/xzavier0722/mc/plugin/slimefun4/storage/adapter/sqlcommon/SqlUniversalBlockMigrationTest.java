package com.xzavier0722.mc.plugin.slimefun4.storage.adapter.sqlcommon;

import static org.junit.jupiter.api.Assertions.*;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.BlockStorageMigration;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SqlUniversalBlockMigrationTest {
    @TempDir
    Path directory;

    @Test
    void commitPreservesAllOpaquePayloadsAndUnrelatedRecordsAcrossReopenAndExactRetry() throws Exception {
        Path file = directory.resolve("items.db");
        try (var connection = open(file)) {
            SqlMigrationFixtures.create(connection, "");
            int originalIsolation = connection.getTransactionIsolation();
            SqlUniversalBlockMigration.execute(connection, "", SqlMigrationFixtures.plan());
            assertCommitted(connection, "");
            assertTrue(connection.getAutoCommit());
            assertEquals(originalIsolation, connection.getTransactionIsolation());
        }
        try (var reopened = open(file)) {
            var before = SqlMigrationFixtures.dump(reopened, "");
            SqlUniversalBlockMigration.execute(reopened, "", SqlMigrationFixtures.plan());
            assertEquals(before, SqlMigrationFixtures.dump(reopened, ""));
            assertCommitted(reopened, "");
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7})
    void everyMutationFailureRollsBackTheWholeMoveAndCanRetry(int stage) throws Exception {
        try (var connection = open(directory.resolve("stage-" + stage + ".db"))) {
            SqlMigrationFixtures.create(connection, "");
            var before = SqlMigrationFixtures.dump(connection, "");
            var failure = assertThrows(
                    BlockStorageMigration.Failure.class,
                    () -> SqlUniversalBlockMigration.execute(
                            failAfterUpdate(connection, stage), "", SqlMigrationFixtures.plan()));
            assertTrue(failure.isRestagingSafe());
            assertEquals(before, SqlMigrationFixtures.dump(connection, ""));
            assertTrue(connection.getAutoCommit());
            SqlUniversalBlockMigration.execute(connection, "", SqlMigrationFixtures.plan());
            assertCommitted(connection, "");
        }
    }

    @Test
    void aConfirmedSerializationConflictRetriesWithoutChangingTheSnapshot() throws Exception {
        try (var connection = open(directory.resolve("transient-conflict.db"))) {
            SqlMigrationFixtures.create(connection, "");
            AtomicInteger conflicts = new AtomicInteger();
            var intercepted = wrap(connection, (method, parameters) -> {
                if (method.equals("prepareStatement")
                        && ((String) parameters[0]).startsWith("INSERT INTO")
                        && conflicts.getAndIncrement() == 0) {
                    throw new java.sql.SQLTransactionRollbackException("injected serialization conflict", "40001");
                }
                return UNHANDLED;
            });
            SqlUniversalBlockMigration.execute(intercepted, "", SqlMigrationFixtures.plan());
            assertTrue(conflicts.get() > 1);
            assertCommitted(connection, "");
        }
    }

    @Test
    void lostCommitAcknowledgementRetriesTheSameDestinationWithoutDuplicatingItems() throws Exception {
        try (var connection = open(directory.resolve("lost-ack.db"))) {
            SqlMigrationFixtures.create(connection, "");
            var interrupted = wrap(connection, (method, parameters) -> {
                if (method.equals("commit")) {
                    connection.commit();
                    throw new SQLException("injected lost commit acknowledgement");
                }
                return UNHANDLED;
            });
            var failure = assertThrows(
                    BlockStorageMigration.Failure.class,
                    () -> SqlUniversalBlockMigration.execute(interrupted, "", SqlMigrationFixtures.plan()));
            assertFalse(failure.isRestagingSafe());
            assertCommitted(connection, "");
            var committed = SqlMigrationFixtures.dump(connection, "");
            SqlUniversalBlockMigration.execute(connection, "", SqlMigrationFixtures.plan());
            assertEquals(committed, SqlMigrationFixtures.dump(connection, ""));
        }
    }

    @Test
    void failureBeforeCommitLeavesTheOriginalAndRetainsAnUnambiguousRetryIdentity() throws Exception {
        try (var connection = open(directory.resolve("commit-fails.db"))) {
            SqlMigrationFixtures.create(connection, "");
            var before = SqlMigrationFixtures.dump(connection, "");
            var intercepted = wrap(connection, (method, parameters) -> {
                if (method.equals("commit")) throw new SQLException("injected commit refusal");
                return UNHANDLED;
            });
            var failure = assertThrows(
                    BlockStorageMigration.Failure.class,
                    () -> SqlUniversalBlockMigration.execute(intercepted, "", SqlMigrationFixtures.plan()));
            assertFalse(
                    failure.isRestagingSafe(),
                    "An exception from commit does not establish whether the server committed");
            assertEquals(before, SqlMigrationFixtures.dump(connection, ""));
            SqlUniversalBlockMigration.execute(connection, "", SqlMigrationFixtures.plan());
            assertCommitted(connection, "");
        }
    }

    @Test
    void changedSourceAndOccupiedDestinationNeverGetOverwritten() throws Exception {
        try (var connection = open(directory.resolve("changed.db"))) {
            SqlMigrationFixtures.create(connection, "");
            SqlMigrationFixtures.execute(
                    connection,
                    "UPDATE block_data SET data_val=? WHERE loc=? AND data_key=?",
                    "newer live value",
                    SqlMigrationFixtures.SOURCE,
                    "opaque:key");
            var changed = SqlMigrationFixtures.dump(connection, "");
            assertThrows(
                    BlockStorageMigration.Failure.class,
                    () -> SqlUniversalBlockMigration.execute(connection, "", SqlMigrationFixtures.plan()));
            assertEquals(changed, SqlMigrationFixtures.dump(connection, ""));
            SqlMigrationFixtures.execute(
                    connection,
                    "UPDATE block_data SET data_val=? WHERE loc=? AND data_key=?",
                    SqlMigrationFixtures.plan().sourceData().get("opaque:key"),
                    SqlMigrationFixtures.SOURCE,
                    "opaque:key");
            SqlMigrationFixtures.execute(
                    connection,
                    "INSERT INTO universal_record VALUES (?, ?, ?)",
                    SqlMigrationFixtures.DESTINATION.toString(),
                    "OTHER_OWNER",
                    "BLOCK,INVENTORY");
            var occupied = SqlMigrationFixtures.dump(connection, "");
            assertThrows(
                    BlockStorageMigration.Failure.class,
                    () -> SqlUniversalBlockMigration.execute(connection, "", SqlMigrationFixtures.plan()));
            assertEquals(occupied, SqlMigrationFixtures.dump(connection, ""));
        }
    }

    @Test
    void changedDestinationAfterCommitDoesNotCountAsAnExactReplay() throws Exception {
        try (var connection = open(directory.resolve("changed-target.db"))) {
            SqlMigrationFixtures.create(connection, "");
            SqlUniversalBlockMigration.execute(connection, "", SqlMigrationFixtures.plan());
            SqlMigrationFixtures.execute(
                    connection,
                    "UPDATE universal_data SET data_val=? WHERE data_key=?",
                    "different data",
                    "opaque:key");
            var changed = SqlMigrationFixtures.dump(connection, "");
            assertThrows(
                    BlockStorageMigration.Failure.class,
                    () -> SqlUniversalBlockMigration.execute(connection, "", SqlMigrationFixtures.plan()));
            assertEquals(changed, SqlMigrationFixtures.dump(connection, ""));
        }
    }

    @Test
    void rollbackFailureNeverEnablesAutoCommitOnAnUnresolvedTransaction() throws Exception {
        Path file = directory.resolve("rollback-fails.db");
        var connection = open(file);
        SqlMigrationFixtures.create(connection, "");
        var before = SqlMigrationFixtures.dump(connection, "");
        AtomicBoolean aborted = new AtomicBoolean();
        AtomicBoolean reset = new AtomicBoolean();
        var intercepted = wrap(failAfterUpdate(connection, 4), (method, parameters) -> {
            if (method.equals("rollback")) throw new SQLException("injected rollback failure");
            if (method.equals("abort")) {
                aborted.set(true);
                connection.close();
                return null;
            }
            if (method.equals("setAutoCommit") && Boolean.TRUE.equals(parameters[0])) reset.set(true);
            return UNHANDLED;
        });
        var failure = assertThrows(
                BlockStorageMigration.Failure.class,
                () -> SqlUniversalBlockMigration.execute(intercepted, "", SqlMigrationFixtures.plan()));
        assertFalse(failure.isRestagingSafe());
        assertTrue(aborted.get());
        assertFalse(reset.get());
        try (var reopened = open(file)) {
            assertEquals(before, SqlMigrationFixtures.dump(reopened, ""));
        }
    }

    @Test
    void callerTransactionAndUnsafePrefixAreRefusedBeforeMutatingAnything() throws Exception {
        try (var connection = open(directory.resolve("owned.db"))) {
            SqlMigrationFixtures.create(connection, "");
            var before = SqlMigrationFixtures.dump(connection, "");
            connection.setAutoCommit(false);
            assertThrows(
                    SQLException.class,
                    () -> SqlUniversalBlockMigration.execute(connection, "", SqlMigrationFixtures.plan()));
            assertFalse(connection.getAutoCommit());
            connection.rollback();
            connection.setAutoCommit(true);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SqlUniversalBlockMigration.execute(
                            connection, "bad;DROP_TABLE", SqlMigrationFixtures.plan()));
            assertEquals(before, SqlMigrationFixtures.dump(connection, ""));
        }
    }

    @Test
    void snapshotDefensiveCopiesRejectReservedKeyConflictsAndKeepScalarTypesOpaque() {
        byte[] payload = {1, 2, 3};
        Map<String, String> data = new HashMap<>(Map.of("counter", "00042"));
        Map<Integer, byte[]> items = new HashMap<>(Map.of(8, payload));
        var plan = new BlockStorageMigration(
                "world;1:2:3", "world;0:0", "OLD_ID", UUID.randomUUID(), "world;1:2:3", data, items);
        data.put("counter", "42");
        payload[0] = 0;
        items.clear();
        plan.inventory().get(8)[1] = 0;
        assertEquals("00042", plan.sourceData().get("counter"));
        assertArrayEquals(new byte[] {1, 2, 3}, plan.inventory().get(8));
        assertThrows(
                UnsupportedOperationException.class, () -> plan.sourceData().clear());
        assertThrows(
                IllegalArgumentException.class,
                () -> new BlockStorageMigration(
                        "world;1:2:3",
                        "world;0:0",
                        "OLD_ID",
                        UUID.randomUUID(),
                        "world;1:2:3",
                        Map.of("location", "another world"),
                        Map.of()));
    }

    @Test
    void twoConcurrentMigrationsCannotProduceTwoDestinationsForOneSource() throws Exception {
        Path file = directory.resolve("concurrent.db");
        try (var connection = open(file)) {
            SqlMigrationFixtures.create(connection, "");
        }
        CountDownLatch start = new CountDownLatch(1);
        List<CompletableFuture<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            UUID uuid = UUID.randomUUID();
            results.add(CompletableFuture.supplyAsync(() -> {
                try (var connection = open(file)) {
                    start.await(10, TimeUnit.SECONDS);
                    var original = SqlMigrationFixtures.plan();
                    var plan = new BlockStorageMigration(
                            original.location(),
                            original.chunk(),
                            original.slimefunId(),
                            uuid,
                            original.location(),
                            original.sourceData(),
                            original.inventory());
                    SqlUniversalBlockMigration.execute(connection, "", plan);
                    return true;
                } catch (Exception failure) {
                    return false;
                }
            }));
        }
        start.countDown();
        int succeeded = 0;
        for (var result : results) if (result.get(15, TimeUnit.SECONDS)) succeeded++;
        assertEquals(1, succeeded);
        try (var connection = open(file)) {
            assertEquals(1, SqlMigrationFixtures.count(connection, "universal_record"));
            assertEquals(1, SqlMigrationFixtures.count(connection, "block_record"));
            assertEquals(4, SqlMigrationFixtures.count(connection, "universal_inventory"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"before", "after"})
    void abruptJvmExitAtCommitBoundaryReopensAsWholeSourceOrWholeDestination(String mode) throws Exception {
        Path file = directory.resolve("crash-" + mode + ".db");
        Map<String, String> original;
        try (var connection = open(file)) {
            SqlMigrationFixtures.create(connection, "");
            original = SqlMigrationFixtures.dump(connection, "");
        }
        String classpath = Stream.of(
                        SqlMigrationCrashProbe.class,
                        BlockStorageMigration.class,
                        org.sqlite.JDBC.class,
                        org.slf4j.LoggerFactory.class)
                .map(type -> Path.of(type.getProtectionDomain()
                                .getCodeSource()
                                .getLocation()
                                .getPath())
                        .toString())
                .distinct()
                .collect(Collectors.joining(java.io.File.pathSeparator));
        var process = new ProcessBuilder(
                        Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                        "-cp",
                        classpath,
                        SqlMigrationCrashProbe.class.getName(),
                        file.toString(),
                        mode)
                .redirectErrorStream(true)
                .redirectOutput(directory.resolve("crash-" + mode + ".log").toFile())
                .start();
        if (!process.waitFor(20, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("Crash probe did not reach its commit boundary");
        }
        assertEquals(mode.equals("before") ? 23 : 24, process.exitValue());
        try (var reopened = open(file)) {
            if (mode.equals("before")) assertEquals(original, SqlMigrationFixtures.dump(reopened, ""));
            else assertCommitted(reopened, "");
            SqlUniversalBlockMigration.execute(reopened, "", SqlMigrationFixtures.plan());
            assertCommitted(reopened, "");
        }
    }

    @TestFactory
    Stream<DynamicTest> explicitlyConfiguredExternalDatabasesUseTheSameAtomicContract() {
        return Stream.of("MYSQL", "POSTGRES")
                .filter(name -> System.getenv("LEGACY_MIGRATION_" + name + "_URL") != null)
                .map(name -> DynamicTest.dynamicTest(name + " commit, rollback and replay", () -> {
                    String url = System.getenv("LEGACY_MIGRATION_" + name + "_URL");
                    String user = System.getenv("LEGACY_MIGRATION_" + name + "_USER");
                    String password = System.getenv("LEGACY_MIGRATION_" + name + "_PASSWORD");
                    String prefix = "migration_"
                            + UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "_";
                    try (var connection = DriverManager.getConnection(url, user, password)) {
                        try {
                            SqlMigrationFixtures.create(connection, prefix);
                            var original = SqlMigrationFixtures.dump(connection, prefix);
                            for (int stage = 1; stage <= 7; stage++) {
                                int failureStage = stage;
                                var failure = assertThrows(
                                        BlockStorageMigration.Failure.class,
                                        () -> SqlUniversalBlockMigration.execute(
                                                failAfterUpdate(connection, failureStage),
                                                prefix,
                                                SqlMigrationFixtures.plan()));
                                assertTrue(failure.isRestagingSafe(), "Confirmed rollback at mutation " + stage);
                                assertEquals(
                                        original,
                                        SqlMigrationFixtures.dump(connection, prefix),
                                        "All original rows must survive mutation failure " + stage);
                                assertTrue(connection.getAutoCommit());
                            }
                            var lostAcknowledgement = wrap(connection, (method, parameters) -> {
                                if (method.equals("commit")) {
                                    connection.commit();
                                    throw new SQLException("injected lost acknowledgement after real database commit");
                                }
                                return UNHANDLED;
                            });
                            var uncertain = assertThrows(
                                    BlockStorageMigration.Failure.class,
                                    () -> SqlUniversalBlockMigration.execute(
                                            lostAcknowledgement, prefix, SqlMigrationFixtures.plan()));
                            assertFalse(uncertain.isRestagingSafe());
                            assertCommitted(connection, prefix);
                            var committed = SqlMigrationFixtures.dump(connection, prefix);
                            SqlUniversalBlockMigration.execute(connection, prefix, SqlMigrationFixtures.plan());
                            assertEquals(committed, SqlMigrationFixtures.dump(connection, prefix));
                        } finally {
                            try (var statement = connection.createStatement()) {
                                for (String table : List.of(
                                        "universal_inventory",
                                        "universal_data",
                                        "universal_record",
                                        "block_inventory",
                                        "block_data",
                                        "block_record")) statement.execute("DROP TABLE IF EXISTS " + prefix + table);
                            }
                        }
                    }
                }));
    }

    private static void assertCommitted(Connection connection, String prefix) throws Exception {
        assertEquals(1, SqlMigrationFixtures.count(connection, prefix + "block_record"), "Unrelated machine survives");
        assertEquals(0, SqlMigrationFixtures.count(connection, prefix + "block_data"));
        assertEquals(0, SqlMigrationFixtures.count(connection, prefix + "block_inventory"));
        assertEquals(1, SqlMigrationFixtures.count(connection, prefix + "universal_record"));
        assertEquals(3, SqlMigrationFixtures.count(connection, prefix + "universal_data"));
        assertTrue(SqlMigrationFixtures.plan()
                .matchesInventory(SqlMigrationFixtures.inventory(
                        connection, prefix, "universal_inventory", SqlMigrationFixtures.DESTINATION.toString())));
    }

    private static Connection open(Path path) throws SQLException {
        var connection = DriverManager.getConnection("jdbc:sqlite:" + path);
        try (var statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout=5000");
        }
        return connection;
    }

    private static Connection failAfterUpdate(Connection connection, int stage) {
        AtomicInteger updates = new AtomicInteger();
        return wrap(connection, (method, parameters) -> {
            if (!method.equals("prepareStatement")) return UNHANDLED;
            PreparedStatement real = connection.prepareStatement((String) parameters[0]);
            return Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(),
                    new Class<?>[] {PreparedStatement.class},
                    (proxy, call, args) -> {
                        Object result;
                        try {
                            result = call.invoke(real, args);
                        } catch (InvocationTargetException failure) {
                            throw failure.getCause();
                        }
                        if (call.getName().equals("executeUpdate") && updates.incrementAndGet() == stage)
                            throw new SQLException("injected write failure after statement " + stage);
                        return result;
                    });
        });
    }

    private static final Object UNHANDLED = new Object();

    @FunctionalInterface
    private interface Intercept {
        Object call(String name, Object[] arguments) throws Throwable;
    }

    private static Connection wrap(Connection connection, Intercept intercept) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(), new Class<?>[] {Connection.class}, (proxy, method, arguments) -> {
                    Object result = intercept.call(method.getName(), arguments);
                    if (result != UNHANDLED) return result;
                    try {
                        return method.invoke(connection, arguments);
                    } catch (InvocationTargetException failure) {
                        throw failure.getCause();
                    }
                });
    }
}
