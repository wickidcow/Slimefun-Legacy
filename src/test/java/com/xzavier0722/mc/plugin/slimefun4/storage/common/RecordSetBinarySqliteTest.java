package com.xzavier0722.mc.plugin.slimefun4.storage.common;

import static org.junit.jupiter.api.Assertions.*;

import com.xzavier0722.mc.plugin.slimefun4.storage.adapter.sqlcommon.SqlUtils;
import com.xzavier0722.mc.plugin.slimefun4.storage.adapter.sqlite.SqliteAdapter;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** Real adapter query rendering and JDBC; only connection/profiler transport is replaced. */
class RecordSetBinarySqliteTest {
    @TempDir
    Path directory;

    static Stream<Arguments> stores() {
        return Stream.of(
                Arguments.of(DataScope.BLOCK_INVENTORY, FieldKey.LOCATION, false),
                Arguments.of(DataScope.BLOCK_INVENTORY, FieldKey.LOCATION, true),
                Arguments.of(DataScope.BACKPACK_INVENTORY, FieldKey.BACKPACK_ID, false),
                Arguments.of(DataScope.BACKPACK_INVENTORY, FieldKey.BACKPACK_ID, true),
                Arguments.of(DataScope.UNIVERSAL_INVENTORY, FieldKey.UNIVERSAL_UUID, false),
                Arguments.of(DataScope.UNIVERSAL_INVENTORY, FieldKey.UNIVERSAL_UUID, true));
    }

    @ParameterizedTest(name = "{0}, mapExport={2}")
    @MethodSource("stores")
    void externalMutationCannotChangeTheBytesWrittenOrReadAfterReopening(
            DataScope scope, FieldKey ownerField, boolean mapExport) throws Exception {
        String url = "jdbc:sqlite:" + directory.resolve(scope.name() + "-" + mapExport + ".db");
        byte[] expected = {83, 70, 50, 0, -1, 17, 42};
        String owner = "f55799ac-2dd7-42f0-91a2-123456abcdef";
        var key = new RecordKey(scope);
        key.addCondition(ownerField, owner);
        key.addCondition(FieldKey.INVENTORY_SLOT, "3");
        try (var adapter = new DirectJdbcAdapter(DriverManager.getConnection(url))) {
            adapter.createTable(scope, ownerField);
            var record = new RecordSet();
            record.put(ownerField, owner);
            record.put(FieldKey.INVENTORY_SLOT, "3");
            record.put(FieldKey.INVENTORY_ITEM, expected);
            record.readonly();
            byte[] exported = (byte[]) (mapExport
                    ? record.getAllValues().get(FieldKey.INVENTORY_ITEM)
                    : record.getValue(FieldKey.INVENTORY_ITEM));
            Arrays.fill(exported, (byte) 99);
            // The actual production setData path consumes this RecordSet through both map/key APIs.
            adapter.setData(key, record);
        }
        try (var adapter = new DirectJdbcAdapter(DriverManager.getConnection(url))) {
            List<RecordSet> rows = adapter.getData(key, false);
            assertEquals(1, rows.size());
            var loaded = rows.getFirst();
            assertEquals(owner, loaded.getString(ownerField));
            assertEquals(3, loaded.getInt(FieldKey.INVENTORY_SLOT));
            assertArrayEquals(expected, (byte[]) loaded.getValue(FieldKey.INVENTORY_ITEM));
            assertThrows(IllegalStateException.class, () -> loaded.put(FieldKey.INVENTORY_SLOT, "4"));
            byte[] exported = (byte[]) loaded.getAllValues().entrySet().stream()
                    .filter(entry -> entry.getKey() == FieldKey.INVENTORY_ITEM)
                    .findFirst().orElseThrow().getValue();
            Arrays.fill(exported, (byte) 0);
            assertArrayEquals(expected, (byte[]) loaded.getValue(FieldKey.INVENTORY_ITEM));
            assertArrayEquals(expected, (byte[]) adapter.getData(key, false).getFirst().getValue(FieldKey.INVENTORY_ITEM));
        }
    }

    private static final class DirectJdbcAdapter extends SqliteAdapter implements AutoCloseable {
        private final Connection connection;

        private DirectJdbcAdapter(Connection connection) {
            this.connection = connection;
        }

        private void createTable(DataScope scope, FieldKey ownerField) {
            executeSql("CREATE TABLE " + SqlUtils.mapTable(scope) + " ("
                    + SqlUtils.mapField(ownerField) + " TEXT NOT NULL, "
                    + SqlUtils.mapField(FieldKey.INVENTORY_SLOT) + " INTEGER NOT NULL, "
                    + SqlUtils.mapField(FieldKey.INVENTORY_ITEM) + " BLOB NOT NULL, PRIMARY KEY ("
                    + SqlUtils.mapField(ownerField) + ", " + SqlUtils.mapField(FieldKey.INVENTORY_SLOT) + "))");
        }

        @Override
        public void executeSql(String sql) {
            try (var statement = connection.createStatement()) {
                statement.execute(sql);
            } catch (SQLException failure) {
                throw new IllegalStateException("Fixture SQL failed: " + sql, failure);
            }
        }

        @Override
        protected List<RecordSet> executeQuery(String sql) {
            try {
                return SqlUtils.execQuery(connection, sql);
            } catch (SQLException failure) {
                throw new IllegalStateException("Fixture query failed: " + sql, failure);
            }
        }

        @Override
        public void close() throws SQLException {
            connection.close();
        }
    }
}
