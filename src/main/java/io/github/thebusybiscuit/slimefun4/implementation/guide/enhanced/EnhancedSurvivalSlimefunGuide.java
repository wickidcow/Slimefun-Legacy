package io.github.thebusybiscuit.slimefun4.implementation.guide.enhanced;

import city.norain.slimefun4.VaultIntegration;
import io.github.bakedlibs.dough.chat.ChatInput;
import io.github.bakedlibs.dough.items.CustomItemStack;
import io.github.bakedlibs.dough.items.ItemUtils;
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.groups.FlexItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.groups.LockedItemGroup;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.api.researches.Research;
import io.github.thebusybiscuit.slimefun4.core.guide.GuideHistory;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuide;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.core.guide.options.SlimefunGuideSettings;
import io.github.thebusybiscuit.slimefun4.core.multiblocks.MultiBlockMachine;
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.guide.SurvivalSlimefunGuide;
import io.github.thebusybiscuit.slimefun4.utils.ChatUtils;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import io.github.thebusybiscuit.slimefun4.utils.compatibility.VersionedItemFlag;
import io.github.thebusybiscuit.slimefun4.utils.itemstack.SlimefunGuideItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Slimefun Legacy's native enhanced guide. It intentionally extends the classic guide so recipe rendering and every
 * public guide API remain compatible, while replacing the category, search and bookmark experience.
 */
public class EnhancedSurvivalSlimefunGuide extends SurvivalSlimefunGuide {

    private final ItemStack guideItem;

    public EnhancedSurvivalSlimefunGuide() {
        guideItem = new SlimefunGuideItem(this, "&aSlimefun Legacy Guide &7(Enhanced)");
    }

    @Override
    public @Nonnull ItemStack getItem() {
        return guideItem;
    }

    @Override
    @ParametersAreNonnullByDefault
    public void displayItem(PlayerProfile profile, SlimefunItem item, boolean addToHistory) {
        super.displayItem(profile, item, addToHistory);

        Player player = profile.getPlayer();
        if (player != null) {
            LegacyMachineRecipeBrowser.get().decorateMachinePage(player, profile, this, item);
            LegacyRecipeFillManager.get().decorateRecipePage(player, item);
        }
    }

    @Override
    public void openMainMenu(@Nonnull PlayerProfile profile, int page) {
        Player player = profile.getPlayer();
        if (player == null) {
            return;
        }

        if (isSurvivalMode()) {
            GuideHistory history = profile.getGuideHistory();
            history.clear();
            history.setMainMenuPage(page);
        }

        LegacyGuideSettings settings = LegacyGuideSettings.get();
        List<String> format = settings.getMainFormat();
        List<Integer> contentSlots = settings.findSlots(format, 'G');
        List<ItemGroup> groups = getVisibleItemGroups(player, profile);
        int pages = pageCount(groups.size(), contentSlots.size());
        int safePage = clampPage(page, pages);

        ChestMenu menu = createMenu(getTitle());
        addBackground(menu, format);
        addCommonControls(menu, profile, format, safePage, pages, () -> openMainMenu(profile, safePage - 1),
                () -> openMainMenu(profile, safePage + 1));

        int start = (safePage - 1) * contentSlots.size();
        for (int index = 0; index < contentSlots.size() && start + index < groups.size(); index++) {
            showItemGroup(menu, player, profile, groups.get(start + index), contentSlots.get(index));
        }
        menu.open(player);
    }

    @Override
    @ParametersAreNonnullByDefault
    public void openItemGroup(PlayerProfile profile, ItemGroup itemGroup, int page) {
        Player player = profile.getPlayer();
        if (player == null) {
            return;
        }

        if (itemGroup instanceof FlexItemGroup flexItemGroup) {
            flexItemGroup.open(player, profile, getMode());
            return;
        }

        if (isSurvivalMode()) {
            profile.getGuideHistory().add(itemGroup, page);
        }

        LegacyGuideSettings settings = LegacyGuideSettings.get();
        List<String> format = settings.getGroupFormat();
        List<Integer> contentSlots = settings.findSlots(format, 'i');
        List<SlimefunItem> visibleItems = itemGroup.getItems().stream()
                .filter(item -> !item.isDisabledIn(player.getWorld()))
                .toList();
        int pages = pageCount(visibleItems.size(), contentSlots.size());
        int safePage = clampPage(page, pages);

        ChestMenu menu = createMenu(itemGroup.getDisplayName(player));
        addBackground(menu, format);
        addCommonControls(menu, profile, format, safePage, pages,
                () -> openItemGroup(profile, itemGroup, safePage - 1),
                () -> openItemGroup(profile, itemGroup, safePage + 1));
        addBackButton(menu, format, player, profile);

        int start = (safePage - 1) * contentSlots.size();
        for (int index = 0; index < contentSlots.size() && start + index < visibleItems.size(); index++) {
            addSlimefunItem(menu, itemGroup, player, profile, visibleItems.get(start + index), safePage,
                    contentSlots.get(index));
        }
        menu.open(player);
    }

