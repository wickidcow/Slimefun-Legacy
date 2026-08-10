package io.github.thebusybiscuit.slimefun4.implementation.guide;

import io.github.bakedlibs.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.groups.FlexItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.groups.NestedItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.groups.SubItemGroup;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuide;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.ChatUtils;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * A generated cheat-guide folder that groups every visible category registered by one Slimefun addon.
 *
 * <p>These folders are created only for guide display and are never registered in the global item-group registry.
 */
public final class CheatAddonItemGroup extends FlexItemGroup {

    private static final int GROUPS_PER_PAGE = 36;

    private final SlimefunAddon addon;
    private final String addonName;
    private final List<ItemGroup> groups;

    private CheatAddonItemGroup(@Nonnull SlimefunAddon addon, @Nonnull List<ItemGroup> groups) {
        super(new NamespacedKey(addon.getJavaPlugin(), "cheat_guide_addon"), new ItemStack(Material.CHEST), 0);

        this.addon = addon;
        this.addonName = addon.getName();
        this.groups = List.copyOf(groups);
    }

    /**
     * Builds the addon-folder view for cheat mode.
     *
     * @param player
     *            Player opening the guide
     * @param profile
     *            Player profile
     * @param mode
     *            Current guide mode
     *
     * @return One generated folder per addon, plus any unusual unregistered groups as a fallback
     */
    @ParametersAreNonnullByDefault
    public static List<ItemGroup> createAddonFolders(Player player, PlayerProfile profile, SlimefunGuideMode mode) {
        Map<SlimefunAddon, List<ItemGroup>> addonGroups = new LinkedHashMap<>();
        List<ItemGroup> fallbackGroups = new ArrayList<>();

        for (ItemGroup group : Slimefun.getRegistry().getAllItemGroups()) {
            if (!isVisibleTopLevelGroup(group, player, profile, mode)) {
                continue;
            }

            SlimefunAddon addon = group.getAddon();
            if (addon == null) {
                fallbackGroups.add(group);
            } else {
                addonGroups.computeIfAbsent(addon, ignored -> new ArrayList<>()).add(group);
            }
        }

        List<ItemGroup> folders = addonGroups.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().getName(), String.CASE_INSENSITIVE_ORDER))
                .map(entry -> (ItemGroup) new CheatAddonItemGroup(entry.getKey(), entry.getValue()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        folders.addAll(fallbackGroups);
        return folders;
    }

    @ParametersAreNonnullByDefault
    private static boolean isVisibleTopLevelGroup(
            ItemGroup group, Player player, PlayerProfile profile, SlimefunGuideMode mode) {
        if (group instanceof SubItemGroup) {
            // Child categories stay inside their real NestedItemGroup.
            return false;
        }

        if (group instanceof NestedItemGroup nestedItemGroup) {
            return nestedItemGroup.hasVisibleSubGroups(player);
        }

        if (group instanceof FlexItemGroup flexItemGroup) {
            return flexItemGroup.isVisible(player, profile, mode);
        }

        return group.isVisible(player);
    }

    @Override
    public @Nonnull ItemStack getItem(@Nonnull Player player) {
        String categoryLabel = groups.size() == 1 ? "category" : "categories";
        return new CustomItemStack(
                Material.CHEST,
                ChatColor.GOLD + addonName,
                "",
                ChatColor.GRAY + String.valueOf(groups.size()) + ' ' + categoryLabel,
                ChatColor.GRAY + "Addon version: " + addon.getPluginVersion(),
                "",
                ChatColor.GREEN + "\u21E8 Open addon categories");
    }

    @Override
    @ParametersAreNonnullByDefault
    public boolean isVisible(Player player, PlayerProfile profile, SlimefunGuideMode mode) {
        return mode == SlimefunGuideMode.CHEAT_MODE && !groups.isEmpty();
    }

    @Override
    @ParametersAreNonnullByDefault
    public void open(Player player, PlayerProfile profile, SlimefunGuideMode mode) {
        openPage(player, profile, mode, 1);
    }

    @ParametersAreNonnullByDefault
    private void openPage(Player player, PlayerProfile profile, SlimefunGuideMode mode, int requestedPage) {
        int pages = Math.max(1, (groups.size() + GROUPS_PER_PAGE - 1) / GROUPS_PER_PAGE);
        int page = Math.max(1, Math.min(requestedPage, pages));

        ChestMenu menu = new ChestMenu(ChatUtils.crop(ChatColor.DARK_GREEN, addonName));
        SurvivalSlimefunGuide guide =
                (SurvivalSlimefunGuide) Slimefun.getRegistry().getSlimefunGuide(mode);

        menu.setEmptySlotsClickable(false);
        menu.addMenuOpeningHandler(SoundEffect.GUIDE_BUTTON_CLICK_SOUND::playFor);
        guide.createHeader(player, profile, menu);

        menu.addItem(
                1,
                new CustomItemStack(ChestMenuUtils.getBackButton(
                        player,
                        "",
                        ChatColor.GRAY + Slimefun.getLocalization().getMessage(player, "guide.back.guide"))));
        menu.addMenuClickHandler(1, (p, slot, item, action) -> {
            SlimefunGuide.openMainMenu(profile, mode, profile.getGuideHistory().getMainMenuPage());
            return false;
        });

        int start = (page - 1) * GROUPS_PER_PAGE;
        int end = Math.min(start + GROUPS_PER_PAGE, groups.size());
        int menuSlot = 9;

        for (int index = start; index < end; index++) {
            ItemGroup group = groups.get(index);
            menu.addItem(menuSlot, group.getItem(player));
            menu.addMenuClickHandler(menuSlot, (p, slot, item, action) -> {
                SlimefunGuide.openItemGroup(profile, group, mode, 1);
                return false;
            });
            menuSlot++;
        }

        menu.addItem(46, ChestMenuUtils.getPreviousButton(player, page, pages));
        menu.addMenuClickHandler(46, (p, slot, item, action) -> {
            if (page > 1) {
                openPage(player, profile, mode, page - 1);
            }
            return false;
        });

        menu.addItem(52, ChestMenuUtils.getNextButton(player, page, pages));
        menu.addMenuClickHandler(52, (p, slot, item, action) -> {
            if (page < pages) {
                openPage(player, profile, mode, page + 1);
            }
            return false;
        });

        menu.open(player);
    }
}
