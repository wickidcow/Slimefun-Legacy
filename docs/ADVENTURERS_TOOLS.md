# Adventurer's Curios — Tools

The **Tools** child section is reserved for specialized excavation and field-engineering equipment that does not belong in the normal Slimefun tool progression.

## Deepcore Tunnel Borer

The Deepcore Tunnel Borer converts the existing Explosive Pickaxe into a controlled rectangular excavation tool.

### Bore modes

- **Service bore — 3x5**
- **Freight bore — 5x7**
- **Shelter bore — 9x11**

Right-click cycles the selected bore size. Sneaking while mining temporarily disables the area effect for precision single-block mining.

The block the player mines determines the forward tunnel face. The bottom of the bore follows the player's foot level, so the remaining blocks are removed upward rather than equally above and below the targeted block.

### Safety and performance

- Additional blocks are checked through Slimefun's protection manager before breaking.
- World-border and unbreakable-material rules are respected.
- The borer never force-loads chunks.
- Slimefun blocks, custom blocks and tile entities such as containers and spawners are skipped.
- Folia operations stay inside the region owned by the current execution context.
- Larger modes have progressively longer short cooldowns to prevent rapid 9x11 excavation from becoming a server-load spike.
- Tool durability is charged progressively: approximately one extra durability roll for every 12 additional blocks actually broken, in addition to the normal primary-block wear.
- Extra terrain uses normal `breakNaturally` behavior, preserving ordinary tool enchantment/drop semantics.
