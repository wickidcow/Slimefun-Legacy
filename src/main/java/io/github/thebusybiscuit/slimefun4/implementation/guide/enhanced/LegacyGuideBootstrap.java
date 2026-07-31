package io.github.thebusybiscuit.slimefun4.implementation.guide.enhanced;

import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideImplementation;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.guide.CheatSheetSlimefunGuide;
import io.github.thebusybiscuit.slimefun4.implementation.guide.SurvivalSlimefunGuide;
import java.util.Map;
import javax.annotation.Nonnull;
import org.apache.commons.lang.Validate;

/** Registers either the native enhanced guide or the unchanged classic guide. */
public final class LegacyGuideBootstrap {

    private LegacyGuideBootstrap() {}

    public static void register(
            @Nonnull Slimefun plugin, @Nonnull Map<SlimefunGuideMode, SlimefunGuideImplementation> guides) {
        Validate.notNull(plugin, "The Plugin cannot be null!");
        Validate.notNull(guides, "The guide registry cannot be null!");

        LegacyGuideSettings.initialize(plugin);
        LegacyGuideBookmarks.initialize(plugin);

        if (LegacyGuideSettings.get().isEnabled()) {
            LegacyRecipeFillManager.initialize(plugin);
            guides.put(SlimefunGuideMode.SURVIVAL_MODE, new EnhancedSurvivalSlimefunGuide());
            guides.put(SlimefunGuideMode.CHEAT_MODE, new EnhancedCheatSheetSlimefunGuide());
            plugin.getLogger().info(
                    "Native enhanced guide enabled (JEG-style menus, smart search, bookmarks, unordered machine fill and Ancient Altar preparation).");

            if (plugin.getServer().getPluginManager().getPlugin("JustEnoughGuide") != null) {
                plugin.getLogger()
                        .warning(
                                "JustEnoughGuide is also installed. Remove its JAR before using Slimefun Legacy's native enhanced guide.");
            }
        } else {
            guides.put(SlimefunGuideMode.SURVIVAL_MODE, new SurvivalSlimefunGuide());
            guides.put(SlimefunGuideMode.CHEAT_MODE, new CheatSheetSlimefunGuide());
            plugin.getLogger().info("Classic Slimefun guide enabled by enhanced-guide.yml.");
        }
    }
}
