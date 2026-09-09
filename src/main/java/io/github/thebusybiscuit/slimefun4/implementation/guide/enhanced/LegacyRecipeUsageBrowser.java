package io.github.thebusybiscuit.slimefun4.implementation.guide.enhanced;

import io.github.bakedlibs.dough.items.CustomItemStack;
import io.github.bakedlibs.dough.items.ItemUtils;
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
 * <p>The item-detail page stays cheap: decorating it only installs a protected button. The reverse index is built on
 * the first explicit usage-browser click for a world and is then reused while the Slimefun item/provider registry
 * signature is unchanged. Both ordinary Slimefun recipes and the normalized {@link MachineRecipeProviderRegistry}
 * feed the same index, so addon machine adapters automatically participate without another compatibility layer.
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

        UsageIndex cached = indexes.get(player.getWorld().getUID());
        Integer cachedCount = cached == null ? null : cached.usages().getOrDefault(targetKey, List.of()).size();
        long expiresAt = System.currentTimeMillis() + LegacyGuideSettings.get().getRecipeFillSessionSeconds() * 1000L;
        contexts.put(
                player.getUniqueId(),
                new ButtonContext(inventory, profile, guide, target, buttonSlot, expiresAt));
        inventory.setItem(buttonSlot, createButton(cachedCount));
        player.updateInventory();
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

        UsageIndex index = getOrBuildIndex(player.getWorld());
        IngredientKey targetKey = ingredientKey(context.target().getItem());
        List<UsageEntry> usages = targetKey == null ? List.of() : index.usages().getOrDefault(targetKey, List.of());
        if (usages.isEmpty()) {
            player.sendMessage(ChatColor.GRAY + "No known Slimefun or machine recipes use "
                    + ItemUtils.getItemName(context.target().getItem()) + ".");
            return;
        }

        openUsageList(player, context, usages, 1);
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
        ButtonContext context = contexts.get(event.getPlayer().getUniqueId());
        if (context != null && context.guideInventory() == event.getInventory()) {
            contexts.remove(event.getPlayer().getUniqueId(), context);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(@Nonnull PlayerQuitEvent event) {
        contexts.remove(event.getPlayer().getUniqueId());
    }

    private @Nonnull UsageIndex getOrBuildIndex(@Nonnull World world) {
        List<SlimefunItem> items = new ArrayList<>(Slimefun.getRegistry().getEnabledSlimefunItems());
        List<MachineRecipeProvider> providers = MachineRecipeProviderRegistry.getProviders();
        IndexSignature signature = signature(items, providers);

        UsageIndex cached = indexes.get(world.getUID());
        if (cached != null && cached.signature().equals(signature)) {
            return cached;
        }

        return indexes.compute(world.getUID(), (ignored, current) -> {
            if (current != null && current.signature().equals(signature)) {
                return current;
            }
            return buildIndex(world, items, providers, signature);
        });
    }

    private @Nonnull UsageIndex buildIndex(
            @Nonnull World world,
            @Nonnull List<SlimefunItem> items,
            @Nonnull List<MachineRecipeProvider> providers,
            @Nonnull IndexSignature signature) {
        long started = System.nanoTime();
        Map<IngredientKey, List<UsageEntry>> mutable = new HashMap<>();
        Set<String> warnedProviders = new HashSet<>();

        for (SlimefunItem item : items) {
            try {
                if (item.isDisabledIn(world)) {
                    continue;
                }

                indexSlimefunRecipe(mutable, item);
                ResolvedMachineRecipes resolved = resolveMachineRecipes(item, world, providers, warnedProviders);
                if (resolved != null) {
                    indexMachineRecipes(mutable, item, resolved);
                }
            } catch (RuntimeException | LinkageError exception) {
                plugin.getLogger()
                        .log(
                                Level.WARNING,
                                "Could not index recipe usages for Slimefun item " + item.getId(),
                                exception);
            }
        }

        int usageCount = 0;
        Map<IngredientKey, List<UsageEntry>> frozen = new HashMap<>(mutable.size());
        for (Map.Entry<IngredientKey, List<UsageEntry>> entry : mutable.entrySet()) {
            List<UsageEntry> usages = new ArrayList<>(entry.getValue());
            usages.sort(Comparator.comparingInt((UsageEntry usage) -> usage.kind().ordinal())
                    .thenComparing(usage -> readableName(usage.owner()), String.CASE_INSENSITIVE_ORDER)
                    .thenComparingInt(UsageEntry::recipeIndex));
            usageCount += usages.size();
            frozen.put(entry.getKey(), List.copyOf(usages));
        }

        long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
        plugin.getLogger()
                .info("Built Enhanced Guide recipe-usage index for world '" + world.getName() + "': "
                        + usageCount + " usages across " + frozen.size() + " ingredient keys in " + elapsedMillis
                        + "ms.");
        return new UsageIndex(Map.copyOf(frozen), signature);
    }

    private void indexSlimefunRecipe(
            @Nonnull Map<IngredientKey, List<UsageEntry>> index, @Nonnull SlimefunItem resultItem) {
        ItemStack[] recipe = resultItem.getRecipe();
        if (recipe == null || recipe.length == 0) {
            return;
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
    }

    private void indexMachineRecipes(
            @Nonnull Map<IngredientKey, List<UsageEntry>> index,
            @Nonnull SlimefunItem machine,
            @Nonnull ResolvedMachineRecipes resolved) {
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
            }
        }
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

    private void openUsageList(
            @Nonnull Player player,
            @Nonnull ButtonContext context,
            @Nonnull List<UsageEntry> usages,
            int requestedPage) {
        int pages = Math.max(1, (usages.size() - 1) / LIST_SLOTS.length + 1);
        int page = Math.max(1, Math.min(requestedPage, pages));
        ChestMenu menu = createMenu(title("Used In: " + ItemUtils.getItemName(context.target().getItem())));
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
            menu.replaceExistingItem(slot, createUsageIcon(context.target(), usage));
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
                new CustomItemStack(
                        Material.PAPER,
                        "&fPage &e" + page + " &7/ &e" + pages,
                        "&7" + usages.size() + " known recipe usages"));
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
        lore.add("&7Find Slimefun crafting and machine recipes");
        lore.add("&7that consume this item as an ingredient.");
        if (cachedCount == null) {
            lore.add("");
            lore.add("&8The reverse index is built only when opened.");
        } else {
            lore.add("");
            lore.add("&7Known usages: &f" + cachedCount);
        }
        lore.add("");
        lore.add("&eClick to browse usages");

        ItemStack button = new CustomItemStack(
                Material.HOPPER, "&6&lRecipes Using This Item", lore.toArray(new String[0]));
        ItemMeta meta = button.getItemMeta();
        meta.getPersistentDataContainer().set(buttonKey, PersistentDataType.BYTE, (byte) 1);
        button.setItemMeta(meta);
        return button;
    }

    private boolean isUsageButton(@Nullable ItemStack item) {
        if (isEmpty(item) || !item.hasItemMeta()) {
            return false;
        }
        Byte value = item.getItemMeta().getPersistentDataContainer().get(buttonKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    private @Nonnull ItemStack createUsageIcon(@Nonnull SlimefunItem target, @Nonnull UsageEntry usage) {
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

    private static @Nonnull IndexSignature signature(
            @Nonnull List<SlimefunItem> items, @Nonnull List<MachineRecipeProvider> providers) {
        int itemHash = 1;
        for (SlimefunItem item : items) {
            itemHash = 31 * itemHash + item.getId().hashCode();
        }

        int providerHash = 1;
        for (MachineRecipeProvider provider : providers) {
            providerHash = 31 * providerHash + provider.getKey().hashCode();
            providerHash = 31 * providerHash + provider.getClass().getName().hashCode();
            providerHash = 31 * providerHash + provider.getPriority();
        }
        return new IndexSignature(items.size(), itemHash, providers.size(), providerHash);
    }

    private static @Nonnull String readableName(@Nonnull SlimefunItem item) {
        String name = ChatColor.stripColor(ItemUtils.getItemName(item.getItem()));
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

    private record IndexSignature(int itemCount, int itemHash, int providerCount, int providerHash) {}

    private record UsageIndex(Map<IngredientKey, List<UsageEntry>> usages, IndexSignature signature) {}

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
}
