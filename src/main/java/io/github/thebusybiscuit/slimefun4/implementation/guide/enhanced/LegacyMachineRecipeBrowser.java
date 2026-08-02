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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
 * Adds a native JEG-style machine recipe browser to the enhanced guide.
 *
 * <p>Phase 4 routes all recipe discovery through the addon-facing {@link MachineRecipeProviderRegistry}. Core
 * containers, existing {@code RecipeDisplayItem} implementations, FastMachines and addon-provided adapters therefore
 * share the same browser and safety behavior.
 */
public final class LegacyMachineRecipeBrowser implements Listener {

    private static final int[] BUTTON_SLOTS = {26, 17, 24};
    private static final int[] LIST_SLOTS = {
        9, 10, 11, 12, 13, 14, 15, 16, 17,
        18, 19, 20, 21, 22, 23, 24, 25, 26,
        27, 28, 29, 30, 31, 32, 33, 34, 35,
        36, 37, 38, 39, 40, 41, 42, 43, 44
    };
    private static final int[] DETAIL_INPUT_SLOTS = {
        9, 10, 11, 12, 13, 14, 15, 16, 17,
        18, 19, 20, 21, 22, 23, 24, 25, 26,
        27, 28, 29, 30, 31, 32, 33, 34, 35,
        36, 37, 38, 39, 40, 41, 42, 43, 44
    };
    private static final int[] DETAIL_OUTPUT_SLOTS = {47, 48, 49, 50, 51};

    private static LegacyMachineRecipeBrowser instance;

    private final Slimefun plugin;
    private final NamespacedKey buttonKey;
    private final Map<UUID, BrowserContext> contexts = new ConcurrentHashMap<>();

