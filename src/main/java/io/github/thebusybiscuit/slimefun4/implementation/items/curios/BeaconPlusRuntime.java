package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.ASlimefunDataContainer;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Beacon;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;

/** Coordinates Resonance Beacon tier resolution and delegates bounded world effects. */
final class BeaconPlusRuntime {

    static final String EFFECTS_KEY = "beacon_plus_effects";
    private static final int PULSE_INTERVAL_TICKS = 20 * 15;
    // One tier must always add exactly one chunk ring to the chunk-aligned field.
    private static final int EXTRA_RANGE_PER_TIER = 16;
    private static final double PLAYER_STATE_RECONCILE_RANGE = 128.0D;
    private static final long OBSERVED_BEACON_TTL_MILLIS = 45_000L;
    private static final Map<BeaconKey, Long> OBSERVED_BEACONS = new ConcurrentHashMap<>();
    private static final Map<BeaconKey, Long> LAST_PULSE_GAME_TICKS = new ConcurrentHashMap<>();

    private BeaconPlusRuntime() {}

    static void observe(Block block) {
        OBSERVED_BEACONS.put(BeaconKey.from(block.getLocation()), System.currentTimeMillis());
    }

    static void forget(Location location) {
        BeaconKey key = BeaconKey.from(location);
        OBSERVED_BEACONS.remove(key);
        LAST_PULSE_GAME_TICKS.remove(key);
        BeaconPlusBeam.markUnpowered(location);
    }

    static EnumSet<BeaconPlusEffect> getConfiguredEffects(Location location) {
        EnumSet<BeaconPlusEffect> effects = BeaconPlusEffect.parse(StorageCacheUtils.getData(location, EFFECTS_KEY));
        BeaconPlusManager manager = BeaconPlusManager.getInstance();
        // Preserve pre-progression/legacy Activator selections whose chunk mode was stored separately.
        if (manager != null && manager.getChunkMode(location) != BeaconPlusChunkMode.OFF) {
            effects.add(BeaconPlusEffect.ACTIVATOR);
        }
        return effects;
    }

    static void setConfiguredEffects(Location location, Set<BeaconPlusEffect> effects) {
        EnumSet<BeaconPlusEffect> stored =
                effects.isEmpty() ? EnumSet.noneOf(BeaconPlusEffect.class) : EnumSet.copyOf(effects);
        StorageCacheUtils.setData(location, EFFECTS_KEY, BeaconPlusEffect.serialize(stored));
        World world = location.getWorld();
        if (world != null && world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            BeaconPlusLegacyDataStore.sync(location.getBlock());
        }
    }

    static boolean hasEffect(Location target, BeaconPlusEffect effect) {
        return getTierForEffect(target, effect) > 0;
    }

    static int getTierForEffect(Location target, BeaconPlusEffect effect) {
        if (!BeaconPlusConfig.isEnabled() || target.getWorld() == null || !BeaconPlusConfig.isPowerEnabled(effect)) {
            return 0;
        }

        purgeStaleObservedBeacons();
        int bestTier = 0;
        long now = System.currentTimeMillis();
        for (Map.Entry<BeaconKey, Long> entry : OBSERVED_BEACONS.entrySet()) {
            if (now - entry.getValue() > OBSERVED_BEACON_TTL_MILLIS) {
                continue;
            }

            BeaconKey key = entry.getKey();
            World world = target.getWorld();
            if (!key.worldId().equals(world.getUID())) {
                continue;
            }
            if (Slimefun.getSchedulerService().isFolia()
                    && (key.x() >> 4 != target.getBlockX() >> 4 || key.z() >> 4 != target.getBlockZ() >> 4)) {
                continue;
            }
            if (!world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) {
                continue;
            }

            Block block = world.getBlockAt(key.x(), key.y(), key.z());
            EnumMap<BeaconPlusEffect, Integer> tiers = getActiveTiers(block);
            int tier = tiers.getOrDefault(effect, 0);
            if (tier <= 0) {
                continue;
            }

            double range = getRange(block, tiers);
            if (range <= 0.0D
                    || !BeaconPlusField.contains(
                            block.getX(), block.getZ(), range, target.getBlockX(), target.getBlockZ())) {
                continue;
            }

            bestTier = Math.max(bestTier, tier);
            if (bestTier >= BeaconPlusConfig.getMaxTier()) {
                break;
            }
        }
        return bestTier;
    }

