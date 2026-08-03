# Machine Input-Fill Adapter API

Slimefun Legacy 4.1.14 introduced an addon-facing API for safely connecting custom machine recipe systems to the Enhanced Guide's **Fill Machine Inputs** action.

A recipe provider controls what the player sees. An input-fill adapter separately proves that a displayed recipe corresponds to a real machine recipe and declares where its ingredients may be inserted.

## Registering an adapter

```java
public final class ExampleInputFillAdapter implements MachineInputFillAdapter {

    private final NamespacedKey key;

    public ExampleInputFillAdapter(JavaPlugin plugin) {
        key = new NamespacedKey(plugin, "example_machine_input_fill");
    }

    @Override
    public NamespacedKey getKey() {
        return key;
    }

    @Override
    public String getDisplayName() {
        return "Example machine";
    }

    @Override
    public int getPriority() {
        return 500;
    }

    @Override
    public boolean supports(SlimefunItem machine) {
        return machine instanceof ExampleMachine;
    }

    @Override
    public boolean supportsRecipe(SlimefunItem machine, MachineRecipeDisplay display) {
        ExampleMachine example = (ExampleMachine) machine;
        return example.hasAuthoritativeRecipe(display);
    }

    @Override
    public MachineInputFillRecipe resolve(
            SlimefunItem machine,
            MachineRecipeDisplay display,
            int[] selectedAlternatives) {
        ExampleMachine example = (ExampleMachine) machine;
        ExampleRecipe recipe = example.resolveAuthoritativeRecipe(display, selectedAlternatives);
        if (recipe == null) {
            return null;
        }

        return MachineInputFillRecipe.builder()
                .ingredients(recipe.inputs())
                .inputSlots(example.getInputSlots())
                .protectedSlots(example.getOutputSlots())
                .maximumSets(32)
                .label("Example machine adapter")
                .build();
    }
}
```

Register it during addon startup after Slimefun is available:

```java
MachineInputFillAdapterRegistry.register(new ExampleInputFillAdapter(this));
```

Remove it during controlled plugin unloading:

```java
MachineInputFillAdapterRegistry.unregister(adapter.getKey());
```

A normal full shutdown clears the registry with the server and plugin classloaders.

## Adapter responsibilities

An adapter must:

1. Use a unique namespaced key owned by its addon.
2. Support only machine classes it understands.
3. Confirm that a displayed recipe maps to an authoritative processing recipe.
4. Revalidate the player's selected ingredient alternatives in `resolve`.
5. Return the real ingredient amounts used by the machine.
6. Declare only writable input slots.
7. Declare outputs, progress indicators, controls, upgrades, and other forbidden slots as protected.
8. Return `null` rather than guessing when a recipe cannot be resolved safely.

## Legacy-owned safeguards

Adapters do not move items themselves. Slimefun Legacy always performs:

- Exact placed-machine validation by Slimefun item ID by default.
- Protection-plugin checks.
- Paper/Folia region ownership checks.
- Menu lock and viewer checks.
- Input and protected slot range, uniqueness, and overlap validation.
- Full player/machine inventory simulation.
- Slimefun recipe-input and stack-merge matching.
- Commit verification and rollback of both inventories after an unexpected failure.

## Optional hooks

### Display name

`getDisplayName()` supplies the short player-facing name shown in the fill-button lore. The default is the key portion of the adapter namespaced key.

### Priority

Higher-priority adapters are checked first. The default priority is `0`. Built-in compatibility adapters use higher priorities than the standard fallback when they must override an inherited `AContainer` recipe model.

### Target validation

The default `isValidTarget` implementation requires the guide machine and placed machine to have the same Slimefun item ID. Override it only when multiple IDs intentionally share the same physical machine contract.

### Final safety check

`isSafeToFill` receives the player, placed Slimefun item, target block, and `BlockMenu`. It may reject busy modes, disabled states, upgrade layouts, or other machine-specific conditions. It must not mutate either inventory.

### Maximum filling

`MachineInputFillRecipe` may disable shift-click maximum filling or clamp it below the server-wide limit:

```java
.allowMaximumFill(false)
```

or:

```java
.maximumSets(8)
```

## Invalid adapter data

Legacy blocks transfers containing:

- Missing, AIR, or zero-amount ingredients.
- Empty, duplicate, negative, or out-of-range input slots.
- Duplicate, negative, or out-of-range protected slots.
- Any overlap between input and protected slots.
- More distinct ingredients than available input slots.

Unsupported or rejected recipes remain visible in the machine recipe browser but do not show a fill button.

## Compatibility policy

The adapter API is marked `@SlimefunAPI` and resides in the compatibility-protected `api.recipes.machine` package. Legacy maintenance should retain source and binary compatibility whenever practical.

## Built-in compatibility adapters

Slimefun Legacy currently includes fail-closed adapters for:

- Standard core and addon `AContainer` implementations whose runtime recipes are registered through `getMachineRecipes()`.
- Supreme `GenericMachine` implementations using their public custom recipe collection and status-slot contract.
- The maintained FastMachines 54-slot inventory using public recipe/choice getters, ingredient slots 0–35 and protected GUI slots 36–53.

The FastMachines adapter intentionally stops offering input filling if its verified slot layout changes. Recipe browsing can continue while the new addon layout is reviewed.

