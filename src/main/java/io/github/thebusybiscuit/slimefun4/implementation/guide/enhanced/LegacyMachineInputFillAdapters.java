package io.github.thebusybiscuit.slimefun4.implementation.guide.enhanced;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunInternal;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.recipes.machine.MachineInputFillAdapter;
import io.github.thebusybiscuit.slimefun4.api.recipes.machine.MachineInputFillAdapterRegistry;
import io.github.thebusybiscuit.slimefun4.api.recipes.machine.MachineInputFillRecipe;
import io.github.thebusybiscuit.slimefun4.api.recipes.machine.MachineRecipeDisplay;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.AContainer;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineRecipe;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Registers the native and compatibility machine-input fill adapters. */
@SlimefunInternal
final class LegacyMachineInputFillAdapters {

    private LegacyMachineInputFillAdapters() {}

    static void registerDefaults(@Nonnull JavaPlugin plugin) {
        MachineInputFillAdapterRegistry.register(new FastMachinesInputFillAdapter(plugin));
        MachineInputFillAdapterRegistry.register(new SupremeGenericMachineAdapter(plugin));
        MachineInputFillAdapterRegistry.register(new StandardContainerAdapter(plugin));
    }

    static @Nullable MachineInputFillAdapter findAdapter(
            @Nonnull JavaPlugin plugin,
            @Nonnull SlimefunItem machine,
            @Nonnull MachineRecipeDisplay recipe) {
        for (MachineInputFillAdapter adapter : MachineInputFillAdapterRegistry.getAdapters()) {
            try {
                if (adapter.supports(machine) && adapter.supportsRecipe(machine, recipe)) {
                    return adapter;
                }
            } catch (RuntimeException | LinkageError exception) {
                plugin.getLogger()
                        .log(
                                Level.FINE,
                                "Machine input fill adapter " + adapter.getKey() + " rejected " + machine.getId(),
                                exception);
            }
        }
        return null;
    }

    private abstract static class BaseAdapter implements MachineInputFillAdapter {

        private final NamespacedKey key;

        BaseAdapter(@Nonnull JavaPlugin plugin, @Nonnull String key) {
            this.key = new NamespacedKey(plugin, key);
        }

        @Override
        public final @Nonnull NamespacedKey getKey() {
            return key;
        }
    }

    private static final class StandardContainerAdapter extends BaseAdapter {

        StandardContainerAdapter(JavaPlugin plugin) {
            super(plugin, "enhanced_guide_standard_container_input_fill");
        }

        @Override
        public @Nonnull String getDisplayName() {
            return "Standard AContainer";
        }

        @Override
        public int getPriority() {
            return 100;
        }

        @Override
        public boolean supports(@Nonnull SlimefunItem machine) {
            return machine instanceof AContainer;
        }

        @Override
        public boolean supportsRecipe(@Nonnull SlimefunItem machine, @Nonnull MachineRecipeDisplay recipe) {
            return machine instanceof AContainer container
                    && LegacyMachineInputFillManager.hasCompatibleRegisteredRecipe(
                            container.getMachineRecipes(),
                            recipe,
                            LegacyMachineInputFillManager::matchesRecipeInput,
                            LegacyMachineInputFillManager::canStackTogether);
        }

        @Override
        public @Nullable MachineInputFillRecipe resolve(
                @Nonnull SlimefunItem machine,
                @Nonnull MachineRecipeDisplay recipe,
                @Nonnull int[] selectedAlternatives) {
            if (!(machine instanceof AContainer container)) {
                return null;
            }

            List<ItemStack> requirements = LegacyMachineInputFillManager.resolveRegisteredRequirements(
                    container.getMachineRecipes(),
                    recipe,
                    selectedAlternatives,
                    LegacyMachineInputFillManager::matchesRecipeInput,
                    LegacyMachineInputFillManager::canStackTogether);
            if (requirements == null) {
                return null;
            }

            return MachineInputFillRecipe.builder()
                    .ingredients(requirements)
                    .inputSlots(container.getInputSlots())
                    .protectedSlots(container.getOutputSlots())
                    .label("Standard AContainer adapter")
                    .build();
        }
    }

    /** Public-surface compatibility adapter for FastMachines' custom Kotlin recipe and inventory model. */
    static final class FastMachinesInputFillAdapter extends BaseAdapter {

