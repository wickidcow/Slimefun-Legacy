package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;

/**
 * Compatibility bridge for BeaconPlus WORLD storage.
 *
 * <p>The legacy plugin stores beacon state at {@code <world>/BeaconData/<chunkX>.<chunkZ>.json}. This class keeps that
 * exact directory, filename and top-level {@code {"Beacons": [...]}} structure so existing data folders can be used
 * directly. Native Slimefun-only metadata such as ownership is stored in Slimefun block data and is never injected
 * into the compatibility JSON.
 */
final class BeaconPlusLegacyDataStore implements Listener {

    static final String IMPORTED_KEY = "beacon_plus_legacy_imported";
    static final String LEGACY_UNLOCKS_KEY = "beacon_plus_legacy_unlocks";
    static final String LEGACY_SELECTED_KEY = "beacon_plus_legacy_selected";
    static final String LEGACY_CUSTOM_NAME_KEY = "beacon_plus_legacy_custom_name";
    static final String LEGACY_SHOW_PARTICLES_KEY = "beacon_plus_legacy_show_particles";
    static final String LEGACY_OVERRIDDEN_RANGE_KEY = "beacon_plus_legacy_overridden_range";

    /** Imported BeaconPlus records have no owner field, so they remain operator-managed until explicitly migrated. */
    static final UUID LEGACY_IMPORTED_OWNER = UUID.nameUUIDFromBytes(
            "SlimefunLegacy:ResonanceBeacon:LegacyImported".getBytes(StandardCharsets.UTF_8));

    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Pattern CHUNK_FILE = Pattern.compile("(-?\\d+)\\.(-?\\d+)\\.json");
    private static final int MAX_JSON_STRING_LAYERS = 3;

    private static BeaconPlusLegacyDataStore instance;

    private final Slimefun plugin;
    private final Set<LocationKey> importing = new HashSet<>();

    private BeaconPlusLegacyDataStore(Slimefun plugin) {
        this.plugin = plugin;
    }

    static synchronized void start(@Nonnull Slimefun plugin) {
        if (!BeaconPlusConfig.isBeaconDataEnabled()) {
            return;
        }

        if (!"WORLD".equals(BeaconPlusConfig.getBeaconDataStorageType())) {
            plugin.getLogger().warning("Resonance Beacon BeaconData storage-type currently supports WORLD only; "
                    + "using exact legacy WORLD storage for compatibility.");
        }

        if (instance == null) {
            instance = new BeaconPlusLegacyDataStore(plugin);
        }
        if (REGISTERED.compareAndSet(false, true)) {
            Bukkit.getPluginManager().registerEvents(instance, plugin);
        }

        for (World world : Bukkit.getWorlds()) {
            instance.ensureDirectory(world);
        }

        Slimefun.getSchedulerService().runLater(instance::importAlreadyLoadedChunks, 20L);
        if (BeaconPlusConfig.shouldBootstrapLegacyActivators()) {
            Slimefun.getSchedulerService().runLater(instance::bootstrapLegacyActivators, 40L);
        }
    }

    static synchronized void shutdownCurrent() {
        instance = null;
        REGISTERED.set(false);
    }

    static boolean isLegacyImported(@Nonnull Location location) {
        return Boolean.parseBoolean(StorageCacheUtils.getData(location, IMPORTED_KEY));
    }

    static int getImportedUnlockedTier(@Nonnull Location location, @Nonnull BeaconPlusEffect effect) {
        if (!isLegacyImported(location)) {
            return 0;
        }
        return readTierMap(StorageCacheUtils.getData(location, LEGACY_UNLOCKS_KEY)).getOrDefault(effect, 0);
    }

    static int getImportedSelectedTier(@Nonnull Location location, @Nonnull BeaconPlusEffect effect) {
        if (!isLegacyImported(location)) {
            return 0;
        }
        return readTierMap(StorageCacheUtils.getData(location, LEGACY_SELECTED_KEY)).getOrDefault(effect, 0);
    }

