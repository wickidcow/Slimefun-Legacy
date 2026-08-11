package io.github.thebusybiscuit.slimefun4.implementation.items.multiblocks;

import io.github.bakedlibs.dough.items.ItemUtils;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.ItemSpawnReason;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.items.virtual.VirtualItemHandler.ConsumeContext;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerBackpack;
import io.github.thebusybiscuit.slimefun4.core.multiblocks.MultiBlockMachine;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.backpacks.SlimefunBackpack;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import io.github.thebusybiscuit.slimefun4.utils.ThreadUtils;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Dispenser;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * This abstract super class is responsible for some utility methods for machines which
 * are capable of upgrading backpacks.
 *
 * @author TheBusyBiscuit
 *
 * @see EnhancedCraftingTable
 * @see MagicWorkbench
 * @see ArmorForge
 *
 */
abstract class AbstractCraftingTable extends MultiBlockMachine {

    @ParametersAreNonnullByDefault
    AbstractCraftingTable(ItemGroup itemGroup, SlimefunItemStack item, ItemStack[] recipe, BlockFace trigger) {
        super(itemGroup, item, recipe, trigger);
    }

    protected @Nonnull Inventory createVirtualInventory(@Nonnull Inventory inv, @Nonnull ItemStack[] recipe) {
        Inventory fakeInv = Bukkit.createInventory(null, 9, "Fake Inventory");

        for (int j = 0; j < inv.getContents().length; j++) {
            ItemStack stack = inv.getContents()[j];

            /*
             * Fixes #2103 - Properly simulating the consumption
             * (which may leave behind empty buckets or glass bottles)
             */
            ItemStack recipeCell = j < recipe.length ? recipe[j] : null;
            if (stack != null && recipeCell != null && recipeCell.getType() != Material.AIR) {
                stack = stack.clone();
                stack = consumeStack(stack, recipeCell.getAmount());
            }

            fakeInv.setItem(j, stack);
        }

        return fakeInv;
    }

    /**
     * Consumes every occupied recipe slot by the amount declared by the matched recipe.
     *
     * <p>Some multiblock recipes require more than one item in a single grid cell. The
     * matching code already validates that the slot contains at least that amount, so
     * consumption must use the same amount instead of always subtracting one.</p>
     *
     * @param inv the real dispenser inventory
     * @param recipe the recipe that matched this inventory
     */
    protected final void consumeInputs(@Nonnull Inventory inv, @Nonnull ItemStack[] recipe) {
        for (int slot = 0; slot < 9 && slot < recipe.length; slot++) {
            ItemStack recipeCell = recipe[slot];
            ItemStack item = inv.getItem(slot);

            if (recipeCell != null
                    && recipeCell.getType() != Material.AIR
                    && item != null
                    && item.getType() != Material.AIR) {
                inv.setItem(slot, consumeStack(item, recipeCell.getAmount()));
            }
        }
    }

    private @Nullable ItemStack consumeStack(@Nonnull ItemStack item, int amount) {
        var consumed = Slimefun.getItemStackService().consume(item, amount, true, ConsumeContext.VIRTUAL_CRAFTING);
        if (consumed.handled()) {
            return consumed.item();
        }

        ItemUtils.consumeItem(item, amount, true);
        return item.getAmount() > 0 && item.getType() != Material.AIR ? item : null;
    }

    /**
     * Finishes a craft against the live dispenser rather than a captured inventory.
     * Delayed animations and asynchronous backpack upgrades may outlive the dispenser
     * that originally supplied the ingredients. In that case the already-earned output
     * is dropped at the machine location instead of being written into a detached inventory.
     */
    protected final void finishCraftedItem(@Nonnull ItemStack output, @Nonnull Block dispenser) {
        BlockState state = dispenser.getState(false);

        if (state instanceof Dispenser liveDispenser) {
            handleCraftedItem(output, dispenser, liveDispenser.getInventory());
        } else {
            SlimefunUtils.spawnItem(
                    dispenser.getLocation(), output, ItemSpawnReason.MULTIBLOCK_MACHINE_OVERFLOW, true);
        }
    }

    // Return: true if upgrade from existing backpack, else false
    @ParametersAreNonnullByDefault
    protected boolean upgradeBackpack(
            Player p, Inventory inv, SlimefunBackpack backpack, ItemStack output, Runnable onReadyCb) {
        ItemStack input = null;

        var contents = inv.getContents();
        for (int j = 0; j < 9; j++) {
            var item = contents[j];
            if (item != null
                    && item.getType() != Material.AIR
                    && SlimefunItem.getByItem(item) instanceof SlimefunBackpack) {
                input = inv.getContents()[j];
                break;
            }
        }

        if (input == null) {
            return false;
        }

        // Fixes #2574 - Carry over the Soulbound status
        if (SlimefunUtils.isSoulbound(input)) {
            SlimefunUtils.setSoulbound(output, true);
        }

        int size = backpack.getSize();
        PlayerBackpack.getAsync(input)
                .thenAcceptAsync(
                        (result) -> {
                            if (result != null) {
                                result.setSize(size);
                                PlayerBackpack.bindItem(output, result);
                            }
                            onReadyCb.run();
                        },
                        ThreadUtils.getEntityDelayedExecutor(p));

        return true;
    }
}
