package io.github.thebusybiscuit.slimefun4.implementation.guide.enhanced;

import io.github.thebusybiscuit.slimefun4.api.annotations.SlimefunInternal;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.recipes.machine.MachineRecipeDisplay;
import io.github.thebusybiscuit.slimefun4.api.recipes.machine.MachineRecipeDisplayItem;
import io.github.thebusybiscuit.slimefun4.api.recipes.machine.MachineRecipeIngredient;
import io.github.thebusybiscuit.slimefun4.api.recipes.machine.MachineRecipeLayout;
import io.github.thebusybiscuit.slimefun4.api.recipes.machine.MachineRecipeProvider;
import io.github.thebusybiscuit.slimefun4.api.recipes.machine.MachineRecipeProviderRegistry;
import io.github.thebusybiscuit.slimefun4.core.attributes.RecipeDisplayItem;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
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
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Registers the native and compatibility providers used by the enhanced guide. */
@SlimefunInternal
public final class LegacyMachineRecipeProviders {

    private LegacyMachineRecipeProviders() {}

    public static void registerDefaults(@Nonnull JavaPlugin plugin) {
        MachineRecipeProviderRegistry.register(new DirectProvider(plugin));
        MachineRecipeProviderRegistry.register(new FastMachinesProvider(plugin));
        MachineRecipeProviderRegistry.register(new ContainerProvider(plugin));
        MachineRecipeProviderRegistry.register(new PublicMethodProvider(plugin));
        MachineRecipeProviderRegistry.register(new RecipeDisplayProvider(plugin));
    }

    private abstract static class BaseProvider implements MachineRecipeProvider {

        private final JavaPlugin plugin;
        private final NamespacedKey key;

        BaseProvider(@Nonnull JavaPlugin plugin, @Nonnull String key) {
            this.plugin = plugin;
            this.key = new NamespacedKey(plugin, key);
        }

        protected final @Nonnull JavaPlugin plugin() {
            return plugin;
        }

        @Override
        public final @Nonnull NamespacedKey getKey() {
            return key;
        }
    }

    private static final class DirectProvider extends BaseProvider {

        DirectProvider(JavaPlugin plugin) {
            super(plugin, "enhanced_guide_direct_machine_recipes");
        }

        @Override
        public int getPriority() {
            return 1000;
        }

        @Override
        public boolean supports(@Nonnull SlimefunItem item) {
            return item instanceof MachineRecipeDisplayItem;
        }

        @Override
        public @Nonnull List<MachineRecipeDisplay> getRecipes(
                @Nonnull SlimefunItem item, @Nonnull World world) {
            if (!(item instanceof MachineRecipeDisplayItem displayItem)) {
                return List.of();
            }

            List<MachineRecipeDisplay> recipes = displayItem.getMachineRecipeDisplays(world);
            if (recipes == null || recipes.isEmpty()) {
                return List.of();
            }

            List<MachineRecipeDisplay> sanitized = new ArrayList<>(recipes.size());
            for (MachineRecipeDisplay recipe : recipes) {
                if (recipe != null) {
                    sanitized.add(recipe);
                }
            }
            return List.copyOf(sanitized);
        }
    }

    private static final class ContainerProvider extends BaseProvider {

        ContainerProvider(JavaPlugin plugin) {
            super(plugin, "enhanced_guide_legacy_container_recipes");
        }

        @Override
        public int getPriority() {
            return 800;
        }

        @Override
        public boolean supports(@Nonnull SlimefunItem item) {
            return item instanceof AContainer;
        }

        @Override
        public @Nonnull List<MachineRecipeDisplay> getRecipes(
                @Nonnull SlimefunItem item, @Nonnull World world) {
            if (!(item instanceof AContainer container)) {
                return List.of();
            }

            List<MachineRecipeDisplay> displays = new ArrayList<>();
            for (MachineRecipe recipe : container.getMachineRecipes()) {
                MachineRecipeDisplay display = fromLegacyRecipe(recipe, container.getEnergyConsumption());
                if (display != null) {
                    displays.add(display);
                }
            }
            return List.copyOf(displays);
        }
    }

    private static final class RecipeDisplayProvider extends BaseProvider {