    @Override
    @ParametersAreNonnullByDefault
    public void openSearch(PlayerProfile profile, String input, boolean addToHistory) {
        openSearchPage(profile, input, 1, addToHistory);
    }

    protected void openBookmarks(@Nonnull PlayerProfile profile, int page) {
        Player player = profile.getPlayer();
        if (player == null) {
            return;
        }

        LegacyGuideSettings settings = LegacyGuideSettings.get();
        List<String> format = settings.getBookmarksFormat();
        List<Integer> contentSlots = settings.findSlots(format, 'i');
        List<SlimefunItem> items = new ArrayList<>();
        for (String id : LegacyGuideBookmarks.get().getBookmarks(player.getUniqueId())) {
            SlimefunItem item = Slimefun.getRegistry().getSlimefunItemIds().get(id);
            if (item != null && !item.isDisabledIn(player.getWorld())) {
                items.add(item);
            }
        }

        int pages = pageCount(items.size(), contentSlots.size());
        int safePage = clampPage(page, pages);
        ChestMenu menu = createMenu(settings.getBookmarksTitle());
        addBackground(menu, format);
        addCommonControls(menu, profile, format, safePage, pages, () -> openBookmarks(profile, safePage - 1),
                () -> openBookmarks(profile, safePage + 1));
        addBackButton(menu, format, player, profile);

        int start = (safePage - 1) * contentSlots.size();
        for (int index = 0; index < contentSlots.size() && start + index < items.size(); index++) {
            SlimefunItem item = items.get(start + index);
            int slot = contentSlots.get(index);
            menu.addItem(slot, decorateItem(player, item, true));
            menu.addMenuClickHandler(slot, (pl, clickedSlot, clickedItem, action) -> {
                if (action.isRightClicked()) {
                    toggleBookmark(pl, item);
                    openBookmarks(profile, safePage);
                } else {
                    openItem(profile, pl, item, action.isShiftClicked());
                }
                return false;
            });
        }
        menu.open(player);
    }

    private void openSearchPage(PlayerProfile profile, String input, int page, boolean addToHistory) {
        Player player = profile.getPlayer();
        if (player == null) {
            return;
        }

        String searchTerm = normalize(input);
        if (addToHistory && isSurvivalMode()) {
            profile.getGuideHistory().add(searchTerm);
        }

        List<SlimefunItem> matches = Slimefun.getRegistry().getEnabledSlimefunItems().stream()
                .filter(item -> !item.isHidden())
                .filter(item -> !item.isDisabledIn(player.getWorld()))
                .filter(item -> isItemGroupAccessible(player, item))
                .filter(item -> matches(player, item, searchTerm))
                .sorted(Comparator.comparing(item -> normalize(item.getItemName())))
                .toList();

        LegacyGuideSettings settings = LegacyGuideSettings.get();
        List<String> format = settings.getSearchFormat();
        List<Integer> contentSlots = settings.findSlots(format, 'i');
        int pages = pageCount(matches.size(), contentSlots.size());
        int safePage = clampPage(page, pages);
        String cropped = ChatUtils.crop(ChatColor.WHITE, input);
        ChestMenu menu = createMenu(settings.getSearchTitle(cropped));
        addBackground(menu, format);
        addCommonControls(menu, profile, format, safePage, pages,
                () -> openSearchPage(profile, input, safePage - 1, false),
                () -> openSearchPage(profile, input, safePage + 1, false));
        addBackButton(menu, format, player, profile);

        int start = (safePage - 1) * contentSlots.size();
        for (int index = 0; index < contentSlots.size() && start + index < matches.size(); index++) {
            SlimefunItem item = matches.get(start + index);
            int slot = contentSlots.get(index);
            menu.addItem(slot, decorateItem(player, item, LegacyGuideBookmarks.get().contains(player.getUniqueId(), item.getId())));
            menu.addMenuClickHandler(slot, (pl, clickedSlot, clickedItem, action) -> {
                if (action.isRightClicked() && LegacyGuideSettings.get().hasBookmarks()) {
                    toggleBookmark(pl, item);
                    openSearchPage(profile, input, safePage, false);
                } else {
                    openItem(profile, pl, item, action.isShiftClicked());
                }
                return false;
            });
        }
        menu.open(player);
    }

