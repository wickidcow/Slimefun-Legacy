package io.github.thebusybiscuit.slimefun4.implementation.guide.enhanced;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Persistent per-player guide bookmarks. Item ids are stored instead of ItemStacks so addon updates do not corrupt
 * saved entries.
 */
public final class LegacyGuideBookmarks {

    private static LegacyGuideBookmarks instance;

    private final Slimefun plugin;
    private final File file;
    private final YamlConfiguration data;

    private LegacyGuideBookmarks(@Nonnull Slimefun plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "guide-bookmarks.yml");
        data = YamlConfiguration.loadConfiguration(file);
    }

    public static synchronized void initialize(@Nonnull Slimefun plugin) {
        instance = new LegacyGuideBookmarks(plugin);
    }

    public static @Nonnull LegacyGuideBookmarks get() {
        if (instance == null) {
            throw new IllegalStateException("Enhanced guide bookmarks were accessed before initialization");
        }
        return instance;
    }

    public synchronized boolean contains(@Nonnull UUID playerId, @Nonnull String itemId) {
        return read(playerId).contains(itemId);
    }

    public synchronized int size(@Nonnull UUID playerId) {
        return read(playerId).size();
    }

    public synchronized @Nonnull List<String> getBookmarks(@Nonnull UUID playerId) {
        return new ArrayList<>(read(playerId));
    }

    /**
     * @return true when the item was added, false when it was removed
     */
    public synchronized boolean toggle(@Nonnull UUID playerId, @Nonnull String itemId) {
        Set<String> ids = read(playerId);
        boolean added;
        if (ids.remove(itemId)) {
            added = false;
        } else {
            ids.add(itemId);
            added = true;
        }

        data.set(playerId.toString(), new ArrayList<>(ids));
        save();
        return added;
    }

    private @Nonnull Set<String> read(@Nonnull UUID playerId) {
        return new LinkedHashSet<>(data.getStringList(playerId.toString()));
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save Slimefun Legacy guide bookmarks", exception);
        }
    }
}
