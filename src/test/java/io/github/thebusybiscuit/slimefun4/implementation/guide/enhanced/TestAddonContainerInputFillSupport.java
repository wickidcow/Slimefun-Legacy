package io.github.thebusybiscuit.slimefun4.implementation.guide.enhanced;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.thebusybiscuit.slimefun4.api.recipes.machine.MachineRecipeDisplay;
import io.github.thebusybiscuit.slimefun4.api.recipes.machine.MachineRecipeIngredient;
import java.util.List;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineRecipe;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class TestAddonContainerInputFillSupport {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void acceptsRegisteredAddonContainerRecipeInAnyDisplayOrder() {
        MachineRecipe registered = recipe(
                new ItemStack[] {stack(Material.IRON_INGOT, 2), stack(Material.GOLD_INGOT, 1)},
                new ItemStack[] {stack(Material.DIAMOND, 1)});
        MachineRecipeDisplay display = MachineRecipeDisplay.builder()
                .addInput(stack(Material.GOLD_INGOT, 1))
                .addInput(stack(Material.IRON_INGOT, 2))
                .addOutput(stack(Material.DIAMOND, 1))
                .build();

        assertTrue(supports(List.of(registered), display));
        List<ItemStack> requirements = resolve(List.of(registered), display, new int[] {0, 0});
        assertNotNull(requirements);
        assertEquals(2, requirements.size());
        assertEquals(Material.IRON_INGOT, requirements.get(0).getType());
        assertEquals(2, requirements.get(0).getAmount());
    }

    @Test
    void rejectsPublicProviderRecipeThatIsNotRegisteredByContainer() {
        MachineRecipe registered =
                recipe(new ItemStack[] {stack(Material.IRON_INGOT, 1)}, new ItemStack[] {stack(Material.DIAMOND, 1)});
        MachineRecipeDisplay reflectedOnly = MachineRecipeDisplay.builder()
                .addInput(stack(Material.COAL, 1))
                .addOutput(stack(Material.EMERALD, 1))
                .build();

        assertFalse(supports(List.of(registered), reflectedOnly));
        assertNull(resolve(List.of(registered), reflectedOnly, new int[] {0}));
    }

    @Test
    void selectedAlternativeMustMatchRegisteredContainerRecipe() {
        MachineRecipe registered =
                recipe(new ItemStack[] {stack(Material.IRON_INGOT, 1)}, new ItemStack[] {stack(Material.DIAMOND, 1)});
        MachineRecipeDisplay display = MachineRecipeDisplay.builder()
                .addIngredient(new MachineRecipeIngredient(
                        List.of(stack(Material.IRON_INGOT, 1), stack(Material.GOLD_INGOT, 1))))
                .addOutput(stack(Material.DIAMOND, 1))
                .build();

        assertTrue(supports(List.of(registered), display));
        assertNotNull(resolve(List.of(registered), display, new int[] {0}));
        assertNull(resolve(List.of(registered), display, new int[] {1}));
    }

    @Test
    void duplicateIngredientsAndOutputsMustMatchExactly() {
        MachineRecipe registered = recipe(
                new ItemStack[] {stack(Material.REDSTONE, 1), stack(Material.REDSTONE, 1)},
                new ItemStack[] {stack(Material.REPEATER, 1)});
        MachineRecipeDisplay valid = MachineRecipeDisplay.builder()
                .addInput(stack(Material.REDSTONE, 1))
                .addInput(stack(Material.REDSTONE, 1))
                .addOutput(stack(Material.REPEATER, 1))
                .build();
        MachineRecipeDisplay wrongOutput = MachineRecipeDisplay.builder()
                .addInput(stack(Material.REDSTONE, 1))
                .addInput(stack(Material.REDSTONE, 1))
                .addOutput(stack(Material.COMPARATOR, 1))
                .build();

        assertTrue(supports(List.of(registered), valid));
        assertFalse(supports(List.of(registered), wrongOutput));
    }

    private static boolean supports(List<MachineRecipe> recipes, MachineRecipeDisplay display) {
        return LegacyMachineInputFillManager.hasCompatibleRegisteredRecipe(
                recipes, display, TestAddonContainerInputFillSupport::sameType, ItemStack::isSimilar);
    }

    private static List<ItemStack> resolve(
            List<MachineRecipe> recipes, MachineRecipeDisplay display, int[] selectedAlternatives) {
        return LegacyMachineInputFillManager.resolveRegisteredRequirements(
                recipes,
                display,
                selectedAlternatives,
                TestAddonContainerInputFillSupport::sameType,
                ItemStack::isSimilar);
    }

    private static boolean sameType(ItemStack actual, ItemStack expected) {
        return actual != null && actual.getType() == expected.getType();
    }

    private static MachineRecipe recipe(ItemStack[] inputs, ItemStack[] outputs) {
        return new MachineRecipe(1, inputs, outputs);
    }

    private static ItemStack stack(Material material, int amount) {
        return new ItemStack(material, amount);
    }
}
