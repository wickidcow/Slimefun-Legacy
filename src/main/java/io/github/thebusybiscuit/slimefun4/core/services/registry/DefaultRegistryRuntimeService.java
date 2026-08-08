package io.github.thebusybiscuit.slimefun4.core.services.registry;

import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunInternal;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.registry.AddonRegistrySnapshot;
import io.github.thebusybiscuit.slimefun4.api.registry.RegistryRuntimeService;
import io.github.thebusybiscuit.slimefun4.api.registry.RegistryRuntimeSnapshot;
import io.github.thebusybiscuit.slimefun4.core.SlimefunRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nonnull;
import org.bukkit.plugin.Plugin;

/** Internal read-only registry observer. It never mutates registered Slimefun content. */
@SlimefunInternal
public final class DefaultRegistryRuntimeService implements RegistryRuntimeService {

    private final SlimefunRegistry registry;
    private final AtomicBoolean finalized = new AtomicBoolean();
    private volatile long finalizedAtMillis;
    private volatile int finalizedItemCount;

    public DefaultRegistryRuntimeService(@Nonnull SlimefunRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public void markInitialRegistrationFinalized() {
        if (finalized.compareAndSet(false, true)) {
            finalizedItemCount = registry.getAllSlimefunItems().size();
            finalizedAtMillis = System.currentTimeMillis();
        }
    }

    @Override
    public @Nonnull RegistryRuntimeSnapshot getSnapshot() {
        int totalItems = registry.getAllSlimefunItems().size();
        int enabledItems = registry.getEnabledSlimefunItems().size();
        int disabledItems = registry.getDisabledSlimefunItems().size();
        int runtimeItems = finalized.get() ? Math.max(0, totalItems - finalizedItemCount) : 0;
        return new RegistryRuntimeSnapshot(
                finalized.get(),
                finalizedAtMillis,
                finalizedItemCount,
                runtimeItems,
                totalItems,
                enabledItems,
                disabledItems,
                registry.getAllItemGroups().size(),
                registry.getResearches().size(),
                registry.getTickerBlocks().size(),
                getAddonSnapshots().size());
    }

    @Override
    public @Nonnull List<AddonRegistrySnapshot> getAddonSnapshots() {
        Map<String, MutableAddonCounts> counts = new LinkedHashMap<>();
        var enabledItems = new HashSet<>(registry.getEnabledSlimefunItems());

        for (SlimefunItem item : new ArrayList<>(registry.getAllSlimefunItems())) {
            Plugin plugin = plugin(item);
            if (plugin == null) {
                continue;
            }
            MutableAddonCounts entry = counts.computeIfAbsent(key(plugin), ignored -> new MutableAddonCounts(plugin));
            entry.totalItems++;
            if (enabledItems.contains(item)) {
                entry.enabledItems++;
            }
            if (item.isDisabled()) {
                entry.disabledItems++;
            }
            if (item.getBlockTicker() != null) {
                entry.tickingItems++;
            }
        }

        for (ItemGroup group : new ArrayList<>(registry.getAllItemGroups())) {
            SlimefunAddon addon = group.getAddon();
            Plugin plugin = addon == null ? null : addon.getJavaPlugin();
            if (plugin != null) {
                counts.computeIfAbsent(key(plugin), ignored -> new MutableAddonCounts(plugin)).itemGroups++;
            }
        }

        return counts.values().stream()
                .map(MutableAddonCounts::snapshot)
                .sorted(Comparator.comparing(AddonRegistrySnapshot::getPluginName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static Plugin plugin(SlimefunItem item) {
        try {
            SlimefunAddon addon = item.getAddon();
            return addon == null ? null : addon.getJavaPlugin();
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static String key(Plugin plugin) {
        return plugin.getName().toLowerCase(Locale.ROOT);
    }

    private static final class MutableAddonCounts {
        private final Plugin plugin;
        private int totalItems;
        private int enabledItems;
        private int disabledItems;
        private int itemGroups;
        private int tickingItems;

        private MutableAddonCounts(Plugin plugin) {
            this.plugin = plugin;
        }

        private AddonRegistrySnapshot snapshot() {
            return new AddonRegistrySnapshot(
                    plugin.getName(),
                    plugin.getDescription().getVersion(),
                    totalItems,
                    enabledItems,
                    disabledItems,
                    itemGroups,
                    tickingItems);
        }
    }
}