        RecipeDisplayProvider(JavaPlugin plugin) {
            super(plugin, "enhanced_guide_recipe_display_items");
        }

        @Override
        public int getPriority() {
            return 100;
        }

        @Override
        public boolean supports(@Nonnull SlimefunItem item) {
            return item instanceof RecipeDisplayItem;
        }

        @Override
        public @Nonnull List<MachineRecipeDisplay> getRecipes(
                @Nonnull SlimefunItem item, @Nonnull World world) {
            if (!(item instanceof RecipeDisplayItem displayItem)) {
                return List.of();
            }

            List<ItemStack> displayRecipes = displayItem.getDisplayRecipes();
            if (displayRecipes == null || displayRecipes.size() < 2) {
                return List.of();
            }

            List<MachineRecipeDisplay> recipes = new ArrayList<>(displayRecipes.size() / 2);
            for (int index = 0; index + 1 < displayRecipes.size(); index += 2) {
                ItemStack input = displayRecipes.get(index);
                ItemStack output = displayRecipes.get(index + 1);
                if (isEmpty(input) || isEmpty(output)) {
                    continue;
                }

                recipes.add(MachineRecipeDisplay.builder()
                        .addInput(input)
                        .addOutput(output)
                        .layout(MachineRecipeLayout.UNSPECIFIED)
                        .label("Guide display recipe")
                        .build());
            }
            return List.copyOf(recipes);
        }
    }

    /**
     * Compatibility adapter for addons that expose recipe data through public methods or fields.
     *
     * <p>This deliberately uses only the public Java surface. It supports common addon conventions such as
     * {@code getMachineRecipes()}, {@code getRecipeProcess()}, {@code getRecipes()}, {@code machineRecipes}, and
     * {@code recipes}. Private fields are never opened and {@code setAccessible(true)} is never used.
     */
    static final class PublicMethodProvider extends BaseProvider {

        private static final String[] MACHINE_METHOD_NAMES = {
            "getMachineRecipes",
            "getMachineRecipeList",
            "getRecipeProcess",
            "getProductionRecipes",
            "getRecipes",
            "getAllRecipes",
            "getAllRecipe",
            "getRecipesForGuide",
            "getRecipeShow",
            "getRecipeList"
        };
        private static final String[] MACHINE_FIELD_NAMES = {
            "machineRecipes",
            "machineRecipeList",
            "recipeProcess",
            "productionRecipes",
            "recipes",
            "allRecipes",
            "recipeShow",
            "recipeList",
            "recipeMap",
            "receitasParaProduzir"
        };
        private static final String[] INPUT_METHOD_NAMES = {
            "getInput",
            "getInputs",
            "getInputNotNull",
            "getFirstItemInput",
            "getInputItems",
            "getIngredients",
            "getKey",
            "getLeft",
            "getFirst"
        };
        private static final String[] OUTPUT_METHOD_NAMES = {
            "getOutput",
            "getOutputs",
            "getOutputNotNull",
            "getFirstItemOutput",
            "getResult",
            "getResults",
            "getOutputItems",
            "getValue",
            "getRight",
            "getSecond"
        };
        private static final String[] TICK_METHOD_NAMES = {
            "getTicks", "getProcessingTicks", "getProcessingTime", "getTimeProcess", "getTime"
        };
        private static final String[] MACHINE_TICK_METHOD_NAMES = {
            "getProcessingTicks", "getProcessingTime", "getTimeProcess"
        };

        private final Map<Class<?>, List<RecipeSourceAccessor>> machineSources = new ConcurrentHashMap<>();
        private final Map<Class<?>, Optional<SimpleRecipeMethods>> recipeMethods = new ConcurrentHashMap<>();

        PublicMethodProvider(JavaPlugin plugin) {
            super(plugin, "enhanced_guide_public_machine_recipe_methods");
        }

        @Override
        public int getPriority() {
            // Run before the plain AContainer provider so addon-owned recipes can be merged with the
            // standard container list instead of one source hiding the other.
            return 850;
        }

        @Override
        public boolean supports(@Nonnull SlimefunItem item) {
            return supportsObject(item);
        }

