package io.github.thebusybiscuit.slimefun4.implementation.guide.enhanced;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.api.recipes.machine.MachineRecipeDisplay;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

class TestPublicMachineRecipeProvider {

    private ServerMock server;
    private LegacyMachineRecipeProviders.PublicMethodProvider provider;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        Slimefun plugin = MockBukkit.load(Slimefun.class);
        provider = new LegacyMachineRecipeProviders.PublicMethodProvider(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void readsSupremeStylePublicRecipeField() {
        SupremeStyleMachine machine = new SupremeStyleMachine();
        List<MachineRecipeDisplay> recipes = provider.getRecipes(machine, world());

        assertTrue(provider.supports(machine));
        assertEquals(1, recipes.size());
        assertEquals(2, recipes.get(0).getInputs().size());
        assertEquals(Material.DIAMOND, recipes.get(0).getOutputs().get(0).getType());
        assertEquals(40, recipes.get(0).getProcessingTicks());
    }

    @Test
    void readsNumberedInputsAndChanceMetadata() {
        NumberedRecipeMachine machine = new NumberedRecipeMachine();
        List<MachineRecipeDisplay> recipes = provider.getRecipes(machine, world());

        assertEquals(1, recipes.size());
        assertEquals(2, recipes.get(0).getInputs().size());
        assertTrue(recipes.get(0).getLabel().contains("25% chance"));
    }

    @Test
    void readsMapBasedRecipes() {
        MapRecipeMachine machine = new MapRecipeMachine();
        List<MachineRecipeDisplay> recipes = provider.getRecipes(machine, world());

        assertEquals(1, recipes.size());
        assertEquals(Material.IRON_INGOT, recipes.get(0).getInputs().get(0).getChoices().get(0).getType());
        assertEquals(Material.GOLD_INGOT, recipes.get(0).getOutputs().get(0).getType());
    }

    private World world() {
        return server.addSimpleWorld("phase4_compatibility");
    }

    public abstract static class TestMachine extends SlimefunItem {

        TestMachine(String id) {
            super(
                    new ItemGroup(new NamespacedKey("slimefun", id.toLowerCase()), new ItemStack(Material.BOOK)),
                    new SlimefunItemStack(id, Material.FURNACE, id),
                    RecipeType.NULL,
                    new ItemStack[9]);
        }
    }

    public static final class SupremeStyleMachine extends TestMachine {

        public final List<SupremeStyleRecipe> machineRecipes = List.of(new SupremeStyleRecipe());

        SupremeStyleMachine() {
            super("TEST_SUPREME_STYLE_MACHINE");
        }

        public List<SupremeStyleRecipe> getMachineRecipes() {
            return List.of();
        }

        public int getTimeProcess() {
            return 40;
        }
    }

    public static final class SupremeStyleRecipe {

        public ItemStack[] getInput() {
            return new ItemStack[] {
                new ItemStack(Material.IRON_INGOT, 2), new ItemStack(Material.REDSTONE, 4)
            };
        }

        public ItemStack[] getOutput() {
            return new ItemStack[] {new ItemStack(Material.DIAMOND)};
        }
    }

    public static final class NumberedRecipeMachine extends TestMachine {

        public final List<NumberedRecipe> recipes = List.of(new NumberedRecipe());

        NumberedRecipeMachine() {
            super("TEST_NUMBERED_RECIPE_MACHINE");
        }
    }

    public static final class NumberedRecipe {

        public ItemStack getInput1() {
            return new ItemStack(Material.COAL);
        }

        public ItemStack getInput2() {
            return new ItemStack(Material.QUARTZ);
        }

        public ItemStack getOutput() {
            return new ItemStack(Material.EMERALD);
        }

        public int getChance() {
            return 25;
        }
    }

    public static final class MapRecipeMachine extends TestMachine {

        public final Map<ItemStack, ItemStack> recipeMap = new LinkedHashMap<>();

        MapRecipeMachine() {
            super("TEST_MAP_RECIPE_MACHINE");
            recipeMap.put(new ItemStack(Material.IRON_INGOT), new ItemStack(Material.GOLD_INGOT));
        }
    }
}