    /** Legacy helper: -1 inactive, then 0/1/2 for Tier I/II/III. */
    static int getPowerForEffect(Location target, BeaconPlusEffect effect) {
        int tier = getTierForEffect(target, effect);
        return tier <= 0 ? -1 : tier - 1;
    }

    static int getEffectiveTierAtBeacon(Block block, BeaconPlusEffect effect) {
        return getActiveTiers(block).getOrDefault(effect, 0);
    }

    static double getEffectiveRange(Block block) {
        return getRange(block, getActiveTiers(block));
    }

    static int getPotentialTierAtBeacon(Block block, BeaconPlusEffect effect) {
        if (!BeaconPlusConfig.isEnabled() || !BeaconPlusConfig.isPowerEnabled(effect)) {
            return 0;
        }
        if (getOwner(block.getLocation()) == null) {
            return 0;
        }

        int naturalTier = BeaconPlusPyramid.inspect(block).naturalPowerTier();
        if (naturalTier <= 0) {
            return 0;
        }

        int unlocked = getUnlockedTierAtBeacon(block, effect);
        int requested = getRequestedTierAtBeacon(block, effect, unlocked);
        int baseTier = Math.min(Math.min(unlocked, requested), naturalTier);
        if (baseTier <= 0 || !supportsExtraPower(effect)) {
            return baseTier;
        }

        EnumSet<BeaconPlusEffect> configured = getConfiguredEffects(block.getLocation());
        int extraUnlocked = getUnlockedTierAtBeacon(block, BeaconPlusEffect.EXTRA_POWER);
        int extraRequested = getRequestedTierAtBeacon(block, BeaconPlusEffect.EXTRA_POWER, extraUnlocked);
        int extraTier = configured.contains(BeaconPlusEffect.EXTRA_POWER)
                ? Math.min(Math.min(extraUnlocked, extraRequested), naturalTier)
                : 0;
        return Math.min(BeaconPlusConfig.getMaxTier(), baseTier + extraTier);
    }

    static int getUnlockedTierAtBeacon(Block block, BeaconPlusEffect effect) {
        if (!effect.isConfigurable()) {
            return 0;
        }
        Location location = block.getLocation();
        int imported = BeaconPlusLegacyDataStore.getImportedUnlockedTier(location, effect);
        if (imported > 0) {
            return Math.min(imported, BeaconPlusConfig.getMaxTier());
        }
        UUID owner = getOwner(location);
        if (owner == null) {
            return 0;
        }
        int unlocked = BeaconPlusProgression.getUnlockedTier(owner, effect);
        return unlocked > 0 ? unlocked : recoverConfiguredTier(location, owner, effect);
    }

    static int getSelectedTierAtBeacon(Block block, BeaconPlusEffect effect) {
        return getRequestedTierAtBeacon(block, effect, getUnlockedTierAtBeacon(block, effect));
    }

    private static int getRequestedTierAtBeacon(Block block, BeaconPlusEffect effect, int unlocked) {
        if (unlocked <= 0) {
            return 0;
        }
        int imported = BeaconPlusLegacyDataStore.getImportedSelectedTier(block.getLocation(), effect);
        return imported > 0 ? Math.min(imported, unlocked) : unlocked;
    }

    static void tick(Block block, ASlimefunDataContainer data) {
        long gameTime = block.getWorld().getGameTime();
        if (!shouldPulse(block.getLocation(), gameTime)) {
            return;
        }

        if (!BeaconPlusConfig.isEnabled()) {
            BeaconPlusBeam.markUnpowered(block.getLocation());
            BeaconPlusRuntimeEffects.refreshNearbyPlayerStates(block, PLAYER_STATE_RECONCILE_RANGE);
            return;
        }

        // Refresh the observed-beacon cache only on the bounded maintenance pulse instead of every Slimefun tick.
        observe(block);

        EnumMap<BeaconPlusEffect, Integer> tiers = getPotentialActiveTiers(block);
        if (!BeaconPlusEnergy.consumePulse(block, data, tiers)) {
            BeaconPlusBeam.markUnpowered(block.getLocation());
            reconcileActivator(block, 0);
            BeaconPlusRuntimeEffects.refreshNearbyPlayerStates(block, PLAYER_STATE_RECONCILE_RANGE);
            return;
        }
        reconcileActivator(block, tiers.getOrDefault(BeaconPlusEffect.ACTIVATOR, 0));
        double range = getRange(block, tiers);
        if (range <= 0.0D) {
            BeaconPlusBeam.markUnpowered(block.getLocation());
            BeaconPlusRuntimeEffects.refreshNearbyPlayerStates(block, PLAYER_STATE_RECONCILE_RANGE);
            return;
        }

        BeaconPlusBeam.markPowered(block);
        BeaconPlusRuntimeEffects.applyPulse(block, tiers, range, gameTime);
    }

