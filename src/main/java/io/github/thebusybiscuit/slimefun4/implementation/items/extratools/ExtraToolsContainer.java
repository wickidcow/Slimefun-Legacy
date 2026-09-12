package io.github.thebusybiscuit.slimefun4.implementation.items.extratools;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.RecipeDisplayItem;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import java.util.ArrayList;
import java.util.List;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.AContainer;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineRecipe;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

/** Shared inventory and recipe-display behavior used by the historical ExtraTools machines. */
abstract class ExtraToolsContainer extends AContainer implements RecipeDisplayItem {

    ExtraToolsContainer(SlimefunItemStack item, ItemStack[] recipe) {
        super(ExtraToolsItems.ITEM_GROUP, item, RecipeType.ENHANCED_CRAFTING_TABLE, recipe);
        addItemHandler(createBreakHandler());
    }

    @Override
    public List<ItemStack> getDisplayRecipes() {
        List<ItemStack> displayRecipes = new ArrayList<>(recipes.size() * 2);
        for (MachineRecipe recipe : recipes) {
            displayRecipes.add(recipe.getInput()[0]);
            displayRecipes.add(recipe.getOutput()[recipe.getOutput().length - 1]);
        }
        return displayRecipes;
    }

    private BlockBreakHandler createBreakHandler() {
        return new BlockBreakHandler(false, false) {
            @Override
            public void onPlayerBreak(BlockBreakEvent event, ItemStack item, List<ItemStack> drops) {
                Block block = event.getBlock();
                BlockMenu menu = BlockStorage.getInventory(block);
                if (menu != null) {
                    menu.dropItems(block.getLocation(), getInputSlots());
                    menu.dropItems(block.getLocation(), getOutputSlots());
                }
            }
        };
    }
}
