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
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;
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

    private final Map<Location, EnergyNetProvider> generators = new ConcurrentHashMap<>();
    private final Map<Location, EnergyNetComponent> capacitors = new ConcurrentHashMap<>();
    private final Map<Location, EnergyNetComponent> consumers = new ConcurrentHashMap<>();

    protected EnergyNet(@Nonnull Location l) {
        super(Slimefun.getNetworkManager(), l);
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
        AtomicLong timestamp = new AtomicLong(Slimefun.getProfiler().newEntry());
        try {
            if (!regulator.equals(b.getLocation())) {
                VanillaPowerStateBridge.sync(b.getLocation(), false);
                updateHologram(b, "&4Another regulator detected nearby", blockData::isPendingRemove);

                return;
            }

            super.tick();

            if (connectorNodes.isEmpty() && terminusNodes.isEmpty()) {
                syncNetworkTransportState(false);
                updateHologram(b, "&4No energy network found", blockData::isPendingRemove);
            } else {
                long generatorsSupply = tickAllGenerators(timestamp::getAndAdd);
                long capacitorsSupply = tickAllCapacitors();
                long supply = NumberUtils.flowSafeAddition(generatorsSupply, capacitorsSupply);
                long remainingEnergy = supply;
                long demand = 0;

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

                storeRemainingEnergy(remainingEnergy);
                syncNetworkTransportState(supply > 0 && demand > 0);
                updateHologram(blockData, supply, demand);
            }
        } finally {
            // We have subtracted the timings from Generators, so they do not show up twice.
            Slimefun.getProfiler()
                    .closeEntry(b.getLocation(), SlimefunItems.ENERGY_REGULATOR.getItem(), timestamp.get());
        }
    }

    private void storeRemainingEnergy(long remainingEnergy) {
        remainingEnergy = Math.max(0L, remainingEnergy);

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
            long stored = Math.min(remainingEnergy, capacity);
            setSafeCharge(component, loc, data, stored, capacity);
            VanillaPowerStateBridge.sync(loc, stored > 0);
            remainingEnergy -= stored;
        }

        for (Map.Entry<Location, EnergyNetProvider> entry : generators.entrySet()) {
            Location loc = entry.getKey();
            if (!isEnergyLocationAccessible(loc)) {
                continue;
            }

            var data = StorageCacheUtils.getDataContainer(loc);
            if (data == null || data.isPendingRemove()) {
                VanillaPowerStateBridge.sync(loc, false);
                generators.remove(loc, entry.getValue());
                continue;
            }

            EnergyNetProvider component = resolveLiveGenerator(loc, entry.getValue(), data.getSfId());
            if (component == null) {
                VanillaPowerStateBridge.sync(loc, false);
                continue;
            }

            if (!data.isDataLoaded()) {
                StorageCacheUtils.requestLoad(data);
                continue;
            }

            long capacity = getSafeCapacity(component, loc);
            long stored = Math.min(remainingEnergy, capacity);
            setSafeCharge(component, loc, data, stored, capacity);
            remainingEnergy -= stored;
        }
    }

    private long tickAllGenerators(@Nonnull LongConsumer timings) {
        Set<Location> explodedBlocks = new HashSet<>();
        long supply = 0;

        for (Map.Entry<Location, EnergyNetProvider> entry : generators.entrySet()) {
            Location loc = entry.getKey();
            if (!isEnergyLocationAccessible(loc)) {
                continue;
            }

            long timestamp = Slimefun.getProfiler().newEntry();
            EnergyNetProvider provider = entry.getValue();
            SlimefunItem item = (SlimefunItem) provider;

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

                if (provider.isChargeable()) {
                    long capacity = getSafeCapacity(provider, loc);
                    long storedCharge = getSafeCharge(provider, loc, data, capacity);
                    energy = NumberUtils.flowSafeAddition(energy, storedCharge);
                }

                if (provider.willExplode(loc, data)) {
                    VanillaPowerStateBridge.sync(loc, false);
                    explodedBlocks.add(loc);
                    Slimefun.getDatabaseManager().getBlockDataController().removeBlock(loc);

                    Slimefun.runSyncAt(loc, () -> {
                        loc.getBlock().setType(Material.LAVA);
                        loc.getWorld().createExplosion(loc, 0F, false);
                    });
                } else {
                    VanillaPowerStateBridge.sync(loc, energy > 0);
                    supply = NumberUtils.flowSafeAddition(supply, energy);
                }
            } catch (Exception | LinkageError throwable) {
                VanillaPowerStateBridge.sync(loc, false);
                explodedBlocks.add(loc);
                new ErrorReport<>(throwable, loc, item);
            }

            long time = Slimefun.getProfiler().closeEntry(loc, item, timestamp);
            timings.accept(time);
        }

        // Remove all generators which have exploded or failed catastrophically.
        if (!explodedBlocks.isEmpty()) {
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
            VanillaPowerStateBridge.sync(loc, charge > 0);
            supply = NumberUtils.flowSafeAddition(supply, charge);
        }

        return supply;
    }

    private void syncNetworkTransportState(boolean powered) {
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

    private void updateHologram(@Nonnull SlimefunBlockData data, double supply, double demand) {
        if (demand > supply) {
            String netLoss = NumberUtils.getCompactDouble(demand - supply);
            updateHologram(
                    data.getLocation().getBlock(), "&4&l- &c" + netLoss + " &7J &e\u26A1", data::isPendingRemove);
        } else {
            String netGain = NumberUtils.getCompactDouble(supply - demand);
            updateHologram(
                    data.getLocation().getBlock(), "&2&l+ &a" + netGain + " &7J &e\u26A1", data::isPendingRemove);
        }
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
