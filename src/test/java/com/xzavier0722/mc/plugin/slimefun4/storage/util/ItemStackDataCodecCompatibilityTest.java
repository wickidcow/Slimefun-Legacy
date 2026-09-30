package com.xzavier0722.mc.plugin.slimefun4.storage.util;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.FieldKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordSet;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Format-contract fixtures generated in the test harness, not claimed captures from every historical server.
 * The historic object-stream writer is intentionally retained here to protect old-item read compatibility.
 */
class ItemStackDataCodecCompatibilityTest {
    private static final byte[] HEADER = {'S', 'F', '2', 0};
    private static final NamespacedKey ITEM_ID = key("slimefun:slimefun_item");
    private static final NamespacedKey BACKPACK_ID = key("slimefun:b_uuid");
    private static final NamespacedKey OWNER_ID = key("slimefun:owner_uuid");
    private static final NamespacedKey CHARGE = key("slimefun:item_charge");
    private static final NamespacedKey SOULBOUND = key("slimefun:soulbound");

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void readsTextEnvelopeWhenSuppliedAsBytes() throws Exception {
        var expected = new ItemStack(Material.DIAMOND, 37);
        byte[] encoded = Base64.getEncoder().encode(ItemStackDataCodec.serialize(expected));

        assertPreserved(expected, ItemStackDataCodec.deserialize(encoded));
    }

    @Test
    void preservesMetadataAndInputBytesAcrossSupportedRepresentations() throws Exception {
        var expected = oldItem("SOULBOUND_BACKPACK", Material.CHEST, 1);
        for (byte[] stored : representations(expected)) {
            byte[] original = stored.clone();
            assertPreserved(expected, ItemStackDataCodec.deserialize(stored));
            assertArrayEquals(original, stored, "Reading must not rewrite the stored representation");
        }

        // Prove the structural assertions reject real corruption, not merely
        // different in-memory array identities after deserialization.
        var changedArray = expected.clone();
        var arrayMeta = changedArray.getItemMeta();
        arrayMeta.getPersistentDataContainer().set(key("legacyaddon:bytes"),
                PersistentDataType.BYTE_ARRAY, new byte[] {0, -1, 43, 127});
        changedArray.setItemMeta(arrayMeta);
        assertThrows(AssertionError.class, () -> assertPreserved(expected, changedArray));

        var changedType = expected.clone();
        var typeMeta = changedType.getItemMeta();
        typeMeta.getPersistentDataContainer().set(CHARGE, PersistentDataType.DOUBLE, 123.5D);
        changedType.setItemMeta(typeMeta);
        assertThrows(AssertionError.class, () -> assertPreserved(expected, changedType));
    }

    @Test
    void preservesRealStackCountsInsteadOfReplacingItemsWithTemplates() throws Exception {
        var expected = oldItem("COMPRESSED_CARBON", Material.COAL, 53);
        for (byte[] stored : representations(expected)) {
            assertPreserved(expected, ItemStackDataCodec.deserialize(stored));
        }
    }

    @Test
    void preservesDamageEnchantmentsAndCustomPresentation() throws Exception {
        var expected = oldItem("MULTI_TOOL", Material.DIAMOND_PICKAXE, 1);
        var meta = (Damageable) expected.getItemMeta();
        meta.setDamage(127);
        meta.addEnchant(Enchantment.EFFICIENCY, 7, true);
        expected.setItemMeta(meta);
        for (byte[] stored : representations(expected)) {
            assertPreserved(expected, ItemStackDataCodec.deserialize(stored));
        }
    }

    @Test
    @SuppressWarnings("deprecation") // Verify the historical addon-facing String reader remains callable.
    void legacyStringAndByteReadersAgree() throws Exception {
        var expected = oldItem("ELECTRIC_MOTOR", Material.IRON_INGOT, 29);
        byte[] nativeBytes = ItemStackDataCodec.serialize(expected);
        for (byte[] textBytes : List.of(Base64.getEncoder().encode(nativeBytes), legacyBytes(expected))) {
            String text = new String(textBytes, StandardCharsets.US_ASCII);
            assertPreserved(expected, DataUtils.deserializeItemStack(text));
            assertPreserved(expected, DataUtils.deserializeStoredItemStack(text));
            assertPreserved(expected, DataUtils.deserializeItemStack(textBytes));
        }
    }