        boolean supportsObject(@Nonnull Object item) {
            return !findMachineSources(item.getClass()).isEmpty();
        }

        @Override
        public @Nonnull List<MachineRecipeDisplay> getRecipes(
                @Nonnull SlimefunItem item, @Nonnull World world) {
            return getRecipesFromObject(item, world);
        }

        @Nonnull
        List<MachineRecipeDisplay> getRecipesFromObject(@Nonnull Object item, @Nonnull World world) {
            List<RecipeSourceAccessor> sources = findMachineSources(item.getClass());
            if (sources.isEmpty()) {
                return List.of();
            }

            int fallbackTicks = readNonNegativeNumber(item, MACHINE_TICK_METHOD_NAMES);
            int energyPerTick = item instanceof AContainer container ? container.getEnergyConsumption() : -1;
            List<MachineRecipeDisplay> displays = new ArrayList<>();
            Set<Object> seenRecipes = Collections.newSetFromMap(new IdentityHashMap<>());

            // Some addons extend AContainer but keep additional recipes in their own public list.
            // Merge the standard list first, then inspect every addon-owned source. This prevents
            // either source from hiding valid recipes exposed by the other.
            if (item instanceof AContainer container) {
                for (MachineRecipe recipe : container.getMachineRecipes()) {
                    if (recipe == null || !seenRecipes.add(recipe)) {
                        continue;
                    }
                    MachineRecipeDisplay display = fromLegacyRecipe(recipe, energyPerTick);
                    if (display != null) {
                        displays.add(display);
                    }
                }
            }

            for (RecipeSourceAccessor source : sources) {
                try {
                    for (Object recipe : objects(source.read(item))) {
                        if (recipe == null || !seenRecipes.add(recipe)) {
                            continue;
                        }
                        MachineRecipeDisplay display = readSimpleRecipe(recipe, fallbackTicks, energyPerTick);
                        if (display != null) {
                            displays.add(display);
                        }
                    }
                } catch (IllegalAccessException | InvocationTargetException | LinkageError exception) {
                    plugin().getLogger()
                            .log(
                                    Level.FINE,
                                    "Could not read a public machine recipe source from "
                                            + item.getClass().getName(),
                                    exception);
                }
            }
            return List.copyOf(displays);
        }

        private @Nonnull List<RecipeSourceAccessor> findMachineSources(@Nonnull Class<?> type) {
            return machineSources.computeIfAbsent(type, ignored -> {
                // FastMachines has world filtering and alternative ingredients that its dedicated provider must retain.
                if (type.getName().startsWith(FastMachinesProvider.PACKAGE_PREFIX)) {
                    return List.of();
                }

                List<RecipeSourceAccessor> sources = new ArrayList<>();
                for (String name : MACHINE_METHOD_NAMES) {
                    try {
                        Method method = type.getMethod(name);
                        if (method.getParameterCount() != 0) {
                            continue;
                        }
                        // The AContainer provider already reads this inherited list. Continue looking for addon-owned
                        // sources, which is how Supreme and several older addons store their real recipes.
                        if ("getMachineRecipes".equals(name) && method.getDeclaringClass() == AContainer.class) {
                            continue;
                        }
                        sources.add(RecipeSourceAccessor.forMethod(method));
                    } catch (NoSuchMethodException | SecurityException ignoredException) {
                        // Try the next supported public method.
                    }
                }

                for (String name : MACHINE_FIELD_NAMES) {
                    try {
                        sources.add(RecipeSourceAccessor.forField(type.getField(name)));
                    } catch (NoSuchFieldException | SecurityException ignoredException) {
                        // Try the next supported public field.
                    }
                }
                return List.copyOf(sources);
            });
        }

