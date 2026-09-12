package io.github.thebusybiscuit.slimefun4.implementation.items.extratools;

import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.CustomItemStack;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Historical ExtraTools vaporizer. */
public final class Vaporizer extends ExtraToolsContainer {

    public Vaporizer() {
        super(
                ExtraToolsItems.VAPORIZER,
                new ItemStack[] {
                    new ItemStack(Material.MAGMA_BLOCK), SlimefunItems.ELECTRIC_MOTOR, new ItemStack(Material.MAGMA_BLOCK),
                    SlimefunItems.HEATING_COIL, SlimefunItems.FLUID_PUMP, SlimefunItems.HEATING_COIL,
                    new ItemStack(Material.MAGMA_BLOCK), SlimefunItems.MEDIUM_CAPACITOR, new ItemStack(Material.MAGMA_BLOCK)
                });
    }

    @Override
    protected void registerDefaultRecipes() {
        registerRecipe(
                8,
                new ItemStack[] {new ItemStack(Material.WATER_BUCKET)},
                new ItemStack[] {new ItemStack(Material.BUCKET), new CustomItemStack(SlimefunItems.SALT, 4)});
        registerRecipe(
                8,
                new ItemStack[] {new ItemStack(Material.LAVA_BUCKET)},
                new ItemStack[] {new ItemStack(Material.BUCKET), new CustomItemStack(SlimefunItems.SULFATE, 16)});
        registerRecipe(3, new ItemStack[] {new ItemStack(Material.MAGMA_BLOCK)}, new ItemStack[] {SlimefunItems.SULFATE});
    }

    @Override
    public ItemStack getProgressBar() {
        return new ItemStack(Material.IRON_HOE);
    }

    @Override
    public String getInventoryTitle() {
        return "&cVaporizer";
    }

    @Override
    public String getMachineIdentifier() {
        return "VAPORIZER";
    }

    @Override
    public int getCapacity() {
        return 256;
    }

    @Override
    public int getEnergyConsumption() {
        return 16;
    }

    @Override
    public int getSpeed() {
        return 1;
    }
}