    static double getImportedOverriddenRange(@Nonnull Location location) {
        if (!isLegacyImported(location) || !BeaconPlusConfig.shouldHonorOverriddenRange()) {
            return 0.0D;
        }
        String value = StorageCacheUtils.getData(location, LEGACY_OVERRIDDEN_RANGE_KEY);
        if (value == null || value.isBlank()) {
            return 0.0D;
        }
        try {
            return Math.max(0.0D, Double.parseDouble(value));
        } catch (NumberFormatException ignored) {
            return 0.0D;
        }
    }

    static void sync(@Nonnull Block block) {
        BeaconPlusLegacyDataStore store = instance;
        if (store != null && BeaconPlusConfig.shouldMirrorBeaconData()) {
            store.writeBeacon(block);
        }
    }

    static void remove(@Nonnull Location location) {
        BeaconPlusLegacyDataStore store = instance;
        if (store != null && BeaconPlusConfig.shouldMirrorBeaconData()) {
            store.removeBeacon(location);
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        ensureDirectory(event.getWorld());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!BeaconPlusConfig.shouldImportExistingBeaconData()) {
            return;
        }
        Chunk chunk = event.getChunk();
        Location anchor = new Location(
                chunk.getWorld(),
                (chunk.getX() << 4) + 8,
                Math.max(chunk.getWorld().getMinHeight(), 0),
                (chunk.getZ() << 4) + 8);
        Slimefun.getSchedulerService().runAtLater(anchor, () -> importChunk(chunk), 2L);
    }

    private void importAlreadyLoadedChunks() {
        if (!BeaconPlusConfig.shouldImportExistingBeaconData()) {
            return;
        }
        for (World world : Bukkit.getWorlds()) {
            ensureDirectory(world);
            for (Chunk chunk : world.getLoadedChunks()) {
                importChunk(chunk);
            }
        }
    }

    private void bootstrapLegacyActivators() {
        if (!BeaconPlusConfig.shouldBootstrapLegacyActivators()) {
            return;
        }

        for (World world : Bukkit.getWorlds()) {
            Path directory = dataDirectory(world);
            if (!Files.isDirectory(directory)) {
                continue;
            }

            try (var files = Files.list(directory)) {
                files.filter(Files::isRegularFile).forEach(path -> {
                    Matcher matcher = CHUNK_FILE.matcher(path.getFileName().toString());
                    if (!matcher.matches()) {
                        return;
                    }
                    JsonObject root = readRoot(path);
                    if (root == null || !containsActiveActivator(root)) {
                        return;
                    }

                    int chunkX = Integer.parseInt(matcher.group(1));
                    int chunkZ = Integer.parseInt(matcher.group(2));
                    if (world.isChunkLoaded(chunkX, chunkZ)) {
                        importChunk(world.getChunkAt(chunkX, chunkZ));
                        return;
                    }

                    world.getChunkAtAsync(chunkX, chunkZ, true).thenAccept(chunk -> {
                        Location anchor = new Location(
                                world,
                                (chunkX << 4) + 8,
                                Math.max(world.getMinHeight(), 0),
                                (chunkZ << 4) + 8);
                        Slimefun.getSchedulerService().runAtLater(anchor, () -> importChunk(chunk), 1L);
                    });
                });
            } catch (IOException | RuntimeException exception) {
                plugin.getLogger().log(
                        Level.WARNING,
                        "Could not scan legacy Resonance Beacon activators in " + directory,
                        exception);
            }
        }
    }

    private synchronized void importChunk(Chunk chunk) {
        Path file = dataFile(chunk.getWorld(), chunk.getX(), chunk.getZ());
        if (!Files.isRegularFile(file)) {
            return;
        }

        JsonObject root = readRoot(file);
        if (root == null) {
            return;
        }
        JsonArray beacons = getBeaconArray(root);
        if (beacons == null) {
            return;
        }

        for (JsonElement element : beacons) {
            if (element.isJsonObject()) {
                importBeacon(chunk.getWorld(), element.getAsJsonObject());
            }
        }
    }