        private @Nullable MachineRecipeDisplay readSimpleRecipe(
                @Nullable Object rawRecipe, int fallbackTicks, int energyPerTick) {
            if (rawRecipe == null) {
                return null;
            }

            if (rawRecipe instanceof Map.Entry<?, ?> entry) {
                List<ItemStack> inputs = itemStacks(entry.getKey());
                List<ItemStack> outputs = itemStacks(entry.getValue());
                if (!outputs.isEmpty()) {
                    MachineRecipeDisplay.Builder builder = MachineRecipeDisplay.builder()
                            .layout(MachineRecipeLayout.SHAPELESS)
                            .label("Addon mapped machine recipe");
                    inputs.forEach(builder::addInput);
                    outputs.forEach(builder::addOutput);
                    if (fallbackTicks >= 0) {
                        builder.processingTicks(fallbackTicks);
                    }
                    if (energyPerTick >= 0) {
                        builder.energyPerTick(energyPerTick);
                    }
                    return builder.build();
                }

                return readSimpleRecipe(entry.getValue(), fallbackTicks, energyPerTick);
            }

            Optional<SimpleRecipeMethods> methods = recipeMethods.computeIfAbsent(
                    rawRecipe.getClass(), PublicMethodProvider::findSimpleRecipeMethods);
            if (methods.isEmpty()) {
                return null;
            }

            try {
                SimpleRecipeMethods accessors = methods.get();
                List<ItemStack> inputs = invokeItems(rawRecipe, accessors.inputs(), accessors.numberedInputs());
                List<ItemStack> outputs = invokeItems(rawRecipe, accessors.outputs(), accessors.numberedOutputs());
                if (outputs.isEmpty()) {
                    return null;
                }

                String label = "Addon machine recipe";
                if (accessors.chance() != null) {
                    Object chance = accessors.chance().invoke(rawRecipe);
                    if (chance instanceof Number number) {
                        label += " (" + formatChance(number.doubleValue()) + ")";
                    }
                }

                MachineRecipeDisplay.Builder builder = MachineRecipeDisplay.builder()
                        .layout(MachineRecipeLayout.SHAPELESS)
                        .label(label);
                inputs.forEach(builder::addInput);
                outputs.forEach(builder::addOutput);

                int ticks = fallbackTicks;
                if (accessors.ticks() != null) {
                    Object value = accessors.ticks().invoke(rawRecipe);
                    if (value instanceof Number number && number.intValue() >= 0) {
                        ticks = number.intValue();
                    }
                }
                if (ticks >= 0) {
                    builder.processingTicks(ticks);
                }
                if (energyPerTick >= 0) {
                    builder.energyPerTick(energyPerTick);
                }
                return builder.build();
            } catch (IllegalAccessException | InvocationTargetException | LinkageError exception) {
                return null;
            }
        }

        private static @Nonnull List<ItemStack> invokeItems(
                @Nonnull Object recipe, @Nullable Method aggregate, @Nonnull List<Method> numbered)
                throws InvocationTargetException, IllegalAccessException {
            if (aggregate != null) {
                List<ItemStack> aggregateItems = itemStacks(aggregate.invoke(recipe));
                if (!aggregateItems.isEmpty()) {
                    return aggregateItems;
                }
            }

            List<ItemStack> items = new ArrayList<>(numbered.size());
            for (Method method : numbered) {
                items.addAll(itemStacks(method.invoke(recipe)));
            }
            return items;
        }

        private static @Nonnull Optional<SimpleRecipeMethods> findSimpleRecipeMethods(@Nonnull Class<?> type) {
            Method inputs = findMethod(type, INPUT_METHOD_NAMES);
            Method outputs = findMethod(type, OUTPUT_METHOD_NAMES);
            // Retain numbered fallbacks even when an aggregate getter exists. A few older addons
            // expose both but return null/empty from the aggregate method for special recipes.
            List<Method> numberedInputs = findNumberedMethods(type, "getInput", 9);
            List<Method> numberedOutputs = findNumberedMethods(type, "getOutput", 9);
            if (outputs == null && numberedOutputs.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new SimpleRecipeMethods(
                    inputs,
                    outputs,
                    numberedInputs,
                    numberedOutputs,
                    findMethod(type, TICK_METHOD_NAMES),
                    findMethod(type, "getChance")));
        }

        private static @Nonnull String formatChance(double rawChance) {
            double percent = rawChance > 0.0D && rawChance <= 1.0D ? rawChance * 100.0D : rawChance;
            if (percent == Math.rint(percent)) {
                return (long) percent + "% chance";
            }
            return String.format(java.util.Locale.ENGLISH, "%.2f%% chance", percent);
        }
    }