    private LegacyMachineRecipeBrowser(@Nonnull Slimefun plugin) {
        this.plugin = plugin;
        buttonKey = new NamespacedKey(plugin, "enhanced_guide_machine_recipes");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public static synchronized void initialize(@Nonnull Slimefun plugin) {
        LegacyMachineRecipeProviders.registerDefaults(plugin);
        instance = new LegacyMachineRecipeBrowser(plugin);
    }

    public static @Nonnull LegacyMachineRecipeBrowser get() {
        if (instance == null) {
            throw new IllegalStateException("Enhanced guide machine recipes were accessed before initialization");
        }
        return instance;
    }

    public void decorateMachinePage(
            @Nonnull Player player,
            @Nonnull PlayerProfile profile,
            @Nonnull EnhancedSurvivalSlimefunGuide guide,
            @Nonnull SlimefunItem item) {
        contexts.remove(player.getUniqueId());

        if (!LegacyGuideSettings.get().hasMachineRecipeBrowser()) {
            return;
        }

        ResolvedRecipes resolved = resolveRecipes(item, player);
        if (resolved == null || resolved.recipes().isEmpty()) {
            return;
        }

        Inventory inventory = player.getOpenInventory().getTopInventory();
        int buttonSlot = findButtonSlot(inventory);
        if (buttonSlot < 0) {
            return;
        }

        BrowserContext context = new BrowserContext(
                inventory,
                profile,
                guide,
                item,
                resolved.provider(),
                resolved.recipes(),
                buttonSlot,
                System.currentTimeMillis() + LegacyGuideSettings.get().getRecipeFillSessionSeconds() * 1000L);
        contexts.put(player.getUniqueId(), context);
        inventory.setItem(buttonSlot, createButton(resolved.provider(), resolved.recipes().size()));
        player.updateInventory();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(@Nonnull InventoryClickEvent event) {
        HumanEntity human = event.getWhoClicked();
        if (!(human instanceof Player player)) {
            return;
        }

        BrowserContext context = contexts.get(player.getUniqueId());
        Inventory topInventory = event.getView().getTopInventory();
        if (context == null || context.guideInventory() != topInventory) {
            return;
        }

        boolean buttonClick = event.getClickedInventory() == topInventory
                && event.getRawSlot() == context.buttonSlot()
                && isBrowserButton(event.getCurrentItem());

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
            player.sendMessage(ChatColor.RED + "This machine-recipe session expired. Reopen the machine in the guide.");
            return;
        }

        openRecipeList(player, context, 1);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(@Nonnull InventoryDragEvent event) {
        BrowserContext context = contexts.get(event.getWhoClicked().getUniqueId());
        if (context != null
                && context.guideInventory() == event.getView().getTopInventory()
                && event.getRawSlots().contains(context.buttonSlot())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInventoryClose(@Nonnull InventoryCloseEvent event) {
        BrowserContext context = contexts.get(event.getPlayer().getUniqueId());
        if (context != null && context.guideInventory() == event.getInventory()) {
            contexts.remove(event.getPlayer().getUniqueId(), context);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(@Nonnull PlayerQuitEvent event) {
        contexts.remove(event.getPlayer().getUniqueId());
    }

    private @Nullable ResolvedRecipes resolveRecipes(@Nonnull SlimefunItem item, @Nonnull Player player) {
        for (MachineRecipeProvider provider : MachineRecipeProviderRegistry.getProviders()) {
            try {
                if (!provider.supports(item)) {
                    continue;
                }

                List<MachineRecipeDisplay> rawRecipes = provider.getRecipes(item, player.getWorld());
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
                    return new ResolvedRecipes(provider, List.copyOf(recipes));
                }
            } catch (RuntimeException | LinkageError exception) {
                plugin.getLogger()
                        .log(
                                Level.WARNING,
                                "Machine recipe provider " + provider.getKey() + " failed for " + item.getId(),
                                exception);
            }
        }
        return null;
    }

    private void openRecipeList(@Nonnull Player player, @Nonnull BrowserContext context, int requestedPage) {
        List<MachineRecipeDisplay> recipes = context.recipes();
        if (recipes.isEmpty()) {
            player.sendMessage(ChatColor.RED + "No enabled recipes are available for this machine in this world.");
            context.guide().displayItem(context.profile(), context.machine(), false);
            return;
        }

        int pages = Math.max(1, (recipes.size() - 1) / LIST_SLOTS.length + 1);
        int page = Math.max(1, Math.min(requestedPage, pages));
        ChestMenu menu = createMenu(title("Recipes: " + ItemUtils.getItemName(context.machine().getItem())));
        fillBackground(menu);

        menu.replaceExistingItem(0, ChestMenuUtils.getBackButton(player, "", "&7Return to the machine"));
        menu.addMenuClickHandler(0, (pl, slot, item, action) -> {
            context.guide().displayItem(context.profile(), context.machine(), false);
            return false;
        });
        menu.replaceExistingItem(4, machineHeader(context.machine(), context.provider(), recipes.size()));
        menu.addMenuClickHandler(4, ChestMenuUtils.getEmptyClickHandler());

        int start = (page - 1) * LIST_SLOTS.length;
        for (int index = 0; index < LIST_SLOTS.length; index++) {
            int recipeIndex = start + index;
            int slot = LIST_SLOTS[index];
            if (recipeIndex >= recipes.size()) {
                menu.replaceExistingItem(slot, null);
                menu.addMenuClickHandler(slot, ChestMenuUtils.getEmptyClickHandler());
                continue;
            }

            MachineRecipeDisplay recipe = recipes.get(recipeIndex);
            ItemStack icon = createRecipeIcon(recipe, recipeIndex, recipes.size());
            menu.replaceExistingItem(slot, icon);
            menu.addMenuClickHandler(slot, (pl, clickedSlot, clickedItem, action) -> {
                List<ItemStack> outputs = recipe.getOutputs();
                if (action.isRightClicked() && !outputs.isEmpty()) {
                    context.guide().displayItem(context.profile(), outputs.get(0), 0, true);
                } else {
                    openRecipeDetail(pl, context, recipeIndex);
                }
                return false;
            });
        }

        menu.replaceExistingItem(46, ChestMenuUtils.getPreviousButton(player, page, pages));
        menu.addMenuClickHandler(46, (pl, slot, item, action) -> {
            if (page > 1) {
                openRecipeList(pl, context, page - 1);
            }
            return false;
        });
        menu.replaceExistingItem(49, new CustomItemStack(
                Material.PAPER,
                "&fPage &e" + page + " &7/ &e" + pages,
                "&7" + recipes.size() + " machine recipes"));
        menu.addMenuClickHandler(49, ChestMenuUtils.getEmptyClickHandler());
        menu.replaceExistingItem(52, ChestMenuUtils.getNextButton(player, page, pages));
        menu.addMenuClickHandler(52, (pl, slot, item, action) -> {
            if (page < pages) {
                openRecipeList(pl, context, page + 1);
            }
            return false;
        });

        menu.open(player);
    }

    private void openRecipeDetail(@Nonnull Player player, @Nonnull BrowserContext context, int requestedIndex) {
        List<MachineRecipeDisplay> recipes = context.recipes();
        int recipeIndex = Math.max(0, Math.min(requestedIndex, recipes.size() - 1));
        MachineRecipeDisplay recipe = recipes.get(recipeIndex);

        ChestMenu menu = createMenu(title("Machine Recipe " + (recipeIndex + 1) + "/" + recipes.size()));
        fillBackground(menu);

        menu.replaceExistingItem(0, ChestMenuUtils.getBackButton(player, "", "&7Return to the recipe list"));
        menu.addMenuClickHandler(0, (pl, slot, item, action) -> {
            openRecipeList(pl, context, recipeIndex / LIST_SLOTS.length + 1);
            return false;
        });
        menu.replaceExistingItem(4, machineHeader(context.machine(), context.provider(), recipes.size()));
        menu.addMenuClickHandler(4, ChestMenuUtils.getEmptyClickHandler());
        menu.replaceExistingItem(8, recipeInformation(recipe));
        menu.addMenuClickHandler(8, ChestMenuUtils.getEmptyClickHandler());

        List<MachineRecipeIngredient> inputs = recipe.getInputs();
        int displayedInputs = Math.min(inputs.size(), DETAIL_INPUT_SLOTS.length);
        int[] selectedAlternatives = new int[displayedInputs];
        for (int index = 0; index < displayedInputs; index++) {
            int slot = DETAIL_INPUT_SLOTS[index];
            MachineRecipeIngredient ingredient = inputs.get(index);
            menu.replaceExistingItem(slot, createIngredientIcon(ingredient, index, 0));
            if (ingredient.getChoices().size() > 1) {
                final int ingredientIndex = index;
                menu.addMenuClickHandler(slot, (pl, clickedSlot, clickedItem, action) -> {
                    int alternatives = ingredient.getChoices().size();
                    if (action.isRightClicked()) {
                        selectedAlternatives[ingredientIndex] =
                                Math.floorMod(selectedAlternatives[ingredientIndex] - 1, alternatives);
                    } else {
                        selectedAlternatives[ingredientIndex] =
                                (selectedAlternatives[ingredientIndex] + 1) % alternatives;
                    }
                    menu.replaceExistingItem(
                            clickedSlot,
                            createIngredientIcon(
                                    ingredient, ingredientIndex, selectedAlternatives[ingredientIndex]));
                    SoundEffect.GUIDE_BUTTON_CLICK_SOUND.playFor(pl);
                    return false;
                });
            } else {
                menu.addMenuClickHandler(slot, ChestMenuUtils.getEmptyClickHandler());
            }
        }

        List<ItemStack> outputs = recipe.getOutputs();
        int displayedOutputs = Math.min(outputs.size(), DETAIL_OUTPUT_SLOTS.length);
        for (int index = 0; index < displayedOutputs; index++) {
            ItemStack recipeOutput = outputs.get(index);
            ItemStack output = addLore(
                    recipeOutput,
                    "",
                    ChatColor.GREEN + "Machine output",
                    ChatColor.GRAY + "Right-click to view this item's own recipe");
            int slot = DETAIL_OUTPUT_SLOTS[index];
            menu.replaceExistingItem(slot, output);
            menu.addMenuClickHandler(slot, (pl, clickedSlot, clickedItem, action) -> {
                if (action.isRightClicked()) {
                    context.guide().displayItem(context.profile(), recipeOutput, 0, true);
                }
                return false;
            });
        }

        LegacyMachineInputFillManager inputFill = LegacyMachineInputFillManager.get();
        if (inputFill.supports(context.machine(), recipe)) {
            menu.replaceExistingItem(46, inputFill.createButton(context.machine(), recipe));
            menu.addMenuClickHandler(46, (pl, slot, item, action) -> {
                if (!action.isRightClicked()) {
                    inputFill.fill(
                            pl, context.machine(), recipe, selectedAlternatives.clone(), action.isShiftClicked());
                }
                return false;
            });
        }

        menu.replaceExistingItem(45, ChestMenuUtils.getPreviousButton(player, recipeIndex + 1, recipes.size()));
        menu.addMenuClickHandler(45, (pl, slot, item, action) -> {
            if (recipeIndex > 0) {
                openRecipeDetail(pl, context, recipeIndex - 1);
            }
            return false;
        });
        menu.replaceExistingItem(53, ChestMenuUtils.getNextButton(player, recipeIndex + 1, recipes.size()));
        menu.addMenuClickHandler(53, (pl, slot, item, action) -> {
            if (recipeIndex + 1 < recipes.size()) {
                openRecipeDetail(pl, context, recipeIndex + 1);
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

    private @Nonnull ItemStack createButton(@Nonnull MachineRecipeProvider provider, int recipeCount) {
        ItemStack button = new CustomItemStack(
                Material.KNOWLEDGE_BOOK,
                "&6&lMachine Recipes",
                "",
                "&7View everything this machine can process.",
                "&7Available recipes: &f" + recipeCount,
                "&7Provider: &f" + provider.getKey(),
                "",
                "&eClick to browse recipes");
        ItemMeta meta = button.getItemMeta();
        meta.getPersistentDataContainer().set(buttonKey, PersistentDataType.BYTE, (byte) 1);
        button.setItemMeta(meta);
        return button;
    }

    private boolean isBrowserButton(@Nullable ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        Byte value = item.getItemMeta().getPersistentDataContainer().get(buttonKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    private @Nonnull ItemStack createRecipeIcon(
            @Nonnull MachineRecipeDisplay recipe, int recipeIndex, int totalRecipes) {
        List<ItemStack> outputs = recipe.getOutputs();
        if (outputs.isEmpty()) {
            return new CustomItemStack(
                    Material.BARRIER,
                    "&cRecipe " + (recipeIndex + 1),
                    "&7This recipe could not be displayed.");
        }

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.GOLD + "Machine recipe " + ChatColor.WHITE + (recipeIndex + 1) + ChatColor.GRAY + "/"
                + totalRecipes);
        lore.add(ChatColor.GRAY + "Inputs: " + ChatColor.WHITE + recipe.getInputs().size());
        lore.add(ChatColor.GRAY + "Outputs: " + ChatColor.WHITE + outputs.size());
        if (!recipe.getLabel().isBlank()) {
            lore.add(ChatColor.DARK_GRAY + recipe.getLabel());
        }
        lore.add(ChatColor.YELLOW + "Left-click: " + ChatColor.GRAY + "View full recipe");
        lore.add(ChatColor.YELLOW + "Right-click: " + ChatColor.GRAY + "View output's own recipe");
        return addLore(outputs.get(0), lore.toArray(new String[0]));
    }

    private @Nonnull ItemStack createIngredientIcon(
            @Nonnull MachineRecipeIngredient ingredient, int ingredientIndex, int selectedAlternative) {
        List<ItemStack> choices = ingredient.getChoices();
        int safeIndex = Math.floorMod(selectedAlternative, choices.size());
        ItemStack choice = choices.get(safeIndex);
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.AQUA + "Ingredient " + ChatColor.WHITE + (ingredientIndex + 1));
        lore.add(ChatColor.GRAY + "Required: " + ChatColor.WHITE + choice.getAmount());
        if (choices.size() > 1) {
            lore.add(ChatColor.GRAY + "Alternative " + ChatColor.WHITE + (safeIndex + 1) + ChatColor.GRAY + "/"
                    + choices.size());
            lore.add(ChatColor.YELLOW + "Left/right-click to cycle choices");
        }
        return addLore(choice, lore.toArray(new String[0]));
    }

    private @Nonnull ItemStack recipeInformation(@Nonnull MachineRecipeDisplay recipe) {
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Inputs: " + ChatColor.WHITE + recipe.getInputs().size());
        lore.add(ChatColor.GRAY + "Outputs: " + ChatColor.WHITE + recipe.getOutputs().size());
        lore.add(ChatColor.GRAY + "Layout: " + ChatColor.WHITE + readableLayout(recipe));
        if (recipe.hasKnownProcessingTime()) {
            lore.add(ChatColor.GRAY + "Processing ticks: " + ChatColor.WHITE + recipe.getProcessingTicks());
        }
        if (recipe.hasKnownEnergyUse()) {
            lore.add(ChatColor.GRAY + "Energy use: " + ChatColor.WHITE + recipe.getEnergyPerTick() + " J/t");
        }
        if (!recipe.getLabel().isBlank()) {
            lore.add(ChatColor.DARK_GRAY + recipe.getLabel());
        }
        if (recipe.getInputs().size() > DETAIL_INPUT_SLOTS.length) {
            lore.add(ChatColor.RED + "Only the first " + DETAIL_INPUT_SLOTS.length + " inputs are shown.");
        }
        return new CustomItemStack(Material.BOOK, "&6Recipe Information", lore.toArray(new String[0]));
    }

    private @Nonnull String readableLayout(@Nonnull MachineRecipeDisplay recipe) {
        return switch (recipe.getLayout()) {
            case SHAPED -> "Shaped";
            case SHAPELESS -> "Shapeless";
            case UNSPECIFIED -> "Provider-defined";
        };
    }

    private @Nonnull ItemStack machineHeader(
            @Nonnull SlimefunItem machine, @Nonnull MachineRecipeProvider provider, int recipeCount) {
        return addLore(
                machine.getItem(),
                "",
                ChatColor.GRAY + "Processes " + ChatColor.WHITE + recipeCount + ChatColor.GRAY + " recipes",
                ChatColor.GRAY + "Provider: " + ChatColor.WHITE + provider.getKey(),
                ChatColor.DARK_GRAY + machine.getId());
    }

    private @Nonnull ItemStack addLore(@Nonnull ItemStack source, @Nonnull String... lines) {
        ItemStack clone = source.clone();
        ItemMeta meta = clone.getItemMeta();
        List<String> lore = meta.hasLore() && meta.getLore() != null
                ? new ArrayList<>(meta.getLore())
                : new ArrayList<>();
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

    private @Nonnull String title(@Nonnull String value) {
        String stripped = ChatColor.stripColor(value);
        if (stripped == null) {
            return "Machine Recipes";
        }
        return stripped.length() <= 32 ? stripped : stripped.substring(0, 32);
    }

    private boolean isEmpty(@Nullable ItemStack item) {
        return item == null || item.getType() == Material.AIR;
    }

    private record ResolvedRecipes(MachineRecipeProvider provider, List<MachineRecipeDisplay> recipes) {}

    private record BrowserContext(
            Inventory guideInventory,
            PlayerProfile profile,
            EnhancedSurvivalSlimefunGuide guide,
            SlimefunItem machine,
            MachineRecipeProvider provider,
            List<MachineRecipeDisplay> recipes,
            int buttonSlot,
            long expiresAt) {}
}
