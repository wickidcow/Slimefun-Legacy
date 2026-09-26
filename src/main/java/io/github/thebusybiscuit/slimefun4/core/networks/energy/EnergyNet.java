package io.github.thebusybiscuit.slimefun4.core.networks.energy;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.ASlimefunDataContainer;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.api.ErrorReport;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.network.Network;
import io.github.thebusybiscuit.slimefun4.api.network.NetworkComponent;
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetComponent;
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetProvider;
import io.github.thebusybiscuit.slimefun4.core.attributes.HologramOwner;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.utils.NumberUtils;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

/**
 * The {@link EnergyNet} is an implementation of {@link Network} that deals with
 * electrical energy being sent from and to nodes.
 *
 * @author meiamsome
 * @author TheBusyBiscuit
 *
 * @see Network
 * @see EnergyNetComponent
 * @see EnergyNetProvider
 * @see EnergyNetComponentType
 *
 */
public class EnergyNet extends Network implements HologramOwner {

    private static final int RANGE = 6;
    private static final int TRANSPORT_STATE_REVALIDATE_INTERVAL = 20;
    private static final long HOLOGRAM_REVALIDATE_INTERVAL_TICKS = 20L;
    private static final int HOLOGRAM_MODE_BALANCE = 0;
    private static final int HOLOGRAM_MODE_DUPLICATE_REGULATOR = 1;
    private static final int HOLOGRAM_MODE_NO_NETWORK = 2;

    private final Map<Location, EnergyNetProvider> generators = new ConcurrentHashMap<>();
    private final Map<Location, EnergyNetComponent> capacitors = new ConcurrentHashMap<>();
    private final Map<Location, EnergyNetComponent> consumers = new ConcurrentHashMap<>();

    /*
     * Capacitors and generators are read once while supply is collected, then written again after
     * consumers have taken their share. Keep the already validated block-data/container references
     * for the remainder-storage phase instead of resolving every source a second time in the same
     * regulator tick. The arrays are reused by this network so the optimization does not trade CPU
     * time for per-tick object churn.
     */
    private final EnergyStorageSnapshot capacitorStorageSnapshot = new EnergyStorageSnapshot();
    private final EnergyStorageSnapshot generatorStorageSnapshot = new EnergyStorageSnapshot();

    /*
     * Generator entries are profiled separately from the regulator. A primitive field is reused
     * for the current tick so the regulator does not allocate an AtomicLong on every sampled pass.
     */
    private long generatorProfileNanos;

    private boolean transportStateDirty = true;
    private boolean transportPowered;

    /*
     * Energy regulator labels are presentation-only. Avoid re-entering the hologram service every
     * server tick when the displayed state is unchanged; the service still gets a periodic refresh
     * so despawned/external hologram state can self-heal.
     */
    private int hologramMode = -1;
    private long hologramSupply = Long.MIN_VALUE;
    private long hologramDemand = Long.MIN_VALUE;
    private long nextHologramRefreshTick;

    protected EnergyNet(@Nonnull Location l) {
        super(Slimefun.getNetworkManager(), l);
    }

    private static final class EnergyStorageSnapshot {
        private static final int INITIAL_CAPACITY = 8;

        private Location[] locations = new Location[INITIAL_CAPACITY];
        private EnergyNetComponent[] components = new EnergyNetComponent[INITIAL_CAPACITY];
        private ASlimefunDataContainer[] containers = new ASlimefunDataContainer[INITIAL_CAPACITY];
        private long[] capacities = new long[INITIAL_CAPACITY];
        private long[] charges = new long[INITIAL_CAPACITY];
        private int size;

        private void add(
                @Nonnull Location location,
                @Nonnull EnergyNetComponent component,
                @Nonnull ASlimefunDataContainer container,
                long capacity,
                long charge) {
            ensureCapacity(size + 1);
            locations[size] = location;
            components[size] = component;
            containers[size] = container;
            capacities[size] = capacity;
            charges[size] = charge;
            size++;
        }

        private void ensureCapacity(int requestedSize) {
            if (requestedSize <= locations.length) {
                return;
            }

            int newCapacity = Math.max(requestedSize, locations.length << 1);
            locations = Arrays.copyOf(locations, newCapacity);
            components = Arrays.copyOf(components, newCapacity);
            containers = Arrays.copyOf(containers, newCapacity);
            capacities = Arrays.copyOf(capacities, newCapacity);
            charges = Arrays.copyOf(charges, newCapacity);
        }