    static void refreshPlayerState(Player player) {
        BeaconPlusRuntimeEffects.refreshPlayerState(player);
    }

    static void clearPlayerState(Player player) {
        BeaconPlusRuntimeEffects.clearPlayerState(player);
    }

    static void shutdown() {
        BeaconPlusRuntimeEffects.shutdown();
        BeaconPlusEnergy.shutdown();
        OBSERVED_BEACONS.clear();
        LAST_PULSE_GAME_TICKS.clear();
    }

    private static boolean shouldPulse(Location location, long gameTime) {
        BeaconKey key = BeaconKey.from(location);
        Long previous = LAST_PULSE_GAME_TICKS.get(key);
        if (previous != null && gameTime >= previous && gameTime - previous < PULSE_INTERVAL_TICKS) {
            return false;
        }
        LAST_PULSE_GAME_TICKS.put(key, gameTime);
        return true;
    }

    static boolean isOperational(Block block, Map<BeaconPlusEffect, Integer> tiers) {
        return tiers.isEmpty() || BeaconPlusEnergy.hasOperationalPower(block, tiers);
    }

    private static EnumMap<BeaconPlusEffect, Integer> getActiveTiers(Block block) {
        EnumMap<BeaconPlusEffect, Integer> tiers = getPotentialActiveTiers(block);
        if (isOperational(block, tiers)) {
            return tiers;
        }
        return new EnumMap<>(BeaconPlusEffect.class);
    }

    static EnumMap<BeaconPlusEffect, Integer> getPotentialActiveTiers(Block block) {
        EnumMap<BeaconPlusEffect, Integer> tiers = new EnumMap<>(BeaconPlusEffect.class);
        if (!BeaconPlusConfig.isEnabled() || getOwner(block.getLocation()) == null) {
            return tiers;
        }

        int naturalTier = BeaconPlusPyramid.inspect(block).naturalPowerTier();
        if (naturalTier <= 0) {
            return tiers;
        }

        EnumSet<BeaconPlusEffect> configured = getConfiguredEffects(block.getLocation());
        int extraPowerTier = 0;
        if (configured.contains(BeaconPlusEffect.EXTRA_POWER)
                && BeaconPlusConfig.isPowerEnabled(BeaconPlusEffect.EXTRA_POWER)) {
            int unlocked = getUnlockedTierAtBeacon(block, BeaconPlusEffect.EXTRA_POWER);
            int requested = getRequestedTierAtBeacon(block, BeaconPlusEffect.EXTRA_POWER, unlocked);
            extraPowerTier = Math.min(Math.min(unlocked, requested), naturalTier);
            if (extraPowerTier > 0) {
                tiers.put(BeaconPlusEffect.EXTRA_POWER, extraPowerTier);
            }
        }

        for (BeaconPlusEffect effect : configured) {
            if (!effect.isConfigurable()
                    || effect == BeaconPlusEffect.EXTRA_POWER
                    || !BeaconPlusConfig.isPowerEnabled(effect)) {
                continue;
            }
            int unlocked = getUnlockedTierAtBeacon(block, effect);
            int requested = getRequestedTierAtBeacon(block, effect, unlocked);
            int tier = Math.min(Math.min(unlocked, requested), naturalTier);
            if (tier <= 0) {
                continue;
            }
            if (supportsExtraPower(effect)) {
                tier = Math.min(BeaconPlusConfig.getMaxTier(), tier + extraPowerTier);
            }
            tiers.put(effect, tier);
        }
        return tiers;
    }

