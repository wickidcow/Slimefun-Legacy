package io.github.thebusybiscuit.slimefun4.implementation.items.tools;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.DamageableItem;
import io.github.thebusybiscuit.slimefun4.core.handlers.ToolUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import io.github.thebusybiscuit.slimefun4.utils.tags.SlimefunTag;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.Effect;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/**
 * The {@link SmeltersPickaxe} automatically smelts any ore you mine.
 *
 * @author TheBusyBiscuit
 *
 */
public class SmeltersPickaxe extends SimpleSlimefunItem<ToolUseHandler> implements DamageableItem {

    @ParametersAreNonnullByDefault
    public SmeltersPickaxe(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    @Override
    public @Nonnull ToolUseHandler getItemHandler() {
        return (e, tool, fortune, drops) -> {
            Block b = e.getBlock();

            if (SlimefunTag.SMELTERS_PICKAXE_BLOCKS.isTagged(b.getType())
                    && !StorageCacheUtils.hasSlimefunBlock(b.getLocation())) {
                Collection<ItemStack> blockDrops = b.getDrops(tool);
                List<ItemStack> itemDrops = new ArrayList<>();
                for (ItemStack drop : blockDrops) {
                    if (drop != null && !drop.getType().isAir()) {
                        itemDrops.add(smelt(b, drop, fortune));
                    }
                }
                // stop blockListener from dropping origin drops
                e.setDropItems(false);
                // drop smelted manually
                for (ItemStack itemDrop : itemDrops) {
                    b.getWorld().dropItemNaturally(b.getLocation(), itemDrop);
                }
                damageItem(e.getPlayer(), tool);
            }
        };
    }

    @ParametersAreNonnullByDefault
    private ItemStack smelt(Block b, ItemStack drop, int fortune) {
        Optional<ItemStack> furnaceOutput = Slimefun.getMinecraftRecipeService().getFurnaceOutput(drop);
        ItemStack result = drop;

        if (furnaceOutput.isPresent()) {
            b.getWorld().playEffect(b.getLocation(), Effect.MOBSPAWNER_FLAMES, 1);
            result = drop.withType(furnaceOutput.get().getType());
        }

        // Fixes #3116
        result.setAmount(fortune);
        return result;
    }

    @Override
    public boolean isDamageable() {
        return true;
    }
}