    @Test
    void recordSetReadsTextAndBinaryColumnsWithoutRewritingEither() throws Exception {
        var expected = oldItem("ELECTRIC_MOTOR", Material.IRON_INGOT, 29);
        for (byte[] stored : representations(expected)) {
            var binaryRecord = new RecordSet();
            binaryRecord.put(FieldKey.INVENTORY_ITEM, stored);
            binaryRecord.readonly();
            assertPreserved(expected, binaryRecord.getItemStack(FieldKey.INVENTORY_ITEM));
            assertArrayEquals(stored, (byte[]) binaryRecord.getValue(FieldKey.INVENTORY_ITEM));
            if (!ItemStackDataCodec.isCurrent(stored)) {
                var textRecord = new RecordSet();
                String text = new String(stored, StandardCharsets.US_ASCII);
                textRecord.put(FieldKey.INVENTORY_ITEM, text);
                textRecord.readonly();
                assertPreserved(expected, textRecord.getItemStack(FieldKey.INVENTORY_ITEM));
                assertEquals(text, textRecord.getValue(FieldKey.INVENTORY_ITEM));
            }
        }
    }

    @Test
    void repeatedUpgradeRoundTripsRetainTheSameItemAndBackpackIdentity() throws Exception {
        var expected = oldItem("SOULBOUND_BACKPACK", Material.CHEST, 1);
        byte[] stored = legacyBytes(expected);
        for (int cycle = 0; cycle < 5; cycle++) {
            var item = ItemStackDataCodec.deserialize(stored);
            assertPreserved(expected, item);
            stored = ItemStackDataCodec.serialize(item);
            assertArrayEquals(HEADER, Arrays.copyOf(stored, HEADER.length));
        }
    }

    @Test
    void unknownAddonIdentityAndPayloadRemainOpaque() throws Exception {
        var expected = oldItem("MISSING_ADDON_MACHINE_OLD_ID", Material.IRON_BLOCK, 17);
        for (byte[] stored : representations(expected)) {
            var restored = ItemStackDataCodec.deserialize(stored);
            assertPreserved(expected, restored);
            assertEquals("MISSING_ADDON_MACHINE_OLD_ID", restored.getItemMeta()
                    .getPersistentDataContainer().get(ITEM_ID, PersistentDataType.STRING));
        }
    }

    @Test
    void restoresItemMetaDeserializerAfterSuccessfulAndFailedLegacyReads() throws Exception {
        var alias = ConfigurationSerialization.getClassByAlias("ItemMeta");
        assertNotNull(alias);
        var expected = oldItem("ELECTRIC_MOTOR", Material.IRON_INGOT, 1);
        assertPreserved(expected, ItemStackDataCodec.deserialize(legacyBytes(expected)));
        assertSame(alias, ConfigurationSerialization.getClassByAlias("ItemMeta"));

        byte[] malformed = Base64.getEncoder().encode(new byte[] {(byte) 0xac, (byte) 0xed, 0, 5});
        assertThrows(Exception.class, () -> ItemStackDataCodec.deserialize(malformed));
        assertSame(alias, ConfigurationSerialization.getClassByAlias("ItemMeta"));
        assertPreserved(expected, ItemStackDataCodec.deserialize(legacyBytes(expected)));
    }

    @Test
    void rejectsCorruptOrRecursivelyWrappedPayloadsRatherThanReturningEmptyItems() throws Exception {
        byte[] nativeBytes = ItemStackDataCodec.serialize(new ItemStack(Material.STONE, 11));
        for (byte[] malformed : List.of(
                HEADER.clone(),
                Base64.getEncoder().encode(HEADER),
                "not-valid-base64".getBytes(StandardCharsets.US_ASCII),
                Base64.getEncoder().encode(Base64.getEncoder().encode(nativeBytes)))) {
            assertThrows(Exception.class, () -> ItemStackDataCodec.deserialize(malformed));
        }
    }

    @Test
    void keepsTheNativeWriterFormatAndEmptySlotContractUnchanged() {
        var item = new ItemStack(Material.DIAMOND, 37);
        byte[] written = ItemStackDataCodec.serialize(item);
        assertArrayEquals(HEADER, Arrays.copyOf(written, HEADER.length));
        assertArrayEquals(item.serializeAsBytes(), Arrays.copyOfRange(written, HEADER.length, written.length));
        assertTrue(ItemStackDataCodec.isCurrent(written));
        assertFalse(ItemStackDataCodec.isLegacy(written));
        assertNull(DataUtils.deserializeItemStack((byte[]) null));
        assertNull(DataUtils.deserializeItemStack(new byte[0]));
        assertNull(DataUtils.deserializeStoredItemStack("  \n"));
    }

