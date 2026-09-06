package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.ASlimefunDataContainer;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.config.CuriositiesConfig;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.handlers.SimpleBlockBreakHandler;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Spider;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

/**
 * A placeable expedition ward that causes spiders to avoid the immediate area.
 *
 * <p>The ward is intentionally non-lethal. Its ticker performs a bounded local pulse while a lightweight target
 * listener prevents spiders from acquiring players while the spider is inside an active ward. Active locations are
 * learned from place events and ticker heartbeats, so no world-wide block or entity scan is required.</p>
 */
public final class ArachnidWardTorch extends SlimefunItem implements Listener {

    private static final String CONFIG_ROOT = "SlimefunLegacyAddition.AdventurersCurios.arachnid-ward-torch";
    private static final int DEFAULT_RADIUS = 10;
    private static final int MIN_RADIUS = 3;
    private static final int MAX_RADIUS = 16;
    private static final int DEFAULT_PULSE_INTERVAL_TICKS = 40;
    private static final int MIN_PULSE_INTERVAL_TICKS = 20;
    private static final int MAX_PULSE_INTERVAL_TICKS = 200;
    private static final long TICK_MILLIS = 50L;
    private static final long MIN_ACTIVE_WINDOW_MILLIS = 5_000L;
    private static final double PUSH_STRENGTH = 0.32D;

    private final Map<WardKey, Long> activeWards = new ConcurrentHashMap<>();
    private final Map<WardKey, Long> lastPulse = new ConcurrentHashMap<>();

    @ParametersAreNonnullByDefault
    public ArachnidWardTorch(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
        addItemHandler(onPlace(), onBreak(), createTicker());
    }

    @Override
    public void postRegister() {
        if (isDisabled()) {
            return;
        }

        installConfigDefaults();
        registerListener(Slimefun.instance());
    }

    private void installConfigDefaults() {
        CuriositiesConfig config = CuriositiesConfig.getConfig();
        config.setDefaultValue(CONFIG_ROOT + ".radius", DEFAULT_RADIUS);
        config.setDefaultValue(CONFIG_ROOT + ".pulse-interval-ticks", DEFAULT_PULSE_INTERVAL_TICKS);
        config.setDefaultValue(CONFIG_ROOT + ".particles", true);
        config.save();
    }

    private void registerListener(Plugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private @Nonnull BlockPlaceHandler onPlace() {
        return new BlockPlaceHandler(false) {
            @Override
            public void onPlayerPlace(@Nonnull BlockPlaceEvent event) {
                WardKey key = WardKey.from(event.getBlockPlaced().getLocation());
                long now = System.currentTimeMillis();
                activeWards.put(key, now);
                lastPulse.put(key, 0L);
            }
        };
    }

    private @Nonnull SimpleBlockBreakHandler onBreak() {
        return new SimpleBlockBreakHandler() {
            @Override
            public void onBlockBreak(@Nonnull Block block) {
                WardKey key = WardKey.from(block.getLocation());
                activeWards.remove(key);
                lastPulse.remove(key);
            }
        };
    }

    private @Nonnull BlockTicker createTicker() {
        return new BlockTicker() {
            @Override
            public boolean isSynchronized() {
                return true;
            }

            @Override
            public void tick(Block block, SlimefunItem item, ASlimefunDataContainer data) {
                tickWard(block);
            }
        };
    }

    private void tickWard(Block block) {
        WardKey key = WardKey.from(block.getLocation());
        long now = System.currentTimeMillis();
        long intervalMillis = (long) getPulseIntervalTicks() * TICK_MILLIS;
        long previous = lastPulse.getOrDefault(key, 0L);
        if (now - previous < intervalMillis) {
            return;
        }

        lastPulse.put(key, now);
        activeWards.put(key, now);
        repelNearbySpiders(block, getRadius());

        if (showParticles()) {
            Location center = block.getLocation().add(0.5D, 0.65D, 0.5D);
            block.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, center, 4, 0.22D, 0.25D, 0.22D, 0.005D);
        }
    }

    private void repelNearbySpiders(Block block, int radius) {
        Location center = block.getLocation().add(0.5D, 0.45D, 0.5D);
        Collection<Entity> nearby;
        if (Slimefun.getSchedulerService().isFolia()) {
            // Cross-region entity iteration is unsafe on Folia. The target listener still protects the entire ward;
            // the physical push is deliberately limited to the owning chunk.
            nearby = java.util.List.of(block.getChunk().getEntities());
        } else {
            nearby = block.getWorld().getNearbyEntities(center, radius, radius, radius, entity -> entity instanceof Spider);
        }

        double radiusSquared = (double) radius * radius;
        for (Entity entity : nearby) {
            if (!(entity instanceof Spider spider) || spider.isDead()) {
                continue;
            }

            Location location = spider.getLocation();
            if (location.distanceSquared(center) > radiusSquared) {
                continue;
            }

            if (spider.getTarget() != null) {
                spider.setTarget(null);
            }

            Vector away = location.toVector().subtract(center.toVector());
            away.setY(0.0D);
            if (away.lengthSquared() < 0.0001D) {
                away.setX(1.0D);
            }
            away.normalize().multiply(PUSH_STRENGTH);
            Vector current = spider.getVelocity().multiply(0.45D);
            away.setY(Math.max(0.05D, current.getY()));
            spider.setVelocity(current.add(away));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpiderTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Spider spider) || !(event.getTarget() instanceof Player)) {
            return;
        }

        if (isInsideActiveWard(spider.getLocation(), System.currentTimeMillis())) {
            event.setCancelled(true);
        }
    }

    boolean isInsideActiveWard(Location location, long now) {
        int radius = getRadius();
        double radiusSquared = (double) radius * radius;
        long activeWindow = Math.max(MIN_ACTIVE_WINDOW_MILLIS, (long) getPulseIntervalTicks() * TICK_MILLIS * 3L);
        UUID worldId = location.getWorld().getUID();

        for (Map.Entry<WardKey, Long> entry : activeWards.entrySet()) {
            WardKey ward = entry.getKey();
            long heartbeat = entry.getValue();
            if (now - heartbeat > activeWindow) {
                activeWards.remove(ward, heartbeat);
                lastPulse.remove(ward);
                continue;
            }
            if (!ward.worldId.equals(worldId)) {
                continue;
            }
            if (distanceSquared(
                            ward.x + 0.5D,
                            ward.y + 0.45D,
                            ward.z + 0.5D,
                            location.getX(),
                            location.getY(),
                            location.getZ())
                    <= radiusSquared) {
                return true;
            }
        }
        return false;
    }

    static double distanceSquared(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = ax - bx;
        double dy = ay - by;
        double dz = az - bz;
        return dx * dx + dy * dy + dz * dz;
    }

    private int getRadius() {
        CuriositiesConfig config = CuriositiesConfig.getConfig();
        int configured = config.getInt(CONFIG_ROOT + ".radius");
        return Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, configured));
    }

    private int getPulseIntervalTicks() {
        CuriositiesConfig config = CuriositiesConfig.getConfig();
        int configured = config.getInt(CONFIG_ROOT + ".pulse-interval-ticks");
        return Math.max(MIN_PULSE_INTERVAL_TICKS, Math.min(MAX_PULSE_INTERVAL_TICKS, configured));
    }

    private boolean showParticles() {
        return CuriositiesConfig.getConfig().getBoolean(CONFIG_ROOT + ".particles");
    }

    private record WardKey(UUID worldId, int x, int y, int z) {
        private static WardKey from(Location location) {
            return new WardKey(
                    location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }
    }
}
