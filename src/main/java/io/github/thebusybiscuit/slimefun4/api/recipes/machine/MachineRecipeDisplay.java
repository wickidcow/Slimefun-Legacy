package io.github.thebusybiscuit.slimefun4.api.recipes.machine;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * A normalized, read-only representation of a machine recipe for guide display.
 *
 * <p>This model intentionally does not execute recipes or move inventory contents. It only describes what a machine
 * accepts and produces so guide implementations can render the information safely.
 */
@SlimefunAPI
public final class MachineRecipeDisplay {

    public static final int UNKNOWN_PROCESSING_TICKS = -1;
    public static final long UNKNOWN_ENERGY_PER_TICK = -1L;

    private final List<MachineRecipeIngredient> inputs;
    private final List<ItemStack> outputs;
    private final MachineRecipeLayout layout;
    private final int processingTicks;
    private final long energyPerTick;
    private final String label;

    public MachineRecipeDisplay(
            @Nonnull List<MachineRecipeIngredient> inputs,
            @Nonnull List<ItemStack> outputs,
            @Nonnull MachineRecipeLayout layout,
            int processingTicks,
            long energyPerTick,
            @Nonnull String label) {
        Objects.requireNonNull(inputs, "inputs");
        Objects.requireNonNull(outputs, "outputs");
        this.layout = Objects.requireNonNull(layout, "layout");
        this.label = Objects.requireNonNull(label, "label");

        List<MachineRecipeIngredient> inputCopies = new ArrayList<>(inputs.size());
        for (MachineRecipeIngredient input : inputs) {
            if (input != null) {
                inputCopies.add(new MachineRecipeIngredient(input.getChoices()));
            }
        }

        List<ItemStack> outputCopies = new ArrayList<>(outputs.size());
        for (ItemStack output : outputs) {
            if (output != null && output.getType() != Material.AIR && output.getAmount() > 0) {
                outputCopies.add(output.clone());
            }
        }

        if (outputCopies.isEmpty()) {
            throw new IllegalArgumentException("A machine recipe display must contain at least one output");
        }
        if (processingTicks < UNKNOWN_PROCESSING_TICKS) {
            throw new IllegalArgumentException("processingTicks must be -1 or greater");
        }
        if (energyPerTick < UNKNOWN_ENERGY_PER_TICK) {
            throw new IllegalArgumentException("energyPerTick must be -1 or greater");
        }

        this.inputs = Collections.unmodifiableList(inputCopies);
        this.outputs = Collections.unmodifiableList(outputCopies);
        this.processingTicks = processingTicks;
        this.energyPerTick = energyPerTick;
    }

    public MachineRecipeDisplay(@Nonnull List<MachineRecipeIngredient> inputs, @Nonnull List<ItemStack> outputs) {
        this(inputs, outputs, MachineRecipeLayout.UNSPECIFIED, UNKNOWN_PROCESSING_TICKS, UNKNOWN_ENERGY_PER_TICK, "");
    }

    @Nonnull
    public List<MachineRecipeIngredient> getInputs() {
        List<MachineRecipeIngredient> copies = new ArrayList<>(inputs.size());
        for (MachineRecipeIngredient input : inputs) {
            copies.add(new MachineRecipeIngredient(input.getChoices()));
        }
        return Collections.unmodifiableList(copies);
    }

    @Nonnull
    public List<ItemStack> getOutputs() {
        List<ItemStack> copies = new ArrayList<>(outputs.size());
        for (ItemStack output : outputs) {
            copies.add(output.clone());
        }
        return Collections.unmodifiableList(copies);
    }

    @Nonnull
    public MachineRecipeLayout getLayout() {
        return layout;
    }

    public int getProcessingTicks() {
        return processingTicks;
    }

    public long getEnergyPerTick() {
        return energyPerTick;
    }

    @Nonnull
    public String getLabel() {
        return label;
    }

    public boolean hasKnownProcessingTime() {
        return processingTicks >= 0;
    }

    public boolean hasKnownEnergyUse() {
        return energyPerTick >= 0;
    }

    @Nonnull
    public static Builder builder() {
        return new Builder();
    }

    /** Builder for addon and compatibility providers. */
    @SlimefunAPI
    public static final class Builder {

        private final List<MachineRecipeIngredient> inputs = new ArrayList<>();
        private final List<ItemStack> outputs = new ArrayList<>();
        private MachineRecipeLayout layout = MachineRecipeLayout.UNSPECIFIED;
        private int processingTicks = UNKNOWN_PROCESSING_TICKS;
        private long energyPerTick = UNKNOWN_ENERGY_PER_TICK;
        private String label = "";

        @Nonnull
        public Builder addInput(@Nonnull ItemStack input) {
            inputs.add(MachineRecipeIngredient.of(input));
            return this;
        }

        @Nonnull
        public Builder addIngredient(@Nonnull MachineRecipeIngredient ingredient) {
            inputs.add(Objects.requireNonNull(ingredient, "ingredient"));
            return this;
        }

        @Nonnull
        public Builder addOutput(@Nonnull ItemStack output) {
            outputs.add(Objects.requireNonNull(output, "output"));
            return this;
        }

        @Nonnull
        public Builder layout(@Nonnull MachineRecipeLayout layout) {
            this.layout = Objects.requireNonNull(layout, "layout");
            return this;
        }

        @Nonnull
        public Builder processingTicks(@Nonnegative int processingTicks) {
            this.processingTicks = processingTicks;
            return this;
        }

        @Nonnull
        public Builder energyPerTick(@Nonnegative long energyPerTick) {
            this.energyPerTick = energyPerTick;
            return this;
        }

        @Nonnull
        public Builder label(@Nonnull String label) {
            this.label = Objects.requireNonNull(label, "label");
            return this;
        }

        @Nonnull
        public MachineRecipeDisplay build() {
            return new MachineRecipeDisplay(inputs, outputs, layout, processingTicks, energyPerTick, label);
        }
    }
}