    private static ItemStack oldItem(String id, Material material, int amount) {
        var item = new ItemStack(material, amount);
        var meta = item.getItemMeta();
        meta.displayName(Component.text("Existing player item"));
        meta.lore(List.of(Component.text("Old lore stays intact"), Component.text("Charge: 123.5 J")));
        var pdc = meta.getPersistentDataContainer();
        pdc.set(ITEM_ID, PersistentDataType.STRING, id);
        pdc.set(BACKPACK_ID, PersistentDataType.STRING, "1f017f3a-8442-4ef2-9fba-456789abcdef");
        pdc.set(OWNER_ID, PersistentDataType.STRING, "2f017f3a-8442-4ef2-9fba-456789abcdef");
        pdc.set(CHARGE, PersistentDataType.FLOAT, 123.5F);
        pdc.set(SOULBOUND, PersistentDataType.BYTE, (byte) 1);
        pdc.set(key("legacyaddon:opaque"), PersistentDataType.STRING, "old data: \"quoted\" \\ path\nnext line");
        pdc.set(key("legacyaddon:count"), PersistentDataType.LONG, 9_000_000_001L);
        pdc.set(key("legacyaddon:bytes"), PersistentDataType.BYTE_ARRAY, new byte[] {0, -1, 42, 127});
        pdc.set(key("legacyaddon:integers"), PersistentDataType.INTEGER_ARRAY, new int[] {0, -1, 42});
        item.setItemMeta(meta);
        return item;
    }

    private static List<byte[]> representations(ItemStack expected) throws Exception {
        byte[] current = ItemStackDataCodec.serialize(expected);
        byte[] legacy = legacyBytes(expected);
        return List.of(current, Base64.getEncoder().encode(current), Base64.getMimeEncoder(32, new byte[] {'\r', '\n'})
                .encode(current), legacy, Base64.getMimeEncoder(32, new byte[] {'\r', '\n'})
                .encode(Base64.getDecoder().decode(legacy)));
    }

    private static void assertPreserved(ItemStack expected, ItemStack actual) {
        assertNotNull(actual);
        assertEquals(expected.getType(), actual.getType());
        assertEquals(expected.getAmount(), actual.getAmount());
        // ItemMeta equality in a mock can compare primitive arrays by identity.
        // Compare the entire serialized metadata tree, including keys and scalar
        // types, and compare primitive arrays by content instead.
        assertStoredValue(expected.getItemMeta().serialize(), actual.getItemMeta().serialize(), "ItemMeta");
    }

    private static void assertStoredValue(Object expected, Object actual, String path) {
        if (expected instanceof byte[] bytes) {
            assertArrayEquals(bytes, assertInstanceOf(byte[].class, actual, path), path);
        } else if (expected instanceof int[] ints) {
            assertArrayEquals(ints, assertInstanceOf(int[].class, actual, path), path);
        } else if (expected instanceof long[] longs) {
            assertArrayEquals(longs, assertInstanceOf(long[].class, actual, path), path);
        } else if (expected instanceof Map<?, ?> map) {
            var actualMap = assertInstanceOf(Map.class, actual, path);
            assertEquals(map.keySet(), actualMap.keySet(), path);
            map.forEach((key, value) -> assertStoredValue(value, actualMap.get(key), path + "." + key));
        } else if (expected instanceof List<?> list) {
            var actualList = assertInstanceOf(List.class, actual, path);
            assertEquals(list.size(), actualList.size(), path);
            for (int index = 0; index < list.size(); index++) {
                assertStoredValue(list.get(index), actualList.get(index), path + "[" + index + "]");
            }
        } else {
            assertEquals(expected, actual, path);
        }
    }

    @SuppressWarnings("deprecation") // Reproduce the historical storage format, never used in production writing.
    private static byte[] legacyBytes(ItemStack item) throws Exception {
        try (var bytes = new ByteArrayOutputStream(); var output = new BukkitObjectOutputStream(bytes)) {
            output.writeObject(item);
            output.flush();
            return Base64.getEncoder().encode(bytes.toByteArray());
        }
    }

    private static NamespacedKey key(String value) {
        return java.util.Objects.requireNonNull(NamespacedKey.fromString(value));
    }
}
