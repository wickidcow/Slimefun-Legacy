package io.github.thebusybiscuit.slimefun4.implementation.items.multiblocks;

import io.github.bakedlibs.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.api.events.MultiBlockCraftEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.multiblocks.MultiBlockMachine;
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.food.Juice;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import io.github.thebusybiscuit.slimefun4.utils.VisualEffectUtils;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
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
 * The {@link Juicer} is a {@link MultiBlockMachine} which can be used to
 * craft {@link Juice}.
 *
 * @author TheBusyBiscuit
 * @author Liruxo
 *
 * @see Juice
 *
 */
public class Juicer extends MultiBlockMachine {

    @ParametersAreNonnullByDefault
    public Juicer(ItemGroup itemGroup, SlimefunItemStack item) {
        super(
                itemGroup,
                item,
                new ItemStack[] {
                    null,
                    new ItemStack(Material.GLASS),
                    null,
                    null,
                    new ItemStack(Material.NETHER_BRICK_FENCE),
                    null,
                    null,
                    new CustomItemStack(Material.DISPENSER, "Dispenser (Facing Up)"),
                    null
                },
                BlockFace.SELF);
    }

    @Override
    public @Nonnull List<ItemStack> getDisplayRecipes() {
        return recipes.stream().map(items -> items[0]).collect(Collectors.toList());
    }

    @Override
    public void onInteract(Player p, Block b) {
        Block possibleDispenser = b.getRelative(BlockFace.DOWN);
        BlockState state = possibleDispenser.getState(false);

        if (state instanceof Dispenser dispenser) {
            Inventory inv = dispenser.getInventory();

            for (ItemStack current : inv.getContents()) {
                for (ItemStack convert : RecipeType.getRecipeInputs(this)) {
                    if (convert != null && SlimefunUtils.isItemSimilar(current, convert, true)) {
                        ItemStack defaultOutput = RecipeType.getRecipeOutput(this, convert);
                        MultiBlockCraftEvent event = new MultiBlockCraftEvent(p, this, current, defaultOutput);

                        Bukkit.getPluginManager().callEvent(event);
                        if (event.isCancelled()) {
                            return;
                        }

                        ItemStack output = event.getOutput();
                        Inventory outputInv = findOutputInventory(output, possibleDispenser, inv);

                        if (outputInv != null) {
                            ItemStack removing = current.clone();
                            removing.setAmount(convert.getAmount());
                            inv.removeItem(removing);
                            handleCraftedItem(output, possibleDispenser, inv);

                            SoundEffect.JUICER_USE_SOUND.playAt(b);
                            // This remains a natural block-breaking visual/sound effect.
                            VisualEffectUtils.playBlockBreakEffect(b.getLocation(), Material.HAY_BLOCK);
                        } else {
                            Slimefun.getLocalization().sendMessage(p, "machines.full-inventory", true);
                        }

                        return;
                    }
                }
            }

            Slimefun.getLocalization().sendMessage(p, "machines.unknown-material", true);
        }
    }
}
