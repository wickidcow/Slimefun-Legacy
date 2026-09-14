package com.xzavier0722.mc.plugin.slimefun4.storage.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class DataUtilsEmptyItemStackTest {
    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void detectsNullAndAirAsEmpty() {
        assertTrue(DataUtils.isEmptyItemStack(null));
        assertTrue(DataUtils.isEmptyItemStack(new ItemStack(Material.AIR)));
    }

    @Test
    void detectsZeroAmountAsEmpty() {
        var item = new ItemStack(Material.STONE);
        item.setAmount(0);
        assertTrue(DataUtils.isEmptyItemStack(item));
    }

    @Test
    void keepsRealItemsSerializable() {
        assertFalse(DataUtils.isEmptyItemStack(new ItemStack(Material.STONE)));
    }
}
