package com.xzavier0722.mc.plugin.slimefun4.storage.common;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Uses the actual codec with MockBukkit items; native Paper coverage is separately required. */
class RecordSetBinaryItemTest {
    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void exportedBytesCannotCorruptAnExistingItem(boolean mapExport) {
        var item = new ItemStack(Material.DIAMOND, 37);
        var meta = item.getItemMeta();
        meta.displayName(Component.text("Existing owner item"));
        meta.lore(List.of(Component.text("Keep the original lore")));
        var pdc = meta.getPersistentDataContainer();
        pdc.set(key("slimefun:slimefun_item"), PersistentDataType.STRING, "UNREGISTERED_OLD_ID");
        pdc.set(key("slimefun:item_charge"), PersistentDataType.FLOAT, 123.4567F);
        pdc.set(key("other:large_count"), PersistentDataType.LONG, 9_007_199_254_740_993L);
        pdc.set(key("other:opaque"), PersistentDataType.BYTE_ARRAY, new byte[] {0, -1, 4});
        item.setItemMeta(meta);
        var record = new RecordSet();
        record.put(FieldKey.INVENTORY_ITEM, item);
        record.readonly();
        String before = record.getString(FieldKey.INVENTORY_ITEM);
        byte[] exported = (byte[])
                (mapExport
                        ? record.getAllValues().get(FieldKey.INVENTORY_ITEM)
                        : record.getValue(FieldKey.INVENTORY_ITEM));
        Arrays.fill(exported, (byte) 0);
        assertEquals(before, record.getString(FieldKey.INVENTORY_ITEM), "The stored item bytes changed");
        ItemStack restored = record.getItemStack(FieldKey.INVENTORY_ITEM);
        assertNotNull(restored);
        assertEquals(item.getType(), restored.getType());
        assertEquals(37, restored.getAmount());
        assertEquals(meta.displayName(), restored.getItemMeta().displayName());
        assertEquals(meta.lore(), restored.getItemMeta().lore());
        var actual = restored.getItemMeta().getPersistentDataContainer();
        assertEquals("UNREGISTERED_OLD_ID", actual.get(key("slimefun:slimefun_item"), PersistentDataType.STRING));
        assertEquals(
                Float.floatToRawIntBits(123.4567F),
                Float.floatToRawIntBits(
                        Objects.requireNonNull(actual.get(key("slimefun:item_charge"), PersistentDataType.FLOAT))));
        assertEquals(
                Long.valueOf(9_007_199_254_740_993L), actual.get(key("other:large_count"), PersistentDataType.LONG));
        assertArrayEquals(new byte[] {0, -1, 4}, actual.get(key("other:opaque"), PersistentDataType.BYTE_ARRAY));
    }

    private static NamespacedKey key(String name) {
        return Objects.requireNonNull(NamespacedKey.fromString(name));
    }
}
