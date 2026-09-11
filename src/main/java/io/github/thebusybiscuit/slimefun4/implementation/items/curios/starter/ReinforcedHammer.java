package io.github.thebusybiscuit.slimefun4.implementation.items.curios.starter;

import io.github.bakedlibs.dough.protection.Interaction;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * A manual ore-crushing tool that gives new players a small, power-free route into basic Slimefun materials.
 */
public final class ReinforcedHammer extends SimpleSlimefunItem<ItemUseHandler> {

    @ParametersAreNonnullByDefault
    public ReinforcedHammer(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    @Override
    public @Nonnull ItemUseHandler getItemHandler() {
        return event -> {
            Block block = event.getClickedBlock().orElse(null);
            if (block == null) {
                return;
            }

            ItemStack output = outputFor(block.getType());
            if (output == null) {
                return;
            }

            Player player = event.getPlayer();
            if (!Slimefun.getProtectionManager().hasPermission(player, block, Interaction.BREAK_BLOCK)) {
                return;
            }

            event.cancel();
            Slimefun.getProtectionManager().logAction(player, block, Interaction.BREAK_BLOCK);
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5D, 0.4D, 0.5D), output);
            block.setType(Material.AIR, false);
            StoneHammer.damageTool(event.getItem());
        };
    }

    private static ItemStack outputFor(Material material) {
        return switch (material) {
            case IRON_ORE, DEEPSLATE_IRON_ORE -> SlimefunItems.IRON_DUST.clone();
            case GOLD_ORE, DEEPSLATE_GOLD_ORE -> SlimefunItems.GOLD_DUST.clone();
            case COPPER_ORE, DEEPSLATE_COPPER_ORE -> SlimefunItems.COPPER_DUST.clone();
            case COAL_ORE, DEEPSLATE_COAL_ORE -> SlimefunItems.CARBON.clone();
            case LAPIS_ORE, DEEPSLATE_LAPIS_ORE -> new ItemStack(Material.LAPIS_LAZULI);
            case REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE -> new ItemStack(Material.REDSTONE);
            case NETHER_QUARTZ_ORE -> new ItemStack(Material.QUARTZ);
            default -> null;
        };
    }
}
