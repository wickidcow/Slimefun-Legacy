package io.github.thebusybiscuit.slimefun4.implementation.items.electric.gadgets;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.Rechargeable;
import io.github.thebusybiscuit.slimefun4.core.handlers.EntityInteractHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.ToolUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.compatibility.VersionedEntityType;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * The {@link MultiTool} is an electric device which can mimic
 * the behaviour of any other {@link SlimefunItem}.
 *
 * @author TheBusyBiscuit
 *
 */
public class MultiTool extends SlimefunItem implements Rechargeable {

    private static final float COST = 0.3F;
    private final NamespacedKey multiToolMode = new NamespacedKey(Slimefun.instance(), "MULTI_TOOL_MODE");
    private final List<MultiToolMode> modes = new ArrayList<>();
    private final float capacity;

    @ParametersAreNonnullByDefault
    public MultiTool(
            ItemGroup itemGroup,
            SlimefunItemStack item,
            RecipeType recipeType,
            ItemStack[] recipe,
            float capacity,
            String... items) {
        super(itemGroup, item, recipeType, recipe);

        for (int i = 0; i < items.length; i++) {
            modes.add(new MultiToolMode(this, i, items[i]));
        }

        this.capacity = capacity;
    }

    @Override
    public float getMaxItemCharge(ItemStack item) {
        return capacity;
    }

    private int nextIndex(int i) {
        int index = i;

        do {
            index++;

            if (index >= modes.size()) {
                index = 0;
            }
        } while (index != i && !modes.get(index).isEnabled());

        return index;
    }

    @Nonnull
    protected ItemUseHandler getItemUseHandler() {
        return e -> {
            Player p = e.getPlayer();
            ItemStack item = e.getItem();
            e.cancel();

            var im = item.getItemMeta();
            var pdc = im.getPersistentDataContainer();
            int index = getStoredModeIndex(pdc);

            /*
             * Older Multi Tools stored a numeric list index. Store the Slimefun item ID
             * instead so mode selection remains stable if modes are reordered or new
             * modes are inserted. Removing the old value first also avoids a PDC type
             * mismatch on items created by previous Legacy/United builds.
             */
            pdc.remove(multiToolMode);
            pdc.set(multiToolMode, PersistentDataType.STRING, modes.get(index).getItemId());
            item.setItemMeta(im);

            if (!p.isSneaking()) {
                if (removeItemCharge(item, COST)) {
                    SlimefunItem sfItem = modes.get(index).getItem();

                    if (sfItem != null) {
                        sfItem.callItemHandler(ItemUseHandler.class, handler -> handler.onRightClick(e));
                    }
                }
            } else {
                index = nextIndex(index);

                SlimefunItem selectedItem = modes.get(index).getItem();
                String itemName = selectedItem != null ? selectedItem.getItemName() : "Unknown";
                Slimefun.getLocalization()
                        .sendMessage(
                                p,
                                "messages.multi-tool.mode-change",
                                true,
                                msg -> msg.replace("%device%", "Multi Tool")
                                        .replace("%mode%", ChatColor.stripColor(itemName)));

                pdc.set(multiToolMode, PersistentDataType.STRING, modes.get(index).getItemId());
                item.setItemMeta(im);
            }
        };
    }

    private int getStoredModeIndex(PersistentDataContainer pdc) {
        String storedItemId = pdc.get(multiToolMode, PersistentDataType.STRING);
        if (storedItemId != null) {
            for (int index = 0; index < modes.size(); index++) {
                if (modes.get(index).getItemId().equals(storedItemId)) {
                    return index;
                }
            }
        }

        Integer legacyIndex = pdc.get(multiToolMode, PersistentDataType.INTEGER);
        if (legacyIndex != null && legacyIndex >= 0 && legacyIndex < modes.size()) {
            return legacyIndex;
        }

        return 0;
    }

    @Nonnull
    private ToolUseHandler getToolUseHandler() {
        return (e, tool, fortune, drops) -> {
            // Multi Tools cannot be used as shears
            Slimefun.getLocalization().sendMessage(e.getPlayer(), "messages.multi-tool.not-shears");
            e.setCancelled(true);
        };
    }

    @Nonnull
    private EntityInteractHandler getEntityInteractionHandler() {
        return (e, item, offhand) -> {
            // Fixes #2217 - Prevent them from being used to shear entities
            EntityType type = e.getRightClicked().getType();
            if (type == VersionedEntityType.MOOSHROOM
                    || type == VersionedEntityType.SNOW_GOLEM
                    || type == EntityType.SHEEP) {
                Slimefun.getLocalization().sendMessage(e.getPlayer(), "messages.multi-tool.not-shears");
                e.setCancelled(true);
            }
        };
    }

    @Override
    public void preRegister() {
        super.preRegister();

        addItemHandler(getItemUseHandler());
        addItemHandler(getToolUseHandler());
        addItemHandler(getEntityInteractionHandler());
    }
}
