# Slimefun Legacy Native Enhanced Guide — Phase 3.1

## FastMachines machine-recipe browser

Phase 3.1 corrects a player-facing difference between JustEnoughGuide and the native Slimefun Legacy Enhanced Guide.

Previously, opening a FastMachines machine in the guide only showed the recipe used to craft the machine itself. The guide did not show the recipes that the machine can process.

Phase 3.1 adds a protected **Machine Recipes** button to supported FastMachines item pages.

## Player controls

- Open a FastMachines machine in the Slimefun Legacy guide.
- Click **Machine Recipes** in the lower-right area of the item page.
- Browse up to 36 machine recipes per page.
- Left-click a recipe output to open its full machine recipe.
- Right-click a recipe output to open that output item's own Slimefun or vanilla recipe.
- In a detailed machine recipe, left-click or right-click ingredients with multiple valid choices to cycle through alternatives.
- Use the previous and next buttons to move through recipes.

## Supported recipe information

The browser displays:

- Up to 36 shapeless machine inputs.
- The required amount for each ingredient.
- Multiple accepted alternatives for a recipe ingredient.
- Up to five recipe outputs.
- Recipes filtered for the player's current world.
- The machine ID and total enabled recipe count.

## Compatibility design

FastMachines remains optional. Slimefun Legacy does not import or link against FastMachines classes at compile time.

The adapter:

- Detects FastMachines items by their public package and `getRecipes()` method.
- Reads only public Kotlin-generated getters.
- Does not access private fields.
- Does not call `setAccessible(true)`.
- Does not replace Slimefun's private guide registry.
- Does not require a FastMachines source change or rebuild.
- Keeps the existing Phase 2 and Phase 3 recipe-fill features unchanged.

## Configuration

```yaml
features:
  machine-recipes:
    enabled: true
```

Existing `enhanced-guide.yml` files remain compatible. When the option is absent, machine recipe browsing defaults to enabled.

## Current scope

Phase 3.1 provides native machine recipe browsing for the current `net.guizhanss.fastmachines` implementation used by the Albion-maintained FastMachines fork.

It does not yet provide a general registration API for every addon machine. Other addons can be added through dedicated safe adapters or a future public machine-recipe provider API.
