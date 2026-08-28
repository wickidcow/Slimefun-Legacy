package io.github.thebusybiscuit.slimefun4.implementation.items.autocrafters;

import java.util.Map;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.inventory.ItemStack;

/**
 * Internal vanilla-inventory matching helper for {@link AbstractAutoCrafter}.
 *
 * <p>The synchronized auto-crafter tick cannot observe an inventory mutation in the middle of one
 * {@code matchRecipe} call, so vanilla inventory contents can be snapshotted once and reused for all
 * ingredient predicates. Keeping the predicate call in this package preserves subclass overrides of
 * {@link AbstractAutoCrafter#matches(ItemStack, Predicate)}.
 */
public final class AutoCrafterInventoryMatcher {

    private AutoCrafterInventoryMatcher() {}

    /**
     * Reserves one matching item from a previously captured inventory snapshot.
     *
     * @param crafter the auto-crafter performing the match
     * @param contents the inventory snapshot for this recipe attempt
     * @param itemQuantities per-slot remaining quantities after local reservations
     * @param predicate the ingredient predicate to satisfy
     * @return whether one item could be reserved for this predicate
     */
    @ParametersAreNonnullByDefault
    public static boolean matchesAny(
            AbstractAutoCrafter crafter,
            ItemStack[] contents,
            Map<Integer, Integer> itemQuantities,
            Predicate<ItemStack> predicate) {
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null) {
                continue;
            }

            int amount = itemQuantities.getOrDefault(slot, item.getAmount());
            if (amount > 0 && crafter.matches(item, predicate)) {
                itemQuantities.put(slot, amount - 1);
                return true;
            }
        }

        return false;
    }

    /**
     * Captures one snapshot for callers that need to share it across multiple predicate matches.
     *
     * @param contents the contents returned by a Bukkit inventory
     * @return the same non-null snapshot reference
     */
    @Nonnull
    public static ItemStack[] snapshot(@Nonnull ItemStack[] contents) {
        return contents;
    }
}