    private void showItemGroup(
            ChestMenu menu, Player player, PlayerProfile profile, ItemGroup group, int slot) {
        if (!(group instanceof LockedItemGroup lockedGroup)
                || !isSurvivalMode()
                || lockedGroup.hasUnlocked(player, profile)) {
            menu.addItem(slot, group.getItem(player));
            menu.addMenuClickHandler(slot, (pl, clickedSlot, item, action) -> {
                openItemGroup(profile, group, 1);
                return false;
            });
            return;
        }

        List<String> lore = new ArrayList<>();
        lore.add("");
        for (String line : Slimefun.getLocalization().getMessages(player, "guide.locked-itemgroup")) {
            lore.add(ChatColor.WHITE + line);
        }
        lore.add("");
        for (ItemGroup parent : lockedGroup.getParents()) {
            lore.add(parent.getItem(player).getItemMeta().getDisplayName());
        }
        menu.addItem(slot, new CustomItemStack(
                Material.BARRIER,
                "&4" + Slimefun.getLocalization().getMessage(player, "guide.locked") + " &7- &f"
                        + group.getItem(player).getItemMeta().getDisplayName(),
                lore.toArray(new String[0])));
        menu.addMenuClickHandler(slot, ChestMenuUtils.getEmptyClickHandler());
    }

    private void addSlimefunItem(
            ChestMenu menu,
            ItemGroup itemGroup,
            Player player,
            PlayerProfile profile,
            SlimefunItem item,
            int page,
            int slot) {
        Research research = item.getResearch();
        if (isSurvivalMode() && !hasPermission(player, item)) {
            List<String> message = Slimefun.getPermissionsService().getLore(item);
            menu.addItem(slot, new CustomItemStack(
                    ChestMenuUtils.getNoPermissionItem(), item.getItemName(), message.toArray(new String[0])));
            menu.addMenuClickHandler(slot, ChestMenuUtils.getEmptyClickHandler());
            return;
        }

        if (isSurvivalMode() && research != null && !profile.hasUnlocked(research)) {
            String cost = VaultIntegration.isEnabled()
                    ? String.format("%.2f", research.getCurrencyCost()) + " coins"
                    : research.getLevelCost() + " experience levels";
            menu.addItem(slot, new CustomItemStack(
                    ChestMenuUtils.getNoPermissionItem(),
                    "&f" + ItemUtils.getItemName(item.getItem()),
                    "&7" + item.getId(),
                    "&4&l" + Slimefun.getLocalization().getMessage(player, "guide.locked"),
                    "",
                    "&a> Click to unlock",
                    "",
                    "&7Requires &b" + cost));
            menu.addMenuClickHandler(slot, (pl, clickedSlot, clickedItem, action) -> {
                research.unlockFromGuide(this, player, profile, item, itemGroup, page);
                return false;
            });
            return;
        }

        boolean bookmarked = LegacyGuideBookmarks.get().contains(player.getUniqueId(), item.getId());
        menu.addItem(slot, decorateItem(player, item, bookmarked));
        menu.addMenuClickHandler(slot, (pl, clickedSlot, clickedItem, action) -> {
            if (action.isRightClicked() && LegacyGuideSettings.get().hasBookmarks()) {
                toggleBookmark(pl, item);
                openItemGroup(profile, itemGroup, page);
            } else {
                openItem(profile, pl, item, action.isShiftClicked());
            }
            return false;
        });
    }

