# 🛠️ Tools, Armor & Equipment

Slimefun adds equipment that changes how players mine, travel, fight, survive hazards and manage resources. Many of these items are useful long before a player reaches full factory automation.

## Specialized tools

Classic Slimefun includes tools built around specific abilities rather than simply higher durability or damage. Familiar examples include:

- Gold Pan and Nether Gold Pan
- Grappling Hook
- Lumber Axe
- Smelter's Pickaxe
- Pickaxe of Containment
- Explosive Pickaxe and Explosive Shovel
- Pickaxe of the Seeker
- Pickaxe of Vein Mining
- Climbing Pick
- Soulbound tool variants

Exact abilities, materials and restrictions should be checked in the current Guide.

## Why special tools need server testing

A tool that breaks multiple blocks, moves a player, interacts with spawners or changes drops can cross boundaries that vanilla tools never touch.

Server owners should test these items with:

- claim/protection plugins
- logging/rollback plugins
- custom enchants
- jobs/economy plugins
- anti-cheat systems
- custom world generators

Test as a normal player, not only as an operator.

## Armor families

Slimefun includes several armor concepts, including hazard protection, magical equipment, mobility gear and other specialized sets.

Important examples include:

- **Hazmat equipment** for radiation protection
- **Soulbound armor** for death-related inventory behavior
- **movement equipment** such as Jet Boots, Jetpacks, wings/parachute-style gear where enabled
- **special protective armor** with passive effects

The safest rule is to treat armor as an active gameplay system, not merely custom-colored vanilla armor.

## Hazmat equipment

Nuclear materials are covered separately in **[Radiation & Reactors](Radiation-and-Reactors.md)**.

For normal Slimefun radiation protection, wear the complete intended protective set. Mixing pieces from different armor sets should not be assumed to provide the same result.

## Movement gear

Mobility items can interact with flight, fall damage and anti-cheat plugins. When testing movement equipment, verify:

- activation/deactivation behavior
- energy or fuel use
- landing/fall behavior
- world-change behavior
- death/respawn behavior
- restrictions in protected regions

Slimefun Legacy includes focused runtime safeguards around movement-gear lifecycle behavior, but external plugins can still cancel or alter movement events.

## Weapons

Slimefun weapons may use special effects beyond ordinary attack damage. Server owners should test them against the same PvP, region and combat rules used on the live server.

Do not assume a weapon bypassing damage in an operator test means it will behave the same inside a protected region.

## Soulbound items

Soulbound equipment interacts with death and inventory handling. Other plugins can also manipulate drops, graves, inventories or keep-inventory rules.

Before deploying a custom death system alongside Slimefun, test:

1. normal player death
2. PvP death
3. death in another world
4. full inventory on respawn
5. server restart after death/recovery

## Equipment troubleshooting

If a tool or armor item suddenly loses its special behavior:

1. Check its Slimefun ID with administrative diagnostics.
2. Confirm it was not converted/recreated by another item plugin.
3. Confirm the player has any required research/permission.
4. Check protection or anti-cheat cancellations.
5. Determine whether the item belongs to core Slimefun or an addon.
6. If the item came from an older translated build, use Doctor recovery rather than manually rebuilding its lore.

## Related pages

- **[Magic, Runes & Talismans](Magic-Runes-and-Talismans.md)**
- **[Radiation & Reactors](Radiation-and-Reactors.md)**
- **[Protection Plugins & Claims](Protection-Plugins-and-Claims.md)**
- **[Troubleshooting](Troubleshooting.md)**
