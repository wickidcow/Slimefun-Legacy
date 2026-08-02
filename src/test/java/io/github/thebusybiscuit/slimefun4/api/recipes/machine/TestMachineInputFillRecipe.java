package io.github.thebusybiscuit.slimefun4.api.recipes.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class TestMachineInputFillRecipe {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void defensivelyCopiesIngredientsAndSlots() {
        List<ItemStack> ingredients = new ArrayList<>();
        ingredients.add(new ItemStack(Material.IRON_INGOT, 3));
        int[] inputSlots = {10, 11};
        int[] protectedSlots = {20, 22};

        MachineInputFillRecipe recipe = MachineInputFillRecipe.builder()
                .ingredients(ingredients)
                .inputSlots(inputSlots)
                .protectedSlots(protectedSlots)
                .label("test")
                .build();

        ingredients.get(0).setAmount(1);
        inputSlots[0] = 99;
        protectedSlots[0] = 99;

        assertEquals(3, recipe.getIngredients().get(0).getAmount());
        assertEquals(10, recipe.getInputSlots()[0]);
        assertEquals(20, recipe.getProtectedSlots()[0]);

        ItemStack returned = recipe.getIngredients().get(0);
        returned.setAmount(2);
        assertNotEquals(2, recipe.getIngredients().get(0).getAmount());
    }

    @Test
    void clampsAdapterMaximumAgainstServerLimit() {
        MachineInputFillRecipe limited = MachineInputFillRecipe.builder()
                .addIngredient(new ItemStack(Material.REDSTONE))
                .inputSlots(1)
                .maximumSets(8)
                .build();
        MachineInputFillRecipe disabled = MachineInputFillRecipe.builder()
                .addIngredient(new ItemStack(Material.REDSTONE))
                .inputSlots(1)
                .allowMaximumFill(false)
                .build();

        assertEquals(8, limited.resolveMaximumSets(64));
        assertEquals(4, limited.resolveMaximumSets(4));
        assertEquals(1, disabled.resolveMaximumSets(64));
    }

    @Test
    void rejectsEmptyIngredientsAndInputSlots() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MachineInputFillRecipe.builder().inputSlots(1).build());
        assertThrows(
                IllegalArgumentException.class,
                () -> MachineInputFillRecipe.builder()
                        .addIngredient(new ItemStack(Material.IRON_INGOT))
                        .build());
        assertThrows(
                IllegalArgumentException.class,
                () -> MachineInputFillRecipe.builder()
                        .addIngredient(new ItemStack(Material.AIR))
                        .inputSlots(1)
                        .build());
    }
}
