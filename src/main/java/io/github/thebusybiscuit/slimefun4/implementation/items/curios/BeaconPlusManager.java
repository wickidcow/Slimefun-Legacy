package io.github.thebusybiscuit.slimefun4.implementation.items.curios;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Owns Beacon Plus persistence and Paper/Folia plugin chunk tickets.
 *
 * <p>This manager never ticks machines itself. It only keeps selected chunks loaded so Slimefun core and addon
 * runtimes continue using their normal schedulers and transaction rules.
 */
public final class BeaconPlusManager {

    public static final String ITEM_ID = "BEACON_PLUS";
    public static final String OWNER_KEY = "beacon_plus_owner";
    public static final String CHUNK_MODE_KEY = "beacon_plus_chunk_mode";
    public static final String SUPPORT_MODE_KEY = "beacon_plus_support_mode";

    private static final int MAX_ACTIVE_BEACONS = 64;
    private static final int MAX_UNIQUE_CHUNKS = 256;
    private static final int VALIDATION_ATTEMPTS = 3;
    private static final long VALIDATION_DELAY_TICKS = 100L;

    private static BeaconPlusManager instance;

    private final Slimefun plugin;
    private final Path registryPath;
    private final Map<LocationKey, BeaconRecord> records = new HashMap<>();
    private final Map<ChunkKey, Integer> ticketReferences = new HashMap<>();

    private BeaconPlusManager(Slimefun plugin) {
        this.plugin = plugin;
        this.registryPath = plugin.getDataFolder().toPath().resolve("adventurers-curios-beacons.properties");
    }

    public static synchronized void start(@Nonnull Slimefun plugin) {
        if (instance != null) {
            instance.shutdown();
        }

        BeaconPlusManager manager = new BeaconPlusManager(plugin);
        instance = manager;
        manager.loadRegistry();
        manager.restoreTickets();
        manager.scheduleValidation();
    }

    public static synchronized void shutdownCurrent() {
        if (instance != null) {
            instance.shutdown();
            instance = null;
        }
    }

    public static @Nullable BeaconPlusManager getInstance() {
        return instance;
    }

    public synchronized void register(@Nonnull Location location, @Nonnull UUID owner) {
        LocationKey key = LocationKey.from(location);
        BeaconRecord existing = records.get(key);
        if (existing != null) {
            releaseCoverage(existing);
        }

        BeaconRecord record = new BeaconRecord(key, owner, BeaconPlusChunkMode.OFF, BeaconPlusSupportMode.OFF);
        records.put(key, record);
        persistBlockData(location, record);
        saveRegistry();
    }

    public synchronized void unregister(@Nonnull Location location) {
        LocationKey key = LocationKey.from(location);
        BeaconRecord record = records.remove(key);
        if (record != null) {
            releaseCoverage(record);
            saveRegistry();
        }
    }

    public synchronized @Nonnull BeaconPlusChunkMode getChunkMode(@Nonnull Location location) {
        BeaconRecord record = records.get(LocationKey.from(location));
        if (record != null) {
            return record.chunkMode();
        }
        return BeaconPlusChunkMode.fromStored(StorageCacheUtils.getData(location, CHUNK_MODE_KEY));
    }

    public synchronized @Nonnull BeaconPlusSupportMode getSupportMode(@Nonnull Location location) {
        BeaconRecord record = records.get(LocationKey.from(location));
        if (record != null) {
            return record.supportMode();
        }
        return BeaconPlusSupportMode.fromStored(StorageCacheUtils.getData(location, SUPPORT_MODE_KEY));
    }