        private static final String PACKAGE_PREFIX = "net.guizhanss.fastmachines.";
        private static final int INVENTORY_SIZE = 54;
        private static final int FIRST_INPUT_SLOT = 0;
        private static final int LAST_INPUT_SLOT = 35;
        private static final int FIRST_PROTECTED_SLOT = 36;
        private static final int LAST_PROTECTED_SLOT = 53;

        private final JavaPlugin plugin;
        private final Map<Class<?>, Optional<FastMachineAccessors>> machineAccessors = new ConcurrentHashMap<>();
        private final Map<Class<?>, Optional<FastRecipeAccessors>> recipeAccessors = new ConcurrentHashMap<>();
        private final Map<Class<?>, Optional<Method>> choiceAccessors = new ConcurrentHashMap<>();
        private final Map<Class<?>, Optional<Method>> wrapperAccessors = new ConcurrentHashMap<>();

        FastMachinesInputFillAdapter(@Nonnull JavaPlugin plugin) {
            super(plugin, "enhanced_guide_fastmachines_input_fill");
            this.plugin = plugin;
        }

        @Override
        public @Nonnull String getDisplayName() {
            return "FastMachines";
        }

        @Override
        public int getPriority() {
            return 1100;
        }

        @Override
        public boolean supports(@Nonnull SlimefunItem machine) {
            return machine.getClass().getName().startsWith(PACKAGE_PREFIX)
                    && findMachineAccessors(machine.getClass()).isPresent();
        }

        @Override
        public boolean supportsRecipe(@Nonnull SlimefunItem machine, @Nonnull MachineRecipeDisplay recipe) {
            if (!supports(machine)) {
                return false;
            }
            return findMatchingRecipe(readAuthoritativeRecipes(machine), recipe) != null;
        }

        @Override
        public @Nullable MachineInputFillRecipe resolve(
                @Nonnull SlimefunItem machine,
                @Nonnull MachineRecipeDisplay recipe,
                @Nonnull int[] selectedAlternatives) {
            if (!supports(machine)) {
                return null;
            }
            return resolveFromObject(machine, recipe, selectedAlternatives);
        }

        @Override
        public boolean isSafeToFill(
                @Nonnull Player player,
                @Nonnull SlimefunItem machine,
                @Nonnull Block target,
                @Nonnull BlockMenu menu) {
            return menu.getSize() == INVENTORY_SIZE && isExpectedInputLayout(readInputSlots(machine));
        }

        @Nullable MachineInputFillRecipe resolveFromObject(
                @Nonnull Object machine,
                @Nonnull MachineRecipeDisplay recipe,
                @Nonnull int[] selectedAlternatives) {
            return resolveFromObject(
                    machine,
                    recipe,
                    selectedAlternatives,
                    LegacyMachineInputFillManager::matchesRecipeInput,
                    LegacyMachineInputFillManager::canStackTogether);
        }

        @Nullable MachineInputFillRecipe resolveFromObject(
                @Nonnull Object machine,
                @Nonnull MachineRecipeDisplay recipe,
                @Nonnull int[] selectedAlternatives,
                @Nonnull LegacyMachineInputFillManager.IngredientMatcher inputMatcher,
                @Nonnull LegacyMachineInputFillManager.StackMatcher outputMatcher) {
            int[] inputSlots = readInputSlots(machine);
            if (!isExpectedInputLayout(inputSlots)) {
                return null;
            }

            FastRecipeDefinition authoritative = findMatchingRecipe(
                    readAuthoritativeRecipes(machine), recipe, inputMatcher, outputMatcher);
            if (authoritative == null) {
                return null;
            }

            List<ItemStack> requirements = resolveRequirements(
                    authoritative, recipe, selectedAlternatives, inputMatcher);
            if (requirements == null) {
                return null;
            }

            return MachineInputFillRecipe.builder()
                    .ingredients(requirements)
                    .inputSlots(inputSlots)
                    .protectedSlots(slotRange(FIRST_PROTECTED_SLOT, LAST_PROTECTED_SLOT))
                    .label("FastMachines adapter")
                    .build();
        }

