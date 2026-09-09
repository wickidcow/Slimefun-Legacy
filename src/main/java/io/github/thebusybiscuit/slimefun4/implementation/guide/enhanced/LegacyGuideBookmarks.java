package io.github.thebusybiscuit.slimefun4.implementation.guide.enhanced;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Persistent per-player guide bookmarks. Item ids are stored instead of ItemStacks so addon updates do not corrupt
 * saved entries.
 *
 * <p>Bookmarks are loaded lazily into memory per player. Guide rendering performs frequent membership checks, so
 * retaining the ordered set avoids rebuilding it from the YAML list for every displayed item while keeping the same
 * on-disk format and ordering semantics. Cached sets are discarded when a player leaves so long-running servers do
 * not retain bookmark state for every UUID seen since startup.
 */
public final class LegacyGuideBookmarks implements Listener {

    private static LegacyGuideBookmarks instance;

    private final Slimefun plugin;
    private final File file;
    private final YamlConfiguration data;
    private final Map<UUID, LinkedHashSet<String>> bookmarks = new HashMap<>();

    private LegacyGuideBookmarks(@Nonnull Slimefun plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "guide-bookmarks.yml");
        data = YamlConfiguration.loadConfiguration(file);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
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

    @EventHandler(priority = EventPriority.MONITOR)
    public synchronized void onPlayerQuit(@Nonnull PlayerQuitEvent event) {
        bookmarks.remove(event.getPlayer().getUniqueId());
    }

    private @Nonnull LinkedHashSet<String> read(@Nonnull UUID playerId) {
        return bookmarks.computeIfAbsent(
                playerId, id -> new LinkedHashSet<>(data.getStringList(id.toString())));
    }

    private void save() {
        try {
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Could not save Slimefun Legacy guide bookmarks", exception);
        }
    }
}
