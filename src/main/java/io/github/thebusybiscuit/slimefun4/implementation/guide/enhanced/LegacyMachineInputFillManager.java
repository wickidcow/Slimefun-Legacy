package io.github.thebusybiscuit.slimefun4.implementation.guide.enhanced;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.bakedlibs.dough.items.CustomItemStack;
import io.github.bakedlibs.dough.items.ItemUtils;
import io.github.bakedlibs.dough.protection.Interaction;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.virtual.VirtualItemHandler.ComparisonResult;
import io.github.thebusybiscuit.slimefun4.api.items.virtual.VirtualItemHandler.InventoryContext;
import io.github.thebusybiscuit.slimefun4.api.items.virtual.VirtualItemHandler.MatchContext;
import io.github.thebusybiscuit.slimefun4.api.recipes.machine.MachineInputFillAdapter;
import io.github.thebusybiscuit.slimefun4.api.recipes.machine.MachineInputFillRecipe;
import io.github.thebusybiscuit.slimefun4.api.recipes.machine.MachineRecipeDisplay;
import io.github.thebusybiscuit.slimefun4.api.recipes.machine.MachineRecipeIngredient;
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.AContainer;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineRecipe;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Safely prepares a selected recipe inside a placed machine supported by a {@link MachineInputFillAdapter}.
 *
 * <p>Standard {@link AContainer} machines and registered custom-machine adapters share the same protection checks,
 * region ownership, simulation, commit validation and rollback path. This manager never creates outputs, starts an
 * operation or changes energy. The machine's own ticker remains responsible for processing inserted ingredients.
 */
public final class LegacyMachineInputFillManager {

    private static LegacyMachineInputFillManager instance;

    private final Slimefun plugin;

    private LegacyMachineInputFillManager(@Nonnull Slimefun plugin) {
        this.plugin = plugin;
    }

    public static synchronized void initialize(@Nonnull Slimefun plugin) {
        LegacyMachineInputFillAdapters.registerDefaults(plugin);
        instance = new LegacyMachineInputFillManager(plugin);
    }

    public static @Nonnull LegacyMachineInputFillManager get() {
        if (instance == null) {
            throw new IllegalStateException("Enhanced guide machine input filling was accessed before initialization");
        }
        return instance;
    }

    public boolean supports(@Nonnull SlimefunItem machine, @Nonnull MachineRecipeDisplay recipe) {
        return LegacyGuideSettings.get().hasMachineInputFill()
                && LegacyMachineInputFillAdapters.findAdapter(plugin, machine, recipe) != null;
    }

    public @Nonnull ItemStack createButton(@Nonnull SlimefunItem machine, @Nonnull MachineRecipeDisplay recipe) {
        int ingredients = recipe.getInputs().size();
        MachineInputFillAdapter adapter = LegacyMachineInputFillAdapters.findAdapter(plugin, machine, recipe);
        String adapterName = adapter == null ? "Unavailable" : adapter.getDisplayName();
        return new CustomItemStack(
                Material.HOPPER,
                "&a&lFill Machine Inputs",
                "",
                "&7Move this recipe from your inventory",
                "&7into the placed machine's input slots.",
                "&7Ingredients: &f" + ingredients,
                "&7Machine: &f" + ItemUtils.getItemName(machine.getItem()),
                "&7Adapter: &f" + adapterName,
                "&7Supports verified standard and custom addon machines.",
                "",
                "&eLeft-click: &7Fill one recipe set",
                "&eShift + left-click: &7Fill the maximum safe amount",
                "",
                "&8The machine still processes normally.");
    }

