package com.xzavier0722.mc.plugin.slimefun4.storage.adapter.sqlcommon;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.BlockStorageMigration;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** One-connection, no-DDL migration of existing rows. No Bukkit/world access is performed here. */
final class SqlUniversalBlockMigration {
    private SqlUniversalBlockMigration() {}

    static void execute(Connection connection, String prefix, BlockStorageMigration plan) throws SQLException {
        // Concurrent migrations can encounter serializable conflicts. Retry only a confirmed
        // rollback, never an ambiguous commit, and keep exactly the same immutable snapshot/UUID.
        for (int attempt = 0; ; attempt++) {
            try {
                executeOnce(connection, prefix, plan);
                return;
            } catch (BlockStorageMigration.Failure failure) {
                if (attempt >= 2 || !retryable(connection, failure)) throw failure;
                try {
                    Thread.sleep(10L * (attempt + 1));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    failure.addSuppressed(interrupted);
                    throw failure;
                }
            }
        }
    }

    private static boolean retryable(Connection connection, BlockStorageMigration.Failure failure) {
        if (!failure.isRestagingSafe() || !(failure.getCause() instanceof SQLException sql)) return false;
        if ("40001".equals(sql.getSQLState()) || "40P01".equals(sql.getSQLState())) return true;
        try {
            return connection
                            .getMetaData()
                            .getDatabaseProductName()
                            .toLowerCase(Locale.ROOT)
                            .contains("sqlite")
                    && ((sql.getErrorCode() & 255) == 5 || (sql.getErrorCode() & 255) == 6);
        } catch (SQLException metadataFailure) {
            failure.addSuppressed(metadataFailure);
            return false;
        }
    }

    private static void executeOnce(Connection connection, String prefix, BlockStorageMigration plan)
            throws SQLException {
        Objects.requireNonNull(plan, "plan");
        Tables tables = new Tables(prefix);
        if (!connection.getAutoCommit()) {
            throw new SQLException("Universal migration requires its own transaction, not a caller-owned transaction");
        }
        if (!connection.getMetaData().supportsTransactions()
                || !connection.getMetaData().supportsTransactionIsolationLevel(Connection.TRANSACTION_SERIALIZABLE)) {
            throw new SQLException("Storage cannot provide a serializable universal migration transaction");
        }
        requireTransactionalTables(connection, tables);
        int isolation = connection.getTransactionIsolation();
        boolean begun = false;
        boolean resolved = false;
        boolean commitAttempted = false;
        Throwable failure = null;
        try {
            connection.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            connection.setAutoCommit(false);
            begun = true;
            moveRows(connection, tables, plan);
            commitAttempted = true;
            connection.commit();
            resolved = true;
        } catch (SQLException | RuntimeException | Error cause) {
            boolean rollbackSucceeded = !begun;
            if (begun) {
                try {
                    connection.rollback();
                    resolved = true;
                    rollbackSucceeded = true;
                } catch (SQLException rollbackFailure) {
                    cause.addSuppressed(rollbackFailure);
                    // Never let resetting auto-commit commit a partially failed transaction.
                    try {
                        connection.abort(Runnable::run);
                    } catch (SQLException | RuntimeException abortFailure) {
                        cause.addSuppressed(abortFailure);
                    }
                }
            }
            if (cause instanceof Error error && !(cause instanceof LinkageError)) {
                failure = error;
                throw error;
            }
            var rejected = new BlockStorageMigration.Failure(
                    "Universal migration did not confirm commit [" + plan.location() + " -> " + plan.destination()
                            + "]; retain the source/destination identity and retry after resolving the storage error.",
                    cause,
                    !commitAttempted && rollbackSucceeded);
            failure = rejected;
            throw rejected;
        } finally {
            if (!begun || resolved) {
                try {
                    if (begun) connection.setAutoCommit(true);
                    if (connection.getTransactionIsolation() != isolation)
                        connection.setTransactionIsolation(isolation);
                } catch (SQLException resetFailure) {
                    if (failure != null) {
                        failure.addSuppressed(resetFailure);
                    } else {
                        throw new BlockStorageMigration.Failure(
                                "Migration commit succeeded but connection cleanup failed; verify the same destination on retry",
                                resetFailure,
                                false);
                    }
                }
            }
        }
    }