    public synchronized @Nullable UUID getOwner(@Nonnull Location location) {
        BeaconRecord record = records.get(LocationKey.from(location));
        if (record != null) {
            return record.owner();
        }

        String stored = StorageCacheUtils.getData(location, OWNER_KEY);
        if (stored == null) {
            return null;
        }

        try {
            return UUID.fromString(stored);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * Updates both independent Beacon Plus controls atomically.
     *
     * @return {@code true} when the requested chunk profile could be activated within the safety caps
     */
    public synchronized boolean updateModes(
            @Nonnull Location location,
            @Nonnull UUID owner,
            @Nonnull BeaconPlusChunkMode chunkMode,
            @Nonnull BeaconPlusSupportMode supportMode) {
        LocationKey key = LocationKey.from(location);
        BeaconRecord previous = records.getOrDefault(
                key, new BeaconRecord(key, owner, BeaconPlusChunkMode.OFF, BeaconPlusSupportMode.OFF));
        BeaconRecord replacement = new BeaconRecord(key, owner, chunkMode, supportMode);

        if (!canActivate(previous, replacement)) {
            return false;
        }

        boolean chunkModeChanged = previous.chunkMode() != replacement.chunkMode();
        if (chunkModeChanged) {
            releaseCoverage(previous);
        }

        records.put(key, replacement);

        if (chunkModeChanged) {
            acquireCoverage(replacement);
        }

        persistBlockData(location, replacement);
        saveRegistry();
        return true;
    }

    public synchronized int getActiveBeaconCount() {
        return (int) records.values().stream()
                .filter(record -> record.chunkMode() != BeaconPlusChunkMode.OFF)
                .count();
    }

    public synchronized int getLoadedChunkCount() {
        return ticketReferences.size();
    }

    private boolean canActivate(BeaconRecord previous, BeaconRecord replacement) {
        if (replacement.chunkMode() == BeaconPlusChunkMode.OFF) {
            return true;
        }

        boolean previousWasActive = previous.chunkMode() != BeaconPlusChunkMode.OFF;
        int activeAfter = getActiveBeaconCount() + (previousWasActive ? 0 : 1);
        if (activeAfter > MAX_ACTIVE_BEACONS) {
            return false;
        }

        Set<ChunkKey> previousCoverage = coverage(previous);
        Set<ChunkKey> replacementCoverage = coverage(replacement);
        int projected = ticketReferences.size();

        for (ChunkKey key : previousCoverage) {
            if (ticketReferences.getOrDefault(key, 0) == 1 && !replacementCoverage.contains(key)) {
                projected--;
            }
        }
        for (ChunkKey key : replacementCoverage) {
            if (!ticketReferences.containsKey(key) && !previousCoverage.contains(key)) {
                projected++;
            }
        }

        return projected <= MAX_UNIQUE_CHUNKS;
    }

    private void restoreTickets() {
        boolean changed = false;
        int restoredActiveBeacons = 0;

        for (Map.Entry<LocationKey, BeaconRecord> entry : new HashMap<>(records).entrySet()) {
            BeaconRecord record = entry.getValue();
            if (record.chunkMode() == BeaconPlusChunkMode.OFF) {
                continue;
            }

            Set<ChunkKey> recordCoverage = coverage(record);
            long newChunks = recordCoverage.stream()
                    .filter(key -> !ticketReferences.containsKey(key))
                    .count();
            boolean exceedsBeaconCap = restoredActiveBeacons >= MAX_ACTIVE_BEACONS;
            boolean exceedsChunkCap = ticketReferences.size() + newChunks > MAX_UNIQUE_CHUNKS;

            if (exceedsBeaconCap || exceedsChunkCap) {
                BeaconRecord inactive = new BeaconRecord(
                        record.location(), record.owner(), BeaconPlusChunkMode.OFF, record.supportMode());
                plugin.getLogger()
                        .warning(
                                "Beacon Plus at " + record.location().describe()
                                        + " was restored with chunk loading disabled because the global safety cap was reached.");
                records.put(entry.getKey(), inactive);
                changed = true;
                continue;
            }

            acquireCoverage(record);
            restoredActiveBeacons++;
        }

        if (changed) {
            saveRegistry();
        }
    }

    private void scheduleValidation() {
        for (BeaconRecord record : new HashMap<>(records).values()) {
            World world = Bukkit.getWorld(record.location().worldId());
            if (world == null) {
                continue;
            }

            Location location = record.location().toLocation(world);
            Slimefun.getSchedulerService()
                    .runAtLater(location, () -> validateRecord(record.location(), 1), VALIDATION_DELAY_TICKS);
        }
    }

    private void validateRecord(LocationKey key, int attempt) {
        World world = Bukkit.getWorld(key.worldId());
        if (world == null) {
            return;
        }

        Location location = key.toLocation(world);
        var data = StorageCacheUtils.getBlock(location);
        if (data == null) {
            if (attempt < VALIDATION_ATTEMPTS) {
                Slimefun.getSchedulerService()
                        .runAtLater(location, () -> validateRecord(key, attempt + 1), VALIDATION_DELAY_TICKS);
            } else {
                unregister(location);
                plugin.getLogger().info("Removed stale Beacon Plus registry entry at " + key.describe());
            }
            return;
        }

        if (!ITEM_ID.equals(data.getSfId())) {
            unregister(location);
            plugin.getLogger().info("Removed stale Beacon Plus registry entry at " + key.describe());
            return;
        }

        BeaconRecord persisted;
        synchronized (this) {
            persisted = records.get(key);
        }
        if (persisted == null) {
            return;
        }

        String owner = data.getData(OWNER_KEY);
        if (owner == null || owner.isBlank()) {
            data.setData(OWNER_KEY, persisted.owner().toString());
        }
        String chunkMode = data.getData(CHUNK_MODE_KEY);
        if (chunkMode == null || chunkMode.isBlank()) {
            data.setData(CHUNK_MODE_KEY, persisted.chunkMode().name());
        }
        String supportMode = data.getData(SUPPORT_MODE_KEY);
        if (supportMode == null || supportMode.isBlank()) {
            data.setData(SUPPORT_MODE_KEY, persisted.supportMode().name());
        }
    }

    private void acquireCoverage(BeaconRecord record) {
        for (ChunkKey key : coverage(record)) {
            int references = ticketReferences.getOrDefault(key, 0);
            if (references == 0) {
                World world = Bukkit.getWorld(key.worldId());
                if (world == null) {
                    continue;
                }

                try {
                    world.addPluginChunkTicket(key.x(), key.z(), plugin);
                } catch (RuntimeException exception) {
                    plugin.getLogger()
                            .log(Level.WARNING, "Could not load Beacon Plus chunk " + key.describe(), exception);
                    continue;
                }
            }
            ticketReferences.put(key, references + 1);
        }
    }

    private void releaseCoverage(BeaconRecord record) {
        for (ChunkKey key : coverage(record)) {
            Integer references = ticketReferences.get(key);
            if (references == null) {
                continue;
            }

            if (references <= 1) {
                ticketReferences.remove(key);
                World world = Bukkit.getWorld(key.worldId());
                if (world != null) {
                    try {
                        world.removePluginChunkTicket(key.x(), key.z(), plugin);
                    } catch (RuntimeException exception) {
                        plugin.getLogger()
                                .log(Level.WARNING, "Could not release Beacon Plus chunk " + key.describe(), exception);
                    }
                }
            } else {
                ticketReferences.put(key, references - 1);
            }
        }
    }

    private Set<ChunkKey> coverage(BeaconRecord record) {
        Set<ChunkKey> chunks = new HashSet<>();
        if (record.chunkMode() == BeaconPlusChunkMode.OFF) {
            return chunks;
        }

        int centerX = record.location().x() >> 4;
        int centerZ = record.location().z() >> 4;
        int radius = record.chunkMode().getRadius();
        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                chunks.add(new ChunkKey(record.location().worldId(), x, z));
            }
        }
        return chunks;
    }

