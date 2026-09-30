package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import static org.junit.jupiter.api.Assertions.*;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.ItemStackDataCodec;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerBackpack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.SlimefunRegistry;
import io.github.thebusybiscuit.slimefun4.core.attributes.Rechargeable;
import io.github.thebusybiscuit.slimefun4.core.services.stability.ItemDoctorReport;
import io.github.thebusybiscuit.slimefun4.core.services.stability.ItemPresentationDoctor;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Real Doctor callbacks and historical encodings; synthetic old-item fixtures, not a captured player world. */
class ItemDoctorPreservationTest {
    private static final String ID = "DOCTOR_EXISTING_POWER_TOOL";
    private static final float EXACT_CHARGE = 123.4567F;
    private static final NamespacedKey CHARGE = key("slimefun:item_charge");
    private static final String OWNER = "11111111-2222-3333-4444-555555555555";
    private static final String BACKPACK = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
    private InventoryReadTestPlugin fixture;
    private ItemPresentationDoctor doctor;

    @BeforeEach
    void setUp() throws Exception {
        fixture = new InventoryReadTestPlugin(MockBukkit.mock());
        for (String fieldName : List.of("itemChargeKey", "soulboundKey")) {
            var field = SlimefunRegistry.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(Slimefun.getRegistry(), fieldName.equals("itemChargeKey") ? CHARGE : key("slimefun:soulbound"));
        }
        doctor = new ItemPresentationDoctor();
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
    void fractionalChargeSurvivesHistoricalDecodeAndPresentationRepairExactly() throws Exception {
        register(new ChargedItem(Mode.NORMAL));
        ItemStack item = legacyDecode(oldItem(ID, Material.DIAMOND_PICKAXE, 17, true));
        ItemStack original = item.clone();
        var report = new ItemDoctorReport(true);

        assertTrue(doctor.inspectItem(item, true, report));
        assertEquals(
                Float.floatToIntBits(EXACT_CHARGE),
                Float.floatToIntBits(
                        item.getItemMeta().getPersistentDataContainer().get(CHARGE, PersistentDataType.FLOAT)));
        assertProtectedMetadata(original, item);
        assertEquals(1, report.getRepairedStacks());
        assertEquals(0, report.getFailures());
        assertEquals("Existing power tool", plain(item.getItemMeta().displayName()));
    }

    @Test
    void readOnlyScanNeverWritesNamesLoreOrMetadata() {
        var definition = new ChargedItem(Mode.NORMAL);
        register(definition);
        ItemStack item = oldItem(ID, Material.DIAMOND_PICKAXE, 17, true);
        ItemStack original = item.clone();
        var report = new ItemDoctorReport(false);
        assertFalse(doctor.inspectItem(item, false, report));
        assertComplete(original, item);
        assertEquals(1, definition.maximumCalls.get(), "The actual state-capture callback must run");
        assertEquals(0, report.getFailures());
        assertEquals(0, report.getRepairedStacks());
    }

    @Test
    void stateCaptureCallbackCannotMutateTheLiveItemEvenDuringScan() {
        var definition = new ChargedItem(Mode.MUTATE_CAPTURE);
        register(definition);
        ItemStack item = oldItem(ID, Material.DIAMOND_PICKAXE, 17, true);
        ItemStack original = item.clone();
        var report = new ItemDoctorReport(false);
        assertFalse(doctor.inspectItem(item, false, report));
        assertEquals(1, definition.maximumCalls.get());
        assertComplete(original, item);
        assertEquals(0, report.getFailures());
    }

    @Test
    void failedRestorationCallbackLeavesTheCompleteLiveItemUntouched() {
        register(new ChargedItem(Mode.THROW_RESTORE));
        ItemStack item = oldItem(ID, Material.DIAMOND_PICKAXE, 17, true);
        ItemStack original = item.clone();
        var report = new ItemDoctorReport(true);
        assertFalse(doctor.inspectItem(item, true, report));
        assertComplete(original, item);
        assertEquals(1, report.getFailures());
        assertEquals(0, report.getRepairedStacks());
    }

    @Test
    void successfulCallbackCannotSilentlyChangeItemIdentityOrAmount() {
        register(new ChargedItem(Mode.MUTATE_RESTORE));
        ItemStack item = oldItem(ID, Material.DIAMOND_PICKAXE, 17, true);
        ItemStack original = item.clone();
        var report = new ItemDoctorReport(true);
        assertFalse(doctor.inspectItem(item, true, report));
        assertComplete(original, item);
        assertEquals(1, report.getFailures());
        assertEquals(0, report.getRepairedStacks());
    }

    @Test
    void nameOnlyRepairDoesNotInvokeDynamicCallbacks() {
        var definition = new ChargedItem(Mode.THROW_RESTORE);
        register(definition);
        ItemStack item = oldItem(ID, Material.DIAMOND_PICKAXE, 17, false);
        ItemStack original = item.clone();
        var report = new ItemDoctorReport(true);
        assertTrue(doctor.inspectItem(item, true, report));
        assertEquals(0, definition.maximumCalls.get());
        assertEquals(original.getItemMeta().lore(), item.getItemMeta().lore());
        assertProtectedMetadata(original, item);
        assertEquals(0, report.getFailures());
    }

    @Test
    void legacyBackpackOwnerAndNumericIdRemainTheSameWithoutInventingAUuid() {
        register(new CanonicalItem("DOCTOR_OLD_BACKPACK", Material.CHEST));
        ItemStack item = oldItem("DOCTOR_OLD_BACKPACK", Material.CHEST, 1, true);
        var meta = item.getItemMeta();
        meta.lore(List.of(
                Component.text("\u65e7\u80cc\u5305"),
                LegacyComponentSerializer.legacyAmpersand().deserialize("&7ID: " + OWNER + "#42")));
        item.setItemMeta(meta);
        ItemStack original = item.clone();
        String identity = PlayerBackpack.getLegacyBackpackIdentity(meta).orElseThrow();
        var report = new ItemDoctorReport(true);
        assertTrue(doctor.inspectItem(item, true, report));
        assertEquals(
                identity,
                PlayerBackpack.getLegacyBackpackIdentity(item.getItemMeta()).orElseThrow());
        assertTrue(PlayerBackpack.getBackpackUUID(item.getItemMeta()).isEmpty());
        assertProtectedMetadata(original, item);
        assertEquals(0, report.getFailures());
    }

    @Test
    void boundBackpackKeepsItsExistingOwnerAndInventoryUuid() {
        register(new CanonicalItem("DOCTOR_BOUND_BACKPACK", Material.CHEST));
        ItemStack item = oldItem("DOCTOR_BOUND_BACKPACK", Material.CHEST, 1, true);
        PlayerBackpack.setItemPdc(item, BACKPACK, OWNER);
        ItemStack original = item.clone();
        var report = new ItemDoctorReport(true);
        assertTrue(doctor.inspectItem(item, true, report));
        assertEquals(
                BACKPACK, PlayerBackpack.getBackpackUUID(item.getItemMeta()).orElseThrow());
        assertEquals(OWNER, PlayerBackpack.getOwnerUUID(item.getItemMeta()).orElseThrow());
        assertProtectedMetadata(original, item);
        assertEquals(0, report.getFailures());
    }

    @Test
    void unknownAddonIdentityAndLoreRemainRecoverableWhenNameRepairIsDisabled() {
        Slimefun.getCfg().setValue("stability.item-doctor.repair-orphaned-item-names", false);
        ItemStack item = oldItem("MISSING_OLD_ADDON_ID", Material.DIAMOND_PICKAXE, 37, true);
        ItemStack original = item.clone();
        var report = new ItemDoctorReport(true);
        assertFalse(doctor.inspectItem(item, true, report));
        assertComplete(original, item);
        assertEquals(1, report.getUnknownIds());
        assertEquals(0, report.getFailures());
    }

    @Test
    void alreadyEnglishItemsAreNotRebuiltFromCurrentTemplates() {
        register(new ChargedItem(Mode.THROW_RESTORE));
        ItemStack item = oldItem(ID, Material.DIAMOND_PICKAXE, 37, false);
        var meta = item.getItemMeta();
        meta.displayName(Component.text("Player's existing custom name"));
        item.setItemMeta(meta);
        ItemStack original = item.clone();
        var report = new ItemDoctorReport(true);
        assertFalse(doctor.inspectItem(item, true, report));
        assertComplete(original, item);
        assertEquals(0, report.getFailures());
    }

    @Test
    void loreOnlyChargeRecoveryRetainsItsExactValueAndOtherOriginalData() {
        register(new ChargedItem(Mode.NORMAL));
        ItemStack item = oldItem(ID, Material.DIAMOND_PICKAXE, 17, true);
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().remove(CHARGE);
        meta.lore(List.of(Component.text("\u7535\u91cf: " + EXACT_CHARGE + " / 1000 J")));
        item.setItemMeta(meta);
        ItemStack original = item.clone();
        var report = new ItemDoctorReport(true);
        assertTrue(doctor.inspectItem(item, true, report));
        assertEquals(
                EXACT_CHARGE, item.getItemMeta().getPersistentDataContainer().get(CHARGE, PersistentDataType.FLOAT));
        // The existing Doctor route recovers a missing charge marker from authoritative lore.
        // Only that missing marker is expected; all original fields remain exact.
        var expected = original.getItemMeta();
        expected.getPersistentDataContainer().set(CHARGE, PersistentDataType.FLOAT, EXACT_CHARGE);
        original.setItemMeta(expected);
        assertProtectedMetadata(original, item);
        assertEquals(0, report.getFailures());
    }

    @Test
    void repeatedRepairAndNativeRoundTripAreIdempotentForExistingState() throws Exception {
        register(new ChargedItem(Mode.NORMAL));
        ItemStack item = oldItem(ID, Material.DIAMOND_PICKAXE, 17, true);
        ItemStack original = item.clone();
        var first = new ItemDoctorReport(true);
        assertTrue(doctor.inspectItem(item, true, first));
        ItemStack repaired = item.clone();
        for (int cycle = 0; cycle < 3; cycle++) {
            item = ItemStackDataCodec.deserialize(ItemStackDataCodec.serialize(item));
            var report = new ItemDoctorReport(true);
            assertFalse(doctor.inspectItem(item, true, report));
            assertComplete(repaired, item);
            assertProtectedMetadata(original, item);
            assertEquals(0, report.getFailures());
        }
    }

    private static void register(SlimefunItem item) {
        // Real lookup registry and real Doctor; no recipe/service startup needed for these fixtures.
        Slimefun.getRegistry().getSlimefunItemIds().put(item.getId(), item);
    }

    private static ItemStack oldItem(String id, Material material, int amount, boolean translatedLore) {
        var item = new ItemStack(material, amount);
        var meta = item.getItemMeta();
        meta.displayName(Component.text("\u65e7\u7269\u54c1"));
        meta.lore(List.of(Component.text(translatedLore ? "\u65e7\u8bf4\u660e" : "Existing English lore")));
        Slimefun.getItemDataService().setItemData(meta, id);
        var pdc = meta.getPersistentDataContainer();
        pdc.set(CHARGE, PersistentDataType.FLOAT, EXACT_CHARGE);
        pdc.set(key("legacyaddon:count"), PersistentDataType.LONG, 9_000_000_001L);
        pdc.set(key("legacyaddon:data"), PersistentDataType.BYTE_ARRAY, new byte[] {0, -1, 42, 127});
        pdc.set(key("legacyaddon:indices"), PersistentDataType.INTEGER_ARRAY, new int[] {0, -1, 42});
        pdc.set(key("slimefun:soulbound"), PersistentDataType.BYTE, (byte) 1);
        pdc.set(key("legacyaddon:unknown"), PersistentDataType.STRING, "original opaque data");
        if (meta instanceof Damageable damageable) damageable.setDamage(127);
        meta.addEnchant(Enchantment.EFFICIENCY, 7, true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setUnbreakable(true);
        var models = meta.getCustomModelDataComponent();
        models.setFloats(List.of(12345F, 0.5F));
        models.setFlags(List.of(true, false));
        models.setStrings(List.of("addon-model"));
        meta.setCustomModelDataComponent(models);
        item.setItemMeta(meta);
        return item;
    }

    private static void assertProtectedMetadata(ItemStack expected, ItemStack actual) {
        assertEquals(expected.getType(), actual.getType());
        assertEquals(expected.getAmount(), actual.getAmount());
        var before = expected.getItemMeta();
        var after = actual.getItemMeta();
        before.displayName(null);
        before.lore(null);
        after.displayName(null);
        after.lore(null);
        assertTree(before.serialize(), after.serialize(), "protected metadata");
    }

    private static void assertComplete(ItemStack expected, ItemStack actual) {
        assertProtectedMetadata(expected, actual);
        assertEquals(expected.getItemMeta().displayName(), actual.getItemMeta().displayName());
        assertEquals(expected.getItemMeta().lore(), actual.getItemMeta().lore());
    }

    private static void assertTree(Object expected, Object actual, String path) {
        if (expected instanceof byte[] values) {
            assertArrayEquals(values, assertInstanceOf(byte[].class, actual), path);
        } else if (expected instanceof int[] values) {
            assertArrayEquals(values, assertInstanceOf(int[].class, actual), path);
        } else if (expected instanceof long[] values) {
            assertArrayEquals(values, assertInstanceOf(long[].class, actual), path);
        } else if (expected instanceof Map<?, ?> values) {
            var target = assertInstanceOf(Map.class, actual);
            assertEquals(values.keySet(), target.keySet(), path);
            values.forEach((key, value) -> assertTree(value, target.get(key), path + "." + key));
        } else if (expected instanceof List<?> values) {
            var target = assertInstanceOf(List.class, actual);
            assertEquals(values.size(), target.size(), path);
            for (int i = 0; i < values.size(); i++) assertTree(values.get(i), target.get(i), path + "[" + i + "]");
        } else {
            assertEquals(expected, actual, path);
        }
    }

    @SuppressWarnings("deprecation") // Historical fixture writer only; shipped encoding is unchanged.
    private static ItemStack legacyDecode(ItemStack item) throws Exception {
        try (var bytes = new ByteArrayOutputStream();
                var output = new BukkitObjectOutputStream(bytes)) {
            output.writeObject(item);
            output.flush();
            return ItemStackDataCodec.deserialize(Base64.getEncoder().encode(bytes.toByteArray()));
        }
    }

    private static NamespacedKey key(String value) {
        return Objects.requireNonNull(NamespacedKey.fromString(value));
    }

    private static String plain(Component value) {
        return PlainTextComponentSerializer.plainText().serialize(value);
    }

    private enum Mode {
        NORMAL,
        MUTATE_CAPTURE,
        THROW_RESTORE,
        MUTATE_RESTORE
    }

    private static class CanonicalItem extends SlimefunItem {
        CanonicalItem(String id, Material material) {
            super(
                    new ItemGroup(key("doctor_test:items"), new ItemStack(Material.BOOK)),
                    new SlimefunItemStack(id, material, "Existing power tool", "English description"),
                    RecipeType.ENHANCED_CRAFTING_TABLE,
                    new ItemStack[9]);
            addon = Slimefun.instance();
        }
    }

    private static final class ChargedItem extends CanonicalItem implements Rechargeable {
        private final Mode mode;
        private final AtomicInteger maximumCalls = new AtomicInteger();

        ChargedItem(Mode mode) {
            super(ID, Material.DIAMOND_PICKAXE);
            this.mode = mode;
        }

        @Override
        public float getMaxItemCharge(ItemStack item) {
            maximumCalls.incrementAndGet();
            if (mode == Mode.MUTATE_CAPTURE) {
                item.setAmount(1);
                Slimefun.getItemDataService().setItemData(item, "WRONG_ID");
            }
            return 1000F;
        }

        @Override
        public void setItemCharge(ItemStack item, float charge) {
            Rechargeable.super.setItemCharge(item, charge);
            if (mode == Mode.THROW_RESTORE || mode == Mode.MUTATE_RESTORE) {
                item.setAmount(1);
                Slimefun.getItemDataService().setItemData(item, "WRONG_ID");
            }
            if (mode == Mode.THROW_RESTORE) throw new IllegalStateException("injected presentation failure");
        }
    }
}
