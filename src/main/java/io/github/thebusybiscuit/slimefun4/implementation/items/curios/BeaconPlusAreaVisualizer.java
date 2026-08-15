package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nonnull;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Beacon;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Optional particle-only effect-area preview for Resonance Beacons.
 *
 * <p>This is deliberately not a 29th beacon power. It stores one display toggle on the placed Slimefun block and
 * renders a sparse three-ring wireframe at the exact runtime effect radius. Rendering is packet-only, does not load
 * chunks, and is scheduled on each viewing player's scheduler for Folia safety.
 */
final class BeaconPlusAreaVisualizer implements Listener {

    static final String SHOW_AREA_KEY = "beacon_plus_show_effect_area";

    private static final int MENU_SLOT = 45;
    private static final int RING_POINTS = 24;
    private static final long RENDER_INTERVAL_TICKS = 40L;
    private static final double VIEW_MARGIN = 48.0D;
    private static final int EXTRA_RANGE_PER_TIER = 10;

    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final Set<BeaconKey> VISIBLE_BEACONS = ConcurrentHashMap.newKeySet();
    private static final ConcurrentHashMap<UUID, BeaconKey> OPEN_MENU_TARGETS = new ConcurrentHashMap<>();

    private static BeaconPlusAreaVisualizer instance;

    private final Slimefun plugin;

    private BeaconPlusAreaVisualizer(Slimefun plugin) {
        this.plugin = plugin;
    }

    static synchronized void register(@Nonnull Slimefun plugin) {
        if (instance == null) {
            instance = new BeaconPlusAreaVisualizer(plugin);
        }
        if (!REGISTERED.compareAndSet(false, true)) {
            return;
        }

        Bukkit.getPluginManager().registerEvents(instance, plugin);
        Slimefun.getSchedulerService().runLater(instance::bootstrapLoadedChunks, 20L);
        Slimefun.getSchedulerService().runAtFixedRate(instance::renderAll, RENDER_INTERVAL_TICKS, RENDER_INTERVAL_TICKS);
    }

    static void shutdown() {
        REGISTERED.set(false);
        VISIBLE_BEACONS.clear();
        OPEN_MENU_TARGETS.clear();
        instance = null;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }
        Location location = event.getClickedBlock().getLocation();
        if (StorageCacheUtils.isBlock(location, BeaconPlusManager.ITEM_ID)) {
            OPEN_MENU_TARGETS.put(event.getPlayer().getUniqueId(), BeaconKey.from(location));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !isResonanceMenu(event.getView().getTitle())) {
            return;
        }
        BeaconKey key = OPEN_MENU_TARGETS.get(player.getUniqueId());
        if (key == null) {
            return;
        }
        Location location = key.toLocation();
        if (location == null || !StorageCacheUtils.isBlock(location, BeaconPlusManager.ITEM_ID)) {
            OPEN_MENU_TARGETS.remove(player.getUniqueId());
            return;
        }
        event.getView().getTopInventory().setItem(MENU_SLOT, createMenuItem(location.getBlock()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !isResonanceMenu(event.getView().getTitle())
                || event.getRawSlot() != MENU_SLOT) {
            return;
        }

        event.setCancelled(true);
        BeaconKey key = OPEN_MENU_TARGETS.get(player.getUniqueId());
        if (key == null) {
            return;
        }
        Location location = key.toLocation();
        if (location == null || !StorageCacheUtils.isBlock(location, BeaconPlusManager.ITEM_ID)) {
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "That Resonance Beacon no longer exists.");
            return;
        }

        BeaconPlusManager manager = BeaconPlusManager.getInstance();
        UUID owner = manager == null ? null : manager.getOwner(location);
        if (!canConfigure(player, owner)) {
            player.closeInventory();
            player.sendMessage(ChatColor.RED + "You no longer have permission to configure this Resonance Beacon.");
            return;
        }

        boolean enabled = !isEnabled(location);
        StorageCacheUtils.setData(location, SHOW_AREA_KEY, Boolean.toString(enabled));
        if (enabled) {
            VISIBLE_BEACONS.add(key);
        } else {
            VISIBLE_BEACONS.remove(key);
        }

