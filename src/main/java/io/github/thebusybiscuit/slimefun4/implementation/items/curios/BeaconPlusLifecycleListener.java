package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
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
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Owns Resonance Beacon lifecycle services and preserves the powered yellow-beam visual control.
 */
final class BeaconPlusLifecycleListener implements Listener {

    private static final int MINIMUM_BEACON_CHUNK_SPACING = 3;

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
     * Prevent a new Resonance Beacon from being placed within three chunks of another
     * Resonance Beacon. This runs before Slimefun's HIGHEST-priority placement listener,
     * so cancellation happens before any Slimefun block data or Beacon Plus registry entry
     * is created. Activator state is intentionally irrelevant; this is placement spacing only.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBeaconPlace(BlockPlaceEvent event) {
        if (!event.canBuild()) {
            return;
        }

        SlimefunItem item = SlimefunItem.getByItem(event.getItemInHand());
        if (item == null || !BeaconPlusManager.ITEM_ID.equals(item.getId())) {
            return;
        }

        Player player = event.getPlayer();
        BeaconPlusManager manager = BeaconPlusManager.getInstance();
        if (manager == null) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Resonance Beacon is still initializing. Try placing it again shortly.");
            return;
        }

        if (manager.isBeaconWithinChunkRadius(
                event.getBlockPlaced().getLocation(), MINIMUM_BEACON_CHUNK_SPACING)) {
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED + "Cannot place Resonance Beacon: " + ChatColor.GRAY
                    + "it is too close to another Resonance Beacon. Beacons must be more than 3 chunks apart.");
        }
    }

    /**
     * Sneak-right-click keeps the 4.1.31 yellow powered-beam toggle without replacing the new Resonance Beacon menu.
     * Off-hand interactions are consumed so the underlying vanilla beacon cannot replace the custom menu opened by
     * Slimefun's main-hand BlockUseHandler.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBeaconInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null || !StorageCacheUtils.isBlock(block.getLocation(), BeaconPlusManager.ITEM_ID)) {
            return;
        }

        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            denyInteraction(event);
            return;
        }

        if (event.getHand() != EquipmentSlot.HAND || !event.getPlayer().isSneaking()) {
            return;
        }

        Player player = event.getPlayer();
        BeaconPlusManager manager = BeaconPlusManager.getInstance();
        UUID owner = manager == null ? null : manager.getOwner(block.getLocation());
        if (!canConfigure(player, owner)) {
            denyInteraction(event);
            player.sendMessage(
                    ChatColor.RED + "Only this Resonance Beacon owner or a server operator can change beam visuals.");
            return;
        }

        boolean enabled = !BeaconPlusBeam.isVisualsEnabled(block.getLocation());
        BeaconPlusBeam.setVisualsEnabled(block.getLocation(), enabled);

        denyInteraction(event);
        player.playSound(
                block.getLocation(),
                enabled ? Sound.BLOCK_BEACON_POWER_SELECT : Sound.BLOCK_BEACON_DEACTIVATE,
                0.65F,
                enabled ? 1.45F : 0.9F);
        player.sendMessage(ChatColor.GOLD + "Resonance Beacon yellow beam visuals: "
                + (enabled ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED")
                + ChatColor.GRAY + ". Sneak-right-click the Resonance Beacon to toggle them.");
    }

    private static void denyInteraction(PlayerInteractEvent event) {
        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
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
