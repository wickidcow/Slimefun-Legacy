package io.github.thebusybiscuit.slimefun4.implementation.guide.enhanced;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.thebusybiscuit.slimefun4.api.recipes.machine.MachineInputFillRecipe;
import io.github.thebusybiscuit.slimefun4.api.recipes.machine.MachineRecipeDisplay;
import io.github.thebusybiscuit.slimefun4.api.recipes.machine.MachineRecipeIngredient;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class TestFastMachinesInputFillAdapter {

    private LegacyMachineInputFillAdapters.FastMachinesInputFillAdapter adapter;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        JavaPlugin plugin = MockBukkit.createMockPlugin();
        adapter = new LegacyMachineInputFillAdapters.FastMachinesInputFillAdapter(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void resolvesSelectedFastMachinesChoiceAndProtectsControlSlots() {
        FastStyleMachine machine = new FastStyleMachine(validInputSlots());
        MachineRecipeDisplay display = MachineRecipeDisplay.builder()
                .addInput(new ItemStack(Material.REDSTONE, 4))
                .addIngredient(new MachineRecipeIngredient(
                        List.of(new ItemStack(Material.GOLD_INGOT, 3), new ItemStack(Material.IRON_INGOT, 2))))
                .addOutput(new ItemStack(Material.DIAMOND))
                .build();

        MachineInputFillRecipe resolved = adapter.resolveFromObject(
                machine, display, new int[] {0, 0}, TestFastMachinesInputFillAdapter::sameType, ItemStack::isSimilar);

        assertNotNull(resolved);
        assertEquals(2, resolved.getIngredients().size());
        assertEquals(Material.REDSTONE, resolved.getIngredients().get(0).getType());
        assertEquals(4, resolved.getIngredients().get(0).getAmount());
        assertEquals(Material.GOLD_INGOT, resolved.getIngredients().get(1).getType());
        assertEquals(3, resolved.getIngredients().get(1).getAmount());
        assertArrayEquals(validInputSlots(), resolved.getInputSlots());
        assertArrayEquals(slotRange(36, 53), resolved.getProtectedSlots());
        assertEquals("FastMachines adapter", resolved.getLabel());
    }

    @Test
    void rejectsRecipeWhenFastMachinesOutputDoesNotMatch() {
        FastStyleMachine machine = new FastStyleMachine(validInputSlots());
        MachineRecipeDisplay display = MachineRecipeDisplay.builder()
                .addIngredient(new MachineRecipeIngredient(
                        List.of(new ItemStack(Material.IRON_INGOT, 2), new ItemStack(Material.GOLD_INGOT, 3))))
                .addInput(new ItemStack(Material.REDSTONE, 4))
                .addOutput(new ItemStack(Material.EMERALD))
                .build();

        assertNull(adapter.resolveFromObject(
                machine, display, new int[] {0, 0}, TestFastMachinesInputFillAdapter::sameType, ItemStack::isSimilar));
    }

    @Test
    void rejectsDisplayedAlternativeWithWrongRequiredAmount() {
        FastStyleMachine machine = new FastStyleMachine(validInputSlots());
        MachineRecipeDisplay display = MachineRecipeDisplay.builder()
                .addIngredient(new MachineRecipeIngredient(
                        List.of(new ItemStack(Material.IRON_INGOT, 1), new ItemStack(Material.GOLD_INGOT, 3))))
                .addInput(new ItemStack(Material.REDSTONE, 4))
                .addOutput(new ItemStack(Material.DIAMOND))
                .build();

        assertNull(adapter.resolveFromObject(
                machine, display, new int[] {0, 0}, TestFastMachinesInputFillAdapter::sameType, ItemStack::isSimilar));
    }

    @Test
    void rejectsUnexpectedFastMachinesInputLayout() {
        int[] unsafeSlots = validInputSlots();
        unsafeSlots[unsafeSlots.length - 1] = 36;
        FastStyleMachine machine = new FastStyleMachine(unsafeSlots);
        MachineRecipeDisplay display = MachineRecipeDisplay.builder()
                .addIngredient(new MachineRecipeIngredient(
                        List.of(new ItemStack(Material.IRON_INGOT, 2), new ItemStack(Material.GOLD_INGOT, 3))))
                .addInput(new ItemStack(Material.REDSTONE, 4))
                .addOutput(new ItemStack(Material.DIAMOND))
                .build();

        assertNull(adapter.resolveFromObject(
                machine, display, new int[] {0, 0}, TestFastMachinesInputFillAdapter::sameType, ItemStack::isSimilar));
    }

    private static boolean sameType(ItemStack actual, ItemStack expected) {
        return actual != null && actual.getType() == expected.getType();
    }

    private static int[] validInputSlots() {
        return slotRange(0, 35);
    }

    private static int[] slotRange(int first, int last) {
        int[] slots = new int[last - first + 1];
        for (int index = 0; index < slots.length; index++) {
            slots[index] = first + index;
        }
        return slots;
    }

    public static final class FastStyleMachine {

        private final int[] inputSlots;
        private final List<FastStyleRecipe> recipes = List.of(new FastStyleRecipe());

        FastStyleMachine(int[] inputSlots) {
            this.inputSlots = inputSlots.clone();
        }

        public int[] getInputSlots() {
            return inputSlots.clone();
        }

        public List<FastStyleRecipe> getRecipes() {
            return recipes;
        }
    }

    public static final class FastStyleRecipe {

        public List<FastStyleChoice> getInputs() {
            Map<FastStyleWrapper, Integer> metals = new LinkedHashMap<>();
            metals.put(new FastStyleWrapper(new ItemStack(Material.IRON_INGOT)), 2);
            metals.put(new FastStyleWrapper(new ItemStack(Material.GOLD_INGOT)), 3);

            Map<FastStyleWrapper, Integer> redstone = new LinkedHashMap<>();
            redstone.put(new FastStyleWrapper(new ItemStack(Material.REDSTONE)), 4);
            return List.of(new FastStyleChoice(metals), new FastStyleChoice(redstone));
        }

        public List<ItemStack> getOutputs() {
            return List.of(new ItemStack(Material.DIAMOND));
        }
    }

    public static final class FastStyleChoice {

        private final Map<FastStyleWrapper, Integer> choices;

        FastStyleChoice(Map<FastStyleWrapper, Integer> choices) {
            this.choices = choices;
        }

        public Map<FastStyleWrapper, Integer> getChoices() {
            return choices;
        }
    }

    public static final class FastStyleWrapper {

        private final ItemStack baseItem;

        FastStyleWrapper(ItemStack baseItem) {
            this.baseItem = baseItem;
        }

        public ItemStack getBaseItem() {
            return baseItem.clone();
        }
    }
}
