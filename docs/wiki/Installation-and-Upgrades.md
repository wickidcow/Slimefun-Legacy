# 📦 Installation & Upgrades

Slimefun Legacy is maintained for modern Paper servers and should be installed like a production plugin: **backup first, stage changes, restart cleanly, then validate**.

## Requirements

| Component | Current Legacy target |
| --- | --- |
| Server | **Paper 26.2 / Minecraft 26.2** |
| Purpur | Supported when based on the same Paper line |
| Java runtime | **Java 25** |
| Folia | Experimental secondary target |
| Client mod | Not required |
| Resource pack | Optional; server-provided if used |

Spigot/CraftBukkit, Sponge, hybrid mod/plugin servers and Fabric/Forge/NeoForge are not supported runtime targets for Slimefun Legacy.

## Fresh installation

1. Stop the server normally.
2. Create a complete backup.
3. Download a tested JAR from the Slimefun Legacy **Releases** page.
4. Place the JAR in `plugins/`.
5. Make sure there is only **one Slimefun core provider** in the plugins directory.
6. Start the server normally.
7. Review console startup for dependency or addon failures.
8. Run the validation commands below.

> [!WARNING]
> Do **not** use `/reload` to install, upgrade or repair Slimefun. Perform a full restart.

## Upgrade checklist

Back up at minimum:

- worlds and player data;
- Slimefun data storage / `data-storage` where used;
- Slimefun plugin configuration;
- databases and storage data;
- addon plugin folders and addon data;
- the exact old JARs needed for rollback.

Then upgrade **one layer at a time** when practical: core first, validate, then addon changes.

## Post-start validation

Run:

```text
/sf doctor status
/sf doctor core
/sf doctor compatibility
/sf doctor dependencies
/sf doctor runtime
/sf doctor integrations
```

Then test representative gameplay:

- Guide/search;
- Enhanced Crafting Table and a multiblock;
- an electric machine;
- Cargo;
- backpacks/storage;
- protection integration;
- representative addon items and machines.

## English-first configuration

For a normal English-only setup, Legacy recommends:

```yaml
options:
  auto-update: false
  language: en
  enable-translations: false
```

Restart fully after changing these settings.

## Migrating older items or translated data

Minecraft item stacks can retain old display names/lore. Slimefun Legacy includes conservative item/storage Doctor tools that identify recognized items by persistent Slimefun ID and avoid guessing unknown or ambiguous data.

Start with a dry run:

```text
/sf doctor status
/sf doctor scan
```

Only after reviewing the results should an operator consider:

```text
/sf doctor repair confirm
```

See [Doctor & Diagnostics](Doctor-and-Diagnostics.md).

## Rollback rule

If an upgrade causes serious problems, stop the server and restore **both the previous plugin set and the matching backed-up data**. Do not repeatedly swap core JARs against actively-mutating production data hoping the issue disappears.