    private void importBeacon(World world, JsonObject legacy) {
        Integer x = getInt(legacy, "x");
        Integer y = getInt(legacy, "y");
        Integer z = getInt(legacy, "z");
        if (x == null || y == null || z == null) {
            return;
        }

        Location location = new Location(world, x, y, z);
        if (!world.isChunkLoaded(x >> 4, z >> 4)) {
            return;
        }
        Block block = world.getBlockAt(x, y, z);
        if (block.getType() != Material.BEACON) {
            return;
        }

        SlimefunBlockData data = StorageCacheUtils.getBlock(location);
        boolean newlyImported = false;
        if (data == null) {
            if (StorageCacheUtils.hasSlimefunBlock(location)) {
                plugin.getLogger().warning("Skipped BeaconData import at " + describe(location)
                        + " because another Slimefun block already owns that location.");
                return;
            }
            try {
                data = Slimefun.getDatabaseManager()
                        .getBlockDataController()
                        .createBlock(location, BeaconPlusManager.ITEM_ID);
                newlyImported = true;
            } catch (IllegalStateException exception) {
                data = StorageCacheUtils.getBlock(location);
                if (data == null) {
                    plugin.getLogger().log(Level.WARNING, "Could not import BeaconData at " + describe(location), exception);
                    return;
                }
            }
        }

        if (!BeaconPlusManager.ITEM_ID.equals(data.getSfId())) {
            return;
        }

        // Native Resonance Beacon state is authoritative. Only legacy-imported blocks are refreshed from BeaconData.
        if (!newlyImported && !Boolean.parseBoolean(data.getData(IMPORTED_KEY))) {
            return;
        }

        LocationKey key = LocationKey.from(location);
        importing.add(key);
        try {
            EnumMap<BeaconPlusEffect, Integer> unlocked = new EnumMap<>(BeaconPlusEffect.class);
            EnumMap<BeaconPlusEffect, Integer> selected = new EnumMap<>(BeaconPlusEffect.class);
            EnumSet<BeaconPlusEffect> enabled = EnumSet.noneOf(BeaconPlusEffect.class);

            JsonObject effects = legacy.has("effects") && legacy.get("effects").isJsonObject()
                    ? legacy.getAsJsonObject("effects")
                    : new JsonObject();
            for (BeaconPlusEffect effect : BeaconPlusEffect.configurableValues()) {
                JsonObject effectData = findLegacyEffect(effects, effect);
                if (effectData == null) {
                    continue;
                }
                int rawLevel = Math.max(0, getInt(effectData, "level", 0));
                int rawSelected = Math.max(0, getInt(effectData, "selected", 0));
                int legacyMaximum = legacyMaximum(effect);
                int unlockedTier = normalizeLegacyTier(rawLevel, legacyMaximum);
                int selectedTier = normalizeLegacyTier(rawSelected, legacyMaximum);
                if (unlockedTier > 0) {
                    unlocked.put(effect, unlockedTier);
                }
                if (selectedTier > 0) {
                    selected.put(effect, unlockedTier > 0 ? Math.min(unlockedTier, selectedTier) : selectedTier);
                    enabled.add(effect);
                }
            }

            data.setData(IMPORTED_KEY, Boolean.TRUE.toString());
            data.setData(BeaconPlusManager.OWNER_KEY, LEGACY_IMPORTED_OWNER.toString());
            data.setData(LEGACY_UNLOCKS_KEY, serializeTierMap(unlocked));
            data.setData(LEGACY_SELECTED_KEY, serializeTierMap(selected));
            data.setData(BeaconPlusRuntime.EFFECTS_KEY, BeaconPlusEffect.serialize(enabled));

            String customName = getString(legacy, "customName");
            if (customName != null) {
                data.setData(LEGACY_CUSTOM_NAME_KEY, customName);
            }
            data.setData(
                    LEGACY_SHOW_PARTICLES_KEY,
                    Boolean.toString(!legacy.has("showParticles") || legacy.get("showParticles").getAsBoolean()));
            if (legacy.has("overriddenRange") && legacy.get("overriddenRange").isJsonPrimitive()) {
                try {
                    data.setData(LEGACY_OVERRIDDEN_RANGE_KEY, Double.toString(legacy.get("overriddenRange").getAsDouble()));
                } catch (RuntimeException ignored) {
                    data.removeData(LEGACY_OVERRIDDEN_RANGE_KEY);
                }
            }

            int activatorTier = selected.getOrDefault(BeaconPlusEffect.ACTIVATOR, 0);
            BeaconPlusChunkMode desiredMode = chunkModeForTier(activatorTier);
            data.setData(BeaconPlusManager.CHUNK_MODE_KEY, desiredMode.name());
            if (data.getData(BeaconPlusManager.SUPPORT_MODE_KEY) == null) {
                data.setData(BeaconPlusManager.SUPPORT_MODE_KEY, BeaconPlusSupportMode.OFF.name());
            }

            BeaconPlusManager manager = BeaconPlusManager.getInstance();
            if (manager != null) {
                if (manager.getOwner(location) == null) {
                    manager.register(location, LEGACY_IMPORTED_OWNER);
                }
                manager.updateModes(
                        location,
                        LEGACY_IMPORTED_OWNER,
                        desiredMode,
                        manager.getSupportMode(location));
            }
            BeaconPlusRuntime.observe(block);

            if (newlyImported) {
                plugin.getLogger().info("Imported legacy BeaconData as Resonance Beacon at " + describe(location));
            }
        } finally {
            importing.remove(key);
        }
    }

