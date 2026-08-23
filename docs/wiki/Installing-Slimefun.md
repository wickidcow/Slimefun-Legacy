# Installing Slimefun Legacy

Slimefun Legacy is a server plugin. Players do **not** need to install a client mod.

## Requirements

| Requirement | Supported setup |
| --- | --- |
| Primary server | Paper 26.2 / Minecraft 26.2 |
| Secondary server | Purpur based on Paper 26.2 |
| Java runtime | Java 25 |
| Client | Normal Minecraft Java client |
| Folia | Experimental |

## New installation

1. Stop the server normally.
2. Create a complete backup before changing the plugin stack.
3. Download a tested Slimefun Legacy build from the repository's Releases page.
4. Place the JAR in the server's `plugins` directory.
5. Make sure only **one Slimefun core provider** is present.
6. Start the server and review the complete startup log.
7. Run `/sf doctor status` and `/sf doctor compatibility` as an operator.
8. Test representative recipes, machines, backpacks, Cargo networks, protections and addon items before opening a production server.

> [!WARNING]
> Never use `/reload` to install, upgrade or repair Slimefun Legacy. Stop and start the server normally.

## Upgrading from another Slimefun 4 build

Back up worlds, player data, Slimefun data, databases and addon data first. Remove or archive the previous core JAR so the server cannot load two competing Slimefun implementations.

For a fuller production migration checklist, see [Installation & Upgrades](Installation-and-Upgrades.md).

## Addons

Install Slimefun addons only after the core starts cleanly. See [Addons](Addons.md) and [Compatibility & Addons](Compatibility-and-Addons.md) before moving a large historical addon stack to a new server version.
