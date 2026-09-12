package io.github.thebusybiscuit.slimefun4.implementation.items.extratools;

import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Historical ExtraTools pulverizer. */
public final class Pulverizer extends ExtraToolsContainer {

    public Pulverizer() {
        super(
                ExtraToolsItems.PULVERIZER,
                new ItemStack[] {
                    SlimefunItems.SILICON, SlimefunItems.HARDENED_METAL_INGOT, SlimefunItems.SILICON,
                    SlimefunItems.ELECTRIC_MOTOR, SlimefunItems.STEEL_PLATE, SlimefunItems.ELECTRIC_MOTOR,
                    new ItemStack(Material.IRON_PICKAXE), SlimefunItems.MEDIUM_CAPACITOR, new ItemStack(Material.IRON_PICKAXE)
                });
    }

    @Override
    protected void registerDefaultRecipes() {
        for (Material material : new Material[] {
            Material.STONE, Material.GRANITE, Material.DIORITE, Material.ANDESITE, Material.COBBLESTONE,
            Material.DEEPSLATE, Material.COBBLED_DEEPSLATE, Material.TUFF, Material.CALCITE,
            Material.GRAVEL, Material.GRASS_BLOCK, Material.DIRT, Material.COARSE_DIRT, Material.PODZOL
        }) {
            registerRecipe(8, new ItemStack[] {new ItemStack(material, 4)}, new ItemStack[] {new ItemStack(Material.SAND)});
        }
        registerRecipe(8, new ItemStack[] {new ItemStack(Material.NETHERRACK, 4)}, new ItemStack[] {new ItemStack(Material.SOUL_SAND)});
    }

    @Override
    public ItemStack getProgressBar() {
        return new ItemStack(Material.IRON_PICKAXE);
    }

    @Override
    public String getInventoryTitle() {
        return "&cPulverizer";
    }

    @Override
    public String getMachineIdentifier() {
        return "PULVERIZER";
    }

    @Override
    public int getCapacity() {
        return 256;
    }

    @Override
    public int getEnergyConsumption() {
        return 25;
    }

    @Override
    public int getSpeed() {
        return 4;
    }
}
