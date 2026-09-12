package io.github.thebusybiscuit.slimefun4.implementation.items.curios.starter;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * An early-game hand crusher for turning cobblestone into gravel without a machine.
 */
public final class StoneHammer extends SimpleSlimefunItem<ItemUseHandler> {

    @ParametersAreNonnullByDefault
    public StoneHammer(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    @Override
    public @Nonnull ItemUseHandler getItemHandler() {
        return event -> {
            Block block = event.getClickedBlock().orElse(null);
            if (block == null || block.getType() != Material.COBBLESTONE) {
                return;
            }

            Player player = event.getPlayer();
            if (!Slimefun.getIntegrations().canBreakBlockAndLog(player, block)) {
                return;
            }

            event.cancel();
            block.setType(Material.GRAVEL, true);
            damageTool(event.getItem());
        };
    }

    static void damageTool(ItemStack tool) {
        ItemMeta meta = tool.getItemMeta();
        if (!(meta instanceof Damageable damageable) || tool.getType().getMaxDurability() <= 0) {
            return;
        }

        int nextDamage = damageable.getDamage() + 1;
        if (nextDamage >= tool.getType().getMaxDurability()) {
            tool.setAmount(Math.max(0, tool.getAmount() - 1));
            return;
        }

        damageable.setDamage(nextDamage);
        tool.setItemMeta(meta);
    }
}