    private static final class FastMachinesProvider extends BaseProvider {

        private static final String PACKAGE_PREFIX = "net.guizhanss.fastmachines.";

        private final Map<Class<?>, Optional<Method>> machineMethods = new ConcurrentHashMap<>();
        private final Map<Class<?>, Optional<FastRecipeMethods>> recipeMethods = new ConcurrentHashMap<>();
        private final Map<Class<?>, Optional<Method>> choiceMethods = new ConcurrentHashMap<>();
        private final Map<Class<?>, Optional<Method>> wrapperMethods = new ConcurrentHashMap<>();

        FastMachinesProvider(JavaPlugin plugin) {
            super(plugin, "enhanced_guide_fastmachines_recipes");
        }

        @Override
        public int getPriority() {
            return 900;
        }

        @Override
        public boolean supports(@Nonnull SlimefunItem item) {
            return item.getClass().getName().startsWith(PACKAGE_PREFIX)
                    && findMachineMethod(item.getClass()).isPresent();
        }

        @Override
        public @Nonnull List<MachineRecipeDisplay> getRecipes(
                @Nonnull SlimefunItem item, @Nonnull World world) {
            Optional<Method> method = findMachineMethod(item.getClass());
            if (method.isEmpty()) {
                return List.of();
            }

            try {
                Object value = method.get().invoke(item);
                if (!(value instanceof Collection<?> rawRecipes)) {
                    return List.of();
                }

                List<MachineRecipeDisplay> displays = new ArrayList<>(rawRecipes.size());
                for (Object rawRecipe : rawRecipes) {
                    MachineRecipeDisplay display = readRecipe(rawRecipe, world);
                    if (display != null) {
                        displays.add(display);
                    }
                }
                return List.copyOf(displays);
            } catch (IllegalAccessException | InvocationTargetException | LinkageError exception) {
                plugin().getLogger()
                        .log(Level.WARNING, "Could not load FastMachines recipes for " + item.getId(), exception);
                return List.of();
            }
        }

        private @Nonnull Optional<Method> findMachineMethod(@Nonnull Class<?> type) {
            return machineMethods.computeIfAbsent(type, ignored -> {
                try {
                    return Optional.of(type.getMethod("getRecipes"));
                } catch (NoSuchMethodException | SecurityException exception) {
                    return Optional.empty();
                }
            });
        }

        private @Nullable MachineRecipeDisplay readRecipe(@Nullable Object rawRecipe, @Nonnull World world) {
            if (rawRecipe == null) {
                return null;
            }

            Optional<FastRecipeMethods> methods = recipeMethods.computeIfAbsent(
                    rawRecipe.getClass(), FastMachinesProvider::findRecipeMethods);
            if (methods.isEmpty()) {
                return null;
            }

            try {
                FastRecipeMethods recipeMethods = methods.get();
                if (recipeMethods.disabledIn() != null) {
                    Object disabled = recipeMethods.disabledIn().invoke(rawRecipe, world);
                    if (disabled instanceof Boolean value && value) {
                        return null;
                    }
                }

                Object inputValue = recipeMethods.inputs().invoke(rawRecipe);
                Object outputValue = recipeMethods.outputs().invoke(rawRecipe);
                if (!(inputValue instanceof Collection<?> rawInputs)) {
                    return null;
                }

                List<ItemStack> outputs = itemStacks(outputValue);
                if (outputs.isEmpty()) {
                    return null;
                }

                MachineRecipeDisplay.Builder builder = MachineRecipeDisplay.builder()
                        .layout(MachineRecipeLayout.SHAPELESS)
                        .label("FastMachines recipe");
                for (Object rawChoice : rawInputs) {
                    MachineRecipeIngredient ingredient = readIngredient(rawChoice);
                    if (ingredient != null) {
                        builder.addIngredient(ingredient);
                    }
                }
                outputs.forEach(builder::addOutput);
                return builder.build();
            } catch (IllegalAccessException | InvocationTargetException | LinkageError exception) {
                return null;
            }
        }

