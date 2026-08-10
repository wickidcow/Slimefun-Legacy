package io.github.thebusybiscuit.slimefun4.implementation.tasks;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.TeleporterListener;
import io.github.thebusybiscuit.slimefun4.implementation.setup.PostSetup;
import javax.annotation.Nonnull;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;

/**
 * This Task initializes all items, some listeners and various other stuff.
 * This has been moved to its own class to make timings log easier to read, so
 * they say "SlimefunStartupTask" instead of "Slimefun:lambda:123456789".
 *
 * @author TheBusyBiscuit
 *
 */
public class SlimefunStartupTask implements Runnable {

    private final Slimefun plugin;
    private final Runnable runnable;

    /**
     * This initializes our {@link SlimefunStartupTask} for the given {@link Slimefun}.
     *
     * @param plugin
     *            The main instance of our {@link Slimefun}
     * @param runnable
     *            A {@link Runnable} containing additional operations that need to be run
     */
    public SlimefunStartupTask(@Nonnull Slimefun plugin, @Nonnull Runnable runnable) {
        this.plugin = plugin;
        this.runnable = runnable;
    }

    @Override
    public void run() {
        runnable.run();

        // Load all items
        PostSetup.loadItems();

        // Load all worlds
        Slimefun.getWorldSettingsService().load(Bukkit.getWorlds());

        // Observe world/chunk lifecycle and route block-data loads through the ownership-aware runtime foundation.
        // These services are read-only to addons; their Listener implementations are internal to Slimefun core.
        plugin.getServer().getPluginManager().registerEvents((Listener) Slimefun.getWorldChunkRuntimeService(), plugin);
        plugin.getServer().getPluginManager().registerEvents((Listener) Slimefun.getBlockDataRuntimeService(), plugin);

        // Only load this Listener if the corresponding items are enabled
        if (isEnabled("ELEVATOR_PLATE", "GPS_ACTIVATION_DEVICE_SHARED", "GPS_ACTIVATION_DEVICE_PERSONAL")) {
            new TeleporterListener(plugin);
        }
    }

    private boolean isEnabled(String... itemIds) {
        for (String id : itemIds) {
            SlimefunItem item = SlimefunItem.getById(id);

            if (item != null && !item.isDisabled()) {
                return true;
            }
        }

        return false;
    }
}
