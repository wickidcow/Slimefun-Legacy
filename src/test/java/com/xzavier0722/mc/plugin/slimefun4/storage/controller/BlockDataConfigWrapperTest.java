package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import org.apache.commons.lang.NotImplementedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises the legacy Config bridge against the real in-memory data-container behavior.
 * These tests do not certify database durability, server threading or cross-fork rollback.
 */
@SuppressWarnings("deprecation")
class BlockDataConfigWrapperTest {

    private RecordingDataContainer data;
    private Config config;

    @BeforeEach
    void setUp() {
        data = new RecordingDataContainer("world;10:64:-20", "ELECTRIC_FURNACE");
        config = new BlockDataConfigWrapper(data);
    }

    @Test
    void nullRemovesOnlyTheRequestedKeyAndSchedulesOneUpdate() {
        data.setData("recipe", "legacy-recipe");
        data.setData("owner", "00000000-0000-0000-0000-000000000001");
        data.setData("energy-charge", "1200");
        data.updates.clear();

        assertDoesNotThrow(() -> config.setValue("recipe", null));

        assertEquals(
                Map.of("owner", "00000000-0000-0000-0000-000000000001", "energy-charge", "1200"),
                data.getAllData());
        assertEquals(List.of("recipe"), data.updates);
        assertEquals("ELECTRIC_FURNACE", data.getSfId());
        assertEquals("world;10:64:-20", data.getKey());
        assertFalse(data.isPendingRemove());
    }

    @Test
    void nullForAMissingLoadedKeyDoesNotScheduleAWrite() {
        assertDoesNotThrow(() -> config.setValue("missing", null));

        assertTrue(data.getAllData().isEmpty());
        assertTrue(data.updates.isEmpty());
    }

    @Test
    void repeatedDeletionDoesNotScheduleDuplicateWrites() {
        data.setData("recipe", "legacy-recipe");
        data.updates.clear();

        assertDoesNotThrow(() -> {
            config.setValue("recipe", null);
            config.setValue("recipe", null);
        });

        assertNull(data.getData("recipe"));
        assertEquals(List.of("recipe"), data.updates);
    }

    @Test
    void unloadedDeletionDelegatesWithoutReadingOrLoadingTheContainer() {
        data.setIsDataLoaded(false);

        assertDoesNotThrow(() -> config.setValue("recipe", null));

        assertEquals(List.of("recipe"), data.updates);
        assertFalse(data.isDataLoaded());
    }

    @Test
    void pendingRemovalDeletionDoesNotSchedulePersistence() {
        data.setData("recipe", "legacy-recipe");
        data.updates.clear();
        data.setPendingRemove(true);

        assertDoesNotThrow(() -> config.setValue("recipe", null));

        assertNull(data.getData("recipe"));
        assertTrue(data.updates.isEmpty());
        assertTrue(data.isPendingRemove());
    }

    @Test
    void stringWritesPreserveOpaqueAddonPayloadsExactly() {
        String payload = "  {\"id\":\"ADDON_MACHINE\",\"counter\":\"00042\"}\n\u00a7a\u03a9  ";

        config.setValue("addon:payload", payload);

        assertEquals(payload, config.getString("addon:payload"));
        assertEquals(payload, config.getValue("addon:payload"));
        assertEquals(Map.of("addon:payload", payload), data.getAllData());
        assertEquals(List.of("addon:payload"), data.updates);
        assertEquals("ELECTRIC_FURNACE", data.getSfId());
    }

    @Test
    void emptyStringIsAStoredValueRatherThanADeletion() {
        config.setValue("empty", "");

        assertTrue(config.contains("empty"));
        assertEquals("", config.getString("empty"));
        assertEquals(Map.of("empty", ""), data.getAllData());
        assertEquals(List.of("empty"), data.updates);
    }

    @Test
    void unsupportedValueTypesAreRejectedWithoutMutation() {
        data.setData("recipe", "original");
        data.updates.clear();
        Map<String, String> original = Map.copyOf(data.getAllData());

        for (Object value : List.of(42, true, List.of("recipe"), Map.of("recipe", "replacement"))) {
            assertThrows(NotImplementedException.class, () -> config.setValue("recipe", value));
            assertEquals(original, data.getAllData());
            assertTrue(data.updates.isEmpty());
        }
    }

    @Test
    void defaultDoesNotOverwriteAnExistingValue() {
        data.setData("recipe", "original");
        data.updates.clear();

        config.setDefaultValue("recipe", "replacement");

        assertEquals("original", data.getData("recipe"));
        assertTrue(data.updates.isEmpty());
    }

    @Test
    void defaultStoresAMissingStringThroughTheContainer() {
        config.setDefaultValue("recipe", "default-recipe");

        assertEquals(Map.of("recipe", "default-recipe"), data.getAllData());
        assertEquals(List.of("recipe"), data.updates);
    }

    @Test
    void unsupportedDefaultTypeIsRejectedWithoutMutation() {
        data.setData("recipe", "original");
        data.updates.clear();

        assertThrows(NotImplementedException.class, () -> config.setDefaultValue("recipe", 42));

        assertEquals(Map.of("recipe", "original"), data.getAllData());
        assertTrue(data.updates.isEmpty());
    }

    @Test
    void keyEnumerationReturnsADetachedSnapshot() {
        data.setData("recipe", "original");
        data.updates.clear();
        Set<String> keys = config.getKeys();

        keys.clear();
        keys.add("unrelated");

        assertEquals(Set.of("recipe"), config.getKeys());
        assertEquals(Map.of("recipe", "original"), data.getAllData());
        assertTrue(data.updates.isEmpty());
    }

    @Test
    void lookupsDoNotScheduleWrites() {
        data.setData("recipe", "original");
        data.updates.clear();

        assertEquals("original", config.getString("recipe"));
        assertEquals("original", config.getValue("recipe"));
        assertTrue(config.contains("recipe"));
        assertFalse(config.contains("missing"));
        assertNull(config.getString("missing"));
        assertNull(config.getValue("missing"));
        assertEquals(Set.of("recipe"), config.getKeys());
        assertTrue(data.updates.isEmpty());
    }

    @Test
    void fileLifecycleMethodsDoNotCreateFilesOrRewriteData(@TempDir Path directory) {
        data.setData("recipe", "original");
        data.updates.clear();
        Path target = directory.resolve("legacy-wrapper.yml");

        assertDoesNotThrow(() -> {
            config.createFile();
            config.save();
            config.save(target.toFile());
            config.reload();
        });

        assertFalse(Files.exists(target));
        assertEquals(Map.of("recipe", "original"), data.getAllData());
        assertTrue(data.updates.isEmpty());
    }

    @Test
    void separateWrappersDoNotShareData() {
        RecordingDataContainer other = new RecordingDataContainer("world;11:64:-20", "ELECTRIC_SMELTERY");
        Config otherConfig = new BlockDataConfigWrapper(other);
        config.setValue("recipe", "first");
        otherConfig.setValue("recipe", "second");
        data.updates.clear();
        other.updates.clear();

        assertDoesNotThrow(() -> config.setValue("recipe", null));

        assertNull(config.getString("recipe"));
        assertEquals("second", otherConfig.getString("recipe"));
        assertEquals(List.of("recipe"), data.updates);
        assertTrue(other.updates.isEmpty());
    }

    private static final class RecordingDataContainer extends ASlimefunDataContainer {
        private final List<String> updates = new ArrayList<>();

        private RecordingDataContainer(String key, String sfId) {
            super(key, sfId);
            setIsDataLoaded(true);
        }

        @Override
        public void scheduleUpdateData(String key) {
            updates.add(key);
        }
    }
}