    public void fill(
            @Nonnull Player player,
            @Nonnull SlimefunItem guideMachine,
            @Nonnull MachineRecipeDisplay recipe,
            @Nonnull int[] selectedAlternatives,
            boolean maximum) {
        LegacyGuideSettings settings = LegacyGuideSettings.get();
        if (!settings.hasMachineInputFill()) {
            send(player, ChatColor.RED + "Machine input filling is disabled on this server.");
            return;
        }

        MachineInputFillAdapter adapter = LegacyMachineInputFillAdapters.findAdapter(plugin, guideMachine, recipe);
        if (adapter == null) {
            send(player, ChatColor.RED + "This machine recipe does not have a compatible input-fill adapter.");
            return;
        }

        Block target = player.getTargetBlockExact(settings.getMachineInputFillTargetRange());
        if (target == null) {
            send(player, ChatColor.RED + "Aim at the placed machine, then click the fill button again.");
            return;
        }

        if (!Slimefun.getSchedulerService().isOwnedByCurrentRegion(target.getLocation())) {
            send(player, ChatColor.RED + "Move closer to the machine and try again.");
            return;
        }

        if (!Slimefun.getProtectionManager().hasPermission(player, target, Interaction.INTERACT_BLOCK)) {
            send(player, ChatColor.RED + "You do not have permission to access this machine.");
            return;
        }

        SlimefunItem placedItem = StorageCacheUtils.getSlimefunItem(target.getLocation());
        if (placedItem == null || !adapter.supports(placedItem) || !adapter.isValidTarget(guideMachine, placedItem)) {
            send(
                    player,
                    ChatColor.RED + "Aim at a placed " + ItemUtils.getItemName(guideMachine.getItem())
                            + ", then try again.");
            return;
        }

        BlockMenu menu = StorageCacheUtils.getMenu(target.getLocation());
        if (menu == null || menu.locked()) {
            send(player, ChatColor.RED + "That machine inventory is unavailable right now.");
            return;
        }

        if (menu.hasViewer() || Slimefun.getTickerTask().isInventoryViewed(target.getLocation())) {
            send(player, ChatColor.RED + "Close the machine inventory before filling it from the guide.");
            return;
        }

        try {
            if (!adapter.isSafeToFill(player, placedItem, target, menu)) {
                send(player, ChatColor.RED + "This machine cannot be filled safely in its current state.");
                return;
            }
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().log(Level.WARNING, "A machine input-fill adapter safety check failed", exception);
            send(player, ChatColor.RED + "The machine adapter could not verify that filling is safe.");
            return;
        }

        MachineInputFillRecipe resolved;
        try {
            resolved = adapter.resolve(placedItem, recipe, selectedAlternatives.clone());
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().log(Level.WARNING, "A machine input-fill adapter could not resolve a recipe", exception);
            send(player, ChatColor.RED + "The selected recipe could not be resolved safely.");
            return;
        }
        if (resolved == null) {
            send(player, ChatColor.RED + "The selected ingredients do not match an authoritative machine recipe.");
            return;
        }

        List<ItemStack> requirements = resolved.getIngredients();
        int[] inputSlots = resolved.getInputSlots();
        int[] protectedSlots = resolved.getProtectedSlots();
        if (requirements.size() > inputSlots.length) {
            send(player, ChatColor.RED + "This recipe uses more inputs than the machine exposes.");
            return;
        }
        if (!validInputSlots(inputSlots, menu.getSize())) {
            send(player, ChatColor.RED + "This machine adapter exposes an invalid input-slot layout.");
            return;
        }
        if (!validProtectedSlots(protectedSlots, menu.getSize())) {
            send(player, ChatColor.RED + "This machine adapter exposes an invalid protected-slot layout.");
            return;
        }
        if (!slotsAreDisjoint(inputSlots, protectedSlots)) {
            send(
                    player,
                    ChatColor.RED + "This machine adapter overlaps input and protected slots, so filling was blocked.");
            return;
        }

        PlayerInventory playerInventory = player.getInventory();
        ItemStack[] originalPlayer = cloneContents(playerInventory.getStorageContents());
        ItemStack[] originalMachine = readSlots(menu, inputSlots);

        Slimefun.getTickerTask().setInventoryViewed(target.getLocation(), true);
        try {
            boolean fillMaximum = maximum && resolved.isMaximumFillAllowed();
            FillPlan plan = fillMaximum
                    ? planMaximum(
                            originalPlayer,
                            originalMachine,
                            requirements,
                            resolved.resolveMaximumSets(settings.getMachineInputFillMaximumSets()),
                            LegacyMachineInputFillManager::matchesRecipeInput,
                            LegacyMachineInputFillManager::canStackTogether,
                            LegacyMachineInputFillManager::maximumStackSize,
                            LegacyMachineInputFillManager::canEnterEmptySlot)
                    : plan(
                            originalPlayer,
                            originalMachine,
                            requirements,
                            1,
                            LegacyMachineInputFillManager::matchesRecipeInput,
                            LegacyMachineInputFillManager::canStackTogether,
                            LegacyMachineInputFillManager::maximumStackSize,
                            LegacyMachineInputFillManager::canEnterEmptySlot);

            if (!plan.success()) {
                send(player, ChatColor.RED + plan.message());
                sendMissing(player, plan.missing());
                return;
            }

            try {
                playerInventory.setStorageContents(cloneContents(plan.playerContents()));
                writeSlots(menu, inputSlots, plan.machineContents(), true);
                validateCommittedSlots(menu, inputSlots, plan.machineContents());
            } catch (RuntimeException exception) {
                restore(playerInventory, originalPlayer, menu, inputSlots, originalMachine);
                player.updateInventory();
                plugin.getLogger()
                        .log(Level.SEVERE, "Could not commit an enhanced-guide machine input fill", exception);
                send(player, ChatColor.RED + "The transfer was cancelled and both inventories were restored.");
                return;
            }

            player.updateInventory();
            SoundEffect.GUIDE_BUTTON_CLICK_SOUND.playFor(player);
            if (plan.movedItems() == 0) {
                send(player, ChatColor.YELLOW + "The machine already contains a complete recipe.");
            } else {
                send(
                        player,
                        ChatColor.GREEN + "Filled " + ItemUtils.getItemName(guideMachine.getItem()) + " for "
                                + plan.sets() + (plan.sets() == 1 ? " recipe set." : " recipe sets."));
            }

            if (settings.shouldCloseGuideAfterMachineInputFill()) {
                player.closeInventory();
            }
        } finally {
            if (!menu.hasViewer()) {
                Slimefun.getTickerTask().setInventoryViewed(target.getLocation(), false);
            }
        }
    }

