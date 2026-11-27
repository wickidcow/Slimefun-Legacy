package io.github.thebusybiscuit.slimefun4.implementation.tasks.armor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.InventoryReadTestPlugin;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

class TestSolarHelmetTask {

    private InventoryReadTestPlugin fixture;
    private final AtomicInteger worldReads = new AtomicInteger();
    private final AtomicInteger timeReads = new AtomicInteger();

    @BeforeEach
    void setup() {
        fixture = new InventoryReadTestPlugin(MockBukkit.mock());
    }

    @AfterEach
    void cleanup() {
        try {
            fixture.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void emptyHelmetSkipsAllWorldReads() {
        SolarHelmetTask task = new SolarHelmetTask();
        task.onPlayerTick(player(null, World.Environment.NORMAL, 1000L), null);
        task.onPlayerTick(player(new ItemStack(Material.AIR), World.Environment.NORMAL, 1000L), null);
        assertEquals(0, worldReads.get());
        assertEquals(0, timeReads.get());
    }

    @Test
    void nonSolarHelmetSkipsSkylightEvenDuringDay() {
        ItemStack helmet = new SlimefunItemStack("UNREGISTERED_SOLAR_FIXTURE", Material.IRON_HELMET, "Fixture");
        new SolarHelmetTask().onPlayerTick(player(helmet, World.Environment.NORMAL, 1000L), null);
        assertEquals(1, worldReads.get());
        assertEquals(1, timeReads.get());
    }

    @Test
    void nightAndNonOverworldSkipSkylight() {
        ItemStack helmet = new ItemStack(Material.IRON_HELMET);
        SolarHelmetTask task = new SolarHelmetTask();
        task.onPlayerTick(player(helmet, World.Environment.NORMAL, 18000L), null);
        task.onPlayerTick(player(helmet, World.Environment.NETHER, 1000L), null);
        assertEquals(2, worldReads.get());
        assertEquals(1, timeReads.get());
    }

    private Player player(ItemStack helmet, World.Environment environment, long time) {
        PlayerInventory inventory = (PlayerInventory) Proxy.newProxyInstance(
                PlayerInventory.class.getClassLoader(), new Class<?>[] {PlayerInventory.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getHelmet")) {
                        return helmet;
                    }
                    throw new AssertionError("Unexpected inventory access: " + method.getName());
                });
        World world = (World) Proxy.newProxyInstance(
                World.class.getClassLoader(), new Class<?>[] {World.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getEnvironment":
                            return environment;
                        case "getTime":
                            timeReads.incrementAndGet();
                            return time;
                        default:
                            throw new AssertionError("Unexpected world access: " + method.getName());
                    }
                });
        return (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(), new Class<?>[] {Player.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getInventory":
                            return inventory;
                        case "getWorld":
                            worldReads.incrementAndGet();
                            return world;
                        default:
                            throw new AssertionError("Unexpected player access: " + method.getName());
                    }
                });
    }
}
