# Slimefun Legacy Native Enhanced Guide — Phase 2

Phase 2 adds safe guide-to-machine recipe filling while preserving Slimefun Legacy's native recipe renderer.

## Player workflow

1. Open a supported item recipe in the enhanced guide.
2. Aim at the completed crafting multiblock or its dispenser.
3. Click **Fill Crafting Interface**.
4. The guide moves the correct ingredients from the player's inventory into the dispenser's 3x3 recipe grid.
5. Right-click the machine normally to craft.

Left click fills one complete recipe. Shift-left click fills as many complete recipe sets as the dispenser and player inventory can hold.

## Supported in this phase

- Enhanced Crafting Table
- Magic Workbench
- Armor Forge

## Safety properties

- Transfers are planned against cloned inventory snapshots and committed only after the whole operation validates.
- The target must be a structurally valid machine, not an arbitrary dispenser; Armor Forge requires the correct upward-facing dispenser.
- Existing incompatible items or occupied unused slots abort the operation.
- Recipe matching follows the selected core machine, including its backpack handling, while stack merging still protects distinctive item instances.
- Backpack and other instance data is preserved by moving the player's actual item stack data.
- Protection plugins are queried using Slimefun's protection manager.
- Folia region ownership is checked before either inventory is changed.
- The feature only moves items. It does not craft, fire synthetic craft events or pull from nearby storage.
- Short-lived guide sessions are bound to the exact open recipe inventory. Dragging, double-click collection and shift-transfer cannot take or overwrite the guide button.
- If an inventory setter unexpectedly fails, the manager attempts to restore both pre-transfer snapshots and logs the exception.

## Deliberate next boundaries

GUI machine adapters, unordered recipes, Ancient Altar filling, recursive sub-recipe crafting and nearby-storage withdrawal are not included yet. Those should use the same transactional planner but need separate target and protection adapters.
