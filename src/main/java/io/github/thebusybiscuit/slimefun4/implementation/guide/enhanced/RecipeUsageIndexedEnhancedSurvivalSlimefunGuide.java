package io.github.thebusybiscuit.slimefun4.implementation.guide.enhanced;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.entity.Player;

/** Adds the 4.2 recipe-usage control without changing the indexed guide's normal search/render path. */
public final class RecipeUsageIndexedEnhancedSurvivalSlimefunGuide extends IndexedEnhancedSurvivalSlimefunGuide {

    @Override
    @ParametersAreNonnullByDefault
    public void displayItem(PlayerProfile profile, SlimefunItem item, boolean addToHistory) {
        super.displayItem(profile, item, addToHistory);

        Player player = profile.getPlayer();
        if (player != null) {
            LegacyRecipeUsageBrowser.get().decorateItemPage(player, profile, this, item);
        }
    }
}
