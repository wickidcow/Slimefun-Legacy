package io.github.thebusybiscuit.slimefun4.core.services.stability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.Lootable;
import org.junit.jupiter.api.Test;

class TestDoctorPhysicalInventoryAccess {

    @Test
    void refusesAttachedEntityLootWithoutObtainingInventory() {
        AtomicInteger reads = new AtomicInteger();
        Inventory inventory = proxy(Inventory.class);
        Entity entity = entity(proxy(LootTable.class), inventory, reads);
        assertNull(DoctorPhysicalInventoryAccess.getEntityInventory(entity));
        assertEquals(0, reads.get());
    }

    @Test
    void ordinaryEntityInventoryIsObtainedOnce() {
        AtomicInteger reads = new AtomicInteger();
        Inventory inventory = proxy(Inventory.class);
        assertSame(inventory, DoctorPhysicalInventoryAccess.getEntityInventory(entity(null, inventory, reads)));
        assertEquals(1, reads.get());
        assertNull(DoctorPhysicalInventoryAccess.getEntityInventory(proxy(Entity.class)));
    }

    private static Entity entity(LootTable table, Inventory inventory, AtomicInteger reads) {
        return (Entity) Proxy.newProxyInstance(
                Entity.class.getClassLoader(),
                new Class<?>[] {Entity.class, InventoryHolder.class, Lootable.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getLootTable" -> table;
                    case "getInventory" -> {
                        reads.incrementAndGet();
                        yield inventory;
                    }
                    default -> throw new AssertionError("Unexpected entity access: " + method.getName());
                });
    }

    private static <T> T proxy(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> {
                    throw new AssertionError("Unexpected access: " + method.getName());
                }));
    }
}