        private void clear() {
            Arrays.fill(locations, 0, size, null);
            Arrays.fill(components, 0, size, null);
            Arrays.fill(containers, 0, size, null);
            size = 0;
        }
    }

    @Override
    public int getRange() {
        return RANGE;
    }

    /**
     * This creates an immutable {@link Map} of {@link EnergyNetProvider}s within this {@link EnergyNet} instance.
     *
     * @return An immutable {@link Map} of generators
     */
    public @Nonnull Map<Location, EnergyNetProvider> getGenerators() {
        return Collections.unmodifiableMap(generators);
    }

    /**
     * This creates an immutable {@link Map} of {@link EnergyNetComponentType#CAPACITOR} {@link EnergyNetComponent}s within this {@link EnergyNet} instance.
     *
     * @return An immutable {@link Map} of capacitors
     */
    public @Nonnull Map<Location, EnergyNetComponent> getCapacitors() {
        return Collections.unmodifiableMap(capacitors);
    }

    /**
     * This creates an immutable {@link Map} of {@link EnergyNetComponentType#CONSUMER} {@link EnergyNetComponent}s within this {@link EnergyNet} instance.
     *
     * @return An immutable {@link Map} of consumers
     */
    public @Nonnull Map<Location, EnergyNetComponent> getConsumers() {
        return Collections.unmodifiableMap(consumers);
    }

    @Override
    public @Nonnull String getId() {
        return "ENERGY_NETWORK";
    }

    @Override
    public NetworkComponent classifyLocation(@Nonnull Location l) {
        if (regulator.equals(l)) {
            return NetworkComponent.REGULATOR;
        }

        EnergyNetComponent component = getComponent(l);

        if (component == null) {
            return null;
        } else {
            return switch (component.getEnergyComponentType()) {
                case CONNECTOR, CAPACITOR -> NetworkComponent.CONNECTOR;
                case CONSUMER, GENERATOR -> NetworkComponent.TERMINUS;
                default -> null;
            };
        }
    }

    @Override
    public void onClassificationChange(Location l, NetworkComponent from, NetworkComponent to) {
        transportStateDirty = true;
        generators.remove(l);
        capacitors.remove(l);
        consumers.remove(l);

        if (to == null) {
            return;
        }

        EnergyNetComponent component = getComponent(l);

        if (component != null) {
            switch (component.getEnergyComponentType()) {
                case CAPACITOR:
                    capacitors.put(l, component);
                    break;
                case CONSUMER:
                    consumers.put(l, component);
                    break;
                case GENERATOR:
                    if (component instanceof EnergyNetProvider provider) {
                        generators.put(l, provider);
                    } else if (component instanceof SlimefunItem item) {
                        item.warn("This Item is marked as a GENERATOR but does not implement the interface"
                                + " EnergyNetProvider!");
                    }
                    break;
                default:
                    break;
            }
        }
    }

