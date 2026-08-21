# Adventurer's Curios — Tools

The **Tools** child section is reserved for specialized excavation and field-engineering equipment that does not belong in the normal Slimefun tool progression.

## Deepcore Tunnel Tools

Deepcore tunnel tools excavate a fixed square tunnel face while preserving the existing directional and player-height behavior.

### Tool families

Each family has separate **3x3**, **5x5**, and **9x9** tools with its own Enhanced Crafting Table recipe:

- **Deepcore Tunnel Pickaxe** — expands only through pickaxe-mineable stone, ore, and similar terrain.
- **Deepcore Tunnel Shovel** — expands only through shovel-mineable dirt, sand, gravel, snow, and similar terrain.
- **Deepcore Tunnel Paxel** — combines the pickaxe and shovel terrain sets in one tool.

The 5x5 tools upgrade from their matching 3x3 tool, and the 9x9 tools upgrade from their matching 5x5 tool. Paxels additionally require the corresponding pickaxe and shovel progression.

Sneaking while mining temporarily disables the area effect for precision single-block mining.

### Tunnel geometry

Each activation excavates a maximum of **3 blocks forward**. The tool's 3x3, 5x5, or 9x9 size controls the width and height of the tunnel face.

The block the player mines determines the forward tunnel direction. The bottom of the tunnel follows the player's foot level, so excavation grows upward from the player's current height rather than being centered around the targeted block.

### Safety and performance

- Additional blocks are checked through Slimefun's protection manager before breaking.
- World-border and unbreakable-material rules are respected.
- Deepcore tools never force-load chunks.
- Slimefun blocks, custom blocks and tile entities such as containers and spawners are skipped.
- Folia operations stay inside the region owned by the current execution context.
- Larger tools have progressively longer short cooldowns to limit rapid 9x9 excavation load.
- Tool durability is charged progressively: approximately one extra durability roll for every 12 additional blocks actually broken, in addition to the normal primary-block wear.
- Extra terrain uses normal `breakNaturally` behavior, preserving ordinary tool enchantment/drop semantics.
