package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.ASlimefunDataContainer;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.Furnace;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/**
 * Shared, bounded runtime for Beacon Plus area effects.
 *
 * <p>One Slimefun block ticker drives periodic work. Event-driven effects query only recently observed, loaded
 * Beacon Plus blocks. No per-effect or per-beacon scheduler is created.
 */
final class BeaconPlusRuntime {

    static final String EFFECTS_KEY = "beacon_plus_effects";

    private static final int PULSE_INTERVAL_TICKS = 20;
    private static final int EFFECT_DURATION_TICKS = 100;
    private static final int NIGHT_VISION_DURATION_TICKS = 240;
    private static final int EXTRA_RANGE_BLOCKS = 20;
    private static final int MAX_TILE_ENTITIES_PER_PULSE = 96;
    private static final int CROP_SAMPLES_PER_PULSE = 48;
    private static final long OBSERVED_BEACON_TTL_MILLIS = 15_000L;
    private static final NamespacedKey SCALE_KEY = new NamespacedKey(Slimefun.instance(), "beacon_plus_scale");

    private static final Map<BeaconKey, Long> OBSERVED_BEACONS = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> ORIGINAL_ALLOW_FLIGHT = new ConcurrentHashMap<>();

    private static final Set<PotionEffectType> HARMFUL_EFFECTS = Set.of(
            PotionEffectType.SLOWNESS,
            PotionEffectType.MINING_FATIGUE,
            PotionEffectType.NAUSEA,
            PotionEffectType.BLINDNESS,
            PotionEffectType.HUNGER,
            PotionEffectType.WEAKNESS,
            PotionEffectType.POISON,
            PotionEffectType.WITHER,
            PotionEffectType.LEVITATION,
            PotionEffectType.UNLUCK,
            PotionEffectType.DARKNESS,
            PotionEffectType.BAD_OMEN,
            PotionEffectType.RAID_OMEN,
            PotionEffectType.TRIAL_OMEN);

    private BeaconPlusRuntime() {}

    static void observe(Block block) {
        OBSERVED_BEACONS.put(BeaconKey.from(block.getLocation()), System.currentTimeMillis());
    }

    static void forget(Location location) {
        OBSERVED_BEACONS.remove(BeaconKey.from(location));
    }

    static EnumSet<BeaconPlusEffect> getConfiguredEffects(Location location) {
        EnumSet<BeaconPlusEffect> effects = BeaconPlusEffect.parse(StorageCacheUtils.getData(location, EFFECTS_KEY));
        BeaconPlusManager manager = BeaconPlusManager.getInstance();
        if (manager != null && manager.getChunkMode(location) != BeaconPlusChunkMode.OFF) {
            effects.add(BeaconPlusEffect.ACTIVATOR);
        }
        return effects;
    }

    static void setConfiguredEffects(Location location, Set<BeaconPlusEffect> effects) {
        EnumSet<BeaconPlusEffect> stored =
                effects.isEmpty() ? EnumSet.noneOf(BeaconPlusEffect.class) : EnumSet.copyOf(effects);
        stored.remove(BeaconPlusEffect.ACTIVATOR);
        StorageCacheUtils.setData(location, EFFECTS_KEY, BeaconPlusEffect.serialize(stored));
    }

    static boolean hasEffect(Location target, BeaconPlusEffect effect) {
        return BeaconPlusPowerState.hasPoweredEffect(target, effect);
    }

    /**
     * @return -1 when no currently powered Beacon Plus provides the effect, otherwise 0 or 1 for normal/extra power
     */
    static int getPowerForEffect(Location target, BeaconPlusEffect effect) {
        return BeaconPlusPowerState.getPowerForEffect(target, effect);
    }

    static void tick(Block block, ASlimefunDataContainer data) {
        observe(block);

        long gameTime = block.getWorld().getGameTime();

        double range = getRange(block);
        if (range <= 0.0D) {
            refreshNearbyPlayerStates(block, 96.0D);
            return;
        }

        EnumSet<BeaconPlusEffect> effects = BeaconPlusEffect.parse(data.getData(EFFECTS_KEY));
        BeaconPlusManager manager = BeaconPlusManager.getInstance();
        if (manager != null && manager.getChunkMode(block.getLocation()) != BeaconPlusChunkMode.OFF) {
            effects.add(BeaconPlusEffect.ACTIVATOR);
        }

        int power = effects.contains(BeaconPlusEffect.EXTRA_POWER) ? 1 : 0;
        Location center = block.getLocation().add(0.5D, 0.5D, 0.5D);
        Collection<Entity> entities = getEntities(block, center, range);

        for (Entity entity : entities) {
            if (entity instanceof Player player) {
                applyPlayerEffects(player, effects, power, gameTime);
                continue;
            }

            if (entity instanceof Monster monster) {
                applyMonsterEffects(monster, effects, power, center);
            }
            if (effects.contains(BeaconPlusEffect.GRAVITY_WELL)
                    && (entity instanceof Mob || entity instanceof Item)) {
                pullEntity(entity, center, power);
            }
        }

        if (effects.contains(BeaconPlusEffect.FURNACE_BOOSTER) || effects.contains(BeaconPlusEffect.SPAWNERS)) {
            applyTileEntityBoosts(block, center, range, effects, power);
        }
        if (effects.contains(BeaconPlusEffect.CROPS) && gameTime % 40L < PULSE_INTERVAL_TICKS) {
            applyCropBoost(block, center, range, power);
        }
    }

