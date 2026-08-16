# 🤖 Programmable Androids

Programmable Androids are one of Slimefun's most distinctive automation systems: small server-side robots that can perform repeated tasks according to a script.

Classic Slimefun Android families include general-purpose, mining, farming, woodcutting, fishing and butchering variants depending on the enabled content.

## The basic idea

An Android normally combines three concepts:

1. **A physical Android block/entity** placed in the world.
2. **Fuel or operating resources** required by that Android type.
3. **A script** made from supported instructions that tells it what to do.

The script is intentionally simple. Androids are not general-purpose computers; they execute a limited set of Slimefun actions repeatedly.

## Learn with a tiny script

When starting out, avoid building a complex mining program immediately.

A safer learning path is:

1. Place an Android in a controlled area.
2. Open its interface.
3. Create a very short script.
4. Make it move or perform one simple action.
5. Verify its orientation and loop behavior.
6. Add one instruction at a time.

If a script stops working, the last instruction added is then easy to identify.

## Android types

Specialized Androids may have abilities that a normal Android does not. Examples historically include:

- **Miner Androids** for breaking appropriate blocks
- **Farmer Androids** for crop work
- **Woodcutter Androids** for tree-related tasks
- **Fisherman Androids** for fishing automation
- **Butcher Androids** for supported mob interactions

Exact abilities are defined by the current Slimefun build and should be verified in the in-game Guide.

## Protection matters

Android automation must respect world protection.

Server owners should test Androids as ordinary players inside the same claims/regions used in production. An Android that appears functional while tested as an operator may be denied actions for a normal player.

## Avoid runaway scripts

Keep Android work areas bounded and understandable.

Good practice:

- use short loops
- avoid sending Androids toward unloaded or unknown terrain without testing
- keep output inventories available
- design a clear stop/recovery procedure
- test scripts before duplicating them across a large farm or mine

## When an Android stops

Check:

- fuel/resources
- script order
- orientation
- blocked movement
- output inventory space
- protection permissions
- unloaded chunks
- addon ownership if it is not a core Android

If a runtime exception appears, follow **[Bug Reporting](Bug-Reporting.md)**.

## Server-owner note

Large numbers of continuously running Androids can contribute to server work just like machines and networks. If an area becomes expensive, profile it rather than assuming Cargo or energy is responsible.

See **[Server Performance](Server-Performance.md)**.

## Related pages

- **[Slimefun Legacy in a Nutshell](Slimefun-in-a-Nutshell.md)**
- **[Research & Progression](Research-and-Progression.md)**
- **[Server Performance](Server-Performance.md)**
- **[Troubleshooting](Troubleshooting.md)**
