package io.github.thebusybiscuit.slimefun4.implementation.items.extratools;

import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;

/** Historical ExtraTools electric composter with both original tiers. */
public abstract class ElectricComposter extends ExtraToolsContainer {

    private final Tier tier;

    protected ElectricComposter(Tier tier) {
        super(tier == Tier.ONE ? ExtraToolsItems.ELECTRIC_COMPOSTER : ExtraToolsItems.ELECTRIC_COMPOSTER_2, tier.recipe);
        this.tier = tier;
    }

    @Override
    protected void registerDefaultRecipes() {
        for (Material leaves : Tag.LEAVES.getValues()) {
            registerRecipe(8, new ItemStack[] {new ItemStack(leaves, 8)}, new ItemStack[] {new ItemStack(Material.DIRT)});
        }
        for (Material sapling : Tag.SAPLINGS.getValues()) {
            registerRecipe(8, new ItemStack[] {new ItemStack(sapling, 8)}, new ItemStack[] {new ItemStack(Material.DIRT)});
        }
        registerRecipe(8, new ItemStack[] {new ItemStack(Material.STONE, 4)}, new ItemStack[] {new ItemStack(Material.NETHERRACK)});
        registerRecipe(8, new ItemStack[] {new ItemStack(Material.SAND, 2)}, new ItemStack[] {new ItemStack(Material.SOUL_SAND)});
        registerRecipe(8, new ItemStack[] {new ItemStack(Material.WHEAT, 4)}, new ItemStack[] {new ItemStack(Material.NETHER_WART)});
    }

    @Override
    public ItemStack getProgressBar() {
        return new ItemStack(Material.WOODEN_HOE);
    }

    @Override
    public String getInventoryTitle() {
        return tier == Tier.ONE ? "&cElectric Composter" : "&cElectric Composter &7(&eII&7)";
    }

    @Override
    public String getMachineIdentifier() {
        return "ELECTRIC_COMPOSTER_" + tier.name();
    }

    @Override
    public int getCapacity() {
        return 256;
    }

    public enum Tier {
        ONE(new ItemStack[] {
            SlimefunItems.GILDED_IRON, SlimefunItems.MAGNESIUM_INGOT, SlimefunItems.GILDED_IRON,
            SlimefunItems.ELECTRIC_MOTOR, SlimefunItems.COMPOSTER, SlimefunItems.ELECTRIC_MOTOR,
            new ItemStack(Material.IRON_HOE), SlimefunItems.MEDIUM_CAPACITOR, new ItemStack(Material.IRON_HOE)
        }),
        TWO(new ItemStack[] {
            SlimefunItems.HARDENED_METAL_INGOT, SlimefunItems.BLISTERING_INGOT_3, SlimefunItems.HARDENED_METAL_INGOT,
            SlimefunItems.ELECTRIC_MOTOR, ExtraToolsItems.ELECTRIC_COMPOSTER, SlimefunItems.ELECTRIC_MOTOR,
            new ItemStack(Material.DIAMOND_HOE), SlimefunItems.LARGE_CAPACITOR, new ItemStack(Material.DIAMOND_HOE)
        });

        private final ItemStack[] recipe;

        Tier(ItemStack[] recipe) {
            this.recipe = recipe;
        }
    }
}
