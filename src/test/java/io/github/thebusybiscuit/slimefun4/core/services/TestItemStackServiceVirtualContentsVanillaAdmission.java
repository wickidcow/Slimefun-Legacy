package io.github.thebusybiscuit.slimefun4.core.services;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.InventoryReadTestPlugin;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.items.virtual.VirtualItemHandler;
import io.github.thebusybiscuit.slimefun4.api.items.virtual.VirtualItemHandler.InventoryContext;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.inventory.ShulkerBoxInventoryMock;

/** Existing virtual items must not bypass vanilla admission rules for the incoming output. */
class TestItemStackServiceVirtualContentsVanillaAdmission {

    private static final String VIRTUAL_ID = "TEST_FIT_VIRTUAL_CONTENTS";
    private InventoryReadTestPlugin fixture;

    @BeforeEach
    void startServer() {
        fixture = new InventoryReadTestPlugin(MockBukkit.mock());
        SlimefunItem definition = new SlimefunItem(
                new ItemGroup(
                        new NamespacedKey("slimefun", "virtual_contents_fit_fixture"),
                        new ItemStack(Material.BOOK)),
                new SlimefunItemStack(VIRTUAL_ID, Material.DIAMOND, "Virtual contents fixture"),
                RecipeType.ENHANCED_CRAFTING_TABLE,
                new ItemStack[9]);
        definition.addItemHandler(new VirtualItemHandler() {
            @Override
            public boolean isVirtualItem(ItemStack stack) {
                return stack != null && stack.getType() == Material.DIAMOND;
            }
        });
        Slimefun.getRegistry().getSlimefunItemIds().put(VIRTUAL_ID, definition);
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
    void virtualStackInSelectedSlotCannotAdmitNestedVanillaShulker() {
        Inventory inventory = new ShulkerBoxInventoryMock(null);
        ItemStack virtualStack = new ItemStack(Material.DIAMOND);
        Slimefun.getItemDataService().setItemData(virtualStack, VIRTUAL_ID);
        assertNotNull(SlimefunItem.getByItem(virtualStack));
        inventory.setItem(0, virtualStack);
        ItemStack[] before = inventory.getContents();
        ItemStack forbidden = new ItemStack(Material.SHULKER_BOX);
        ItemStackService service = new ItemStackService();

        assertFalse(service.fits(inventory, forbidden, InventoryContext.MACHINE_OUTPUT));
        assertFalse(service.fits(inventory, forbidden, InventoryContext.MACHINE_OUTPUT, 0, 1));
        assertFalse(service.fits(inventory, forbidden, InventoryContext.MACHINE_OUTPUT, 0, 0, 1));
        assertFalse(service.fitAll(inventory, new ItemStack[] {forbidden},
                InventoryContext.MACHINE_OUTPUT, 0, 1));
        assertEquals(forbidden, service.addItem(inventory, forbidden, InventoryContext.MACHINE_OUTPUT, 0, 1));
        assertArrayEquals(before, inventory.getContents(), "Preflight and insertion must both preserve contents");
    }

    @Test
    void allowedVanillaOutputCanStillUseFreeSpaceBesideVirtualStack() {
        Inventory inventory = new ShulkerBoxInventoryMock(null);
        ItemStack virtualStack = new ItemStack(Material.DIAMOND);
        Slimefun.getItemDataService().setItemData(virtualStack, VIRTUAL_ID);
        inventory.setItem(0, virtualStack);
        ItemStack[] before = inventory.getContents();
        ItemStack allowed = new ItemStack(Material.STONE, 2);
        ItemStackService service = new ItemStackService();

        assertTrue(service.fits(inventory, allowed, InventoryContext.MACHINE_OUTPUT, 0, 1));
        assertTrue(service.fitAll(inventory, new ItemStack[] {allowed}, InventoryContext.MACHINE_OUTPUT, 0, 1));
        assertArrayEquals(before, inventory.getContents(), "Preflight must not mutate either stack");
        assertEquals(2, allowed.getAmount());
    }
}