    public void tick(@Nonnull Block b, SlimefunBlockData blockData) {
        var profiler = Slimefun.getProfiler();
        long timestamp = profiler.newEntry();
        boolean profileGenerators = timestamp != 0L;
        generatorProfileNanos = 0L;

        Location regulatorLocation = blockData.getLocation();
        boolean ownsNetwork = regulator.equals(regulatorLocation);

        try {
            if (!ownsNetwork) {
                VanillaPowerStateBridge.sync(regulatorLocation, false);
                if (shouldRefreshHologram(
                        regulatorLocation, HOLOGRAM_MODE_DUPLICATE_REGULATOR, 0L, 0L)) {
                    updateHologram(b, "&4Another regulator detected nearby", blockData::isPendingRemove);
                }

                return;
            }

            capacitorStorageSnapshot.clear();
            generatorStorageSnapshot.clear();

            long phaseTimestamp = profiler.startPhase();
            super.tick();
            profiler.closePhase("EnergyNet", "network discovery", phaseTimestamp);

            if (connectorNodes.isEmpty() && terminusNodes.isEmpty()) {
                phaseTimestamp = profiler.startPhase();
                syncNetworkTransportState(false);
                profiler.closePhase("EnergyNet", "transport state", phaseTimestamp);

                phaseTimestamp = profiler.startPhase();
                if (shouldRefreshHologram(
                        regulatorLocation, HOLOGRAM_MODE_NO_NETWORK, 0L, 0L)) {
                    updateHologram(b, "&4No energy network found", blockData::isPendingRemove);
                }
                profiler.closePhase("EnergyNet", "hologram", phaseTimestamp);
            } else {
                phaseTimestamp = profiler.startPhase();
                long generatorsSupply = tickAllGenerators(profileGenerators);
                profiler.closePhase("EnergyNet", "generators (separately profiled)", phaseTimestamp);

                phaseTimestamp = profiler.startPhase();
                long capacitorsSupply = tickAllCapacitors();
                profiler.closePhase("EnergyNet", "capacitor supply", phaseTimestamp);

                long supply = NumberUtils.flowSafeAddition(generatorsSupply, capacitorsSupply);
                long remainingEnergy = supply;
                long demand = 0;

                phaseTimestamp = profiler.startPhase();
                for (Map.Entry<Location, EnergyNetComponent> entry : consumers.entrySet()) {
                    Location loc = entry.getKey();
                    if (!isEnergyLocationAccessible(loc)) {
                        continue;
                    }

                    var data = StorageCacheUtils.getDataContainer(loc);
                    if (data == null || data.isPendingRemove()) {
                        VanillaPowerStateBridge.sync(loc, false);
                        consumers.remove(loc, entry.getValue());
                        continue;
                    }

                    EnergyNetComponent component = resolveLiveComponent(
                            loc, entry.getValue(), data.getSfId(), EnergyNetComponentType.CONSUMER, consumers);
                    if (component == null) {
                        VanillaPowerStateBridge.sync(loc, false);
                        continue;
                    }

                    if (!data.isDataLoaded()) {
                        StorageCacheUtils.requestLoad(data);
                        continue;
                    }

                    if (!component.isEnergyNetActive(loc, data)) {
                        VanillaPowerStateBridge.sync(loc, false);
                        continue;
                    }

                    long capacity = getSafeCapacity(component, loc);
                    long charge = getSafeCharge(component, loc, data, capacity);
                    long resultingCharge = charge;

                    if (charge < capacity) {
                        long availableSpace = capacity - charge;
                        demand = NumberUtils.flowSafeAddition(demand, availableSpace);

                        if (remainingEnergy > 0) {
                            if (remainingEnergy > availableSpace) {
                                resultingCharge = capacity;
                                setSafeCharge(component, loc, data, resultingCharge, capacity);
                                remainingEnergy -= availableSpace;
                            } else {
                                resultingCharge = Math.min(
                                        NumberUtils.flowSafeAddition(charge, remainingEnergy), capacity);
                                setSafeCharge(component, loc, data, resultingCharge, capacity);
                                remainingEnergy = 0;
                            }
                        }
                    }

                    VanillaPowerStateBridge.sync(loc, resultingCharge > 0);
                }
                profiler.closePhase("EnergyNet", "consumer distribution", phaseTimestamp);

                phaseTimestamp = profiler.startPhase();
                storeRemainingEnergy(remainingEnergy);
                profiler.closePhase("EnergyNet", "remainder storage", phaseTimestamp);

                phaseTimestamp = profiler.startPhase();
                syncNetworkTransportState(supply > 0 && demand > 0);
                profiler.closePhase("EnergyNet", "transport state", phaseTimestamp);

                phaseTimestamp = profiler.startPhase();
                updateHologram(blockData, supply, demand);
                profiler.closePhase("EnergyNet", "hologram", phaseTimestamp);
            }
        } finally {
            if (ownsNetwork) {
                capacitorStorageSnapshot.clear();
                generatorStorageSnapshot.clear();
            }

            if (timestamp != 0L) {
                // Generator timings are added to the start timestamp so they are not reported twice.
                profiler.closeEntry(
                        regulatorLocation,
                        SlimefunItems.ENERGY_REGULATOR.getItem(),
                        timestamp + generatorProfileNanos);
            }
        }
    }

