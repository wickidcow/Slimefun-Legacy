# 🪄 Magic, Runes & Talismans

Slimefun is not only an industrial technology plugin. Its classic magic progression adds special crafting stations, ritual-style recipes, runes, talismans and unusual utility items alongside the machines and factories.

## The magical crafting path

The two most recognizable magical crafting systems are the **Magic Workbench** and **Ancient Altar**.

A typical progression is:

**basic magical materials → Magic Workbench recipes → runes / talismans / gadgets → Ancient Altar recipes → high-tier magical equipment**

As with machines, use the current in-game Guide for exact recipes and structures.

## Magical materials

Classic Slimefun magic uses intermediate ingredients such as magical/ender lumps, magical glass, specialized crystals, necrotic materials and other components.

These items are meant to form a reusable crafting tree. If you find yourself repeatedly working backward through the same recipes, bookmark those components in the Enhanced Guide and keep a small stockpile.

## Ancient Runes

Runes are specialized magical items used for crafting or applying particular effects. Their exact behavior depends on the rune and current implementation.

Because some runes consume items, modify equipment or perform delayed actions, test unfamiliar runes on non-critical equipment first.

Slimefun Legacy includes correctness safeguards around delayed rune transactions so item state is handled conservatively when actions are cancelled or interrupted.

## Talismans

Talismans provide passive or triggered utility effects. Classic Slimefun includes a family of talismans built from a common base item and upgraded into specialized variants.

The current configuration includes an option to display talisman feedback in the action bar:

```yaml
talismans:
  use-actionbar: true
```

Server owners can change this, so player feedback may look different between servers.

## Magical gadgets

Magic eventually branches into utility, mobility, combat and survival items. Classic examples include knowledge-related items, special bonemeal, zombie-curing tools, teleportation-style items and other gadgets.

Do not assume an old wiki or video describes every current interaction exactly. Slimefun Legacy deliberately preserves classic behavior where practical, but Minecraft/Paper changes may require implementation updates.

## Soulbound equipment

Some magical recipes can create or interact with soulbound equipment. Because death/inventory handling can also be affected by other plugins, test soulbound behavior with the same death, inventory and protection plugins used on the production server.

## Magic and addons

Magic-focused addons can add entire parallel progression trees. If an item does not appear in the core Guide categories you expect, check the owning addon and `/sf versions`.

## Troubleshooting magical items

When a rune, talisman or magical gadget does not work:

1. Confirm the player has the required research.
2. Confirm the item is recognized as the expected Slimefun ID.
3. Check whether the action is blocked by a protection plugin.
4. Check whether the item belongs to an addon.
5. Capture the first exception if one occurs.
6. For old translated/migrated items, use Doctor diagnostics rather than manually rewriting item metadata.

## Related pages

- **[Enhanced Guide](Enhanced-Guide.md)**
- **[Research & Progression](Research-and-Progression.md)**
- **[Tools, Armor & Equipment](Tools-Armor-and-Equipment.md)**
- **[Doctor & Diagnostics](Doctor-and-Diagnostics.md)**
