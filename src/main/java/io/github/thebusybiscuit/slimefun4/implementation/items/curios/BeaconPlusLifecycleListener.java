package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Releases Beacon Plus runtime state on shutdown and handles the lightweight beam-visual toggle.
 */
final class BeaconPlusLifecycleListener implements Listener {

    private static final String BEACON_PLUS_ID = "BEACON_PLUS";

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

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBeamVisualToggle(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || !event.getPlayer().isSneaking()) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null || !StorageCacheUtils.isBlock(block.getLocation(), BEACON_PLUS_ID)) {
            return;
        }

        BeaconPlusManager manager = BeaconPlusManager.getInstance();
        UUID owner = manager == null ? null : manager.getOwner(block.getLocation());
        if (owner != null && !owner.equals(event.getPlayer().getUniqueId()) && !event.getPlayer().isOp()) {
            event.setCancelled(true);
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
            event.getPlayer().sendMessage(
                    ChatColor.RED + "Only this Beacon Plus owner or a server operator can change beam visuals.");
            return;
        }

        boolean enabled = !BeaconPlusBeam.isVisualsEnabled(block.getLocation());
        BeaconPlusBeam.setVisualsEnabled(block.getLocation(), enabled);

        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        event.getPlayer().playSound(
                block.getLocation(),
                enabled ? Sound.BLOCK_BEACON_POWER_SELECT : Sound.BLOCK_BEACON_DEACTIVATE,
                0.65F,
                enabled ? 1.45F : 0.9F);
        event.getPlayer().sendMessage(ChatColor.GOLD + "Beacon Plus beam visuals: "
                + (enabled ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED")
                + ChatColor.GRAY + ". Sneak-right-click the Beacon Plus to toggle them.");
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == plugin) {
            BeaconPlusPowerState.shutdown();
            BeaconPlus.clearPulseState();
            BeaconPlusRuntime.shutdown();
            BeaconPlusManager.shutdownCurrent();
            registered = false;
        }
    }
}
