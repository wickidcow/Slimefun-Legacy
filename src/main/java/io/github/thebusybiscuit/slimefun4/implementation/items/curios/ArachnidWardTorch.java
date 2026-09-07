package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import com.xzavier0722.mc.plugin.slimefun4.storage.event.SlimefunChunkDataLoadEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.config.CuriositiesConfig;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Spider;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * A portable and placeable Adventurer's Curios ward which repels spiders without damaging them.
 *
 * <p>The held ward follows the player while the placed ward is indexed from loaded Slimefun block data. The
 * implementation never scans every entity or block in a world and never force-loads chunks.</p>
 */
public final class ArachnidWardTorch extends SlimefunItem implements Listener {

    public static final int DEFAULT_HELD_RADIUS = 8;
    public static final int DEFAULT_PLACED_RADIUS = 12;

    private static final String CONFIG_ROOT = "SlimefunLegacyAddition.ArachnidWardTorch";
    private static final String HELD_RADIUS_PATH = CONFIG_ROOT + ".held-radius";
    private static final String PLACED_RADIUS_PATH = CONFIG_ROOT + ".placed-radius";
    private static final int MAX_RADIUS = 32;
    private static final long SWEEP_INTERVAL_MILLIS = 750L;
    private static final double REPEL_HORIZONTAL_SPEED = 0.38D;
    private static final double REPEL_MIN_Y = 0.08D;

    private final int heldRadius;
    private final int placedRadius;
    private final Map<UUID, Map<Long, Set<WardBlock>>> placedWards = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastSweep = new ConcurrentHashMap<>();

    @ParametersAreNonnullByDefault
    public ArachnidWardTorch(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);

        CuriositiesConfig config = CuriositiesConfig.getConfig();
        config.setDefaultValue(HELD_RADIUS_PATH, DEFAULT_HELD_RADIUS);
        config.setDefaultValue(PLACED_RADIUS_PATH, DEFAULT_PLACED_RADIUS);
        config.save();
        heldRadius = clampRadius(config.getInt(HELD_RADIUS_PATH));
        placedRadius = clampRadius(config.getInt(PLACED_RADIUS_PATH));

        addItemHandler(new BlockPlaceHandler(false) {
            @Override
            public void onPlayerPlace(@Nonnull BlockPlaceEvent event) {
                indexWard(event.getBlock().getLocation());
            }
        });

