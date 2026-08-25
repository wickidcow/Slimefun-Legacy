package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.ArrayList;
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

/** Performs the bounded world/player work for a resolved Resonance Beacon pulse. */
final class BeaconPlusRuntimeEffects {

    static final int MAX_TILE_ENTITIES_PER_PULSE = 96;
    static final int CROP_SAMPLES_PER_CHUNK = 8;
    static final int MAX_CROP_SAMPLES_PER_PULSE = 512;
    private static final int CROP_VERTICAL_RADIUS = 8;
    private static final int PULSE_INTERVAL_TICKS = 20;
    private static final int PLAYER_EFFECT_DURATION_TICKS = 600;
    private static final int PLAYER_EFFECT_REFRESH_THRESHOLD_TICKS = 300;
    private static final int MOB_EFFECT_DURATION_TICKS = 70;
    private static final long TILE_ENTITY_DISCOVERY_CACHE_TICKS = 300L;
    private static final int MAX_TILE_ENTITY_CACHE_CHUNKS = 4096;
    private static final NamespacedKey SCALE_KEY = new NamespacedKey(Slimefun.instance(), "beacon_plus_scale");
    private static final Map<UUID, Boolean> ORIGINAL_ALLOW_FLIGHT = new ConcurrentHashMap<>();
    private static final Map<TileEntityChunkKey, CachedTileEntities> TILE_ENTITY_CACHE = new ConcurrentHashMap<>();
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

    private BeaconPlusRuntimeEffects() {}

    static void applyPulse(Block block, Map<BeaconPlusEffect, Integer> tiers, double range, long gameTime) {
        // Resolve the loaded field once per pulse. Entity, tile-entity and crop work all use the same
        // snapshot instead of rebuilding the chunk footprint independently for every effect family.
        List<Chunk> loadedChunks = getLoadedChunksInField(block, range);
        if (loadedChunks.isEmpty()) {
            return;
        }

        Location center = block.getLocation().add(0.5D, 0.5D, 0.5D);
        int gravityTier = tiers.getOrDefault(BeaconPlusEffect.GRAVITY_WELL, 0);
        for (Chunk chunk : loadedChunks) {
            for (Entity entity : chunk.getEntities()) {
                if (entity instanceof Player player) {
                    applyPlayerEffects(player, tiers, gameTime);
                } else if (entity instanceof Monster monster) {
                    applyMonsterEffects(monster, tiers);
                }

                // Match the proven BeaconPlus behavior: every resolved once-per-second pulse may pull AI mobs and items.
                if (gravityTier > 0 && (entity instanceof Mob || entity instanceof Item)) {
                    pullEntity(entity, center, gravityTier);
                }
            }
        }

        int furnaceTier = tiers.getOrDefault(BeaconPlusEffect.FURNACE_BOOSTER, 0);
        int spawnerTier = tiers.getOrDefault(BeaconPlusEffect.SPAWNERS, 0);
        if (furnaceTier > 0 || spawnerTier > 0) {
            applyTileEntityBoosts(loadedChunks, furnaceTier, spawnerTier, gameTime);
        }
        int cropTier = tiers.getOrDefault(BeaconPlusEffect.CROPS, 0);
        if (cropTier > 0 && gameTime % 40L < PULSE_INTERVAL_TICKS) {
            applyCropBoost(block, loadedChunks, cropTier);
        }
    }

    static void refreshPlayerState(Player player) {
        updateFlight(player, BeaconPlusRuntime.hasEffect(player.getLocation(), BeaconPlusEffect.FLYING));
        updateScale(player, false);
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
        ORIGINAL_ALLOW_FLIGHT.clear();
        TILE_ENTITY_CACHE.clear();
    }

    static void refreshNearbyPlayerStates(Block block, double range) {
        // This path is mostly used for power-loss cleanup. Avoid building an intermediate entity list.
        for (Chunk chunk : getLoadedChunksInField(block, range)) {
            for (Entity entity : chunk.getEntities()) {
                if (entity instanceof Player player) {
                    refreshPlayerState(player);
                }
            }
        }
    }

