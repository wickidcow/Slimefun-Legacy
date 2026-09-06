package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Spider;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

/**
 * A portable and placeable spider-repelling ward for Adventurer's Curios.
 *
 * <p>The ward never damages spiders. A carried torch creates a smaller mobile field, while a placed torch creates a
 * larger stationary field. Placed ward locations are learned from their normal Slimefun ticker rather than by
 * scanning worlds or forcing chunks to load.</p>
 */
public final class ArachnidWardTorch extends SlimefunItem implements Listener {

    private static final double REPEL_HORIZONTAL_SPEED = 0.42D;
    private static final double REPEL_VERTICAL_SPEED = 0.12D;
    private static final int PARTICLE_COUNT = 2;

    private final int heldRadius;
    private final int placedRadius;
    private final int scanIntervalTicks;
    private final long placedWardExpiryMillis;

    /** Active placed wards, indexed by their own chunk. Entries expire if their ticker stops refreshing them. */
    private final Map<ChunkKey, ConcurrentHashMap<WardPoint, Long>> placedWards = new ConcurrentHashMap<>();

    private volatile int tickerCycle;

    @ParametersAreNonnullByDefault
    public ArachnidWardTorch(
            ItemGroup itemGroup,
            SlimefunItemStack item,
            RecipeType recipeType,
            ItemStack[] recipe,
            int heldRadius,
            int placedRadius,
            int scanIntervalTicks) {
        super(itemGroup, item, recipeType, recipe);
        this.heldRadius = heldRadius;
        this.placedRadius = placedRadius;
        this.scanIntervalTicks = scanIntervalTicks;
        this.placedWardExpiryMillis = Math.max(3_000L, scanIntervalTicks * 50L * 3L);
    }

    @Override
    public void preRegister() {
        addItemHandler(new BlockTicker() {
            @Override
            public boolean isSynchronized() {
                return true;
            }

            @Override
            public void uniqueTick() {
                tickerCycle++;
                if (tickerCycle >= scanIntervalTicks) {
                    tickerCycle = 0;
                }
            }

            @Override
            public void tick(org.bukkit.block.Block block, SlimefunItem item, SlimefunBlockData data) {
                if (tickerCycle != 0) {
                    return;
                }

                refreshPlacedWard(block);
            }
        });
    }

