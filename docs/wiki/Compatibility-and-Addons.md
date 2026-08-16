# 🔌 Compatibility & Addons

Slimefun's addon ecosystem is one of its biggest strengths — and one of the main reasons Legacy has dedicated compatibility tooling.

## Server software

| Platform | Legacy status |
| --- | --- |
| Paper 26.2 / Minecraft 26.2 | ✅ Primary supported line |
| Purpur based on Paper 26.2 | ✅ Supported |
| Conventional Paper derivatives | ⚠️ Often compatible, not guaranteed |
| Folia based on the target Paper line | ⚠️ Experimental |
| Spigot / CraftBukkit / Bukkit | ❌ Unsupported |
| Sponge | ❌ Unsupported |
| Hybrid servers such as Arclight/Mohist/Cardboard | ❌ Unsupported/blocked |
| Fabric / Forge / NeoForge | ❌ Not a supported plugin runtime |

The historical `api-version: 1.16` in the plugin descriptor exists for compatibility behavior; it is **not** the supported Minecraft-version floor.

## Addon compatibility statuses

Legacy can consume optional addon compatibility declarations. An addon without a declaration is not automatically blocked; it is reported as **Undeclared**.

A declaration can describe:

- tested Slimefun core variants;
- minimum Minecraft/Java versions;
- required platform capabilities;
- required and optional plugins;
- supported platform families;
- compatibility notes.

Missing required capabilities/plugins can produce an **Incompatible** diagnostic, while an untested core can produce a **Warning**.

## CI-monitored addon families

The current compatibility matrix includes release-blocking Legacy forks and representative advisory probes.

### Release-blocking Legacy probes

- FastMachines Legacy
- Networks Expansion Legacy
- SlimeTinker IE2 Legacy
- BetterChests Legacy

### Representative/advisory probes

Examples currently include Networks upstream, Infinity Expansion 2, DynaTech, Supreme, Magic Expansion, FluffyMachines, FastMachines upstream, SlimeTinker upstream, FoxyMachines, FlowerPower, IDreamOfEasy, Gastronomicon, Bump, SlimeCustomizer and EMCTech.

> [!IMPORTANT]
> **CI coverage is evidence, not a guarantee that your exact addon JAR is compatible.** Versions, optional dependencies and local configuration still matter.

## Check an installed server

Use:

```text
/sf versions
/sf doctor compatibility
/sf doctor dependencies
```

Legacy may annotate addons with markers such as `Deps!`, `Alias`, `Runtime!`, `Linkage!` or `Startup?`. Read the hover/details before deciding the core is at fault.

## External plugin dependencies

If an addon requires a library plugin, install the **real dependency required by that addon**. Legacy can diagnose declared dependencies and provider aliases; it does not emulate arbitrary third-party library classes.

## Reporting an addon problem

Include:

- Paper/Minecraft version;
- Java version;
- Slimefun Legacy release/commit;
- exact addon JAR/version;
- full startup log and complete exception;
- reproduction steps;
- staging-server result;
- `/sf versions` output;
- relevant Doctor output.

This is far more useful than “addon X doesn't work.”