package io.github.thebusybiscuit.slimefun4.api.recipes.machine;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import io.github.thebusybiscuit.slimefun4.core.attributes.ItemAttribute;
import java.util.List;
import javax.annotation.Nonnull;
import org.bukkit.World;

/**
 * Item attribute for machines that can directly expose structured guide recipes.
 *
 * <p>This is the simplest integration route for new addons. Existing addons can instead register a separate
 * {@link MachineRecipeProvider} without changing their item hierarchy.
 */
@SlimefunAPI
public interface MachineRecipeDisplayItem extends ItemAttribute {

    @Nonnull
    List<MachineRecipeDisplay> getMachineRecipeDisplays(@Nonnull World world);
}
