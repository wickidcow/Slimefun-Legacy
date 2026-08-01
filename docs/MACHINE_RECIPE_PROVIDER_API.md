# Machine Recipe Provider API

Slimefun Legacy Phase 4 adds a normalized addon-facing API for displaying machine recipes in the native Enhanced Guide.

The browser no longer needs a hard-coded implementation for every addon. Addons can either:

1. Implement `MachineRecipeDisplayItem` directly on a machine item, or
2. Register a separate `MachineRecipeProvider` for existing machine classes.

The API is display-only. It does not start machines, move items, consume energy, or bypass addon logic.

## Direct item integration

```java
public final class ExampleMachine extends SlimefunItem implements MachineRecipeDisplayItem {

    @Override
    public List<MachineRecipeDisplay> getMachineRecipeDisplays(World world) {
        return List.of(
            MachineRecipeDisplay.builder()
                .addInput(new ItemStack(Material.IRON_INGOT, 2))
                .addOutput(new ItemStack(Material.GOLD_INGOT))
                .layout(MachineRecipeLayout.SHAPELESS)
                .processingTicks(20)
                .energyPerTick(16)
                .label("Example conversion")
                .build()
        );
    }
}
```

This route is best for new addons because the item owns its own recipe description.

## Provider registration

A compatibility addon can expose recipes for machine classes it does not control:

```java
MachineRecipeProviderRegistry.register(new MachineRecipeProvider() {
    private final NamespacedKey key = new NamespacedKey(plugin, "example_machine_recipes");

    @Override
    public NamespacedKey getKey() {
        return key;
    }

    @Override
    public int getPriority() {
        return 500;
    }

    @Override
    public boolean supports(SlimefunItem item) {
        return item instanceof ExampleMachine;
    }

    @Override
    public List<MachineRecipeDisplay> getRecipes(SlimefunItem item, World world) {
        ExampleMachine machine = (ExampleMachine) item;
        return machine.getRecipes().stream()
            .map(recipe -> MachineRecipeDisplay.builder()
                .addInput(recipe.input())
                .addOutput(recipe.output())
                .layout(MachineRecipeLayout.SHAPELESS)
                .build())
            .toList();
    }
});
```

Providers are ordered by priority. The Enhanced Guide uses the first provider that supports the item and returns at least one valid recipe.

Use a namespaced key owned by your addon. Registering another provider with the same key replaces the old provider, which is useful during addon reload or controlled compatibility upgrades.

## Built-in compatibility providers

Slimefun Legacy registers providers for:

- Items implementing `MachineRecipeDisplayItem`
- Core and addon classes extending `AContainer`
- Addons exposing public recipe methods such as `getMachineRecipes()`, `getRecipeProcess()`, `getRecipes()`, `getRecipeShow()` or `getRecipeList()`
- Addons exposing public recipe-list fields such as `machineRecipes`, `recipeProcess`, `recipes` or `recipeShow`
- Recipe objects using aggregate input/output getters or numbered getters such as `getInput1()` and `getInput2()`
- Existing `RecipeDisplayItem` implementations
- FastMachines through its public recipe, choice, output, and world-filter getters

The compatibility providers only use public methods and public fields. They do not access private members, call `getDeclaredField`, or call `setAccessible`. Supreme-style machines are supported because their maintained classes expose processing lists through public fields or getters even when the inherited `AContainer` list is empty.

When an `AContainer` subclass also exposes addon-owned recipes, Legacy merges the normal container list with every recognized public recipe source. Empty aggregate getters do not hide populated public fields, and numbered getters remain a fallback when an aggregate input/output getter returns no usable items.

## Recipe model

`MachineRecipeDisplay` supports:

- Any number of ingredients
- Alternative choices for each ingredient
- Multiple outputs
- Shaped, shapeless, or provider-defined layouts
- Optional processing ticks
- Optional energy use per tick
- Optional labels

All `ItemStack` values are defensively copied when stored and when returned. Guide rendering cannot mutate the addon's original recipe objects.

## Removing a provider

```java
MachineRecipeProviderRegistry.unregister(key);
```

This should be called when a compatibility plugin is disabled if the server supports plugin unloading. A normal full server shutdown clears the registry with the plugin classloader.

## Compatibility policy

The provider API is marked `@SlimefunAPI` and is included in Legacy's compatibility-protected package inventory. Future maintenance should preserve source and binary compatibility whenever practical.
