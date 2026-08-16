<div align="center">

<img src="https://raw.githubusercontent.com/wickidcow/Slimefun-Legacy/master/docs/images/slimefun-legacy-logo.png" alt="Slimefun Legacy" width="180">

# Slimefun Legacy Wiki
### Classic Slimefun gameplay. Modern Paper maintenance. Production-focused diagnostics.

**[Getting Started](Getting-Started.md)** · **[Learn Slimefun](Slimefun-in-a-Nutshell.md)** · **[Install](Installation-and-Upgrades.md)** · **[Addons](Addon-Ecosystem.md)** · **[Troubleshoot](Troubleshooting.md)** · **[Developers](Developer-Guide.md)**

</div>

> [!IMPORTANT]
> **Slimefun Legacy is an unofficial, independently maintained downstream fork of Slimefun 4.** It is not an official release of the original Slimefun project, Slimefun United, or the SlimefunGuguProject.

Slimefun Legacy turns a normal Minecraft server into a modpack-like experience with machines, electricity, automation, Cargo networks, magic, backpacks, Androids, GPS, reactors, equipment, resources and a huge addon ecosystem — without requiring a client mod.

This wiki keeps the approachable structure that made the classic Slimefun community wiki useful, while rewriting it for Legacy's modern Paper support, Enhanced Guide, compatibility tooling, production safeguards and recovery diagnostics.

## 🧭 Choose your path

| I am a... | Start here |
| --- | --- |
| 🧪 **New player** | [Getting Started](Getting-Started.md) → [Research & Progression](Research-and-Progression.md) |
| ⚒️ **Learning machines** | [Multiblocks & Basic Machines](Multiblocks-and-Basic-Machines.md) → [Electric Machines](Electric-Machines.md) |
| ⚙️ **Factory builder** | [Energy Networks](Energy-Networks.md) + [Cargo Networks](Cargo-Networks.md) → [Factory Design Patterns](Factory-Design-Patterns.md) |
| 🪄 **Magic player** | [Magic, Runes & Talismans](Magic-Runes-and-Talismans.md) → [Tools, Armor & Equipment](Tools-Armor-and-Equipment.md) |
| 🤖 **Automation player** | [Programmable Androids](Programmable-Androids.md) |
| 🛰️ **Explorer / late game** | [GPS, GEO & Teleportation](GPS-GEO-and-Teleportation.md) → [Radiation & Reactors](Radiation-and-Reactors.md) |
| 🛡️ **Server owner** | [Installation & Upgrades](Installation-and-Upgrades.md) → [Server Configuration](Server-Configuration.md) → [Server Owner Guide](Server-Owner-Guide.md) |
| 🔐 **Claims / protection admin** | [Protection Plugins & Claims](Protection-Plugins-and-Claims.md) |
| 🔌 **Addon administrator** | [Addon Ecosystem](Addon-Ecosystem.md) → [Compatibility & Addons](Compatibility-and-Addons.md) |
| 🚀 **Performance admin** | [Server Performance](Server-Performance.md) |
| 🩺 **Troubleshooter** | [Doctor & Diagnostics](Doctor-and-Diagnostics.md) → [Troubleshooting](Troubleshooting.md) |
| 🐛 **Bug reporter** | [Bug Reporting](Bug-Reporting.md) |
| 🧑‍💻 **Addon developer** | [Developer Guide](Developer-Guide.md) |

## 🎮 Player guides

| Guide | What it covers |
| --- | --- |
| [🧪 Slimefun in a Nutshell](Slimefun-in-a-Nutshell.md) | What Slimefun is, classic progression and project history |
| [🚀 Getting Started](Getting-Started.md) | Your first Guide, research, resources and machines |
| [📚 Research & Progression](Research-and-Progression.md) | Unlocks, XP progression and planning your tech tree |
| [⚒️ Multiblocks & Basic Machines](Multiblocks-and-Basic-Machines.md) | The classic pre-electric workshop |
| [⛏️ Resources, Dusts & Alloys](Resources-Dusts-and-Alloys.md) | Material processing and component chains |
| [⚙️ Electric Machines](Electric-Machines.md) | Powered processing, machine tiers and automation |
| [⚡ Energy Networks](Energy-Networks.md) | Generation, storage, distribution and consumers |
| [📦 Cargo Networks](Cargo-Networks.md) | Item transport, channels and factory automation |
| [🏭 Factory Design Patterns](Factory-Design-Patterns.md) | Reliable production-line layouts and debugging patterns |
| [🤖 Programmable Androids](Programmable-Androids.md) | Slimefun's programmable worker robots |
| [🛰️ GPS, GEO & Teleportation](GPS-GEO-and-Teleportation.md) | GPS infrastructure, GEO resources and teleporters |
| [🪄 Magic, Runes & Talismans](Magic-Runes-and-Talismans.md) | Magical crafting, runes, passive effects and gadgets |
| [🛠️ Tools, Armor & Equipment](Tools-Armor-and-Equipment.md) | Special tools, movement gear, armor and weapons |
| [🎒 Backpacks & Storage](Backpacks-and-Storage.md) | Portable storage and Legacy safety notes |
| [☢️ Radiation & Reactors](Radiation-and-Reactors.md) | Nuclear materials, Hazmat protection and reactors |
| [📖 Glossary](Glossary.md) | Common Slimefun and Legacy terminology |

## 🛡️ Server administration

Server operators should begin with the installation and backup procedure, then learn the configuration and Doctor tooling before adding a large addon stack.

Recommended administrator path:

**[Installation & Upgrades](Installation-and-Upgrades.md)** → **[Server Configuration](Server-Configuration.md)** → **[Server Owner Guide](Server-Owner-Guide.md)** → **[Protection Plugins & Claims](Protection-Plugins-and-Claims.md)** → **[Addon Ecosystem](Addon-Ecosystem.md)** → **[Doctor & Diagnostics](Doctor-and-Diagnostics.md)** → **[Server Performance](Server-Performance.md)**

## 🚀 Current platform target

Slimefun Legacy is maintained primarily for **Paper 26.2 / Minecraft 26.2 on Java 25**. Purpur based on the same Paper line is supported. Folia support exists as an **experimental** secondary target and every installed addon must also be Folia-safe.

For the exact current release, always use the repository's **Releases** page rather than relying on a version number copied into a wiki page.

## ✨ What makes Legacy different?

- **English-first operation** without requiring a translation plugin for the normal English experience.
- **Compatibility preservation** for established Slimefun APIs, saved data and addons wherever practical.
- **Enhanced Guide** features including smarter search, bookmarks, recipe preparation and broader machine recipe browsing.
- **Doctor diagnostics** for storage/item recovery, plugin dependencies, addon compatibility, runtime health and integrations.
- **Release compatibility gates** that source-build and binary-linkage test representative addons before release.
- **Production safety** around machine failures, backpack opening, shutdown/write state and risky upgrade paths.

## 📚 Classic Slimefun knowledge, modernized

The original Slimefun community wiki documented individual machines, resources, Androids, Cargo, GPS, reactors, magic, equipment, performance guidance, protection integrations, common issues and developer topics. Slimefun Legacy's wiki uses those proven concepts as a roadmap while rewriting documentation for the current project rather than copying historical pages verbatim.

For recipes and item availability on a specific server, **the in-game Slimefun Guide is authoritative** because server owners can disable content and addons can add or modify categories.

## ❤️ Community

Slimefun exists because of years of work from the original developers, addon authors, translators, testers, wiki contributors and server communities. Please see [Credits, Licensing & Support](Credits-Licensing-and-Support.md) before redistributing or building on this project.

> **NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**
