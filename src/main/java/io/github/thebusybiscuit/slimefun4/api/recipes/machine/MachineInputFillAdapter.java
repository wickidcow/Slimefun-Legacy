package io.github.thebusybiscuit.slimefun4.api.recipes.machine;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Adapter for machines whose real recipe storage or inventory layout is not represented by the standard
 * {@code AContainer#getMachineRecipes()} contract.
 *
 * <p>Adapters resolve a guide recipe into concrete ingredients and input slots. Slimefun Legacy retains ownership of
 * protection checks, region-thread checks, transaction simulation, rollback and inventory commits.
 */
@SlimefunAPI
public interface MachineInputFillAdapter {

    @Nonnull
    NamespacedKey getKey();

    /** Player-facing adapter description used by the Enhanced Guide. */
    @Nonnull
    default String getDisplayName() {
        return getKey().getKey();
    }

    default int getPriority() {
        return 0;
    }

    boolean supports(@Nonnull SlimefunItem machine);

    /**
     * Returns whether this adapter can authoritatively match the displayed recipe.
     *
     * <p>This method must not assume that the first alternative of every ingredient is selected.
     */
    boolean supportsRecipe(@Nonnull SlimefunItem machine, @Nonnull MachineRecipeDisplay recipe);

    /**
     * Resolves the currently selected ingredient alternatives into an authoritative transfer definition.
     *
     * @return a transfer definition, or {@code null} when the selected alternatives do not match a real recipe
     */
    @Nullable MachineInputFillRecipe resolve(
            @Nonnull SlimefunItem machine, @Nonnull MachineRecipeDisplay recipe, @Nonnull int[] selectedAlternatives);

    /**
     * Verifies that the placed machine is the correct target. The default requires the exact Slimefun item ID.
     */
    default boolean isValidTarget(@Nonnull SlimefunItem guideMachine, @Nonnull SlimefunItem placedMachine) {
        return guideMachine.getId().equals(placedMachine.getId());
    }

    /**
     * Final adapter-specific safety check, called after protection and region ownership checks.
     */
    default boolean isSafeToFill(
            @Nonnull Player player, @Nonnull SlimefunItem machine, @Nonnull Block target, @Nonnull BlockMenu menu) {
        return true;
    }
}