        @Nonnull List<FastRecipeDefinition> readAuthoritativeRecipes(@Nonnull Object machine) {
            Optional<FastMachineAccessors> accessors = machineAccessors.computeIfAbsent(
                    machine.getClass(), FastMachinesInputFillAdapter::findMachineAccessors);
            if (accessors.isEmpty()) {
                return List.of();
            }

            try {
                Object rawRecipes = accessors.get().recipes().invoke(machine);
                List<FastRecipeDefinition> recipes = new ArrayList<>();
                for (Object rawRecipe : objects(rawRecipes)) {
                    FastRecipeDefinition recipe = readRecipe(rawRecipe);
                    if (recipe != null) {
                        recipes.add(recipe);
                    }
                }
                return List.copyOf(recipes);
            } catch (IllegalAccessException | InvocationTargetException | LinkageError exception) {
                plugin.getLogger().log(Level.FINE, "Could not read FastMachines recipes", exception);
                return List.of();
            }
        }

        private @Nonnull int[] readInputSlots(@Nonnull Object machine) {
            Optional<FastMachineAccessors> accessors = machineAccessors.computeIfAbsent(
                    machine.getClass(), FastMachinesInputFillAdapter::findMachineAccessors);
            if (accessors.isEmpty()) {
                return new int[0];
            }

            try {
                Object value = accessors.get().inputSlots().invoke(machine);
                if (!(value instanceof int[] slots)) {
                    return new int[0];
                }
                return slots.clone();
            } catch (IllegalAccessException | InvocationTargetException | LinkageError exception) {
                return new int[0];
            }
        }

        private @Nullable FastRecipeDefinition readRecipe(@Nullable Object rawRecipe) {
            if (rawRecipe == null) {
                return null;
            }

            Optional<FastRecipeAccessors> accessors = recipeAccessors.computeIfAbsent(
                    rawRecipe.getClass(), FastMachinesInputFillAdapter::findRecipeAccessors);
            if (accessors.isEmpty()) {
                return null;
            }

            try {
                Object rawInputs = accessors.get().inputs().invoke(rawRecipe);
                List<List<ItemStack>> ingredients = new ArrayList<>();
                for (Object rawChoice : objects(rawInputs)) {
                    List<ItemStack> choices = readChoices(rawChoice);
                    if (choices.isEmpty()) {
                        return null;
                    }
                    ingredients.add(choices);
                }

                List<ItemStack> outputs = SupremeGenericMachineAdapter.itemStacks(
                        accessors.get().outputs().invoke(rawRecipe));
                if (ingredients.isEmpty() || outputs.isEmpty()) {
                    return null;
                }
                return new FastRecipeDefinition(ingredients, outputs);
            } catch (IllegalAccessException | InvocationTargetException | LinkageError exception) {
                return null;
            }
        }

        private @Nonnull List<ItemStack> readChoices(@Nullable Object rawChoice) {
            if (rawChoice == null) {
                return List.of();
            }

            Optional<Method> accessor = choiceAccessors.computeIfAbsent(rawChoice.getClass(), type -> {
                Method method = findMethod(type, "getChoices");
                return Optional.ofNullable(method);
            });
            if (accessor.isEmpty()) {
                return List.of();
            }

            try {
                Object value = accessor.get().invoke(rawChoice);
                if (!(value instanceof Map<?, ?> rawChoices)) {
                    return List.of();
                }

                List<ItemStack> choices = new ArrayList<>(rawChoices.size());
                for (Map.Entry<?, ?> entry : rawChoices.entrySet()) {
                    Object wrapper = entry.getKey();
                    if (wrapper == null || !(entry.getValue() instanceof Number amount)) {
                        continue;
                    }
                    long requiredAmount = amount.longValue();
                    if (requiredAmount <= 0 || requiredAmount > Integer.MAX_VALUE) {
                        continue;
                    }

                    Optional<Method> baseItem = wrapperAccessors.computeIfAbsent(wrapper.getClass(), type -> {
                        Method method = findMethod(type, "getBaseItem");
                        return Optional.ofNullable(method);
                    });
                    if (baseItem.isEmpty()) {
                        continue;
                    }

                    Object item = baseItem.get().invoke(wrapper);
                    if (item instanceof ItemStack stack
                            && stack.getType() != Material.AIR
                            && stack.getAmount() > 0) {
                        ItemStack choice = stack.clone();
                        choice.setAmount((int) requiredAmount);
                        choices.add(choice);
                    }
                }
                return List.copyOf(choices);
            } catch (IllegalAccessException | InvocationTargetException | LinkageError exception) {
                return List.of();
            }
        }