    static boolean hasCompatibleRegisteredRecipe(
            @Nonnull List<MachineRecipe> registeredRecipes,
            @Nonnull MachineRecipeDisplay display,
            @Nonnull IngredientMatcher inputMatcher,
            @Nonnull StackMatcher outputMatcher) {
        List<List<ItemStack>> displayedInputs = displayChoices(display);
        List<ItemStack> displayedOutputs = display.getOutputs();
        for (MachineRecipe registered : registeredRecipes) {
            if (registered != null
                    && matchesRegisteredRecipe(
                            registered, displayedInputs, displayedOutputs, inputMatcher, outputMatcher)) {
                return true;
            }
        }
        return false;
    }

    static @Nullable List<ItemStack> resolveRegisteredRequirements(
            @Nonnull List<MachineRecipe> registeredRecipes,
            @Nonnull MachineRecipeDisplay display,
            @Nonnull int[] selectedAlternatives,
            @Nonnull IngredientMatcher inputMatcher,
            @Nonnull StackMatcher outputMatcher) {
        List<ItemStack> selected = selectedRequirements(display, selectedAlternatives);
        List<List<ItemStack>> selectedInputs = new ArrayList<>(selected.size());
        for (ItemStack item : selected) {
            selectedInputs.add(List.of(item));
        }

        List<ItemStack> displayedOutputs = display.getOutputs();
        for (MachineRecipe registered : registeredRecipes) {
            if (registered != null
                    && matchesRegisteredRecipe(
                            registered, selectedInputs, displayedOutputs, inputMatcher, outputMatcher)) {
                return registeredInputs(registered);
            }
        }
        return null;
    }

    private static boolean matchesRegisteredRecipe(
            @Nonnull MachineRecipe registered,
            @Nonnull List<List<ItemStack>> displayedInputs,
            @Nonnull List<ItemStack> displayedOutputs,
            @Nonnull IngredientMatcher inputMatcher,
            @Nonnull StackMatcher outputMatcher) {
        List<ItemStack> registeredInputs = registeredInputs(registered);
        List<ItemStack> registeredOutputs = registeredOutputs(registered);
        return matchesChoiceGroups(registeredInputs, displayedInputs, inputMatcher)
                && matchesStacks(registeredOutputs, displayedOutputs, outputMatcher);
    }

    private static @Nonnull List<List<ItemStack>> displayChoices(@Nonnull MachineRecipeDisplay display) {
        List<MachineRecipeIngredient> inputs = display.getInputs();
        List<List<ItemStack>> choices = new ArrayList<>(inputs.size());
        for (MachineRecipeIngredient ingredient : inputs) {
            choices.add(ingredient.getChoices());
        }
        return List.copyOf(choices);
    }

    private static @Nonnull List<ItemStack> registeredInputs(@Nonnull MachineRecipe recipe) {
        return nonEmptyStacks(recipe.getInput());
    }

