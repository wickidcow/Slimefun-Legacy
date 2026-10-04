package io.github.thebusybiscuit.slimefun4.core.services.stability;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.InventoryReadTestPlugin;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.DataUtils;
import io.github.bakedlibs.dough.config.Config;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideImplementation;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.core.services.CustomTextureService;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class TestResourcePackModelExecutor {
    private static final String ID = "STEEL_INGOT";
    private static final float MODEL = 2200080.0f;
    private InventoryReadTestPlugin fixture;
    private Slimefun plugin;
    private Config mappings;

    @TempDir
    Path directory;

    TestResourcePackModelExecutor() {}

    @BeforeEach
    void setUp() throws Exception {
        ServerMock server = MockBukkit.mock();
        this.fixture = new InventoryReadTestPlugin(server);
        this.plugin = Slimefun.instance();
        Field field = CustomTextureService.class.getDeclaredField("config");
        field.setAccessible(true);
        this.mappings = new Config(
                this.directory.resolve("item-models.yml").toFile(), (FileConfiguration) new YamlConfiguration());
        new CustomTextureService(this.mappings);
        field.set(Slimefun.getItemTextureService(), this.mappings);
        for (SlimefunGuideMode mode : SlimefunGuideMode.values()) {
            SlimefunGuideImplementation guide = (SlimefunGuideImplementation) Proxy.newProxyInstance(
                    this.getClass().getClassLoader(),
                    new Class[] {SlimefunGuideImplementation.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getMode" -> mode;
                        case "getItem" -> new ItemStack(Material.ENCHANTED_BOOK);
                        default -> null;
                    });
            Slimefun.getRegistry().registerSlimefunGuide(mode, guide);
        }
        this.registerTemplate(false);
        Assertions.assertEquals(
                (Object) ID,
                (Object) Slimefun.getItemDataService().getItemData(this.item()).orElse("missing"),
                (String) Slimefun.getItemDataService().getKey().toString());
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            if (this.fixture != null) {
                this.fixture.close();
            }
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void repairedStackMatchesModelFreeOriginalAndBackupPrecedesMutation() {
        ItemStack modelFree = this.item();
        ItemStack old = TestResourcePackModelExecutor.modeled(modelFree, 2200080.0f);
        Assertions.assertTrue((boolean) old.getItemMeta().hasCustomModelDataComponent(), (String)
                old.getItemMeta().serialize().toString());
        Assertions.assertEquals(List.of(Float.valueOf(2200080.0f)), (Object)
                old.getItemMeta().getCustomModelDataComponent().getFloats());
        AtomicInteger backups = new AtomicInteger();
        ResourcePackModelExecutor executor = new ResourcePackModelExecutor(true, (identity, original) -> {
            Assertions.assertEquals((Object) "backpack:uuid:3", (Object) identity);
            Assertions.assertEquals((Object) old, (Object) original);
            Assertions.assertTrue((boolean) old.getItemMeta().hasCustomModelDataComponent());
            backups.incrementAndGet();
        });
        ItemDoctorReport report = new ItemDoctorReport(true);
        Assertions.assertTrue((boolean) executor.inspectItem(old, report, "backpack:uuid:3"), (String)
                ("SF=" + report.getSlimefunStacks() + ", candidates=" + report.getItemModelCandidates() + ", conflicts="
                        + report.getItemModelConflicts() + ", failures=" + report.getFailures()));
        Assertions.assertEquals((Object) modelFree, (Object) old);
        Assertions.assertTrue((boolean) modelFree.isSimilar(old));
        Assertions.assertEquals((int) 1, (int) backups.get());
        Assertions.assertEquals((long) 1L, (long) report.getItemModelRepairs());
        Assertions.assertFalse((boolean) executor.inspectItem(old, report));
        Assertions.assertEquals((int) 1, (int) backups.get(), (String)
                "Repeated cleanup must not create another backup or mutate a clean item");
    }

    @Test
    void backupFailureLeavesWholeStackAndRepairCountsUnchanged() {
        ItemStack item = TestResourcePackModelExecutor.modeled(this.item(), 2200080.0f);
        ItemStack original = item.clone();
        ItemDoctorReport report = new ItemDoctorReport(true);
        ResourcePackModelExecutor executor = new ResourcePackModelExecutor(true, (identity, before) -> {
            throw new IOException("disk full");
        });
        Assertions.assertFalse((boolean) executor.inspectItem(item, report));
        Assertions.assertEquals((Object) original, (Object) item);
        Assertions.assertEquals((long) 1L, (long) report.getItemModelCandidates());
        Assertions.assertEquals((long) 0L, (long) report.getItemModelRepairs());
        Assertions.assertEquals((long) 1L, (long) report.getFailures());
    }

    @Test
    void previewFindsOldNumericMappingWithoutChangingAnything() {
        this.mappings.setValue(ID, (Object) 2200080);
        ItemStack item = TestResourcePackModelExecutor.modeled(this.item(), 2200080.0f);
        ItemStack original = item.clone();
        ItemDoctorReport report = new ItemDoctorReport(false);
        ResourcePackModelExecutor executor = new ResourcePackModelExecutor(
                false, (identity, before) -> Assertions.fail((String) "A scan must never write a backup"));
        Assertions.assertFalse((boolean) executor.inspectItem(item, report));
        Assertions.assertEquals((Object) original, (Object) item);
        Assertions.assertEquals((int) 2200080, (int) this.mappings.getInt(ID));
        Assertions.assertEquals((long) 1L, (long) report.getItemModelCandidates());
        Assertions.assertEquals((long) 0L, (long) report.getItemModelRepairs());
    }

    @Test
    void externalModelsAndCustomizedMappingsAreProtected() {
        ResourcePackModelExecutor executor = new ResourcePackModelExecutor(
                true, (identity, before) -> Assertions.fail((String) "Protected models cannot be rewritten"));
        for (float model : List.of(Float.valueOf(99123.0f), Float.valueOf(2200080.0f))) {
            if (model == 2200080.0f) {
                this.mappings.setValue(ID, (Object) 99123);
            }
            ItemStack item = TestResourcePackModelExecutor.modeled(this.item(), model);
            ItemStack original = item.clone();
            Assertions.assertFalse((boolean) executor.inspectItem(item, new ItemDoctorReport(true)));
            Assertions.assertEquals((Object) original, (Object) item);
        }
    }

    @Test
    void removesOnlyLegacyFloatAndKeepsEveryExternalLane() {
        ModernMetaStack item = new ModernMetaStack(this.item());
        ItemMeta meta = item.getItemMeta();
        CustomModelDataComponent component = meta.getCustomModelDataComponent();
        component.setFloats(List.of(Float.valueOf(2200080.0f), Float.valueOf(99123.0f)));
        component.setFlags(List.of(Boolean.valueOf(true), Boolean.valueOf(false)));
        component.setStrings(List.of("itemsadder:custom_item"));
        component.setColors(List.of(Color.RED));
        meta.setCustomModelDataComponent(component);
        item.setItemMeta(meta);
        ResourcePackModelExecutor executor = new ResourcePackModelExecutor(true, (identity, before) -> {});
        Assertions.assertTrue((boolean) executor.inspectItem(item, new ItemDoctorReport(true)));
        CustomModelDataComponent result = item.getItemMeta().getCustomModelDataComponent();
        Assertions.assertEquals(List.of(Float.valueOf(99123.0f)), (Object) result.getFloats());
        Assertions.assertEquals(List.of(Boolean.valueOf(true), Boolean.valueOf(false)), (Object) result.getFlags());
        Assertions.assertEquals(List.of("itemsadder:custom_item"), (Object) result.getStrings());
        Assertions.assertEquals(List.of(Color.RED), (Object) result.getColors());
        Assertions.assertEquals((int) 32, (int) item.getAmount());
        Assertions.assertEquals((Object) ID, (Object)
                Slimefun.getItemDataService().getItemData(item).orElseThrow());
    }

    @Test
    void oldRuntimeTemplateBlocksCleanupUntilRestart() {
        this.registerTemplate(true);
        ItemStack item = TestResourcePackModelExecutor.modeled(this.item(), 2200080.0f);
        ItemStack before = item.clone();
        ItemDoctorReport report = new ItemDoctorReport(true);
        ResourcePackModelExecutor executor = new ResourcePackModelExecutor(
                true, (identity, original) -> Assertions.fail((String) "Restart barrier must precede backup/repair"));
        Assertions.assertFalse((boolean) executor.inspectItem(item, report));
        Assertions.assertEquals((Object) before, (Object) item);
        Assertions.assertEquals((long) 1L, (long) report.getItemModelConflicts());
        Assertions.assertTrue((Slimefun.getItemTextureService().getHostedPackTemplateMismatchCount() > 0 ? 1 : 0) != 0);
        this.registerTemplate(false);
        Assertions.assertEquals((int) 0, (int) Slimefun.getItemTextureService().getHostedPackTemplateMismatchCount());
    }

    @Test
    void nestedBundleRepairsAreCoveredByOriginalOuterStackBackup() {
        ItemStack bundle = new ItemStack(Material.BUNDLE);
        BundleMeta meta = (BundleMeta) bundle.getItemMeta();
        meta.setItems(List.of(TestResourcePackModelExecutor.modeled(this.item(), 2200080.0f)));
        bundle.setItemMeta((ItemMeta) meta);
        ItemStack original = bundle.clone();
        AtomicInteger backups = new AtomicInteger();
        ResourcePackModelExecutor executor = new ResourcePackModelExecutor(true, (identity, before) -> {
            Assertions.assertEquals((Object) original, (Object) before);
            backups.incrementAndGet();
        });
        ItemDoctorReport report = new ItemDoctorReport(true);
        Assertions.assertTrue((boolean) executor.inspectItem(bundle, report));
        Assertions.assertEquals(
                (Object) this.item(),
                ((BundleMeta) bundle.getItemMeta()).getItems().getFirst());
        Assertions.assertEquals((int) 1, (int) backups.get());
        Assertions.assertEquals((long) 1L, (long) report.getItemModelRepairs());
    }

    @Test
    void checkedMappingResetBacksUpLiveValuesAndPreservesCustomMappings() throws IOException {
        this.mappings.setValue(ID, (Object) 2200080);
        this.mappings.setValue("BRONZE_INGOT", (Object) 123456);
        this.mappings.setValue("MY_ADDON_ITEM", (Object) 654321);
        int reset = Slimefun.getItemTextureService().removeHostedPackMappingsChecked(this.directory);
        Assertions.assertEquals((int) 1, (int) reset);
        Assertions.assertEquals((int) 0, (int) this.mappings.getInt(ID));
        Assertions.assertEquals((int) 123456, (int) this.mappings.getInt("BRONZE_INGOT"));
        Assertions.assertEquals((int) 654321, (int) this.mappings.getInt("MY_ADDON_ITEM"));
        YamlConfiguration saved = YamlConfiguration.loadConfiguration((File) this.mappings.getFile());
        Assertions.assertEquals((int) 0, (int) saved.getInt(ID));
        YamlConfiguration backup = YamlConfiguration.loadConfiguration(
                (File) this.directory.resolve("item-models-current.yml").toFile());
        Assertions.assertEquals((int) 2200080, (int) backup.getInt(ID));
    }

    @Test
    void failedMappingBackupDoesNotChangeLiveMappings() {
        this.mappings.setValue(ID, (Object) 2200080);
        Assertions.assertThrows(
                IOException.class,
                () -> Slimefun.getItemTextureService()
                        .removeHostedPackMappingsChecked(this.directory.resolve("missing")));
        Assertions.assertEquals((int) 2200080, (int) this.mappings.getInt(ID));
    }

    @Test
    void itemJournalRoundTripPreservesProtectedMetadata() throws IOException {
        ResourcePackDoctorBackup backup = ResourcePackDoctorBackup.create(this.directory, "uninstall");
        ItemStack item = this.item();
        backup.item("player:uuid:12", item);
        String[] fields = Files.readString(backup.directory().resolve("items.tsv"))
                .strip()
                .split("\t");
        Assertions.assertEquals((Object) "player:uuid:12", (Object)
                new String(Base64.getDecoder().decode(fields[0]), StandardCharsets.UTF_8));
        Assertions.assertEquals((Object) "B", (Object) fields[1]);
        Assertions.assertEquals((Object) item, (Object)
                DataUtils.deserializeItemStack(Base64.getDecoder().decode(fields[2])));
    }

    @Test
    void noDeferredModelCleanupExistsWithoutSavedAuthorization() {
        Assertions.assertNull(
                (Object) Slimefun.getItemDoctorService().getResourcePackDoctor().deferredExecutor());
        Assertions.assertFalse((boolean) Slimefun.getItemDoctorService()
                .getResourcePackDoctor()
                .resume(result -> Assertions.fail())
                .accepted());
    }

    private void registerTemplate(boolean oldModel) {
        final ItemStack template =
                oldModel ? TestResourcePackModelExecutor.modeled(this.item(), 2200080.0f) : this.item();
        SlimefunItemStack stack = new SlimefunItemStack(ID, template);
        SlimefunItem definition =
                new SlimefunItem(
                        new ItemGroup(
                                new NamespacedKey((Plugin) this.plugin, "doctor_test"), new ItemStack(Material.CHEST)),
                        stack,
                        RecipeType.NULL,
                        new ItemStack[9]) {

                    @Override
                    public ItemStack getItem() {
                        return template;
                    }
                };
        Slimefun.getRegistry().getSlimefunItemIds().put(ID, definition);
    }

    private ItemStack item() {
        ItemStack item = new ItemStack(Material.IRON_INGOT, 32);
        ItemMeta meta = item.getItemMeta();
        meta.displayName((Component) Component.text((String) "Existing steel ingot"));
        meta.lore(List.of(Component.text((String) "Original lore")));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Slimefun.getItemDataService().setItemData(meta, ID);
        pdc.set(
                NamespacedKey.fromString((String) "slimefun:item_charge"),
                PersistentDataType.FLOAT,
                Float.valueOf(123.4567f));
        pdc.set(NamespacedKey.fromString((String) "slimefun:b_uuid"), PersistentDataType.STRING, "backpack-uuid");
        pdc.set(NamespacedKey.fromString((String) "slimefun:owner_uuid"), PersistentDataType.STRING, "owner-uuid");
        pdc.set(
                NamespacedKey.fromString((String) "itemsadder:opaque"),
                PersistentDataType.STRING,
                "opaque original metadata");
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack modeled(ItemStack item, float model) {
        ModernMetaStack copy = new ModernMetaStack(item);
        ItemMeta meta = copy.getItemMeta();
        CustomModelDataComponent component = meta.getCustomModelDataComponent();
        component.setFloats(List.of(Float.valueOf(model)));
        meta.setCustomModelDataComponent(component);
        copy.setItemMeta(meta);
        return copy;
    }

    /** MockBukkit 4.110 drops modern model lanes on clone; real Paper is checked by the native probe. */
    private static final class ModernMetaStack extends ItemStack {
        private ItemMeta modernMeta;

        ModernMetaStack(ItemStack source) {
            super(source.getType(), source.getAmount());
            this.setItemMeta(source.getItemMeta());
        }

        public ItemMeta getItemMeta() {
            return ModernMetaStack.copyMeta(this.modernMeta);
        }

        public boolean hasItemMeta() {
            return this.modernMeta != null;
        }

        public boolean setItemMeta(ItemMeta meta) {
            this.modernMeta = ModernMetaStack.copyMeta(meta);
            return super.setItemMeta(meta);
        }

        public ItemStack clone() {
            return new ModernMetaStack(this);
        }

        private static ItemMeta copyMeta(ItemMeta source) {
            if (source == null) {
                return null;
            }
            ItemMeta copy = source.clone();
            if (source.hasCustomModelDataComponent()) {
                CustomModelDataComponent original = source.getCustomModelDataComponent();
                CustomModelDataComponent component = copy.getCustomModelDataComponent();
                component.setFloats(original.getFloats());
                component.setFlags(original.getFlags());
                component.setStrings(original.getStrings());
                component.setColors(original.getColors());
                copy.setCustomModelDataComponent(component);
            }
            return copy;
        }
    }
}