        private static @Nullable FastRecipeDefinition findMatchingRecipe(
                @Nonnull List<FastRecipeDefinition> recipes, @Nonnull MachineRecipeDisplay display) {
            return findMatchingRecipe(
                    recipes,
                    display,
                    LegacyMachineInputFillManager::matchesRecipeInput,
                    LegacyMachineInputFillManager::canStackTogether);
        }

        private static @Nullable FastRecipeDefinition findMatchingRecipe(
                @Nonnull List<FastRecipeDefinition> recipes,
                @Nonnull MachineRecipeDisplay display,
                @Nonnull LegacyMachineInputFillManager.IngredientMatcher inputMatcher,
                @Nonnull LegacyMachineInputFillManager.StackMatcher outputMatcher) {
            List<List<ItemStack>> displayedInputs = display.getInputs().stream()
                    .map(ingredient -> ingredient.getChoices())
                    .toList();
            List<ItemStack> displayedOutputs = display.getOutputs();
            for (FastRecipeDefinition recipe : recipes) {
                if (matchesChoiceGroups(recipe.inputs(), displayedInputs, inputMatcher)
                        && matchesStacks(recipe.outputs(), displayedOutputs, outputMatcher)) {
                    return recipe;
                }
            }
            return null;
        }

        private static @Nullable List<ItemStack> resolveRequirements(
                @Nonnull FastRecipeDefinition authoritative,
                @Nonnull MachineRecipeDisplay display,
                @Nonnull int[] selectedAlternatives,
                @Nonnull LegacyMachineInputFillManager.IngredientMatcher matcher) {
            List<List<ItemStack>> displayed = display.getInputs().stream()
                    .map(ingredient -> ingredient.getChoices())
                    .toList();
            int[] displayToAuthoritative = matchChoiceGroupMapping(authoritative.inputs(), displayed, matcher);
            if (displayToAuthoritative == null) {
                return null;
            }

            List<ItemStack> requirements = new ArrayList<>(displayed.size());
            for (int displayIndex = 0; displayIndex < displayed.size(); displayIndex++) {
                List<ItemStack> choices = displayed.get(displayIndex);
                int selectedIndex = displayIndex < selectedAlternatives.length ? selectedAlternatives[displayIndex] : 0;
                ItemStack selected = choices.get(Math.floorMod(selectedIndex, choices.size()));
                List<ItemStack> authoritativeChoices = authoritative.inputs().get(displayToAuthoritative[displayIndex]);
                if (!anyChoiceMatches(authoritativeChoices, selected, matcher)) {
                    return null;
                }
                requirements.add(selected.clone());
            }
            return List.copyOf(requirements);
        }

        private static boolean matchesChoiceGroups(
                @Nonnull List<List<ItemStack>> authoritative,
                @Nonnull List<List<ItemStack>> displayed,
                @Nonnull LegacyMachineInputFillManager.IngredientMatcher matcher) {
            return matchChoiceGroupMapping(authoritative, displayed, matcher) != null;
        }

        private static @Nullable int[] matchChoiceGroupMapping(
                @Nonnull List<List<ItemStack>> authoritative,
                @Nonnull List<List<ItemStack>> displayed,
                @Nonnull LegacyMachineInputFillManager.IngredientMatcher matcher) {
            if (authoritative.size() != displayed.size()) {
                return null;
            }

            int[] displayToAuthoritative = new int[displayed.size()];
            Arrays.fill(displayToAuthoritative, -1);
            return mapChoiceGroup(
                            0,
                            authoritative,
                            displayed,
                            new boolean[displayed.size()],
                            displayToAuthoritative,
                            matcher)
                    ? displayToAuthoritative
                    : null;
        }

        private static boolean mapChoiceGroup(
                int authoritativeIndex,
                @Nonnull List<List<ItemStack>> authoritative,
                @Nonnull List<List<ItemStack>> displayed,
                @Nonnull boolean[] usedDisplayed,
                @Nonnull int[] displayToAuthoritative,
                @Nonnull LegacyMachineInputFillManager.IngredientMatcher matcher) {
            if (authoritativeIndex >= authoritative.size()) {
                return true;
            }

            List<ItemStack> expectedChoices = authoritative.get(authoritativeIndex);
            for (int displayIndex = 0; displayIndex < displayed.size(); displayIndex++) {
                if (usedDisplayed[displayIndex]
                        || !matchesChoiceSet(expectedChoices, displayed.get(displayIndex), matcher)) {
                    continue;
                }
                usedDisplayed[displayIndex] = true;
                displayToAuthoritative[displayIndex] = authoritativeIndex;
                if (mapChoiceGroup(
                        authoritativeIndex + 1,
                        authoritative,
                        displayed,
                        usedDisplayed,
                        displayToAuthoritative,
                        matcher)) {
                    return true;
                }
                displayToAuthoritative[displayIndex] = -1;
                usedDisplayed[displayIndex] = false;
            }
            return false;
        }

