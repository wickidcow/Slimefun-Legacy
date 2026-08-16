# 🛡️ Protection Plugins & Claims

Slimefun adds interactions that vanilla Minecraft does not have: machines can move items, Androids can act for a player, tools can affect multiple blocks, teleporters can move players and automated systems can interact with inventories.

That makes protection integration essential on a public server.

## The core rule

A Slimefun feature should not become a shortcut around a server's claim or region rules.

Slimefun's protection layer exists so actions can be checked against the installed protection system before they are performed.

## Why compatibility can vary

Protection plugins expose different kinds of information. Some can answer questions about offline owners, entities, PvP or container access; others only understand normal online-player block interactions.

That difference matters for features such as:

- Programmable Androids
- Cargo access to protected inventories
- special mining tools
- block placers
- GPS/teleportation
- entity-affecting items

Do not infer full compatibility from one successful block-place test.

## Test as a normal player

Operators often bypass protection checks. For meaningful testing:

1. Create a normal non-OP test account/player.
2. Make a claim/region owned by Player A.
3. Test Slimefun actions as Player B.
4. Repeat with the owner offline when Androids or offline ownership matter.
5. Test containers, entities, PvP and special tools separately.

## Fail-closed behavior

Slimefun Legacy's protection integration is intended to fail closed in protected situations: when the system cannot safely establish permission for a protected action, it should prefer denying that action over allowing a grief path.

This is important for production servers, but it can also make a feature appear "broken" when the real problem is a protection integration or permission decision.

## Common symptoms of protection conflicts

A protection issue can look like:

- a machine works in wilderness but not inside a claim
- an Android moves but cannot break/place/interact
- Cargo can see a machine but cannot move items through a protected inventory
- a special tool behaves like a normal tool or does nothing
- a teleporter refuses a destination
- an entity-affecting gadget works only for operators

When that pattern appears, test the same action outside the protected region before changing Slimefun configuration.

## Protection loggers

Logging/rollback plugins are separate from permission/claim plugins. A server can use both.

For unusual Slimefun block interactions, verify that your logging solution records the kinds of changes you expect before relying on it for rollback.

## Town/region servers

On Towny-, claims- or region-heavy servers, include Slimefun in your staging checklist whenever the protection plugin updates.

Test at least:

- placing/breaking Slimefun blocks
- opening machines
- Cargo between inventories
- Android actions
- multi-block tools
- teleportation
- entity interactions

## When a protection integration looks wrong

Collect:

- protection plugin name/version
- Slimefun Legacy version
- exact action that was allowed/denied
- whether the player was OP
- claim/region ownership
- whether the owner was online
- any console exception

Then report it using **[Bug Reporting](Bug-Reporting.md)**.

## Related pages

- **[Programmable Androids](Programmable-Androids.md)**
- **[Tools, Armor & Equipment](Tools-Armor-and-Equipment.md)**
- **[Cargo Networks](Cargo-Networks.md)**
- **[GPS, GEO & Teleportation](GPS-GEO-and-Teleportation.md)**
- **[Bug Reporting](Bug-Reporting.md)**
