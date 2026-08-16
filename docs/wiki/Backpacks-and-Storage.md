# 🎒 Backpacks & Storage

Backpacks are portable inventories that let players carry far more than a normal Minecraft inventory. They are especially useful for mining, exploration, factory work and large crafting projects.

## Classic backpack tiers

Slimefun's traditional backpack progression contains six increasing storage tiers:

| Backpack | Slots |
| --- | :---: |
| **Small Backpack** | 9 |
| **Backpack** | 18 |
| **Large Backpack** | 27 |
| **Woven Backpack** | 36 |
| **Gilded Backpack** | 45 |
| **Radiant Backpack** | 54 |

Some servers or addons may introduce additional storage items, so always use the in-game Guide for what is actually available.

## How backpack storage differs from a chest

A Slimefun backpack is not simply a renamed chest item. Its inventory is backed by Slimefun data and must be opened, saved and closed safely.

That means server owners should treat backpack data as something worth protecting during:

- core upgrades
- database changes
- crash recovery
- addon migrations
- player-data restoration

## Legacy backpack safety

Slimefun Legacy includes additional safeguards around backpack opening and lifecycle handling. These are intended to prevent duplicate/re-entrant opens and reduce the risk of stale open-state reservations when an unexpected runtime failure occurs.

These safeguards protect normal operation, but they are **not a substitute for backups**.

## Administrative recovery

Slimefun includes administrator-facing backpack recovery tooling. Access is controlled by permissions such as:

- `slimefun.command.backpack`
- `slimefun.command.backpack.other`

Keep these permissions restricted to trusted administrators because backpack recovery can expose player storage.

## Good player habits

For valuable backpacks:

- do not intentionally duplicate the item
- avoid handing the same backpack item between players while its inventory is open
- wait for the inventory to close normally before moving the item between unusual storage systems
- do not use client or plugin exploits that rapidly reopen inventories
- report a backpack that refuses to open instead of repeatedly forcing it

## Server-owner backup advice

Before changing Slimefun builds, back up:

- worlds
- player data
- Slimefun data
- databases
- addon data

Then stop the server normally and perform the upgrade while it is offline.

Never use `/reload` as an upgrade or repair method.

## If a backpack will not open

1. Keep the backpack item intact.
2. Do not repeatedly spam-open it.
3. Record the player's name and backpack tier.
4. Check the console for the first exception.
5. Run `/sf doctor status` and relevant runtime diagnostics.
6. Confirm the server is running the expected Slimefun Legacy JAR.
7. If this began after an update, test a copy of the data on staging before attempting recovery.

## Related pages

- **[Doctor & Diagnostics](Doctor-and-Diagnostics.md)**
- **[Installation & Upgrades](Installation-and-Upgrades.md)**
- **[Troubleshooting](Troubleshooting.md)**