        private @Nullable MachineRecipeIngredient readIngredient(@Nullable Object rawChoice) {
            if (rawChoice == null) {
                return null;
            }

            Optional<Method> choiceMethod = choiceMethods.computeIfAbsent(rawChoice.getClass(), type -> {
                try {
                    return Optional.of(type.getMethod("getChoices"));
                } catch (NoSuchMethodException | SecurityException exception) {
                    return Optional.empty();
                }
            });
            if (choiceMethod.isEmpty()) {
                return null;
            }

            try {
                Object value = choiceMethod.get().invoke(rawChoice);
                if (!(value instanceof Map<?, ?> rawChoices)) {
                    return null;
                }

                List<ItemStack> choices = new ArrayList<>(rawChoices.size());
                for (Map.Entry<?, ?> entry : rawChoices.entrySet()) {
                    Object wrapper = entry.getKey();
                    if (wrapper == null || !(entry.getValue() instanceof Number amount)) {
                        continue;
                    }

                    Optional<Method> baseItemMethod = wrapperMethods.computeIfAbsent(wrapper.getClass(), type -> {
                        try {
                            return Optional.of(type.getMethod("getBaseItem"));
                        } catch (NoSuchMethodException | SecurityException exception) {
                            return Optional.empty();
                        }
                    });
                    if (baseItemMethod.isEmpty()) {
                        continue;
                    }

                    Object baseItem = baseItemMethod.get().invoke(wrapper);
                    if (baseItem instanceof ItemStack stack && !isEmpty(stack)) {
                        ItemStack choice = stack.clone();
                        choice.setAmount(Math.max(1, amount.intValue()));
                        choices.add(choice);
                    }
                }
                return choices.isEmpty() ? null : new MachineRecipeIngredient(choices);
            } catch (IllegalAccessException | InvocationTargetException | LinkageError exception) {
                return null;
            }
        }

        private static @Nonnull Optional<FastRecipeMethods> findRecipeMethods(@Nonnull Class<?> type) {
            Method inputs = findMethod(type, "getInputs");
            Method outputs = findMethod(type, "getOutputs");
            if (inputs == null || outputs == null) {
                return Optional.empty();
            }
            return Optional.of(new FastRecipeMethods(inputs, outputs, findMethod(type, "isDisabledIn", World.class)));
        }
    }

    private static @Nullable MachineRecipeDisplay fromLegacyRecipe(
            @Nullable MachineRecipe recipe, int energyPerTick) {
        if (recipe == null) {
            return null;
        }

        MachineRecipeDisplay.Builder builder = MachineRecipeDisplay.builder()
                .layout(MachineRecipeLayout.SHAPELESS)
                .label("Slimefun machine recipe");
        for (ItemStack input : recipe.getInput()) {
            if (!isEmpty(input)) {
                builder.addInput(input);
            }
        }

        int outputs = 0;
        for (ItemStack output : recipe.getOutput()) {
            if (!isEmpty(output)) {
                builder.addOutput(output);
                outputs++;
            }
        }
        if (outputs == 0) {
            return null;
        }

        if (recipe.getTicks() >= 0) {
            builder.processingTicks(recipe.getTicks());
        }
        if (energyPerTick >= 0) {
            builder.energyPerTick(energyPerTick);
        }
        return builder.build();
    }

    private static @Nonnull List<ItemStack> itemStacks(@Nullable Object value) {
        List<ItemStack> items = new ArrayList<>();
        collectItemStacks(value, items, 0);
        return items;
    }