    private static int recoverConfiguredTier(Location location, UUID owner, BeaconPlusEffect effect) {
        if (BeaconPlusLegacyDataStore.isLegacyImported(location) || !getConfiguredEffects(location).contains(effect)) {
            return 0;
        }

        int minimumTier = 1;
        if (effect == BeaconPlusEffect.ACTIVATOR) {
            BeaconPlusManager manager = BeaconPlusManager.getInstance();
            BeaconPlusChunkMode mode = manager == null
                    ? BeaconPlusChunkMode.fromStored(StorageCacheUtils.getData(location, BeaconPlusManager.CHUNK_MODE_KEY))
                    : manager.getChunkMode(location);
            minimumTier = switch (mode) {
                case SINGLE -> 1;
                case AREA_3X3 -> 2;
                case AREA_5X5 -> 3;
                case OFF -> 0;
            };
        }

        return minimumTier <= 0 ? 0 : BeaconPlusProgression.ensureMinimumTier(owner, effect, minimumTier);
    }

    private static boolean supportsExtraPower(BeaconPlusEffect effect) {
        return effect != BeaconPlusEffect.EXTRA_POWER
                && effect != BeaconPlusEffect.EXTRA_RANGE
                && effect != BeaconPlusEffect.ACTIVATOR;
    }

    private static UUID getOwner(Location location) {
        BeaconPlusManager manager = BeaconPlusManager.getInstance();
        if (manager != null) {
            UUID owner = manager.getOwner(location);
            if (owner != null) {
                return owner;
            }
        }
        String stored = StorageCacheUtils.getData(location, BeaconPlusManager.OWNER_KEY);
        if (stored != null && !stored.isBlank()) {
            try {
                return UUID.fromString(stored);
            } catch (IllegalArgumentException ignored) {
                // Older imported records are recovered below using their fixed compatibility owner.
            }
        }
        return BeaconPlusLegacyDataStore.isLegacyImported(location)
                ? BeaconPlusLegacyDataStore.LEGACY_IMPORTED_OWNER
                : null;
    }

    private static double getRange(Block block, Map<BeaconPlusEffect, Integer> tiers) {
        BlockState state = block.getState();
        if (!(state instanceof Beacon beacon) || beacon.getTier() <= 0) {
            return 0.0D;
        }
        double importedOverride = BeaconPlusLegacyDataStore.getImportedOverriddenRange(block.getLocation());
        double range = importedOverride > 0.0D ? importedOverride : Math.max(0.0D, beacon.getEffectRange());
        int extraRangeTier = tiers.getOrDefault(BeaconPlusEffect.EXTRA_RANGE, 0);
        return extraRangeTier > 0 ? range + EXTRA_RANGE_PER_TIER * extraRangeTier : range;
    }

    static boolean reconcileActivator(Block block, int tier) {
        BeaconPlusManager manager = BeaconPlusManager.getInstance();
        if (manager == null) {
            return false;
        }
        BeaconPlusChunkMode desired =
                switch (tier) {
                    case 1 -> BeaconPlusChunkMode.SINGLE;
                    case 2 -> BeaconPlusChunkMode.AREA_3X3;
                    case 3 -> BeaconPlusChunkMode.AREA_5X5;
                    default -> BeaconPlusChunkMode.OFF;
                };
        if (manager.getChunkMode(block.getLocation()) == desired) {
            return true;
        }
        UUID owner = getOwner(block.getLocation());
        return owner != null
                && manager.updateModes(
                        block.getLocation(), owner, desired, manager.getSupportMode(block.getLocation()));
    }

    static boolean reconcileActivator(Block block) {
        return reconcileActivator(block, getActiveTiers(block).getOrDefault(BeaconPlusEffect.ACTIVATOR, 0));
    }

    private static void purgeStaleObservedBeacons() {
        long cutoff = System.currentTimeMillis() - OBSERVED_BEACON_TTL_MILLIS;
        OBSERVED_BEACONS.entrySet().removeIf(entry -> {
            if (entry.getValue() >= cutoff) {
                return false;
            }
            LAST_PULSE_GAME_TICKS.remove(entry.getKey());
            return true;
        });
    }

    private record BeaconKey(UUID worldId, int x, int y, int z) {
        private static BeaconKey from(Location location) {
            return new BeaconKey(
                    location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }
    }
}
