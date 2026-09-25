package io.github.thebusybiscuit.slimefun4.implementation.items.electric.machines;

import io.github.bakedlibs.dough.items.CustomItemStack;
import io.github.bakedlibs.dough.protection.Interaction;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.NotHopperable;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.multiblocks.Smeltery;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import java.util.Arrays;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu.AdvancedMenuClickHandler;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.AContainer;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineRecipe;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.inventory.DirtyChestMenu;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * The {@link ElectricSmeltery} is an electric version of the standard {@link Smeltery}.
 *
 * @author TheBusyBiscuit
 *
 */
public class ElectricSmeltery extends AContainer implements NotHopperable {

    private static final int[] border = {4, 5, 6, 7, 8, 13, 31, 40, 41, 42, 43, 44};
    private static final int[] inputBorder = {0, 1, 2, 3, 9, 12, 18, 21, 27, 30, 36, 37, 38, 39};
    private static final int[] outputBorder = {14, 15, 16, 17, 23, 26, 32, 33, 34, 35};
    private static final int[] INPUT_SLOTS = {10, 11, 19, 20, 28, 29};
    private static final int[] OUTPUT_SLOTS = {24, 25};

    @ParametersAreNonnullByDefault
    public ElectricSmeltery(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);

        new BlockMenuPreset(getId(), getItemName()) {

            @Override
            public void init() {
                constructMenu(this);
            }

            @Override
            public boolean canOpen(Block b, Player p) {
                return p.hasPermission("slimefun.inventory.bypass")
                        || Slimefun.getProtectionManager()
                                .hasPermission(p, b.getLocation(), Interaction.INTERACT_BLOCK);
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
                return new int[0];
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(DirtyChestMenu menu, ItemTransportFlow flow, ItemStack item) {
                if (flow == ItemTransportFlow.WITHDRAW) {
                    return getOutputSlots();
                }

                /*
                 * This method is queried heavily by cargo/Networks. The old path allocated two
                 * LinkedLists, boxed every slot and sorted through a Comparator on every request.
                 * There are only six input slots, so fixed primitive arrays are cheaper and keep
                 * the exact same preference: partial matching stacks first, smallest stack first,
                 * then empty slots in normal input-slot order.
                 */
                int[] matchingSlots = new int[INPUT_SLOTS.length];
                int[] matchingAmounts = new int[INPUT_SLOTS.length];
                int[] emptySlots = new int[INPUT_SLOTS.length];
                int matchingCount = 0;
                int emptyCount = 0;

                for (int slot : INPUT_SLOTS) {
                    ItemStack stack = menu.getItemInSlot(slot);

                    if (stack == null || stack.getType().isAir()) {
                        emptySlots[emptyCount++] = slot;
                    } else if (stack.getAmount() < stack.getMaxStackSize()
                            && SlimefunUtils.isItemSimilar(stack, item, true, false)) {
                        int insertAt = matchingCount;
                        while (insertAt > 0 && matchingAmounts[insertAt - 1] > stack.getAmount()) {
                            matchingSlots[insertAt] = matchingSlots[insertAt - 1];
                            matchingAmounts[insertAt] = matchingAmounts[insertAt - 1];
                            insertAt--;
                        }
                        matchingSlots[insertAt] = slot;
                        matchingAmounts[insertAt] = stack.getAmount();
                        matchingCount++;
                    }
                }

                if (matchingCount > 0) {
                    return Arrays.copyOf(matchingSlots, matchingCount);
                }

                return Arrays.copyOf(emptySlots, emptyCount);
            }
        };
    }

    @Override
    protected void constructMenu(BlockMenuPreset preset) {
        for (int i : border) {
            preset.addItem(i, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
        }

        for (int i : inputBorder) {
            preset.addItem(i, ChestMenuUtils.getInputSlotTexture(), ChestMenuUtils.getEmptyClickHandler());
        }

        for (int i : outputBorder) {
            preset.addItem(i, ChestMenuUtils.getOutputSlotTexture(), ChestMenuUtils.getEmptyClickHandler());
        }

        preset.addItem(
                22, new CustomItemStack(Material.BLACK_STAINED_GLASS_PANE, " "), ChestMenuUtils.getEmptyClickHandler());

        for (int i : OUTPUT_SLOTS) {
            preset.addMenuClickHandler(i, new AdvancedMenuClickHandler() {

                @Override
                public boolean onClick(Player p, int slot, ItemStack cursor, ClickAction action) {
                    return false;
                }

                @Override
                public boolean onClick(
                        InventoryClickEvent e, Player p, int slot, ItemStack cursor, ClickAction action) {
                    return cursor == null || cursor.getType() == null || cursor.getType() == Material.AIR;
                }
            });
        }
    }

    @Override
    protected MachineRecipe findNextRecipe(BlockMenu inv) {
        for (int slot : INPUT_SLOTS) {
            ItemStack item = inv.getItemInSlot(slot);
            if (item != null && !item.getType().isAir()) {
                return super.findNextRecipe(inv);
            }
        }

        return null;
    }

    @Override
    public ItemStack getProgressBar() {
        return new ItemStack(Material.FLINT_AND_STEEL);
    }

    @Override
    public int[] getInputSlots() {
        return INPUT_SLOTS;
    }

    @Override
    public int[] getOutputSlots() {
        return OUTPUT_SLOTS;
    }

    @Override
    public String getMachineIdentifier() {
        return "ELECTRIC_SMELTERY";
    }
}
