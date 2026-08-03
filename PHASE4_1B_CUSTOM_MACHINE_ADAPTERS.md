# Phase 4.1B-B — Custom Machine Input-Fill Adapters

Slimefun Legacy 4.1.14 introduced **Fill Machine Inputs** to custom addon machines without weakening the transaction safeguards introduced in Phases 4.1A and 4.1B-A.

## Delivered

- Public `MachineInputFillAdapter`, `MachineInputFillRecipe`, and `MachineInputFillAdapterRegistry` APIs.
- Priority-ordered adapter selection by stable namespaced key.
- Standard `AContainer` support moved behind the same adapter contract.
- Built-in Supreme `GenericMachine` compatibility adapter.
- Built-in FastMachines compatibility adapter added in Slimefun Legacy 4.1.15.
- Authoritative recipe matching against the selected guide alternatives and outputs.
- Explicit writable input slots and protected output/control/status slots.
- Per-adapter maximum-fill limits and safety hooks.
- Existing exact-target, protection, region-thread, menu-viewer, simulation, commit-validation, and rollback safeguards retained.

## Supreme compatibility

Supreme `GenericMachine` classes keep their processing recipes in a public `machineRecipes` collection rather than the inherited `AContainer#getMachineRecipes()` list. The built-in adapter reads only that public contract and the recipe object's public input/output getters. It does not use `setAccessible`, private fields, or hard dependencies on Supreme classes.

The adapter:

- Requires a Supreme-package `GenericMachine` class that still extends `AContainer`.
- Matches the guide recipe to an actual custom processing recipe.
- Uses the custom recipe's ingredient amounts as the transfer authority.
- Uses the machine's declared input and output slots.
- Adds its public status slot to the protected-slot set.
- Leaves unmatched or incompatible recipes browse-only.

## FastMachines compatibility

Slimefun Legacy 4.1.15 connects the existing FastMachines recipe provider to a separate authoritative input-fill adapter. It reads the addon's public Kotlin/JVM getters without linking against FastMachines classes.

The adapter:

- Reads the machine's public `getRecipes()` and `getInputSlots()` contracts.
- Reads each recipe's public inputs and outputs.
- Preserves alternative ingredient choices and their individual required amounts.
- Revalidates the selected guide alternative against the complete authoritative choice group.
- Requires the maintained FastMachines 54-slot layout with ingredient slots 0–35.
- Protects slots 36–53, which contain previews, navigation, information, energy, selection and crafting controls.
- Fails closed and leaves recipes browse-only if the addon changes that verified inventory contract.

## Safety boundary

An adapter cannot directly commit inventory changes. It only returns a transfer definition. Slimefun Legacy validates that definition and performs the transaction.

Legacy rejects the transfer when:

- The target is not the exact expected Slimefun machine.
- The player lacks protection access or the block is not owned by the current Folia region.
- The menu is locked or currently viewed.
- The adapter fails its final safety hook.
- The recipe no longer matches the selected alternatives.
- Ingredient, input-slot, or protected-slot data is invalid.
- Input slots overlap protected slots.
- The player lacks ingredients or the machine cannot hold the requested sets.
- Commit validation fails; both inventories are restored.

## Deferred

- Additional addon-specific custom inventories discovered during server testing.
- Nearby storage withdrawal.
- Recursive sub-recipe crafting.
- Automatic machine processing or output generation.
