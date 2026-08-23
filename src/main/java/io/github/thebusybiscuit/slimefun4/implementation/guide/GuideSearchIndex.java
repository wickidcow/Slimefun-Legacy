package io.github.thebusybiscuit.slimefun4.implementation.guide;

import io.github.bakedlibs.dough.items.ItemUtils;
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Shared cached search index for the classic and enhanced Slimefun guides.
 *
 * <p>The index stores stable searchable metadata once and keeps both registry order and a name-sorted view. Player,
 * world and category visibility checks remain on the server thread at query time. Player-sensitive category and recipe
 * labels are resolved lazily only when a query actually needs them.
 */
public final class GuideSearchIndex {

    private static final GuideSearchIndex INSTANCE = new GuideSearchIndex();

    private volatile Snapshot snapshot = Snapshot.empty();

    private GuideSearchIndex() {}

    public static @Nonnull GuideSearchIndex get() {
        return INSTANCE;
    }

    /** Returns classic name matches in registry order, capped to the requested number of results. */
    public @Nonnull List<SlimefunItem> searchByName(
            @Nonnull String input, @Nonnull Predicate<SlimefunItem> visibility, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        String searchTerm = normalize(input);
        List<SlimefunItem> matches = new ArrayList<>(Math.min(limit, 36));
        for (Entry entry : currentSnapshot().registryOrder()) {
            SlimefunItem item = entry.item();
            if (visibility.test(item) && entry.matchesName(searchTerm)) {
                matches.add(item);
                if (matches.size() >= limit) {
                    break;
                }
            }
        }
        return matches;
    }

    /** Returns enhanced smart-search matches in pre-sorted name order. */
    public @Nonnull List<SlimefunItem> searchSmart(
            @Nonnull Player player, @Nonnull String input, @Nonnull Predicate<SlimefunItem> visibility) {
        SearchQuery query = SearchQuery.parse(input);
        List<SlimefunItem> matches = new ArrayList<>();
        for (Entry entry : currentSnapshot().sortedByName()) {
            SlimefunItem item = entry.item();
            if (visibility.test(item) && entry.matchesSmart(player, query)) {
                matches.add(item);
            }
        }
        return matches;
    }

    /** Drops the snapshot so the next search rebuilds it from the enabled-item registry. */
    public synchronized void invalidate() {
        snapshot = Snapshot.empty();
    }

    public static @Nonnull String normalize(String input) {
        String stripped = ChatColor.stripColor(input == null ? "" : input);
        return stripped == null ? "" : stripped.toLowerCase(Locale.ROOT).trim();
    }

    public static @Nonnull String getAddonName(@Nonnull SlimefunItem item) {
        try {
            SlimefunAddon addon = item.getAddon();
            return addon == null ? "Slimefun" : addon.getName();
        } catch (RuntimeException | LinkageError ignored) {
            return "";
        }
    }

    private @Nonnull Snapshot currentSnapshot() {
        Collection<SlimefunItem> enabledItems = Slimefun.getRegistry().getEnabledSlimefunItems();
        Snapshot current = snapshot;
        if (current.registrySize() == enabledItems.size()) {
            return current;
        }

        synchronized (this) {
            current = snapshot;
            if (current.registrySize() == enabledItems.size()) {
                return current;
            }

            IdentityHashMap<SlimefunItem, Entry> entriesByItem = new IdentityHashMap<>();
            List<Entry> registryOrder = new ArrayList<>(enabledItems.size());
            for (SlimefunItem item : enabledItems) {
                Entry entry = current.entriesByItem().get(item);
                if (entry == null) {
                    entry = Entry.create(item);
                }
                entriesByItem.put(item, entry);
                registryOrder.add(entry);
            }

            List<Entry> sortedByName = new ArrayList<>(registryOrder);
            sortedByName.sort(Comparator.comparing(Entry::name));

            Snapshot rebuilt = new Snapshot(
                    enabledItems.size(),
                    List.copyOf(registryOrder),
                    List.copyOf(sortedByName),
                    entriesByItem);
            snapshot = rebuilt;
            return rebuilt;
        }
    }

