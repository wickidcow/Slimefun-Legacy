package io.github.thebusybiscuit.slimefun4.implementation.guide.enhanced;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.core.guide.GuideHistory;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuide;
import io.github.thebusybiscuit.slimefun4.core.guide.options.SlimefunGuideSettings;
import io.github.thebusybiscuit.slimefun4.core.multiblocks.MultiBlockMachine;
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.guide.GuideRuntimeGuard;
import io.github.thebusybiscuit.slimefun4.implementation.guide.GuideSearchIndex;
import io.github.thebusybiscuit.slimefun4.utils.ChatUtils;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import io.github.thebusybiscuit.slimefun4.utils.compatibility.VersionedItemFlag;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Enhanced Slimefun Legacy guide backed by the shared cached search index. */
public class IndexedEnhancedSurvivalSlimefunGuide extends EnhancedSurvivalSlimefunGuide {

    @Override
    @ParametersAreNonnullByDefault
    public void openSearch(PlayerProfile profile, String input, boolean addToHistory) {
        runIndexedPage(
                profile,
                "open indexed enhanced search page 1",
                () -> openIndexedSearchPage(profile, input, 1, addToHistory));
    }

    private void openIndexedSearchPage(PlayerProfile profile, String input, int page, boolean addToHistory) {
        Player player = profile.getPlayer();
        if (player == null) {
            return;
        }

        String searchTerm = GuideSearchIndex.normalize(input);
        if (addToHistory && isSurvivalMode()) {
            profile.getGuideHistory().add(searchTerm);
        }

        List<SlimefunItem> matches = GuideSearchIndex.get().searchSmart(
                player,
                searchTerm,
                item -> !item.isHidden()
                        && !item.isDisabledIn(player.getWorld())
                        && isItemGroupAccessible(player, item));

        LegacyGuideSettings settings = LegacyGuideSettings.get();
        List<String> format = settings.getSearchFormat();
        List<Integer> contentSlots = settings.findSlots(format, 'i');
        int pages = pageCount(matches.size(), contentSlots.size());
        int safePage = clampPage(page, pages);
        String cropped = ChatUtils.crop(ChatColor.WHITE, input);

        ChestMenu menu = createIndexedMenu(settings.getSearchTitle(cropped));
        addBackground(menu, format);
        addSearchControls(menu, profile, format, safePage, pages, input);
        addIndexedBackButton(menu, format, player, profile);

        int start = (safePage - 1) * contentSlots.size();
        for (int index = 0; index < contentSlots.size() && start + index < matches.size(); index++) {
            SlimefunItem item = matches.get(start + index);
            int slot = contentSlots.get(index);
            menu.addItem(
                    slot,
                    decorateIndexedItem(
                            player,
                            item,
                            LegacyGuideBookmarks.get().contains(player.getUniqueId(), item.getId())));
            menu.addMenuClickHandler(slot, (clickedPlayer, clickedSlot, clickedItem, action) -> {
                if (action.isRightClicked() && LegacyGuideSettings.get().hasBookmarks()) {
                    toggleIndexedBookmark(clickedPlayer, item);
                    runIndexedPage(
                            profile,
                            "refresh indexed enhanced search page " + safePage,
                            () -> openIndexedSearchPage(profile, input, safePage, false));
                } else {
                    openIndexedItem(profile, clickedPlayer, item, action.isShiftClicked());
                }
                return false;
            });
        }
        menu.open(player);
    }