    private static void moveRows(Connection connection, Tables tables, BlockStorageMigration plan) throws SQLException {
        String source = plan.location();
        String target = plan.destination().toString();
        String[] record = record(connection, tables.blockRecord, "loc", source, "sf_id, chunk");
        if (record == null) {
            // A commit acknowledgement may be lost after the database committed. Do not allocate
            // another UUID or create a second copy: verify the exact already-committed target.
            requireDestination(connection, tables, plan);
            require(
                    sourceData(connection, tables.blockData, "loc", source).isEmpty()
                            && sourceInventory(connection, tables.blockInventory, "loc", source)
                                    .isEmpty(),
                    "Source record is missing but source children remain");
            return;
        }
        require(
                plan.slimefunId().equals(record[0]) && plan.chunk().equals(record[1]),
                "Source block identity changed after migration preflight");
        require(
                plan.sourceData().equals(sourceData(connection, tables.blockData, "loc", source)),
                "Source custom data changed after migration preflight");
        require(
                plan.matchesInventory(sourceInventory(connection, tables.blockInventory, "loc", source)),
                "Source inventory changed after migration preflight");
        require(
                record(connection, tables.universalRecord, "universal_uuid", target, "sf_id, universal_traits") == null
                        && sourceData(connection, tables.universalData, "universal_uuid", target)
                                .isEmpty()
                        && sourceInventory(connection, tables.universalInventory, "universal_uuid", target)
                                .isEmpty(),
                "Destination UUID is already in use; no records were overwritten");

        update(
                connection,
                "INSERT INTO " + tables.universalRecord + " (universal_uuid, sf_id, universal_traits) VALUES (?, ?, ?)",
                1,
                target,
                plan.slimefunId(),
                plan.traits());
        // INSERT SELECT keeps the original SQL payload and its representation unchanged.
        update(
                connection,
                "INSERT INTO " + tables.universalData + " (universal_uuid, data_key, data_val)"
                        + " SELECT ?, data_key, data_val FROM " + tables.blockData + " WHERE loc = ?",
                plan.sourceData().size(),
                target,
                source);
        if (!plan.sourceData().containsKey("location")) {
            update(
                    connection,
                    "INSERT INTO " + tables.universalData + " (universal_uuid, data_key, data_val) VALUES (?, ?, ?)",
                    1,
                    target,
                    "location",
                    plan.destinationData().get("location"));
        }
        update(
                connection,
                "INSERT INTO " + tables.universalInventory + " (universal_uuid, i_slot, i_item)"
                        + " SELECT ?, i_slot, i_item FROM " + tables.blockInventory + " WHERE loc = ?",
                plan.inventorySize(),
                target,
                source);
        requireDestination(connection, tables, plan);

        // Explicit child removal also works when an old SQLite connection did not enable FK cascades.
        update(connection, "DELETE FROM " + tables.blockInventory + " WHERE loc = ?", plan.inventorySize(), source);
        update(
                connection,
                "DELETE FROM " + tables.blockData + " WHERE loc = ?",
                plan.sourceData().size(),
                source);
        update(
                connection,
                "DELETE FROM " + tables.blockRecord + " WHERE loc = ? AND sf_id = ? AND chunk = ?",
                1,
                source,
                plan.slimefunId(),
                plan.chunk());
        require(
                record(connection, tables.blockRecord, "loc", source, "sf_id, chunk") == null
                        && sourceData(connection, tables.blockData, "loc", source)
                                .isEmpty()
                        && sourceInventory(connection, tables.blockInventory, "loc", source)
                                .isEmpty(),
                "Source deletion did not remove exactly the migrated inventory");
        requireDestination(connection, tables, plan);
    }

    private static void requireDestination(Connection connection, Tables tables, BlockStorageMigration plan)
            throws SQLException {
        String target = plan.destination().toString();
        String[] destination =
                record(connection, tables.universalRecord, "universal_uuid", target, "sf_id, universal_traits");
        require(
                destination != null
                        && plan.slimefunId().equals(destination[0])
                        && plan.traits().equals(destination[1]),
                "Destination record does not match the staged migration");
        require(
                plan.destinationData().equals(sourceData(connection, tables.universalData, "universal_uuid", target)),
                "Destination custom data does not match the staged migration");
        require(
                plan.matchesInventory(sourceInventory(connection, tables.universalInventory, "universal_uuid", target)),
                "Destination inventory does not match the staged migration");
    }

