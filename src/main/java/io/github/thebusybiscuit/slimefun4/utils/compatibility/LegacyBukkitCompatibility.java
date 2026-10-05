package io.github.thebusybiscuit.slimefun4.utils.compatibility;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunInternal;
import javax.annotation.Nonnull;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;

/**
 * Narrow compatibility boundary for Bukkit APIs that are deprecated without a behavior-equivalent generic
 * replacement across Slimefun Legacy's supported runtime range.
 *
 * <p>Keep calls here only when replacing the deprecated API would change established gameplay or lose information.
 * Production callers should prefer modern APIs everywhere else.
 */
@SlimefunInternal
public final class LegacyBukkitCompatibility {

    private LegacyBukkitCompatibility() {}

    /**
     * Preserves Bukkit's historical material-level interaction classification.
     *
     * <p>Bukkit deprecated this classification because it is not comprehensive, but does not provide a replacement
     * that answers the same question. Slimefun's legacy food/flask behavior depends on this exact classification.
     */
    @SuppressWarnings("deprecation")
    public static boolean isInteractable(@Nonnull Material material) {
        return material.isInteractable();
    }

    /**
     * Returns the representative stack supplied by a generic {@link RecipeChoice}.
     *
     * <p>Exact choices expose the same first-stack clone through their modern accessor. Other choice types still
     * need the generic fallback, including MaterialChoice's historical wildcard durability and predicate choices.
     */
    @SuppressWarnings("deprecation")
    public static @Nonnull ItemStack getRecipeChoiceRepresentative(@Nonnull RecipeChoice choice) {
        // Older supported APIs may allow subclasses with a custom representative.
        // Use the modern path only for the concrete Bukkit exact-choice implementation.
        if (choice.getClass() == RecipeChoice.ExactChoice.class) {
            return ((RecipeChoice.ExactChoice) choice).getChoices().getFirst().clone();
        }
        return choice.getItemStack();
    }
}
