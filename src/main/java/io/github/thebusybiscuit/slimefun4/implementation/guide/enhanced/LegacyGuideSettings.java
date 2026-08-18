package io.github.thebusybiscuit.slimefun4.implementation.guide.enhanced;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Immutable configuration for Slimefun Legacy's native enhanced guide.
 */
public final class LegacyGuideSettings {

    private static final List<String> DEFAULT_MAIN =
            List.of("BTBBBBRSB", "GGGGGGGGG", "GGGGGGGGG", "GGGGGGGGG", "GGGGGGGGG", "BPBBCBBNB");
    private static final List<String> DEFAULT_GROUP =
            List.of("BbBBBBRSB", "iiiiiiiii", "iiiiiiiii", "iiiiiiiii", "iiiiiiiii", "BPBBCBBNB");
    private static final List<String> DEFAULT_SEARCH = DEFAULT_GROUP;
    private static final List<String> DEFAULT_BOOKMARKS =
            List.of("BbBBBBRSB", "iiiiiiiii", "iiiiiiiii", "iiiiiiiii", "iiiiiiiii", "BPBBBBBNB");
    private static final String OLD_DEFAULT_CHEAT_TITLE = "&c&lSlimefun Legacy Guide &4(Cheat Mode)";
    private static final String DEFAULT_CHEAT_TITLE = "&c&lSlimefun Guide &4(Cheat Mode)";

    private static LegacyGuideSettings instance;

    private final boolean enabled;
    private final boolean bookmarks;
    private final boolean smartSearch;
    private final boolean displayItemId;
    private final boolean displayAddon;
    private final boolean machineRecipeBrowser;
    private final boolean machineInputFill;
    private final boolean closeGuideAfterMachineInputFill;
    private final int machineInputFillTargetRange;
    private final int machineInputFillMaximumSets;
    private final boolean recipeFill;
    private final boolean recipeFillUnorderedMachines;
    private final boolean recipeFillAncientAltar;
    private final boolean prepareAltarCatalystInHand;
    private final boolean showSubRecipeHints;
    private final boolean closeGuideAfterRecipeFill;
    private final int recipeFillTargetRange;
    private final int recipeFillMaximumSets;
    private final int recipeFillSessionSeconds;
    private final int recipeFillAltarLockSeconds;
    private final int recipeFillMaximumMissingLines;
    private final String survivalTitle;
    private final String cheatTitle;
    private final String searchTitle;
    private final String bookmarksTitle;
    private final List<String> mainFormat;
    private final List<String> groupFormat;
    private final List<String> searchFormat;
    private final List<String> bookmarksFormat;

    private LegacyGuideSettings(@Nonnull YamlConfiguration config) {
        enabled = config.getBoolean("enabled", true);
        bookmarks = config.getBoolean("features.bookmarks", true);
        smartSearch = config.getBoolean("features.smart-search", true);
        displayItemId = config.getBoolean("features.display-item-id", true);
        displayAddon = config.getBoolean("features.display-addon", true);
        machineRecipeBrowser = config.getBoolean("features.machine-recipes.enabled", true);
        machineInputFill = config.getBoolean("features.machine-input-fill.enabled", true);
        closeGuideAfterMachineInputFill = config.getBoolean("features.machine-input-fill.close-guide-on-success", true);
        machineInputFillTargetRange = clamp(config.getInt("features.machine-input-fill.target-range", 6), 2, 12);
        machineInputFillMaximumSets = clamp(config.getInt("features.machine-input-fill.maximum-sets", 64), 1, 64);
        recipeFill = config.getBoolean("features.recipe-fill.enabled", true);
        recipeFillUnorderedMachines = config.getBoolean("features.recipe-fill.unordered-machines", true);
        recipeFillAncientAltar = config.getBoolean("features.recipe-fill.ancient-altar.enabled", true);
        prepareAltarCatalystInHand =
                config.getBoolean("features.recipe-fill.ancient-altar.prepare-catalyst-in-hand", true);
        showSubRecipeHints = config.getBoolean("features.recipe-fill.missing-report.show-sub-recipe-hints", true);
        closeGuideAfterRecipeFill = config.getBoolean("features.recipe-fill.close-guide-on-success", true);
        recipeFillTargetRange = clamp(config.getInt("features.recipe-fill.target-range", 6), 2, 12);
        recipeFillMaximumSets = clamp(config.getInt("features.recipe-fill.maximum-sets", 64), 1, 64);
        recipeFillSessionSeconds = clamp(config.getInt("features.recipe-fill.session-seconds", 120), 15, 600);
        recipeFillAltarLockSeconds =
                clamp(config.getInt("features.recipe-fill.ancient-altar.activation-lock-seconds", 15), 5, 60);
        recipeFillMaximumMissingLines =
                clamp(config.getInt("features.recipe-fill.missing-report.maximum-lore-lines", 4), 1, 8);
        survivalTitle = color(config.getString("titles.survival", "&2&lSlimefun Legacy Guide"));

        String configuredCheatTitle = config.getString("titles.cheat", DEFAULT_CHEAT_TITLE);
        if (OLD_DEFAULT_CHEAT_TITLE.equals(configuredCheatTitle)) {
            configuredCheatTitle = DEFAULT_CHEAT_TITLE;
        }
        cheatTitle = color(configuredCheatTitle);

        searchTitle = color(config.getString("titles.search", "&2&lSearch &8- &f%query%"));
        bookmarksTitle = color(config.getString("titles.bookmarks", "&6&lBookmarked Items"));
        mainFormat = validate(config.getStringList("format.main"), DEFAULT_MAIN);
        groupFormat = validate(config.getStringList("format.group"), DEFAULT_GROUP);
        searchFormat = validate(config.getStringList("format.search"), DEFAULT_SEARCH);
        bookmarksFormat = validate(config.getStringList("format.bookmarks"), DEFAULT_BOOKMARKS);
    }