    private void storeRemainingEnergy(long remainingEnergy) {
        remainingEnergy = Math.max(0L, remainingEnergy);

        for (int i = 0; i < capacitorStorageSnapshot.size; i++) {
            Location loc = capacitorStorageSnapshot.locations[i];
            if (!isEnergyLocationAccessible(loc)) {
                continue;
            }

            ASlimefunDataContainer data = capacitorStorageSnapshot.containers[i];
            EnergyNetComponent cached = capacitorStorageSnapshot.components[i];
            if (data.isPendingRemove()) {
                VanillaPowerStateBridge.sync(loc, false);
                capacitors.remove(loc, cached);
                continue;
            }

            if (!data.isDataLoaded()) {
                StorageCacheUtils.requestLoad(data);
                continue;
            }

            EnergyNetComponent component = resolveLiveComponent(
                    loc, cached, data.getSfId(), EnergyNetComponentType.CAPACITOR, capacitors);
            if (component == null) {
                VanillaPowerStateBridge.sync(loc, false);
                continue;
            }

            long capacity = component == cached
                    ? capacitorStorageSnapshot.capacities[i]
                    : getSafeCapacity(component, loc);
            long previousCharge = component == cached
                    ? capacitorStorageSnapshot.charges[i]
                    : getSafeCharge(component, loc, data, capacity);
            long stored = Math.min(remainingEnergy, capacity);
            if (stored != previousCharge) {
                setSafeCharge(component, loc, data, stored, capacity);
            }
            VanillaPowerStateBridge.sync(loc, stored > 0);
            remainingEnergy -= stored;
        }

        for (int i = 0; i < generatorStorageSnapshot.size; i++) {
            Location loc = generatorStorageSnapshot.locations[i];
            if (!isEnergyLocationAccessible(loc)) {
                continue;
            }

            ASlimefunDataContainer data = generatorStorageSnapshot.containers[i];
            EnergyNetProvider cached = (EnergyNetProvider) generatorStorageSnapshot.components[i];
            if (data.isPendingRemove()) {
                VanillaPowerStateBridge.sync(loc, false);
                generators.remove(loc, cached);
                continue;
            }

            if (!data.isDataLoaded()) {
                StorageCacheUtils.requestLoad(data);
                continue;
            }

            EnergyNetProvider component = resolveLiveGenerator(loc, cached, data.getSfId());
            if (component == null) {
                VanillaPowerStateBridge.sync(loc, false);
                continue;
            }

            long capacity = component == cached
                    ? generatorStorageSnapshot.capacities[i]
                    : getSafeCapacity(component, loc);
            long previousCharge = component == cached
                    ? generatorStorageSnapshot.charges[i]
                    : getSafeCharge(component, loc, data, capacity);
            long stored = Math.min(remainingEnergy, capacity);
            if (stored != previousCharge) {
                setSafeCharge(component, loc, data, stored, capacity);
            }
            remainingEnergy -= stored;
        }
    }

    private long tickAllGenerators(boolean profileGenerators) {
        // Explosions/failures are exceptional. Do not allocate a HashSet on every healthy
        // Energy Regulator tick just to prove that nothing needs removing.
        Set<Location> explodedBlocks = null;
        long supply = 0;

        for (Map.Entry<Location, EnergyNetProvider> entry : generators.entrySet()) {
            Location loc = entry.getKey();
            if (!isEnergyLocationAccessible(loc)) {
                continue;
            }

            EnergyNetProvider provider = entry.getValue();
            SlimefunItem item = (SlimefunItem) provider;
            long timestamp = profileGenerators ? Slimefun.getProfiler().newEntry() : 0L;

            try {
                var data = StorageCacheUtils.getDataContainer(loc);
                if (data == null || data.isPendingRemove()) {
                    VanillaPowerStateBridge.sync(loc, false);
                    generators.remove(loc, entry.getValue());
                    continue;
                }

                provider = resolveLiveGenerator(loc, provider, data.getSfId());
                if (provider == null) {
                    VanillaPowerStateBridge.sync(loc, false);
                    continue;
                }
                item = (SlimefunItem) provider;

                if (!data.isDataLoaded()) {
                    StorageCacheUtils.requestLoad(data);
                    continue;
                }

                long energy = Math.max(0L, provider.getGeneratedOutputLong(loc, data));
                long storageCapacity = -1L;
                long storedCharge = 0L;

                if (provider.isChargeable()) {
                    storageCapacity = getSafeCapacity(provider, loc);
                    storedCharge = getSafeCharge(provider, loc, data, storageCapacity);
                    energy = NumberUtils.flowSafeAddition(energy, storedCharge);
                }

                if (provider.willExplode(loc, data)) {
                    VanillaPowerStateBridge.sync(loc, false);
                    if (explodedBlocks == null) {
                        explodedBlocks = new HashSet<>();
                    }
                    explodedBlocks.add(loc);
                    Slimefun.getDatabaseManager().getBlockDataController().removeBlock(loc);

                    Slimefun.runSyncAt(loc, () -> {
                        loc.getBlock().setType(Material.LAVA);
                        loc.getWorld().createExplosion(loc, 0F, false);
                    });
                } else {
                    if (storageCapacity < 0L) {
                        storageCapacity = getSafeCapacity(provider, loc);
                    }
                    generatorStorageSnapshot.add(loc, provider, data, storageCapacity, storedCharge);
                    VanillaPowerStateBridge.sync(loc, energy > 0);
                    supply = NumberUtils.flowSafeAddition(supply, energy);
                }
            } catch (Exception | LinkageError throwable) {
                VanillaPowerStateBridge.sync(loc, false);
                if (explodedBlocks == null) {
                    explodedBlocks = new HashSet<>();
                }
                explodedBlocks.add(loc);
                new ErrorReport<>(throwable, loc, item);
            } finally {
                if (timestamp != 0L) {
                    long time = Slimefun.getProfiler().closeEntry(loc, item, timestamp);
                    generatorProfileNanos += time;
                }
            }
        }

        // Remove all generators which have exploded or failed catastrophically.
        if (explodedBlocks != null) {
            generators.keySet().removeAll(explodedBlocks);
        }

        return supply;
    }