    private static void applyPlayerEffects(Player player, Map<BeaconPlusEffect, Integer> tiers, long gameTime) {
        applyPlayerPotionIfPresent(player, tiers, BeaconPlusEffect.STRENGTH, PotionEffectType.STRENGTH);
        applyPlayerPotionIfPresent(player, tiers, BeaconPlusEffect.REGENERATION, PotionEffectType.REGENERATION);
        applyPlayerPotionIfPresent(player, tiers, BeaconPlusEffect.RESISTANCE, PotionEffectType.RESISTANCE);
        applyPlayerPotionIfPresent(player, tiers, BeaconPlusEffect.FAST_DIGGING, PotionEffectType.HASTE);
        applyPlayerPotionIfPresent(player, tiers, BeaconPlusEffect.SPEED, PotionEffectType.SPEED);
        applyPlayerPotionIfPresent(player, tiers, BeaconPlusEffect.NIGHT_VISION, PotionEffectType.NIGHT_VISION);
        applyPlayerPotionIfPresent(player, tiers, BeaconPlusEffect.LUCK, PotionEffectType.LUCK);
        applyPlayerPotionIfPresent(
                player, tiers, BeaconPlusEffect.WATER_BREATHING, PotionEffectType.WATER_BREATHING);
        applyPlayerPotionIfPresent(player, tiers, BeaconPlusEffect.JUMP, PotionEffectType.JUMP_BOOST);

        if (tiers.getOrDefault(BeaconPlusEffect.CURE, 0) > 0) {
            for (PotionEffectType harmful : HARMFUL_EFFECTS) {
                player.removePotionEffect(harmful);
            }
        }
        if (tiers.getOrDefault(BeaconPlusEffect.FIRE_EXTINGUISHER, 0) > 0 && player.getFireTicks() > 0) {
            player.setFireTicks(0);
        }
        int expGainTier = tiers.getOrDefault(BeaconPlusEffect.EXP_GAIN, 0);
        if (expGainTier > 0 && gameTime % 100L < PULSE_INTERVAL_TICKS) {
            player.giveExp(expGainTier);
        }
        int repairTier = tiers.getOrDefault(BeaconPlusEffect.AUTO_REPAIR, 0);
        if (repairTier > 0 && gameTime % 100L < PULSE_INTERVAL_TICKS) {
            repairInventory(player.getInventory(), repairTier);
        }
        updateFlight(player, tiers.getOrDefault(BeaconPlusEffect.FLYING, 0) > 0);
    }

    private static void applyPlayerPotionIfPresent(
            Player player,
            Map<BeaconPlusEffect, Integer> tiers,
            BeaconPlusEffect effect,
            PotionEffectType type) {
        int tier = tiers.getOrDefault(effect, 0);
        if (tier <= 0) {
            return;
        }

        int amplifier = tier - 1;
        PotionEffect current = player.getPotionEffect(type);
        if (current != null) {
            // Never churn a stronger externally supplied effect. A matching Resonance Beacon effect is refreshed
            // only when about 15 seconds remain, giving players a stable 30-second countdown without
            // re-sending the same potion effect every one-second beacon pulse.
            if (current.getAmplifier() > amplifier
                    || (current.getAmplifier() == amplifier
                            && current.getDuration() > PLAYER_EFFECT_REFRESH_THRESHOLD_TICKS)) {
                return;
            }
        }

        player.addPotionEffect(new PotionEffect(type, PLAYER_EFFECT_DURATION_TICKS, amplifier, true, false, true));
    }

    private static void applyMonsterEffects(Monster monster, Map<BeaconPlusEffect, Integer> tiers) {
        applyPotionIfPresent(
                monster, tiers, BeaconPlusEffect.SLOWDOWN, PotionEffectType.SLOWNESS, MOB_EFFECT_DURATION_TICKS);
        applyPotionIfPresent(
                monster, tiers, BeaconPlusEffect.POISON, PotionEffectType.POISON, MOB_EFFECT_DURATION_TICKS);
        int burnerTier = tiers.getOrDefault(BeaconPlusEffect.BURNER, 0);
        if (burnerTier > 0 && isUndead(monster.getType())) {
            monster.setFireTicks(Math.max(monster.getFireTicks(), 40 + burnerTier * 40));
        }
        if (tiers.getOrDefault(BeaconPlusEffect.PEACEFUL, 0) > 0) {
            monster.setTarget(null);
        }
    }

