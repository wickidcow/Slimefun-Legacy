package io.github.thebusybiscuit.slimefun4.core.services;

import static org.junit.jupiter.api.Assertions.*;

import io.github.bakedlibs.dough.config.Config;
import java.nio.file.Path;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

class TestCustomTextureDefaults {
    @TempDir
    Path directory;

    @BeforeEach
    void startServer() {
        MockBukkit.mock();
    }

    @AfterEach
    void stopServer() {
        MockBukkit.unmock();
    }

    private Config configuration() {
        return new Config(directory.resolve("item-models.yml").toFile(), new YamlConfiguration());
    }

    @Test
    void cleanInstallDoesNotOptIntoBundledModels() {
        Config config = configuration();
        CustomTextureService service = new CustomTextureService(config);
        assertTrue(config.contains("STEEL_INGOT"));
        assertTrue(config.contains("_UI_BACK"));
        assertEquals(0, service.getModelData("STEEL_INGOT"));
        assertEquals(0, service.getModelData("SLIMEFUN_GUIDE"));
        for (String id : config.getKeys()) {
            if (!id.startsWith("_SLIMEFUN_LEGACY_MIGRATIONS")) {
                assertEquals(0, config.getInt(id), id);
            }
        }
    }

    @Test
    void incompleteExistingFileKeepsExplicitValuesAndAddsOnlyZeros() {
        Config config = configuration();
        config.setValue("STEEL_INGOT", 0);
        config.setValue("SLIMEFUN_GUIDE", 2200001);
        config.setValue("_UI_BACK", 73421);
        config.setValue("EXTERNAL_ADDON_ITEM", 87654);
        CustomTextureService service = new CustomTextureService(config);
        assertEquals(0, service.getModelData("STEEL_INGOT"));
        assertEquals(0, service.getModelData("BRONZE_INGOT"));
        assertEquals(2200001, service.getModelData("SLIMEFUN_GUIDE"));
        assertEquals(73421, service.getModelData("_UI_BACK"));
        assertEquals(87654, service.getModelData("EXTERNAL_ADDON_ITEM"));
    }

    @Test
    void persistedDefaultsRemainDisabledAfterRestart() {
        Config config = configuration();
        config.setValue("SLIMEFUN_GUIDE", 2200001);
        new CustomTextureService(config);
        config.save();
        Config reloaded = new Config(config.getFile());
        CustomTextureService restarted = new CustomTextureService(reloaded);
        assertEquals(0, restarted.getModelData("STEEL_INGOT"));
        assertEquals(2200001, restarted.getModelData("SLIMEFUN_GUIDE"));
    }

    @Test
    void newUnmappedItemStillStacksWithOldItem() {
        CustomTextureService service = new CustomTextureService(configuration());
        ItemStack oldItem = identifiedItem();
        ItemStack newItem = oldItem.clone();
        service.setTexture(newItem, "STEEL_INGOT");
        assertEquals(oldItem, newItem);
        assertTrue(oldItem.isSimilar(newItem));
        assertFalse(newItem.getItemMeta().hasCustomModelDataComponent());
    }

    @Test
    void zeroMappingPreservesAllExternalModelDataAndItemIdentity() {
        CustomTextureService service = new CustomTextureService(configuration());
        ItemStack item = identifiedItem();
        var meta = item.getItemMeta();
        var component = meta.getCustomModelDataComponent();
        component.setFloats(List.of(9123F, 7F));
        component.setStrings(List.of("itemsadder:existing_item"));
        component.setFlags(List.of(true));
        meta.setCustomModelDataComponent(component);
        item.setItemMeta(meta);
        ItemStack before = item.clone();
        service.setTexture(item, "STEEL_INGOT");
        assertEquals(before, item);
    }

    private ItemStack identifiedItem() {
        ItemStack item = new ItemStack(Material.IRON_INGOT, 32);
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer()
                .set(NamespacedKey.fromString("slimefun:slimefun_item"), PersistentDataType.STRING, "STEEL_INGOT");
        meta.getPersistentDataContainer()
                .set(NamespacedKey.fromString("other:preserve"), PersistentDataType.INTEGER, 17);
        item.setItemMeta(meta);
        return item;
    }
}
