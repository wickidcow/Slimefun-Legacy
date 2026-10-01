package com.xzavier0722.mc.plugin.slimefun4.storage.adapter.sqlcommon;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.BlockStorageMigration;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Opaque format-contract values, not claimed player-world captures. */
final class SqlMigrationFixtures {
    static final String SOURCE = "old_'world;10:64:-20";
    static final String CHUNK = "old_'world;0:-2";
    static final String ID = "EXISTING_MACHINE";
    static final UUID DESTINATION = UUID.fromString("11111111-2222-4333-8444-555555555555");
    static final List<String> TABLES = List.of(
            "block_record",
            "block_data",
            "block_inventory",
            "universal_record",
            "universal_data",
            "universal_inventory");

    private SqlMigrationFixtures() {}

    static BlockStorageMigration plan() {
        return new BlockStorageMigration(
                SOURCE,
                CHUNK,
                ID,
                DESTINATION,
                SOURCE,
                Map.of(
                        "owner_uuid",
                        "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
                        "opaque:key",
                        "old \"quoted\" data\\line\nvalue"),
                Map.of(
                        0,
                        new byte[] {'S', 'F', '2', 0, 1, -1, 42},
                        8,
                        new byte[0],
                        17,
                        "historical-text-envelope".getBytes(StandardCharsets.US_ASCII),
                        53,
                        new byte[] {127, 0, -1, 37}));
    }

    static void create(Connection connection, String prefix) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName();
        String binary = product.equalsIgnoreCase("PostgreSQL") ? "BYTEA" : "BLOB";
        String engine =
                product.equalsIgnoreCase("MySQL") || product.equalsIgnoreCase("MariaDB") ? " ENGINE=InnoDB" : "";
        if (product.equalsIgnoreCase("SQLite")) {
            try (var statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA foreign_keys=ON");
                statement.execute("PRAGMA busy_timeout=5000");
            }
        }
        List<String> ddl = List.of(
                "block_record (loc VARCHAR(255) PRIMARY KEY, chunk VARCHAR(255) NOT NULL, sf_id VARCHAR(255) NOT NULL)",
                "block_data (loc VARCHAR(255), data_key VARCHAR(100), data_val TEXT NOT NULL, PRIMARY KEY(loc,data_key), FOREIGN KEY(loc) REFERENCES "
                        + prefix + "block_record(loc) ON DELETE CASCADE)",
                "block_inventory (loc VARCHAR(255), i_slot INTEGER, i_item " + binary
                        + " NOT NULL, PRIMARY KEY(loc,i_slot), FOREIGN KEY(loc) REFERENCES " + prefix
                        + "block_record(loc) ON DELETE CASCADE)",
                "universal_record (universal_uuid VARCHAR(36) PRIMARY KEY, sf_id VARCHAR(255) NOT NULL, universal_traits VARCHAR(100) NOT NULL)",
                "universal_data (universal_uuid VARCHAR(36), data_key VARCHAR(100), data_val TEXT NOT NULL, PRIMARY KEY(universal_uuid,data_key), FOREIGN KEY(universal_uuid) REFERENCES "
                        + prefix + "universal_record(universal_uuid) ON DELETE CASCADE)",
                "universal_inventory (universal_uuid VARCHAR(36), i_slot INTEGER, i_item " + binary
                        + " NOT NULL, PRIMARY KEY(universal_uuid,i_slot), FOREIGN KEY(universal_uuid) REFERENCES "
                        + prefix + "universal_record(universal_uuid) ON DELETE CASCADE)");
        try (var statement = connection.createStatement()) {
            for (String sql : ddl) statement.execute("CREATE TABLE " + prefix + sql + engine);
        }
        BlockStorageMigration plan = plan();
        execute(connection, "INSERT INTO " + prefix + "block_record VALUES (?, ?, ?)", SOURCE, CHUNK, ID);
        execute(
                connection,
                "INSERT INTO " + prefix + "block_record VALUES (?, ?, ?)",
                "unrelated;11:64:-20",
                CHUNK,
                "OTHER_MACHINE");
        for (var entry : plan.sourceData().entrySet()) {
            execute(
                    connection,
                    "INSERT INTO " + prefix + "block_data VALUES (?, ?, ?)",
                    SOURCE,
                    entry.getKey(),
                    entry.getValue());
        }
        for (var entry : plan.inventory().entrySet()) {
            try (var statement =
                    connection.prepareStatement("INSERT INTO " + prefix + "block_inventory VALUES (?, ?, ?)")) {
                statement.setString(1, SOURCE);
                statement.setInt(2, entry.getKey());
                statement.setBytes(3, entry.getValue());
                statement.executeUpdate();
            }
        }
    }

    static void execute(Connection connection, String sql, String... values) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setString(i + 1, values[i]);
            statement.executeUpdate();
        }
    }

    static Map<String, String> dump(Connection connection, String prefix) throws SQLException {
        Map<String, String> result = new TreeMap<>();
        for (String table : TABLES) {
            try (var statement = connection.createStatement();
                    var rows = statement.executeQuery("SELECT * FROM " + prefix + table)) {
                int count = rows.getMetaData().getColumnCount();
                while (rows.next()) {
                    StringBuilder identity = new StringBuilder(table);
                    for (int column = 1; column <= Math.min(2, count); column++)
                        identity.append('|').append(rows.getString(column));
                    StringBuilder data = new StringBuilder();
                    for (int column = 1; column <= count; column++) {
                        byte[] bytes = rows.getBytes(column);
                        data.append(bytes == null ? "NULL" : Base64.getEncoder().encodeToString(bytes))
                                .append(';');
                    }
                    result.put(identity.toString(), data.toString());
                }
            }
        }
        return result;
    }

    static Map<Integer, byte[]> inventory(Connection connection, String prefix, String table, String owner)
            throws SQLException {
        Map<Integer, byte[]> result = new LinkedHashMap<>();
        String ownerColumn = table.startsWith("universal") ? "universal_uuid" : "loc";
        try (var statement = connection.prepareStatement(
                "SELECT i_slot, i_item FROM " + prefix + table + " WHERE " + ownerColumn + " = ?")) {
            statement.setString(1, owner);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) result.put(rows.getInt(1), rows.getBytes(2));
            }
        }
        return result;
    }

    static int count(Connection connection, String table) throws SQLException {
        try (var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rows.next();
            return rows.getInt(1);
        }
    }
}