    private void persistBlockData(Location location, BeaconRecord record) {
        var data = StorageCacheUtils.getBlock(location);
        if (data == null) {
            return;
        }

        data.setData(OWNER_KEY, record.owner().toString());
        data.setData(CHUNK_MODE_KEY, record.chunkMode().name());
        data.setData(SUPPORT_MODE_KEY, record.supportMode().name());
    }

    private void loadRegistry() {
        if (!Files.isRegularFile(registryPath)) {
            return;
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(registryPath)) {
            properties.load(input);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not read the Beacon Plus registry.", exception);
            return;
        }

        for (String keyText : properties.stringPropertyNames()) {
            try {
                LocationKey location = LocationKey.parse(keyText);
                String[] parts = properties.getProperty(keyText, "").split(";", -1);
                if (parts.length < 3) {
                    continue;
                }

                UUID owner = UUID.fromString(parts[0]);
                BeaconPlusChunkMode chunkMode = BeaconPlusChunkMode.fromStored(parts[1]);
                BeaconPlusSupportMode supportMode = BeaconPlusSupportMode.fromStored(parts[2]);
                records.put(location, new BeaconRecord(location, owner, chunkMode, supportMode));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Ignored malformed Beacon Plus registry entry: " + keyText);
            }
        }
    }

    private synchronized void saveRegistry() {
        Properties properties = new Properties();
        for (BeaconRecord record : records.values()) {
            properties.setProperty(
                    record.location().serialize(),
                    record.owner() + ";" + record.chunkMode().name() + ";"
                            + record.supportMode().name());
        }

        try {
            Files.createDirectories(registryPath.getParent());
            try (OutputStream output = Files.newOutputStream(registryPath)) {
                properties.store(output, "Slimefun Legacy Adventurer's Curios - Beacon Plus registry");
            }
        } catch (IOException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not save the Beacon Plus registry.", exception);
        }
    }

    private synchronized void shutdown() {
        for (ChunkKey key : new HashSet<>(ticketReferences.keySet())) {
            World world = Bukkit.getWorld(key.worldId());
            if (world != null) {
                try {
                    world.removePluginChunkTicket(key.x(), key.z(), plugin);
                } catch (RuntimeException exception) {
                    plugin.getLogger()
                            .log(Level.FINE, "Could not release Beacon Plus chunk during shutdown.", exception);
                }
            }
        }
        ticketReferences.clear();
        saveRegistry();
    }

    private record BeaconRecord(
            LocationKey location, UUID owner, BeaconPlusChunkMode chunkMode, BeaconPlusSupportMode supportMode) {}

    private record LocationKey(UUID worldId, int x, int y, int z) {
        private static LocationKey from(Location location) {
            return new LocationKey(
                    location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }

        private static LocationKey parse(String value) {
            String[] parts = value.split(";", -1);
            if (parts.length != 4) {
                throw new IllegalArgumentException("Malformed location key");
            }
            return new LocationKey(
                    UUID.fromString(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]),
                    Integer.parseInt(parts[3]));
        }

        private Location toLocation(World world) {
            return new Location(world, x, y, z);
        }

        private String serialize() {
            return worldId + ";" + x + ";" + y + ";" + z;
        }

        private String describe() {
            return worldId + " [" + x + ", " + y + ", " + z + "]";
        }
    }

    private record ChunkKey(UUID worldId, int x, int z) {
        private String describe() {
            return worldId + " [" + x + ", " + z + "]";
        }
    }
}
