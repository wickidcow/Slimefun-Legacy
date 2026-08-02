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
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** Registers the native and compatibility machine-input fill adapters. */
@SlimefunInternal
final class LegacyMachineInputFillAdapters {

    private LegacyMachineInputFillAdapters() {}

    static void registerDefaults(@Nonnull JavaPlugin plugin) {
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
