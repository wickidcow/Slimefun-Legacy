package com.xzavier0722.mc.plugin.slimefun4.storage.patch;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xzavier0722.mc.plugin.slimefun4.storage.adapter.sqlite.SqliteConfig;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.ItemStackDataCodec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Exercises the real existing migration on disposable SQLite files, not a production world. */
class ItemStackEnvelopeMigrationTest {
    private static final Logger LOGGER = Logger.getLogger(ItemStackEnvelopeMigrationTest.class.getName());
    private static final List<Table> TABLES = List.of(
            new Table("backpack_inventory", "b_id", "11111111-2222-3333-4444-555555555555"),
            new Table("block_inventory", "loc", "old_world;-321:64:43613"),
            new Table("universal_inventory", "universal_uuid", "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"));

    @TempDir
    Path directory;

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        Class.forName("org.sqlite.JDBC");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void migratesMixedHistoricalRepresentationsAcrossAllInventoryScopesAndReopensLosslessly() throws Exception {
        Path database = directory.resolve("old-inventories.db");
        String jdbc = "jdbc:sqlite:" + database;
        var expected = item();
        byte[] nativeBytes = ItemStackDataCodec.serialize(expected);
        String textEnvelope = Base64.getEncoder().encodeToString(nativeBytes);
        String oldObjectStream = legacyText(expected);
        var config = new SqliteConfig(database.toString(), 1);
        var patch = new DatabasePatchV3(() -> LOGGER);
        Map<String, byte[]> firstMigration;

        try (var connection = DriverManager.getConnection(jdbc)) {
            for (Table table : TABLES) {
                createTable(connection, table);
                insert(connection, table, 3, oldObjectStream);
                insert(connection, table, 17, textEnvelope);
                insert(connection, table, 26, textEnvelope.getBytes(StandardCharsets.US_ASCII));
                insert(connection, table, 53, nativeBytes);
            }
            try (var statement = connection.createStatement()) {
                patch.patch(statement, config);
            }
            firstMigration = verifyRows(connection, expected);
            assertEquals(12, firstMigration.size());
            for (Table table : TABLES) {
                assertArrayEquals(nativeBytes, firstMigration.get(table.name() + ":53"),
                        "Already-current item bytes must not be rewritten");
            }
        }

        // Close/reopen the actual file and retry the same migration. Stable keys,
        // slot numbers, amounts, metadata and all encoded payloads must survive.
        try (var connection = DriverManager.getConnection(jdbc); var statement = connection.createStatement()) {
            patch.patch(statement, config);
            Map<String, byte[]> secondMigration = verifyRows(connection, expected);
            assertEquals(firstMigration.keySet(), secondMigration.keySet());
            for (String identity : firstMigration.keySet()) {
                assertArrayEquals(firstMigration.get(identity), secondMigration.get(identity));
            }
        }
    }

