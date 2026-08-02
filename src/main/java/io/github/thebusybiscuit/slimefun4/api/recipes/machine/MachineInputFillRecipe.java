package io.github.thebusybiscuit.slimefun4.api.recipes.machine;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Describes one authoritative machine-input transfer resolved by a {@link MachineInputFillAdapter}.
 *
 * <p>The enhanced guide only writes to {@link #getInputSlots()}. Protected slots are supplied so Legacy can reject
 * malformed adapters whose input layout overlaps outputs, controls, upgrades or progress indicators.
 */
@SlimefunAPI
public final class MachineInputFillRecipe {

    /** Use the server-wide maximum from {@code enhanced-guide.yml}. */
    public static final int USE_SERVER_MAXIMUM = -1;

    private final List<ItemStack> ingredients;
    private final int[] inputSlots;
    private final int[] protectedSlots;
    private final boolean maximumFillAllowed;
    private final int maximumSets;
    private final String label;

    public MachineInputFillRecipe(
            @Nonnull List<ItemStack> ingredients,
            @Nonnull int[] inputSlots,
            @Nonnull int[] protectedSlots,
            boolean maximumFillAllowed,
            int maximumSets,
            @Nonnull String label) {
        Objects.requireNonNull(ingredients, "ingredients");
        Objects.requireNonNull(inputSlots, "inputSlots");
        Objects.requireNonNull(protectedSlots, "protectedSlots");
        this.label = Objects.requireNonNull(label, "label");

        if (ingredients.isEmpty()) {
            throw new IllegalArgumentException("A machine input fill recipe must contain at least one ingredient");
        }

        List<ItemStack> copies = new ArrayList<>(ingredients.size());
        for (ItemStack ingredient : ingredients) {
            if (ingredient == null || ingredient.getType() == Material.AIR || ingredient.getAmount() <= 0) {
                throw new IllegalArgumentException("Machine input fill ingredients must be concrete non-empty items");
            }
            copies.add(ingredient.clone());
        }
        if (inputSlots.length == 0) {
            throw new IllegalArgumentException("A machine input fill recipe must expose at least one input slot");
        }
        if (maximumSets != USE_SERVER_MAXIMUM && maximumSets < 1) {
            throw new IllegalArgumentException("maximumSets must be -1 or greater than zero");
        }

        this.ingredients = Collections.unmodifiableList(copies);
        this.inputSlots = inputSlots.clone();
        this.protectedSlots = protectedSlots.clone();
        this.maximumFillAllowed = maximumFillAllowed;
        this.maximumSets = maximumSets;
    }

    @Nonnull
    public List<ItemStack> getIngredients() {
        List<ItemStack> copies = new ArrayList<>(ingredients.size());
        for (ItemStack ingredient : ingredients) {
            copies.add(ingredient.clone());
        }
        return Collections.unmodifiableList(copies);
    }

    @Nonnull
    public int[] getInputSlots() {
        return inputSlots.clone();
    }

    @Nonnull
    public int[] getProtectedSlots() {
        return protectedSlots.clone();
    }

    public boolean isMaximumFillAllowed() {
        return maximumFillAllowed;
    }

    public int getMaximumSets() {
        return maximumSets;
    }

    @Nonnull
    public String getLabel() {
        return label;
    }

    /**
     * Applies this adapter's optional limit to the configured server maximum.
     *
     * @param serverMaximum server-configured upper bound
     * @return a safe positive maximum number of sets
     */
    public int resolveMaximumSets(@Nonnegative int serverMaximum) {
        int safeServerMaximum = Math.max(1, serverMaximum);
        if (!maximumFillAllowed) {
            return 1;
        }
        return maximumSets == USE_SERVER_MAXIMUM ? safeServerMaximum : Math.min(safeServerMaximum, maximumSets);
    }

    @Nonnull
    public static Builder builder() {
        return new Builder();
    }

    /** Builder used by addon adapters. */
    @SlimefunAPI
    public static final class Builder {

        private final List<ItemStack> ingredients = new ArrayList<>();
        private int[] inputSlots = new int[0];
        private int[] protectedSlots = new int[0];
        private boolean maximumFillAllowed = true;
        private int maximumSets = USE_SERVER_MAXIMUM;
        private String label = "Custom machine adapter";

        @Nonnull
        public Builder addIngredient(@Nonnull ItemStack ingredient) {
            ingredients.add(Objects.requireNonNull(ingredient, "ingredient"));
            return this;
        }

        @Nonnull
        public Builder ingredients(@Nonnull List<ItemStack> ingredients) {
            Objects.requireNonNull(ingredients, "ingredients");
            this.ingredients.clear();
            this.ingredients.addAll(ingredients);
            return this;
        }

        @Nonnull
        public Builder inputSlots(@Nonnull int... inputSlots) {
            this.inputSlots = Objects.requireNonNull(inputSlots, "inputSlots").clone();
            return this;
        }

        @Nonnull
        public Builder protectedSlots(@Nonnull int... protectedSlots) {
            this.protectedSlots = Objects.requireNonNull(protectedSlots, "protectedSlots").clone();
            return this;
        }

        @Nonnull
        public Builder allowMaximumFill(boolean maximumFillAllowed) {
            this.maximumFillAllowed = maximumFillAllowed;
            return this;
        }

        @Nonnull
        public Builder maximumSets(int maximumSets) {
            this.maximumSets = maximumSets;
            return this;
        }

        @Nonnull
        public Builder label(@Nonnull String label) {
            this.label = Objects.requireNonNull(label, "label");
            return this;
        }

        @Nonnull
        public MachineInputFillRecipe build() {
            return new MachineInputFillRecipe(
                    ingredients,
                    Arrays.copyOf(inputSlots, inputSlots.length),
                    Arrays.copyOf(protectedSlots, protectedSlots.length),
                    maximumFillAllowed,
                    maximumSets,
                    label);
        }
    }
}
