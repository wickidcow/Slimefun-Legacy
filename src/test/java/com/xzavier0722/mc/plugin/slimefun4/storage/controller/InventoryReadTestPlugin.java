package com.xzavier0722.mc.plugin.slimefun4.storage.controller;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.lang.reflect.Field;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPluginLoader;
import org.mockbukkit.mockbukkit.ServerMock;

/** Uses the retained test constructor: real registries, no service startup and no subclass of the final core. */
final class InventoryReadTestPlugin implements AutoCloseable {
    private final Field instance;
    private final Object previous;

    @SuppressWarnings({"deprecation", "removal"}) // Existing explicit test constructor, not production API use.
    InventoryReadTestPlugin(ServerMock server) {
        try {
            instance = Slimefun.class.getDeclaredField("instance");
            instance.setAccessible(true);
            previous = instance.get(null);
            var manager = server.getPluginManager();
            var plugin = new Slimefun(
                    new JavaPluginLoader(server),
                    new PluginDescriptionFile("Slimefun", "4.1.62-test", Slimefun.class.getName()),
                    manager.createTemporaryDirectory("Slimefun-read-fixture"),
                    manager.createTemporaryPluginFile("Slimefun-read-fixture"));
            instance.set(null, plugin);
        } catch (Exception failure) {
            throw new IllegalStateException("Could not initialize the inventory-read fixture", failure);
        }
    }

    @Override
    public void close() {
        try {
            instance.set(null, previous);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Could not restore the inventory-read fixture", failure);
        }
    }
}
