package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

/**
 * A carried early-warning curio for miners and explorers.
 *
 * <p>The canary chirps when it detects exposed lava, a hostile mob targeting or moving toward the carrier,
 * or a few immediate player-state hazards. Passive scans are throttled, bounded and never load chunks.</p>
 */
public final class MinersCanary extends SimpleSlimefunItem<ItemUseHandler> implements Listener {

    private static final int LAVA_RANGE = 6;
    private static final int LAVA_VERTICAL_RANGE = 5;
    private static final int MOB_RANGE = 12;
    private static final int MANUAL_COOLDOWN_TICKS = 5 * 20;
    private static final long PASSIVE_SCAN_INTERVAL_MILLIS = 2_000L;
    private static final long ALERT_COOLDOWN_MILLIS = 4_000L;
    private static final double APPROACHING_VELOCITY_SQUARED = 0.01D;
    private static final double APPROACHING_DOT_THRESHOLD = 0.08D;

    private static final BlockFace[] EXPOSED_FACES = {
        BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private static final BlockFace[] BREAK_HAZARD_FACES = {
        BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    private final Map<UUID, Long> lastPassiveScan = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastAlert = new ConcurrentHashMap<>();

    @ParametersAreNonnullByDefault
    public MinersCanary(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    public void registerListener(Plugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public @Nonnull ItemUseHandler getItemHandler() {
        return event -> {
            event.cancel();
            Player player = event.getPlayer();
            if (player.hasCooldown(Material.YELLOW_DYE)) {
                return;
            }

            Danger danger = detectDanger(player, true);
            player.setCooldown(Material.YELLOW_DYE, MANUAL_COOLDOWN_TICKS);
            if (danger == null) {
                player.playSound(player.getLocation(), Sound.ENTITY_PARROT_AMBIENT, 0.55F, 1.35F);
                player.sendMessage(ChatColor.YELLOW + "The Miner's Canary is calm. " + ChatColor.GRAY
                        + "No immediate danger detected nearby.");
            } else {
                chirp(player, danger, true);
            }
        };
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
        if (!isCarryingCanary(player)) {
            return;
        }

        long now = System.currentTimeMillis();
        long previous = lastPassiveScan.getOrDefault(player.getUniqueId(), 0L);
        if (now - previous < PASSIVE_SCAN_INTERVAL_MILLIS) {
            return;
        }
        lastPassiveScan.put(player.getUniqueId(), now);

        Danger danger = detectDanger(player, true);
        if (danger != null) {
            chirp(player, danger, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!isCarryingCanary(player)) {
            return;
        }

        Block broken = event.getBlock();
        for (BlockFace face : BREAK_HAZARD_FACES) {
            Block adjacent = broken.getRelative(face);
            if (adjacent.getType() == Material.LAVA) {
                double distance = adjacent.getLocation().add(0.5D, 0.5D, 0.5D).distance(player.getLocation());
                chirp(player, new Danger(DangerType.LAVA, "newly exposed lava", distance), false);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHostileTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getTarget() instanceof Player player)
                || !(event.getEntity() instanceof Enemy)
                || !isCarryingCanary(player)) {
            return;
        }

        if (Slimefun.getSchedulerService().isFolia()
                && (event.getEntity().getLocation().getBlockX() >> 4
                                != player.getLocation().getBlockX() >> 4
                        || event.getEntity().getLocation().getBlockZ() >> 4
                                != player.getLocation().getBlockZ() >> 4)) {
            return;
        }

        double distanceSquared = event.getEntity().getLocation().distanceSquared(player.getLocation());
        if (distanceSquared <= (double) MOB_RANGE * MOB_RANGE) {
            chirp(
                    player,
                    new Danger(DangerType.HOSTILE, event.getEntity().getType().name(), Math.sqrt(distanceSquared)),
                    false);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastPassiveScan.remove(uuid);
        lastAlert.remove(uuid);
    }

    private Danger detectDanger(Player player, boolean includeLavaScan) {
        Danger immediate = detectImmediatePlayerDanger(player);
        if (immediate != null) {
            return immediate;
        }

        Danger hostile = findApproachingHostile(player);
        if (hostile != null) {
            return hostile;
        }

        return includeLavaScan ? findExposedLava(player) : null;
    }

    private Danger detectImmediatePlayerDanger(Player player) {
        if (player.getFireTicks() > 0) {
            return new Danger(DangerType.FIRE, "fire", 0.0D);
        }
        if (player.getRemainingAir() <= Math.max(20, player.getMaximumAir() / 4)) {
            return new Danger(DangerType.DROWNING, "low air", 0.0D);
        }
        if (player.getFallDistance() >= 8.0F && player.getVelocity().getY() < -0.35D) {
            return new Danger(DangerType.FALL, "dangerous fall", player.getFallDistance());
        }
        return null;
    }

    private Danger findApproachingHostile(Player player) {
        Location origin = player.getLocation();
        Collection<Entity> nearby;
        if (Slimefun.getSchedulerService().isFolia()) {
            nearby = java.util.List.of(player.getChunk().getEntities());
        } else {
            nearby = player.getWorld().getNearbyEntities(origin, MOB_RANGE, MOB_RANGE, MOB_RANGE);
        }

        Danger nearest = null;
        double nearestSquared = Double.MAX_VALUE;
        for (Entity entity : nearby) {
            if (!(entity instanceof Enemy) || !(entity instanceof LivingEntity hostile) || hostile.isDead()) {
                continue;
            }

            double distanceSquared = hostile.getLocation().distanceSquared(origin);
            if (distanceSquared > (double) MOB_RANGE * MOB_RANGE || distanceSquared >= nearestSquared) {
                continue;
            }

            boolean targetingPlayer = entity instanceof Mob mob && player.equals(mob.getTarget());
            Vector velocity = entity.getVelocity();
            Vector towardPlayer =
                    origin.toVector().subtract(entity.getLocation().toVector());
            boolean movingTowardPlayer = velocity.lengthSquared() >= APPROACHING_VELOCITY_SQUARED
                    && velocity.dot(towardPlayer) > APPROACHING_DOT_THRESHOLD;

            if (targetingPlayer || movingTowardPlayer) {
                nearestSquared = distanceSquared;
                nearest = new Danger(DangerType.HOSTILE, entity.getType().name(), Math.sqrt(distanceSquared));
            }
        }
        return nearest;
    }

    private Danger findExposedLava(Player player) {
        Location origin = player.getLocation();
        World world = origin.getWorld();
        int originChunkX = origin.getBlockX() >> 4;
        int originChunkZ = origin.getBlockZ() >> 4;
        double nearestSquared = Double.MAX_VALUE;

        int minY = Math.max(world.getMinHeight(), origin.getBlockY() - LAVA_VERTICAL_RANGE);
        int maxY = Math.min(world.getMaxHeight() - 1, origin.getBlockY() + LAVA_VERTICAL_RANGE);
        for (int x = origin.getBlockX() - LAVA_RANGE; x <= origin.getBlockX() + LAVA_RANGE; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = origin.getBlockZ() - LAVA_RANGE; z <= origin.getBlockZ() + LAVA_RANGE; z++) {
                    int chunkX = x >> 4;
                    int chunkZ = z >> 4;
                    if (!world.isChunkLoaded(chunkX, chunkZ)) {
                        continue;
                    }
                    if (Slimefun.getSchedulerService().isFolia()
                            && (chunkX != originChunkX || chunkZ != originChunkZ)) {
                        continue;
                    }

                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() != Material.LAVA || !isExposed(block)) {
                        continue;
                    }

                    double dx = x + 0.5D - origin.getX();
                    double dy = y + 0.5D - origin.getY();
                    double dz = z + 0.5D - origin.getZ();
                    nearestSquared = Math.min(nearestSquared, dx * dx + dy * dy + dz * dz);
                }
            }
        }

        return nearestSquared == Double.MAX_VALUE
                ? null
                : new Danger(DangerType.LAVA, "exposed lava", Math.sqrt(nearestSquared));
    }

    private static boolean isExposed(Block lava) {
        for (BlockFace face : EXPOSED_FACES) {
            Block adjacent = lava.getRelative(face);
            if (adjacent.getType() != Material.LAVA && (adjacent.getType().isAir() || adjacent.isPassable())) {
                return true;
            }
        }
        return false;
    }

    private boolean isCarryingCanary(Player player) {
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (isItem(offHand)) {
            return true;
        }
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (isItem(stack)) {
                return true;
            }
        }
        return false;
    }

    private void chirp(Player player, Danger danger, boolean forceMessage) {
        long now = System.currentTimeMillis();
        long previous = lastAlert.getOrDefault(player.getUniqueId(), 0L);
        if (!forceMessage && now - previous < ALERT_COOLDOWN_MILLIS) {
            return;
        }
        lastAlert.put(player.getUniqueId(), now);

        player.playSound(player.getLocation(), Sound.ENTITY_PARROT_HURT, 0.9F, 1.7F);
        String detail =
                switch (danger.type()) {
                    case HOSTILE ->
                        ChatColor.RED + prettyName(danger.detail()) + ChatColor.GRAY + " is closing in about "
                                + ChatColor.WHITE + (int) Math.ceil(danger.distance()) + ChatColor.GRAY
                                + " blocks away.";
                    case LAVA ->
                        ChatColor.RED + "Exposed lava" + ChatColor.GRAY + " is about " + ChatColor.WHITE
                                + (int) Math.ceil(danger.distance()) + ChatColor.GRAY + " blocks away.";
                    case FIRE -> ChatColor.RED + "You are on fire.";
                    case DROWNING -> ChatColor.RED + "Your air is dangerously low.";
                    case FALL -> ChatColor.RED + "You are in a dangerous fall.";
                };
        player.sendMessage(ChatColor.GOLD + "The Miner's Canary chirps sharply! " + detail);
    }

    private static String prettyName(String value) {
        String[] words = value.toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return builder.toString();
    }

    private enum DangerType {
        HOSTILE,
        LAVA,
        FIRE,
        DROWNING,
        FALL
    }

    private record Danger(DangerType type, String detail, double distance) {}
}