    private static @Nonnull List<ItemStack> registeredOutputs(@Nonnull MachineRecipe recipe) {
        return nonEmptyStacks(recipe.getOutput());
    }

    private static @Nonnull List<ItemStack> nonEmptyStacks(@Nonnull ItemStack[] source) {
        List<ItemStack> items = new ArrayList<>(source.length);
        for (ItemStack item : source) {
            if (!isEmpty(item)) {
                items.add(item.clone());
            }
        }
        return List.copyOf(items);
    }

    private static boolean matchesChoiceGroups(
            @Nonnull List<ItemStack> registered,
            @Nonnull List<List<ItemStack>> displayed,
            @Nonnull IngredientMatcher matcher) {
        if (registered.size() != displayed.size()) {
            return false;
        }
        return matchChoiceGroup(0, registered, displayed, new boolean[displayed.size()], matcher);
    }

    private static boolean matchChoiceGroup(
            int registeredIndex,
            @Nonnull List<ItemStack> registered,
            @Nonnull List<List<ItemStack>> displayed,
            @Nonnull boolean[] usedDisplayed,
            @Nonnull IngredientMatcher matcher) {
        if (registeredIndex >= registered.size()) {
            return true;
        }

        ItemStack expected = registered.get(registeredIndex);
        for (int displayedIndex = 0; displayedIndex < displayed.size(); displayedIndex++) {
            if (usedDisplayed[displayedIndex] || !anyChoiceMatches(displayed.get(displayedIndex), expected, matcher)) {
                continue;
            }
            usedDisplayed[displayedIndex] = true;
            if (matchChoiceGroup(registeredIndex + 1, registered, displayed, usedDisplayed, matcher)) {
                return true;
            }
            usedDisplayed[displayedIndex] = false;
        }
        return false;
    }

