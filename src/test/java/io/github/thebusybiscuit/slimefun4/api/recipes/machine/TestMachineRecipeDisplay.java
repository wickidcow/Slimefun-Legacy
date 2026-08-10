package io.github.thebusybiscuit.slimefun4.api.recipes.machine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class TestMachineRecipeDisplay {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void createsDefensiveCopies() {
        ItemStack input = new ItemStack(Material.IRON_INGOT, 2);
        ItemStack output = new ItemStack(Material.GOLD_INGOT, 1);

        MachineRecipeDisplay display = MachineRecipeDisplay.builder()
                .addInput(input)
                .addOutput(output)
                .layout(MachineRecipeLayout.SHAPELESS)
                .processingTicks(8)
                .energyPerTick(16)
                .build();

        input.setAmount(64);
        output.setAmount(64);

        ItemStack storedInput = display.getInputs().get(0).getChoices().get(0);
        ItemStack storedOutput = display.getOutputs().get(0);
        assertEquals(2, storedInput.getAmount());
        assertEquals(1, storedOutput.getAmount());
        assertNotSame(storedInput, display.getInputs().get(0).getChoices().get(0));
        assertNotSame(storedOutput, display.getOutputs().get(0));
    }

    @Test
    void rejectsRecipesWithoutOutputs() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MachineRecipeDisplay(
                        List.of(MachineRecipeIngredient.of(new ItemStack(Material.IRON_INGOT))), List.of()));
    }
}
