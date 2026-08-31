<div align="center">

<img src="docs/images/slimefun-legacy-logo.png" alt="Slimefun Legacy logo" width="190">

# Slimefun Legacy
### Slimefun for modern Paper servers (EN)

Slimefun Legacy turns a normal Minecraft server into a modpack-like experience with machines, electricity, cargo networks, automation, reactors, magic, backpacks, and hundreds of custom items—without requiring players to install a mod.
[![Build](https://github.com/wickidcow/Slimefun-Legacy/actions/workflows/build-ci.yml/badge.svg)](https://github.com/wickidcow/Slimefun-Legacy/actions/workflows/build-ci.yml)
[![Compatibility](https://github.com/wickidcow/Slimefun-Legacy/actions/workflows/compatibility-ci.yml/badge.svg)](https://github.com/wickidcow/Slimefun-Legacy/actions/workflows/compatibility-ci.yml)
[![License](https://img.shields.io/github/license/wickidcow/Slimefun-Legacy?label=license)](LICENSE)
[![Java](https://img.shields.io/badge/Runtime-Java%2025-orange)](https://adoptium.net/)
[![Paper](https://img.shields.io/badge/Server-Paper-blue)](https://papermc.io/)
[![Language](https://img.shields.io/badge/Player%20language-English-brightgreen)](#english-first-and-recovery)
[![Servers](https://img.shields.io/bstats/servers/32960?label=servers)](https://bstats.org/plugin/bukkit/Slimefun%20Legacy/32960)
[![Players](https://img.shields.io/bstats/players/32960?label=players)](https://bstats.org/plugin/bukkit/Slimefun%20Legacy/32960)
[Download](https://github.com/wickidcow/Slimefun-Legacy/releases) ·
[Builds](https://github.com/wickidcow/Slimefun-Legacy/actions) ·
[Statistics](https://bstats.org/plugin/bukkit/Slimefun%20Legacy/32960) ·
[Report a Bug](https://github.com/wickidcow/Slimefun-Legacy/issues) ·
[Release History](EVERYTHING_THAT_CHANGED.md)

Current release candidate: **4.1.42 — Beacon Performance**. ·
[Contributing](CONTRIBUTING.md)

</div>

> [!IMPORTANT]
> **Slimefun Legacy is an unofficial, independently maintained downstream fork of Slimefun 4.**
> It exists to preserve and maintain the classic Slimefun experience for modern Paper servers and is maintained for the [AlbionMC.com](https://albionmc.com) server and the wider Slimefun community. It is not an official release of the original Slimefun project, Slimefun United, or the SlimefunGuguProject.
>
> **NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**

---
## ✨ What is Slimefun Legacy?

Slimefun Legacy preserves the familiar Slimefun 4 gameplay, saved-world format, and addon ecosystem while maintaining the project for modern Paper servers.
| Focus | What it means |
| --- | --- |
| **English first** | Player-facing names, lore, menus, messages, and controls are maintained in English without requiring Slimefun Translate. |
| **Legacy compatible** | Established Slimefun addon entry points and saved data are preserved wherever practical. |
| **Stability focused** | Storage recovery, machine circuit breakers, lifecycle safeguards, and regression tests protect production servers. |
| **Modernized carefully** | Paper APIs, scheduling, energy handling, and internal annotations are updated without casually breaking older addons. |
| **Addon compatibility gates** | Runtime declarations, source-build probes, and binary-linkage checks expose regressions before release. |
| **External adapter diagnostics** | Optional Rebar/Pylon blocks can be capability-mapped without hard-linking their experimental APIs. |
Players can build automated factories, move items through Cargo networks, generate and store power, operate reactors, explore magic, craft equipment, and expand the experience with compatible addons.

---
## 📊 Live Statistics

<p align="center">
  <a href="https://bstats.org/plugin/bukkit/Slimefun%20Legacy/32960">
    <img alt="Servers" src="https://img.shields.io/bstats/servers/32960?style=for-the-badge&label=Servers">
  </a>
  <a href="https://bstats.org/plugin/bukkit/Slimefun%20Legacy/32960">
    <img alt="Players" src="https://img.shields.io/bstats/players/32960?style=for-the-badge&label=Players">
  </a>
</p>
<p align="center">
  <a href="https://bstats.org/plugin/bukkit/Slimefun%20Legacy/32960">
    <img src="https://bstats.org/signatures/bukkit/Slimefun%20Legacy.svg" alt="Slimefun Legacy bStats statistics">
  </a>
</p>

<p align="center">
  <sub>Anonymous usage statistics are provided by bStats. Server owners can opt out in <code>plugins/bStats/config.yml</code>.</sub>
</p>

---
## 🖼️ Screenshots

A few classic community builds showing the kinds of technology, automation, and magic available through Slimefun:
| Reactors and Energy | Automation Factory | Magic Laboratory |
| :---: | :---: | :---: |
| ![Reactors and energy systems](docs/images/showcase/showcase1.png) | ![Large automation factory](docs/images/showcase/showcase6.png) | ![Slimefun magic laboratory](docs/images/showcase/showcase5.png) |
| *HamtaBot#0001* | *Piͭxͪeͤl (mnb)#5049* | *Kilaruna#4981* |
| ![Slimefun industrial build](docs/images/showcase/showcase4.png) | ![Slimefun factory interior](docs/images/showcase/showcase3.png) | ![Slimefun laboratory build](docs/images/showcase/showcase2.png) |
| *GalaxyKat11#3816* | *TamThan#7987* | *Kilaruna#4981* |
These are historical Slimefun community showcase images. Full image credits and source information are included in [`docs/images/README.md`](docs/images/README.md).

---
## 📦 Download and requirements

| Requirement | Supported setup |
| --- | --- |
| **Primary server** | Paper 26.2 / Minecraft 26.2 |
| **Secondary server** | Purpur based on Paper 26.2 |
| **Java runtime** | Java 25 |
| **Client** | Normal Minecraft Java client; no client mod required |
| **Resource pack** | Optional; Slimefun Legacy's external sender is disabled by default |
The supported production line uses Java 25. The repository also builds with Java 25 while deliberately targeting Java 21 bytecode for Slimefun-owned classes.

Resource-pack delivery is optional and remains off unless a server owner explicitly enables it. Servers already using ItemsAdder can leave Legacy's sender disabled and continue using their own combined pack. See [`docs/RESOURCE_PACK.md`](docs/RESOURCE_PACK.md).

Download a tested build from [GitHub Releases](https://github.com/wickidcow/Slimefun-Legacy/releases). Development artifacts are available from [GitHub Actions](https://github.com/wickidcow/Slimefun-Legacy/actions).
> [!WARNING]
> Back up all worlds, player data, Slimefun data, databases, and addon data before replacing any Slimefun core build. Never use `/reload` for an installation, upgrade, or repair.

---
## 🚀 Installation

1. Stop the server normally and create a complete backup.
2. Download Slimefun Legacy from this repository's [Releases](https://github.com/wickidcow/Slimefun-Legacy/releases) page.
3. Place the JAR in the server's `plugins` directory.
4. Remove or archive the previous Slimefun core JAR so only one core provider can load.
5. Start the server, review the console, and run:

```text
/sf doctor status
/sf doctor core
/sf doctor compatibility
/sf doctor dependencies
/sf doctor runtime
/sf doctor integrations
```
Test representative machines, backpacks, Cargo networks, recipes, protections, and addon items before reopening a production server.

---
### Rebar/Pylon diagnostics

When Rebar is installed and its runtime API matches a supported reflective shape, Slimefun Legacy can classify loaded Rebar/Pylon blocks without a compile-time dependency. Look at a block within 8 blocks and run:

```text
/sf doctor integrations probe
```
The probe reports mapped inventory/storage, cargo/logistics, processor/machine, and fluid capabilities. This is discovery only: Slimefun does not automatically inject items into Rebar cargo networks or convert Rebar/Pylon electricity.
Phase 1E also isolates repeatedly failing external adapter callbacks without changing normal Slimefun Cargo, Energy, machine, guide, or addon execution. Admins can use `/sf doctor integrations retry <id|all>` or `/sf doctor integrations reload`; machine isolation can be cleared with `/sf doctor runtime retry` or `/sf doctor runtime retry all`.
Phase 1F improves `/sf versions` with a runtime recognition registry for addon families monitored by Legacy CI. It clearly separates declared compatibility from CI coverage and unknown compatibility; CI coverage is never treated as a guarantee for the exact installed addon JAR.

### Plugin dependency diagnostics

Phase 1K adds read-only plugin dependency diagnostics so operators can distinguish a Slimefun/API problem from an addon's own external library requirement. Useful commands include:

```text
/sf doctor dependencies
/sf doctor dependencies GuizhanLibPlugin
/sf doctor compatibility SlimefunLuckyBlocks
```

The report shows declared hard and soft dependencies, missing or disabled required plugins, reverse consumers, and Paper provider aliases. Provider aliases are reported only as descriptor-level resolution: they do **not** prove that the provider contains every Java class or runtime API expected by a dependent addon.

Slimefun Legacy does not install, enable, replace, or emulate third-party plugin dependencies. If an addon requires an external library plugin such as GuizhanLibPlugin, use the real dependency required by that addon. Cross-fork Gugu API probes remain advisory compatibility evidence; Gugu is not a Slimefun Legacy runtime-core target.

Phase 1K Part 2 carries the same boundary evidence into `/sf versions`. The addon list remains compact, but addons that need attention can show `Deps!`, `Alias`, `Runtime!`/`Linkage!`, or `Startup?` markers with detailed hover text. The summary also reports how many installed Slimefun addons have healthy declared hard dependencies, dependency problems, provider-alias resolution, or failures observed inside Slimefun-guarded callbacks.

These diagnostics are intentionally conservative. Slimefun can report dependency metadata and failures that occurred inside its own guarded addon callback boundary, but it does **not** intercept arbitrary Paper plugin startup/onEnable exceptions or parse the server log. If a plugin is disabled while its declared hard dependencies are satisfied, `/sf versions` and `/sf doctor compatibility <plugin>` tell the operator to inspect the console and that addon's configuration instead of guessing a core cause.

### Release lifecycle

Phase 1L begins with a release-lifecycle rollover: 4.1.30 becomes the active development candidate, while the validated 4.1.29 release commit becomes the release-blocking previous-stable compatibility baseline. The historical 4.1.15 floor remains advisory. This Part 1 work is metadata and CI lifecycle hardening only; it does not change normal Cargo, Energy, machine, storage, database, saved-world, or gameplay semantics.

## 🌐 English-first and recovery

The normal English experience does **not** require Slimefun Translate. Recommended settings in `plugins/Slimefun/config.yml` are:

```yaml
options:
  auto-update: false
  language: en
  enable-translations: false
```

A full restart is required after changing these options.
Items created by an older translated build can retain translated names or lore because Minecraft stores that display data inside each item stack. Slimefun Legacy includes a conservative **Storage and Item Doctor** that identifies recognized items by persistent Slimefun ID and repairs visible metadata while preserving stored item data.
| Command | Purpose |
| --- | --- |
| `/sf doctor status` | Shows shutdown state, pending writes, paused machine circuits, and repair status |
| `/sf doctor hand` | Repairs the recognized Slimefun item in your hand |
| `/sf doctor inventory [player]` | Repairs an online player's inventory and ender chest |
| `/sf doctor scan` | Performs a batched server-wide dry run without changing items |
| `/sf doctor repair confirm` | Starts the confirmed batched server-wide repair |
Permission: `slimefun.command.doctor`
Default access: server operators

For existing translated servers, use:

```text
/sf doctor status
/sf doctor scan
/sf doctor repair confirm
```

Always review the dry-run results before confirming a repair. Unknown IDs, malformed state, and ambiguous dynamic lore are reported and skipped rather than guessed.

---
## 🛡️ Maintenance highlights

Slimefun Legacy currently includes:
- Compatibility Foundation gates for public API removals, Java bytecode drift, dependency boundaries, deprecations, and future Paper API compilation
- Capability-based Paper, Purpur, Folia, Minecraft-version, scheduler, Adventure, chunk-loading, and data-component diagnostics
- A reviewed multi-fork candidate radar for Original Slimefun, Gugu, Slimefun5, Slimefun United, and Slimefun4Core
- Duplicate and re-entrant backpack-open protection
- Clean-shutdown tracking and pending database-write visibility
- Per-machine ticker circuit breakers with cooldown and retry support
- Safer viewer, ticker, chunk, inventory, and entity lifecycles
- Cargo allocation reductions, cached block resolution, and corrected profiler accounting
- Clear Cargo connector text using `Connected: ✔` and `Connected: ✘`
- Addon compatibility CI and public API binary compatibility reporting
- Protection integration tests that fail closed
- Global, asynchronous, location-owned, and entity-owned scheduler paths
- Modern `BlockTicker` and long-capacity energy API overloads
- Preserved legacy method descriptors for addon compatibility
- Native Enhanced Guide with indexed smart search, bookmarks, safe recipe preparation, and universal machine recipe browsing
- Guide runtime isolation with slow-menu and addon ownership diagnostics
- Addon-facing machine recipe provider API for structured inputs, alternatives, outputs, timing, and energy metadata
- Safe machine input-fill adapter API for standard and custom addon inventories, including Supreme `GenericMachine` and FastMachines compatibility
Historical compatibility, core-platform, release, validation, and Enhanced Guide development notes are consolidated in [`EVERYTHING_THAT_CHANGED.md`](EVERYTHING_THAT_CHANGED.md). Current addon integration details remain in [`docs/MACHINE_RECIPE_PROVIDER_API.md`](docs/MACHINE_RECIPE_PROVIDER_API.md), [`docs/MACHINE_INPUT_FILL_ADAPTER_API.md`](docs/MACHINE_INPUT_FILL_ADAPTER_API.md), and [`docs/PLATFORM_COMPATIBILITY_API.md`](docs/PLATFORM_COMPATIBILITY_API.md).

---
## 🔌 Compatibility
### Compatible server software
| Server software | Compatibility |
| --- | :---: |
| Paper 26.2 / Minecraft 26.2 | ✅ Primary supported line |
| Purpur based on Paper 26.2 | ✅ Supported |
| Most conventional Paper forks | ⚠️ Usually compatible |
| Folia based on Paper 26.2 | ⚠️ Experimental |
| Spigot | ❌ Unsupported |
| CraftBukkit / Bukkit | ❌ Unsupported |
| Sponge | ❌ Unsupported |
| Hybrid servers such as Arclight, Mohist, or Cardboard | ❌ Unsupported and blocked |
| Fabric / Forge / NeoForge | ❌ Unsupported — this is a server plugin, not a mod |
Slimefun Legacy 4.1.42 is tested primarily against **Paper 26.2 / Minecraft 26.2 on Java 25**. Purpur and most conventional Paper forks should work, but fork-specific behavior cannot be guaranteed. The `api-version: 1.16` plugin descriptor is retained for historical Bukkit material and addon behavior; it is not the supported Minecraft-version floor.
The machine-readable support contract remains under `compatibility/`. Historical Compatibility Foundation and Paper/Purpur maintenance notes are consolidated in [`EVERYTHING_THAT_CHANGED.md`](EVERYTHING_THAT_CHANGED.md).
Folia Phase 1 routes machine ticks and entity/location callbacks through their owning schedulers while preserving Paper behavior. Cargo and energy networks intentionally operate only on nodes owned by the regulator's current Folia region; transactional cross-region transfers are not enabled yet. Folia therefore remains experimental.
**Every installed addon must also be Folia-safe.** The historical Folia Phase 1 safety boundary and staging checklist are preserved in [`EVERYTHING_THAT_CHANGED.md`](EVERYTHING_THAT_CHANGED.md).
### Addons and existing worlds

The project aims to preserve the established Slimefun 4 addon API and data used by official Slimefun, Gugu-based installations, and compatible forks. The addon ecosystem is large, so no core fork can guarantee every historical addon build.
When reporting an addon compatibility issue, include the Paper and Minecraft versions, Java version, Slimefun Legacy commit, exact addon build, full startup log, complete exception, reproduction steps, and results from a clean staging server.

---
## ⚙️ Building from source

<details>
<summary>Show build instructions</summary>

Clone the repository:

```bash
git clone https://github.com/wickidcow/Slimefun-Legacy.git
cd Slimefun-Legacy
```
Linux or macOS:

```bash
chmod +x gradlew
python3 scripts/verify_legacy.py .
./gradlew spotlessApply --no-daemon
./gradlew spotlessCheck clean build --no-daemon
```

Windows:
```powershell
python scripts/verify_legacy.py .
.\gradlew.bat spotlessApply --no-daemon
.\gradlew.bat spotlessCheck clean build --no-daemon
```
The shaded plugin JAR is written to `build/libs/`.

</details>

---
## 🤝 Contributing and reporting bugs

Pull requests are welcome. Changes should preserve English player-facing text, maintain addon compatibility where practical, include tests for behavior changes, avoid unsafe global inventory rewrites, and respect server-thread or region ownership.

Use the [GitHub Issue Tracker](https://github.com/wickidcow/Slimefun-Legacy/issues) for reproducible bugs. Include complete logs and version information rather than a cropped screenshot of a single error line.
See [`CONTRIBUTING.md`](CONTRIBUTING.md) for the complete development workflow.

---
## 🌳 Project lineage & thanks

Slimefun Legacy would not exist without the original Slimefun project and the community that built it.

**Slimefun was created by [TheBusyBiscuit](https://github.com/TheBusyBiscuit) in 2013.** What began as a single-developer project grew into a long-running open-source ecosystem maintained and expanded by hundreds of contributors, addon developers, translators, testers, server owners, and community members.

Slimefun Legacy gratefully acknowledges and preserves the work of:

- **[TheBusyBiscuit](https://github.com/TheBusyBiscuit)** — original creator of Slimefun
- **[Slimefun/Slimefun4](https://github.com/Slimefun/Slimefun4)** — the original Slimefun 4 project and its contributors
- **[SlimefunGuguProject/Slimefun4](https://github.com/SlimefunGuguProject/Slimefun4)** — downstream maintenance and compatibility work used as an upstream reference
- **[Slimefun United](https://github.com/Slimefun-United/Slimefun-United)** — community continuation and compatibility work used as an upstream reference
- The many Slimefun addon authors and maintainers whose projects made the ecosystem what it is

This fork is intended to **preserve, maintain, and extend** that work for modern servers—not to replace the original developers or claim their work as its own. Upstream authorship, commit history, copyright notices, and license obligations remain respected. Where code or ideas originate from another project, that project and its contributors should continue to receive appropriate credit.

Thank you to everyone who created, maintained, documented, tested, translated, supported, or built addons for Slimefun over the years. Slimefun Legacy stands on that work.

---
## ⚖️ Independence, trademarks & non-affiliation

**Slimefun Legacy is an independent community-maintained fork.** It is maintained for **AlbionMC.com** and for people who want the classic Slimefun experience on modern Paper-based servers.

**NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**

Slimefun Legacy is not affiliated with, endorsed by, sponsored by, approved by, or operated by Mojang Studios, Microsoft Corporation, Minecraft, the original Slimefun project, Slimefun United, or the SlimefunGuguProject. References to those projects, companies, products, or communities are for identification, compatibility, historical attribution, and interoperability only.

Minecraft, Mojang Studios, Microsoft, and all other third-party names, logos, brands, and trademarks referenced by this project remain the property of their respective owners. No ownership, sponsorship, partnership, or endorsement is claimed or implied.

This repository does not grant rights to Minecraft, Mojang, Microsoft, or other third-party assets beyond rights provided by their respective licenses, terms, policies, or applicable law. Users and distributors remain responsible for complying with the Minecraft EULA, Minecraft Usage Guidelines, third-party licenses, and any other terms that apply to their use or distribution.

---
## 📄 License

Slimefun Legacy is distributed under the [GNU General Public License v3.0](LICENSE), consistent with the upstream Slimefun 4 licensing. Modifications distributed from this repository remain subject to the GPLv3 and its source-availability requirements.

Copyright and authorship of upstream work remain with the original Slimefun authors and contributors. Copyright in later modifications remains with the contributors who authored those changes. Nothing in the Slimefun Legacy name, branding, README, or distribution is intended to transfer, erase, or claim ownership of upstream authorship.

For the complete license terms, see [`LICENSE`](LICENSE).

<div align="center">

### Keep Slimefun alive. Keep it compatible. Keep it understandable.

Made for modern Paper servers, maintained for **AlbionMC.com**, and dedicated to the community that kept Slimefun alive.

[Back to top](#slimefun-legacy)

</div>
