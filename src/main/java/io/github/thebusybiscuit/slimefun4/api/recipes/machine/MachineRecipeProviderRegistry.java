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

/**
 * Global registry for addon-facing machine recipe providers.
 */
@SlimefunAPI
public final class MachineRecipeProviderRegistry {

    private static final Map<NamespacedKey, MachineRecipeProvider> PROVIDERS = new ConcurrentHashMap<>();

    private MachineRecipeProviderRegistry() {}

    /**
     * Registers or replaces a provider with the same namespaced key.
     *
     * @param provider the provider to register
     * @return the previous provider using this key, or {@code null}
     */
    public static @Nullable MachineRecipeProvider register(@Nonnull MachineRecipeProvider provider) {
        Objects.requireNonNull(provider, "provider");
        NamespacedKey key = Objects.requireNonNull(provider.getKey(), "provider key");
        return PROVIDERS.put(key, provider);
    }

    /**
     * Removes a provider.
     *
     * @param key provider key
     * @return the removed provider, or {@code null}
     */
    public static @Nullable MachineRecipeProvider unregister(@Nonnull NamespacedKey key) {
        return PROVIDERS.remove(Objects.requireNonNull(key, "key"));
    }

    /**
     * Returns a stable priority-ordered snapshot of the registered providers.
     */
    @Nonnull
    public static List<MachineRecipeProvider> getProviders() {
        List<MachineRecipeProvider> providers = new ArrayList<>(PROVIDERS.values());
        providers.sort(Comparator.comparingInt(MachineRecipeProvider::getPriority)
                .reversed()
                .thenComparing(provider -> provider.getKey().toString()));
        return List.copyOf(providers);
    }
}
