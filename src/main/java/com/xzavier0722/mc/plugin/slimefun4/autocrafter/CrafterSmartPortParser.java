package com.xzavier0722.mc.plugin.slimefun4.autocrafter;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.api.items.virtual.VirtualItemHandler.InventoryContext;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.autocrafters.AbstractAutoCrafter;
import io.github.thebusybiscuit.slimefun4.implementation.items.autocrafters.AutoCrafterInventoryMatcher;
import java.util.Collection;
import java.util.Map;
import java.util.function.Predicate;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class CrafterSmartPortParser implements CrafterInteractable {

    private final BlockMenu inv;
    private final Inventory inventory;

    public CrafterSmartPortParser(BlockMenu inv) {
        this.inv = inv;
        this.inventory = inv.toInventory();
    }

    @Override
    public boolean canOutput(ItemStack item) {
        return Slimefun.getItemStackService()
                .fits(inventory, item, InventoryContext.MACHINE_OUTPUT, CrafterSmartPort.OUTPUT_SLOTS);
    }

    @Override
    public boolean matchRecipe(
            AbstractAutoCrafter crafter,
            Collection<Predicate<ItemStack>> recipe,
            Map<Integer, Integer> itemQuantities) {
        ItemStack[] contents = inventory.getContents();

        for (Predicate<ItemStack> predicate : recipe) {
            // Reuse one synchronized Smart Port inventory snapshot for the complete recipe attempt.
            if (!AutoCrafterInventoryMatcher.matchesAny(crafter, contents, itemQuantities, predicate)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return inv.getItemInSlot(slot);
    }

    @Override
    public boolean addItem(ItemStack item) {
        ItemStack remainder = inv.pushItem(item, CrafterSmartPort.OUTPUT_SLOTS);
        if (remainder == null || remainder.getAmount() <= 0) {
            return true;
        }

        inv.getBlock().getWorld().dropItemNaturally(inv.getLocation(), remainder);
        return true;
    }

    @Override
    public void setIngredientCount(org.bukkit.block.Block b, int count) {
        count = Math.max(1, count);
        StorageCacheUtils.setData(b.getLocation(), "ingredientCount", String.valueOf(count));
        inv.getItemInSlot(6).setAmount(count);
    }
}
