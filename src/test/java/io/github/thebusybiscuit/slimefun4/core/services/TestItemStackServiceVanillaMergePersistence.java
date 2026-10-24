package io.github.thebusybiscuit.slimefun4.core.services;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.InventoryReadTestPlugin;
import io.github.thebusybiscuit.slimefun4.api.items.virtual.VirtualItemHandler.InventoryContext;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Vanilla insertion must persist merges even if Inventory#getItem returns detached ItemStacks. */
class TestItemStackServiceVanillaMergePersistence {

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
    void singlePartialStackMergeIsWrittenToTheBackingInventory() {
        Inventory backing = Bukkit.createInventory(null, 18);
        backing.setItem(0, new ItemStack(Material.STONE, 60));
        AtomicInteger writes = new AtomicInteger();
        Inventory detached = copyOnRead(backing, writes);
        ItemStack input = new ItemStack(Material.STONE, 4);

        ItemStack remainder = new ItemStackService()
                .addItem(detached, input, InventoryContext.MACHINE_OUTPUT, 0);

        assertNull(remainder);
        assertEquals(64, backing.getItem(0).getAmount());
        assertEquals(1, writes.get(), "An accepted merge must explicitly commit to the inventory");
        assertEquals(4, input.getAmount(), "The caller input must remain unchanged");
    }

    @Test
    void splitInsertionPersistsEveryMergeAndReturnsOnlyTrueRemainder() {
        Inventory backing = Bukkit.createInventory(null, 18);
        backing.setItem(0, new ItemStack(Material.COBBLESTONE, 63));
        backing.setItem(1, new ItemStack(Material.COBBLESTONE, 62));
        AtomicInteger writes = new AtomicInteger();
        Inventory detached = copyOnRead(backing, writes);
        ItemStack input = new ItemStack(Material.COBBLESTONE, 6);

        ItemStack remainder = new ItemStackService()
                .addItem(detached, input, InventoryContext.CARGO_INSERT, 0, 1);

        assertNotNull(remainder);
        assertNotSame(input, remainder);
        assertEquals(3, remainder.getAmount(), "Only items that did not fit may remain");
        assertEquals(64, backing.getItem(0).getAmount());
        assertEquals(64, backing.getItem(1).getAmount());
        assertEquals(2, writes.get(), "Each changed physical slot must be persisted");
        assertEquals(6, input.getAmount());
    }

    @Test
    void fullStackNoOpReturnsTheUninsertedInputWithoutWriting() {
        Inventory backing = Bukkit.createInventory(null, 18);
        backing.setItem(0, new ItemStack(Material.DIAMOND, 64));
        ItemStack[] before = backing.getContents();
        AtomicInteger writes = new AtomicInteger();
        Inventory detached = copyOnRead(backing, writes);
        ItemStack input = new ItemStack(Material.DIAMOND, 2);

        ItemStack remainder = new ItemStackService()
                .addItem(detached, input, InventoryContext.MACHINE_OUTPUT, 0);

        assertNotNull(remainder);
        assertNotSame(input, remainder);
        assertEquals(input, remainder);
        assertEquals(0, writes.get(), "A rejected insertion must not write unchanged slots");
        assertArrayEquals(before, backing.getContents());
    }

    private static Inventory copyOnRead(Inventory backing, AtomicInteger writes) {
        return (Inventory) Proxy.newProxyInstance(
                Inventory.class.getClassLoader(),
                new Class<?>[] {Inventory.class},
                (proxy, method, args) -> {
                    try {
                        if ("getItem".equals(method.getName()) && args != null && args.length == 1) {
                            ItemStack item = (ItemStack) method.invoke(backing, args);
                            return item == null ? null : item.clone();
                        }

                        if ("setItem".equals(method.getName())) {
                            writes.incrementAndGet();
                        }
                        return method.invoke(backing, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }
}
