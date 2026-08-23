package io.github.thebusybiscuit.slimefun4.implementation.guide.enhanced;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.utils.itemstack.SlimefunGuideItem;
import java.util.List;
import javax.annotation.Nonnull;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Admin variant of the native enhanced guide. */
public final class EnhancedCheatSheetSlimefunGuide extends IndexedEnhancedSurvivalSlimefunGuide {

    private final ItemStack guideItem;

    public EnhancedCheatSheetSlimefunGuide() {
        guideItem = new SlimefunGuideItem(this, "&cSlimefun Legacy Guide &4(Enhanced Cheat Mode)");
    }

    @Override
    protected @Nonnull List<ItemGroup> getVisibleItemGroups(@Nonnull Player player, @Nonnull PlayerProfile profile) {
        return getVisibleItemGroups(player, profile, SlimefunGuideMode.SURVIVAL_MODE);
    }

    @Override
    public @Nonnull SlimefunGuideMode getMode() {
        return SlimefunGuideMode.CHEAT_MODE;
    }

    @Override
    public @Nonnull ItemStack getItem() {
        return guideItem;
    }
}
