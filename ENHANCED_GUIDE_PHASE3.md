# Slimefun Legacy Native Enhanced Guide — Phase 3

Phase 3 expands the native recipe-preparation system beyond shaped crafting grids. It adds unordered core multiblock support, transactional Ancient Altar preparation and detailed missing-ingredient reports while preserving normal Slimefun crafting and ritual activation.

## Player workflow

### Unordered multiblock machines

1. Open a supported recipe in the enhanced guide.
2. Aim at any block in the completed multiblock or its dispenser.
3. Left-click **Fill Crafting Interface** to prepare one recipe.
4. Shift-left-click to prepare as many complete sets as inventory and dispenser capacity allow.
5. Activate the machine normally.

Supported unordered machines:

- Grind Stone
- Smeltery
- Ore Crusher
- Compressor
- Pressure Chamber

Unlike shaped recipes, ingredients may occupy any suitable dispenser slots. Existing unrelated contents stop the transfer instead of being overwritten.

### Ancient Altar

1. Open an Ancient Altar recipe in the enhanced guide.
2. Aim at the altar or any of its eight pedestals.
3. Click **Prepare Ancient Altar**.
4. The guide places one correct ingredient on each empty pedestal.
5. When enabled, the center catalyst is moved safely into the selected hotbar slot.
6. Right-click the altar normally to begin the ritual.

The guide does not trigger the ritual itself. Shift-click preparation is intentionally unavailable for altar recipes.

## Ingredient reports

- The button lore shows the first configurable number of missing ingredients.
- Right-clicking the button sends the complete required/available report to chat.
- Missing Slimefun ingredients can show that a sub-recipe exists and display its Slimefun ID.
- Sub-recipe information is advisory only; recursive crafting is not executed in Phase 3.

## Safety properties

- All player and dispenser changes are planned against cloned snapshots before commit.
- Shaped transfers retain the Phase 2 all-or-nothing extraction fix.
- Unordered machines are matched through their registered core multiblock definitions and required dispenser orientation.
- Protection permissions and Folia region ownership are checked before any world or inventory write.
- Ancient Altar preparation validates the altar, all eight registered pedestals, clear space above them and the absence of existing pedestal items.
- Pedestal item entities retain the player's actual item metadata and use the same no-pickup marker and armor-stand presentation expected by the core altar.
- If any pedestal placement or inventory commit fails, placed entities are removed and the original player inventory is restored where possible.
- A conservative altar activity lock prevents guide preparation during the ritual animation. Its duration is derived from the altar's configured 36-step sequence and step delay, with the configured fallback as a minimum.
- The protected guide button remains bound to a short-lived inventory session and cannot be extracted through dragging, shift-transfer or double-click collection.

## Configuration

```yaml
features:
  recipe-fill:
    unordered-machines: true

    ancient-altar:
      enabled: true
      prepare-catalyst-in-hand: true
      activation-lock-seconds: 15

    missing-report:
      maximum-lore-lines: 4
      show-sub-recipe-hints: true
```

Existing server copies of `enhanced-guide.yml` remain compatible because every Phase 3 setting has a code default. Merge the new section or regenerate the file only when you want the options visible for editing.

## Current boundary

Phase 3 does not automatically craft recipes, start altar rituals, withdraw from nearby storage or recursively craft sub-components. GUI-based addon machine adapters and direct crafting remain separate future phases so each inventory boundary can receive its own validation and rollback behavior.
