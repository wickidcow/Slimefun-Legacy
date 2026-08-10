package io.github.thebusybiscuit.slimefun4.api.items.groups;

import io.github.bakedlibs.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunAPI;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.core.guide.GuideHistory;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuide;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.guide.GuideRuntimeGuard;
import io.github.thebusybiscuit.slimefun4.implementation.guide.SurvivalSlimefunGuide;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import org.apache.commons.lang.Validate;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@SlimefunAPI
public class NestedItemGroup extends FlexItemGroup {

    private static final int GROUP_SIZE = 36;
    private final List<SubItemGroup> subGroups = new ArrayList<>();

    @ParametersAreNonnullByDefault
    public NestedItemGroup(NamespacedKey key, ItemStack item) {
        this(key, item, 3);
    }

    @ParametersAreNonnullByDefault
    public NestedItemGroup(NamespacedKey key, ItemStack item, int tier) {
        super(key, item, tier);
    }

    public void addSubGroup(@Nonnull SubItemGroup group) {
        Validate.notNull(group, "The sub item group cannot be null!");
        subGroups.add(group);
    }

    public void removeSubGroup(@Nonnull SubItemGroup group) {
        Validate.notNull(group, "The sub item group cannot be null!");
        subGroups.remove(group);
    }

    /** Returns whether at least one subgroup can be rendered without allowing one broken addon child to abort it. */
    public final boolean hasVisibleSubGroups(@Nonnull Player player) {
        for (SubItemGroup subGroup : subGroups) {
            try {
                if (subGroup.isVisibleInNested(player)) {
                    return true;
                }
            } catch (RuntimeException | LinkageError | StackOverflowError failure) {
                Slimefun.logger()
                        .warning("Could not evaluate nested guide subgroup visibility: " + safeKey(subGroup)
                                + " [class=" + subGroup.getClass().getName() + ", failure="
                                + failure.getClass().getName() + ']');
            }
        }
        return false;
    }

    @Override
    @ParametersAreNonnullByDefault
    public boolean isVisible(Player player, PlayerProfile profile, SlimefunGuideMode mode) {
        return mode == SlimefunGuideMode.SURVIVAL_MODE;
    }

    @Override
    @ParametersAreNonnullByDefault
    public void open(Player player, PlayerProfile profile, SlimefunGuideMode mode) {
        GuideRuntimeGuard.run(
                profile, mode, "open nested item group page 1", this, () -> openGuide(player, profile, mode, 1));
    }

    @ParametersAreNonnullByDefault
    private void openGuide(Player player, PlayerProfile profile, SlimefunGuideMode mode, int page) {
        GuideHistory history = profile.getGuideHistory();
        if (mode == SlimefunGuideMode.SURVIVAL_MODE) {
            history.add(this, page);
        }

        ChestMenu menu = new ChestMenu(Slimefun.getLocalization().getMessage(player, "guide.title.main"));
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
        menu.addMenuClickHandler(1, (pl, slot, item, action) -> {
            SlimefunGuide.openMainMenu(profile, mode, history.getMainMenuPage());
            return false;
        });

        int index = 9;
        int target = (GROUP_SIZE * (page - 1)) - 1;
        while (target < (subGroups.size() - 1) && index < GROUP_SIZE + 9) {
            target++;

            SubItemGroup itemGroup = subGroups.get(target);
            boolean visible = GuideRuntimeGuard.getOrDefault(
                    profile,
                    mode,
                    "evaluate nested subgroup visibility",
                    itemGroup,
                    false,
                    () -> itemGroup.isVisibleInNested(player));
            if (!visible) {
                continue;
            }

            ItemStack icon = GuideRuntimeGuard.getOrDefault(
                    profile,
                    mode,
                    "render nested subgroup icon",
                    itemGroup,
                    brokenCategoryIcon(itemGroup),
                    () -> itemGroup.getItem(player));
            menu.addItem(index, icon);
            menu.addMenuClickHandler(index, (pl, slot, item, action) -> {
                SlimefunGuide.openItemGroup(profile, itemGroup, mode, 1);
                return false;
            });
            index++;
        }

        int pages = target == subGroups.size() - 1 ? page : (subGroups.size() - 1) / GROUP_SIZE + 1;
        menu.addItem(46, ChestMenuUtils.getPreviousButton(player, page, pages));
        menu.addMenuClickHandler(46, (pl, slot, item, action) -> {
            int next = page - 1;
            if (next != page && next > 0) {
                GuideRuntimeGuard.run(
                        profile,
                        mode,
                        "open nested item group page " + next,
                        this,
                        () -> openGuide(player, profile, mode, next));
            }
            return false;
        });

        menu.addItem(52, ChestMenuUtils.getNextButton(player, page, pages));
        menu.addMenuClickHandler(52, (pl, slot, item, action) -> {
            int next = page + 1;
            if (next != page && next <= pages) {
                GuideRuntimeGuard.run(
                        profile,
                        mode,
                        "open nested item group page " + next,
                        this,
                        () -> openGuide(player, profile, mode, next));
            }
            return false;
        });

        menu.open(player);
    }

    private static @Nonnull ItemStack brokenCategoryIcon(@Nonnull ItemGroup group) {
        return new CustomItemStack(
                Material.BARRIER,
                "&4Broken guide category",
                "",
                "&7This addon category could not be rendered.",
                "&7Check the server console for details.",
                "",
                "&8" + safeKey(group));
    }

    private static @Nonnull String safeKey(@Nonnull ItemGroup group) {
        try {
            NamespacedKey key = group.getKey();
            return key == null ? "<null-key>" : key.toString();
        } catch (RuntimeException | LinkageError failure) {
            return "<unreadable-key:" + group.getClass().getName() + '>';
        }
    }
}
