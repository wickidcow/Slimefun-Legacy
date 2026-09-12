package io.github.thebusybiscuit.slimefun4.implementation.items.extratools;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetComponent;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.core.networks.energy.EnergyNetComponentType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import java.util.List;
import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/** Historical ExtraTools powered cobblestone generator. */
public final class CobblestoneGenerator extends SimpleSlimefunItem<BlockTicker> implements EnergyNetComponent {

    private static final int ENERGY_CONSUMPTION = 32;
    private static final int[] BORDER = {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 18, 19, 20, 21, 22, 27, 28, 29, 30, 31, 36, 37,
        38, 39, 40, 41, 42, 43, 44
    };
    private static final int[] OUTPUT_BORDER = {14, 15, 16, 17, 23, 26, 32, 33, 34, 35};

    private int decrement = 2;

    public CobblestoneGenerator() {
        super(
                ExtraToolsItems.ITEM_GROUP,
                ExtraToolsItems.COBBLESTONE_GENERATOR,
                RecipeType.ENHANCED_CRAFTING_TABLE,
                new ItemStack[] {
                    SlimefunItems.PROGRAMMABLE_ANDROID_MINER,
                    SlimefunItems.MAGNESIUM_INGOT,
                    SlimefunItems.PROGRAMMABLE_ANDROID_MINER,
                    new ItemStack(Material.WATER_BUCKET),
                    SlimefunItems.BLISTERING_INGOT_3,
                    new ItemStack(Material.LAVA_BUCKET),
                    SlimefunItems.PROGRAMMABLE_ANDROID_MINER,
                    SlimefunItems.BIG_CAPACITOR,
                    SlimefunItems.PROGRAMMABLE_ANDROID_MINER
                });

        createPreset();
        addItemHandler(createBreakHandler());
    }

    private void createPreset() {
        new BlockMenuPreset(getId(), getItemName()) {
            @Override
            public void init() {
                for (int slot : BORDER) {
                    addItem(
                            slot,
                            new CustomItemStack(new ItemStack(Material.GRAY_STAINED_GLASS_PANE), " "),
                            ChestMenuUtils.getEmptyClickHandler());
                }
                for (int slot : OUTPUT_BORDER) {
                    addItem(
                            slot,
                            new CustomItemStack(new ItemStack(Material.ORANGE_STAINED_GLASS_PANE), " "),
                            ChestMenuUtils.getEmptyClickHandler());
                }
                for (int slot : getOutputSlots()) {
                    addMenuClickHandler(slot, new ChestMenu.AdvancedMenuClickHandler() {
                        @Override
                        public boolean onClick(Player player, int clickedSlot, ItemStack cursor, ClickAction action) {
                            return false;
                        }

                        @Override
                        public boolean onClick(
                                InventoryClickEvent event,
                                Player player,
                                int clickedSlot,
                                ItemStack cursor,
                                ClickAction action) {
                            return cursor == null || cursor.getType() == Material.AIR;
                        }
                    });
                }
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
                return flow == ItemTransportFlow.INSERT ? getInputSlots() : getOutputSlots();
            }

            @Override
            public boolean canOpen(Block block, Player player) {
                return player.hasPermission("slimefun.inventory.bypass")
                        || Slimefun.getProtectionManager()
                                        .hasPermission(player, block.getLocation(), Interaction.INTERACT_BLOCK)
                                && Slimefun.getPermissionsService().hasPermission(player, CobblestoneGenerator.this);
            }
        };
    }

    public int[] getInputSlots() {
        return new int[] {};
    }

    public int[] getOutputSlots() {
        return new int[] {24, 25};
    }

    @Override
    public EnergyNetComponentType getEnergyComponentType() {
        return EnergyNetComponentType.CONSUMER;
    }

    @Override
    public int getCapacity() {
        return 512;
    }

    @Override
    public BlockTicker getItemHandler() {
        return new BlockTicker() {
            @Override
            public void uniqueTick() {
                if (decrement == 1) {
                    decrement = 2;
                } else {
                    decrement--;
                }
            }

            @Override
            public void tick(Block block, SlimefunItem item, Config data) {
                if (decrement != 2 || getCharge(block.getLocation()) < ENERGY_CONSUMPTION) {
                    return;
                }

                ItemStack output = new ItemStack(Material.COBBLESTONE);
                BlockMenu menu = BlockStorage.getInventory(block);
                if (menu == null || !menu.fits(output, getOutputSlots())) {
                    return;
                }

                removeCharge(block.getLocation(), ENERGY_CONSUMPTION);
                menu.pushItem(output, getOutputSlots());
            }

            @Override
            public boolean isSynchronized() {
                return true;
            }
        };
    }

    private BlockBreakHandler createBreakHandler() {
        return new BlockBreakHandler(false, false) {
            @Override
            public void onPlayerBreak(BlockBreakEvent event, ItemStack item, List<ItemStack> drops) {
                Block block = event.getBlock();
                BlockMenu menu = BlockStorage.getInventory(block);
                if (menu != null) {
                    menu.dropItems(block.getLocation(), getOutputSlots());
                }
            }
        };
    }
}
