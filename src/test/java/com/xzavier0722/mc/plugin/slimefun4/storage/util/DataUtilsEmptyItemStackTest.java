package com.xzavier0722.mc.plugin.slimefun4.storage.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class DataUtilsEmptyItemStackTest {

    @BeforeAll
    static void setUpBukkit() {
        MockBukkit.mock();
    }

    @AfterAll
    static void tearDownBukkit() {
        MockBukkit.unmock();
    }

    @Test
    void nullItemSerializesAsEmpty() {
        assertEquals(0, DataUtils.serializeItemStackBytes(null).length);
    }

    @Test
    void airItemSerializesAsEmpty() {
        assertEquals(0, DataUtils.serializeItemStackBytes(new ItemStack(Material.AIR)).length);
    }

    @Test
    void zeroAmountItemSerializesAsEmpty() {
        ItemStack item = new ItemStack(Material.STONE);
        item.setAmount(0);

        assertEquals(0, DataUtils.serializeItemStackBytes(item).length);
    }

    @Test
    void normalItemStillProducesVersionedPayload() {
        byte[] data = ItemStackDataCodec.serialize(new ItemStack(Material.STONE));

        assertTrue(data.length > 4);
        assertTrue(ItemStackDataCodec.isCurrent(data));
    }
}