    private void openItem(PlayerProfile profile, Player player, SlimefunItem item, boolean shiftClicked) {
        try {
            if (isSurvivalMode()) {
                displayItem(profile, item, true);
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
        } catch (Exception | LinkageError exception) {
            player.sendMessage(ChatColor.DARK_RED
                    + "An internal error occurred while opening this item. Please inform an administrator.");
            item.error("This item caused an error while being opened in the enhanced Slimefun guide.", exception);
        }
    }

    private ItemStack decorateItem(Player player, SlimefunItem item, boolean bookmarked) {
        return new CustomItemStack(item.getItem(), meta -> {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.DARK_GRAY + "Category: " + ChatColor.WHITE + item.getItemGroup().getDisplayName(player));
            if (LegacyGuideSettings.get().shouldDisplayAddon()) {
                lore.add(ChatColor.DARK_GRAY + "Addon: " + ChatColor.WHITE + getAddonName(item));
            }
            if (LegacyGuideSettings.get().shouldDisplayItemId()) {
                lore.add(ChatColor.DARK_GRAY + "ID: " + ChatColor.GRAY + item.getId());
            }
            if (LegacyGuideSettings.get().hasBookmarks()) {
                lore.add("");
                lore.add((bookmarked ? ChatColor.GOLD + "★ Bookmarked" : ChatColor.YELLOW + "Right-click to bookmark"));
                if (bookmarked) {
                    lore.add(ChatColor.GRAY + "Right-click to remove bookmark");
                }
            }
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, VersionedItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        });
    }