    private static void applyPotionIfPresent(
            LivingEntity entity,
            Map<BeaconPlusEffect, Integer> tiers,
            BeaconPlusEffect effect,
            PotionEffectType type,
            int duration) {
        int tier = tiers.getOrDefault(effect, 0);
        if (tier > 0) {
            entity.addPotionEffect(new PotionEffect(type, duration, tier - 1, true, false, true));
        }
    }

    private static void pullEntity(Entity entity, Location center, int tier) {
        Location location = entity.getLocation();
        double deltaX = center.getX() - location.getX();
        double deltaZ = center.getZ() - location.getZ();
        double horizontalDistanceSquared = deltaX * deltaX + deltaZ * deltaZ;

        if (horizontalDistanceSquared < 0.25D) {
            return;
        }

        double inverseHorizontalDistance = 1.0D / Math.sqrt(horizontalDistanceSquared);
        double horizontalStrength = switch (Math.max(1, Math.min(3, tier))) {
            case 1 -> 0.45D;
            case 2 -> 0.65D;
            default -> 0.85D;
        };

        Vector velocity = entity.getVelocity();
        velocity.setX(deltaX * inverseHorizontalDistance * horizontalStrength);
        velocity.setZ(deltaZ * inverseHorizontalDistance * horizontalStrength);
        entity.setVelocity(velocity);
    }

    private static void applyTileEntityBoosts(
            List<Chunk> loadedChunks, int furnaceTier, int spawnerTier, long gameTime) {
        int inspected = 0;
        for (Chunk chunk : loadedChunks) {
            for (TileEntityRef ref : getCachedTileEntities(chunk, gameTime)) {
                if (++inspected > MAX_TILE_ENTITIES_PER_PULSE) {
                    return;
                }

                if (ref.kind() == TileEntityKind.FURNACE && furnaceTier > 0) {
                    BlockState state = chunk.getWorld().getBlockAt(ref.x(), ref.y(), ref.z()).getState(false);
                    if (state instanceof Furnace furnace) {
                        boostFurnace(furnace, furnaceTier);
                    }
                } else if (ref.kind() == TileEntityKind.SPAWNER && spawnerTier > 0) {
                    BlockState state = chunk.getWorld().getBlockAt(ref.x(), ref.y(), ref.z()).getState(false);
                    if (state instanceof CreatureSpawner spawner) {
                        boostSpawner(spawner, spawnerTier);
                    }
                }
            }
        }
    }

    private static List<TileEntityRef> getCachedTileEntities(Chunk chunk, long gameTime) {
        TileEntityChunkKey key = new TileEntityChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        CachedTileEntities cached = TILE_ENTITY_CACHE.get(key);
        if (cached != null
                && gameTime >= cached.scannedGameTime()
                && gameTime - cached.scannedGameTime() < TILE_ENTITY_DISCOVERY_CACHE_TICKS) {
            return cached.entries();
        }

        List<TileEntityRef> entries = new ArrayList<>();
        for (BlockState state : chunk.getTileEntities(false)) {
            TileEntityKind kind = state instanceof Furnace
                    ? TileEntityKind.FURNACE
                    : state instanceof CreatureSpawner ? TileEntityKind.SPAWNER : TileEntityKind.OTHER;
            entries.add(new TileEntityRef(state.getX(), state.getY(), state.getZ(), kind));
        }

        if (TILE_ENTITY_CACHE.size() >= MAX_TILE_ENTITY_CACHE_CHUNKS) {
            TILE_ENTITY_CACHE.clear();
        }

        List<TileEntityRef> immutableEntries = List.copyOf(entries);
        TILE_ENTITY_CACHE.put(key, new CachedTileEntities(gameTime, immutableEntries));
        return immutableEntries;
    }

