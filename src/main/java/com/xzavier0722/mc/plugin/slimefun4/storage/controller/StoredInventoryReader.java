package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import com.xzavier0722.mc.plugin.slimefun4.storage.common.FieldKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordSet;
import java.util.List;
import java.util.Objects;
import org.bukkit.inventory.ItemStack;

/** Stages a whole stored inventory without modifying its records or publishing a partial menu. */
final class StoredInventoryReader {
    private StoredInventoryReader() {}

    static ItemStack[] read(List<RecordSet> records, int size, String owner) {
        Objects.requireNonNull(records, "records");
        if (size < 1 || size > 54) {
            throw refused(owner, "invalid inventory size", null);
        }
        ItemStack[] inventory = new ItemStack[size];
        boolean[] seen = new boolean[size];
        for (RecordSet record : records) {
            String slotLabel = "unknown";
            try {
                slotLabel = record.getString(FieldKey.INVENTORY_SLOT);
                int slot = record.getInt(FieldKey.INVENTORY_SLOT);
                if (slot < 0 || slot >= inventory.length || seen[slot]) {
                    throw new IllegalArgumentException("Stored slot is out of range or duplicated");
                }
                seen[slot] = true;
                Object raw = record.getValue(FieldKey.INVENTORY_ITEM);
                if (raw == null
                        || raw instanceof byte[] bytes && bytes.length == 0
                        || raw instanceof String text && text.isBlank()) {
                    continue; // Historical explicit empty-slot representations remain valid.
                }
                if (!(raw instanceof String) && !(raw instanceof byte[])) {
                    throw new IllegalArgumentException("Unsupported stored item representation");
                }
                ItemStack item = record.getItemStack(FieldKey.INVENTORY_ITEM);
                if (item == null || item.isEmpty() || item.getType().isAir() || item.getAmount() <= 0) {
                    throw new IllegalStateException("Non-empty item data did not decode to a usable item");
                }
                inventory[slot] = item;
            } catch (RuntimeException | LinkageError failure) {
                throw refused(owner, "slot " + slotLabel, failure);
            }
        }
        return inventory;
    }

    static boolean hasItems(ItemStack[] inventory) {
        for (ItemStack item : inventory) {
            if (item != null) {
                return true;
            }
        }
        return false;
    }

    static IllegalStateException refused(String owner, String reason, Throwable cause) {
        return new IllegalStateException(
                "Refused incomplete inventory load [" + owner + ", " + reason
                        + "]; original stored records were retained. Restore the required addon or repair a backup before retrying.",
                cause);
    }
}
