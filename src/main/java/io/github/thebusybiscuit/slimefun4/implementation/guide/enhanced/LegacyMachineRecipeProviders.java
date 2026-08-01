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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
     * Compatibility adapter for addons that expose a public getMachineRecipes() method with recipe objects using
     * getInput()/getOutput() or getInputs()/getOutputs().
     */
    private static final class PublicMethodProvider extends BaseProvider {

        private final Map<Class<?>, Optional<Method>> machineMethods = new ConcurrentHashMap<>();
        private final Map<Class<?>, Optional<SimpleRecipeMethods>> recipeMethods = new ConcurrentHashMap<>();

        PublicMethodProvider(JavaPlugin plugin) {
            super(plugin, "enhanced_guide_public_machine_recipe_methods");
        }

        @Override
        public int getPriority() {
            return 700;
        }

        @Override
        public boolean supports(@Nonnull SlimefunItem item) {
            return findMachineMethod(item.getClass()).isPresent();
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
                if (!(value instanceof Collection<?> recipes)) {
                    return List.of();
                }

                List<MachineRecipeDisplay> displays = new ArrayList<>(recipes.size());
                for (Object recipe : recipes) {
                    MachineRecipeDisplay display = readSimpleRecipe(recipe);
                    if (display != null) {
                        displays.add(display);
                    }
                }
                return List.copyOf(displays);
            } catch (IllegalAccessException | InvocationTargetException | LinkageError exception) {
                plugin().getLogger()
                        .log(
                                Level.FINE,
                                "Could not read public machine recipes from " + item.getClass().getName(),
                                exception);
                return List.of();
            }
        }

        private @Nonnull Optional<Method> findMachineMethod(@Nonnull Class<?> type) {
            return machineMethods.computeIfAbsent(type, ignored -> {
                if (AContainer.class.isAssignableFrom(type)) {
                    return Optional.empty();
                }
                try {
                    return Optional.of(type.getMethod("getMachineRecipes"));
                } catch (NoSuchMethodException | SecurityException exception) {
                    return Optional.empty();
                }
            });
        }

        private @Nullable MachineRecipeDisplay readSimpleRecipe(@Nullable Object rawRecipe) {
            if (rawRecipe == null) {
                return null;
            }

            Optional<SimpleRecipeMethods> methods = recipeMethods.computeIfAbsent(
                    rawRecipe.getClass(), PublicMethodProvider::findSimpleRecipeMethods);
            if (methods.isEmpty()) {
                return null;
            }

            try {
                SimpleRecipeMethods recipeMethods = methods.get();
                List<ItemStack> inputs = itemStacks(recipeMethods.inputs().invoke(rawRecipe));
                List<ItemStack> outputs = itemStacks(recipeMethods.outputs().invoke(rawRecipe));
                if (outputs.isEmpty()) {
                    return null;
                }

                MachineRecipeDisplay.Builder builder = MachineRecipeDisplay.builder()
                        .layout(MachineRecipeLayout.SHAPELESS)
                        .label("Addon machine recipe");
                inputs.forEach(builder::addInput);
                outputs.forEach(builder::addOutput);

                if (recipeMethods.ticks() != null) {
                    Object ticks = recipeMethods.ticks().invoke(rawRecipe);
                    if (ticks instanceof Number number && number.intValue() >= 0) {
                        builder.processingTicks(number.intValue());
                    }
                }
                return builder.build();
            } catch (IllegalAccessException | InvocationTargetException | LinkageError exception) {
                return null;
            }
        }

        private static @Nonnull Optional<SimpleRecipeMethods> findSimpleRecipeMethods(@Nonnull Class<?> type) {
            Method inputs = findMethod(type, "getInput", "getInputs");
            Method outputs = findMethod(type, "getOutput", "getOutputs");
            if (inputs == null || outputs == null) {
                return Optional.empty();
            }
            return Optional.of(new SimpleRecipeMethods(inputs, outputs, findMethod(type, "getTicks")));
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
        if (value instanceof ItemStack[] array) {
            for (ItemStack item : array) {
                if (!isEmpty(item)) {
                    items.add(item.clone());
                }
            }
        } else if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item instanceof ItemStack stack && !isEmpty(stack)) {
                    items.add(stack.clone());
                }
            }
        } else if (value instanceof ItemStack item && !isEmpty(item)) {
            items.add(item.clone());
        }
        return items;
    }

    private static @Nullable Method findMethod(@Nonnull Class<?> type, @Nonnull String... names) {
        for (String name : names) {
            try {
                return type.getMethod(name);
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

    private record SimpleRecipeMethods(Method inputs, Method outputs, Method ticks) {}

    private record FastRecipeMethods(Method inputs, Method outputs, Method disabledIn) {}
}