    /** Registers the immediate target/damage guards and the bounded held-item scan. */
    public void registerListener(Plugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        Slimefun.getSchedulerService().runAtFixedRate(
                () -> {
                    for (Player player : plugin.getServer().getOnlinePlayers()) {
                        if (Slimefun.getSchedulerService().isFolia()) {
                            Slimefun.getSchedulerService().runFor(player, () -> scanHeldWard(player));
                        } else {
                            scanHeldWard(player);
                        }
                    }
                },
                scanIntervalTicks,
                scanIntervalTicks);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpiderTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Spider spider) || !(event.getTarget() instanceof Player player)) {
            return;
        }

        WardSource source = strongestProtectingSource(player, spider);
        if (source == null) {
            return;
        }

        event.setCancelled(true);
        spider.setTarget(null);
        repel(spider, source.location());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpiderDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Spider spider) || !(event.getEntity() instanceof Player player)) {
            return;
        }

        WardSource source = strongestProtectingSource(player, spider);
        if (source == null) {
            return;
        }

        event.setCancelled(true);
        if (player.equals(spider.getTarget())) {
            spider.setTarget(null);
        }
        repel(spider, source.location());
    }

    private void scanHeldWard(Player player) {
        if (!player.isOnline() || player.isDead() || !isHeld(player)) {
            return;
        }

        Location source = player.getLocation();
        for (Entity entity : nearbyEntities(source, heldRadius)) {
            if (!(entity instanceof Spider spider) || spider.isDead()) {
                continue;
            }

            double distanceSquared = distanceSquared(source, spider.getLocation());
            if (distanceSquared > square(heldRadius)) {
                continue;
            }

            WardSource placed = findPlacedWard(spider.getLocation(), player.getLocation());
            if (placed != null && placed.radius() >= heldRadius) {
                continue;
            }

            if (player.equals(spider.getTarget())) {
                spider.setTarget(null);
            }
            repel(spider, source);
        }
    }

    private void refreshPlacedWard(org.bukkit.block.Block block) {
        Location center = block.getLocation().add(0.5D, 0.5D, 0.5D);
        WardPoint point = WardPoint.from(center);
        long now = System.currentTimeMillis();
        placedWards
                .computeIfAbsent(point.chunkKey(), ignored -> new ConcurrentHashMap<>())
                .put(point, now);

        for (Entity entity : nearbyEntities(center, placedRadius)) {
            if (!(entity instanceof Spider spider) || spider.isDead()) {
                continue;
            }

            if (distanceSquared(center, spider.getLocation()) > square(placedRadius)) {
                continue;
            }

            // If a deliberately configured held ward is stronger, let the carrier's scan own the repulsion.
            Player strongerCarrier = findStrongerHeldCarrier(spider);
            if (strongerCarrier != null) {
                continue;
            }

            if (spider.getTarget() instanceof Player target
                    && distanceSquared(center, target.getLocation()) <= square(placedRadius)) {
                spider.setTarget(null);
            }
            repel(spider, center);
        }

        pruneChunk(point.chunkKey(), now);
    }

    private WardSource strongestProtectingSource(Player player, Spider spider) {
        Location playerLocation = player.getLocation();
        Location spiderLocation = spider.getLocation();

        WardSource placed = findPlacedWard(spiderLocation, playerLocation);
        boolean held = isHeld(player) && distanceSquared(playerLocation, spiderLocation) <= square(heldRadius);

        if (placed == null) {
            return held ? new WardSource(playerLocation, heldRadius) : null;
        }
        if (!held || placed.radius() >= heldRadius) {
            return placed;
        }
        return new WardSource(playerLocation, heldRadius);
    }

    private WardSource findPlacedWard(Location spiderLocation, Location playerLocation) {
        if (spiderLocation.getWorld() == null
                || playerLocation.getWorld() == null
                || spiderLocation.getWorld() != playerLocation.getWorld()) {
            return null;
        }

        UUID worldId = spiderLocation.getWorld().getUID();
        int chunkRadius = Math.max(1, (placedRadius + 15) >> 4);
        int centerChunkX = spiderLocation.getBlockX() >> 4;
        int centerChunkZ = spiderLocation.getBlockZ() >> 4;
        long now = System.currentTimeMillis();
        double radiusSquared = square(placedRadius);

        WardSource nearest = null;
        double nearestSquared = Double.MAX_VALUE;
        for (int chunkX = centerChunkX - chunkRadius; chunkX <= centerChunkX + chunkRadius; chunkX++) {
            for (int chunkZ = centerChunkZ - chunkRadius; chunkZ <= centerChunkZ + chunkRadius; chunkZ++) {
                ChunkKey chunkKey = new ChunkKey(worldId, chunkX, chunkZ);
                Map<WardPoint, Long> wards = placedWards.get(chunkKey);
                if (wards == null) {
                    continue;
                }

                for (Map.Entry<WardPoint, Long> entry : wards.entrySet()) {
                    if (now - entry.getValue() > placedWardExpiryMillis) {
                        wards.remove(entry.getKey(), entry.getValue());
                        continue;
                    }

                    WardPoint point = entry.getKey();
                    double spiderDistance = point.distanceSquared(spiderLocation);
                    if (spiderDistance > radiusSquared || point.distanceSquared(playerLocation) > radiusSquared) {
                        continue;
                    }
                    if (spiderDistance < nearestSquared) {
                        nearestSquared = spiderDistance;
                        nearest = new WardSource(point.toLocation(spiderLocation.getWorld()), placedRadius);
                    }
                }

                if (wards.isEmpty()) {
                    placedWards.remove(chunkKey, wards);
                }
            }
        }
        return nearest;
    }

    private Player findStrongerHeldCarrier(Spider spider) {
        if (heldRadius <= placedRadius) {
            return null;
        }

        Location spiderLocation = spider.getLocation();
        for (Entity entity : nearbyEntities(spiderLocation, heldRadius)) {
            if (entity instanceof Player player
                    && player.isOnline()
                    && !player.isDead()
                    && isHeld(player)
                    && distanceSquared(spiderLocation, player.getLocation()) <= square(heldRadius)) {
                return player;
            }
        }
        return null;
    }

    private Collection<Entity> nearbyEntities(Location center, int radius) {
        World world = center.getWorld();
        if (world == null) {
            return java.util.List.of();
        }

        if (Slimefun.getSchedulerService().isFolia()) {
            return java.util.List.of(center.getChunk().getEntities());
        }
        return world.getNearbyEntities(center, radius, radius, radius);
    }

    private boolean isHeld(Player player) {
        return isItem(player.getInventory().getItemInMainHand()) || isItem(player.getInventory().getItemInOffHand());
    }

    private void repel(Spider spider, Location source) {
        Location spiderLocation = spider.getLocation();
        Vector away = spiderLocation.toVector().subtract(source.toVector());
        away.setY(0.0D);

        if (away.lengthSquared() < 0.0001D) {
            away.setX(1.0D);
        } else {
            away.normalize();
        }

        away.multiply(REPEL_HORIZONTAL_SPEED);
        away.setY(Math.max(REPEL_VERTICAL_SPEED, spider.getVelocity().getY()));
        spider.setVelocity(away);
        spider.getWorld().spawnParticle(
                Particle.SOUL_FIRE_FLAME,
                spiderLocation.clone().add(0.0D, 0.45D, 0.0D),
                PARTICLE_COUNT,
                0.12D,
                0.15D,
                0.12D,
                0.01D);
    }

    private void pruneChunk(ChunkKey chunkKey, long now) {
        Map<WardPoint, Long> wards = placedWards.get(chunkKey);
        if (wards == null) {
            return;
        }
        wards.entrySet().removeIf(entry -> now - entry.getValue() > placedWardExpiryMillis);
        if (wards.isEmpty()) {
            placedWards.remove(chunkKey, wards);
        }
    }

    private static double distanceSquared(Location first, Location second) {
        if (first.getWorld() == null || second.getWorld() == null || first.getWorld() != second.getWorld()) {
            return Double.MAX_VALUE;
        }
        double dx = first.getX() - second.getX();
        double dy = first.getY() - second.getY();
        double dz = first.getZ() - second.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static double square(int value) {
        return (double) value * value;
    }

    private record WardSource(Location location, int radius) {}

    private record ChunkKey(UUID worldId, int chunkX, int chunkZ) {}

    private record WardPoint(UUID worldId, int blockX, int blockY, int blockZ) {
        static WardPoint from(Location location) {
            return new WardPoint(
                    location.getWorld().getUID(),
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ());
        }

        ChunkKey chunkKey() {
            return new ChunkKey(worldId, blockX >> 4, blockZ >> 4);
        }

        double distanceSquared(Location location) {
            if (location.getWorld() == null || !worldId.equals(location.getWorld().getUID())) {
                return Double.MAX_VALUE;
            }
            double dx = blockX + 0.5D - location.getX();
            double dy = blockY + 0.5D - location.getY();
            double dz = blockZ + 0.5D - location.getZ();
            return dx * dx + dy * dy + dz * dz;
        }

        Location toLocation(World world) {
            return new Location(world, blockX + 0.5D, blockY + 0.5D, blockZ + 0.5D);
        }
    }
}
