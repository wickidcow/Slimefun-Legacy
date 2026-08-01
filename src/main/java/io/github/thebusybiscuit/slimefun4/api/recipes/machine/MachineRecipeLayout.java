package io.github.thebusybiscuit.slimefun4.api.recipes.machine;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;

/**
 * Describes how the ingredients of a machine recipe are arranged.
 */
@SlimefunAPI
public enum MachineRecipeLayout {
    /** The machine requires ingredients in fixed positions. */
    SHAPED,

    /** The machine accepts the listed ingredients in any valid input slots. */
    SHAPELESS,

    /** The provider does not expose enough information to determine the layout. */
    UNSPECIFIED
}