    private static boolean anyChoiceMatches(
            @Nonnull List<ItemStack> choices, @Nonnull ItemStack expected, @Nonnull IngredientMatcher matcher) {
        for (ItemStack choice : choices) {
            if (!isEmpty(choice) && choice.getAmount() == expected.getAmount() && matcher.matches(choice, expected)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesStacks(
            @Nonnull List<ItemStack> registered, @Nonnull List<ItemStack> displayed, @Nonnull StackMatcher matcher) {
        if (registered.size() != displayed.size()) {
            return false;
        }
        return matchStack(0, registered, displayed, new boolean[displayed.size()], matcher);
    }

    private static boolean matchStack(
            int registeredIndex,
            @Nonnull List<ItemStack> registered,
            @Nonnull List<ItemStack> displayed,
            @Nonnull boolean[] usedDisplayed,
            @Nonnull StackMatcher matcher) {
        if (registeredIndex >= registered.size()) {
            return true;
        }

        ItemStack expected = registered.get(registeredIndex);
        for (int displayedIndex = 0; displayedIndex < displayed.size(); displayedIndex++) {
            ItemStack candidate = displayed.get(displayedIndex);
            if (usedDisplayed[displayedIndex]
                    || isEmpty(candidate)
                    || candidate.getAmount() != expected.getAmount()
                    || !matcher.matches(candidate, expected)) {
                continue;
            }
            usedDisplayed[displayedIndex] = true;
            if (matchStack(registeredIndex + 1, registered, displayed, usedDisplayed, matcher)) {
                return true;
            }
            usedDisplayed[displayedIndex] = false;
        }
        return false;
    }

    private static @Nonnull List<ItemStack> selectedRequirements(
            @Nonnull MachineRecipeDisplay recipe, @Nonnull int[] selectedAlternatives) {
        List<MachineRecipeIngredient> inputs = recipe.getInputs();
        List<ItemStack> selected = new ArrayList<>(inputs.size());
        for (int index = 0; index < inputs.size(); index++) {
            List<ItemStack> choices = inputs.get(index).getChoices();
            int selectedIndex = index < selectedAlternatives.length ? selectedAlternatives[index] : 0;
            selected.add(
                    choices.get(Math.floorMod(selectedIndex, choices.size())).clone());
        }
        return List.copyOf(selected);
    }

    static @Nonnull FillPlan planMaximum(
            @Nonnull ItemStack[] originalPlayer,
            @Nonnull ItemStack[] originalMachine,
            @Nonnull List<ItemStack> requirements,
            int maximumSets,
            @Nonnull IngredientMatcher matcher,
            @Nonnull StackMatcher stackMatcher,
            @Nonnull MaxStackResolver maxStackResolver,
            @Nonnull EmptySlotAdmission emptySlotAdmission) {
        FillPlan best = null;
        for (int sets = 1; sets <= maximumSets; sets++) {
            FillPlan candidate = plan(
                    originalPlayer,
                    originalMachine,
                    requirements,
                    sets,
                    matcher,
                    stackMatcher,
                    maxStackResolver,
                    emptySlotAdmission);
            if (!candidate.success()) {
                return best == null ? candidate : best;
            }
            best = candidate;
        }
        return best == null ? FillPlan.failure("This recipe has no transferable ingredients.", List.of()) : best;
    }

    static @Nonnull FillPlan plan(
            @Nonnull ItemStack[] originalPlayer,
            @Nonnull ItemStack[] originalMachine,
            @Nonnull List<ItemStack> requirements,
            int sets,
            @Nonnull IngredientMatcher matcher,
            @Nonnull StackMatcher stackMatcher,
            @Nonnull MaxStackResolver maxStackResolver,
            @Nonnull EmptySlotAdmission emptySlotAdmission) {
        if (requirements.isEmpty()) {
            return FillPlan.failure("This recipe has no transferable ingredients.", List.of());
        }
        if (requirements.size() > originalMachine.length) {
            return FillPlan.failure("This recipe uses more inputs than the machine exposes.", List.of());
        }

        ItemStack[] player = cloneContents(originalPlayer);
        ItemStack[] machine = cloneContents(originalMachine);
        int[] assignment = assignMachineSlots(machine, requirements, matcher);
        if (assignment == null) {
            return FillPlan.failure("Clear unrelated items from the machine's input slots first.", List.of());
        }

        List<Integer> freeSlots = new ArrayList<>();
        boolean[] usedRequirements = new boolean[requirements.size()];
        for (int slot = 0; slot < assignment.length; slot++) {
            if (assignment[slot] >= 0) {
                usedRequirements[assignment[slot]] = true;
            } else if (isEmpty(machine[slot])) {
                freeSlots.add(slot);
            }
        }

        for (int requirement = 0; requirement < requirements.size(); requirement++) {
            if (!usedRequirements[requirement]) {
                if (freeSlots.isEmpty()) {
                    return FillPlan.failure("The machine does not have enough free input slots.", List.of());
                }
                assignment[freeSlots.remove(0)] = requirement;
                usedRequirements[requirement] = true;
            }
        }

        List<MissingIngredient> missing = new ArrayList<>();
        int moved = 0;
        for (int machineSlot = 0; machineSlot < assignment.length; machineSlot++) {
            int requirementIndex = assignment[machineSlot];
            if (requirementIndex < 0) {
                continue;
            }

            ItemStack expected = requirements.get(requirementIndex);
            int required;
            try {
                required = Math.multiplyExact(Math.max(1, expected.getAmount()), sets);
            } catch (ArithmeticException exception) {
                return FillPlan.failure("The requested recipe amount is too large.", List.of());
            }

            ItemStack current = machine[machineSlot];
            int currentAmount = isEmpty(current) ? 0 : current.getAmount();
            if (currentAmount >= required) {
                continue;
            }

            int deficit = required - currentAmount;
            ItemStack template =
                    current == null ? findTemplate(player, expected, deficit, matcher, stackMatcher) : current.clone();
            if (template == null) {
                missing.add(new MissingIngredient(expected.clone(), deficit, countMatching(player, expected, matcher)));
                continue;
            }
            if (isEmpty(current) && !emptySlotAdmission.allows(template)) {
                return FillPlan.failure("A required virtual item cannot enter an empty machine slot.", List.of());
            }

            int maximumStack = maxStackResolver.resolve(template);
            if (required > maximumStack) {
                return FillPlan.failure(
                        "The machine input slots cannot hold " + sets + " full recipe sets.", List.of());
            }

            int extracted = removeMatching(player, expected, template, deficit, matcher, stackMatcher);
            if (extracted < deficit) {
                missing.add(new MissingIngredient(expected.clone(), deficit, extracted));
                continue;
            }

            if (isEmpty(current)) {
                ItemStack inserted = template.clone();
                inserted.setAmount(deficit);
                machine[machineSlot] = inserted;
            } else {
                current.setAmount(currentAmount + deficit);
            }
            moved += deficit;
        }

        if (!missing.isEmpty()) {
            return FillPlan.failure("Not all machine ingredients are available.", mergeMissing(missing, matcher));
        }
        return FillPlan.success(player, machine, sets, moved);
    }

    private static @Nullable int[] assignMachineSlots(
            @Nonnull ItemStack[] machine, @Nonnull List<ItemStack> requirements, @Nonnull IngredientMatcher matcher) {
        List<Integer> occupied = new ArrayList<>();
        for (int slot = 0; slot < machine.length; slot++) {
            if (!isEmpty(machine[slot])) {
                occupied.add(slot);
            }
        }
        occupied.sort(
                Comparator.comparingInt((Integer slot) -> matchingRequirements(machine[slot], requirements, matcher)));

        int[] assignment = new int[machine.length];
        Arrays.fill(assignment, -1);
        boolean[] usedRequirements = new boolean[requirements.size()];
        return assignOccupied(0, occupied, machine, requirements, matcher, assignment, usedRequirements)
                ? assignment
                : null;
    }

    private static boolean assignOccupied(
            int index,
            @Nonnull List<Integer> occupied,
            @Nonnull ItemStack[] machine,
            @Nonnull List<ItemStack> requirements,
            @Nonnull IngredientMatcher matcher,
            @Nonnull int[] assignment,
            @Nonnull boolean[] usedRequirements) {
        if (index >= occupied.size()) {
            return true;
        }

        int machineSlot = occupied.get(index);
        ItemStack current = machine[machineSlot];
        for (int requirement = 0; requirement < requirements.size(); requirement++) {
            if (usedRequirements[requirement] || !matcher.matches(current, requirements.get(requirement))) {
                continue;
            }
            assignment[machineSlot] = requirement;
            usedRequirements[requirement] = true;
            if (assignOccupied(index + 1, occupied, machine, requirements, matcher, assignment, usedRequirements)) {
                return true;
            }
            usedRequirements[requirement] = false;
            assignment[machineSlot] = -1;
        }
        return false;
    }

    private static int matchingRequirements(
            @Nonnull ItemStack current, @Nonnull List<ItemStack> requirements, @Nonnull IngredientMatcher matcher) {
        int matches = 0;
        for (ItemStack requirement : requirements) {
            if (matcher.matches(current, requirement)) {
                matches++;
            }
        }
        return matches;
    }

    private static @Nullable ItemStack findTemplate(
            @Nonnull ItemStack[] player,
            @Nonnull ItemStack expected,
            int amount,
            @Nonnull IngredientMatcher matcher,
            @Nonnull StackMatcher stackMatcher) {
        ItemStack best = null;
        int bestAvailable = 0;
        for (ItemStack candidate : player) {
            if (isEmpty(candidate) || !matcher.matches(candidate, expected)) {
                continue;
            }
            int available = 0;
            for (ItemStack source : player) {
                if (!isEmpty(source) && matcher.matches(source, expected) && stackMatcher.matches(source, candidate)) {
                    available += source.getAmount();
                }
            }
            if (available >= amount) {
                return candidate.clone();
            }
            if (available > bestAvailable) {
                best = candidate.clone();
                bestAvailable = available;
            }
        }
        return bestAvailable >= amount ? best : null;
    }

    private static int removeMatching(
            @Nonnull ItemStack[] player,
            @Nonnull ItemStack expected,
            @Nonnull ItemStack template,
            int amount,
            @Nonnull IngredientMatcher matcher,
            @Nonnull StackMatcher stackMatcher) {
        ItemStack[] trial = cloneContents(player);
        int remaining = amount;
        for (int slot = 0; slot < trial.length && remaining > 0; slot++) {
            ItemStack source = trial[slot];
            if (isEmpty(source) || !matcher.matches(source, expected) || !stackMatcher.matches(source, template)) {
                continue;
            }
            int taken = Math.min(remaining, source.getAmount());
            decrement(trial, slot, taken);
            remaining -= taken;
        }
        if (remaining > 0) {
            return amount - remaining;
        }
        for (int slot = 0; slot < player.length; slot++) {
            player[slot] = cloneItem(trial[slot]);
        }
        return amount;
    }

    private static int countMatching(
            @Nonnull ItemStack[] player, @Nonnull ItemStack expected, @Nonnull IngredientMatcher matcher) {
        int available = 0;
        for (ItemStack item : player) {
            if (!isEmpty(item) && matcher.matches(item, expected)) {
                available += item.getAmount();
            }
        }
        return available;
    }

    private static @Nonnull List<MissingIngredient> mergeMissing(
            @Nonnull List<MissingIngredient> source, @Nonnull IngredientMatcher matcher) {
        List<MissingIngredient> merged = new ArrayList<>();
        for (MissingIngredient candidate : source) {
            int existingIndex = -1;
            for (int index = 0; index < merged.size(); index++) {
                MissingIngredient existing = merged.get(index);
                if (matcher.matches(existing.expected(), candidate.expected())
                        && matcher.matches(candidate.expected(), existing.expected())) {
                    existingIndex = index;
                    break;
                }
            }
            if (existingIndex < 0) {
                merged.add(candidate);
            } else {
                MissingIngredient existing = merged.get(existingIndex);
                merged.set(
                        existingIndex,
                        new MissingIngredient(
                                existing.expected(),
                                existing.required() + candidate.required(),
                                Math.max(existing.available(), candidate.available())));
            }
        }
        return List.copyOf(merged);
    }

    static boolean matchesRecipeInput(@Nullable ItemStack actual, @Nonnull ItemStack expected) {
        return !isEmpty(actual)
                && Slimefun.getItemStackService().isSimilar(actual, expected, MatchContext.RECIPE_INPUT, true, false);
    }

    static boolean canStackTogether(@Nonnull ItemStack first, @Nonnull ItemStack second) {
        ComparisonResult comparison = Slimefun.getItemStackService().matches(first, second, MatchContext.STACK_MERGE);
        if (comparison == ComparisonResult.MATCH) {
            return true;
        }
        if (comparison == ComparisonResult.NO_MATCH) {
            return false;
        }
        return SlimefunUtils.isItemSimilarWithoutVirtualItems(first, second, true, false, true, true);
    }

    private static int maximumStackSize(@Nonnull ItemStack item) {
        return Math.min(
                item.getMaxStackSize(),
                Slimefun.getItemStackService()
                        .getMaxStackSize(item, InventoryContext.MENU_INSERT, item.getMaxStackSize()));
    }

    private static boolean canEnterEmptySlot(@Nonnull ItemStack item) {
        return Slimefun.getItemStackService().canInsertIntoEmptySlot(item, InventoryContext.MENU_INSERT);
    }

    private static void validateCommittedSlots(
            @Nonnull BlockMenu menu, @Nonnull int[] slots, @Nonnull ItemStack[] expected) {
        for (int index = 0; index < slots.length; index++) {
            ItemStack actual = menu.getItemInSlot(slots[index]);
            ItemStack planned = expected[index];
            if (isEmpty(actual) && isEmpty(planned)) {
                continue;
            }
            if (isEmpty(actual)
                    || isEmpty(planned)
                    || actual.getAmount() != planned.getAmount()
                    || !canStackTogether(actual, planned)) {
                throw new IllegalStateException("A machine input slot rejected or changed the planned ingredient");
            }
        }
    }

    private static boolean validInputSlots(@Nonnull int[] slots, int menuSize) {
        if (slots.length == 0) {
            return false;
        }
        boolean[] seen = new boolean[menuSize];
        for (int slot : slots) {
            if (slot < 0 || slot >= menuSize || seen[slot]) {
                return false;
            }
            seen[slot] = true;
        }
        return true;
    }

    private static boolean validProtectedSlots(@Nonnull int[] slots, int menuSize) {
        boolean[] seen = new boolean[menuSize];
        for (int slot : slots) {
            if (slot < 0 || slot >= menuSize || seen[slot]) {
                return false;
            }
            seen[slot] = true;
        }
        return true;
    }

    private static boolean slotsAreDisjoint(@Nonnull int[] first, @Nonnull int[] second) {
        for (int firstSlot : first) {
            for (int secondSlot : second) {
                if (firstSlot == secondSlot) {
                    return false;
                }
            }
        }
        return true;
    }

    private static @Nonnull ItemStack[] readSlots(@Nonnull BlockMenu menu, @Nonnull int[] slots) {
        ItemStack[] contents = new ItemStack[slots.length];
        for (int index = 0; index < slots.length; index++) {
            contents[index] = cloneItem(menu.getItemInSlot(slots[index]));
        }
        return contents;
    }

    private static void writeSlots(
            @Nonnull BlockMenu menu, @Nonnull int[] slots, @Nonnull ItemStack[] contents, boolean event) {
        for (int index = 0; index < slots.length; index++) {
            ItemStack current = menu.getItemInSlot(slots[index]);
            ItemStack planned = contents[index];
            if (sameStack(current, planned)) {
                continue;
            }
            menu.replaceExistingItem(slots[index], cloneItem(planned), event);
        }
    }

    private static boolean sameStack(@Nullable ItemStack first, @Nullable ItemStack second) {
        if (isEmpty(first) || isEmpty(second)) {
            return isEmpty(first) && isEmpty(second);
        }
        return first.getAmount() == second.getAmount() && canStackTogether(first, second);
    }

    private static void restore(
            @Nonnull PlayerInventory playerInventory,
            @Nonnull ItemStack[] playerContents,
            @Nonnull BlockMenu menu,
            @Nonnull int[] inputSlots,
            @Nonnull ItemStack[] machineContents) {
        try {
            playerInventory.setStorageContents(cloneContents(playerContents));
        } catch (RuntimeException ignored) {
            // The original failure is logged by the caller.
        }
        try {
            writeSlots(menu, inputSlots, machineContents, false);
        } catch (RuntimeException ignored) {
            // The original failure is logged by the caller.
        }
    }

    private static void sendMissing(@Nonnull Player player, @Nonnull List<MissingIngredient> missing) {
        int limit = Math.min(3, missing.size());
        for (int index = 0; index < limit; index++) {
            MissingIngredient ingredient = missing.get(index);
            int shortfall = Math.max(0, ingredient.required() - ingredient.available());
            player.sendMessage(ChatColor.DARK_GRAY + " • " + ChatColor.RED + shortfall + "x " + ChatColor.GRAY
                    + ItemUtils.getItemName(ingredient.expected()));
        }
        if (missing.size() > limit) {
            player.sendMessage(ChatColor.DARK_GRAY + " • " + ChatColor.GRAY + "Additional ingredients are missing.");
        }
    }

    private static void decrement(@Nonnull ItemStack[] contents, int slot, int amount) {
        ItemStack source = contents[slot];
        if (source == null || amount >= source.getAmount()) {
            contents[slot] = null;
        } else {
            source.setAmount(source.getAmount() - amount);
        }
    }

    private static @Nonnull ItemStack[] cloneContents(@Nonnull ItemStack[] source) {
        ItemStack[] clone = new ItemStack[source.length];
        for (int slot = 0; slot < source.length; slot++) {
            clone[slot] = cloneItem(source[slot]);
        }
        return clone;
    }

    private static @Nullable ItemStack cloneItem(@Nullable ItemStack item) {
        return isEmpty(item) ? null : item.clone();
    }

    private static boolean isEmpty(@Nullable ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }

    private static void send(@Nonnull Player player, @Nonnull String message) {
        player.sendMessage(ChatColor.DARK_GREEN + "[Slimefun Legacy] " + message);
    }

    @FunctionalInterface
    interface IngredientMatcher {
        boolean matches(@Nullable ItemStack actual, @Nonnull ItemStack expected);
    }

    @FunctionalInterface
    interface StackMatcher {
        boolean matches(@Nonnull ItemStack first, @Nonnull ItemStack second);
    }

    @FunctionalInterface
    interface MaxStackResolver {
        int resolve(@Nonnull ItemStack item);
    }

    @FunctionalInterface
    interface EmptySlotAdmission {
        boolean allows(@Nonnull ItemStack item);
    }

    record MissingIngredient(ItemStack expected, int required, int available) {}

    record FillPlan(
            boolean success,
            String message,
            ItemStack[] playerContents,
            ItemStack[] machineContents,
            int sets,
            int movedItems,
            List<MissingIngredient> missing) {

        static @Nonnull FillPlan success(
                @Nonnull ItemStack[] playerContents, @Nonnull ItemStack[] machineContents, int sets, int movedItems) {
            return new FillPlan(
                    true,
                    "",
                    cloneContents(playerContents),
                    cloneContents(machineContents),
                    sets,
                    movedItems,
                    List.of());
        }

        static @Nonnull FillPlan failure(@Nonnull String message, @Nonnull List<MissingIngredient> missing) {
            return new FillPlan(false, message, new ItemStack[0], new ItemStack[0], 0, 0, List.copyOf(missing));
        }
    }
}
