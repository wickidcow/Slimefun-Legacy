package io.github.thebusybiscuit.slimefun4.implementation.guide.enhanced;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.api.recipes.machine.MachineRecipeDisplay;
import io.github.thebusybiscuit.slimefun4.api.recipes.machine.MachineRecipeIngredient;
import io.github.thebusybiscuit.slimefun4.api.recipes.machine.MachineRecipeProvider;
import io.github.thebusybiscuit.slimefun4.api.recipes.machine.MachineRecipeProviderRegistry;
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Provides the Enhanced Guide 4.2 reverse recipe browser.
 *
 * <p>Normal item pages remain intentionally cheap. Decorating a page only installs a protected button and checks an
 * already-built per-world cache. The first explicit click starts a bounded, incremental reverse-index build. Only a
 * small number of registered items are inspected per scheduled tick and a time budget can end a batch even earlier.
 * Concurrent requests for the same world join that one build instead of repeating provider scans.
 *
 * <p>Both ordinary Slimefun recipes and the normalized {@link MachineRecipeProviderRegistry} feed the same index, so
 * existing addon machine adapters participate without another compatibility layer. Completed indexes live for the
 * server lifetime unless {@link #invalidate()} is called; this deliberately avoids re-hashing the entire registry on
 * every guide click.
 */
public final class LegacyRecipeUsageBrowser implements Listener {

    private static final int[] BUTTON_SLOTS = {24, 17, 23, 18};
    private static final int[] LIST_SLOTS = {
        9, 10, 11, 12, 13, 14, 15, 16, 17,
        18, 19, 20, 21, 22, 23, 24, 25, 26,
        27, 28, 29, 30, 31, 32, 33, 34, 35,
        36, 37, 38, 39, 40, 41, 42, 43, 44
    };

    private static LegacyRecipeUsageBrowser instance;

    private final Slimefun plugin;
    private final NamespacedKey buttonKey;
    private final Map<UUID, ButtonContext> contexts = new ConcurrentHashMap<>();
    private final Map<UUID, UsageIndex> indexes = new ConcurrentHashMap<>();
    private final Map<UUID, IndexBuildState> builds = new ConcurrentHashMap<>();

    private LegacyRecipeUsageBrowser(@Nonnull Slimefun plugin) {
        this.plugin = plugin;
        buttonKey = new NamespacedKey(plugin, "enhanced_guide_recipe_usages");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public static synchronized void initialize(@Nonnull Slimefun plugin) {
        instance = new LegacyRecipeUsageBrowser(plugin);
    }

    public static @Nonnull LegacyRecipeUsageBrowser get() {
        if (instance == null) {
            throw new IllegalStateException("Enhanced guide recipe usages were accessed before initialization");
        }
        return instance;
    }

    /** Clears completed and in-flight indexes. No automatic registry scan is triggered. */
    public void invalidate() {
        indexes.clear();
        builds.clear();
    }

    public void decorateItemPage(
            @Nonnull Player player,
            @Nonnull PlayerProfile profile,
            @Nonnull EnhancedSurvivalSlimefunGuide guide,
            @Nonnull SlimefunItem target) {
        contexts.remove(player.getUniqueId());

        if (!LegacyGuideSettings.get().hasRecipeUsages()) {
            return;
        }

        Inventory inventory = player.getOpenInventory().getTopInventory();
        int buttonSlot = findButtonSlot(inventory);
        if (buttonSlot < 0) {
            return;
        }

        IngredientKey targetKey = ingredientKey(target.getItem());
        if (targetKey == null) {
            return;
        }

        UUID worldId = player.getWorld().getUID();
        UsageIndex cached = indexes.get(worldId);
        Integer cachedCount = cached == null ? null : cached.usages().getOrDefault(targetKey, List.of()).size();
        IndexBuildState build = builds.get(worldId);
        long expiresAt = System.currentTimeMillis() + LegacyGuideSettings.get().getRecipeFillSessionSeconds() * 1000L;
        contexts.put(
                player.getUniqueId(),
                new ButtonContext(inventory, profile, guide, target, buttonSlot, expiresAt));
        inventory.setItem(buttonSlot, build == null ? createButton(cachedCount) : createBuildingButton(build));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(@Nonnull InventoryClickEvent event) {
        HumanEntity human = event.getWhoClicked();
        if (!(human instanceof Player player)) {
            return;
        }

        ButtonContext context = contexts.get(player.getUniqueId());
        Inventory topInventory = event.getView().getTopInventory();
        if (context == null || context.guideInventory() != topInventory) {
            return;
        }

        boolean buttonClick = event.getClickedInventory() == topInventory
                && event.getRawSlot() == context.buttonSlot()
                && isUsageButton(event.getCurrentItem());

        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR
                || (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY && !buttonClick)) {
            event.setCancelled(true);
            return;
        }

        if (!buttonClick) {
            return;
        }

        event.setCancelled(true);
        if (context.expiresAt() < System.currentTimeMillis()) {
            contexts.remove(player.getUniqueId());
            player.sendMessage(ChatColor.RED + "This recipe-usage session expired. Reopen the item in the guide.");
            return;
        }

        IngredientKey targetKey = ingredientKey(context.target().getItem());
        if (targetKey == null) {
            return;
        }

        UUID worldId = player.getWorld().getUID();
        UsageIndex cached = indexes.get(worldId);
        if (cached != null) {
            openCachedUsages(player, context, targetKey, cached);
            return;
        }

        requestIndex(player, context, targetKey);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(@Nonnull InventoryDragEvent event) {
        ButtonContext context = contexts.get(event.getWhoClicked().getUniqueId());
        if (context != null
                && context.guideInventory() == event.getView().getTopInventory()
                && event.getRawSlots().contains(context.buttonSlot())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInventoryClose(@Nonnull InventoryCloseEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        ButtonContext context = contexts.get(playerId);
        if (context != null && context.guideInventory() == event.getInventory()) {
            contexts.remove(playerId, context);
            removeWaitingRequest(playerId);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(@Nonnull PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        contexts.remove(playerId);
        removeWaitingRequest(playerId);
    }

    private void requestIndex(
            @Nonnull Player player, @Nonnull ButtonContext context, @Nonnull IngredientKey targetKey) {
        UUID worldId = player.getWorld().getUID();
        IndexRequest request = new IndexRequest(player, context, targetKey);
        IndexBuildState existing = builds.get(worldId);
        if (existing != null) {
            existing.waiters.put(player.getUniqueId(), request);
            refreshBuildingButton(player, context, existing);
            player.sendMessage(ChatColor.GRAY + "Recipe usage index is still building ("
                    + existing.progressPercent() + "%).");
            return;
        }

        LegacyGuideSettings settings = LegacyGuideSettings.get();
        IndexBuildState created = new IndexBuildState(
                player.getWorld(),
                Slimefun.getRegistry().getEnabledSlimefunItems(),
                new ArrayList<>(MachineRecipeProviderRegistry.getProviders()),
                settings.getRecipeUsageIndexItemsPerTick(),
                settings.getRecipeUsageIndexBudgetMicros() * 1_000L);
        IndexBuildState state = builds.putIfAbsent(worldId, created);
        if (state == null) {
            state = created;
            state.waiters.put(player.getUniqueId(), request);
            refreshBuildingButton(player, context, state);
            player.sendMessage(ChatColor.GRAY
                    + "Building recipe usages gradually to protect server tick time. You can keep using the guide.");
            scheduleNextBatch(state);
        } else {
            state.waiters.put(player.getUniqueId(), request);
            refreshBuildingButton(player, context, state);
            player.sendMessage(ChatColor.GRAY + "Recipe usage index is already building ("
                    + state.progressPercent() + "%).");
        }
    }

    private void scheduleNextBatch(@Nonnull IndexBuildState state) {
        if (builds.get(state.world.getUID()) != state || !Slimefun.getSchedulerService().isAcceptingTasks()) {
            builds.remove(state.world.getUID(), state);
            return;
        }

        try {
            Slimefun.getSchedulerService().runLater(() -> runBuildBatch(state), 1L);
        } catch (RuntimeException exception) {
            builds.remove(state.world.getUID(), state);
            plugin.getLogger().log(Level.WARNING, "Could not schedule Enhanced Guide recipe-usage indexing", exception);
        }
    }

    private void runBuildBatch(@Nonnull IndexBuildState state) {
        UUID worldId = state.world.getUID();
        if (builds.get(worldId) != state) {
            return;
        }

        long batchStarted = System.nanoTime();
        int processed = 0;
        while (state.nextItem < state.itemLimit
                && state.nextItem < state.items.size()
                && processed < state.maxItemsPerTick) {
            SlimefunItem item = state.items.get(state.nextItem++);
            processed++;
            indexOneItem(state, item);

            if (processed > 0 && System.nanoTime() - batchStarted >= state.batchBudgetNanos) {
                break;
            }
        }
        state.batches++;

        if (state.nextItem < state.itemLimit && state.nextItem < state.items.size()) {
            scheduleNextBatch(state);
            return;
        }

        UsageIndex completed = new UsageIndex(Collections.unmodifiableMap(state.usages));
        indexes.put(worldId, completed);
        builds.remove(worldId, state);
        plugin.getLogger()
                .info("Built Enhanced Guide recipe-usage index for world '" + state.world.getName() + "': "
                        + state.usageCount + " usages across " + state.usages.size() + " ingredient keys over "
                        + state.batches + " scheduled batches.");

        for (IndexRequest request : new ArrayList<>(state.waiters.values())) {
            deliverCompletedIndex(request, completed);
        }
        state.waiters.clear();
    }

    private void indexOneItem(@Nonnull IndexBuildState state, @Nonnull SlimefunItem item) {
        try {
            if (item.isDisabledIn(state.world)) {
                return;
            }

            state.usageCount += indexSlimefunRecipe(state.usages, item);
            ResolvedMachineRecipes resolved =
                    resolveMachineRecipes(item, state.world, state.providers, state.warnedProviders);
            if (resolved != null) {
                state.usageCount += indexMachineRecipes(state.usages, item, resolved);
            }
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger()
                    .log(Level.WARNING, "Could not index recipe usages for Slimefun item " + item.getId(), exception);
        }
    }

    private int indexSlimefunRecipe(
            @Nonnull Map<IngredientKey, List<UsageEntry>> index, @Nonnull SlimefunItem resultItem) {
        ItemStack[] recipe = resultItem.getRecipe();
        if (recipe == null || recipe.length == 0) {
            return 0;
        }

        Map<IngredientKey, Integer> required = new HashMap<>();
        for (ItemStack ingredient : recipe) {
            IngredientKey key = ingredientKey(ingredient);
            if (key != null) {
                required.merge(key, normalizedAmount(ingredient), LegacyRecipeUsageBrowser::safeAdd);
            }
        }

        for (Map.Entry<IngredientKey, Integer> entry : required.entrySet()) {
            addUsage(index, entry.getKey(), UsageEntry.slimefunRecipe(resultItem, entry.getValue()));
        }
        return required.size();
    }

    private int indexMachineRecipes(
            @Nonnull Map<IngredientKey, List<UsageEntry>> index,
            @Nonnull SlimefunItem machine,
            @Nonnull ResolvedMachineRecipes resolved) {
        int additions = 0;
        for (int recipeIndex = 0; recipeIndex < resolved.recipes().size(); recipeIndex++) {
            MachineRecipeDisplay recipe = resolved.recipes().get(recipeIndex);
            Map<IngredientKey, Integer> required = new HashMap<>();

            for (MachineRecipeIngredient ingredient : recipe.getInputs()) {
                Map<IngredientKey, Integer> alternatives = new HashMap<>();
                for (ItemStack choice : ingredient.getChoices()) {
                    IngredientKey key = ingredientKey(choice);
                    if (key != null) {
                        alternatives.merge(key, normalizedAmount(choice), Math::min);
                    }
                }
                for (Map.Entry<IngredientKey, Integer> alternative : alternatives.entrySet()) {
                    required.merge(alternative.getKey(), alternative.getValue(), LegacyRecipeUsageBrowser::safeAdd);
                }
            }

            for (Map.Entry<IngredientKey, Integer> entry : required.entrySet()) {
                addUsage(
                        index,
                        entry.getKey(),
                        UsageEntry.machineRecipe(
                                machine,
                                resolved.provider().getKey().toString(),
                                recipe,
                                recipeIndex,
                                entry.getValue()));
                additions++;
            }
        }
        return additions;
    }

    private @Nullable ResolvedMachineRecipes resolveMachineRecipes(
            @Nonnull SlimefunItem item,
            @Nonnull World world,
            @Nonnull List<MachineRecipeProvider> providers,
            @Nonnull Set<String> warnedProviders) {
        for (MachineRecipeProvider provider : providers) {
            try {
                if (!provider.supports(item)) {
                    continue;
                }

                List<MachineRecipeDisplay> rawRecipes = provider.getRecipes(item, world);
                if (rawRecipes == null || rawRecipes.isEmpty()) {
                    continue;
                }

                List<MachineRecipeDisplay> recipes = new ArrayList<>(rawRecipes.size());
                for (MachineRecipeDisplay recipe : rawRecipes) {
                    if (recipe != null && !recipe.getOutputs().isEmpty()) {
                        recipes.add(recipe);
                    }
                }
                if (!recipes.isEmpty()) {
                    return new ResolvedMachineRecipes(provider, List.copyOf(recipes));
                }
            } catch (RuntimeException | LinkageError exception) {
                String key = provider.getKey().toString();
                if (warnedProviders.add(key)) {
                    plugin.getLogger()
                            .log(
                                    Level.WARNING,
                                    "Machine recipe provider " + key
                                            + " failed while building the Enhanced Guide recipe-usage index. Additional failures from this provider are suppressed for this build.",
                                    exception);
                }
            }
        }
        return null;
    }

    private void deliverCompletedIndex(@Nonnull IndexRequest request, @Nonnull UsageIndex index) {
        try {
            Slimefun.getSchedulerService().runFor(
                    request.player,
                    () -> {
                        Player player = request.player;
                        ButtonContext current = contexts.get(player.getUniqueId());
                        if (current != request.context
                                || current.expiresAt() < System.currentTimeMillis()
                                || player.getOpenInventory().getTopInventory() != current.guideInventory()) {
                            return;
                        }
                        openCachedUsages(player, current, request.targetKey, index);
                    },
                    () -> contexts.remove(request.player.getUniqueId(), request.context));
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.FINE, "Could not deliver completed recipe-usage index", exception);
        }
    }

    private void openCachedUsages(
            @Nonnull Player player,
            @Nonnull ButtonContext context,
            @Nonnull IngredientKey targetKey,
            @Nonnull UsageIndex index) {
        List<UsageEntry> usages = sortedUsages(index, targetKey);
        if (usages.isEmpty()) {
            if (player.getOpenInventory().getTopInventory() == context.guideInventory()) {
                context.guideInventory().setItem(context.buttonSlot(), createButton(0));
                player.updateInventory();
            }
            player.sendMessage(ChatColor.GRAY + "No known Slimefun or machine recipes use "
                    + readableName(context.target()) + ".");
            return;
        }
        openUsageList(player, context, usages, 1);
    }

    private @Nonnull List<UsageEntry> sortedUsages(
            @Nonnull UsageIndex index, @Nonnull IngredientKey targetKey) {
        List<UsageEntry> usages = index.usages().get(targetKey);
        if (usages == null || usages.isEmpty()) {
            return List.of();
        }

        List<UsageEntry> sorted = new ArrayList<>(usages);
        sorted.sort(Comparator.comparingInt((UsageEntry usage) -> usage.kind().ordinal())
                .thenComparing(usage -> readableName(usage.owner()), String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(UsageEntry::recipeIndex));
        return List.copyOf(sorted);
    }

    private void refreshBuildingButton(
            @Nonnull Player player, @Nonnull ButtonContext context, @Nonnull IndexBuildState state) {
        if (player.getOpenInventory().getTopInventory() == context.guideInventory()) {
            context.guideInventory().setItem(context.buttonSlot(), createBuildingButton(state));
            player.updateInventory();
        }
    }

    private void removeWaitingRequest(@Nonnull UUID playerId) {
        for (IndexBuildState state : builds.values()) {
            state.waiters.remove(playerId);
        }
    }

    private void openUsageList(
            @Nonnull Player player,
            @Nonnull ButtonContext context,
            @Nonnull List<UsageEntry> usages,
            int requestedPage) {
        int pages = Math.max(1, (usages.size() - 1) / LIST_SLOTS.length + 1);
        int page = Math.max(1, Math.min(requestedPage, pages));
        ChestMenu menu = createMenu(title("Used In: " + readableName(context.target())));
        fillBackground(menu);

        menu.replaceExistingItem(0, ChestMenuUtils.getBackButton(player, "", "&7Return to this item's recipe"));
        menu.addMenuClickHandler(0, (pl, slot, item, action) -> {
            context.guide().displayItem(context.profile(), context.target(), false);
            return false;
        });
        menu.replaceExistingItem(4, targetHeader(context.target(), usages.size()));
        menu.addMenuClickHandler(4, ChestMenuUtils.getEmptyClickHandler());

        int start = (page - 1) * LIST_SLOTS.length;
        for (int index = 0; index < LIST_SLOTS.length; index++) {
            int usageIndex = start + index;
            int slot = LIST_SLOTS[index];
            if (usageIndex >= usages.size()) {
                menu.replaceExistingItem(slot, null);
                menu.addMenuClickHandler(slot, ChestMenuUtils.getEmptyClickHandler());
                continue;
            }

            UsageEntry usage = usages.get(usageIndex);
            menu.replaceExistingItem(slot, createUsageIcon(usage));
            menu.addMenuClickHandler(slot, (pl, clickedSlot, clickedItem, action) -> {
                if (usage.kind() == UsageKind.SLIMEFUN_RECIPE) {
                    context.guide().displayItem(context.profile(), usage.owner(), true);
                } else if (action.isRightClicked() && usage.machineRecipe() != null) {
                    List<ItemStack> outputs = usage.machineRecipe().getOutputs();
                    if (!outputs.isEmpty()) {
                        context.guide().displayItem(context.profile(), outputs.get(0), 0, true);
                    }
                } else {
                    context.guide().displayItem(context.profile(), usage.owner(), true);
                }
                return false;
            });
        }

        menu.replaceExistingItem(46, ChestMenuUtils.getPreviousButton(player, page, pages));
        menu.addMenuClickHandler(46, (pl, slot, item, action) -> {
            if (page > 1) {
                openUsageList(pl, context, usages, page - 1);
            }
            return false;
        });
        menu.replaceExistingItem(
                49,
                createMenuItem(
                        Material.PAPER,
                        ChatColor.WHITE + "Page " + ChatColor.YELLOW + page + ChatColor.GRAY + " / "
                                + ChatColor.YELLOW + pages,
                        List.of(ChatColor.GRAY + "" + usages.size() + " known recipe usages")));
        menu.addMenuClickHandler(49, ChestMenuUtils.getEmptyClickHandler());
        menu.replaceExistingItem(52, ChestMenuUtils.getNextButton(player, page, pages));
        menu.addMenuClickHandler(52, (pl, slot, item, action) -> {
            if (page < pages) {
                openUsageList(pl, context, usages, page + 1);
            }
            return false;
        });

        menu.open(player);
    }

    private int findButtonSlot(@Nonnull Inventory inventory) {
        for (int slot : BUTTON_SLOTS) {
            if (slot < inventory.getSize() && isEmpty(inventory.getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private @Nonnull ItemStack createButton(@Nullable Integer cachedCount) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GRAY + "Find Slimefun crafting and machine recipes");
        lore.add(ChatColor.GRAY + "that consume this item as an ingredient.");
        if (cachedCount == null) {
            lore.add("");
            lore.add(ChatColor.DARK_GRAY + "Indexing starts only when you click this button.");
        } else {
            lore.add("");
            lore.add(ChatColor.GRAY + "Known usages: " + ChatColor.WHITE + cachedCount);
        }
        lore.add("");
        lore.add(ChatColor.YELLOW + "Click to browse usages");

        ItemStack button = createMenuItem(
                Material.HOPPER, ChatColor.GOLD + "" + ChatColor.BOLD + "Recipes Using This Item", lore);
        markButton(button);
        return button;
    }

    private @Nonnull ItemStack createBuildingButton(@Nonnull IndexBuildState state) {
        ItemStack button = createMenuItem(
                Material.CLOCK,
                ChatColor.YELLOW + "" + ChatColor.BOLD + "Building Recipe Usages",
                List.of(
                        "",
                        ChatColor.GRAY + "Progress: " + ChatColor.WHITE + state.progressPercent() + "%",
                        ChatColor.GRAY + "Processed: " + ChatColor.WHITE + state.nextItem + ChatColor.GRAY + "/"
                                + ChatColor.WHITE + state.itemLimit,
                        "",
                        ChatColor.DARK_GRAY + "Work is split across ticks to protect TPS.",
                        ChatColor.YELLOW + "Click for current progress"));
        markButton(button);
        return button;
    }

    private @Nonnull ItemStack createMenuItem(
            @Nonnull Material material, @Nonnull String displayName, @Nonnull List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(displayName);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void markButton(@Nonnull ItemStack button) {
        ItemMeta meta = button.getItemMeta();
        meta.getPersistentDataContainer().set(buttonKey, PersistentDataType.BYTE, (byte) 1);
        button.setItemMeta(meta);
    }

    private boolean isUsageButton(@Nullable ItemStack item) {
        if (isEmpty(item) || !item.hasItemMeta()) {
            return false;
        }
        Byte value = item.getItemMeta().getPersistentDataContainer().get(buttonKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    private @Nonnull ItemStack createUsageIcon(@Nonnull UsageEntry usage) {
        if (usage.kind() == UsageKind.SLIMEFUN_RECIPE) {
            return addLore(
                    usage.owner().getItem(),
                    "",
                    ChatColor.GOLD + "Slimefun recipe",
                    ChatColor.GRAY + "Result: " + ChatColor.WHITE + readableName(usage.owner()),
                    ChatColor.GRAY + "Uses this item: " + ChatColor.WHITE + usage.requiredAmount(),
                    "",
                    ChatColor.YELLOW + "Click to view the result recipe");
        }

        MachineRecipeDisplay recipe = usage.machineRecipe();
        ItemStack icon = recipe == null || recipe.getOutputs().isEmpty()
                ? usage.owner().getItem()
                : recipe.getOutputs().get(0);
        return addLore(
                icon,
                "",
                ChatColor.GOLD + "Machine recipe " + ChatColor.WHITE + (usage.recipeIndex() + 1),
                ChatColor.GRAY + "Machine: " + ChatColor.WHITE + readableName(usage.owner()),
                ChatColor.GRAY + "Uses this item: " + ChatColor.WHITE + usage.requiredAmount(),
                ChatColor.GRAY + "Provider: " + ChatColor.WHITE + usage.providerKey(),
                "",
                ChatColor.YELLOW + "Left-click: " + ChatColor.GRAY + "Open the machine",
                ChatColor.YELLOW + "Right-click: " + ChatColor.GRAY + "View this output's recipe");
    }

    private @Nonnull ItemStack targetHeader(@Nonnull SlimefunItem target, int usageCount) {
        return addLore(
                target.getItem(),
                "",
                ChatColor.GRAY + "Known recipe usages: " + ChatColor.WHITE + usageCount,
                ChatColor.DARK_GRAY + target.getId());
    }

    private static void addUsage(
            @Nonnull Map<IngredientKey, List<UsageEntry>> index,
            @Nonnull IngredientKey key,
            @Nonnull UsageEntry usage) {
        index.computeIfAbsent(key, ignored -> new ArrayList<>()).add(usage);
    }

    private static @Nullable IngredientKey ingredientKey(@Nullable ItemStack item) {
        if (isEmpty(item)) {
            return null;
        }

        SlimefunItem slimefunItem = SlimefunItem.getByItem(item);
        if (slimefunItem != null) {
            return new IngredientKey(slimefunItem.getId(), null);
        }

        ItemStack normalized = item.clone();
        normalized.setAmount(1);
        return new IngredientKey(null, normalized);
    }

    private static int normalizedAmount(@Nonnull ItemStack item) {
        return Math.max(1, item.getAmount());
    }

    private static int safeAdd(int left, int right) {
        long result = (long) left + right;
        return result > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) result;
    }

    private static @Nonnull String readableName(@Nonnull SlimefunItem item) {
        String name = ChatColor.stripColor(item.getItemName());
        return name == null || name.isBlank() ? item.getId() : name;
    }

    private @Nonnull ItemStack addLore(@Nonnull ItemStack source, @Nonnull String... lines) {
        ItemStack clone = source.clone();
        ItemMeta meta = clone.getItemMeta();
        List<String> lore =
                meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        Collections.addAll(lore, lines);
        meta.setLore(lore);
        clone.setItemMeta(meta);
        return clone;
    }

    private @Nonnull ChestMenu createMenu(@Nonnull String title) {
        ChestMenu menu = new ChestMenu(title);
        menu.setEmptySlotsClickable(false);
        menu.addMenuOpeningHandler(SoundEffect.GUIDE_BUTTON_CLICK_SOUND::playFor);
        return menu;
    }

    private void fillBackground(@Nonnull ChestMenu menu) {
        for (int slot = 0; slot < 54; slot++) {
            menu.addItem(slot, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
        }
    }

    private static @Nonnull String title(@Nonnull String value) {
        String stripped = ChatColor.stripColor(value);
        if (stripped == null || stripped.isBlank()) {
            return "Recipes Using This Item";
        }
        return stripped.length() <= 32 ? stripped : stripped.substring(0, 32);
    }

    private static boolean isEmpty(@Nullable ItemStack item) {
        return item == null || item.getType() == Material.AIR;
    }

    private enum UsageKind {
        SLIMEFUN_RECIPE,
        MACHINE_RECIPE
    }

    private record IngredientKey(@Nullable String slimefunId, @Nullable ItemStack normalizedItem) {}

    private record UsageIndex(Map<IngredientKey, List<UsageEntry>> usages) {}

    private record ResolvedMachineRecipes(MachineRecipeProvider provider, List<MachineRecipeDisplay> recipes) {}

    private record UsageEntry(
            UsageKind kind,
            SlimefunItem owner,
            @Nullable String providerKey,
            @Nullable MachineRecipeDisplay machineRecipe,
            int recipeIndex,
            int requiredAmount) {

        private static @Nonnull UsageEntry slimefunRecipe(@Nonnull SlimefunItem result, int requiredAmount) {
            return new UsageEntry(UsageKind.SLIMEFUN_RECIPE, result, null, null, -1, requiredAmount);
        }

        private static @Nonnull UsageEntry machineRecipe(
                @Nonnull SlimefunItem machine,
                @Nonnull String providerKey,
                @Nonnull MachineRecipeDisplay recipe,
                int recipeIndex,
                int requiredAmount) {
            return new UsageEntry(
                    UsageKind.MACHINE_RECIPE, machine, providerKey, recipe, recipeIndex, requiredAmount);
        }
    }

    private record ButtonContext(
            Inventory guideInventory,
            PlayerProfile profile,
            EnhancedSurvivalSlimefunGuide guide,
            SlimefunItem target,
            int buttonSlot,
            long expiresAt) {}

    private record IndexRequest(Player player, ButtonContext context, IngredientKey targetKey) {}

    private static final class IndexBuildState {
        private final World world;
        private final List<SlimefunItem> items;
        private final int itemLimit;
        private final List<MachineRecipeProvider> providers;
        private final int maxItemsPerTick;
        private final long batchBudgetNanos;
        private final Map<IngredientKey, List<UsageEntry>> usages = new HashMap<>();
        private final Set<String> warnedProviders = new HashSet<>();
        private final Map<UUID, IndexRequest> waiters = new ConcurrentHashMap<>();
        private volatile int nextItem;
        private int batches;
        private int usageCount;

        private IndexBuildState(
                World world,
                List<SlimefunItem> items,
                List<MachineRecipeProvider> providers,
                int maxItemsPerTick,
                long batchBudgetNanos) {
            this.world = world;
            this.items = items;
            this.itemLimit = items.size();
            this.providers = providers;
            this.maxItemsPerTick = maxItemsPerTick;
            this.batchBudgetNanos = batchBudgetNanos;
        }

        private int progressPercent() {
            if (itemLimit == 0) {
                return 100;
            }
            return Math.min(99, (int) ((long) nextItem * 100L / itemLimit));
        }
    }
}