        private static boolean matchesChoiceSet(
                @Nonnull List<ItemStack> authoritative,
                @Nonnull List<ItemStack> displayed,
                @Nonnull LegacyMachineInputFillManager.IngredientMatcher matcher) {
            if (authoritative.size() != displayed.size()) {
                return false;
            }
            return matchChoice(0, authoritative, displayed, new boolean[displayed.size()], matcher);
        }

        private static boolean matchChoice(
                int authoritativeIndex,
                @Nonnull List<ItemStack> authoritative,
                @Nonnull List<ItemStack> displayed,
                @Nonnull boolean[] usedDisplayed,
                @Nonnull LegacyMachineInputFillManager.IngredientMatcher matcher) {
            if (authoritativeIndex >= authoritative.size()) {
                return true;
            }

            ItemStack expected = authoritative.get(authoritativeIndex);
            for (int displayIndex = 0; displayIndex < displayed.size(); displayIndex++) {
                ItemStack candidate = displayed.get(displayIndex);
                if (usedDisplayed[displayIndex]
                        || candidate.getAmount() != expected.getAmount()
                        || !matcher.matches(candidate, expected)) {
                    continue;
                }
                usedDisplayed[displayIndex] = true;
                if (matchChoice(authoritativeIndex + 1, authoritative, displayed, usedDisplayed, matcher)) {
                    return true;
                }
                usedDisplayed[displayIndex] = false;
            }
            return false;
        }

