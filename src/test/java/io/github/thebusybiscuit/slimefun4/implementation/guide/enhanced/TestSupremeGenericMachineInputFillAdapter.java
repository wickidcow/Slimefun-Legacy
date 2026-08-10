package io.github.thebusybiscuit.slimefun4.implementation.guide.enhanced;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.thebusybiscuit.slimefun4.api.recipes.machine.MachineInputFillRecipe;
import io.github.thebusybiscuit.slimefun4.api.recipes.machine.MachineRecipeDisplay;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class TestSupremeGenericMachineInputFillAdapter {

    private LegacyMachineInputFillAdapters.SupremeGenericMachineAdapter adapter;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        JavaPlugin plugin = MockBukkit.createMockPlugin();
        adapter = new LegacyMachineInputFillAdapters.SupremeGenericMachineAdapter(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void resolvesSupremePublicRecipeFieldAndProtectsStatusSlot() {
        SupremeStyleMachine machine = new SupremeStyleMachine();
        MachineRecipeDisplay display = MachineRecipeDisplay.builder()
                .addInput(new ItemStack(Material.REDSTONE, 4))
                .addInput(new ItemStack(Material.IRON_INGOT, 2))
                .addOutput(new ItemStack(Material.DIAMOND))
                .build();

        MachineInputFillRecipe resolved = adapter.resolveFromObject(
                machine,
                display,
                new int[] {0, 0},
                new int[] {10, 11},
                new int[] {20},
                TestSupremeGenericMachineInputFillAdapter::sameType,
                ItemStack::isSimilar);

        assertNotNull(resolved);
        assertEquals(2, resolved.getIngredients().size());
        assertEquals(Material.IRON_INGOT, resolved.getIngredients().get(0).getType());
        assertEquals(2, resolved.getIngredients().get(0).getAmount());
        assertArrayEquals(new int[] {10, 11}, resolved.getInputSlots());
        assertArrayEquals(new int[] {20, 22}, resolved.getProtectedSlots());
        assertEquals("Supreme GenericMachine adapter", resolved.getLabel());
    }

    @Test
    void rejectsDisplayedRecipeThatDoesNotMatchSupremeOutput() {
        SupremeStyleMachine machine = new SupremeStyleMachine();
        MachineRecipeDisplay display = MachineRecipeDisplay.builder()
                .addInput(new ItemStack(Material.IRON_INGOT, 2))
                .addInput(new ItemStack(Material.REDSTONE, 4))
                .addOutput(new ItemStack(Material.EMERALD))
                .build();

        MachineInputFillRecipe resolved = adapter.resolveFromObject(
                machine,
                display,
                new int[] {0, 0},
                new int[] {10, 11},
                new int[] {20},
                TestSupremeGenericMachineInputFillAdapter::sameType,
                ItemStack::isSimilar);

        assertNull(resolved);
    }

    @Test
    void readsOnlyConcreteNonEmptySupremeRecipeStacks() {
        List<ItemStack> stacks = LegacyMachineInputFillAdapters.SupremeGenericMachineAdapter.itemStacks(
                new ItemStack[] {null, new ItemStack(Material.AIR), new ItemStack(Material.COAL, 3)});

        assertEquals(1, stacks.size());
        assertEquals(Material.COAL, stacks.get(0).getType());
        assertEquals(3, stacks.get(0).getAmount());
    }

    private static boolean sameType(ItemStack actual, ItemStack expected) {
        return actual != null && actual.getType() == expected.getType();
    }

    public static final class SupremeStyleMachine {

        public final List<SupremeStyleRecipe> machineRecipes = List.of(new SupremeStyleRecipe());

        public int getStatusSlot() {
            return 22;
        }
    }

    public static final class SupremeStyleRecipe {

        public ItemStack[] getInputNotNull() {
            return new ItemStack[] {new ItemStack(Material.IRON_INGOT, 2), new ItemStack(Material.REDSTONE, 4)};
        }

        public ItemStack[] getOutputNotNull() {
            return new ItemStack[] {new ItemStack(Material.DIAMOND)};
        }
    }
}
