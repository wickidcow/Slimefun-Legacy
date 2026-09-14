package com.xzavier0722.mc.plugin.slimefun4.storage.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class DataUtilsEmptyItemStackTest {
    @Test
    void detectsNullAndAirAsEmpty() {
        assertTrue(DataUtils.isEmptyItemStack(null));
        assertTrue(DataUtils.isEmptyItemStack(new ItemStack(Material.AIR)));
    }

    @Test
    void detectsZeroAmountAsEmpty() {
        assertTrue(DataUtils.isEmptyItemStack(new ItemStack(Material.STONE, 0)));
    }

    @Test
    void keepsRealItemsSerializable() {
        assertFalse(DataUtils.isEmptyItemStack(new ItemStack(Material.STONE)));
    }
}
