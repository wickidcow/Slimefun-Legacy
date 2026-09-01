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
    private static final int PLAYER_POTION_INTERVAL_TICKS = 200;
    private static final int PLAYER_UTILITY_INTERVAL_TICKS = 40;
    private static final int PLAYER_PASSIVE_INTERVAL_TICKS = 100;
    private static final int MONSTER_INTERVAL_TICKS = 40;
    private static final int TILE_BOOST_INTERVAL_TICKS = 40;
    private static final int CROP_INTERVAL_TICKS = 40;
    private static final int PLAYER_EFFECT_DURATION_TICKS = 600;
    private static final int PLAYER_EFFECT_REFRESH_THRESHOLD_TICKS = 200;
    private static final int MOB_EFFECT_DURATION_TICKS = 70;
    private static final int GRAVITY_SLICES = 4;
    private static final long GRAVITY_SLICE_DELAY_TICKS = 5L;
    private static final long TILE_ENTITY_DISCOVERY_CACHE_TICKS = 300L;
    private static final int MAX_TILE_ENTITY_CACHE_CHUNKS = 4096;
    private static final NamespacedKey SCALE_KEY = new NamespacedKey(Slimefun.instance(), "beacon_plus_scale");
    private static final Map<UUID, Boolean> ORIGINAL_ALLOW_FLIGHT = new ConcurrentHashMap<>();
    private static final Map<TileEntityChunkKey, CachedTileEntities> TILE_ENTITY_CACHE = new ConcurrentHashMap<>();
    private static final Set<BeaconPlusEffect> PLAYER_POTION_EFFECTS = Set.of(
            BeaconPlusEffect.STRENGTH,
            BeaconPlusEffect.REGENERATION,
            BeaconPlusEffect.RESISTANCE,
            BeaconPlusEffect.FAST_DIGGING,
            BeaconPlusEffect.SPEED,
            BeaconPlusEffect.NIGHT_VISION,
            BeaconPlusEffect.LUCK,
            BeaconPlusEffect.WATER_BREATHING,
            BeaconPlusEffect.JUMP);
    private static final Set<BeaconPlusEffect> PLAYER_UTILITY_EFFECTS =
            Set.of(BeaconPlusEffect.CURE, BeaconPlusEffect.FIRE_EXTINGUISHER);
    private static final Set<BeaconPlusEffect> PLAYER_PASSIVE_EFFECTS =
            Set.of(BeaconPlusEffect.EXP_GAIN, BeaconPlusEffect.AUTO_REPAIR);
    private static final Set<BeaconPlusEffect> MONSTER_PERIODIC_EFFECTS =
            Set.of(BeaconPlusEffect.SLOWDOWN, BeaconPlusEffect.BURNER, BeaconPlusEffect.POISON);
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

    static void applyPulse(
            Block block, Map<BeaconPlusEffect, Integer> tiers, double range, long gameTime, boolean newlyPowered) {
        int gravityTier = tiers.getOrDefault(BeaconPlusEffect.GRAVITY_WELL, 0);
        int furnaceTier = tiers.getOrDefault(BeaconPlusEffect.FURNACE_BOOSTER, 0);
        int spawnerTier = tiers.getOrDefault(BeaconPlusEffect.SPAWNERS, 0);
        int cropTier = tiers.getOrDefault(BeaconPlusEffect.CROPS, 0);
        boolean folia = Slimefun.getSchedulerService().isFolia();

        boolean potionPulse = hasAnyTier(tiers, PLAYER_POTION_EFFECTS)
                && (newlyPowered || isLaneDue(block, gameTime, PLAYER_POTION_INTERVAL_TICKS, 11));
        boolean utilityPulse = hasAnyTier(tiers, PLAYER_UTILITY_EFFECTS)
                && (newlyPowered || isLaneDue(block, gameTime, PLAYER_UTILITY_INTERVAL_TICKS, 23));
        boolean passivePulse = hasAnyTier(tiers, PLAYER_PASSIVE_EFFECTS)
                && (newlyPowered || isLaneDue(block, gameTime, PLAYER_PASSIVE_INTERVAL_TICKS, 37));
        boolean flightPulse = newlyPowered && tiers.getOrDefault(BeaconPlusEffect.FLYING, 0) > 0;
        boolean monsterPulse = hasAnyTier(tiers, MONSTER_PERIODIC_EFFECTS)
                && (newlyPowered || isLaneDue(block, gameTime, MONSTER_INTERVAL_TICKS, 41));
        boolean peacefulActivation = newlyPowered && tiers.getOrDefault(BeaconPlusEffect.PEACEFUL, 0) > 0;
        boolean tilePulse = (furnaceTier > 0 || spawnerTier > 0)
                && (newlyPowered || isLaneDue(block, gameTime, TILE_BOOST_INTERVAL_TICKS, 53));
        boolean cropPulse = cropTier > 0 && (newlyPowered || isLaneDue(block, gameTime, CROP_INTERVAL_TICKS, 67));

        if ((potionPulse || utilityPulse || passivePulse || flightPulse) && !folia) {
            long started = System.nanoTime();
            applyPlayerLanes(block, tiers, range, potionPulse, utilityPulse, passivePulse, flightPulse);
            BeaconPlusPerformance.record(BeaconPlusPerformance.Section.PLAYERS, System.nanoTime() - started);
        }

        boolean needsLoadedChunks = gravityTier > 0
                || monsterPulse
                || peacefulActivation
                || tilePulse
                || cropPulse
                || (folia && (potionPulse || utilityPulse || passivePulse || flightPulse));
        if (!needsLoadedChunks) {
            return;
        }

        List<Chunk> loadedChunks = getLoadedChunksInField(block, range);
        if (loadedChunks.isEmpty()) {
            return;
        }

        if (folia && (potionPulse || utilityPulse || passivePulse || flightPulse)) {
            long started = System.nanoTime();
            for (Entity entity : loadedChunks.get(0).getEntities()) {
                if (entity instanceof Player player) {
                    applyPlayerLanes(
                            player, tiers, potionPulse, utilityPulse, passivePulse, flightPulse);
                }
            }
            BeaconPlusPerformance.record(BeaconPlusPerformance.Section.PLAYERS, System.nanoTime() - started);
        }

        if (monsterPulse || peacefulActivation) {
            long started = System.nanoTime();
            for (Chunk chunk : loadedChunks) {
                for (Entity entity : chunk.getEntities()) {
                    if (!(entity instanceof Monster monster)) {
                        continue;
                    }
                    if (monsterPulse) {
                        applyMonsterEffects(monster, tiers);
                    }
                    if (peacefulActivation) {
                        monster.setTarget(null);
                    }
                }
            }
            BeaconPlusPerformance.record(BeaconPlusPerformance.Section.MONSTERS, System.nanoTime() - started);
        }

        if (gravityTier > 0) {
            scheduleGravityWell(block, loadedChunks, gravityTier);
        }

        if (tilePulse) {
            long started = System.nanoTime();
            applyTileEntityBoosts(loadedChunks, furnaceTier, spawnerTier, gameTime);
            BeaconPlusPerformance.record(BeaconPlusPerformance.Section.TILES, System.nanoTime() - started);
        }

        if (cropPulse) {
            long started = System.nanoTime();
            applyCropBoost(block, loadedChunks, cropTier);
            BeaconPlusPerformance.record(BeaconPlusPerformance.Section.CROPS, System.nanoTime() - started);
        }
    }

    private static boolean hasAnyTier(Map<BeaconPlusEffect, Integer> tiers, Set<BeaconPlusEffect> effects) {
        for (BeaconPlusEffect effect : effects) {
            if (tiers.getOrDefault(effect, 0) > 0) {
                return true;
            }
        }
        return false;
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
        if (Slimefun.getSchedulerService().isFolia()) {
            for (Entity entity : block.getChunk().getEntities()) {
                if (entity instanceof Player player) {
                    refreshPlayerState(player);
                }
            }
            return;
        }

        BeaconPlusField.ChunkFootprint footprint = BeaconPlusField.footprint(block.getX(), block.getZ(), range);
        for (Player player : block.getWorld().getPlayers()) {
            Location location = player.getLocation();
            if (footprint.containsChunk(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                refreshPlayerState(player);
            }
        }
    }

    static void reconcilePeacefulTargets(Block block, double range) {
        if (range <= 0.0D) {
            return;
        }
        long started = System.nanoTime();
        for (Chunk chunk : getLoadedChunksInField(block, range)) {
            for (Entity entity : chunk.getEntities()) {
                if (entity instanceof Monster monster) {
                    monster.setTarget(null);
                }
            }
        }
        BeaconPlusPerformance.record(BeaconPlusPerformance.Section.MONSTERS, System.nanoTime() - started);
    }

    private static void applyPlayerLanes(
            Block block,
            Map<BeaconPlusEffect, Integer> tiers,
            double range,
            boolean potionPulse,
            boolean utilityPulse,
            boolean passivePulse,
            boolean flightPulse) {
        BeaconPlusField.ChunkFootprint footprint = BeaconPlusField.footprint(block.getX(), block.getZ(), range);
        for (Player player : block.getWorld().getPlayers()) {
            Location playerLocation = player.getLocation();
            if (!footprint.containsChunk(playerLocation.getBlockX() >> 4, playerLocation.getBlockZ() >> 4)) {
                continue;
            }
            applyPlayerLanes(player, tiers, potionPulse, utilityPulse, passivePulse, flightPulse);
        }
    }

    private static void applyPlayerLanes(
            Player player,
            Map<BeaconPlusEffect, Integer> tiers,
            boolean potionPulse,
            boolean utilityPulse,
            boolean passivePulse,
            boolean flightPulse) {
        if (potionPulse) {
            applyPlayerPotions(player, tiers);
        }
        if (utilityPulse) {
            applyPlayerUtility(player, tiers);
        }
        if (passivePulse) {
            applyPlayerPassive(player, tiers);
        }
        if (flightPulse) {
            updateFlight(player, tiers.getOrDefault(BeaconPlusEffect.FLYING, 0) > 0);
        }
    }

    private static void applyPlayerPotions(Player player, Map<BeaconPlusEffect, Integer> tiers) {
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
    }

    private static void applyPlayerUtility(Player player, Map<BeaconPlusEffect, Integer> tiers) {
        if (tiers.getOrDefault(BeaconPlusEffect.CURE, 0) > 0) {
            for (PotionEffectType harmful : HARMFUL_EFFECTS) {
                player.removePotionEffect(harmful);
            }
        }
        if (tiers.getOrDefault(BeaconPlusEffect.FIRE_EXTINGUISHER, 0) > 0 && player.getFireTicks() > 0) {
            player.setFireTicks(0);
        }
    }

    private static void applyPlayerPassive(Player player, Map<BeaconPlusEffect, Integer> tiers) {
        int expGainTier = tiers.getOrDefault(BeaconPlusEffect.EXP_GAIN, 0);
        if (expGainTier > 0) {
            player.giveExp(expGainTier);
        }
        int repairTier = tiers.getOrDefault(BeaconPlusEffect.AUTO_REPAIR, 0);
        if (repairTier > 0) {
            repairInventory(player.getInventory(), repairTier);
        }
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
            // Potions last 30 seconds but are checked only on a staggered 10-second lane. Refreshing at about
            // 10 seconds remaining preserves a visible countdown while eliminating nine redundant checks out of ten.
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

    private static void scheduleGravityWell(Block beaconBlock, List<Chunk> loadedChunks, int tier) {
        if (loadedChunks.isEmpty()) {
            return;
        }

        if (Slimefun.getSchedulerService().isFolia() || loadedChunks.size() == 1) {
            long started = System.nanoTime();
            processGravityChunks(
                    beaconBlock.getWorld(),
                    beaconBlock.getX(),
                    beaconBlock.getY(),
                    beaconBlock.getZ(),
                    List.of(new ChunkCoordinate(loadedChunks.get(0).getX(), loadedChunks.get(0).getZ())),
                    tier);
            BeaconPlusPerformance.record(BeaconPlusPerformance.Section.GRAVITY, System.nanoTime() - started);
            return;
        }

        int slices = Math.min(GRAVITY_SLICES, loadedChunks.size());
        List<List<ChunkCoordinate>> work = new ArrayList<>(slices);
        for (int i = 0; i < slices; i++) {
            work.add(new ArrayList<>());
        }
        for (int index = 0; index < loadedChunks.size(); index++) {
            Chunk chunk = loadedChunks.get(index);
            work.get(index % slices).add(new ChunkCoordinate(chunk.getX(), chunk.getZ()));
        }

        World world = beaconBlock.getWorld();
        int beaconX = beaconBlock.getX();
        int beaconY = beaconBlock.getY();
        int beaconZ = beaconBlock.getZ();
        for (int slice = 0; slice < slices; slice++) {
            List<ChunkCoordinate> coordinates = List.copyOf(work.get(slice));
            long delay = slice * GRAVITY_SLICE_DELAY_TICKS;
            if (delay == 0L) {
                long started = System.nanoTime();
                processGravityChunks(world, beaconX, beaconY, beaconZ, coordinates, tier);
                BeaconPlusPerformance.record(BeaconPlusPerformance.Section.GRAVITY, System.nanoTime() - started);
                continue;
            }

            Slimefun.getSchedulerService().runLater(() -> {
                if (!world.isChunkLoaded(beaconX >> 4, beaconZ >> 4)) {
                    return;
                }
                Block currentBeacon = world.getBlockAt(beaconX, beaconY, beaconZ);
                int currentTier = BeaconPlusRuntime.getEffectiveTierAtBeacon(currentBeacon, BeaconPlusEffect.GRAVITY_WELL);
                if (currentTier <= 0) {
                    return;
                }
                long started = System.nanoTime();
                processGravityChunks(world, beaconX, beaconY, beaconZ, coordinates, currentTier);
                BeaconPlusPerformance.record(BeaconPlusPerformance.Section.GRAVITY, System.nanoTime() - started);
            }, delay);
        }
    }

    private static void processGravityChunks(
            World world,
            int beaconX,
            int beaconY,
            int beaconZ,
            List<ChunkCoordinate> coordinates,
            int tier) {
        Location center = new Location(world, beaconX + 0.5D, beaconY + 0.5D, beaconZ + 0.5D);
        for (ChunkCoordinate coordinate : coordinates) {
            if (!world.isChunkLoaded(coordinate.x(), coordinate.z())) {
                continue;
            }
            Chunk chunk = world.getChunkAt(coordinate.x(), coordinate.z());
            for (Entity entity : chunk.getEntities()) {
                if (entity instanceof Mob || entity instanceof Item) {
                    pullEntity(entity, center, tier);
                }
            }
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
        int accumulatedBoost = tier * 4 * (TILE_BOOST_INTERVAL_TICKS / PULSE_INTERVAL_TICKS);
        int next = Math.min(furnace.getCookTimeTotal() - 1, furnace.getCookTime() + accumulatedBoost);
        if (next > furnace.getCookTime()) {
            furnace.setCookTime((short) next);
            furnace.update(true, false);
        }
    }

    private static void boostSpawner(CreatureSpawner spawner, int tier) {
        int accumulatedBoost = (10 + tier * 10) * (TILE_BOOST_INTERVAL_TICKS / PULSE_INTERVAL_TICKS);
        int next = Math.max(20, spawner.getDelay() - accumulatedBoost);
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

    private static boolean isLaneDue(Block block, long gameTime, int intervalTicks, int salt) {
        long locationHash = block.getWorld().getUID().getLeastSignificantBits()
                ^ ((long) block.getX() * 73428767L)
                ^ ((long) block.getY() * 912931L)
                ^ ((long) block.getZ() * 438289L)
                ^ salt;
        long phase = Math.floorMod(locationHash, intervalTicks);
        return Math.floorMod(gameTime - phase, intervalTicks) < PULSE_INTERVAL_TICKS;
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

    private record ChunkCoordinate(int x, int z) {}
}
