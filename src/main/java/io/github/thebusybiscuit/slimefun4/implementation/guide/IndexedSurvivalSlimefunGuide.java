package io.github.thebusybiscuit.slimefun4.implementation.guide;

import io.github.bakedlibs.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.core.guide.GuideHistory;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuide;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.ChatUtils;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import io.github.thebusybiscuit.slimefun4.utils.compatibility.VersionedItemFlag;
import java.util.Arrays;
import java.util.List;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

/** Classic Slimefun guide with the shared cached search index. */
public class IndexedSurvivalSlimefunGuide extends SurvivalSlimefunGuide {

    private static final int MAX_SEARCH_RESULTS = 35;

    @Override
    @ParametersAreNonnullByDefault
    public void openSearch(PlayerProfile profile, String input, boolean addToHistory) {
        Player player = profile.getPlayer();
        if (player == null) {
            return;
        }

        ChestMenu menu = new ChestMenu(Slimefun.getLocalization()
                .getMessage(player, "guide.search.inventory")
                .replace("%item%", ChatUtils.crop(ChatColor.WHITE, input)));
        String searchTerm = GuideSearchIndex.normalize(input);

        if (addToHistory) {
            profile.getGuideHistory().add(searchTerm);
        }

        menu.setEmptySlotsClickable(false);
        createHeader(player, profile, menu);
        addIndexedBackButton(menu, 1, player, profile);

        List<SlimefunItem> matches = GuideSearchIndex.get().searchByName(
                searchTerm,
                item -> !item.isHidden() && isItemGroupAccessible(player, item),
                MAX_SEARCH_RESULTS);

        int index = 9;
        for (SlimefunItem slimefunItem : matches) {
            ItemStack itemStack = new CustomItemStack(slimefunItem.getItem(), meta -> {
                ItemGroup itemGroup = slimefunItem.getItemGroup();
                meta.setLore(Arrays.asList(
                        "", ChatColor.DARK_GRAY + "\u21E8 " + ChatColor.WHITE + itemGroup.getDisplayName(player)));
                meta.addItemFlags(
                        ItemFlag.HIDE_ATTRIBUTES,
                        ItemFlag.HIDE_ENCHANTS,
                        VersionedItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            });

            menu.addItem(index, itemStack);
            menu.addMenuClickHandler(index, (clickedPlayer, slot, item, action) -> {
                try {
                    if (!isSurvivalMode()) {
                        clickedPlayer.getInventory().addItem(slimefunItem.getItem().clone());
                    } else {
                        SlimefunGuide.displayItem(profile, slimefunItem, true);
                    }
                } catch (Exception | LinkageError failure) {
                    clickedPlayer.sendMessage(ChatColor.DARK_RED
                            + "An internal error occurred while opening this item. Please inform an administrator.");
                    slimefunItem.error("This item caused an error while being opened from indexed search.", failure);
                }
                return false;
            });
            index++;
        }

        menu.open(player);
    }

    private boolean isItemGroupAccessible(Player player, SlimefunItem item) {
        return Slimefun.getConfigManager().isShowHiddenItemGroupsInSearch()
                || item.getItemGroup().isAccessible(player);
    }

    private void addIndexedBackButton(ChestMenu menu, int slot, Player player, PlayerProfile profile) {
        GuideHistory history = profile.getGuideHistory();
        if (isSurvivalMode() && history.size() > 1) {
            menu.addItem(
                    slot,
                    new CustomItemStack(ChestMenuUtils.getBackButton(
                            player,
                            "",
                            "&fLeft Click: &7Return to previous page",
                            "&fShift + Left Click: &7Return to main menu")));
            menu.addMenuClickHandler(slot, (clickedPlayer, clickedSlot, item, action) -> {
                if (action.isShiftClicked()) {
                    SlimefunGuide.openMainMenu(profile, getMode(), history.getMainMenuPage());
                } else {
                    history.goBack(this);
                }
                return false;
            });
            return;
        }

        menu.addItem(
                slot,
                new CustomItemStack(ChestMenuUtils.getBackButton(
                        player,
                        "",
                        ChatColor.GRAY + Slimefun.getLocalization().getMessage(player, "guide.back.guide"))));
        menu.addMenuClickHandler(slot, (clickedPlayer, clickedSlot, item, action) -> {
            SlimefunGuide.openMainMenu(profile, getMode(), history.getMainMenuPage());
            return false;
        });
    }
}