    static boolean matchesSmart(
            @Nonnull SearchQuery query,
            @Nonnull String name,
            @Nonnull String id,
            @Nonnull String addon,
            @Nonnull String lore,
            @Nonnull Supplier<String> groupSupplier,
            @Nonnull Supplier<String> recipeSupplier) {
        return switch (query.filter()) {
            case ID -> id.contains(query.value());
            case ADDON -> addon.contains(query.value());
            case GROUP -> groupSupplier.get().contains(query.value());
            case RECIPE -> recipeSupplier.get().contains(query.value());
            case ANY -> matchesAny(query.tokens(), name, id, addon, lore, groupSupplier, recipeSupplier);
        };
    }

    private static boolean matchesAny(
            String[] tokens,
            String name,
            String id,
            String addon,
            String lore,
            Supplier<String> groupSupplier,
            Supplier<String> recipeSupplier) {
        if (tokens.length == 0) {
            return true;
        }

        String group = null;
        String recipe = null;
        for (String token : tokens) {
            if (token.isBlank() || name.contains(token) || id.contains(token) || addon.contains(token) || lore.contains(token)) {
                continue;
            }

            if (group == null) {
                group = groupSupplier.get();
            }
            if (group.contains(token)) {
                continue;
            }

            if (recipe == null) {
                recipe = recipeSupplier.get();
            }
            if (!recipe.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private record Entry(SlimefunItem item, String name, String id, String addon, String lore) {

        private static @Nonnull Entry create(@Nonnull SlimefunItem item) {
            return new Entry(
                    item,
                    safeNormalize(item::getItemName),
                    safeNormalize(item::getId),
                    normalize(getAddonName(item)),
                    safeLore(item));
        }

        private boolean matchesName(String query) {
            return !name.isEmpty() && name.contains(query);
        }

        private boolean matchesSmart(Player player, SearchQuery query) {
            return GuideSearchIndex.matchesSmart(
                    query,
                    name,
                    id,
                    addon,
                    lore,
                    () -> normalizeGroup(item, player),
                    () -> normalizeRecipe(item, player));
        }
    }

    private static @Nonnull String safeNormalize(@Nonnull Supplier<String> supplier) {
        try {
            return normalize(supplier.get());
        } catch (RuntimeException | LinkageError ignored) {
            return "";
        }
    }

    private static @Nonnull String safeLore(@Nonnull SlimefunItem item) {
        try {
            ItemStack stack = item.getItem();
            ItemMeta meta = stack == null ? null : stack.getItemMeta();
            if (meta == null || !meta.hasLore() || meta.getLore() == null) {
                return "";
            }
            return normalize(String.join(" ", meta.getLore()));
        } catch (RuntimeException | LinkageError ignored) {
            return "";
        }
    }

    private static @Nonnull String normalizeGroup(@Nonnull SlimefunItem item, @Nonnull Player player) {
        try {
            return normalize(item.getItemGroup().getDisplayName(player));
        } catch (RuntimeException | LinkageError ignored) {
            return "";
        }
    }

    private static @Nonnull String normalizeRecipe(@Nonnull SlimefunItem item, @Nonnull Player player) {
        try {
            return normalize(ItemUtils.getItemName(item.getRecipeType().getItem(player)));
        } catch (RuntimeException | LinkageError ignored) {
            return "";
        }
    }

    enum Filter {
        ANY,
        ID,
        ADDON,
        GROUP,
        RECIPE
    }

    record SearchQuery(Filter filter, String value, String[] tokens) {

        static @Nonnull SearchQuery parse(@Nonnull String input) {
            String normalized = normalize(input);
            if (normalized.startsWith("id:")) {
                return filtered(Filter.ID, normalized.substring(3));
            }
            if (normalized.startsWith("addon:")) {
                return filtered(Filter.ADDON, normalized.substring(6));
            }
            if (normalized.startsWith("group:")) {
                return filtered(Filter.GROUP, normalized.substring(6));
            }
            if (normalized.startsWith("recipe:")) {
                return filtered(Filter.RECIPE, normalized.substring(7));
            }
            return new SearchQuery(
                    Filter.ANY,
                    normalized,
                    normalized.isBlank() ? new String[0] : normalized.split("\\s+"));
        }

        private static @Nonnull SearchQuery filtered(@Nonnull Filter filter, @Nonnull String value) {
            return new SearchQuery(filter, value.trim(), new String[0]);
        }
    }

    private record Snapshot(
            int registrySize,
            List<Entry> registryOrder,
            List<Entry> sortedByName,
            Map<SlimefunItem, Entry> entriesByItem) {

        private static @Nonnull Snapshot empty() {
            return new Snapshot(-1, List.of(), List.of(), new IdentityHashMap<>());
        }
    }
}
