package io.github.thebusybiscuit.slimefun4.implementation.operations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineFuel;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineRecipe;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class MachineOperationSnapshotTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void craftingOperationSnapshotsMutableRecipeStacks() {
        ItemStack ingredient = new ItemStack(Material.IRON_INGOT, 3);
        ItemStack result = new ItemStack(Material.DIAMOND, 2);
        MachineRecipe recipe = new MachineRecipe(40, new ItemStack[] {ingredient}, new ItemStack[] {result});
        int expectedTicks = recipe.getTicks();

        CraftingOperation operation = new CraftingOperation(recipe);

        ingredient.setAmount(1);
        result.setAmount(64);
        recipe.getInput()[0] = new ItemStack(Material.DIRT, 16);
        recipe.getOutput()[0] = new ItemStack(Material.COBBLESTONE, 16);
        recipe.setTicks(1);

        assertEquals(Material.IRON_INGOT, operation.getIngredients()[0].getType());
        assertEquals(3, operation.getIngredients()[0].getAmount());
        assertEquals(Material.DIAMOND, operation.getResults()[0].getType());
        assertEquals(2, operation.getResults()[0].getAmount());
        assertEquals(expectedTicks, operation.getTotalTicks());
    }

    @Test
    void craftingOperationRecordsCancellation() {
        MachineRecipe recipe = new MachineRecipe(
                40,
                new ItemStack[] {new ItemStack(Material.DIAMOND_SWORD), new ItemStack(Material.BOOK)},
                new ItemStack[] {new ItemStack(Material.DIAMOND_SWORD), new ItemStack(Material.ENCHANTED_BOOK)});
        CraftingOperation operation = new CraftingOperation(recipe);

        assertFalse(operation.isCancelled());
        operation.onCancel(null);
        assertTrue(operation.isCancelled());
        assertEquals(Material.DIAMOND_SWORD, operation.getIngredients()[0].getType());
        assertEquals(Material.BOOK, operation.getIngredients()[1].getType());
    }

    @Test
    void fuelOperationSnapshotsMutableRecipeStacks() {
        ItemStack ingredient = new ItemStack(Material.COAL, 4);
        ItemStack result = new ItemStack(Material.BUCKET, 1);
        MachineFuel recipe = new MachineFuel(80, ingredient, result);
        int expectedTicks = recipe.getTicks();

        FuelOperation operation = new FuelOperation(recipe);

        ingredient.setAmount(1);
        result.setAmount(8);

        assertEquals(Material.COAL, operation.getIngredient().getType());
        assertEquals(4, operation.getIngredient().getAmount());
        assertEquals(Material.BUCKET, operation.getResult().getType());
        assertEquals(1, operation.getResult().getAmount());
        assertEquals(expectedTicks, operation.getTotalTicks());
    }
}