    private static String[] record(
            Connection connection, String table, String ownerColumn, String owner, String columns) throws SQLException {
        try (var statement = select(connection, table, ownerColumn, owner, columns);
                var result = statement.executeQuery()) {
            if (!result.next()) return null;
            var record = new String[] {result.getString(1), result.getString(2)};
            require(!result.next(), "Stored record identity is duplicated");
            return record;
        }
    }

    private static Map<String, String> sourceData(Connection connection, String table, String ownerColumn, String owner)
            throws SQLException {
        Map<String, String> values = new HashMap<>();
        try (var statement = select(connection, table, ownerColumn, owner, "data_key, data_val");
                var result = statement.executeQuery()) {
            while (result.next()) {
                String key = result.getString(1);
                String value = result.getString(2);
                require(key != null && value != null && !values.containsKey(key), "Invalid or duplicated custom data");
                values.put(key, value);
            }
        }
        return values;
    }

    private static Map<Integer, byte[]> sourceInventory(
            Connection connection, String table, String ownerColumn, String owner) throws SQLException {
        Map<Integer, byte[]> items = new HashMap<>();
        try (var statement = select(connection, table, ownerColumn, owner, "i_slot, i_item");
                var result = statement.executeQuery()) {
            while (result.next()) {
                int slot = Integer.parseInt(result.getString(1));
                require(slot >= 0 && slot < 54 && !items.containsKey(slot), "Invalid or duplicated inventory slot");
                items.put(slot, result.getBytes(2));
            }
        }
        return items;
    }

    private static PreparedStatement select(
            Connection connection, String table, String ownerColumn, String owner, String columns) throws SQLException {
        var statement =
                connection.prepareStatement("SELECT " + columns + " FROM " + table + " WHERE " + ownerColumn + " = ?");
        try {
            statement.setString(1, owner);
            return statement;
        } catch (SQLException | RuntimeException failure) {
            try {
                statement.close();
            } catch (SQLException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    private static void update(Connection connection, String sql, int expected, String... values) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setString(i + 1, values[i]);
            require(statement.executeUpdate() == expected, "Migration affected an unexpected number of records");
        }
    }

    private static void requireTransactionalTables(Connection connection, Tables tables) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
        if (product.contains("mysql") || product.contains("mariadb")) {
            try (var statement = connection.prepareStatement(
                    "SELECT table_name, engine FROM information_schema.tables WHERE table_schema = DATABASE()"
                            + " AND table_name IN (?, ?, ?, ?, ?, ?)")) {
                List<String> names = tables.names();
                for (int i = 0; i < names.size(); i++) statement.setString(i + 1, names.get(i));
                int found = 0;
                try (var rows = statement.executeQuery()) {
                    while (rows.next()) {
                        require(
                                "InnoDB".equalsIgnoreCase(rows.getString(2)),
                                "Universal migration requires transactional InnoDB storage for every involved table");
                        found++;
                    }
                }
                require(found == 6, "Universal migration tables could not all be verified");
            }
        } else {
            require(
                    product.contains("sqlite") || product.contains("postgresql"),
                    "No atomic universal migration contract is defined for this database");
        }
    }

    private static void require(boolean valid, String message) throws SQLException {
        if (!valid) throw new SQLException(message);
    }

    private static final class Tables {
        private final String blockRecord, blockData, blockInventory, universalRecord, universalData, universalInventory;

        private Tables(String prefix) {
            if (prefix == null || !prefix.matches("[A-Za-z0-9_]*")) {
                throw new IllegalArgumentException("Unsafe or unsupported SQL table prefix for universal migration");
            }
            blockRecord = prefix + "block_record";
            blockData = prefix + "block_data";
            blockInventory = prefix + "block_inventory";
            universalRecord = prefix + "universal_record";
            universalData = prefix + "universal_data";
            universalInventory = prefix + "universal_inventory";
        }

        private List<String> names() {
            return List.of(blockRecord, blockData, blockInventory, universalRecord, universalData, universalInventory);
        }
    }
}
