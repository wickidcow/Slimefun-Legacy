package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import static org.junit.jupiter.api.Assertions.*;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.FieldKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordSet;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.ScopeKey;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/** Actual profile/controller records, with captured submissions and an explicit SQLite foreign-key fixture. */
class ProfileSourceNameTest {
    private InventoryReadTestPlugin fixture;
    private RecordingProfiles profiles;
    private ServerMock server;

    @TempDir
    Path directory;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        fixture = new InventoryReadTestPlugin(server);
        profiles = new RecordingProfiles();
    }

    @AfterEach
    void tearDown() {
        try {
            if (fixture != null) fixture.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void keepsTheSuppliedNameWhenUuidLookupDoesNotKnowTheMachinePlayer() {
        var source = source(UUID.randomUUID(), "SE_Butcher");
        var profile = profiles.createProfile(source);
        assertNotEquals(
                source.getName(), profile.getOwner().getName(), "Fixture must reproduce the separate UUID-only lookup");
        assertEquals("SE_Butcher", profiles.writes.getFirst().data().getString(FieldKey.PLAYER_NAME));
        assertEquals(source.getUniqueId(), profile.getUUID());
        assertSame(profile, Slimefun.getRegistry().getPlayerProfiles().get(source.getUniqueId()));
    }

    @Test
    void parentRowAndResearchSurviveReopeningWithoutRenamingTheOwner() throws Exception {
        var owner = UUID.randomUUID();
        var source = source(owner, "SE_Butcher");
        profiles.createProfile(source);
        profiles.setResearch(owner.toString(), new NamespacedKey("slimefun", "walking_sticks"), true);
        assertEquals(
                List.of(DataScope.PLAYER_PROFILE, DataScope.PLAYER_RESEARCH),
                profiles.writes.stream().map(write -> write.key().getScope()).toList());
        try (Connection database = open()) {
            schema(database);
            for (Write write : profiles.writes) persist(database, write);
            assertRows(database, owner, "SE_Butcher");
        }
        try (Connection database = open()) {
            assertRows(database, owner, "SE_Butcher");
        }
    }

    @Test
    void cachedCreationDoesNotOverwriteExistingIdentityOrIssueAnotherWrite() {
        UUID owner = UUID.randomUUID();
        var first = profiles.createProfile(source(owner, "OriginalName"));
        var second = profiles.createProfile(source(owner, "OtherName"));
        assertSame(first, second);
        assertEquals(1, profiles.writes.size());
        assertEquals("OriginalName", profiles.writes.getFirst().data().getString(FieldKey.PLAYER_NAME));
    }

    @Test
    void ownersWithTheSameNameKeepSeparateUuidRecords() {
        var first = profiles.createProfile(source(UUID.randomUUID(), "SE_Butcher"));
        var second = profiles.createProfile(source(UUID.randomUUID(), "SE_Butcher"));
        assertNotEquals(first.getUUID(), second.getUUID());
        assertNotSame(first, second);
        assertEquals(2, profiles.writes.size());
        assertNotEquals(
                profiles.writes.get(0).data().getString(FieldKey.PLAYER_UUID),
                profiles.writes.get(1).data().getString(FieldKey.PLAYER_UUID));
    }

    @Test
    void preservesExactSuppliedNamesWithoutNormalizationOrInventedFallbacks() {
        for (String name : List.of("a", "_Mixed_Case123456", "Owner Name", "")) {
            var source = source(UUID.randomUUID(), name);
            profiles.createProfile(source);
            assertEquals(name, profiles.writes.getLast().data().getString(FieldKey.PLAYER_NAME));
            assertEquals(name, source.getName());
        }
    }

    @Test
    void missingSuppliedNameRetainsTheExistingOwnerLookupBehavior() {
        // Unknown UUID lookups in MockBukkit create a differently named mock on each call.
        // A registered owner makes the existing lookup path deterministic without inventing a fallback.
        var knownOwner = server.addPlayer();
        var profile = profiles.createProfile(source(knownOwner.getUniqueId(), null));
        assertEquals(knownOwner.getName(), profiles.writes.getFirst().data().getString(FieldKey.PLAYER_NAME));
        assertEquals(knownOwner.getUniqueId(), profile.getUUID());
    }

    @Test
    void registrationAndSchedulingContractsStayIntact() {
        var owner = UUID.randomUUID();
        var profile = profiles.createProfile(source(owner, "ExistingOwner"));
        Write write = profiles.writes.getFirst();
        assertEquals(DataScope.PLAYER_PROFILE, write.key().getScope());
        assertTrue(write.forceScopeKey());
        assertEquals(owner.toString(), write.data().getString(FieldKey.PLAYER_UUID));
        assertEquals(0, write.data().getInt(FieldKey.PLAYER_BACKPACK_NUM));
        assertEquals(0, profile.getBackpackCount());
        assertTrue(profile.getResearches().isEmpty());
        assertTrue(profile.getWaypoints().isEmpty());
    }

    @Test
    void sqliteControlProvesNullNameCannotSupportAResearchChildRow() throws Exception {
        UUID owner = UUID.randomUUID();
        try (Connection database = open()) {
            schema(database);
            var data = new RecordSet();
            data.put(FieldKey.PLAYER_UUID, owner.toString());
            data.put(FieldKey.PLAYER_NAME, (String) null);
            data.put(FieldKey.PLAYER_BACKPACK_NUM, "0");
            var key = new RecordKey(DataScope.PLAYER_PROFILE);
            persist(database, new Write(null, key, data, true));
            var research = new RecordSet();
            research.put(FieldKey.PLAYER_UUID, owner.toString());
            research.put(FieldKey.RESEARCH_ID, "slimefun:walking_sticks");
            assertThrows(
                    SQLException.class,
                    () -> persist(
                            database, new Write(null, new RecordKey(DataScope.PLAYER_RESEARCH), research, false)));
            try (var query = database.createStatement();
                    var rows = query.executeQuery("SELECT count(*) FROM player_profile")) {
                assertTrue(rows.next());
                assertEquals(0, rows.getInt(1));
            }
        }
    }

    private static OfflinePlayer source(UUID uuid, String name) {
        return (OfflinePlayer) Proxy.newProxyInstance(
                OfflinePlayer.class.getClassLoader(),
                new Class<?>[] {OfflinePlayer.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUniqueId" -> uuid;
                    case "getName" -> name;
                    case "toString" -> "Profile source " + uuid;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.toString());
                });
    }

    private Connection open() throws SQLException {
        var database = DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("profiles.db"));
        try (var statement = database.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return database;
    }

    private static void schema(Connection database) throws SQLException {
        try (var statement = database.createStatement()) {
            statement.execute(
                    "CREATE TABLE player_profile(p_uuid TEXT PRIMARY KEY NOT NULL, p_name TEXT NOT NULL, b_num INTEGER DEFAULT 0)");
            statement.execute(
                    "CREATE TABLE player_research(p_uuid TEXT NOT NULL, research_id TEXT NOT NULL, FOREIGN KEY(p_uuid) REFERENCES player_profile(p_uuid) ON UPDATE CASCADE ON DELETE CASCADE)");
        }
    }

    private static void persist(Connection database, Write write) throws SQLException {
        var data = write.data();
        if (write.key().getScope() == DataScope.PLAYER_PROFILE) {
            try (var statement = database.prepareStatement(
                    "INSERT OR IGNORE INTO player_profile(p_uuid, p_name, b_num) VALUES (?, ?, ?)")) {
                statement.setString(1, data.getString(FieldKey.PLAYER_UUID));
                statement.setString(2, data.getString(FieldKey.PLAYER_NAME));
                statement.setInt(3, data.getInt(FieldKey.PLAYER_BACKPACK_NUM));
                statement.executeUpdate();
            }
        } else {
            assertEquals(DataScope.PLAYER_RESEARCH, write.key().getScope());
            try (var statement = database.prepareStatement(
                    "INSERT OR IGNORE INTO player_research(p_uuid, research_id) VALUES (?, ?)")) {
                statement.setString(1, data.getString(FieldKey.PLAYER_UUID));
                statement.setString(2, data.getString(FieldKey.RESEARCH_ID));
                statement.executeUpdate();
            }
        }
    }

    private static void assertRows(Connection database, UUID uuid, String name) throws SQLException {
        try (var statement = database.prepareStatement("SELECT p_name, b_num FROM player_profile WHERE p_uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                assertEquals(name, rows.getString(1));
                assertEquals(0, rows.getInt(2));
                assertFalse(rows.next());
            }
        }
        try (var statement = database.prepareStatement("SELECT research_id FROM player_research WHERE p_uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (var rows = statement.executeQuery()) {
                assertTrue(rows.next());
                assertEquals("slimefun:walking_sticks", rows.getString(1));
                assertFalse(rows.next());
            }
        }
    }

    private record Write(ScopeKey scope, RecordKey key, RecordSet data, boolean forceScopeKey) {}

    private static final class RecordingProfiles extends ProfileDataController {
        final List<Write> writes = new ArrayList<>();

        @Override
        protected void scheduleWriteTask(ScopeKey scope, RecordKey key, RecordSet data, boolean forceScopeKey) {
            writes.add(new Write(scope, key, data, forceScopeKey));
        }
    }
}
