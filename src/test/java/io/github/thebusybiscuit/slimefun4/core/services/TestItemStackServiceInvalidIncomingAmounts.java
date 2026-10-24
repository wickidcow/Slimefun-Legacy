package io.github.thebusybiscuit.slimefun4.core.services;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.InventoryReadTestPlugin;
import io.github.thebusybiscuit.slimefun4.api.items.virtual.VirtualItemHandler.InventoryContext;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Malformed addon outputs must never grow, shrink or replace physical inventory contents. */
class TestItemStackServiceInvalidIncomingAmounts {

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
    void negativeOutputCannotDrainExistingStack() {
        Inventory inventory = Bukkit.createInventory(null, 18);
        inventory.setItem(0, new ItemStack(Material.STONE, 8));
        ItemStack[] before = inventory.getContents();
        ItemStack negative = new ItemStack(Material.STONE, 1);
        negative.setAmount(-3);
        ItemStackService service = new ItemStackService();

        ItemStack rest = service.addItem(inventory, negative, InventoryContext.CARGO_INSERT, 0);

        assertNotNull(rest);
        assertNotSame(negative, rest);
        assertEquals(negative, rest, "A malformed amount must be returned intact");
        assertEquals(-3, negative.getAmount());
        assertArrayEquals(before, inventory.getContents(), "A negative insertion must not remove items");
    }

    @Test
    void zeroAmountCannotCreateGhostStackInEmptySlot() {
        Inventory inventory = Bukkit.createInventory(null, 18);
        ItemStack[] before = inventory.getContents();
        ItemStack zero = new ItemStack(Material.COBBLESTONE, 1);
        zero.setAmount(0);
        ItemStackService service = new ItemStackService();

        ItemStack rest = service.addItem(inventory, zero, InventoryContext.MACHINE_OUTPUT, 0);

        assertNotNull(rest);
        assertNotSame(zero, rest);
        assertEquals(zero, rest);
        assertArrayEquals(before, inventory.getContents(), "Zero-amount insertion must not write a ghost stack");
    }

    @Test
    void airInputDoesNotMutateDefaultOrExplicitInventory() {
        Inventory inventory = Bukkit.createInventory(null, 18);
        inventory.setItem(0, new ItemStack(Material.DIAMOND, 3));
        ItemStack[] before = inventory.getContents();
        ItemStack air = new ItemStack(Material.AIR);
        ItemStackService service = new ItemStackService();

        for (int[] slots : new int[][] {{}, {0}, {0, 1}}) {
            ItemStack rest = service.addItem(inventory, air, InventoryContext.MACHINE_OUTPUT, slots);
            assertNotNull(rest);
            assertNotSame(air, rest);
            assertEquals(air, rest);
            assertArrayEquals(before, inventory.getContents());
        }
    }

    @Test
    void malformedCountsCannotPassCapacityChecks() {
        Inventory inventory = Bukkit.createInventory(null, 18);
        inventory.setItem(0, new ItemStack(Material.DIAMOND, 64));
        ItemStack[] before = inventory.getContents();
        ItemStack negative = new ItemStack(Material.STONE, 1);
        negative.setAmount(-3);
        ItemStack zero = new ItemStack(Material.STONE, 1);
        zero.setAmount(0);
        ItemStack positive = new ItemStack(Material.STONE, 2);
        ItemStackService service = new ItemStackService();

        assertFalse(service.fits(inventory, negative, InventoryContext.MACHINE_OUTPUT, 0, 1));
        assertFalse(service.fitAll(
                inventory, new ItemStack[] {negative}, InventoryContext.MACHINE_OUTPUT, 0, 1));
        assertFalse(service.fitAll(
                inventory, new ItemStack[] {positive, negative}, InventoryContext.MACHINE_OUTPUT, 0, 1));
        assertFalse(service.fits(inventory, zero, InventoryContext.MACHINE_OUTPUT, 1));
        assertFalse(service.fits(inventory, new ItemStack(Material.AIR), InventoryContext.MACHINE_OUTPUT, 1));
        assertArrayEquals(before, inventory.getContents(), "No preflight may alter any slot");
    }

    @Test
    void zeroAmountRecipePlaceholdersAreIgnoredWithoutGhostContents() {
        Inventory inventory = Bukkit.createInventory(null, 18);
        inventory.setItem(0, new ItemStack(Material.DIAMOND, 64));
        ItemStack[] before = inventory.getContents();
        ItemStack zero = new ItemStack(Material.STONE, 1);
        zero.setAmount(0);
        ItemStack valid = new ItemStack(Material.STONE, 2);
        ItemStackService service = new ItemStackService();

        assertTrue(service.fitAll(inventory, new ItemStack[] {zero}, InventoryContext.MACHINE_OUTPUT, 0));
        assertFalse(
                service.fitAll(inventory, new ItemStack[] {zero, valid}, InventoryContext.MACHINE_OUTPUT, 0));
        assertTrue(
                service.fitAll(inventory, new ItemStack[] {zero, valid}, InventoryContext.MACHINE_OUTPUT, 0, 1));
        assertArrayEquals(before, inventory.getContents(), "No-op outputs must not create ghost stacks");
    }

    @Test
    void normalPositiveStackStillInserts() {
        Inventory inventory = Bukkit.createInventory(null, 18);
        ItemStack item = new ItemStack(Material.COBBLESTONE, 5);
        ItemStackService service = new ItemStackService();

        assertNull(service.addItem(inventory, item, InventoryContext.MACHINE_OUTPUT, 0));
        assertEquals(item, inventory.getItem(0));
        assertEquals(5, item.getAmount());
    }
}