    private static void boostFurnace(Furnace furnace, int tier) {
        if (furnace.getBurnTime() <= 0 || furnace.getCookTimeTotal() <= 0) {
            return;
        }
        int next = Math.min(furnace.getCookTimeTotal() - 1, furnace.getCookTime() + tier * 4);
        if (next > furnace.getCookTime()) {
            furnace.setCookTime((short) next);
            furnace.update(true, false);
        }
    }

    private static void boostSpawner(CreatureSpawner spawner, int tier) {
        int next = Math.max(20, spawner.getDelay() - (10 + tier * 10));
        if (next < spawner.getDelay()) {
            spawner.setDelay(next);
            spawner.update(true, false);
        }
    }

    private static void applyCropBoost(Block beaconBlock, List<Chunk> chunks, int tier) {
        if (chunks.isEmpty()) {
            return;
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        World world = beaconBlock.getWorld();
        int perChunk = CROP_SAMPLES_PER_CHUNK + (tier - 1) * 4;
        int samples = Math.min(MAX_CROP_SAMPLES_PER_PULSE, chunks.size() * perChunk);
        int minY = Math.max(world.getMinHeight(), beaconBlock.getY() - CROP_VERTICAL_RADIUS);
        int maxYExclusive = Math.min(world.getMaxHeight(), beaconBlock.getY() + CROP_VERTICAL_RADIUS + 1);
        if (minY >= maxYExclusive) {
            return;
        }

        for (int i = 0; i < samples; i++) {
            Chunk chunk = chunks.get(random.nextInt(chunks.size()));
            int x = (chunk.getX() << 4) + random.nextInt(16);
            int z = (chunk.getZ() << 4) + random.nextInt(16);
            int y = random.nextInt(minY, maxYExclusive);
            Block target = world.getBlockAt(x, y, z);
            if (target.getBlockData() instanceof Ageable ageable && ageable.getAge() < ageable.getMaximumAge()) {
                ageable.setAge(Math.min(ageable.getMaximumAge(), ageable.getAge() + tier));
                target.setBlockData(ageable, false);
            }
        }
    }

    private static List<Chunk> getLoadedChunksInField(Block beaconBlock, double range) {
        List<Chunk> chunks = new ArrayList<>();
        if (Slimefun.getSchedulerService().isFolia()) {
            chunks.add(beaconBlock.getChunk());
            return chunks;
        }
        BeaconPlusField.ChunkFootprint footprint =
                BeaconPlusField.footprint(beaconBlock.getX(), beaconBlock.getZ(), range);
        World world = beaconBlock.getWorld();
        for (int x = footprint.minChunkX(); x <= footprint.maxChunkX(); x++) {
            for (int z = footprint.minChunkZ(); z <= footprint.maxChunkZ(); z++) {
                if (world.isChunkLoaded(x, z)) {
                    chunks.add(world.getChunkAt(x, z));
                }
            }
        }
        return chunks;
    }

    static void repairInventory(PlayerInventory inventory, int amount) {
        if (amount <= 0) {
            return;
        }

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }

            ItemMeta meta = stack.getItemMeta();
            if (!(meta instanceof Damageable damageable) || damageable.getDamage() <= 0) {
                continue;
            }

            int maxDamage = damageable.hasMaxDamage()
                    ? damageable.getMaxDamage()
                    : stack.getType().getMaxDurability();
            if (maxDamage <= 0) {
                continue;
            }

            int repairedDamage = Math.max(0, Math.min(maxDamage, damageable.getDamage() - amount));
            damageable.setDamage(repairedDamage);
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

    private enum TileEntityKind {
        OTHER,
        FURNACE,
        SPAWNER
    }

    private record TileEntityRef(int x, int y, int z, TileEntityKind kind) {}

    private record TileEntityChunkKey(UUID worldId, int x, int z) {}

    private record CachedTileEntities(long scannedGameTime, List<TileEntityRef> entries) {}
}