    private void addCommonControls(
            ChestMenu menu,
            PlayerProfile profile,
            List<String> format,
            int page,
            int pages,
            Runnable previous,
            Runnable next) {
        Player player = profile.getPlayer();
        if (player == null) {
            return;
        }

        for (int slot : LegacyGuideSettings.get().findSlots(format, 'T')) {
            if (isSurvivalMode()) {
                menu.addItem(slot, ChestMenuUtils.getMenuButton(player));
                menu.addMenuClickHandler(slot, (pl, s, item, action) -> {
                    SlimefunGuideSettings.openSettings(pl, pl.getInventory().getItemInMainHand());
                    return false;
                });
            } else {
                addBackgroundSlot(menu, slot);
            }
        }

        for (int slot : LegacyGuideSettings.get().findSlots(format, 'S')) {
            menu.addItem(slot, ChestMenuUtils.getSearchButton(player));
            menu.addMenuClickHandler(slot, (pl, s, item, action) -> {
                requestSearch(pl, profile);
                return false;
            });
        }

        for (int slot : LegacyGuideSettings.get().findSlots(format, 'R')) {
            if (LegacyGuideSettings.get().hasSmartSearch()) {
                menu.addItem(slot, new CustomItemStack(
                        Material.COMPASS,
                        "&b&lSmart Search",
                        "",
                        "&7Search names, IDs, addons, categories,",
                        "&7recipe types and item lore.",
                        "",
                        "&fFilters: &7id:, addon:, group:, recipe:"));
                menu.addMenuClickHandler(slot, (pl, s, item, action) -> {
                    requestSearch(pl, profile);
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
                    menu.addItem(slot, new CustomItemStack(
                            Material.NETHER_STAR,
                            "&6&lBookmarks",
                            "",
                            "&7Saved items: &f" + count,
                            "",
                            "&eClick to open"));
                    menu.addMenuClickHandler(slot, (pl, s, item, action) -> {
                        openBookmarks(profile, 1);
                        return false;
                    });
                } else {
                    addBackgroundSlot(menu, slot);
                }
            }
        }

        for (int slot : LegacyGuideSettings.get().findSlots(format, 'P')) {
            menu.addItem(slot, ChestMenuUtils.getPreviousButton(player, page, pages));
            menu.addMenuClickHandler(slot, (pl, s, item, action) -> {
                if (page > 1) {
                    previous.run();
                }
                return false;
            });
        }

        for (int slot : LegacyGuideSettings.get().findSlots(format, 'N')) {
            menu.addItem(slot, ChestMenuUtils.getNextButton(player, page, pages));
            menu.addMenuClickHandler(slot, (pl, s, item, action) -> {
                if (page < pages) {
                    next.run();
                }
                return false;
            });
        }
    }

    private void requestSearch(Player player, PlayerProfile profile) {
        player.closeInventory();
        player.sendMessage(ChatColor.GREEN + "Enter a search term. " + ChatColor.GRAY
                + "Optional filters: id:, addon:, group:, recipe:");
        ChatInput.waitForPlayer(
                Slimefun.instance(), player, message -> SlimefunGuide.openSearch(profile, message, getMode(), isSurvivalMode()));
    }

    private void addBackButton(
            ChestMenu menu, List<String> format, Player player, PlayerProfile profile) {
        List<Integer> slots = LegacyGuideSettings.get().findSlots(format, 'b');
        if (slots.isEmpty()) {
            return;
        }
        int slot = slots.get(0);
        GuideHistory history = profile.getGuideHistory();
        if (isSurvivalMode() && history.size() > 1) {
            menu.addItem(slot, new CustomItemStack(
                    ChestMenuUtils.getBackButton(
                            player,
                            "",
                            "&fLeft Click: &7Return to previous page",
                            "&fShift + Left Click: &7Return to main menu")));
            menu.addMenuClickHandler(slot, (pl, s, item, action) -> {
                if (action.isShiftClicked()) {
                    openMainMenu(profile, history.getMainMenuPage());
                } else {
                    history.goBack(this);
                }
                return false;
            });
        } else {
            menu.addItem(slot, ChestMenuUtils.getBackButton(player, "", "&7Return to the guide"));
            menu.addMenuClickHandler(slot, (pl, s, item, action) -> {
                openMainMenu(profile, history.getMainMenuPage());
                return false;
            });
        }
    }

    private void addBackground(ChestMenu menu, List<String> format) {
        for (int slot : LegacyGuideSettings.get().findSlots(format, 'B')) {
            addBackgroundSlot(menu, slot);
        }
    }

    private void addBackgroundSlot(ChestMenu menu, int slot) {
        menu.addItem(slot, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
    }

    private ChestMenu createMenu(String title) {
        ChestMenu menu = new ChestMenu(title);
        menu.setEmptySlotsClickable(false);
        menu.addMenuOpeningHandler(SoundEffect.GUIDE_BUTTON_CLICK_SOUND::playFor);
        return menu;
    }

    private String getTitle() {
        return isSurvivalMode() ? LegacyGuideSettings.get().getSurvivalTitle() : LegacyGuideSettings.get().getCheatTitle();
    }

    private boolean isItemGroupAccessible(Player player, SlimefunItem item) {
        return Slimefun.getConfigManager().isShowHiddenItemGroupsInSearch() || item.getItemGroup().isAccessible(player);
    }

    private boolean matches(Player player, SlimefunItem item, String query) {
        if (query.isBlank()) {
            return true;
        }

        String id = normalize(item.getId());
        String addon = normalize(getAddonName(item));
        String group = normalize(item.getItemGroup().getDisplayName(player));
        String recipe = normalize(ItemUtils.getItemName(item.getRecipeType().getItem(player)));
        String name = normalize(item.getItemName());
        String lore = normalizeLore(item.getItem().getItemMeta());

        if (query.startsWith("id:")) {
            return id.contains(query.substring(3).trim());
        }
        if (query.startsWith("addon:")) {
            return addon.contains(query.substring(6).trim());
        }
        if (query.startsWith("group:")) {
            return group.contains(query.substring(6).trim());
        }
        if (query.startsWith("recipe:")) {
            return recipe.contains(query.substring(7).trim());
        }

        String haystack = name + ' ' + id + ' ' + addon + ' ' + group + ' ' + recipe + ' ' + lore;
        for (String token : query.split("\\s+")) {
            if (!token.isBlank() && !haystack.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private String normalizeLore(ItemMeta meta) {
        if (meta == null || !meta.hasLore()) {
            return "";
        }
        return normalize(String.join(" ", meta.getLore()));
    }

    private static String normalize(String input) {
        String stripped = ChatColor.stripColor(input == null ? "" : input);
        return stripped == null ? "" : stripped.toLowerCase(Locale.ROOT).trim();
    }

    private static @Nonnull String getAddonName(@Nonnull SlimefunItem item) {
        SlimefunAddon addon = item.getAddon();
        return addon == null ? "Slimefun" : addon.getName();
    }

    private void toggleBookmark(Player player, SlimefunItem item) {
        boolean added = LegacyGuideBookmarks.get().toggle(player.getUniqueId(), item.getId());
        player.sendMessage(added
                ? ChatColor.GOLD + "★ Added " + ChatColor.WHITE + ChatColor.stripColor(item.getItemName())
                        + ChatColor.GOLD + " to your bookmarks."
                : ChatColor.YELLOW + "Removed " + ChatColor.WHITE + ChatColor.stripColor(item.getItemName())
                        + ChatColor.YELLOW + " from your bookmarks.");
    }

    private static int pageCount(int entries, int pageSize) {
        return Math.max(1, pageSize <= 0 ? 1 : (entries + pageSize - 1) / pageSize);
    }

    private static int clampPage(int page, int pages) {
        return Math.max(1, Math.min(page, pages));
    }

    private static boolean hasPermission(Player player, SlimefunItem item) {
        return Slimefun.getPermissionsService().hasPermission(player, item);
    }
}