    static void refreshPlayerState(Player player) {
        boolean shouldFly = hasEffect(player.getLocation(), BeaconPlusEffect.FLYING);
        updateFlight(player, shouldFly);

        boolean shouldScale = hasEffect(player.getLocation(), BeaconPlusEffect.SCALE);
        updateScale(player, shouldScale);
    }

    static void refreshNearbyPlayerStates(Block block, double range) {
        Location center = block.getLocation().add(0.5D, 0.5D, 0.5D);
        for (Entity entity : getEntities(block, center, range)) {
            if (entity instanceof Player player) {
                refreshPlayerState(player);
            }
        }
    }

    static void clearPlayerState(Player player) {
        Boolean original = ORIGINAL_ALLOW_FLIGHT.remove(player.getUniqueId());
        if (original != null
                && player.getGameMode() != GameMode.CREATIVE
                && player.getGameMode() != GameMode.SPECTATOR) {
            player.setAllowFlight(original);
            if (!original && player.isFlying()) {
                player.setFlying(false);
            }
        }
        updateScale(player, false);
    }

    static void shutdown() {
        for (Player player : Slimefun.instance().getServer().getOnlinePlayers()) {
            clearPlayerState(player);
        }
        OBSERVED_BEACONS.clear();
        ORIGINAL_ALLOW_FLIGHT.clear();
    }

    private static boolean isEffectEnabled(Location location, BeaconPlusEffect effect) {
        if (effect == BeaconPlusEffect.ACTIVATOR) {
            BeaconPlusManager manager = BeaconPlusManager.getInstance();
            return manager != null && manager.getChunkMode(location) != BeaconPlusChunkMode.OFF;
        }
        return BeaconPlusEffect.parse(StorageCacheUtils.getData(location, EFFECTS_KEY))
                .contains(effect);
    }

    private static double getRange(Block block) {
        double range = BeaconPlusPowerSource.getBaseRange(block);
        if (range <= 0.0D) {
            return 0.0D;
        }

        if (isEffectEnabled(block.getLocation(), BeaconPlusEffect.EXTRA_RANGE)) {
            range += EXTRA_RANGE_BLOCKS;
        }
        return range;
    }

    private static Collection<Entity> getEntities(Block block, Location center, double range) {
        if (Slimefun.getSchedulerService().isFolia()) {
            List<Entity> result = new ArrayList<>();
            double rangeSquared = range * range;
            for (Entity entity : block.getChunk().getEntities()) {
                if (entity.getLocation().distanceSquared(center) <= rangeSquared) {
                    result.add(entity);
                }
            }
            return result;
        }
        return block.getWorld().getNearbyEntities(center, range, range, range);
    }

    private static void applyPlayerEffects(Player player, EnumSet<BeaconPlusEffect> effects, int power, long gameTime) {
        if (effects.contains(BeaconPlusEffect.STRENGTH)) {
            applyPotion(player, PotionEffectType.STRENGTH, power, EFFECT_DURATION_TICKS);
        }
        if (effects.contains(BeaconPlusEffect.REGENERATION)) {
            applyPotion(player, PotionEffectType.REGENERATION, power, EFFECT_DURATION_TICKS);
        }
        if (effects.contains(BeaconPlusEffect.RESISTANCE)) {
            applyPotion(player, PotionEffectType.RESISTANCE, power, EFFECT_DURATION_TICKS);
        }
        if (effects.contains(BeaconPlusEffect.FAST_DIGGING)) {
            applyPotion(player, PotionEffectType.HASTE, power, EFFECT_DURATION_TICKS);
        }
        if (effects.contains(BeaconPlusEffect.SPEED)) {
            applyPotion(player, PotionEffectType.SPEED, power, EFFECT_DURATION_TICKS);
        }
        if (effects.contains(BeaconPlusEffect.NIGHT_VISION)) {
            applyPotion(player, PotionEffectType.NIGHT_VISION, 0, NIGHT_VISION_DURATION_TICKS);
        }
        if (effects.contains(BeaconPlusEffect.LUCK)) {
            applyPotion(player, PotionEffectType.LUCK, power, EFFECT_DURATION_TICKS);
        }
        if (effects.contains(BeaconPlusEffect.WATER_BREATHING)) {
            applyPotion(player, PotionEffectType.WATER_BREATHING, 0, EFFECT_DURATION_TICKS);
        }
        if (effects.contains(BeaconPlusEffect.JUMP)) {
            applyPotion(player, PotionEffectType.JUMP_BOOST, power, EFFECT_DURATION_TICKS);
        }
        if (effects.contains(BeaconPlusEffect.CURE)) {
            for (PotionEffectType harmful : HARMFUL_EFFECTS) {
                player.removePotionEffect(harmful);
            }
        }
        if (effects.contains(BeaconPlusEffect.FIRE_EXTINGUISHER) && player.getFireTicks() > 0) {
            player.setFireTicks(0);
        }
        if (effects.contains(BeaconPlusEffect.EXP_GAIN) && gameTime % 100L < PULSE_INTERVAL_TICKS) {
            player.giveExp(1 + power);
        }
        if (effects.contains(BeaconPlusEffect.AUTO_REPAIR) && gameTime % 100L < PULSE_INTERVAL_TICKS) {
            repairInventory(player.getInventory(), 1 + power);
        }
        if (effects.contains(BeaconPlusEffect.FLYING)) {
            updateFlight(player, true);
        }
        if (effects.contains(BeaconPlusEffect.SCALE)) {
            updateScale(player, true);
        }
    }

