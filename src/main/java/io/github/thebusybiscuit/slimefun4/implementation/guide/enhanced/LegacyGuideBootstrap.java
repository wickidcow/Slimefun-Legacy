package io.github.thebusybiscuit.slimefun4.implementation.guide.enhanced;

import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideImplementation;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.guide.CheatSheetSlimefunGuide;
import io.github.thebusybiscuit.slimefun4.implementation.guide.IndexedSurvivalSlimefunGuide;
import java.util.Map;
import javax.annotation.Nonnull;
import org.apache.commons.lang.Validate;

/** Registers either the native enhanced guide or the classic guide with indexed search. */
public final class LegacyGuideBootstrap {

    private LegacyGuideBootstrap() {}

    public static void register(
            @Nonnull Slimefun plugin, @Nonnull Map<SlimefunGuideMode, SlimefunGuideImplementation> guides) {
        Validate.notNull(plugin, "The Plugin cannot be null!");
        Validate.notNull(guides, "The guide registry cannot be null!");

        LegacyGuideSettings.initialize(plugin);
        LegacyGuideBookmarks.initialize(plugin);

        if (LegacyGuideSettings.get().isEnabled()) {
            LegacyMachineRecipeBrowser.initialize(plugin);
            LegacyMachineInputFillManager.initialize(plugin);
            LegacyRecipeFillManager.initialize(plugin);
            guides.put(SlimefunGuideMode.SURVIVAL_MODE, new IndexedEnhancedSurvivalSlimefunGuide());
            guides.put(SlimefunGuideMode.CHEAT_MODE, new EnhancedCheatSheetSlimefunGuide());
            plugin.getLogger()
                    .info(
                            "Native enhanced guide enabled (indexed smart search, JEG-style menus, bookmarks, machine recipe browsing, standard and custom-addon GUI machine input fill, unordered machine fill and Ancient Altar preparation).");

            if (plugin.getServer().getPluginManager().getPlugin("JustEnoughGuide") != null) {
                plugin.getLogger()
                        .warning(
                                "JustEnoughGuide is also installed. Remove its JAR before using Slimefun Legacy's native enhanced guide.");
            }
        } else {
            guides.put(SlimefunGuideMode.SURVIVAL_MODE, new IndexedSurvivalSlimefunGuide());
            guides.put(SlimefunGuideMode.CHEAT_MODE, new CheatSheetSlimefunGuide());
            plugin.getLogger().info("Classic Slimefun guide enabled with indexed search by enhanced-guide.yml.");
        }
    }
}