    @Test
    void malformedTextEnvelopesRemainStoredForRepairInsteadOfBeingErased() throws Exception {
        for (Table table : TABLES) {
            try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                    var statement = connection.createStatement()) {
                createTable(connection, table);
                String malformed = Base64.getEncoder().encodeToString(new byte[] {'S', 'F', '2', 0});
                insert(connection, table, 17, malformed);
                var patch = new DatabasePatchV3(() -> LOGGER);
                assertThrows(SQLException.class, () -> patch.patch(statement, new SqliteConfig(":memory:", 1)));
                try (var rows = statement.executeQuery("SELECT " + table.ownerField() + ", i_slot, i_item, typeof(i_item) FROM " + table.name())) {
                    assertTrue(rows.next());
                    assertEquals(table.owner(), rows.getString(1));
                    assertEquals(17, rows.getInt(2));
                    assertEquals(malformed, rows.getString(3));
                    assertEquals("text", rows.getString(4));
                    assertEquals(false, rows.next());
                }
            }
        }
    }

    @Test
    void callerTransactionCanRollBackEarlierConversionsAfterALaterMalformedRecord() throws Exception {
        Table table = TABLES.getFirst();
        try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
                var statement = connection.createStatement()) {
            createTable(connection, table);
            String valid = Base64.getEncoder().encodeToString(ItemStackDataCodec.serialize(item()));
            String malformed = Base64.getEncoder().encodeToString(new byte[] {'S', 'F', '2', 0});
            insert(connection, table, 3, valid);
            insert(connection, table, 17, malformed);
            connection.setAutoCommit(false);
            var patch = new DatabasePatchV3(() -> LOGGER);
            assertThrows(SQLException.class, () -> patch.patch(statement, new SqliteConfig(":memory:", 1)));
            connection.rollback();
            try (var rows = statement.executeQuery("SELECT i_slot, i_item, typeof(i_item) FROM backpack_inventory ORDER BY i_slot")) {
                assertTrue(rows.next());
                assertEquals(3, rows.getInt(1));
                assertEquals(valid, rows.getString(2));
                assertEquals("text", rows.getString(3));
                assertTrue(rows.next());
                assertEquals(17, rows.getInt(1));
                assertEquals(malformed, rows.getString(2));
                assertEquals("text", rows.getString(3));
                assertEquals(false, rows.next());
            }
        }
    }

    private static Map<String, byte[]> verifyRows(Connection connection, ItemStack expected) throws Exception {
        Map<String, byte[]> saved = new HashMap<>();
        for (Table table : TABLES) {
            try (var statement = connection.createStatement();
                    var rows = statement.executeQuery("SELECT " + table.ownerField() + ", i_slot, i_item, typeof(i_item) FROM " + table.name() + " ORDER BY i_slot")) {
                int count = 0;
                while (rows.next()) {
                    assertEquals(table.owner(), rows.getString(1));
                    int slot = rows.getInt(2);
                    assertTrue(List.of(3, 17, 26, 53).contains(slot));
                    assertEquals("blob", rows.getString(4));
                    byte[] stored = rows.getBytes(3);
                    var actual = ItemStackDataCodec.deserialize(stored);
                    assertNotNull(actual);
                    assertEquals(expected.getType(), actual.getType());
                    assertEquals(expected.getAmount(), actual.getAmount());
                    assertEquals(expected.getItemMeta(), actual.getItemMeta());
                    saved.put(table.name() + ":" + slot, stored);
                    count++;
                }
                assertEquals(4, count);
            }
        }
        return saved;
    }

    private static ItemStack item() {
        var item = new ItemStack(Material.COAL, 37);
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(java.util.Objects.requireNonNull(NamespacedKey.fromString("slimefun:slimefun_item")),
                PersistentDataType.STRING, "COMPRESSED_CARBON");
        meta.getPersistentDataContainer().set(java.util.Objects.requireNonNull(NamespacedKey.fromString("legacyaddon:stored_count")),
                PersistentDataType.LONG, 9_000_000_001L);
        item.setItemMeta(meta);
        return item;
    }

    private static void createTable(Connection connection, Table table) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE " + table.name() + " (" + table.ownerField()
                    + " TEXT NOT NULL, i_slot INTEGER NOT NULL, i_item TEXT NOT NULL, PRIMARY KEY ("
                    + table.ownerField() + ", i_slot))");
        }
    }

    private static void insert(Connection connection, Table table, int slot, Object stored) throws SQLException {
        try (var statement = connection.prepareStatement("INSERT INTO " + table.name() + " VALUES (?, ?, ?)")) {
            statement.setString(1, table.owner());
            statement.setInt(2, slot);
            if (stored instanceof byte[] bytes) {
                statement.setBytes(3, bytes);
            } else {
                statement.setString(3, (String) stored);
            }
            statement.executeUpdate();
        }
    }

    @SuppressWarnings("deprecation") // Historical fixture encoding only; no new production object-stream writer.
    private static String legacyText(ItemStack item) throws Exception {
        try (var bytes = new ByteArrayOutputStream(); var output = new BukkitObjectOutputStream(bytes)) {
            output.writeObject(item);
            output.flush();
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        }
    }

    private record Table(String name, String ownerField, String owner) {}
}