    private static void collectItemStacks(
            @Nullable Object value, @Nonnull List<ItemStack> items, int depth) {
        if (value == null || depth > 4) {
            return;
        }
        if (value instanceof ItemStack item) {
            if (!isEmpty(item)) {
                items.add(item.clone());
            }
            return;
        }
        if (value instanceof Optional<?> optional) {
            optional.ifPresent(element -> collectItemStacks(element, items, depth + 1));
            return;
        }
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                int before = items.size();
                collectItemStacks(entry.getKey(), items, depth + 1);
                if (items.size() > before && entry.getValue() instanceof Number amount) {
                    ItemStack item = items.get(items.size() - 1);
                    item.setAmount(Math.max(1, amount.intValue()));
                } else if (items.size() == before) {
                    collectItemStacks(entry.getValue(), items, depth + 1);
                }
            }
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                collectItemStacks(element, items, depth + 1);
            }
            return;
        }
        Class<?> type = value.getClass();
        if (type.isArray()) {
            int length = Array.getLength(value);
            for (int index = 0; index < length; index++) {
                collectItemStacks(Array.get(value, index), items, depth + 1);
            }
            return;
        }

        Method itemGetter = findMethod(type, "getItemStack", "getBaseItem", "getItem");
        if (itemGetter != null && itemGetter.getReturnType() != Void.TYPE) {
            try {
                Object nested = itemGetter.invoke(value);
                if (nested != value) {
                    collectItemStacks(nested, items, depth + 1);
                }
            } catch (IllegalAccessException | InvocationTargetException | LinkageError ignored) {
                // Unsupported public wrapper type; leave it out of the normalized recipe.
            }
        }
    }

    private static @Nonnull List<Object> objects(@Nullable Object value) {
        List<Object> values = new ArrayList<>();
        collectObjects(value, values, 0);
        return values;
    }

    private static void collectObjects(@Nullable Object value, @Nonnull List<Object> values, int depth) {
        if (value == null || depth > 3) {
            return;
        }
        if (value instanceof Optional<?> optional) {
            optional.ifPresent(element -> collectObjects(element, values, depth + 1));
        } else if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                values.add(entry);
            }
        } else if (value instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                collectObjects(element, values, depth + 1);
            }
        } else if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int index = 0; index < length; index++) {
                collectObjects(Array.get(value, index), values, depth + 1);
            }
        } else {
            values.add(value);
        }
    }

    private static int readNonNegativeNumber(@Nonnull Object target, @Nonnull String... methodNames) {
        Method method = findMethod(target.getClass(), methodNames);
        if (method == null) {
            return -1;
        }
        try {
            Object value = method.invoke(target);
            return value instanceof Number number && number.intValue() >= 0 ? number.intValue() : -1;
        } catch (IllegalAccessException | InvocationTargetException | LinkageError exception) {
            return -1;
        }
    }

    private static @Nonnull List<Method> findNumberedMethods(
            @Nonnull Class<?> type, @Nonnull String prefix, int maximum) {
        List<Method> methods = new ArrayList<>();
        for (int index = 1; index <= maximum; index++) {
            Method method = findMethod(type, prefix + index);
            if (method != null) {
                methods.add(method);
            }
        }
        return List.copyOf(methods);
    }

    private static @Nullable Method findMethod(@Nonnull Class<?> type, @Nonnull String... names) {
        for (String name : names) {
            try {
                Method method = type.getMethod(name);
                if (method.getParameterCount() == 0) {
                    return method;
                }
            } catch (NoSuchMethodException | SecurityException ignored) {
                // Try the next supported getter name.
            }
        }
        return null;
    }

    private static @Nullable Method findMethod(
            @Nonnull Class<?> type, @Nonnull String name, @Nonnull Class<?> parameter) {
        try {
            return type.getMethod(name, parameter);
        } catch (NoSuchMethodException | SecurityException exception) {
            return null;
        }
    }

    private static boolean isEmpty(@Nullable ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }

    private record RecipeSourceAccessor(@Nullable Method method, @Nullable Field field) {

        static @Nonnull RecipeSourceAccessor forMethod(@Nonnull Method method) {
            return new RecipeSourceAccessor(method, null);
        }

        static @Nonnull RecipeSourceAccessor forField(@Nonnull Field field) {
            return new RecipeSourceAccessor(null, field);
        }

        @Nullable Object read(@Nonnull Object target)
                throws InvocationTargetException, IllegalAccessException {
            return method != null ? method.invoke(target) : field.get(target);
        }
    }

    private record SimpleRecipeMethods(
            @Nullable Method inputs,
            @Nullable Method outputs,
            @Nonnull List<Method> numberedInputs,
            @Nonnull List<Method> numberedOutputs,
            @Nullable Method ticks,
            @Nullable Method chance) {}

    private record FastRecipeMethods(Method inputs, Method outputs, Method disabledIn) {}
}