    private static void applyMonsterEffects(
            Monster monster, EnumSet<BeaconPlusEffect> effects, int power, Location center) {
        if (effects.contains(BeaconPlusEffect.SLOWDOWN)) {
            applyPotion(monster, PotionEffectType.SLOWNESS, power, EFFECT_DURATION_TICKS);
        }
        if (effects.contains(BeaconPlusEffect.POISON)) {
            applyPotion(monster, PotionEffectType.POISON, power, EFFECT_DURATION_TICKS);
        }
        if (effects.contains(BeaconPlusEffect.BURNER) && isUndead(monster.getType())) {
            monster.setFireTicks(Math.max(monster.getFireTicks(), 80));
        }
        if (effects.contains(BeaconPlusEffect.PEACEFUL)) {
            monster.setTarget(null);
        }
    }

    private static void applyPotion(LivingEntity entity, PotionEffectType type, int amplifier, int duration) {
        entity.addPotionEffect(new PotionEffect(type, duration, amplifier, false, true, true));
    }

    private static void pullEntity(Entity entity, Location center, int power) {
        Vector delta = center.toVector().subtract(entity.getLocation().toVector());
        if (delta.lengthSquared() < 0.25D) {
            return;
        }

        Vector pull = delta.normalize().multiply(0.30D + 0.12D * power);
        pull.setY(Math.max(-0.60D, Math.min(0.60D, pull.getY())));
        entity.setVelocity(entity.getVelocity().multiply(0.75D).add(pull));
    }

    private static void applyTileEntityBoosts(
            Block beaconBlock, Location center, double range, EnumSet<BeaconPlusEffect> effects, int power) {
        int inspected = 0;
        double rangeSquared = range * range;
        for (Chunk chunk : getLoadedChunksInRange(beaconBlock, range)) {
            for (BlockState state : chunk.getTileEntities()) {
                if (++inspected > MAX_TILE_ENTITIES_PER_PULSE) {
                    return;
                }
                if (state.getLocation().distanceSquared(center) > rangeSquared) {
                    continue;
                }

                if (effects.contains(BeaconPlusEffect.FURNACE_BOOSTER) && state instanceof Furnace furnace) {
                    boostFurnace(furnace, power);
                }
                if (effects.contains(BeaconPlusEffect.SPAWNERS) && state instanceof CreatureSpawner spawner) {
                    boostSpawner(spawner, power);
                }
            }
        }
    }

    private static void boostFurnace(Furnace furnace, int power) {
        if (furnace.getBurnTime() <= 0 || furnace.getCookTimeTotal() <= 0) {
            return;
        }

        int extraTicks = 4 + power * 4;
        int next = Math.min(furnace.getCookTimeTotal() - 1, furnace.getCookTime() + extraTicks);
        if (next > furnace.getCookTime()) {
            furnace.setCookTime((short) next);
            furnace.update(true, false);
        }
    }

    private static void boostSpawner(CreatureSpawner spawner, int power) {
        int reduction = 20 + power * 10;
        int next = Math.max(20, spawner.getDelay() - reduction);
        if (next < spawner.getDelay()) {
            spawner.setDelay(next);
            spawner.update(true, false);
        }
    }

    private static void applyCropBoost(Block beaconBlock, Location center, double range, int power) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int horizontal = Math.max(1, (int) Math.floor(range));
        int samples = CROP_SAMPLES_PER_PULSE + power * 16;

