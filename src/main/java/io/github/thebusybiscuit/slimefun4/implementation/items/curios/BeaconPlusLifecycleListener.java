package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Owns Resonance Beacon lifecycle services and preserves the powered yellow-beam visual control.
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
        BeaconPlusAdminCommand.register(plugin);
        BeaconPlusAreaVisualizer.register(plugin);
    }

    /**
     * Sneak-right-click keeps the 4.1.31 yellow powered-beam toggle without replacing the new Resonance Beacon menu.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBeaconInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null || !StorageCacheUtils.isBlock(block.getLocation(), BeaconPlusManager.ITEM_ID)) {
            return;
        }

        if (!event.getPlayer().isSneaking()) {
            return;
        }

        Player player = event.getPlayer();
        BeaconPlusManager manager = BeaconPlusManager.getInstance();
        UUID owner = manager == null ? null : manager.getOwner(block.getLocation());
        if (!canConfigure(player, owner)) {
            event.setCancelled(true);
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
            player.sendMessage(
                    ChatColor.RED + "Only this Resonance Beacon owner or a server operator can change beam visuals.");
            return;
        }

        boolean enabled = !BeaconPlusBeam.isVisualsEnabled(block.getLocation());
        BeaconPlusBeam.setVisualsEnabled(block.getLocation(), enabled);

        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        player.playSound(
                block.getLocation(),
                enabled ? Sound.BLOCK_BEACON_POWER_SELECT : Sound.BLOCK_BEACON_DEACTIVATE,
                0.65F,
                enabled ? 1.45F : 0.9F);
        player.sendMessage(ChatColor.GOLD + "Resonance Beacon yellow beam visuals: "
                + (enabled ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED")
                + ChatColor.GRAY + ". Sneak-right-click the Resonance Beacon to toggle them.");
    }

    private static boolean canConfigure(Player player, UUID owner) {
        if (owner == null || BeaconPlusLegacyDataStore.LEGACY_IMPORTED_OWNER.equals(owner)) {
            return player.isOp();
        }
        return owner.equals(player.getUniqueId()) || player.isOp();
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == plugin) {
            BeaconPlusAreaVisualizer.shutdown();
            BeaconPlusRuntime.shutdown();
            BeaconPlusProgression.shutdown();
            BeaconPlusLegacyDataStore.shutdownCurrent();
            BeaconPlusManager.shutdownCurrent();
            registered = false;
        }
    }
}