        addItemHandler(new BlockBreakHandler(false, false) {
            @Override
            public void onPlayerBreak(BlockBreakEvent event, ItemStack item, java.util.List<ItemStack> drops) {
                removeWard(event.getBlock().getLocation());
            }

            @Override
            public void onExplode(org.bukkit.block.Block block, java.util.List<ItemStack> drops) {
                removeWard(block.getLocation());
            }
        });
    }

    @Override
    public void postRegister() {
        if (!isDisabled()) {
            Bukkit.getPluginManager().registerEvents(this, Slimefun.instance());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkDataLoad(SlimefunChunkDataLoadEvent event) {
        UUID worldId = event.getWorld().getUID();
        long chunkKey = chunkKey(event.getChunk().getX(), event.getChunk().getZ());
        Set<WardBlock> wards = ConcurrentHashMap.newKeySet();

        for (SlimefunBlockData data : event.getChunkData().getAllBlockData()) {
            if (getId().equals(data.getSfId())) {
                wards.add(WardBlock.from(data.getLocation()));
            }
        }

        Map<Long, Set<WardBlock>> worldWards = placedWards.computeIfAbsent(worldId, ignored -> new ConcurrentHashMap<>());
        if (wards.isEmpty()) {
            worldWards.remove(chunkKey);
            if (worldWards.isEmpty()) {
                placedWards.remove(worldId, worldWards);
            }
        } else {
            worldWards.put(chunkKey, wards);
        }
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        Map<Long, Set<WardBlock>> worldWards = placedWards.get(event.getWorld().getUID());
        if (worldWards == null) {
            return;
        }

        worldWards.remove(chunkKey(event.getChunk().getX(), event.getChunk().getZ()));
        if (worldWards.isEmpty()) {
            placedWards.remove(event.getWorld().getUID(), worldWards);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpiderTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Spider spider) || !(event.getTarget() instanceof Player player)) {
            return;
        }

        if (!isFoliaSafePair(spider.getLocation(), player.getLocation())) {
            return;
        }

        Location playerLocation = player.getLocation();
        boolean held = isHeld(player);
        WardBlock placed = findNearestPlacedWard(playerLocation);
        if (!held && placed == null) {
            return;
        }

        event.setCancelled(true);
        Location repelCenter = chooseRepelCenter(spider.getLocation(), playerLocation, held, placed);
        if (repelCenter != null) {
            repel(spider, repelCenter);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpiderDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Spider spider) || !(event.getEntity() instanceof Player player)) {
            return;
        }

        if (!isFoliaSafePair(spider.getLocation(), player.getLocation())) {
            return;
        }

        Location playerLocation = player.getLocation();
        boolean held = isHeld(player);
        WardBlock placed = findNearestPlacedWard(playerLocation);
        if (!held && placed == null) {
            return;
        }

        event.setCancelled(true);
        Location repelCenter = chooseRepelCenter(spider.getLocation(), playerLocation, held, placed);
        if (repelCenter != null) {
            repel(spider, repelCenter);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null
                || (from.getWorld() == to.getWorld()
                        && from.getBlockX() == to.getBlockX()
                        && from.getBlockY() == to.getBlockY()
                        && from.getBlockZ() == to.getBlockZ())) {
            return;
        }

        Player player = event.getPlayer();
        boolean held = isHeld(player);
        WardBlock placed = findNearestPlacedWard(to);
        if (!held && placed == null) {
            return;
        }

        long now = System.currentTimeMillis();
        long previous = lastSweep.getOrDefault(player.getUniqueId(), 0L);
        if (now - previous < SWEEP_INTERVAL_MILLIS) {
            return;
        }
        lastSweep.put(player.getUniqueId(), now);

        Set<UUID> processed = new HashSet<>();
        if (placed != null && placedRadius > 0) {
            Location center = placed.center(to.getWorld());
            sweep(player, center, placedRadius, processed);
            pulse(player, center);
        }
        if (held && heldRadius > 0) {
            sweep(player, to, heldRadius, processed);
            if (placed == null) {
                pulse(player, to);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastSweep.remove(event.getPlayer().getUniqueId());
    }

    private void sweep(Player player, Location center, int radius, Set<UUID> processed) {
        Collection<Entity> nearby;
        if (Slimefun.getSchedulerService().isFolia()) {
            nearby = java.util.List.of(player.getChunk().getEntities());
        } else {
            nearby = center.getWorld().getNearbyEntities(center, radius, radius, radius);
        }

        double radiusSquared = (double) radius * radius;
        for (Entity entity : nearby) {
            if (!(entity instanceof Spider spider) || spider.isDead() || !processed.add(spider.getUniqueId())) {
                continue;
            }

            Location spiderLocation = spider.getLocation();
            if (spiderLocation.getWorld() != center.getWorld() || spiderLocation.distanceSquared(center) > radiusSquared) {
                continue;
            }

            if (player.equals(spider.getTarget())) {
                spider.setTarget(null);
            }
            repel(spider, center);
        }
    }

    private void pulse(Player player, Location center) {
        player.spawnParticle(Particle.SOUL_FIRE_FLAME, center, 2, 0.2D, 0.25D, 0.2D, 0.0D);
    }

    private void repel(Spider spider, Location center) {
        Location spiderLocation = spider.getLocation();
        double dx = spiderLocation.getX() - center.getX();
        double dz = spiderLocation.getZ() - center.getZ();
        Vector horizontal = new Vector(dx, 0.0D, dz);

        if (horizontal.lengthSquared() < 0.0001D) {
            horizontal = spiderLocation.getDirection().multiply(-1.0D).setY(0.0D);
        }
        if (horizontal.lengthSquared() < 0.0001D) {
            horizontal = new Vector(1.0D, 0.0D, 0.0D);
        }

        horizontal.normalize().multiply(REPEL_HORIZONTAL_SPEED);
        horizontal.setY(Math.max(REPEL_MIN_Y, spider.getVelocity().getY()));
        spider.setVelocity(horizontal);
    }

    private @Nullable Location chooseRepelCenter(
            Location spiderLocation, Location playerLocation, boolean held, @Nullable WardBlock placed) {
        if (placed != null && placedRadius > 0) {
            Location center = placed.center(playerLocation.getWorld());
            if (spiderLocation.distanceSquared(center) <= (double) placedRadius * placedRadius) {
                return center;
            }
        }

        if (held
                && heldRadius > 0
                && spiderLocation.distanceSquared(playerLocation) <= (double) heldRadius * heldRadius) {
            return playerLocation;
        }
        return null;
    }

    private boolean isHeld(Player player) {
        if (heldRadius <= 0) {
            return false;
        }
        return isItem(player.getInventory().getItemInMainHand()) || isItem(player.getInventory().getItemInOffHand());
    }

    private @Nullable WardBlock findNearestPlacedWard(Location location) {
        if (placedRadius <= 0) {
            return null;
        }

        Map<Long, Set<WardBlock>> worldWards = placedWards.get(location.getWorld().getUID());
        if (worldWards == null || worldWards.isEmpty()) {
            return null;
        }

        int chunkRadius = (placedRadius + 15) / 16;
        int centerChunkX = location.getBlockX() >> 4;
        int centerChunkZ = location.getBlockZ() >> 4;
        double radiusSquared = (double) placedRadius * placedRadius;
        double nearestSquared = Double.MAX_VALUE;
        WardBlock nearest = null;

        for (int chunkX = centerChunkX - chunkRadius; chunkX <= centerChunkX + chunkRadius; chunkX++) {
            for (int chunkZ = centerChunkZ - chunkRadius; chunkZ <= centerChunkZ + chunkRadius; chunkZ++) {
                Set<WardBlock> wards = worldWards.get(chunkKey(chunkX, chunkZ));
                if (wards == null) {
                    continue;
                }

                for (WardBlock ward : wards) {
                    double distanceSquared = ward.distanceSquared(location);
                    if (distanceSquared <= radiusSquared && distanceSquared < nearestSquared) {
                        nearestSquared = distanceSquared;
                        nearest = ward;
                    }
                }
            }
        }
        return nearest;
    }

    private void indexWard(Location location) {
        UUID worldId = location.getWorld().getUID();
        long chunkKey = chunkKey(location.getBlockX() >> 4, location.getBlockZ() >> 4);
        placedWards
                .computeIfAbsent(worldId, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(chunkKey, ignored -> ConcurrentHashMap.newKeySet())
                .add(WardBlock.from(location));
    }

    private void removeWard(Location location) {
        UUID worldId = location.getWorld().getUID();
        Map<Long, Set<WardBlock>> worldWards = placedWards.get(worldId);
        if (worldWards == null) {
            return;
        }

        long chunkKey = chunkKey(location.getBlockX() >> 4, location.getBlockZ() >> 4);
        Set<WardBlock> wards = worldWards.get(chunkKey);
        if (wards != null) {
            wards.remove(WardBlock.from(location));
            if (wards.isEmpty()) {
                worldWards.remove(chunkKey, wards);
            }
        }
        if (worldWards.isEmpty()) {
            placedWards.remove(worldId, worldWards);
        }
    }

    private static boolean isFoliaSafePair(Location first, Location second) {
        if (!Slimefun.getSchedulerService().isFolia()) {
            return true;
        }
        return first.getWorld() == second.getWorld()
                && (first.getBlockX() >> 4) == (second.getBlockX() >> 4)
                && (first.getBlockZ() >> 4) == (second.getBlockZ() >> 4);
    }

    private static int clampRadius(int radius) {
        return Math.max(0, Math.min(MAX_RADIUS, radius));
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    private record WardBlock(int x, int y, int z) {
        private static WardBlock from(Location location) {
            return new WardBlock(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }

        private Location center(World world) {
            return new Location(world, x + 0.5D, y + 0.5D, z + 0.5D);
        }

        private double distanceSquared(Location location) {
            double dx = x + 0.5D - location.getX();
            double dy = y + 0.5D - location.getY();
            double dz = z + 0.5D - location.getZ();
            return dx * dx + dy * dy + dz * dz;
        }
    }
}
