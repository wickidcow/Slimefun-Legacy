package io.github.thebusybiscuit.slimefun4.implementation.items.blocks;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.bakedlibs.dough.protection.Interaction;
import io.github.thebusybiscuit.slimefun4.api.events.BlockPlacerPlaceEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.ItemSetting;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.items.settings.MaterialTagSetting;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.NotPlaceable;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockDispenseHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.handlers.VanillaInventoryDropHandler;
import io.github.thebusybiscuit.slimefun4.utils.VisualEffectUtils;
import io.github.thebusybiscuit.slimefun4.utils.tags.SlimefunTag;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Nameable;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Dispenser;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * The {@link BlockPlacer} is a machine which can place {@link Block Blocks}, as the name
 * would suggest.
 * It really just is a special type of {@link Dispenser} which places items instead of
 * shooting them.
 *
 * @author TheBusyBiscuit
 *
 * @see BlockPlacerPlaceEvent
 *
 */
public class BlockPlacer extends SlimefunItem {

    private final ItemSetting<List<String>> unplaceableBlocks =
            new MaterialTagSetting(this, "unplaceable-blocks", SlimefunTag.UNBREAKABLE_MATERIALS);

    @ParametersAreNonnullByDefault
    public BlockPlacer(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);

        addItemSetting(unplaceableBlocks);

        addItemHandler(onPlace(), onBlockDispense());
        addItemHandler(new VanillaInventoryDropHandler<>(Dispenser.class));
    }

    @Nonnull
    private BlockPlaceHandler onPlace() {
        return new BlockPlaceHandler(false) {

            @Override
            public void onPlayerPlace(BlockPlaceEvent e) {
                Player p = e.getPlayer();

                StorageCacheUtils.setData(
                        e.getBlock().getLocation(), "owner", p.getUniqueId().toString());
            }
        };
    }

    @Nonnull
    private BlockDispenseHandler onBlockDispense() {
        return (e, dispenser, facedBlock, machine) -> {
            if (!hasPermission(dispenser, facedBlock)) {
                e.setCancelled(true);
                return;
            }

            Material material = e.getItem().getType();

            if (SlimefunTag.SHULKER_BOXES.isTagged(material)) {
                return;
            }

            e.setCancelled(true);

            if (facedBlock.isEmpty()
                    && dispenser.getInventory().getViewers().isEmpty()
                    && isAllowed(facedBlock, material)) {
                SlimefunItem item = SlimefunItem.getByItem(e.getItem());

                if (item != null) {
                    if (!(item instanceof NotPlaceable) && !item.isDisabledIn(dispenser.getWorld())) {
                        placeSlimefunBlock(item, e.getItem(), facedBlock, dispenser);
                    }
                } else if (!Slimefun.getIntegrations().isCustomItem(e.getItem())) {
                    placeBlock(e.getItem(), facedBlock, dispenser);
                }
            }
        };
    }

    @ParametersAreNonnullByDefault
    private boolean hasPermission(Dispenser dispenser, Block target) {
        String owner = StorageCacheUtils.getData(dispenser.getLocation(), "owner");

        if (owner == null) {
            return true;
        }

        if (owner.isBlank()) {
            return false;
        }

        try {
            OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(owner));
            return Slimefun.getProtectionManager().hasPermission(player, target, Interaction.PLACE_BLOCK);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private boolean isAllowed(@Nonnull Block facedBlock, @Nonnull Material type) {
        if (!type.isBlock()) {
            return false;
        } else if (type == Material.CAKE) {
            return !facedBlock.getRelative(BlockFace.DOWN).isPassable();
        } else if (SlimefunTag.BLOCK_PLACER_IGNORED_MATERIALS.isTagged(type)) {
            return false;
        } else {
            for (String blockType : unplaceableBlocks.getValue()) {
                if (type.toString().equals(blockType)) {
                    return false;
                }
            }

            return true;
        }
    }

    @ParametersAreNonnullByDefault
    private void placeSlimefunBlock(SlimefunItem sfItem, ItemStack item, Block block, Dispenser dispenser) {
        BlockPlacerPlaceEvent e = new BlockPlacerPlaceEvent(dispenser.getBlock(), item, block);
        Bukkit.getPluginManager().callEvent(e);

        if (!e.isCancelled()) {
            boolean hasItemHandler = sfItem.callItemHandler(BlockPlaceHandler.class, handler -> {
                if (handler.isBlockPlacerAllowed()) {
                    schedulePlacement(block, dispenser.getInventory(), item, () -> {
                        block.setType(item.getType());
                        Slimefun.getDatabaseManager()
                                .getBlockDataController()
                                .createBlock(block.getLocation(), sfItem.getId());

                        handler.onBlockPlacerPlace(e);
                    });
                }
            });

            if (!hasItemHandler) {
                schedulePlacement(block, dispenser.getInventory(), item, () -> {
                    block.setType(item.getType());
                    Slimefun.getDatabaseManager()
                            .getBlockDataController()
                            .createBlock(block.getLocation(), sfItem.getId());
                });
            }
        }
    }

    @ParametersAreNonnullByDefault
    private void placeBlock(ItemStack item, Block facedBlock, Dispenser dispenser) {
        BlockPlacerPlaceEvent e = new BlockPlacerPlaceEvent(dispenser.getBlock(), item, facedBlock);
        Bukkit.getPluginManager().callEvent(e);

        if (!e.isCancelled()) {
            schedulePlacement(facedBlock, dispenser.getInventory(), item, () -> {
                facedBlock.setType(item.getType());

                if (item.hasItemMeta()) {
                    ItemMeta meta = item.getItemMeta();

                    if (meta.hasDisplayName()) {
                        BlockState blockState = facedBlock.getState(false);

                        if (blockState instanceof Nameable nameable) {
                            nameable.setCustomName(meta.getDisplayName());
                            blockState.update(true, false);
                        }
                    }
                }
            });
        }
    }

    @ParametersAreNonnullByDefault
    private void schedulePlacement(Block b, Inventory inv, ItemStack item, Runnable runnable) {
        Slimefun.runSyncAt(
                b.getLocation(),
                () -> {
                    if (b.isEmpty()) {
                        ItemStack removedItem = item.clone();
                        removedItem.setAmount(1);

                        VisualEffectUtils.playBlockBreakEffect(b.getLocation(), item.getType());

                        try {
                            if (inv.removeItem(removedItem).isEmpty()) {
                                runnable.run();
                            }
                        } catch (Exception x) {
                            error("An Exception was thrown while a BlockPlacer was performing its action", x);
                        }
                    }
                },
                2L);
    }

    @Override
    public boolean loadDataByDefault() {
        return true;
    }
}
