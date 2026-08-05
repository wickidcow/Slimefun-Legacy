package io.github.thebusybiscuit.slimefun4.core.guide;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.implementation.guide.GuideRuntimeGuard;
import java.util.Deque;
import java.util.LinkedList;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.lang.Validate;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * {@link GuideHistory} represents the browsing history of a {@link Player} through the
 * {@link SlimefunGuide}.
 *
 * @author TheBusyBiscuit
 *
 * @see SlimefunGuide
 * @see PlayerProfile
 */
public class GuideHistory {

    private final PlayerProfile profile;
    private final Deque<GuideEntry<?>> queue = new LinkedList<>();
    private int mainMenuPage = 1;

    /**
     * This creates a new {@link GuideHistory} for the given {@link PlayerProfile}
     *
     * @param profile
     *            The {@link PlayerProfile} this {@link GuideHistory} was made for
     */
    public GuideHistory(@Nonnull PlayerProfile profile) {
        Validate.notNull(profile, "Cannot create a GuideHistory without a PlayerProfile!");
        this.profile = profile;
    }

    /** This method will clear this {@link GuideHistory} and remove all entries. */
    public void clear() {
        queue.clear();
    }

    /**
     * This method sets the page of the main menu of this {@link GuideHistory}
     *
     * @param page
     *            The current page of the main menu that should be stored
     */
    public void setMainMenuPage(int page) {
        Validate.isTrue(page >= 1, "page must be greater than 0!");
        mainMenuPage = page;
    }

    /** @return The current main menu page of this {@link GuideHistory} */
    public int getMainMenuPage() {
        return mainMenuPage;
    }

    public void add(@Nonnull ItemGroup itemGroup, int page) {
        refresh(itemGroup, page);
    }

    public void add(@Nonnull ItemStack item, int page) {
        refresh(item, page);
    }

    public void add(@Nonnull SlimefunItem item) {
        Validate.notNull(item, "Cannot add a non-existing SlimefunItem to the GuideHistory!");
        queue.add(new GuideEntry<>(item, 0));
    }

    public void add(@Nonnull String searchTerm) {
        Validate.notNull(searchTerm, "Cannot add an empty Search Term to the GuideHistory!");
        queue.add(new GuideEntry<>(searchTerm, 0));
    }

    private <T> void refresh(@Nonnull T object, int page) {
        Validate.notNull(object, "Cannot add a null Entry to the GuideHistory!");
        Validate.isTrue(page >= 0, "page must not be negative!");

        GuideEntry<?> lastEntry = getLastEntry(false);
        if (lastEntry != null && lastEntry.getIndexedObject().equals(object)) {
            lastEntry.setPage(page);
        } else {
            queue.add(new GuideEntry<>(object, page));
        }
    }

    public int size() {
        return queue.size();
    }

    @Nullable
    private GuideEntry<?> getLastEntry(boolean remove) {
        if (remove && !queue.isEmpty()) {
            queue.removeLast();
        }
        return queue.isEmpty() ? null : queue.getLast();
    }

    /** Opens the last stored guide entry through the runtime guard. */
    public void openLastEntry(@Nonnull SlimefunGuideImplementation guide) {
        GuideEntry<?> entry = getLastEntry(false);
        open(guide, entry);
    }

    /** Rewinds one guide entry and opens it through the runtime guard. */
    public void goBack(@Nonnull SlimefunGuideImplementation guide) {
        GuideEntry<?> entry = getLastEntry(true);
        open(guide, entry);
    }

    private <T> void open(@Nonnull SlimefunGuideImplementation guide, @Nullable GuideEntry<T> entry) {
        SlimefunGuideMode mode = guide.getMode();

        if (entry == null) {
            GuideRuntimeGuard.run(
                    profile,
                    mode,
                    "restore history main menu page " + mainMenuPage,
                    null,
                    () -> guide.openMainMenu(profile, mainMenuPage));
        } else if (entry.getIndexedObject() instanceof ItemGroup group) {
            GuideRuntimeGuard.run(
                    profile,
                    mode,
                    "restore history item group page " + entry.getPage(),
                    group,
                    () -> guide.openItemGroup(profile, group, entry.getPage()));
        } else if (entry.getIndexedObject() instanceof SlimefunItem item) {
            GuideRuntimeGuard.run(
                    profile,
                    mode,
                    "restore history Slimefun item " + item.getId(),
                    item.getItemGroup(),
                    () -> guide.displayItem(profile, item, false));
        } else if (entry.getIndexedObject() instanceof ItemStack stack) {
            GuideRuntimeGuard.run(
                    profile,
                    mode,
                    "restore history recipe page " + entry.getPage(),
                    null,
                    () -> guide.displayItem(profile, stack, entry.getPage(), false));
        } else if (entry.getIndexedObject() instanceof String query) {
            GuideRuntimeGuard.run(
                    profile,
                    mode,
                    "restore history search",
                    null,
                    () -> guide.openSearch(profile, query, false));
        } else {
            throw new IllegalStateException("Unknown GuideHistory entry: " + entry.getIndexedObject());
        }
    }
}
