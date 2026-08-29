package io.github.thebusybiscuit.slimefun4.implementation.items.autocrafters;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.Map;
import java.util.function.Predicate;
import javax.annotation.Nullable;
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

    private static final byte MATCH_MODE_UNKNOWN = 0;
    private static final byte MATCH_MODE_DIRECT = 1;
    private static final byte MATCH_MODE_VIRTUAL = 2;

    private AutoCrafterInventoryMatcher() {}

    /**
     * Creates a per-slot virtual-item resolution cache for the exact core Enhanced Auto Crafter.
     *
     * <p>Addon subclasses are deliberately excluded because they may override matching semantics.
     * A null return value means the caller must use the normal crafter matching path.</p>
     *
     * @param crafter the auto-crafter performing the recipe attempt
     * @param inventorySize number of slots in the captured inventory snapshot
     * @return a per-slot match-mode cache, or null when the fast path is not safe
     */
    @ParametersAreNonnullByDefault
    public static @Nullable byte[] createMatchModeCache(AbstractAutoCrafter crafter, int inventorySize) {
        return crafter.getClass() == EnhancedAutoCrafter.class ? new byte[inventorySize] : null;
    }

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
        return matchesAny(crafter, contents, itemQuantities, predicate, null);
    }

    /**
     * Reserves one matching item while optionally reusing per-slot virtual-item resolution.
     *
     * <p>For the exact core Enhanced Auto Crafter, normal stacks are resolved once and then use
     * {@link Predicate#test(Object)} directly. This is equivalent to the fallback path in
     * ItemStackService#matchesPredicate after it determines that no virtual handler claims the stack.
     * Virtual stacks continue through {@link AbstractAutoCrafter#matches(ItemStack, Predicate)}.</p>
     *
     * @param crafter the auto-crafter performing the match
     * @param contents the inventory snapshot for this recipe attempt
     * @param itemQuantities per-slot remaining quantities after local reservations
     * @param predicate the ingredient predicate to satisfy
     * @param matchModes optional per-slot virtual-item resolution cache
     * @return whether one item could be reserved for this predicate
     */
    @ParametersAreNonnullByDefault
    public static boolean matchesAny(
            AbstractAutoCrafter crafter,
            ItemStack[] contents,
            Map<Integer, Integer> itemQuantities,
            Predicate<ItemStack> predicate,
            @Nullable byte[] matchModes) {
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null) {
                continue;
            }

            int amount = itemQuantities.getOrDefault(slot, item.getAmount());
            if (amount <= 0) {
                continue;
            }

            boolean matches;
            if (matchModes != null) {
                byte mode = matchModes[slot];
                if (mode == MATCH_MODE_UNKNOWN) {
                    mode = Slimefun.getItemStackService().isVirtualItem(item)
                            ? MATCH_MODE_VIRTUAL
                            : MATCH_MODE_DIRECT;
                    matchModes[slot] = mode;
                }
                matches = mode == MATCH_MODE_DIRECT ? predicate.test(item) : crafter.matches(item, predicate);
            } else {
                matches = crafter.matches(item, predicate);
            }

            if (matches) {
                itemQuantities.put(slot, amount - 1);
                return true;
            }
        }

        return false;
    }
}
