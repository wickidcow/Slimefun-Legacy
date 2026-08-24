package com.xzavier0722.mc.plugin.slimefun4.storage.adapter.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.xzavier0722.mc.plugin.slimefun4.storage.adapter.sqlcommon.SqlUtils;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.FieldKey;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteBlockInventoryCascadeTest {

    @TempDir
    Path tempDir;

    @Test
    void deletingAndRecreatingBlockDoesNotResurrectInventory() throws Exception {
        try (Connection connection = openDatabase("delete-recreate.db")) {
            createBlockStorageSchema(connection);

            String location = "world;10:64:-3";
            insertBlock(connection, location, "world;0:-1", "TEST_MACHINE");
            insertBlockData(connection, location, "owner", "player");
            insertBlockInventory(connection, location, 0, new byte[] {1, 2, 3});

            assertEquals(1, countRows(connection, DataScope.BLOCK_DATA, location));
            assertEquals(1, countRows(connection, DataScope.BLOCK_INVENTORY, location));

            deleteBlock(connection, location);

            assertEquals(0, countRows(connection, DataScope.BLOCK_DATA, location));
            assertEquals(0, countRows(connection, DataScope.BLOCK_INVENTORY, location));

            insertBlock(connection, location, "world;0:-1", "REPLACEMENT_MACHINE");

            assertEquals(0, countRows(connection, DataScope.BLOCK_DATA, location));
            assertEquals(0, countRows(connection, DataScope.BLOCK_INVENTORY, location));
        }
    }

    @Test
    void movingBlockRecordCascadesInventoryWithoutDuplication() throws Exception {
        try (Connection connection = openDatabase("move.db")) {
            createBlockStorageSchema(connection);

            String oldLocation = "world;10:64:-3";
            String newLocation = "world;11:64:-3";
            insertBlock(connection, oldLocation, "world;0:-1", "TEST_MACHINE");
            insertBlockData(connection, oldLocation, "mode", "round-robin");
            insertBlockInventory(connection, oldLocation, 4, new byte[] {4, 5, 6});

            updateBlockLocation(connection, oldLocation, newLocation);

            assertEquals(0, countRows(connection, DataScope.BLOCK_DATA, oldLocation));
            assertEquals(0, countRows(connection, DataScope.BLOCK_INVENTORY, oldLocation));
            assertEquals(1, countRows(connection, DataScope.BLOCK_DATA, newLocation));
            assertEquals(1, countRows(connection, DataScope.BLOCK_INVENTORY, newLocation));
        }
    }

    private Connection openDatabase(String name) throws Exception {
        var config = new SqliteConfig(tempDir.resolve(name).toString(), 1);
        Connection connection = DriverManager.getConnection(config.jdbcUrl());

        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("PRAGMA foreign_keys;")) {
            assertEquals(1, result.getInt(1));
        }

        return connection;
    }

    private static void createBlockStorageSchema(Connection connection) throws Exception {
        for (String sql : captureBlockStorageSchema()) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(sql);
            }
        }
    }

    private static List<String> captureBlockStorageSchema() throws Exception {
        var adapter = new RecordingSqliteAdapter();
        Method method = SqliteAdapter.class.getDeclaredMethod("createBlockStorageTables");
        method.setAccessible(true);
        method.invoke(adapter);
        return adapter.statements;
    }

    private static void insertBlock(Connection connection, String location, String chunk, String slimefunId)
            throws Exception {
        String sql = "INSERT INTO "
                + SqlUtils.mapTable(DataScope.BLOCK_RECORD)
                + " ("
                + SqlUtils.mapField(FieldKey.LOCATION)
                + ", "
                + SqlUtils.mapField(FieldKey.CHUNK)
                + ", "
                + SqlUtils.mapField(FieldKey.SLIMEFUN_ID)
                + ") VALUES (?, ?, ?);";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, location);
            statement.setString(2, chunk);
            statement.setString(3, slimefunId);
            statement.executeUpdate();
        }
    }

    private static void insertBlockData(Connection connection, String location, String key, String value)
            throws Exception {
        String sql = "INSERT INTO "
                + SqlUtils.mapTable(DataScope.BLOCK_DATA)
                + " ("
                + SqlUtils.mapField(FieldKey.LOCATION)
                + ", "
                + SqlUtils.mapField(FieldKey.DATA_KEY)
                + ", "
                + SqlUtils.mapField(FieldKey.DATA_VALUE)
                + ") VALUES (?, ?, ?);";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, location);
            statement.setString(2, key);
            statement.setString(3, value);
            statement.executeUpdate();
        }
    }

    private static void insertBlockInventory(Connection connection, String location, int slot, byte[] item)
            throws Exception {
        String sql = "INSERT INTO "
                + SqlUtils.mapTable(DataScope.BLOCK_INVENTORY)
                + " ("
                + SqlUtils.mapField(FieldKey.LOCATION)
                + ", "
                + SqlUtils.mapField(FieldKey.INVENTORY_SLOT)
                + ", "
                + SqlUtils.mapField(FieldKey.INVENTORY_ITEM)
                + ") VALUES (?, ?, ?);";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, location);
            statement.setInt(2, slot);
            statement.setBytes(3, item);
            statement.executeUpdate();
        }
    }

    private static void deleteBlock(Connection connection, String location) throws Exception {
        String sql = "DELETE FROM "
                + SqlUtils.mapTable(DataScope.BLOCK_RECORD)
                + " WHERE "
                + SqlUtils.mapField(FieldKey.LOCATION)
                + " = ?;";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, location);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void updateBlockLocation(Connection connection, String oldLocation, String newLocation)
            throws Exception {
        String sql = "UPDATE "
                + SqlUtils.mapTable(DataScope.BLOCK_RECORD)
                + " SET "
                + SqlUtils.mapField(FieldKey.LOCATION)
                + " = ? WHERE "
                + SqlUtils.mapField(FieldKey.LOCATION)
                + " = ?;";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, newLocation);
            statement.setString(2, oldLocation);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static int countRows(Connection connection, DataScope scope, String location) throws Exception {
        String sql = "SELECT COUNT(*) FROM "
                + SqlUtils.mapTable(scope)
                + " WHERE "
                + SqlUtils.mapField(FieldKey.LOCATION)
                + " = ?;";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, location);
            try (ResultSet result = statement.executeQuery()) {
                return result.getInt(1);
            }
        }
    }

    private static final class RecordingSqliteAdapter extends SqliteAdapter {
        private final List<String> statements = new ArrayList<>();

        @Override
        public synchronized void executeSql(String sql) {
            statements.add(sql);
        }
    }
}
