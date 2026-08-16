package io.github.thebusybiscuit.slimefun4.implementation.operations;

import io.github.bakedlibs.dough.blocks.BlockPosition;
import io.github.thebusybiscuit.slimefun4.core.machines.MachineOperation;
import javax.annotation.Nonnull;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineRecipe;
import org.apache.commons.lang.Validate;
import org.bukkit.inventory.ItemStack;

/**
 * This {@link MachineOperation} represents a crafting process.
 *
 * @author TheBusyBiscuit
 *
 */
public class CraftingOperation implements MachineOperation {

    private final ItemStack[] ingredients;
    private final ItemStack[] results;

    private final int totalTicks;
    private int currentTicks = 0;
    private volatile boolean cancelled;

    public CraftingOperation(@Nonnull MachineRecipe recipe) {
        this(recipe.getInput(), recipe.getOutput(), recipe.getTicks());
    }

    public CraftingOperation(@Nonnull ItemStack[] ingredients, @Nonnull ItemStack[] results, int totalTicks) {
        Validate.notEmpty(ingredients, "The Ingredients array cannot be empty or null");
        Validate.notEmpty(results, "The results array cannot be empty or null");
        Validate.isTrue(
                totalTicks >= 0,
                "The amount of total ticks must be a positive integer or zero, received: " + totalTicks);

        this.ingredients = snapshot(ingredients);
        this.results = snapshot(results);
        this.totalTicks = totalTicks;
    }

    private static ItemStack[] snapshot(ItemStack[] stacks) {
        ItemStack[] snapshot = new ItemStack[stacks.length];

        for (int i = 0; i < stacks.length; i++) {
            snapshot[i] = stacks[i] == null ? null : stacks[i].clone();
        }

        return snapshot;
    }

    @Override
    public void addProgress(int num) {
        Validate.isTrue(num > 0, "Progress must be positive.");
        currentTicks += num;
    }

    @Nonnull
    public ItemStack[] getIngredients() {
        return ingredients;
    }

    @Nonnull
    public ItemStack[] getResults() {
        return results;
    }

    @Override
    public int getProgress() {
        return currentTicks;
    }

    @Override
    public int getTotalTicks() {
        return totalTicks;
    }

    @Override
    public void onCancel(BlockPosition position) {
        cancelled = true;
    }

    /**
     * Returns whether this operation was removed from its processor before it completed.
     *
     * @return whether the operation was cancelled
     */
    public boolean isCancelled() {
        return cancelled;
    }
}