    private long tickAllCapacitors() {
        long supply = 0;

        for (Map.Entry<Location, EnergyNetComponent> entry : capacitors.entrySet()) {
            Location loc = entry.getKey();
            if (!isEnergyLocationAccessible(loc)) {
                continue;
            }

            var data = StorageCacheUtils.getDataContainer(loc);
            if (data == null || data.isPendingRemove()) {
                VanillaPowerStateBridge.sync(loc, false);
                capacitors.remove(loc, entry.getValue());
                continue;
            }

            EnergyNetComponent component = resolveLiveComponent(
                    loc, entry.getValue(), data.getSfId(), EnergyNetComponentType.CAPACITOR, capacitors);
            if (component == null) {
                VanillaPowerStateBridge.sync(loc, false);
                continue;
            }

            if (!data.isDataLoaded()) {
                StorageCacheUtils.requestLoad(data);
                continue;
            }

            long capacity = getSafeCapacity(component, loc);
            long charge = getSafeCharge(component, loc, data, capacity);
            capacitorStorageSnapshot.add(loc, component, data, capacity, charge);
            VanillaPowerStateBridge.sync(loc, charge > 0);
            supply = NumberUtils.flowSafeAddition(supply, charge);
        }

        return supply;
    }

    private void syncNetworkTransportState(boolean powered) {
        long gameTime = regulator.getWorld().getGameTime();
        boolean periodicRevalidation = Math.floorMod(
                        gameTime + regulator.hashCode(), TRANSPORT_STATE_REVALIDATE_INTERVAL)
                == 0;

        if (!transportStateDirty && transportPowered == powered && !periodicRevalidation) {
            return;
        }

        transportStateDirty = false;
        transportPowered = powered;

        if (isEnergyLocationAccessible(regulator)) {
            VanillaPowerStateBridge.sync(regulator, powered);
        }

        for (Location loc : connectorNodes) {
            if (isEnergyLocationAccessible(loc)) {
                VanillaPowerStateBridge.sync(loc, powered);
            }
        }
    }

