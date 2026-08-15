package io.github.thebusybiscuit.slimefun4.implementation.items.multiblocks;

import io.github.bakedlibs.dough.inventory.InvUtils;
import io.github.thebusybiscuit.slimefun4.api.events.MultiBlockCraftEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.multiblocks.MultiBlockMachine;
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import java.util.List;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Dispenser;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * An abstract super class for the {@link Smeltery} and {@link MakeshiftSmeltery}.
 *
 * @author TheBusyBiscuit
 *
 */
abstract class AbstractSmeltery extends MultiBlockMachine {

    @ParametersAreNonnullByDefault
    protected AbstractSmeltery(ItemGroup itemGroup, SlimefunItemStack item, ItemStack[] recipe, BlockFace trigger) {
        super(itemGroup, item, recipe, trigger);
    }

    @Override
    public void onInteract(Player p, Block b) {
        Block possibleDispenser = b.getRelative(BlockFace.DOWN);
        BlockState state = possibleDispenser.getState(false);

        if (state instanceof Dispenser dispenser) {
            Inventory inv = dispenser.getInventory();
            List<ItemStack[]> inputs = RecipeType.getRecipeInputList(this);

            for (int i = 0; i < inputs.size(); i++) {
                if (canCraft(inv, inputs, i)) {
                    ItemStack defaultOutput =
                            RecipeType.getRecipeOutputList(this, inputs.get(i)).clone();
                    MultiBlockCraftEvent event = new MultiBlockCraftEvent(p, this, inputs.get(i), defaultOutput);

                    Bukkit.getPluginManager().callEvent(event);
                    if (!event.isCancelled()) {
                        ItemStack output = event.getOutput();

                        if (SlimefunUtils.canPlayerUseItem(p, output, true)) {
                            Inventory outputInv = findOutputInventory(output, possibleDispenser, inv);

                            if (outputInv != null) {
                                craft(p, b, inv, inputs.get(i), output, possibleDispenser);
                            } else {
                                Slimefun.getLocalization().sendMessage(p, "machines.full-inventory", true);
                            }
                        }
                    }

                    return;
                }
            }

            Slimefun.getLocalization().sendMessage(p, "machines.unknown-material", true);
        }
    }

    private boolean canCraft(Inventory inv, List<ItemStack[]> inputs, int recipeIndex) {
        ItemStack[] contents = inv.getContents();
        int[] remainingAmounts = new int[contents.length];

        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            remainingAmounts[slot] = stack == null ? 0 : stack.getAmount();
        }

        for (ItemStack expectedInput : inputs.get(recipeIndex)) {
            if (expectedInput == null) {
                continue;
            }

            int required = expectedInput.getAmount();

            for (int slot = 0; slot < contents.length && required > 0; slot++) {
                ItemStack stack = contents[slot];

                if (remainingAmounts[slot] <= 0
                        || !SlimefunUtils.isItemSimilar(stack, expectedInput, true, false)) {
                    continue;
                }

                int reserved = Math.min(remainingAmounts[slot], required);
                remainingAmounts[slot] -= reserved;
                required -= reserved;
            }

            if (required > 0) {
                return false;
            }
        }

        return true;
    }

    protected void craft(
            Player p, Block b, Inventory inv, ItemStack[] recipe, ItemStack output, Block dispenser) {
        for (ItemStack removing : recipe) {
            if (removing != null) {
                InvUtils.removeItem(
                        inv, removing.getAmount(), true, stack -> SlimefunUtils.isItemSimilar(stack, removing, true));
            }
        }

        handleCraftedItem(output, dispenser, inv);
        SoundEffect.SMELTERY_CRAFT_SOUND.playAt(b);
        b.getWorld().playEffect(b.getLocation(), Effect.MOBSPAWNER_FLAMES, 1);
    }
}
