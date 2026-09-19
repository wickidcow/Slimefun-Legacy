# Slimefun Legacy Addons

Slimefun Legacy is designed to preserve the classic Slimefun addon ecosystem while keeping addons usable on modern Paper servers.

> [!IMPORTANT]
> Repository availability is not the same as a compatibility guarantee. Always test addon updates on a staging server and review `/sf versions` plus the Slimefun Doctor compatibility diagnostics before deploying to production.

## Installing addons

1. Install a supported Slimefun Legacy build and start the server once.
2. Stop the server normally.
3. Download the addon JAR from its repository or from the curated addon bundle attached to Slimefun Legacy releases.
4. Place the addon JAR in `plugins/`.
5. Install any dependency explicitly required by that addon.
6. Start the server normally. Do **not** use `/reload`.
7. Review the console and run:

```text
/sf versions
/sf doctor compatibility
/sf doctor dependencies
/sf doctor runtime
```

Slimefun Legacy releases: https://github.com/wickidcow/Slimefun-Legacy/releases

## Maintained Legacy addon repositories

The repositories below are currently present and not archived under the `wickidcow` account. This list describes maintenance status only; it does not claim that every exact build has been validated against every Minecraft, Paper, Purpur, or Folia version.

| Addon | Repository |
| --- | --- |
| AlchimiaVitae | https://github.com/wickidcow/SF_AlchimiaVitae |
| BetterChests | https://github.com/wickidcow/SF_BetterChests |
| BetterFarming | https://github.com/wickidcow/SF_BetterFarming |
| BuildingStaff | https://github.com/wickidcow/SF_BuildingStaff |
| Cultivation Legacy | https://github.com/wickidcow/SF_CultivationLegacy |
| DankTech2 | https://github.com/wickidcow/SF_DankTech2 |
| DracFun Reborn | https://github.com/wickidcow/SF_DracFun2 |
| DynaTech | https://github.com/wickidcow/SF_DynaTech |
| ElectricSpawners | https://github.com/wickidcow/SF_ElectricSpawners |
| ExoticGarden | https://github.com/wickidcow/SF_ExoticGarden |
| ExtraHeads | https://github.com/wickidcow/SF_ExtraHeads |
| FastMachines | https://github.com/wickidcow/SF_FastMachines |
| FinalTECH | https://github.com/wickidcow/SF_FinalTECH |
| FluffyMachines | https://github.com/wickidcow/SF_FluffyMachines |
| FlowerPower | https://github.com/wickidcow/SF_FlowerPower |
| FNAmplifications | https://github.com/wickidcow/SF_FNAmplifications |
| FoxyMachines | https://github.com/wickidcow/SF_FoxyMachines |
| Galactifun | https://github.com/wickidcow/SF_Galactifun |
| Gastronomicon | https://github.com/wickidcow/SF_Gastronomicon |
| GeneticChickEngineering | https://github.com/wickidcow/SF_GeneticChickEngineering |
| HotbarPets | https://github.com/wickidcow/SF_HotbarPets |
| IDreamOfEasy | https://github.com/wickidcow/SF_IDreamOfEasy |
| InfinityExpansion2 | https://github.com/wickidcow/SF_InfinityExpansion2 |
| JustEnoughGuide | https://github.com/wickidcow/SF_JustEnoughGuide |
| LiteXpansion | https://github.com/wickidcow/SF_LiteXpansion |
| LuckyBlocks | https://github.com/wickidcow/SF_LuckyBlocks |
| Magic8Ball | https://github.com/wickidcow/SF_Magic8Ball |
| MagicExpansion | https://github.com/wickidcow/SF_MagicExpansion |
| MilitaryArsenal | https://github.com/wickidcow/SF_MilitaryArsenal |
| MobCapturer | https://github.com/wickidcow/SF_MobCapturer |
| MobDrops | https://github.com/wickidcow/SF_MobDrops |
| NetworksExp | https://github.com/wickidcow/SF_NetworksExp |
| RelicsOfCthonia | https://github.com/wickidcow/SF_RelicsOfCthonia |
| RykenSlimeCustomizer | https://github.com/wickidcow/SF_RykenSlimeCustomizer |
| SlimeEasy | https://github.com/wickidcow/SF_SlimeEasy |
| SlimefunAdvancements | https://github.com/wickidcow/SF_SlimefunAdvancements |
| SlimefunWarfare | https://github.com/wickidcow/SF_SlimefunWarfare |
| SlimeGlue | https://github.com/wickidcow/SF_SlimeGlue |
| SlimeHUD | https://github.com/wickidcow/SF_SlimeHUD |
| SlimeTinkerIE2 | https://github.com/wickidcow/SF_SlimeTinkerIE2 |
| SlimyTreeTaps | https://github.com/wickidcow/SF_SlimyTreeTaps |
| SMG | https://github.com/wickidcow/SF_SMG |
| SoulJars | https://github.com/wickidcow/SF_SoulJars |
| Supreme | https://github.com/wickidcow/SF_Supreme |

## Related maintained projects

These projects are maintained alongside the Slimefun Legacy ecosystem but are not presented here as ordinary Slimefun addons:

| Project | Repository | Notes |
| --- | --- | --- |
| Pylon | https://github.com/wickidcow/pylon | Related modern addon/platform ecosystem |
| Rebar | https://github.com/wickidcow/rebar | Related modern addon/platform ecosystem |

Slimefun Legacy exposes read-only diagnostics for supported Rebar/Pylon runtime capabilities through `/sf doctor integrations`.

## Archived addon repositories

These repositories are currently archived and should be treated as historical unless they are explicitly revived:

| Addon | Repository |
| --- | --- |
| ExtraTools | https://github.com/wickidcow/SF_ExtraTools |
| Magic_RSC | https://github.com/wickidcow/SF_Magic_RSC |
| WorldTaste | https://github.com/wickidcow/SF_WorldTaste |

## Compatibility troubleshooting

When an addon fails to load or behaves incorrectly, collect:

- Paper/Purpur and Minecraft version
- Java version
- Slimefun Legacy version or commit
- Exact addon version/JAR
- Full startup log
- Full exception/stack trace
- Results from `/sf versions`
- Results from `/sf doctor compatibility <plugin>`
- Results from `/sf doctor dependencies <plugin>`

A plugin being listed here does not mean Slimefun Legacy can replace an addon's own external dependencies. Install the real dependency required by the addon.

## For addon developers

Slimefun Legacy preserves established Slimefun 4 APIs wherever practical and adds compatibility diagnostics and maintained integration surfaces for modern servers.

Relevant developer documentation:

- [Developer Guide](Developer-Guide.md)
- [Compatibility & Addons](Compatibility-and-Addons.md)
- [Doctor & Diagnostics](Doctor-and-Diagnostics.md)
- [Machine Recipe Provider API](https://github.com/wickidcow/Slimefun-Legacy/blob/master/docs/MACHINE_RECIPE_PROVIDER_API.md)
- [Machine Input Fill Adapter API](https://github.com/wickidcow/Slimefun-Legacy/blob/master/docs/MACHINE_INPUT_FILL_ADAPTER_API.md)
- [Platform Compatibility API](https://github.com/wickidcow/Slimefun-Legacy/blob/master/docs/PLATFORM_COMPATIBILITY_API.md)

For core compatibility issues, report them at:
https://github.com/wickidcow/Slimefun-Legacy/issues
