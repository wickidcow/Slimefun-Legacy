package io.github.thebusybiscuit.slimefun4.implementation.items.electric.machines;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.InventoryReadTestPlugin;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.inventory.DirtyChestMenu;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Electric Smeltery cargo input must keep each ingredient to the stacks it already occupies. */
class TestElectricSmelteryCargoInput {

    // Input slots in order: 10, 11, 19, 20, 28, 29

    private InventoryReadTestPlugin fixture;

    @BeforeEach
    void startServer() {
        fixture = new InventoryReadTestPlugin(MockBukkit.mock());
    }

    @AfterEach
    void stopServer() {
        try {
            if (fixture != null) {
                fixture.close();
            }
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void newIngredientMayUseAnyEmptySlot() {
        DirtyChestMenu menu = newMenu();
        menu.replaceExistingItem(10, new ItemStack(Material.COPPER_INGOT, 64));

        assertArrayEquals(
                new int[] {11, 19, 20, 28, 29},
                ElectricSmeltery.getCargoInputSlots(menu, new ItemStack(Material.IRON_INGOT)));
    }

    @Test
    void presentIngredientIsOnlyToppedUpSmallestStackFirst() {
        DirtyChestMenu menu = newMenu();
        menu.replaceExistingItem(10, new ItemStack(Material.IRON_INGOT, 40));
        menu.replaceExistingItem(19, new ItemStack(Material.IRON_INGOT, 12));

        assertArrayEquals(
                new int[] {19, 10}, ElectricSmeltery.getCargoInputSlots(menu, new ItemStack(Material.IRON_INGOT)));
    }

    @Test
    void presentIngredientWithOnlyFullStacksIsRefusedInsteadOfSpilling() {
        DirtyChestMenu menu = newMenu();
        menu.replaceExistingItem(10, new ItemStack(Material.IRON_INGOT, 64));

        assertArrayEquals(new int[0], ElectricSmeltery.getCargoInputSlots(menu, new ItemStack(Material.IRON_INGOT)));
    }

    @Test
    void fullStackDoesNotBlockTopUpOfAnotherPartialStack() {
        DirtyChestMenu menu = newMenu();
        menu.replaceExistingItem(10, new ItemStack(Material.IRON_INGOT, 64));
        menu.replaceExistingItem(11, new ItemStack(Material.IRON_INGOT, 5));

        assertArrayEquals(
                new int[] {11}, ElectricSmeltery.getCargoInputSlots(menu, new ItemStack(Material.IRON_INGOT)));
    }

    private DirtyChestMenu newMenu() {
        DirtyChestMenu menu = new DirtyChestMenu(new TestPreset());
        menu.setSize(54);
        return menu;
    }

    private static final class TestPreset extends BlockMenuPreset {

        private TestPreset() {
            super("TEST_ELECTRIC_SMELTERY_CARGO", "Electric Smeltery Cargo");
        }

        @Override
        public void init() {
            setSize(54);
        }

        @Override
        public boolean canOpen(Block block, Player player) {
            return true;
        }

        @Override
        public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
            return new int[0];
        }
    }
}