        Block block = location.getBlock();
        event.getView().getTopInventory().setItem(MENU_SLOT, createMenuItem(block));
        player.playSound(
                location,
                enabled ? Sound.BLOCK_BEACON_POWER_SELECT : Sound.BLOCK_BEACON_DEACTIVATE,
                0.55F,
                enabled ? 1.55F : 1.0F);
        player.sendMessage(ChatColor.GOLD + "Resonance Beacon effect-area outline: "
                + (enabled ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF") + ChatColor.GRAY + ".");

        if (enabled) {
            renderBeacon(key);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player && isResonanceMenu(event.getView().getTitle())) {
            OPEN_MENU_TARGETS.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        OPEN_MENU_TARGETS.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        scheduleInspection(event.getBlockPlaced().getChunk(), 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        VISIBLE_BEACONS.remove(BeaconKey.from(event.getBlock().getLocation()));
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        scheduleInspection(event.getChunk(), 20L);
        scheduleInspection(event.getChunk(), 100L);
    }

    private void bootstrapLoadedChunks() {
        if (!REGISTERED.get()) {
            return;
        }
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                scheduleInspection(chunk, 1L);
                scheduleInspection(chunk, 80L);
            }
        }
    }

    private void scheduleInspection(Chunk chunk, long delayTicks) {
        World world = chunk.getWorld();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        Location anchor = new Location(world, chunkX << 4, world.getMinHeight(), chunkZ << 4);
        Slimefun.getSchedulerService().runAtLater(anchor, () -> inspectChunk(world, chunkX, chunkZ), delayTicks);
    }

    private void inspectChunk(World world, int chunkX, int chunkZ) {
        if (!REGISTERED.get() || !world.isChunkLoaded(chunkX, chunkZ)) {
            return;
        }
        Chunk chunk = world.getChunkAt(chunkX, chunkZ, false);
        for (BlockState state : chunk.getTileEntities()) {
            if (state.getType() != Material.BEACON) {
                continue;
            }
            Location location = state.getLocation();
            BeaconKey key = BeaconKey.from(location);
            if (StorageCacheUtils.isBlock(location, BeaconPlusManager.ITEM_ID) && isEnabled(location)) {
                VISIBLE_BEACONS.add(key);
            } else {
                VISIBLE_BEACONS.remove(key);
            }
        }
    }

    private void renderAll() {
        if (!REGISTERED.get() || !BeaconPlusConfig.isEnabled()) {
            return;
        }
        for (BeaconKey key : VISIBLE_BEACONS) {
            Location location = key.toLocation();
            if (location != null) {
                Slimefun.getSchedulerService().runAt(location, () -> renderBeacon(key));
            }
        }
    }

    private void renderBeacon(BeaconKey key) {
        if (!REGISTERED.get()) {
            return;
        }
        Location location = key.toLocation();
        if (location == null) {
            VISIBLE_BEACONS.remove(key);
            return;
        }
        World world = location.getWorld();
        if (world == null || !world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return;
        }

        Block block = location.getBlock();
        if (block.getType() != Material.BEACON
                || !StorageCacheUtils.isBlock(location, BeaconPlusManager.ITEM_ID)
                || !isEnabled(location)) {
            VISIBLE_BEACONS.remove(key);
            return;
        }

        double range = getEffectiveRange(block);
        if (range <= 0.0D) {
            return;
        }

        UUID worldId = world.getUID();
        double centerX = block.getX() + 0.5D;
        double centerY = block.getY() + 0.5D;
        double centerZ = block.getZ() + 0.5D;
        double viewerRange = range + VIEW_MARGIN;
        double viewerRangeSquared = viewerRange * viewerRange;

        for (Player player : Bukkit.getOnlinePlayers()) {
            Slimefun.getSchedulerService().runFor(player, () -> {
                if (!player.isOnline() || !player.getWorld().getUID().equals(worldId)) {
                    return;
                }
                Location viewer = player.getLocation();
                double dx = viewer.getX() - centerX;
                double dy = viewer.getY() - centerY;
                double dz = viewer.getZ() - centerZ;
                if (dx * dx + dy * dy + dz * dz > viewerRangeSquared) {
                    return;
                }
                renderWireframe(player, centerX, centerY, centerZ, range);
            });
        }
    }

    private static void renderWireframe(Player player, double centerX, double centerY, double centerZ, double radius) {
        int minHeight = player.getWorld().getMinHeight();
        int maxHeight = player.getWorld().getMaxHeight();
        for (int index = 0; index < RING_POINTS; index++) {
            double angle = Math.PI * 2.0D * index / RING_POINTS;
            double cos = Math.cos(angle) * radius;
            double sin = Math.sin(angle) * radius;

            spawn(player, centerX + cos, centerY, centerZ + sin, minHeight, maxHeight);
            spawn(player, centerX + cos, centerY + sin, centerZ, minHeight, maxHeight);
            spawn(player, centerX, centerY + sin, centerZ + cos, minHeight, maxHeight);
        }
    }

    private static void spawn(Player player, double x, double y, double z, int minHeight, int maxHeight) {
        if (y < minHeight || y >= maxHeight) {
            return;
        }
        player.spawnParticle(Particle.END_ROD, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    private static double getEffectiveRange(Block block) {
        BlockState state = block.getState();
        if (!(state instanceof Beacon beacon) || beacon.getTier() <= 0) {
            return 0.0D;
        }
        double importedOverride = BeaconPlusLegacyDataStore.getImportedOverriddenRange(block.getLocation());
        double range = importedOverride > 0.0D ? importedOverride : Math.max(0.0D, beacon.getEffectRange());
        int extraRangeTier = BeaconPlusRuntime.getEffectiveTierAtBeacon(block, BeaconPlusEffect.EXTRA_RANGE);
        return extraRangeTier > 0 ? range + EXTRA_RANGE_PER_TIER * extraRangeTier : range;
    }

    private static boolean isEnabled(Location location) {
        return Boolean.parseBoolean(StorageCacheUtils.getData(location, SHOW_AREA_KEY));
    }

    private static ItemStack createMenuItem(Block block) {
        boolean enabled = isEnabled(block.getLocation());
        double range = getEffectiveRange(block);
        ItemStack item = new ItemStack(Material.SPYGLASS);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName((enabled ? ChatColor.GREEN : ChatColor.GRAY) + "Show Effect Area");
        meta.setLore(java.util.List.of(
                ChatColor.GRAY + "Shows a sparse particle outline of the",
                ChatColor.GRAY + "Resonance Beacon's actual effect radius.",
                "",
                ChatColor.GRAY + "Status: " + (enabled ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"),
                ChatColor.GRAY + "Current radius: "
                        + (range > 0.0D ? ChatColor.AQUA.toString() + (int) Math.floor(range) + " blocks" : ChatColor.RED + "Dormant"),
                "",
                ChatColor.DARK_GRAY + "Display only • visible to nearby players",
                ChatColor.DARK_GRAY + "Does not load chunks or change beacon powers",
                "",
                ChatColor.YELLOW + "Click to toggle"));
        item.setItemMeta(meta);
        return item;
    }

    private static boolean canConfigure(Player player, UUID owner) {
        if (owner == null || BeaconPlusLegacyDataStore.LEGACY_IMPORTED_OWNER.equals(owner)) {
            return player.isOp();
        }
        return owner.equals(player.getUniqueId()) || player.isOp();
    }

    private static boolean isResonanceMenu(String title) {
        return "Resonance Beacon".equals(ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', title)));
    }

    private record BeaconKey(UUID worldId, int x, int y, int z) {
        private static BeaconKey from(Location location) {
            return new BeaconKey(
                    location.getWorld().getUID(),
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ());
        }

        private Location toLocation() {
            World world = Bukkit.getWorld(worldId);
            return world == null ? null : new Location(world, x, y, z);
        }
    }
}