    private void addSearchControls(
            ChestMenu menu,
            PlayerProfile profile,
            List<String> format,
            int page,
            int pages,
            String input) {
        Player player = profile.getPlayer();
        if (player == null) {
            return;
        }

        for (int slot : LegacyGuideSettings.get().findSlots(format, 'T')) {
            if (isSurvivalMode()) {
                menu.addItem(slot, ChestMenuUtils.getMenuButton(player));
                menu.addMenuClickHandler(slot, (clickedPlayer, clickedSlot, item, action) -> {
                    SlimefunGuideSettings.openSettings(
                            clickedPlayer, clickedPlayer.getInventory().getItemInMainHand());
                    return false;
                });
            } else {
                addBackgroundSlot(menu, slot);
            }
        }

        for (int slot : LegacyGuideSettings.get().findSlots(format, 'S')) {
            menu.addItem(slot, ChestMenuUtils.getSearchButton(player));
            menu.addMenuClickHandler(slot, (clickedPlayer, clickedSlot, item, action) -> {
                requestIndexedSearch(clickedPlayer, profile);
                return false;
            });
        }

        for (int slot : LegacyGuideSettings.get().findSlots(format, 'R')) {
            if (LegacyGuideSettings.get().hasSmartSearch()) {
                menu.addItem(slot, createSmartSearchButton());
                menu.addMenuClickHandler(slot, (clickedPlayer, clickedSlot, item, action) -> {
                    requestIndexedSearch(clickedPlayer, profile);
                    return false;
                });
            } else {
                addBackgroundSlot(menu, slot);
            }
        }

        for (char marker : new char[] {'C', 'c'}) {
            for (int slot : LegacyGuideSettings.get().findSlots(format, marker)) {
                if (LegacyGuideSettings.get().hasBookmarks()) {
                    int count = LegacyGuideBookmarks.get().size(player.getUniqueId());
                    menu.addItem(slot, createBookmarksButton(count));
                    menu.addMenuClickHandler(slot, (clickedPlayer, clickedSlot, item, action) -> {
                        runIndexedPage(
                                profile,
                                "open enhanced bookmarks page 1",
                                () -> openBookmarks(profile, 1));
                        return false;
                    });
                } else {
                    addBackgroundSlot(menu, slot);
                }
            }
        }

        for (int slot : LegacyGuideSettings.get().findSlots(format, 'P')) {
            menu.addItem(slot, ChestMenuUtils.getPreviousButton(player, page, pages));
            menu.addMenuClickHandler(slot, (clickedPlayer, clickedSlot, item, action) -> {
                if (page > 1) {
                    int previous = page - 1;
                    runIndexedPage(
                            profile,
                            "open indexed enhanced search page " + previous,
                            () -> openIndexedSearchPage(profile, input, previous, false));
                }
                return false;
            });
        }

        for (int slot : LegacyGuideSettings.get().findSlots(format, 'N')) {
            menu.addItem(slot, ChestMenuUtils.getNextButton(player, page, pages));
            menu.addMenuClickHandler(slot, (clickedPlayer, clickedSlot, item, action) -> {
                if (page < pages) {
                    int next = page + 1;
                    runIndexedPage(
                            profile,
                            "open indexed enhanced search page " + next,
                            () -> openIndexedSearchPage(profile, input, next, false));
                }
                return false;
            });
        }
    }

