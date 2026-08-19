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
 * renders the exact chunk-aligned square footprint used by field powers. The preview follows each viewer's current
 * Y level because the field covers the full world height. Rendering is packet-only and never loads chunks.
 */
final class BeaconPlusAreaVisualizer implements Listener {

    static final String SHOW_AREA_KEY = "beacon_plus_show_effect_area";

    private static final int MENU_SLOT = 45;
    private static final int GRID_POINT_STEP = 8;
    private static final int VIEW_MARGIN_CHUNKS = 3;
    private static final int MAX_PARTICLES_PER_VIEWER = 512;
    private static final long RENDER_INTERVAL_TICKS = 40L;

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
        Slimefun.getSchedulerService()
                .runAtFixedRate(instance::renderAll, RENDER_INTERVAL_TICKS, RENDER_INTERVAL_TICKS);
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

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)
                || !isResonanceMenu(event.getView().getTitle())) {
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
        if (event.getPlayer() instanceof Player player
                && isResonanceMenu(event.getView().getTitle())) {
            OPEN_MENU_TARGETS.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        OPEN_MENU_TARGETS.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlockPlaced().getType() == Material.BEACON) {
            scheduleInspection(event.getBlockPlaced().getChunk(), 2L);
        }
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

        double range = BeaconPlusRuntime.getEffectiveRange(block);
        if (range <= 0.0D) {
            return;
        }

        BeaconPlusField.ChunkFootprint footprint =
                Slimefun.getSchedulerService().isFolia()
                        ? BeaconPlusField.footprint(block.getX(), block.getZ(), 1.0D)
                        : BeaconPlusField.footprint(block.getX(), block.getZ(), range);
        UUID worldId = world.getUID();

        for (Player player : Bukkit.getOnlinePlayers()) {
            Slimefun.getSchedulerService().runFor(player, () -> {
                if (!player.isOnline() || !player.getWorld().getUID().equals(worldId)) {
                    return;
                }
                Location viewer = player.getLocation();
                int viewerChunkX = viewer.getBlockX() >> 4;
                int viewerChunkZ = viewer.getBlockZ() >> 4;
                if (viewerChunkX < footprint.minChunkX() - VIEW_MARGIN_CHUNKS
                        || viewerChunkX > footprint.maxChunkX() + VIEW_MARGIN_CHUNKS
                        || viewerChunkZ < footprint.minChunkZ() - VIEW_MARGIN_CHUNKS
                        || viewerChunkZ > footprint.maxChunkZ() + VIEW_MARGIN_CHUNKS) {
                    return;
                }
                double renderY = Math.max(
                        world.getMinHeight() + 0.15D,
                        Math.min(world.getMaxHeight() - 0.15D, Math.floor(viewer.getY()) + 0.15D));
                renderChunkGrid(player, footprint, renderY);
            });
        }
    }

    private static void renderChunkGrid(Player player, BeaconPlusField.ChunkFootprint footprint, double y) {
        int minX = footprint.minBlockX();
        int maxX = footprint.maxBlockXExclusive();
        int minZ = footprint.minBlockZ();
        int maxZ = footprint.maxBlockZExclusive();
        int width = maxX - minX;
        int gridLines = footprint.widthChunks() + 1;
        long estimated = 2L * gridLines * (width / GRID_POINT_STEP + 1L);
        int step = estimated > MAX_PARTICLES_PER_VIEWER ? 16 : GRID_POINT_STEP;
        int sent = 0;

        for (int x = minX; x <= maxX; x += 16) {
            for (int z = minZ; z <= maxZ; z += step) {
                spawn(player, x, y, z);
                if (++sent >= MAX_PARTICLES_PER_VIEWER) {
                    return;
                }
            }
        }
        for (int z = minZ; z <= maxZ; z += 16) {
            for (int x = minX; x <= maxX; x += step) {
                spawn(player, x, y, z);
                if (++sent >= MAX_PARTICLES_PER_VIEWER) {
                    return;
                }
            }
        }
    }

    private static void spawn(Player player, double x, double y, double z) {
        player.spawnParticle(Particle.END_ROD, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    private static boolean isEnabled(Location location) {
        return Boolean.parseBoolean(StorageCacheUtils.getData(location, SHOW_AREA_KEY));
    }

    private static ItemStack createMenuItem(Block block) {
        boolean enabled = isEnabled(block.getLocation());
        double range = BeaconPlusRuntime.getEffectiveRange(block);
        BeaconPlusField.ChunkFootprint footprint =
                Slimefun.getSchedulerService().isFolia()
                        ? BeaconPlusField.footprint(block.getX(), block.getZ(), 1.0D)
                        : BeaconPlusField.footprint(block.getX(), block.getZ(), range);
        ItemStack item = new ItemStack(Material.SPYGLASS);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName((enabled ? ChatColor.GREEN : ChatColor.GRAY) + "Show Effect Area");
        meta.setLore(java.util.List.of(
                ChatColor.GRAY + "Shows the exact chunk-aligned square",
                ChatColor.GRAY + "covered by Resonance Beacon field powers.",
                "",
                ChatColor.GRAY + "Status: " + (enabled ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"),
                ChatColor.GRAY + "Effect footprint: "
                        + (range > 0.0D
                                ? ChatColor.AQUA.toString() + footprint.widthChunks() + "x" + footprint.widthChunks()
                                        + " chunks"
                                : ChatColor.RED + "Dormant"),
                ChatColor.GRAY + "Vertical reach: " + ChatColor.AQUA + "Full world height",
                "",
                ChatColor.DARK_GRAY + "Particle grid follows your current Y level",
                ChatColor.DARK_GRAY + "Display only • never loads extra chunks",
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
                    location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }

        private Location toLocation() {
            World world = Bukkit.getWorld(worldId);
            return world == null ? null : new Location(world, x, y, z);
        }
    }
}