    private boolean isEnergyLocationAccessible(@Nonnull Location location) {
        return isLocationAccessible(location)
                && location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    private long getSafeCapacity(@Nonnull EnergyNetComponent component, @Nonnull Location loc) {
        try {
            return Math.max(0L, component.getCapacityLong());
        } catch (Exception | LinkageError throwable) {
            reportComponentFailure(component, loc, throwable);
            return 0L;
        }
    }

    private long getSafeCharge(
            @Nonnull EnergyNetComponent component,
            @Nonnull Location loc,
            @Nonnull ASlimefunDataContainer data,
            long capacity) {
        if (capacity <= 0) {
            return 0L;
        }

        try {
            return NumberUtils.clamp(0L, component.getChargeLong(loc, data), capacity);
        } catch (Exception | LinkageError throwable) {
            reportComponentFailure(component, loc, throwable);
            return 0L;
        }
    }

    private void setSafeCharge(
            @Nonnull EnergyNetComponent component,
            @Nonnull Location loc,
            @Nonnull ASlimefunDataContainer data,
            long charge,
            long capacity) {
        long safeCharge = NumberUtils.clamp(0L, charge, capacity);

        try {
            component.setCharge(loc, safeCharge, data);
        } catch (Exception | LinkageError throwable) {
            reportComponentFailure(component, loc, throwable);
        }
    }

    private void reportComponentFailure(
            @Nonnull EnergyNetComponent component, @Nonnull Location loc, @Nonnull Throwable throwable) {
        if (component instanceof SlimefunItem item) {
            new ErrorReport<>(throwable, loc, item);
        }
    }

    @Nullable private EnergyNetComponent resolveLiveComponent(
            @Nonnull Location loc,
            @Nonnull EnergyNetComponent cached,
            @Nullable String sfId,
            @Nonnull EnergyNetComponentType expectedType,
            @Nonnull Map<Location, EnergyNetComponent> cache) {
        if (cached instanceof SlimefunItem item
                && item.getId().equals(sfId)
                && cached.getEnergyComponentType() == expectedType) {
            return cached;
        }

        SlimefunItem liveItem = SlimefunItem.getById(sfId);
        if (liveItem instanceof EnergyNetComponent liveComponent
                && liveComponent.getEnergyComponentType() == expectedType) {
            cache.put(loc, liveComponent);
            return liveComponent;
        }

        cache.remove(loc, cached);
        return null;
    }

    @Nullable private EnergyNetProvider resolveLiveGenerator(
            @Nonnull Location loc, @Nonnull EnergyNetProvider cached, @Nullable String sfId) {
        if (cached instanceof SlimefunItem item
                && item.getId().equals(sfId)
                && cached.getEnergyComponentType() == EnergyNetComponentType.GENERATOR) {
            return cached;
        }

        SlimefunItem liveItem = SlimefunItem.getById(sfId);
        if (liveItem instanceof EnergyNetProvider liveProvider
                && liveProvider.getEnergyComponentType() == EnergyNetComponentType.GENERATOR) {
            generators.put(loc, liveProvider);
            return liveProvider;
        }

        generators.remove(loc, cached);
        return null;
    }

    private void updateHologram(@Nonnull SlimefunBlockData data, long supply, long demand) {
        Location location = data.getLocation();
        if (!shouldRefreshHologram(location, HOLOGRAM_MODE_BALANCE, supply, demand)) {
            return;
        }

        if (demand > supply) {
            String netLoss = NumberUtils.getCompactDouble((double) demand - supply);
            updateHologram(
                    location.getBlock(), "&4&l- &c" + netLoss + " &7J &e\u26A1", data::isPendingRemove);
        } else {
            String netGain = NumberUtils.getCompactDouble((double) supply - demand);
            updateHologram(
                    location.getBlock(), "&2&l+ &a" + netGain + " &7J &e\u26A1", data::isPendingRemove);
        }
    }

    private boolean shouldRefreshHologram(
            @Nonnull Location location, int mode, long supply, long demand) {
        long gameTime = location.getWorld().getGameTime();

        if (hologramMode == mode
                && hologramSupply == supply
                && hologramDemand == demand
                && gameTime < nextHologramRefreshTick) {
            return false;
        }

        hologramMode = mode;
        hologramSupply = supply;
        hologramDemand = demand;
        nextHologramRefreshTick = gameTime + HOLOGRAM_REVALIDATE_INTERVAL_TICKS;
        return true;
    }

    @Nullable private static EnergyNetComponent getComponent(@Nonnull Location l) {
        SlimefunItem item = StorageCacheUtils.getSlimefunItem(l);

        if (item instanceof EnergyNetComponent component) {
            return component;
        }

        return null;
    }

    /**
     * This attempts to get an {@link EnergyNet} from a given {@link Location}.
     * If no suitable {@link EnergyNet} could be found, {@code null} will be returned.
     *
     * @param l
     *            The target {@link Location}
     *
     * @return The {@link EnergyNet} at that {@link Location}, or {@code null}
     */
    @Nullable public static EnergyNet getNetworkFromLocation(@Nonnull Location l) {
        return Slimefun.getNetworkManager()
                .getNetworkFromLocation(l, EnergyNet.class)
                .orElse(null);
    }

    /**
     * This attempts to get an {@link EnergyNet} from a given {@link Location}.
     * If no suitable {@link EnergyNet} could be found, a new one will be created.
     *
     * @param l
     *            The target {@link Location}
     *
     * @return The {@link EnergyNet} at that {@link Location}, or a new one
     */
    @Nonnull
    public static EnergyNet getNetworkFromLocationOrCreate(@Nonnull Location l) {
        Optional<EnergyNet> energyNetwork = Slimefun.getNetworkManager().getNetworkFromLocation(l, EnergyNet.class);

        if (energyNetwork.isPresent()) {
            return energyNetwork.get();
        } else {
            EnergyNet network = new EnergyNet(l);
            Slimefun.getNetworkManager().registerNetwork(network);
            return network;
        }
    }
}