        private static boolean anyChoiceMatches(
                @Nonnull List<ItemStack> authoritative,
                @Nonnull ItemStack selected,
                @Nonnull LegacyMachineInputFillManager.IngredientMatcher matcher) {
            for (ItemStack expected : authoritative) {
                if (expected.getAmount() == selected.getAmount() && matcher.matches(selected, expected)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean matchesStacks(
                @Nonnull List<ItemStack> authoritative,
                @Nonnull List<ItemStack> displayed,
                @Nonnull LegacyMachineInputFillManager.StackMatcher matcher) {
            if (authoritative.size() != displayed.size()) {
                return false;
            }
            return matchStack(0, authoritative, displayed, new boolean[displayed.size()], matcher);
        }

        private static boolean matchStack(
                int authoritativeIndex,
                @Nonnull List<ItemStack> authoritative,
                @Nonnull List<ItemStack> displayed,
                @Nonnull boolean[] usedDisplayed,
                @Nonnull LegacyMachineInputFillManager.StackMatcher matcher) {
            if (authoritativeIndex >= authoritative.size()) {
                return true;
            }

            ItemStack expected = authoritative.get(authoritativeIndex);
            for (int displayIndex = 0; displayIndex < displayed.size(); displayIndex++) {
                ItemStack candidate = displayed.get(displayIndex);
                if (usedDisplayed[displayIndex]
                        || candidate.getAmount() != expected.getAmount()
                        || !matcher.matches(candidate, expected)) {
                    continue;
                }
                usedDisplayed[displayIndex] = true;
                if (matchStack(authoritativeIndex + 1, authoritative, displayed, usedDisplayed, matcher)) {
                    return true;
                }
                usedDisplayed[displayIndex] = false;
            }
            return false;
        }

        private static boolean isExpectedInputLayout(@Nonnull int[] slots) {
            if (slots.length != LAST_INPUT_SLOT - FIRST_INPUT_SLOT + 1) {
                return false;
            }
            boolean[] seen = new boolean[LAST_INPUT_SLOT + 1];
            for (int slot : slots) {
                if (slot < FIRST_INPUT_SLOT || slot > LAST_INPUT_SLOT || seen[slot]) {
                    return false;
                }
                seen[slot] = true;
            }
            return true;
        }

        private static @Nonnull int[] slotRange(int first, int last) {
            int[] slots = new int[last - first + 1];
            for (int index = 0; index < slots.length; index++) {
                slots[index] = first + index;
            }
            return slots;
        }

        private static @Nonnull Optional<FastMachineAccessors> findMachineAccessors(@Nonnull Class<?> type) {
            Method recipes = findMethod(type, "getRecipes");
            Method inputSlots = findMethod(type, "getInputSlots");
            return recipes == null || inputSlots == null
                    ? Optional.empty()
                    : Optional.of(new FastMachineAccessors(recipes, inputSlots));
        }

        private static @Nonnull Optional<FastRecipeAccessors> findRecipeAccessors(@Nonnull Class<?> type) {
            Method inputs = findMethod(type, "getInputs");
            Method outputs = findMethod(type, "getOutputs");
            return inputs == null || outputs == null
                    ? Optional.empty()
                    : Optional.of(new FastRecipeAccessors(inputs, outputs));
        }

        private static @Nullable Method findMethod(@Nonnull Class<?> type, @Nonnull String name) {
            try {
                Method method = type.getMethod(name);
                return method.getParameterCount() == 0 ? method : null;
            } catch (NoSuchMethodException | SecurityException exception) {
                return null;
            }
        }

        private static @Nonnull List<Object> objects(@Nullable Object value) {
            List<Object> objects = new ArrayList<>();
            if (value instanceof Collection<?> collection) {
                objects.addAll(collection);
            } else if (value instanceof Iterable<?> iterable) {
                for (Object element : iterable) {
                    objects.add(element);
                }
            } else if (value != null && value.getClass().isArray()) {
                int length = Array.getLength(value);
                for (int index = 0; index < length; index++) {
                    objects.add(Array.get(value, index));
                }
            }
            return objects;
        }

        record FastRecipeDefinition(
                @Nonnull List<List<ItemStack>> inputs, @Nonnull List<ItemStack> outputs) {

            FastRecipeDefinition {
                List<List<ItemStack>> inputCopies = new ArrayList<>(inputs.size());
                for (List<ItemStack> input : inputs) {
                    List<ItemStack> choiceCopies = new ArrayList<>(input.size());
                    for (ItemStack choice : input) {
                        choiceCopies.add(choice.clone());
                    }
                    inputCopies.add(List.copyOf(choiceCopies));
                }
                inputs = List.copyOf(inputCopies);

                List<ItemStack> outputCopies = new ArrayList<>(outputs.size());
                for (ItemStack output : outputs) {
                    outputCopies.add(output.clone());
                }
                outputs = List.copyOf(outputCopies);
            }
        }

        private record FastMachineAccessors(@Nonnull Method recipes, @Nonnull Method inputSlots) {}

        private record FastRecipeAccessors(@Nonnull Method inputs, @Nonnull Method outputs) {}
    }

    /** Public-surface compatibility adapter for Supreme's custom GenericMachine recipe list. */
    static final class SupremeGenericMachineAdapter extends BaseAdapter {

        private static final String SUPREME_PACKAGE_MARKER = ".supreme.";
        private static final String GENERIC_MACHINE_SIMPLE_NAME = "GenericMachine";
        private static final String[] INPUT_METHODS = {"getInputNotNull", "getInput", "getInputs"};
        private static final String[] OUTPUT_METHODS = {"getOutputNotNull", "getOutput", "getOutputs"};

        private final JavaPlugin plugin;
        private final Map<Class<?>, Optional<MachineAccessors>> machineAccessors = new ConcurrentHashMap<>();
        private final Map<Class<?>, Optional<RecipeAccessors>> recipeAccessors = new ConcurrentHashMap<>();

        SupremeGenericMachineAdapter(@Nonnull JavaPlugin plugin) {
            super(plugin, "enhanced_guide_supreme_generic_machine_input_fill");
            this.plugin = plugin;
        }

        @Override
        public @Nonnull String getDisplayName() {
            return "Supreme GenericMachine";
        }

        @Override
        public int getPriority() {
            return 1000;
        }

        @Override
        public boolean supports(@Nonnull SlimefunItem machine) {
            return machine instanceof AContainer && isSupremeGenericMachine(machine.getClass());
        }

        @Override
        public boolean supportsRecipe(@Nonnull SlimefunItem machine, @Nonnull MachineRecipeDisplay recipe) {
            if (!supports(machine)) {
                return false;
            }
            List<MachineRecipe> recipes = readAuthoritativeRecipes(machine);
            return LegacyMachineInputFillManager.hasCompatibleRegisteredRecipe(
                    recipes,
                    recipe,
                    LegacyMachineInputFillManager::matchesRecipeInput,
                    LegacyMachineInputFillManager::canStackTogether);
        }

        @Override
        public @Nullable MachineInputFillRecipe resolve(
                @Nonnull SlimefunItem machine,
                @Nonnull MachineRecipeDisplay recipe,
                @Nonnull int[] selectedAlternatives) {
            if (!(machine instanceof AContainer container) || !isSupremeGenericMachine(machine.getClass())) {
                return null;
            }

            return resolveFromObject(
                    machine, recipe, selectedAlternatives, container.getInputSlots(), container.getOutputSlots());
        }

        @Nullable MachineInputFillRecipe resolveFromObject(
                @Nonnull Object machine,
                @Nonnull MachineRecipeDisplay recipe,
                @Nonnull int[] selectedAlternatives,
                @Nonnull int[] inputSlots,
                @Nonnull int[] outputSlots) {
            return resolveFromObject(
                    machine,
                    recipe,
                    selectedAlternatives,
                    inputSlots,
                    outputSlots,
                    LegacyMachineInputFillManager::matchesRecipeInput,
                    LegacyMachineInputFillManager::canStackTogether);
        }

        @Nullable MachineInputFillRecipe resolveFromObject(
                @Nonnull Object machine,
                @Nonnull MachineRecipeDisplay recipe,
                @Nonnull int[] selectedAlternatives,
                @Nonnull int[] inputSlots,
                @Nonnull int[] outputSlots,
                @Nonnull LegacyMachineInputFillManager.IngredientMatcher inputMatcher,
                @Nonnull LegacyMachineInputFillManager.StackMatcher outputMatcher) {
            List<ItemStack> requirements = LegacyMachineInputFillManager.resolveRegisteredRequirements(
                    readAuthoritativeRecipes(machine),
                    recipe,
                    selectedAlternatives,
                    inputMatcher,
                    outputMatcher);
            if (requirements == null) {
                return null;
            }

            return MachineInputFillRecipe.builder()
                    .ingredients(requirements)
                    .inputSlots(inputSlots)
                    .protectedSlots(protectedSlots(machine, outputSlots))
                    .label("Supreme GenericMachine adapter")
                    .build();
        }

        @Nonnull List<MachineRecipe> readAuthoritativeRecipes(@Nonnull Object machine) {
            Optional<MachineAccessors> accessors = machineAccessors.computeIfAbsent(
                    machine.getClass(), SupremeGenericMachineAdapter::findMachineAccessors);
            if (accessors.isEmpty()) {
                return List.of();
            }

            try {
                Object rawRecipes = accessors.get().recipeField().get(machine);
                List<MachineRecipe> recipes = new ArrayList<>();
                for (Object rawRecipe : objects(rawRecipes)) {
                    MachineRecipe recipe = readRecipe(rawRecipe);
                    if (recipe != null) {
                        recipes.add(recipe);
                    }
                }
                return List.copyOf(recipes);
            } catch (IllegalAccessException | LinkageError exception) {
                plugin.getLogger().log(Level.FINE, "Could not read Supreme GenericMachine recipes", exception);
                return List.of();
            }
        }

        private @Nullable MachineRecipe readRecipe(@Nullable Object rawRecipe) {
            if (rawRecipe == null) {
                return null;
            }

            Optional<RecipeAccessors> accessors = recipeAccessors.computeIfAbsent(
                    rawRecipe.getClass(), SupremeGenericMachineAdapter::findRecipeAccessors);
            if (accessors.isEmpty()) {
                return null;
            }

            try {
                List<ItemStack> inputs = itemStacks(accessors.get().inputs().invoke(rawRecipe));
                List<ItemStack> outputs = itemStacks(accessors.get().outputs().invoke(rawRecipe));
                if (inputs.isEmpty() || outputs.isEmpty()) {
                    return null;
                }
                return new MachineRecipe(0, inputs.toArray(ItemStack[]::new), outputs.toArray(ItemStack[]::new));
            } catch (IllegalAccessException | InvocationTargetException | LinkageError exception) {
                return null;
            }
        }

        private @Nonnull int[] protectedSlots(@Nonnull Object machine, @Nonnull int[] outputSlots) {
            Set<Integer> protectedSlots = new LinkedHashSet<>();
            for (int slot : outputSlots) {
                protectedSlots.add(slot);
            }

            Optional<MachineAccessors> accessors = machineAccessors.computeIfAbsent(
                    machine.getClass(), SupremeGenericMachineAdapter::findMachineAccessors);
            if (accessors.isPresent() && accessors.get().statusSlot() != null) {
                try {
                    Object status = accessors.get().statusSlot().invoke(machine);
                    if (status instanceof Number number) {
                        protectedSlots.add(number.intValue());
                    }
                } catch (IllegalAccessException | InvocationTargetException | LinkageError ignored) {
                    // Output slots remain protected even when an optional status getter is unavailable at runtime.
                }
            }

            return protectedSlots.stream().mapToInt(Integer::intValue).toArray();
        }

        static boolean isSupremeGenericMachine(@Nonnull Class<?> type) {
            for (Class<?> current = type; current != null; current = current.getSuperclass()) {
                if (GENERIC_MACHINE_SIMPLE_NAME.equals(current.getSimpleName())
                        && current.getName().contains(SUPREME_PACKAGE_MARKER)) {
                    return true;
                }
            }
            return false;
        }

        private static @Nonnull Optional<MachineAccessors> findMachineAccessors(@Nonnull Class<?> type) {
            try {
                Field recipes = type.getField("machineRecipes");
                return Optional.of(new MachineAccessors(recipes, findMethod(type, "getStatusSlot")));
            } catch (NoSuchFieldException | SecurityException exception) {
                return Optional.empty();
            }
        }

        private static @Nonnull Optional<RecipeAccessors> findRecipeAccessors(@Nonnull Class<?> type) {
            Method inputs = findMethod(type, INPUT_METHODS);
            Method outputs = findMethod(type, OUTPUT_METHODS);
            return inputs == null || outputs == null
                    ? Optional.empty()
                    : Optional.of(new RecipeAccessors(inputs, outputs));
        }

        private static @Nullable Method findMethod(@Nonnull Class<?> type, @Nonnull String... names) {
            for (String name : names) {
                try {
                    Method method = type.getMethod(name);
                    if (method.getParameterCount() == 0) {
                        return method;
                    }
                } catch (NoSuchMethodException | SecurityException ignored) {
                    // Try the next public method name.
                }
            }
            return null;
        }

        static @Nonnull List<ItemStack> itemStacks(@Nullable Object value) {
            List<ItemStack> items = new ArrayList<>();
            collectItemStacks(value, items, 0);
            return List.copyOf(items);
        }

        private static void collectItemStacks(
                @Nullable Object value, @Nonnull List<ItemStack> items, int depth) {
            if (value == null || depth > 3) {
                return;
            }
            if (value instanceof ItemStack stack) {
                if (stack.getType() != Material.AIR && stack.getAmount() > 0) {
                    items.add(stack.clone());
                }
            } else if (value instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    collectItemStacks(entry.getKey(), items, depth + 1);
                    collectItemStacks(entry.getValue(), items, depth + 1);
                }
            } else if (value instanceof Iterable<?> iterable) {
                for (Object element : iterable) {
                    collectItemStacks(element, items, depth + 1);
                }
            } else if (value.getClass().isArray()) {
                int length = Array.getLength(value);
                for (int index = 0; index < length; index++) {
                    collectItemStacks(Array.get(value, index), items, depth + 1);
                }
            }
        }

        private static @Nonnull List<Object> objects(@Nullable Object value) {
            List<Object> objects = new ArrayList<>();
            if (value instanceof Collection<?> collection) {
                objects.addAll(collection);
            } else if (value instanceof Iterable<?> iterable) {
                for (Object element : iterable) {
                    objects.add(element);
                }
            } else if (value != null && value.getClass().isArray()) {
                int length = Array.getLength(value);
                for (int index = 0; index < length; index++) {
                    objects.add(Array.get(value, index));
                }
            }
            return objects;
        }

        private record MachineAccessors(@Nonnull Field recipeField, @Nullable Method statusSlot) {}

        private record RecipeAccessors(@Nonnull Method inputs, @Nonnull Method outputs) {}
    }
}