    private synchronized void writeBeacon(Block block) {
        Location location = block.getLocation();
        if (importing.contains(LocationKey.from(location))
                || !StorageCacheUtils.isBlock(location, BeaconPlusManager.ITEM_ID)) {
            return;
        }

        Path file = dataFile(block.getWorld(), block.getX() >> 4, block.getZ() >> 4);
        JsonObject root = readRoot(file);
        if (root == null) {
            root = new JsonObject();
        }
        JsonArray beacons = getOrCreateBeaconArray(root);
        JsonObject beacon = findBeacon(beacons, block.getX(), block.getY(), block.getZ());
        if (beacon == null) {
            beacon = new JsonObject();
            beacons.add(beacon);
        }

        beacon.addProperty("x", block.getX());
        beacon.addProperty("y", block.getY());
        beacon.addProperty("z", block.getZ());

        boolean imported = isLegacyImported(location);
        String storedName = StorageCacheUtils.getData(location, LEGACY_CUSTOM_NAME_KEY);
        if (!imported || storedName == null || storedName.isBlank()) {
            beacon.addProperty("customName", "Resonance Beacon");
        } else {
            beacon.addProperty("customName", storedName);
        }

        String particleValue = StorageCacheUtils.getData(location, LEGACY_SHOW_PARTICLES_KEY);
        beacon.addProperty("showParticles", particleValue == null || Boolean.parseBoolean(particleValue));

        double overriddenRange = getImportedOverriddenRange(location);
        if (imported && overriddenRange > 0.0D) {
            beacon.addProperty("overriddenRange", overriddenRange);
        }

        JsonObject effects = beacon.has("effects") && beacon.get("effects").isJsonObject()
                ? beacon.getAsJsonObject("effects")
                : new JsonObject();
        beacon.add("effects", effects);

        EnumSet<BeaconPlusEffect> configured = BeaconPlusRuntime.getConfiguredEffects(location);
        UUID owner = getOwner(location);
        for (BeaconPlusEffect effect : BeaconPlusEffect.configurableValues()) {
            int unlockedTier = BeaconPlusRuntime.getUnlockedTierAtBeacon(block, effect);
            boolean active = configured.contains(effect);
            int selectedTier = active ? BeaconPlusRuntime.getSelectedTierAtBeacon(block, effect) : 0;

            String existingKey = findLegacyEffectKey(effects, effect);
            JsonObject existing = existingKey == null ? null : effects.getAsJsonObject(existingKey);
            if (unlockedTier <= 0 && existing == null) {
                continue;
            }
            if (existing == null) {
                existing = new JsonObject();
            }

            int existingLevel = Math.max(0, getInt(existing, "level", 0));
            int rawLevel = imported && existingLevel > 0 ? existingLevel : unlockedTier;
            if (rawLevel <= 0 && owner != null) {
                rawLevel = BeaconPlusProgression.getUnlockedTier(owner, effect);
            }
            if (rawLevel <= 0) {
                rawLevel = Math.max(1, unlockedTier);
            }
            int existingSelected = Math.max(0, getInt(existing, "selected", 0));
            int rawSelected = imported ? (active ? Math.max(1, existingSelected) : 0) : selectedTier;

            existing.addProperty("level", rawLevel);
            existing.addProperty("selected", Math.max(0, rawSelected));
            effects.add(existingKey == null ? legacyKey(effect) : existingKey, existing);
        }

        writeRoot(file, root);
    }