    private ItemStack createSmartSearchButton() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "Smart Search");
            meta.setLore(List.of(
                    "",
                    ChatColor.GRAY + "Search names, IDs, addons, categories,",
                    ChatColor.GRAY + "groups, recipe types and item lore.",
                    "",
                    ChatColor.WHITE + "Filters: " + ChatColor.GRAY + "id:, addon:, category:,",
                    ChatColor.GRAY + "group:, recipe:"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createBookmarksButton(int count) {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Bookmarks");
            meta.setLore(List.of(
                    "",
                    ChatColor.GRAY + "Saved items: " + ChatColor.WHITE + count,
                    "",
                    ChatColor.YELLOW + "Click to open"));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void requestIndexedSearch(Player player, PlayerProfile profile) {
        player.closeInventory();
        player.sendMessage(ChatColor.GREEN + "Enter a search term. " + ChatColor.GRAY
                + "Optional filters: id:, addon:, category:, group:, recipe:");
        io.github.bakedlibs.dough.chat.ChatInput.waitForPlayer(
                Slimefun.instance(),
                player,
                message -> SlimefunGuide.openSearch(profile, message, getMode(), isSurvivalMode()));
    }

    private void addIndexedBackButton(
            ChestMenu menu, List<String> format, Player player, PlayerProfile profile) {
        List<Integer> slots = LegacyGuideSettings.get().findSlots(format, 'b');
        if (slots.isEmpty()) {
            return;
        }

        int slot = slots.get(0);
        GuideHistory history = profile.getGuideHistory();
        if (isSurvivalMode() && history.size() > 1) {
            menu.addItem(
                    slot,
                    ChestMenuUtils.getBackButton(
                            player,
                            "",
                            "&fLeft Click: &7Return to previous page",
                            "&fShift + Left Click: &7Return to main menu"));
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

        menu.addItem(slot, ChestMenuUtils.getBackButton(player, "", "&7Return to the guide"));
        menu.addMenuClickHandler(slot, (clickedPlayer, clickedSlot, item, action) -> {
            SlimefunGuide.openMainMenu(profile, getMode(), history.getMainMenuPage());
            return false;
        });
    }

    private ItemStack decorateIndexedItem(Player player, SlimefunItem item, boolean bookmarked) {
        ItemStack decorated = item.getItem().clone();
        ItemMeta meta = decorated.getItemMeta();
        if (meta == null) {
            return decorated;
        }

        List<String> lore = meta.hasLore() && meta.getLore() != null
                ? new ArrayList<>(meta.getLore())
                : new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + "Group: " + ChatColor.WHITE
                + item.getItemGroup().getDisplayName(player));
        String categoryId = item.getItemGroup().getCategoryId();
        if (categoryId != null && !categoryId.isBlank()) {
            lore.add(ChatColor.DARK_GRAY + "Guide Category: " + ChatColor.WHITE
                    + categoryId.replace('_', ' '));
        }
        if (LegacyGuideSettings.get().shouldDisplayAddon()) {
            lore.add(ChatColor.DARK_GRAY + "Addon: " + ChatColor.WHITE + GuideSearchIndex.getAddonName(item));
        }
        if (LegacyGuideSettings.get().shouldDisplayItemId()) {
            lore.add(ChatColor.DARK_GRAY + "ID: " + ChatColor.GRAY + item.getId());
        }
        if (LegacyGuideSettings.get().hasBookmarks()) {
            lore.add("");
            lore.add(bookmarked
                    ? ChatColor.GOLD + "★ Bookmarked"
                    : ChatColor.YELLOW + "Right-click to bookmark");
            if (bookmarked) {
                lore.add(ChatColor.GRAY + "Right-click to remove bookmark");
            }
        }
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, VersionedItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        decorated.setItemMeta(meta);
        return decorated;
    }

    private void openIndexedItem(
            PlayerProfile profile, Player player, SlimefunItem item, boolean shiftClicked) {
        try {
            if (isSurvivalMode()) {
                SlimefunGuide.displayItem(profile, item, true);
            } else if (player.hasPermission("slimefun.cheat.items")) {
                if (item instanceof MultiBlockMachine) {
                    Slimefun.getLocalization().sendMessage(player, "guide.cheat.no-multiblocks");
                } else {
                    ItemStack cloned = item.getItem().clone();
                    if (shiftClicked) {
                        cloned.setAmount(cloned.getMaxStackSize());
                    }
                    player.getInventory().addItem(cloned);
                }
            } else {
                Slimefun.getLocalization().sendMessage(player, "messages.no-permission", true);
            }
        } catch (Exception | LinkageError failure) {
            player.sendMessage(ChatColor.DARK_RED
                    + "An internal error occurred while opening this item. Please inform an administrator.");
            item.error("This item caused an error while being opened in indexed enhanced search.", failure);
        }
    }

    private void toggleIndexedBookmark(Player player, SlimefunItem item) {
        boolean added = LegacyGuideBookmarks.get().toggle(player.getUniqueId(), item.getId());
        player.sendMessage(
                added
                        ? ChatColor.GOLD + "★ Added " + ChatColor.WHITE + ChatColor.stripColor(item.getItemName())
                                + ChatColor.GOLD + " to your bookmarks."
                        : ChatColor.YELLOW + "Removed " + ChatColor.WHITE + ChatColor.stripColor(item.getItemName())
                                + ChatColor.YELLOW + " from your bookmarks.");
    }

    private boolean isItemGroupAccessible(Player player, SlimefunItem item) {
        return Slimefun.getConfigManager().isShowHiddenItemGroupsInSearch()
                || item.getItemGroup().isAccessible(player);
    }

    private void runIndexedPage(PlayerProfile profile, String operation, Runnable action) {
        GuideRuntimeGuard.run(profile, getMode(), operation, null, action);
    }

    private ChestMenu createIndexedMenu(String title) {
        ChestMenu menu = new ChestMenu(title);
        menu.setEmptySlotsClickable(false);
        menu.addMenuOpeningHandler(SoundEffect.GUIDE_BUTTON_CLICK_SOUND::playFor);
        return menu;
    }

    private void addBackground(ChestMenu menu, List<String> format) {
        for (int slot : LegacyGuideSettings.get().findSlots(format, 'B')) {
            addBackgroundSlot(menu, slot);
        }
    }

    private void addBackgroundSlot(ChestMenu menu, int slot) {
        menu.addItem(slot, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
    }

    private static int pageCount(int entries, int pageSize) {
        return Math.max(1, pageSize <= 0 ? 1 : (entries + pageSize - 1) / pageSize);
    }

    private static int clampPage(int page, int pages) {
        return Math.max(1, Math.min(page, pages));
    }
}
