package io.github.thebusybiscuit.slimefun4.api.recipes.machine;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Represents one ingredient in a machine recipe.
 *
 * <p>An ingredient may expose multiple valid choices. The amount stored on each choice is the amount required by the
 * recipe when that choice is used.
 */
@SlimefunAPI
public final class MachineRecipeIngredient {

    private final List<ItemStack> choices;

    public MachineRecipeIngredient(@Nonnull List<ItemStack> choices) {
        Objects.requireNonNull(choices, "choices");

        List<ItemStack> sanitized = new ArrayList<>(choices.size());
        for (ItemStack choice : choices) {
            if (choice != null && choice.getType() != Material.AIR && choice.getAmount() > 0) {
                sanitized.add(choice.clone());
            }
        }

        if (sanitized.isEmpty()) {
            throw new IllegalArgumentException("A machine recipe ingredient must contain at least one valid choice");
        }

        this.choices = Collections.unmodifiableList(sanitized);
    }

    @Nonnull
    public static MachineRecipeIngredient of(@Nonnull ItemStack choice) {
        return new MachineRecipeIngredient(List.of(Objects.requireNonNull(choice, "choice")));
    }

    @Nonnull
    public List<ItemStack> getChoices() {
        List<ItemStack> copies = new ArrayList<>(choices.size());
        for (ItemStack choice : choices) {
            copies.add(choice.clone());
        }
        return Collections.unmodifiableList(copies);
    }
}