    private synchronized void removeBeacon(Location location) {
        Path file = dataFile(location.getWorld(), location.getBlockX() >> 4, location.getBlockZ() >> 4);
        JsonObject root = readRoot(file);
        if (root == null) {
            return;
        }
        JsonArray beacons = getBeaconArray(root);
        if (beacons == null) {
            return;
        }

        for (int index = beacons.size() - 1; index >= 0; index--) {
            JsonElement element = beacons.get(index);
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject beacon = element.getAsJsonObject();
            if (getInt(beacon, "x", Integer.MIN_VALUE) == location.getBlockX()
                    && getInt(beacon, "y", Integer.MIN_VALUE) == location.getBlockY()
                    && getInt(beacon, "z", Integer.MIN_VALUE) == location.getBlockZ()) {
                beacons.remove(index);
            }
        }

        try {
            if (beacons.size() == 0) {
                Files.deleteIfExists(file);
            } else {
                writeRoot(file, root);
            }
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not remove Resonance Beacon data from " + file, exception);
        }
    }

    private boolean containsActiveActivator(JsonObject root) {
        JsonArray beacons = getBeaconArray(root);
        if (beacons == null) {
            return false;
        }
        for (JsonElement element : beacons) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject beacon = element.getAsJsonObject();
            if (!beacon.has("effects") || !beacon.get("effects").isJsonObject()) {
                continue;
            }
            JsonObject activator = findLegacyEffect(beacon.getAsJsonObject("effects"), BeaconPlusEffect.ACTIVATOR);
            if (activator != null && getInt(activator, "selected", 0) > 0) {
                return true;
            }
        }
        return false;
    }

    private void ensureDirectory(World world) {
        try {
            Files.createDirectories(dataDirectory(world));
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not create Resonance Beacon BeaconData folder.", exception);
        }
    }

    private Path dataDirectory(World world) {
        return world.getWorldFolder().toPath().resolve(BeaconPlusConfig.getBeaconDataFolderName());
    }

    private Path dataFile(World world, int chunkX, int chunkZ) {
        return dataDirectory(world).resolve(chunkX + "." + chunkZ + ".json");
    }

    private @Nullable JsonObject readRoot(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            int unwrapped = 0;
            while (element != null
                    && element.isJsonPrimitive()
                    && element.getAsJsonPrimitive().isString()
                    && unwrapped < MAX_JSON_STRING_LAYERS) {
                String nested = element.getAsString();
                if (nested.isBlank()) {
                    return null;
                }
                element = JsonParser.parseString(nested);
                unwrapped++;
            }
            if (element == null || !element.isJsonObject()) {
                plugin.getLogger().warning("Ignored malformed BeaconData file with non-object JSON: " + file);
                return null;
            }
            if (unwrapped > 0) {
                plugin.getLogger().warning("Recovered double-encoded BeaconData JSON at " + file + " ("
                        + unwrapped + " layer(s)); it will normalize on the next save.");
            }
            return element.getAsJsonObject();
        } catch (IOException | RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not read BeaconData file " + file, exception);
            return null;
        }
    }

    private void writeRoot(Path file, JsonObject root) {
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            try {
                Files.move(
                        temporary,
                        file,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not write BeaconData file " + file, exception);
        }
    }

    private static @Nullable JsonArray getBeaconArray(JsonObject root) {
        JsonElement element = root.get("Beacons");
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static JsonArray getOrCreateBeaconArray(JsonObject root) {
        JsonArray beacons = getBeaconArray(root);
        if (beacons == null) {
            beacons = new JsonArray();
            root.add("Beacons", beacons);
        }
        return beacons;
    }

    private static @Nullable JsonObject findBeacon(JsonArray beacons, int x, int y, int z) {
        for (JsonElement element : beacons) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject beacon = element.getAsJsonObject();
            if (getInt(beacon, "x", Integer.MIN_VALUE) == x
                    && getInt(beacon, "y", Integer.MIN_VALUE) == y
                    && getInt(beacon, "z", Integer.MIN_VALUE) == z) {
                return beacon;
            }
        }
        return null;
    }

    private static @Nullable String findLegacyEffectKey(JsonObject effects, BeaconPlusEffect effect) {
        for (String key : legacyKeys(effect)) {
            JsonElement element = effects.get(key);
            if (element != null && element.isJsonObject()) {
                return key;
            }
        }
        return null;
    }

    private static @Nullable JsonObject findLegacyEffect(JsonObject effects, BeaconPlusEffect effect) {
        String key = findLegacyEffectKey(effects, effect);
        return key == null ? null : effects.getAsJsonObject(key);
    }

    private static List<String> legacyKeys(BeaconPlusEffect effect) {
        return switch (effect) {
            case FURNACE_BOOSTER -> List.of("furnace", "furnace_booster");
            case STRENGTH -> List.of("strength");
            case REGENERATION -> List.of("regeneration");
            case RESISTANCE -> List.of("resist", "resistance");
            case FAST_DIGGING -> List.of("fastdig", "fast_digging");
            case CURE -> List.of("cure");
            case CROPS -> List.of("crops");
            case SPAWNERS -> List.of("spawners");
            case SLOWDOWN -> List.of("slowdown");
            case SPEED -> List.of("speed");
            case PEACEFUL -> List.of("peaceful");
            case NIGHT_VISION -> List.of("nightvision", "night_vision");
            case FLYING -> List.of("flying");
            case EXPERIENCE_BOOSTER -> List.of("exp_boost", "experience_booster");
            case LUCK -> List.of("luck");
            case BURNER -> List.of("burner");
            case WATER_BREATHING -> List.of("water_breathing");
            case FIRE_EXTINGUISHER -> List.of("fireExtinguisher", "fire_extenguisher", "fire_extinguisher");
            case POISON -> List.of("poison");
            case GRAVITY_WELL -> List.of("gravity_well");
            case JUMP -> List.of("jump");
            case EXP_GAIN -> List.of("exp_gain");
            case COOLDOWN_REDUCTION -> List.of("cooldown_reduction");
            case IMMORTALITY_FIELD -> List.of("immortality", "immortality_field");
            case EXTRA_POWER -> List.of("extra_power");
            case EXTRA_RANGE -> List.of("extra_range");
            case ACTIVATOR -> List.of("activator");
            case AUTO_REPAIR -> List.of("auto_repair");
            case SCALE -> List.of("scale");
        };
    }

    private static String legacyKey(BeaconPlusEffect effect) {
        return legacyKeys(effect).get(0);
    }

    private static int normalizeLegacyTier(int value, int legacyMaximum) {
        if (value <= 0) {
            return 0;
        }
        int max = Math.max(1, legacyMaximum);
        if (max <= BeaconPlusConfig.getMaxTier()) {
            return Math.min(value, BeaconPlusConfig.getMaxTier());
        }
        return Math.max(
                1,
                Math.min(
                        BeaconPlusConfig.getMaxTier(),
                        (int) Math.ceil(value * (double) BeaconPlusConfig.getMaxTier() / max)));
    }

    private static int legacyMaximum(BeaconPlusEffect effect) {
        return switch (effect) {
            case CROPS -> 6;
            case EXPERIENCE_BOOSTER, SPAWNERS -> 5;
            case FLYING, EXP_GAIN, IMMORTALITY_FIELD, FURNACE_BOOSTER -> 4;
            case ACTIVATOR, PEACEFUL, REGENERATION -> 2;
            case BURNER, FIRE_EXTINGUISHER, NIGHT_VISION, WATER_BREATHING -> 1;
            case SCALE -> 3;
            default -> 3;
        };
    }

    private static BeaconPlusChunkMode chunkModeForTier(int tier) {
        return switch (tier) {
            case 1 -> BeaconPlusChunkMode.SINGLE;
            case 2 -> BeaconPlusChunkMode.AREA_3X3;
            case 3 -> BeaconPlusChunkMode.AREA_5X5;
            default -> BeaconPlusChunkMode.OFF;
        };
    }

    private static String serializeTierMap(EnumMap<BeaconPlusEffect, Integer> tiers) {
        List<String> values = new ArrayList<>();
        for (BeaconPlusEffect effect : BeaconPlusEffect.configurableValues()) {
            int tier = tiers.getOrDefault(effect, 0);
            if (tier > 0) {
                values.add(effect.getId() + "=" + tier);
            }
        }
        return String.join(",", values);
    }

    private static EnumMap<BeaconPlusEffect, Integer> readTierMap(String stored) {
        EnumMap<BeaconPlusEffect, Integer> result = new EnumMap<>(BeaconPlusEffect.class);
        if (stored == null || stored.isBlank()) {
            return result;
        }
        for (String entry : stored.split(",")) {
            String[] parts = entry.split("=", 2);
            if (parts.length != 2) {
                continue;
            }
            String id = parts[0].trim().toLowerCase(Locale.ROOT);
            BeaconPlusEffect effect = null;
            for (BeaconPlusEffect candidate : BeaconPlusEffect.configurableValues()) {
                if (candidate.getId().equals(id)) {
                    effect = candidate;
                    break;
                }
            }
            if (effect == null) {
                continue;
            }
            try {
                int tier = Math.max(0, Math.min(BeaconPlusConfig.getMaxTier(), Integer.parseInt(parts[1].trim())));
                if (tier > 0) {
                    result.put(effect, tier);
                }
            } catch (NumberFormatException ignored) {
                // Ignore one malformed effect tier without discarding the rest of the imported beacon.
            }
        }
        return result;
    }

    private static @Nullable UUID getOwner(Location location) {
        BeaconPlusManager manager = BeaconPlusManager.getInstance();
        UUID owner = manager == null ? null : manager.getOwner(location);
        if (owner != null) {
            return owner;
        }
        String stored = StorageCacheUtils.getData(location, BeaconPlusManager.OWNER_KEY);
        if (stored == null || stored.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(stored);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static @Nullable Integer getInt(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
            return null;
        }
        try {
            return object.get(key).getAsInt();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static int getInt(JsonObject object, String key, int fallback) {
        Integer value = getInt(object, key);
        return value == null ? fallback : value;
    }

    private static @Nullable String getString(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonPrimitive()) {
            return null;
        }
        try {
            return object.get(key).getAsString();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String describe(Location location) {
        return location.getWorld().getName() + " [" + location.getBlockX() + ", " + location.getBlockY() + ", "
                + location.getBlockZ() + "]";
    }

    private record LocationKey(UUID worldId, int x, int y, int z) {
        private static LocationKey from(Location location) {
            return new LocationKey(
                    location.getWorld().getUID(),
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ());
        }
    }
}
