package io.github.thebusybiscuit.slimefun4.api.recipes.machine;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import java.util.List;
import javax.annotation.Nonnull;
import org.bukkit.NamespacedKey;
import org.bukkit.World;

/**
 * Supplies normalized machine recipes for one or more {@link SlimefunItem} implementations.
 *
 * <p>Addons can register a provider through {@link MachineRecipeProviderRegistry}. Providers with a higher priority
 * are checked first. The first provider that both supports an item and returns recipes is used by the enhanced guide.
 */
@SlimefunAPI
public interface MachineRecipeProvider {

    @Nonnull
    NamespacedKey getKey();

    default int getPriority() {
        return 0;
    }

    boolean supports(@Nonnull SlimefunItem item);

    @Nonnull
    List<MachineRecipeDisplay> getRecipes(@Nonnull SlimefunItem item, @Nonnull World world);
}
