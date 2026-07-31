package io.github.thebusybiscuit.slimefun4.implementation.guide.enhanced;

import io.github.bakedlibs.dough.items.CustomItemStack;
import io.github.bakedlibs.dough.items.ItemUtils;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * Adds a native JEG-style machine recipe browser to the enhanced guide.
 *
 * <p>FastMachines exposes its loaded recipes through public Kotlin getters, but it does not implement Slimefun's
 * simple {@code RecipeDisplayItem} contract because its recipes can contain up to 36 shapeless ingredients and
 * multiple alternatives. This compatibility layer reads only those public getters and never accesses private fields.
 * The adapter is intentionally isolated here so FastMachines remains an optional addon.
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
    private static final String FAST_MACHINES_PACKAGE = "net.guizhanss.fastmachines.";

    private static LegacyMachineRecipeBrowser instance;

    private final Slimefun plugin;
    private final NamespacedKey buttonKey;
    private final Map<UUID, BrowserContext> contexts = new ConcurrentHashMap<>();
    private final Map<Class<?>, Optional<FastMachinesAdapter>> adapters = new ConcurrentHashMap<>();
    private final Map<RecipeCacheKey, List<Object>> recipeCache = new ConcurrentHashMap<>();
    private final Map<Class<?>, Optional<RecipeMethods>> recipeMethods = new ConcurrentHashMap<>();
    private final Map<Class<?>, Optional<ChoiceMethods>> choiceMethods = new ConcurrentHashMap<>();
    private final Map<Class<?>, Optional<WrapperMethods>> wrapperMethods = new ConcurrentHashMap<>();

    private LegacyMachineRecipeBrowser(@Nonnull Slimefun plugin) {
        this.plugin = plugin;
        buttonKey = new NamespacedKey(plugin, "enhanced_guide_machine_recipes");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public static synchronized void initialize(@Nonnull Slimefun plugin) {
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

        Optional<FastMachinesAdapter> optionalAdapter = adapterFor(item.getClass());
        if (optionalAdapter.isEmpty()) {
            return;
        }

        List<?> rawRecipes;
        try {
            rawRecipes = optionalAdapter.get().getRecipes(item);
        } catch (ReflectiveOperationException | LinkageError exception) {
            logAdapterFailure(item, exception);
            return;
        }

        if (rawRecipes.isEmpty()) {
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
                optionalAdapter.get(),
                buttonSlot,
                System.currentTimeMillis() + LegacyGuideSettings.get().getRecipeFillSessionSeconds() * 1000L);
        contexts.put(player.getUniqueId(), context);
        inventory.setItem(buttonSlot, createButton(rawRecipes.size()));
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

    private void openRecipeList(@Nonnull Player player, @Nonnull BrowserContext context, int requestedPage) {
        List<Object> recipes = getEnabledRecipes(context, player.getWorld());
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
        menu.replaceExistingItem(4, machineHeader(context.machine(), recipes.size()));
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

            Object rawRecipe = recipes.get(recipeIndex);
            MachineRecipeData recipe = readRecipe(rawRecipe);
            ItemStack icon = createRecipeIcon(recipe, recipeIndex, recipes.size());
            menu.replaceExistingItem(slot, icon);
            menu.addMenuClickHandler(slot, (pl, clickedSlot, clickedItem, action) -> {
                if (action.isRightClicked() && recipe != null && !recipe.outputs().isEmpty()) {
                    context.guide().displayItem(context.profile(), recipe.outputs().get(0), 0, true);
                } else {
                    openRecipeDetail(pl, context, recipes, recipeIndex);
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

    private void openRecipeDetail(
            @Nonnull Player player,
            @Nonnull BrowserContext context,
            @Nonnull List<Object> recipes,
            int requestedIndex) {
        int recipeIndex = Math.max(0, Math.min(requestedIndex, recipes.size() - 1));
        MachineRecipeData recipe = readRecipe(recipes.get(recipeIndex));
        if (recipe == null) {
            player.sendMessage(ChatColor.RED + "This recipe could not be displayed.");
            openRecipeList(player, context, recipeIndex / LIST_SLOTS.length + 1);
            return;
        }

        ChestMenu menu = createMenu(title("Machine Recipe " + (recipeIndex + 1) + "/" + recipes.size()));
        fillBackground(menu);

        menu.replaceExistingItem(0, ChestMenuUtils.getBackButton(player, "", "&7Return to the recipe list"));
        menu.addMenuClickHandler(0, (pl, slot, item, action) -> {
            openRecipeList(pl, context, recipeIndex / LIST_SLOTS.length + 1);
            return false;
        });
        menu.replaceExistingItem(4, machineHeader(context.machine(), recipes.size()));
        menu.addMenuClickHandler(4, ChestMenuUtils.getEmptyClickHandler());
        menu.replaceExistingItem(8, new CustomItemStack(
                Material.BOOK,
                "&6Recipe Information",
                "&7Inputs: &f" + recipe.inputs().size(),
                "&7Outputs: &f" + recipe.outputs().size(),
                recipe.inputs().size() > DETAIL_INPUT_SLOTS.length
                        ? "&cOnly the first " + DETAIL_INPUT_SLOTS.length + " inputs are shown."
                        : "&7Ingredients may be placed in any FastMachines input slot."));
        menu.addMenuClickHandler(8, ChestMenuUtils.getEmptyClickHandler());

        int displayedInputs = Math.min(recipe.inputs().size(), DETAIL_INPUT_SLOTS.length);
        int[] selectedAlternatives = new int[displayedInputs];
        for (int index = 0; index < displayedInputs; index++) {
            int slot = DETAIL_INPUT_SLOTS[index];
            IngredientData ingredient = recipe.inputs().get(index);
            menu.replaceExistingItem(slot, createIngredientIcon(ingredient, index, 0));
            if (ingredient.choices().size() > 1) {
                final int ingredientIndex = index;
                menu.addMenuClickHandler(slot, (pl, clickedSlot, clickedItem, action) -> {
                    int alternatives = ingredient.choices().size();
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

        int displayedOutputs = Math.min(recipe.outputs().size(), DETAIL_OUTPUT_SLOTS.length);
        for (int index = 0; index < displayedOutputs; index++) {
            ItemStack recipeOutput = recipe.outputs().get(index);
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

        menu.replaceExistingItem(45, ChestMenuUtils.getPreviousButton(player, recipeIndex + 1, recipes.size()));
        menu.addMenuClickHandler(45, (pl, slot, item, action) -> {
            if (recipeIndex > 0) {
                openRecipeDetail(pl, context, recipes, recipeIndex - 1);
            }
            return false;
        });
        menu.replaceExistingItem(53, ChestMenuUtils.getNextButton(player, recipeIndex + 1, recipes.size()));
        menu.addMenuClickHandler(53, (pl, slot, item, action) -> {
            if (recipeIndex + 1 < recipes.size()) {
                openRecipeDetail(pl, context, recipes, recipeIndex + 1);
            }
            return false;
        });

        menu.open(player);
    }

    private @Nonnull List<Object> getEnabledRecipes(@Nonnull BrowserContext context, @Nonnull World world) {
        RecipeCacheKey key = new RecipeCacheKey(context.machine().getId(), world.getUID());
        return recipeCache.computeIfAbsent(key, ignored -> {
            try {
                List<?> rawRecipes = context.adapter().getRecipes(context.machine());
                List<Object> enabled = new ArrayList<>(rawRecipes.size());
                for (Object recipe : rawRecipes) {
                    if (recipe != null && !isDisabled(recipe, world)) {
                        enabled.add(recipe);
                    }
                }
                return Collections.unmodifiableList(enabled);
            } catch (ReflectiveOperationException | LinkageError exception) {
                logAdapterFailure(context.machine(), exception);
                return List.of();
            }
        });
    }

    private boolean isDisabled(@Nonnull Object recipe, @Nonnull World world) {
        Optional<RecipeMethods> methods = methodsForRecipe(recipe.getClass());
        if (methods.isEmpty()) {
            return false;
        }
        try {
            Object result = methods.get().isDisabledIn().invoke(recipe, world);
            return result instanceof Boolean disabled && disabled;
        } catch (IllegalAccessException | InvocationTargetException exception) {
            return false;
        }
    }

    private @Nullable MachineRecipeData readRecipe(@Nonnull Object rawRecipe) {
        Optional<RecipeMethods> optionalMethods = methodsForRecipe(rawRecipe.getClass());
        if (optionalMethods.isEmpty()) {
            return null;
        }

        try {
            RecipeMethods methods = optionalMethods.get();
            Object inputValue = methods.inputs().invoke(rawRecipe);
            Object outputValue = methods.outputs().invoke(rawRecipe);
            if (!(inputValue instanceof List<?> rawInputs) || !(outputValue instanceof List<?> rawOutputs)) {
                return null;
            }

            List<IngredientData> inputs = new ArrayList<>(rawInputs.size());
            for (Object rawChoice : rawInputs) {
                IngredientData ingredient = readIngredient(rawChoice);
                if (ingredient != null && !ingredient.choices().isEmpty()) {
                    inputs.add(ingredient);
                }
            }

            List<ItemStack> outputs = new ArrayList<>(rawOutputs.size());
            for (Object rawOutput : rawOutputs) {
                if (rawOutput instanceof ItemStack output && output.getType() != Material.AIR) {
                    outputs.add(output.clone());
                }
            }
            return outputs.isEmpty() ? null : new MachineRecipeData(List.copyOf(inputs), List.copyOf(outputs));
        } catch (IllegalAccessException | InvocationTargetException | LinkageError exception) {
            plugin.getLogger().log(Level.FINE, "Could not read a FastMachines recipe for the enhanced guide", exception);
            return null;
        }
    }

    private @Nullable IngredientData readIngredient(@Nullable Object rawChoice) {
        if (rawChoice == null) {
            return null;
        }
        Optional<ChoiceMethods> optionalMethods = choiceMethods.computeIfAbsent(rawChoice.getClass(), this::findChoiceMethods);
        if (optionalMethods.isEmpty()) {
            return null;
        }

        try {
            Object value = optionalMethods.get().choices().invoke(rawChoice);
            if (!(value instanceof Map<?, ?> rawChoices)) {
                return null;
            }

            List<ItemStack> choices = new ArrayList<>(rawChoices.size());
            for (Map.Entry<?, ?> entry : rawChoices.entrySet()) {
                Object rawWrapper = entry.getKey();
                if (rawWrapper == null || !(entry.getValue() instanceof Number amountValue)) {
                    continue;
                }
                Optional<WrapperMethods> methods =
                        wrapperMethods.computeIfAbsent(rawWrapper.getClass(), this::findWrapperMethods);
                if (methods.isEmpty()) {
                    continue;
                }
                Object baseItem = methods.get().baseItem().invoke(rawWrapper);
                if (baseItem instanceof ItemStack stack && stack.getType() != Material.AIR) {
                    ItemStack choice = stack.clone();
                    int required = Math.max(1, amountValue.intValue());
                    choice.setAmount(Math.min(required, choice.getMaxStackSize()));
                    choices.add(addLore(choice, "", ChatColor.GRAY + "Required: " + ChatColor.WHITE + required));
                }
            }
            return choices.isEmpty() ? null : new IngredientData(List.copyOf(choices));
        } catch (IllegalAccessException | InvocationTargetException | LinkageError exception) {
            return null;
        }
    }

    private @Nonnull Optional<FastMachinesAdapter> adapterFor(@Nonnull Class<?> machineClass) {
        return adapters.computeIfAbsent(machineClass, this::findFastMachinesAdapter);
    }

    private @Nonnull Optional<FastMachinesAdapter> findFastMachinesAdapter(@Nonnull Class<?> machineClass) {
        if (!machineClass.getName().startsWith(FAST_MACHINES_PACKAGE)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new FastMachinesAdapter(machineClass.getMethod("getRecipes")));
        } catch (NoSuchMethodException | SecurityException exception) {
            return Optional.empty();
        }
    }

    private @Nonnull Optional<RecipeMethods> methodsForRecipe(@Nonnull Class<?> recipeClass) {
        return recipeMethods.computeIfAbsent(recipeClass, this::findRecipeMethods);
    }

    private @Nonnull Optional<RecipeMethods> findRecipeMethods(@Nonnull Class<?> recipeClass) {
        try {
            return Optional.of(new RecipeMethods(
                    recipeClass.getMethod("getInputs"),
                    recipeClass.getMethod("getOutputs"),
                    recipeClass.getMethod("isDisabledIn", World.class)));
        } catch (NoSuchMethodException | SecurityException exception) {
            return Optional.empty();
        }
    }

    private @Nonnull Optional<ChoiceMethods> findChoiceMethods(@Nonnull Class<?> choiceClass) {
        try {
            return Optional.of(new ChoiceMethods(choiceClass.getMethod("getChoices")));
        } catch (NoSuchMethodException | SecurityException exception) {
            return Optional.empty();
        }
    }

    private @Nonnull Optional<WrapperMethods> findWrapperMethods(@Nonnull Class<?> wrapperClass) {
        try {
            return Optional.of(new WrapperMethods(wrapperClass.getMethod("getBaseItem")));
        } catch (NoSuchMethodException | SecurityException exception) {
            return Optional.empty();
        }
    }

    private int findButtonSlot(@Nonnull Inventory inventory) {
        for (int slot : BUTTON_SLOTS) {
            if (slot < inventory.getSize() && isEmpty(inventory.getItem(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private @Nonnull ItemStack createButton(int rawRecipeCount) {
        ItemStack button = new CustomItemStack(
                Material.KNOWLEDGE_BOOK,
                "&6&lMachine Recipes",
                "",
                "&7View everything this FastMachine can process.",
                "&7Loaded recipes: &f" + rawRecipeCount,
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
            @Nullable MachineRecipeData recipe, int recipeIndex, int totalRecipes) {
        if (recipe == null || recipe.outputs().isEmpty()) {
            return new CustomItemStack(
                    Material.BARRIER,
                    "&cRecipe " + (recipeIndex + 1),
                    "&7This recipe could not be displayed.");
        }
        return addLore(
                recipe.outputs().get(0),
                "",
                ChatColor.GOLD + "Machine recipe " + ChatColor.WHITE + (recipeIndex + 1) + ChatColor.GRAY + "/"
                        + totalRecipes,
                ChatColor.GRAY + "Inputs: " + ChatColor.WHITE + recipe.inputs().size(),
                ChatColor.YELLOW + "Left-click: " + ChatColor.GRAY + "View full recipe",
                ChatColor.YELLOW + "Right-click: " + ChatColor.GRAY + "View output's own recipe");
    }

    private @Nonnull ItemStack createIngredientIcon(
            @Nonnull IngredientData ingredient, int ingredientIndex, int selectedAlternative) {
        int safeIndex = Math.floorMod(selectedAlternative, ingredient.choices().size());
        ItemStack choice = ingredient.choices().get(safeIndex);
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(ChatColor.AQUA + "Ingredient " + ChatColor.WHITE + (ingredientIndex + 1));
        if (ingredient.choices().size() > 1) {
            lore.add(ChatColor.GRAY + "Alternative " + ChatColor.WHITE + (safeIndex + 1) + ChatColor.GRAY + "/"
                    + ingredient.choices().size());
            lore.add(ChatColor.YELLOW + "Left/right-click to cycle choices");
        }
        return addLore(choice, lore.toArray(new String[0]));
    }

    private @Nonnull ItemStack machineHeader(@Nonnull SlimefunItem machine, int recipeCount) {
        return addLore(
                machine.getItem(),
                "",
                ChatColor.GRAY + "Processes " + ChatColor.WHITE + recipeCount + ChatColor.GRAY + " recipes",
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

    private void logAdapterFailure(@Nonnull SlimefunItem item, @Nonnull Throwable exception) {
        plugin.getLogger().log(
                Level.WARNING,
                "Could not load machine recipes for " + item.getId() + " from " + item.getClass().getName(),
                exception);
    }

    private record FastMachinesAdapter(Method recipes) {
        @Nonnull
        List<?> getRecipes(@Nonnull SlimefunItem item)
                throws InvocationTargetException, IllegalAccessException {
            Object result = recipes.invoke(item);
            return result instanceof List<?> list ? list : List.of();
        }
    }

    private record RecipeMethods(Method inputs, Method outputs, Method isDisabledIn) {}

    private record ChoiceMethods(Method choices) {}

    private record WrapperMethods(Method baseItem) {}

    private record IngredientData(List<ItemStack> choices) {}

    private record MachineRecipeData(List<IngredientData> inputs, List<ItemStack> outputs) {}

    private record RecipeCacheKey(String machineId, UUID worldId) {}

    private record BrowserContext(
            Inventory guideInventory,
            PlayerProfile profile,
            EnhancedSurvivalSlimefunGuide guide,
            SlimefunItem machine,
            FastMachinesAdapter adapter,
            int buttonSlot,
            long expiresAt) {}
}
