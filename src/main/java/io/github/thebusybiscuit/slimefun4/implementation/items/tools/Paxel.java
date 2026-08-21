package io.github.thebusybiscuit.slimefun4.implementation.items.tools;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.NotPlaceable;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.tags.SlimefunTag;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

/**
 * A combined pickaxe, axe and shovel which automatically changes its vanilla tool type
 * to match the block being mined while preserving its Slimefun item data.
 *
 * <p>The canonical item is a diamond pickaxe, so the normal vanilla Netherite Smithing Table
 * upgrade path can upgrade the Paxel while preserving its Slimefun item data.
 */
public final class Paxel extends SlimefunItem implements Listener, NotPlaceable {

    private static final Set<Material> AXE_BLOCKS = Stream.of(
                    Tag.LOGS.getValues(),
                    Tag.PLANKS.getValues(),
                    Tag.WOODEN_STAIRS.getValues(),
                    Tag.SIGNS.getValues(),
                    Tag.WOODEN_FENCES.getValues(),
                    Tag.FENCE_GATES.getValues(),
                    Tag.WOODEN_TRAPDOORS.getValues(),
                    Tag.WOODEN_PRESSURE_PLATES.getValues(),
                    Tag.WOODEN_DOORS.getValues(),
                    Tag.WOODEN_SLABS.getValues(),
                    Tag.WOODEN_BUTTONS.getValues(),
                    Tag.BANNERS.getValues(),
                    Tag.LEAVES.getValues(),
                    new HashSet<>(Arrays.asList(
                            Material.CHEST,
                            Material.TRAPPED_CHEST,
                            Material.CRAFTING_TABLE,
                            Material.SMITHING_TABLE,
                            Material.LOOM,
                            Material.CARTOGRAPHY_TABLE,
                            Material.FLETCHING_TABLE,
                            Material.BARREL,
                            Material.JUKEBOX,
                            Material.CAMPFIRE,
                            Material.BOOKSHELF,
                            Material.CHISELED_BOOKSHELF,
                            Material.JACK_O_LANTERN,
                            Material.CARVED_PUMPKIN,
                            Material.PUMPKIN,
                            Material.MELON,
                            Material.COMPOSTER,
                            Material.BEEHIVE,
                            Material.BEE_NEST,
                            Material.NOTE_BLOCK,
                            Material.LADDER,
                            Material.DAYLIGHT_DETECTOR,
                            Material.MUSHROOM_STEM,
                            Material.BROWN_MUSHROOM_BLOCK,
                            Material.RED_MUSHROOM_BLOCK,
                            Material.BAMBOO,
                            Material.VINE,
                            Material.LECTERN)))
            .flatMap(Set::stream)
            .collect(Collectors.toUnmodifiableSet());

    public Paxel(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    @Override
    public void postRegister() {
        if (!isDisabled()) {
            Bukkit.getPluginManager().registerEvents(this, Slimefun.instance());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onMine(BlockDamageEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        SlimefunItem slimefunItem = SlimefunItem.getByItem(item);
        if (!(slimefunItem instanceof Paxel)) {
            return;
        }

        Block block = event.getBlock();
        boolean netherite = isNetheriteTool(item.getType());

        if (SlimefunTag.EXPLOSIVE_SHOVEL_BLOCKS.isTagged(block.getType())) {
            item.setType(netherite ? Material.NETHERITE_SHOVEL : Material.DIAMOND_SHOVEL);
        } else if (AXE_BLOCKS.contains(block.getType())) {
            item.setType(netherite ? Material.NETHERITE_AXE : Material.DIAMOND_AXE);
        } else {
            item.setType(netherite ? Material.NETHERITE_PICKAXE : Material.DIAMOND_PICKAXE);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (!(SlimefunItem.getByItem(item) instanceof Paxel)) {
            return;
        }

        item.setType(isNetheriteTool(item.getType()) ? Material.NETHERITE_AXE : Material.DIAMOND_AXE);
    }

    private static boolean isNetheriteTool(Material material) {
        return material == Material.NETHERITE_PICKAXE
                || material == Material.NETHERITE_AXE
                || material == Material.NETHERITE_SHOVEL;
    }
}
