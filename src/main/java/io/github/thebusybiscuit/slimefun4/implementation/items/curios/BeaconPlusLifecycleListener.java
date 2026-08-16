package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Releases Beacon Plus runtime state on shutdown and handles the lightweight visual controls.
 */
final class BeaconPlusLifecycleListener implements Listener {

    private static final String BEACON_PLUS_ID = "BEACON_PLUS";
    private static final String BEACON_PLUS_MENU_TITLE = ChatColor.GOLD.toString() + ChatColor.BOLD + "Beacon Plus";
    private static final int AREA_PREVIEW_SLOT = 51;
    private static final Map<UUID, Location> MENU_TARGETS = new ConcurrentHashMap<>();

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
    public void onBeaconInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null || !StorageCacheUtils.isBlock(block.getLocation(), BEACON_PLUS_ID)) {
            return;
        }

        Player player = event.getPlayer();
        MENU_TARGETS.put(player.getUniqueId(), block.getLocation());
        if (!player.isSneaking()) {
            return;
        }

        BeaconPlusManager manager = BeaconPlusManager.getInstance();
        UUID owner = manager == null ? null : manager.getOwner(block.getLocation());
        if (!canConfigure(player, owner)) {
            event.setCancelled(true);
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
            player.sendMessage(ChatColor.RED + "Only this Beacon Plus owner or a server operator can change beam visuals.");
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
        player.sendMessage(ChatColor.GOLD + "Beacon Plus beam visuals: "
                + (enabled ? ChatColor.GREEN + "ENABLED" : ChatColor.RED + "DISABLED")
                + ChatColor.GRAY + ". Sneak-right-click the Beacon Plus to toggle them.");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBeaconMenuOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)
                || !(event.getInventory().getHolder() instanceof ChestMenu menu)
                || !BEACON_PLUS_MENU_TITLE.equals(menu.getTitle())) {
            return;
        }

        Location location = MENU_TARGETS.get(player.getUniqueId());
        if (location == null || !StorageCacheUtils.isBlock(location, BEACON_PLUS_ID)) {
            return;
        }

        BeaconPlusManager manager = BeaconPlusManager.getInstance();
        UUID owner = manager == null ? null : manager.getOwner(location);
        if (!canConfigure(player, owner)) {
            return;
        }

        Block block = location.getBlock();
        installAreaPreviewControl(menu, block, owner);
        BeaconPlusAreaPreview.render(block);
    }

    private static void installAreaPreviewControl(ChestMenu menu, Block block, UUID expectedOwner) {
        menu.addItem(AREA_PREVIEW_SLOT, createAreaPreviewItem(block.getLocation()));
        menu.addMenuClickHandler(AREA_PREVIEW_SLOT, (player, slot, item, action) -> {
            Location location = block.getLocation();
            if (!StorageCacheUtils.isBlock(location, BEACON_PLUS_ID)) {
                player.closeInventory();
                player.sendMessage(ChatColor.RED + "That Beacon Plus no longer exists.");
                return false;
            }

            BeaconPlusManager manager = BeaconPlusManager.getInstance();
            UUID owner = manager == null ? expectedOwner : manager.getOwner(location);
            if (!canConfigure(player, owner)) {
                player.closeInventory();
                player.sendMessage(ChatColor.RED + "You no longer have permission to configure this Beacon Plus.");
                return false;
            }

            boolean enabled = !BeaconPlusAreaPreview.isEnabled(location);
            BeaconPlusAreaPreview.setEnabled(location, enabled);
            menu.addItem(AREA_PREVIEW_SLOT, createAreaPreviewItem(location));
            player.playSound(location, Sound.BLOCK_LEVER_CLICK, 0.8F, enabled ? 1.2F : 0.8F);
            player.sendMessage(ChatColor.AQUA + "Beacon Plus area preview: "
                    + (enabled ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF")
                    + ChatColor.GRAY + ". This only changes the visual boundary, not the effect range.");

            if (enabled) {
                BeaconPlusAreaPreview.render(block);
            }
            return false;
        });
    }

    private static ItemStack createAreaPreviewItem(Location location) {
        boolean enabled = BeaconPlusAreaPreview.isEnabled(location);
        BeaconPlusFieldArea area = BeaconPlusRuntime.getEffectiveFieldArea(
                location, BeaconPlusRuntime.getConfiguredEffects(location));

        ItemStack item = new ItemStack(Material.LEVER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName((enabled ? ChatColor.GREEN + "↑ " : ChatColor.RED + "↓ ")
                + "Area Preview: "
                + (enabled ? "ON" : "OFF"));
        meta.setLore(List.of(
                ChatColor.GRAY + "Shows the exact outer boundary of the",
                ChatColor.GRAY + "Beacon Plus effective chunk area.",
                "",
                ChatColor.GRAY + "Effective area: " + ChatColor.AQUA + area.getDisplayName(),
                ChatColor.GRAY + "Lever: " + (enabled ? ChatColor.GREEN + "↑ UP" : ChatColor.RED + "↓ DOWN"),
                ChatColor.DARK_GRAY + "Visual only; effect range is unchanged.",
                ChatColor.DARK_GRAY + "Uses sparse particles and no marker entities.",
                "",
                ChatColor.YELLOW + "Click to switch the preview " + (enabled ? "off" : "on")));
        item.setItemMeta(meta);
        return item;
    }

    private static boolean canConfigure(Player player, UUID owner) {
        return owner == null || owner.equals(player.getUniqueId()) || player.isOp();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        MENU_TARGETS.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == plugin) {
            MENU_TARGETS.clear();
            BeaconPlusPowerState.shutdown();
            BeaconPlus.clearPulseState();
            BeaconPlusRuntime.shutdown();
            BeaconPlusManager.shutdownCurrent();
            registered = false;
        }
    }
}
