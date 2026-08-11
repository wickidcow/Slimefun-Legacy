package io.github.thebusybiscuit.slimefun4.implementation.items.electric.machines.enchanting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

class EnchantmentMachineRuntimeTest {

    @Test
    void processingTimeScalesWithEnchantmentsAndSpeed() {
        assertEquals(75, EnchantmentMachineRuntime.processingTicks(75, 1, 1));
        assertEquals(150, EnchantmentMachineRuntime.processingTicks(75, 2, 1));
        assertEquals(75, EnchantmentMachineRuntime.processingTicks(75, 2, 2));
        assertEquals(37, EnchantmentMachineRuntime.processingTicks(75, 1, 2));
    }

    @Test
    void processingTimeNeverBecomesZero() {
        assertEquals(1, EnchantmentMachineRuntime.processingTicks(0, 0, 0));
        assertEquals(1, EnchantmentMachineRuntime.processingTicks(1, 1, Integer.MAX_VALUE));
    }

    @Test
    void recipeInputCloneConsumesExactlyOneWithoutMutatingSourceStack() {
        ItemStack source = new ItemStack(Material.DIAMOND, 64);

        ItemStack single = EnchantmentMachineRuntime.one(source);

        assertNotSame(source, single);
        assertEquals(64, source.getAmount());
        assertEquals(1, single.getAmount());
        assertEquals(Material.DIAMOND, single.getType());
    }
}
