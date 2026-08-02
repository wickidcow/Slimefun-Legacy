package io.github.thebusybiscuit.slimefun4.api.recipes.machine;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.NamespacedKey;

/** Global registry for addon-defined machine-input fill adapters. */
@SlimefunAPI
public final class MachineInputFillAdapterRegistry {

    private static final Map<NamespacedKey, MachineInputFillAdapter> ADAPTERS = new ConcurrentHashMap<>();

    private MachineInputFillAdapterRegistry() {}

    /**
     * Registers or replaces an adapter using the same namespaced key.
     *
     * @return the previous adapter using this key, or {@code null}
     */
    public static @Nullable MachineInputFillAdapter register(@Nonnull MachineInputFillAdapter adapter) {
        Objects.requireNonNull(adapter, "adapter");
        NamespacedKey key = Objects.requireNonNull(adapter.getKey(), "adapter key");
        return ADAPTERS.put(key, adapter);
    }

    /**
     * Removes a registered adapter.
     *
     * @return the removed adapter, or {@code null}
     */
    public static @Nullable MachineInputFillAdapter unregister(@Nonnull NamespacedKey key) {
        return ADAPTERS.remove(Objects.requireNonNull(key, "key"));
    }

    /** Returns a stable priority-ordered snapshot of registered adapters. */
    @Nonnull
    public static List<MachineInputFillAdapter> getAdapters() {
        List<MachineInputFillAdapter> adapters = new ArrayList<>(ADAPTERS.values());
        adapters.sort(Comparator.comparingInt(MachineInputFillAdapter::getPriority)
                .reversed()
                .thenComparing(adapter -> adapter.getKey().toString()));
        return List.copyOf(adapters);
    }
}