    public static synchronized void initialize(@Nonnull Slimefun plugin) {
        File file = new File(plugin.getDataFolder(), "enhanced-guide.yml");
        if (!file.isFile()) {
            plugin.saveResource("enhanced-guide.yml", false);
        }

        instance = new LegacyGuideSettings(YamlConfiguration.loadConfiguration(file));
    }

    public static @Nonnull LegacyGuideSettings get() {
        if (instance == null) {
            throw new IllegalStateException("Enhanced guide settings were accessed before initialization");
        }
        return instance;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean hasBookmarks() {
        return bookmarks;
    }

    public boolean hasSmartSearch() {
        return smartSearch;
    }

    public boolean shouldDisplayItemId() {
        return displayItemId;
    }

    public boolean shouldDisplayAddon() {
        return displayAddon;
    }

    public boolean hasMachineRecipeBrowser() {
        return machineRecipeBrowser;
    }

    public boolean hasMachineInputFill() {
        return machineInputFill;
    }

    public boolean shouldCloseGuideAfterMachineInputFill() {
        return closeGuideAfterMachineInputFill;
    }

    public int getMachineInputFillTargetRange() {
        return machineInputFillTargetRange;
    }

    public int getMachineInputFillMaximumSets() {
        return machineInputFillMaximumSets;
    }

    public boolean hasRecipeFill() {
        return recipeFill;
    }

    public boolean hasRecipeFillUnorderedMachines() {
        return recipeFillUnorderedMachines;
    }

    public boolean hasRecipeFillAncientAltar() {
        return recipeFillAncientAltar;
    }

    public boolean shouldPrepareAltarCatalystInHand() {
        return prepareAltarCatalystInHand;
    }

    public boolean shouldShowSubRecipeHints() {
        return showSubRecipeHints;
    }

    public boolean shouldCloseGuideAfterRecipeFill() {
        return closeGuideAfterRecipeFill;
    }

    public int getRecipeFillTargetRange() {
        return recipeFillTargetRange;
    }

    public int getRecipeFillMaximumSets() {
        return recipeFillMaximumSets;
    }

    public int getRecipeFillSessionSeconds() {
        return recipeFillSessionSeconds;
    }

    public int getRecipeFillAltarLockSeconds() {
        return recipeFillAltarLockSeconds;
    }

    public int getRecipeFillMaximumMissingLines() {
        return recipeFillMaximumMissingLines;
    }

    public @Nonnull String getSurvivalTitle() {
        return survivalTitle;
    }

    public @Nonnull String getCheatTitle() {
        return cheatTitle;
    }

    public @Nonnull String getSearchTitle(@Nonnull String query) {
        return searchTitle.replace("%query%", query);
    }

    public @Nonnull String getBookmarksTitle() {
        return bookmarksTitle;
    }

    public @Nonnull List<String> getMainFormat() {
        return mainFormat;
    }

    public @Nonnull List<String> getGroupFormat() {
        return groupFormat;
    }

    public @Nonnull List<String> getSearchFormat() {
        return searchFormat;
    }

    public @Nonnull List<String> getBookmarksFormat() {
        return bookmarksFormat;
    }

    public @Nonnull List<Integer> findSlots(@Nonnull List<String> format, char marker) {
        List<Integer> slots = new ArrayList<>();
        for (int row = 0; row < format.size(); row++) {
            String line = format.get(row);
            for (int column = 0; column < line.length(); column++) {
                if (line.charAt(column) == marker) {
                    slots.add(row * 9 + column);
                }
            }
        }
        return slots;
    }

    private static @Nonnull List<String> validate(List<String> configured, List<String> fallback) {
        if (configured == null || configured.size() != 6) {
            return fallback;
        }

        for (String row : configured) {
            if (row == null || row.length() != 9) {
                return fallback;
            }
        }
        return Collections.unmodifiableList(new ArrayList<>(configured));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static @Nonnull String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value);
    }
}
