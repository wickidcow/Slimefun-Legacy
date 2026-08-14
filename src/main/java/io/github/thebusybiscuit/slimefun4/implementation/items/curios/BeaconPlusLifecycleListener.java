package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;

/**
 * Releases Beacon Plus tickets before Slimefun finishes disabling.
 */
final class BeaconPlusLifecycleListener implements Listener {

    private static boolean registered;
    private final Slimefun plugin;

    private BeaconPlusLifecycleListener(Slimefun plugin) {
        this.plugin = plugin;
    }

    static void register(Slimefun plugin) {
        if (registered) {
            return;
        }
        registered = true;
        Bukkit.getPluginManager().registerEvents(new BeaconPlusLifecycleListener(plugin), plugin);
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == plugin) {
            BeaconPlusManager.shutdownCurrent();
        }
    }
}