        for (int i = 0; i < samples; i++) {
            int x = center.getBlockX() + random.nextInt(-horizontal, horizontal + 1);
            int z = center.getBlockZ() + random.nextInt(-horizontal, horizontal + 1);
            int y = center.getBlockY() + random.nextInt(-8, 9);

            if (y < beaconBlock.getWorld().getMinHeight()
                    || y >= beaconBlock.getWorld().getMaxHeight()) {
                continue;
            }
            if (!beaconBlock.getWorld().isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }
            if (Slimefun.getSchedulerService().isFolia()
                    && (x >> 4 != beaconBlock.getX() >> 4 || z >> 4 != beaconBlock.getZ() >> 4)) {
                continue;
            }

            Block target = beaconBlock.getWorld().getBlockAt(x, y, z);
            if (target.getLocation().distanceSquared(center) > range * range) {
                continue;
            }
            if (target.getBlockData() instanceof Ageable ageable && ageable.getAge() < ageable.getMaximumAge()) {
                ageable.setAge(Math.min(ageable.getMaximumAge(), ageable.getAge() + 1 + power));
                target.setBlockData(ageable, false);
            }
        }
    }

    private static List<Chunk> getLoadedChunksInRange(Block beaconBlock, double range) {
        List<Chunk> chunks = new ArrayList<>();
        int centerChunkX = beaconBlock.getX() >> 4;
        int centerChunkZ = beaconBlock.getZ() >> 4;
        if (Slimefun.getSchedulerService().isFolia()) {
            chunks.add(beaconBlock.getChunk());
            return chunks;
        }

        int chunkRadius = Math.max(0, (int) Math.ceil(range / 16.0D));
        World world = beaconBlock.getWorld();
        for (int x = centerChunkX - chunkRadius; x <= centerChunkX + chunkRadius; x++) {
            for (int z = centerChunkZ - chunkRadius; z <= centerChunkZ + chunkRadius; z++) {
                if (world.isChunkLoaded(x, z)) {
                    chunks.add(world.getChunkAt(x, z));
                }
            }
        }
        return chunks;
    }

    private static void repairInventory(PlayerInventory inventory, int amount) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null
                    || stack.getType() == Material.AIR
                    || stack.getType().getMaxDurability() <= 0) {
                continue;
            }

            ItemMeta meta = stack.getItemMeta();
            if (!(meta instanceof Damageable damageable) || damageable.getDamage() <= 0) {
                continue;
            }

            damageable.setDamage(Math.max(0, damageable.getDamage() - amount));
            stack.setItemMeta(meta);
            inventory.setItem(slot, stack);
        }
    }

    private static void updateFlight(Player player, boolean enabled) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            ORIGINAL_ALLOW_FLIGHT.remove(player.getUniqueId());
            return;
        }

        if (enabled) {
            ORIGINAL_ALLOW_FLIGHT.putIfAbsent(player.getUniqueId(), player.getAllowFlight());
            if (!player.getAllowFlight()) {
                player.setAllowFlight(true);
            }
            return;
        }

        Boolean original = ORIGINAL_ALLOW_FLIGHT.remove(player.getUniqueId());
        if (original != null) {
            player.setAllowFlight(original);
            if (!original && player.isFlying()) {
                player.setFlying(false);
            }
        }
    }

    private static void updateScale(Player player, boolean enabled) {
        AttributeInstance scale = player.getAttribute(Attribute.SCALE);
        if (scale == null) {
            return;
        }

        AttributeModifier existing = scale.getModifiers().stream()
                .filter(modifier -> SCALE_KEY.equals(modifier.getKey()))
                .findFirst()
                .orElse(null);

        if (enabled && existing == null) {
            scale.addModifier(new AttributeModifier(SCALE_KEY, 0.25D, AttributeModifier.Operation.ADD_SCALAR));
        } else if (!enabled && existing != null) {
            scale.removeModifier(existing);
        }
    }

    private static boolean isUndead(EntityType type) {
        return switch (type) {
            case ZOMBIE,
                    ZOMBIE_VILLAGER,
                    HUSK,
                    DROWNED,
                    SKELETON,
                    STRAY,
                    WITHER_SKELETON,
                    BOGGED,
                    PHANTOM,
                    ZOGLIN,
                    ZOMBIFIED_PIGLIN,
                    WITHER -> true;
            default -> false;
        };
    }

    private static void purgeStaleObservedBeacons() {
        long cutoff = System.currentTimeMillis() - OBSERVED_BEACON_TTL_MILLIS;
        OBSERVED_BEACONS.entrySet().removeIf(entry -> entry.getValue() < cutoff);
    }

    private record BeaconKey(UUID worldId, int x, int y, int z) {
        private static BeaconKey from(Location location) {
            return new BeaconKey(
                    location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }

        private Location toLocation(World world) {
            return new Location(world, x, y, z);
        }
    }
}
