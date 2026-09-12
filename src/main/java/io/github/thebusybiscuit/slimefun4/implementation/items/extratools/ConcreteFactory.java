package io.github.thebusybiscuit.slimefun4.implementation.items.extratools;

import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** Historical ExtraTools concrete factory. */
public final class ConcreteFactory extends ExtraToolsContainer {

    public ConcreteFactory() {
        super(
                ExtraToolsItems.CONCRETE_FACTORY,
                new ItemStack[] {
                    new ItemStack(Material.WATER_BUCKET), SlimefunItems.GILDED_IRON, new ItemStack(Material.WATER_BUCKET),
                    SlimefunItems.ADVANCED_CIRCUIT_BOARD, SlimefunItems.ELECTRIC_MOTOR, SlimefunItems.ADVANCED_CIRCUIT_BOARD,
                    new ItemStack(Material.WATER_BUCKET), SlimefunItems.SMALL_CAPACITOR, new ItemStack(Material.WATER_BUCKET)
                });
    }

    @Override
    protected void registerDefaultRecipes() {
        for (Material powder : new Material[] {
            Material.WHITE_CONCRETE_POWDER, Material.ORANGE_CONCRETE_POWDER, Material.MAGENTA_CONCRETE_POWDER,
            Material.LIGHT_BLUE_CONCRETE_POWDER, Material.YELLOW_CONCRETE_POWDER, Material.LIME_CONCRETE_POWDER,
            Material.PINK_CONCRETE_POWDER, Material.GRAY_CONCRETE_POWDER, Material.LIGHT_GRAY_CONCRETE_POWDER,
            Material.CYAN_CONCRETE_POWDER, Material.PURPLE_CONCRETE_POWDER, Material.BLUE_CONCRETE_POWDER,
            Material.BROWN_CONCRETE_POWDER, Material.GREEN_CONCRETE_POWDER, Material.RED_CONCRETE_POWDER,
            Material.BLACK_CONCRETE_POWDER
        }) {
            Material concrete = Material.matchMaterial(powder.name().replace("_POWDER", ""));
            if (concrete != null) {
                registerRecipe(4, new ItemStack[] {new ItemStack(powder, 8)}, new ItemStack[] {new ItemStack(concrete, 8)});
            }
        }
    }

    @Override
    public ItemStack getProgressBar() {
        return new ItemStack(Material.IRON_SHOVEL);
    }

    @Override
    public String getInventoryTitle() {
        return "&cConcrete Factory";
    }

    @Override
    public String getMachineIdentifier() {
        return "CONCRETE_FACTORY";
    }

    @Override
    public int getCapacity() {
        return 256;
    }

    @Override
    public int getEnergyConsumption() {
        return 8;
    }

    @Override
    public int getSpeed() {
        return 1;
    }
}
