package io.github.thebusybiscuit.slimefun4.implementation.items.backpacks;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerBackpack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.DistinctiveItem;
import io.github.thebusybiscuit.slimefun4.core.attributes.NotPlaceable;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.BackpackListener;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import io.github.thebusybiscuit.slimefun4.utils.tags.SlimefunTag;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * This class represents a {@link SlimefunItem} that is considered a Backpack.
 * Right-Clicking will open the {@link Inventory} of the currently held Backpack.
 *
 * @author TheBusyBiscuit
 *
 * @see BackpackListener
 * @see PlayerBackpack
 *
 */
public class SlimefunBackpack extends SimpleSlimefunItem<ItemUseHandler> implements DistinctiveItem, NotPlaceable {
    private final int size;

    @ParametersAreNonnullByDefault
    public SlimefunBackpack(
            int size, ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);

        this.size = size;
    }

    /**
     * This returns the size of this {@link SlimefunBackpack}.
     *
     * @return The size of this backpack
     */
    public int getSize() {
        return size;
    }

    /**
     * This method returns whether a given {@link ItemStack} is allowed to be stored
     * in this {@link SlimefunBackpack}.
     *
     * @param item
     *            The {@link ItemStack} to check for
     *
     * @param itemAsSlimefunItem
     *            The same {@link ItemStack} as a {@link SlimefunItem}, might be null
     *
     * @return Whether the given {@link ItemStack} is allowed to be put into this {@link SlimefunBackpack}
     */
    public boolean isItemAllowed(@Nonnull ItemStack item, @Nullable SlimefunItem itemAsSlimefunItem) {
        // Shulker Boxes are not allowed!
        if (SlimefunTag.SHULKER_BOXES.isTagged(item.getType())) {
            return false;
        }

        return !(itemAsSlimefunItem instanceof SlimefunBackpack);
    }

    @Override
    public ItemUseHandler getItemHandler() {
        return e -> {
            e.cancel();

            BackpackListener listener = Slimefun.getBackpackListener();

            if (listener != null) {
                listener.openBackpack(e.getPlayer(), e.getItem(), this);
            }
        };
    }

    private static java.util.List<String> legacyLore(java.util.List<Component> lore) {
        return lore.stream().map(LegacyComponentSerializer.legacySection()::serialize).toList();
    }

    @Override
    public boolean canStack(@Nonnull ItemMeta itemMetaOne, @Nonnull ItemMeta itemMetaTwo) {
        /*
         * Bound backpacks are unique containers and must never merge into a
         * stack, even if two duplicated item representations happen to carry
         * the same identity. Only fresh, unbound backpack items may stack.
         */
        if (PlayerBackpack.hasBackpackIdentity(itemMetaOne) || PlayerBackpack.hasBackpackIdentity(itemMetaTwo)) {
            return false;
        }

        List<Component> itemLore = itemMetaTwo.lore();
        List<Component> slimefunLore = itemMetaOne.lore();

        if (itemLore != null && slimefunLore != null
                && SlimefunUtils.equalsLore(legacyLore(itemLore), legacyLore(slimefunLore))) {
            return true;
        }
        return itemLore == null && slimefunLore == null;
    }
}
