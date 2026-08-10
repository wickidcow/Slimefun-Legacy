package io.github.thebusybiscuit.slimefun4.implementation.guide.enhanced;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class TestMachineInputFillPlan {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void topsUpPartiallyFilledInputSlot() {
        ItemStack[] player = {stack(Material.IRON_INGOT, 1), stack(Material.IRON_INGOT, 2)};
        ItemStack[] machine = {stack(Material.IRON_INGOT, 1), null};

        LegacyMachineInputFillManager.FillPlan plan = plan(player, machine, List.of(stack(Material.IRON_INGOT, 3)), 1);

        assertTrue(plan.success());
        assertEquals(3, plan.machineContents()[0].getAmount());
        assertNull(plan.playerContents()[0]);
        assertEquals(1, plan.playerContents()[1].getAmount());
        assertEquals(2, plan.movedItems());
    }

    @Test
    void keepsDuplicateIngredientsInSeparateMachineSlots() {
        ItemStack[] player = {stack(Material.IRON_INGOT, 1)};
        ItemStack[] machine = {stack(Material.IRON_INGOT, 1), null};

        LegacyMachineInputFillManager.FillPlan plan =
                plan(player, machine, List.of(stack(Material.IRON_INGOT, 1), stack(Material.IRON_INGOT, 1)), 1);

        assertTrue(plan.success());
        assertEquals(1, plan.machineContents()[0].getAmount());
        assertEquals(1, plan.machineContents()[1].getAmount());
        assertNull(plan.playerContents()[0]);
    }

    @Test
    void refusesUnrelatedInputItems() {
        ItemStack[] player = {stack(Material.IRON_INGOT, 1)};
        ItemStack[] machine = {stack(Material.DIAMOND, 1), null};

        LegacyMachineInputFillManager.FillPlan plan = plan(player, machine, List.of(stack(Material.IRON_INGOT, 1)), 1);

        assertFalse(plan.success());
        assertTrue(plan.message().contains("unrelated"));
        assertEquals(Material.DIAMOND, machine[0].getType());
        assertEquals(1, player[0].getAmount());
    }

    @Test
    void fillsMaximumCompleteRecipeSets() {
        ItemStack[] player = {stack(Material.IRON_INGOT, 10), stack(Material.GOLD_INGOT, 3)};
        ItemStack[] machine = {null, null};

        LegacyMachineInputFillManager.FillPlan plan = LegacyMachineInputFillManager.planMaximum(
                player,
                machine,
                List.of(stack(Material.IRON_INGOT, 1), stack(Material.GOLD_INGOT, 1)),
                64,
                TestMachineInputFillPlan::sameType,
                ItemStack::isSimilar,
                ItemStack::getMaxStackSize,
                ignored -> true);

        assertTrue(plan.success());
        assertEquals(3, plan.sets());
        assertEquals(3, plan.machineContents()[0].getAmount());
        assertEquals(3, plan.machineContents()[1].getAmount());
        assertEquals(7, plan.playerContents()[0].getAmount());
        assertNull(plan.playerContents()[1]);
    }

    private static LegacyMachineInputFillManager.FillPlan plan(
            ItemStack[] player, ItemStack[] machine, List<ItemStack> requirements, int sets) {
        return LegacyMachineInputFillManager.plan(
                player,
                machine,
                requirements,
                sets,
                TestMachineInputFillPlan::sameType,
                ItemStack::isSimilar,
                ItemStack::getMaxStackSize,
                ignored -> true);
    }

    private static boolean sameType(ItemStack actual, ItemStack expected) {
        return actual != null && actual.getType() == expected.getType();
    }

    private static ItemStack stack(Material material, int amount) {
        return new ItemStack(material, amount);
    }
}
