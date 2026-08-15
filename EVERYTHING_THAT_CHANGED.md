# Slimefun Legacy 4.1.31 — Resonance Beacon & Radiation Gear Update

- Fixed the Resonance Beacon GUI so display-only status, pyramid and controls items cannot be picked up or moved.
- Gravity Well now pulls every Bukkit `Enemy` implementation, including Endermen, plus dropped items while preserving Monster-only debuff behavior.
- Added the Advanced Hazmat Suit: a four-piece Armor Forge upgrade with native Slimefun radiation and bee protection, Protection IV and Unbreaking VI; the helmet retains water breathing and the chestplate retains fire resistance.
- Added optional per-beacon **Electric Operation** using the native Slimefun Energy Network. It is OFF by default for backwards compatibility.
- Electric beacons use a configurable 4,096 J buffer and a bounded once-per-second cost derived from active power tiers. Insufficient energy pauses powers without deleting unlocks or selections.
- Activator chunk tickets are released while an electric beacon is unpowered and resume automatically when energy returns.
- Added a backwards-compatible location-aware EnergyNet participation hook; existing energy components inherit the old always-active behavior.
- Resonance Beacon still has exactly 28 powers; Electric Operation is an operating mode, not a new power.

---

# Slimefun Legacy 4.1.29 — Core Platform Phase 1K

## Part 2 — Addon Boundary Evidence in `/sf versions`

- `/sf versions` now summarizes declared addon hard-dependency health beside the existing compatibility status summary.
- Addon lines expose compact `Deps!`, `Alias`, `Runtime!`/`Linkage!`, and `Startup?` markers only when operator attention is useful.
- Compatibility-status hover text now includes hard-dependency health, provider-alias warnings, guarded callback failure evidence, and conservative disabled-addon startup guidance.
- Provider aliases remain descriptor-level dependency evidence only; they are never treated as proof that the provider exposes the Java classes or APIs an addon expects.
- Guarded callback failures are explicitly scoped to callbacks Slimefun executed through its existing addon runtime boundary. Slimefun does not intercept arbitrary Paper plugin startup/onEnable failures or parse the server log.
- `/sf doctor compatibility <plugin>` now distinguishes guarded runtime linkage evidence from compatibility-provider warnings and explains when a disabled addon's hard dependencies are healthy but its plugin-side startup cause is unknown.
- No addon is automatically enabled, disabled, replaced, downloaded, or modified by these diagnostics.
- No Slimefun item IDs, recipes, research IDs, Cargo/Energy behavior, machine processing semantics, storage keys, database schemas, or saved-world formats were changed.

## Part 1 — Dependency & Addon Boundary Hardening

- Added read-only Paper plugin dependency diagnostics through `/sf doctor dependencies [plugin-or-dependency]`.
- Reports declared hard dependencies, soft dependencies, missing/disabled required dependencies, provider aliases, and reverse dependency consumers.
- `/sf doctor compatibility <plugin>` now includes declared hard-dependency health beside Slimefun compatibility evidence.
- Provider aliases are explicitly treated as descriptor-level load resolution only, never proof that expected Java classes or runtime APIs are present.
- Slimefun Legacy does not install, enable, disable, replace, download, or emulate third-party plugin dependencies. Addons remain responsible for their own required libraries.
- Gugu/Original/United source probes remain advisory cross-fork API evidence; Legacy/Paper remains the runtime and release target.
- Phase 1J verification was made forward-compatible so later Phase 1 releases preserve all Phase 1J invariants instead of being blocked by a literal phase label.
- No Slimefun item IDs, recipes, research IDs, Cargo/Energy behavior, machine processing semantics, storage keys, database schemas, or saved-world formats were changed.

---

## 4.1.28 Item Doctor translation-recovery hotfix

- Item Doctor now repairs a Chinese display name independently from lore safety. A stateful addon item can therefore receive its English canonical name on pickup even when Doctor deliberately preserves an unknown lore-backed state line.
- Core Slimefun items use their authoritative English lore template even when translated descriptive lines contain static numbers such as percentages, healing values, armor statistics, machine speeds, or talisman levels. Functional metadata is not replaced.
- Third-party addon lore now uses a conservative partial recovery path: text-only translated lines and unambiguous numeric/UUID lines are repaired, while ambiguous state lines remain untouched.
- Fixed false unsafe-state detection for durability-only addon items that implement `Rechargeable` but correctly report zero charge capacity, including the Reinforced Fluffy Wrench.
- Added PlayerBackpack PDC recognition for non-core items such as FluffyMachines Dolly so its English owner presentation can be rebuilt without changing its backing storage UUID or contents.
- Added authoritative static recovery for the known FluffyMachines `ELECTRIC_DUST_FABRICATOR` and `REINFORCED_FLUFFY_WRENCH` IDs.
- Unknown/orphaned Slimefun IDs continue to receive only a safe English display-name fallback; their lore remains untouched until an authoritative addon template is available.
- Item Doctor completion warnings now distinguish protected/unresolved CJK lore from unknown IDs instead of implying that every registered template is itself untranslated.

## 4.1.28 Item Doctor orphaned-item presentation hotfix

- Item Doctor can now safely repair Chinese display names on orphaned Slimefun items whose addon is no longer installed by deriving an English name from the stored Slimefun ID.
- Orphaned item lore is deliberately preserved when no authoritative English addon template is available. Potion effects, PDC/NBT, enchantments, attributes, custom model data and all other functional metadata remain untouched.
- Registered items continue to use their canonical English Slimefun template, so old talismans and other surviving addon/core items still receive their exact current English presentation.
- Added `stability.item-doctor.repair-orphaned-item-names` (default `true`) as a safety switch for this fallback.
# Slimefun Legacy 4.1.28 — Core Platform Phase 1J

Phase 1J unifies addon-facing compatibility services and adds advisory source-drift verification for Original Slimefun, Slimefun Gugu, and Slimefun United while preserving Legacy's existing addon API and gameplay contracts.

## Part 1 — Addon API Compatibility Facade

- Added a stable `AddonApiCompatibilityFacade` exposing the running core family, intended cross-fork API targets, registry state, guarded callback health, compatibility declarations, and registration compatibility service.
- Added explicit capability identifiers so addons and diagnostics can query supported compatibility facilities without guessing from implementation classes.
- Original Slimefun, Gugu, United, and Legacy are compatibility targets; this is an API-contract commitment, not a guarantee for every exact addon JAR.

## Part 2 — Addon Registration Compatibility

- Added `AddonRegistrationService` for callbacks that need to run after initial Slimefun item registration has finalized.
- Callbacks submitted early are queued until `SlimefunItemRegistryFinalizedEvent`; callbacks submitted later run immediately.
- Queued callbacks execute behind the existing guarded addon failure boundary and never auto-disable the owning plugin.
- Late/runtime Slimefun item registration remains supported and is not frozen by the new service.
- Added read-only registration snapshots for pending, completed, failed, and skipped callbacks plus registry ownership evidence.

## Part 3 — Cross-Fork Verification & Doctor

- Added a machine-readable cross-fork API matrix for Original Slimefun, Slimefun Gugu, and Slimefun United.
- Added advisory GitHub Actions source-drift probes for representative shared APIs such as `SlimefunAddon`, `SlimefunItem.register(...)`, and registry-finalization hooks.
- External fork drift is informational/advisory; Legacy's protected API baseline and addon source/binary matrix remain the release-blocking regression gates.
- Added `/sf doctor compatibility api [plugin]` for the active facade, registration timing, registry ownership, declaration source, and guarded callback evidence.

## Compatibility guarantees

- Existing 4.1.19 protected addon signatures remain release-blocking compatibility gates.
- Normal Cargo, Energy, Guide, Ticker, AContainer, BlockTicker, SlimefunItem, and NetworkManager protected behavior remains unchanged.
- No item IDs, recipes, research IDs, storage keys, database schemas, or saved-world formats are changed.
- No automatic upstream merge or addon/core download behavior is introduced.

---

# Slimefun Legacy 4.1.27 — Core Platform Phase 1I

Phase 1I modernizes Slimefun's world, chunk, and block-data runtime foundations while preserving normal gameplay semantics.

## Part 1 — World & Chunk Lifecycle Foundation

- Added a read-only world/chunk runtime service backed by chunk/world lifecycle events.
- Tracks ready, loading, unloading, failed, and untracked chunk states without loading, pinning, generating, or unloading chunks.
- Added `/sf doctor chunks` for world/chunk lifecycle diagnostics.
- Added Paper/Folia ownership-aware chunk-load fallback through the centralized Slimefun scheduler.

## Part 2 — Block Data Runtime Foundation

- Added a read-only block-data runtime service and immutable diagnostics snapshot.
- Reports loaded chunk/block records, unknown Slimefun IDs, lifecycle correlation, deferred loads, and load failures.
- Preserves the existing database/storage schema and saved-world format.
- On Folia, world-startup storage resolution now resolves each stored chunk on its owning region instead of touching chunk state from the global region.

## Part 3 — Machine/Chunk Runtime Coordination

- Added a read-only machine/chunk coordination service that correlates existing ticker registrations with observed chunk lifecycle state.
- `/sf doctor core` now includes chunk lifecycle and machine/chunk correlation evidence.
- Coordination remains observational: it does not pause, remove, re-register, accelerate, or otherwise alter normal machine tickers.

## Compatibility guarantees

- Existing protected addon APIs remain intact.
- Normal Slimefun Cargo, Energy, Guide, Ticker, AContainer, BlockTicker, SlimefunItem, and NetworkManager protected behavior remains unchanged.
- No item IDs, recipes, research IDs, storage keys, database schemas, or saved-world formats are changed.

---

# Slimefun Legacy 4.1.26 — Core Platform Phase 1H

Phase 1H continues the internal modernization work without changing normal Slimefun gameplay semantics.

## Part 1 — Registry Runtime Foundation

- Added a read-only Registry Runtime API and immutable registry snapshots.
- Initial item-registration finalization is now observable without freezing or changing the existing registry.
- Runtime-added item counts are derived from the finalized baseline, preserving existing runtime registration support.
- Added per-plugin registry ownership summaries for items, item groups, and ticker-backed items.
- Added `/sf doctor registry` for read-only registry diagnostics.

## Part 2 — Core Readiness

- Added a combined Core Readiness service with STARTING, READY, DEGRADED, STOPPING, STOPPED, and FAILED states.
- Readiness combines lifecycle, registry-finalization, scheduler, storage, and machine-runtime health.
- `/sf doctor core` now reports the combined readiness state and reasons when degraded.
- Readiness is observational and never pauses machines, changes storage, or mutates registered content.

## Part 3 — Guarded Addon Callback Foundation

- Added additive default guarded-callback helpers to `AddonRuntimeHealthService`.
- Guarded callbacks record RuntimeException/LinkageError failures without disabling the owning plugin.
- Third-party integration hook registration now uses the shared guarded callback path.
- Existing compatibility-provider and runtime telemetry semantics remain available.

## Compatibility guarantees

- Existing protected addon APIs remain intact.
- No item IDs, recipes, research IDs, storage keys, database schemas, or saved-world formats are changed.
- Normal Slimefun Cargo, Energy, machine ticking, Guide behavior, and protected machine core remain unchanged.

---

# Slimefun Legacy — Everything That Changed

This document is the single consolidated history for Slimefun Legacy. It replaces the former root `CHANGELOG.md` and the individual historical phase/release documents that were stored under `docs/history/`.

Current contributor and platform documentation remains in `README.md`, `CONTRIBUTING.md`, `AGENTS.md`, and the focused files under `docs/`. Historical release notes, compatibility work, migration notes, validation records, and prior phase documents are preserved below.

---

## Consolidated release changelog

## 4.1.25

# Slimefun Legacy 4.1.25 — Core Platform Phase 1G

## Core lifecycle and scheduler

- Added observable core lifecycle phases and ordered shutdown failure isolation.
- Added scheduler quiesce/health snapshots with compatibility-preserving default API methods.
- Added explicit ThreadService shutdown and corrected fixed-delay period handling.

## Machine, storage, and addon runtime foundations

- Added stable machine-runtime and read-only storage-runtime facades.
- Added addon callback health telemetry at existing guarded failure boundaries.
- Added `/sf doctor core` and addon callback-health evidence to focused compatibility diagnostics.

## Compatibility

- Preserves all 991 protected 4.1.19 API signatures and the Phase 1E normal-core hash guard.
- Does not intentionally change Cargo, Energy, machine processing, recipes, item IDs, research IDs, storage schemas, databases, or saved-world formats.

## 4.1.24

- Phase 1F Part 2.1: compact `/sf versions` addon lines to a single status word with detailed hover evidence and long-version hover preservation.

# Slimefun Legacy 4.1.24 — Core Platform Phase 1F

## Phase 1F Part 2 — Compatibility Diagnostics & Evidence

- Added a recognition-only addon tier distinct from Legacy CI monitoring.
- Expanded runtime addon lookup with Better Farming, DankTech2, Cultivation, Electric Spawners, ExtraTools, GeneticChickengineering, HotbarPets, Magic 8 Ball, MobCapturer, SFMobDrops, SlimefunAdvancements, SlimeGlue, SimpleMaterialGenerators, and SoulJars.
- Expanded `/sf doctor compatibility` with declaration, registry, runtime machine-health, and safe linkage evidence.
- Kept undeclared and recognition-only addons loadable and did not promote them to API status `Compatible`.

## Compatibility intelligence

- Makes `/sf versions` distinguish declared compatibility from Legacy CI monitoring and truly unknown compatibility.
- Recognizes addon families already covered by the compatibility matrix without falsely marking an undeclared exact JAR as guaranteed compatible.
- Replaces ambiguous undeclared/unrecognized presentation with operator-readable `✔`, `◉`, `?`, `⚠`, and `✕` states.
- Sorts installed addon output alphabetically.
- Adds a runtime addon recognition registry and a verifier that keeps it synchronized with the enabled CI matrix.

## Compatibility

- Does not change addon loading or the public compatibility status API.
- Keeps the 991 protected API signatures and Phase 1E normal-core hash guard intact.
- No normal Slimefun Cargo, Energy, machine, guide, item, recipe, storage, database, or saved-world behavior changes.

# Slimefun Legacy 4.1.23 — Core Platform Phase 1E

## Runtime stability

- Tracks live machine failures and automatic retry/isolation state for administrator diagnostics.
- Protects deferred synchronized machine callbacks and rate-limits repeated ticker lifecycle failures.
- Adds `/sf doctor runtime retry` recovery controls without removing ticker registrations or machine data.

## External integration hardening

- Adds capability-based Rebar/Pylon discovery without hard-linking experimental APIs.
- Isolates repeatedly failing external provider status/probe callbacks independently from Slimefun core.
- Adds `/sf doctor integrations probe`, `retry <id|all>`, and `reload`.
- Keeps Rebar/Pylon cargo transfer and energy exchange disabled until semantics are explicitly proven compatible.

## Administrator diagnostics

- Reworked `/sf versions` so every addon has a plain-language compatibility result instead of raw labels such as `[Undeclared]`.
- Shows `✔ Compatible`, `⚠ Compatible with warnings`, `? Compatibility not verified`, `✕ Incompatible`, or `✕ Disabled` beside each addon version.
- Explains that "not verified" means an enabled addon did not provide a Legacy compatibility declaration; it is not automatically considered incompatible.
- Adds an overall compatibility summary while keeping detailed declaration sources and reasons in hover text.

## Compatibility

- Keeps the 991 protected API signature baseline intact.
- Adds a hash guard proving normal Slimefun Cargo, Energy, NetworkManager, Guide, SlimefunItem, BlockTicker, AContainer and the green TickerTask are unchanged by Part 3.
- No item IDs, recipes, storage keys, database schemas, saved-world formats, normal cargo behavior, or normal energy behavior changed.

# Slimefun Legacy 4.1.22 — Core Platform Phase 1D

## Compatibility lifecycle

- Moved the release-blocking addon baseline from 4.1.15 to the previous stable 4.1.21 release.
- Added one machine-readable baseline registry shared by addon and public API compatibility workflows.
- Retained 4.1.15 as a separate non-blocking historical compatibility floor.
- Pinned baseline source refs for reproducible regression testing.

## CI hardening

- Required addon failures block release only when a candidate regresses relative to the previous stable baseline.
- Historical-floor comparisons run as advisory drift probes on scheduled/manual compatibility runs.
- Expanded the advisory addon matrix with FoxyMachines, FlowerPower, IDreamOfEasy, Gastronomicon, Bump, SlimeCustomizer, and EMCTech.
- Made Phase 1A-1C verifiers forward-compatible with future Legacy versions.
- Added a permanent Phase 1D lifecycle verifier.

## Compatibility

- No addon API signatures, item IDs, recipes, storage keys, database schemas, saved-world formats, or gameplay behavior changed.

# Slimefun Legacy 4.1.21 — Core Platform Phase 1C

## Addon compatibility infrastructure

- Added addon compatibility declarations through explicit registration, a provider interface, or `slimefun-compatibility.json`.
- Added runtime diagnostics for tested core variants, platform requirements, required dependencies, and optional integrations.
- Added a centralized optional-dependency and guarded-reflection service.
- Added `/sf doctor compatibility` and compatibility status details to `/sf versions`.
- Kept undeclared addons loadable and treated inactive optional integrations as informational.

## Release gates

- Added a machine-readable representative addon matrix and core API registry.
- Added dynamic GitHub Actions matrix generation.
- Extended addon comparison to verify both source compilation and precompiled binary linkage.
- Added missing-class, missing-method, and missing-field detection across compatibility-protected Slimefun namespaces.
- Added permanent Phase 1C verification and synthetic linkage regression tests.

## Compatibility

- Existing addon APIs remain available.
- No item IDs, recipes, storage keys, database schemas, saved-world formats, or gameplay behavior changed.
- Required compatibility targets block candidate-only regressions; independently maintained probes remain advisory.

# Slimefun Legacy 4.1.20 — Core Platform Phase 1B

## Addon compatibility API

- Added declarative `PlatformRequirements` and immutable `PlatformCompatibilityReport` APIs so addons can request capabilities, platform families, Java versions, and Minecraft versions without hard-coded fork checks.
- Added default compatibility helpers to `PlatformCompatibilityService` while preserving binary compatibility for implementations compiled against 4.1.19.
- Added convenience methods to `PlatformProfile` for Paper compatibility, region-owned execution, family checks, and display diagnostics.
- Added detection for Paper's player pick-block event as an explicit runtime capability.

## Centralized platform routing

- Added a single internal `RuntimePlatformDetector` for Paper, Purpur, Folia, scheduler, Adventure, data-component, async-chunk, and optional Paper class probes.
- Routed the core scheduler through the initialized platform service instead of a frozen static Folia flag.
- Replaced remaining PaperLib, direct Bukkit server-version, and scattered Folia probes in startup, diagnostics, guide version display, event threading, and error reporting.
- Retained the original `PaperScheduler(Plugin)`, `FoliaSupport.isFolia()`, legacy `MinecraftVersion`, and existing public scheduler signatures as compatibility bridges.

## API lifecycle and safeguards

- Added `@SlimefunDeprecated` to document the deprecation version, replacement, and optional future removal version without scheduling removals by default.
- Marked `FoliaSupport` as a retained compatibility bridge rather than removing or renaming it.
- Added Phase 1B verification that blocks new direct PaperLib checks, direct Paper/Folia class probes, and region-scheduler calls outside the compatibility and scheduler boundaries.
- Added a machine-readable 4.1.19 API signature baseline and verifier covering 991 compatibility-protected public and protected declarations; additions remain allowed, removals and signature changes fail CI.
- Added unit coverage for declarative addon requirements and complete incompatibility reporting.

## Compatibility

- No existing addon API signatures were removed.
- No item IDs, research IDs, recipes, storage keys, database schemas, saved-world formats, or gameplay behavior changed.
- Paper remains primary, Purpur supported, conventional Paper derivatives best effort, and Folia experimental.

# Slimefun Legacy 4.1.19 — Core Platform Foundation

## Core compatibility

- Added an addon-facing capability-based platform compatibility service.
- Added immutable Paper, Purpur, Folia, Paper-derivative, support-level, Java, Minecraft-version, and runtime-capability diagnostics.
- Added semantic Minecraft version parsing that is independent of historical enum ordering.
- Centralized startup version parsing while retaining the existing `MinecraftVersion` enum and public compatibility signatures.
- Aligned the startup Java recommendation with the Java 21 bytecode contract.
- Expanded `/sf versions` with the detected platform profile and capability inventory.

## Future update workflow

- Added a machine-readable registry for Original Slimefun, Gugu, Slimefun5, Slimefun United, and Slimefun4Core.
- Added a reviewed feature backlog so useful ideas can be scheduled without silently enabling them.
- Added an advisory upstream candidate checker and weekly GitHub Actions report.
- Kept the existing guarded Gugu merge workflow as the only code-merge upstream path.
- No workflow automatically merges, replaces, or downloads source into the Legacy branch.

## Compatibility

- No item IDs, research IDs, recipes, storage keys, database schemas, or gameplay behavior changed.
- Existing addon API signatures remain available.
- The new platform API is additive and covered by source and unit-test invariants.
- Paper remains primary, Purpur supported, conventional Paper derivatives best effort, and Folia experimental.

# Slimefun Legacy 4.1.18 — Guide & Runtime Stability

## Guide stability

- Guards classic and enhanced guide entry points, nested item groups, history restoration, search, bookmarks, pagination, item clicks, and addon `FlexItemGroup` menus.
- Blocks recursive calls and isolates addon runtime/linkage failures.
- Uses safe fallback icons and names for broken addon categories.
- Reports slow guide calls with player, mode, category key, category class, addon owner, nesting depth, and active call chain.
- Counts failures, recursion blocks, slow calls, fallbacks, and suppressed duplicate warnings, with periodic runtime summaries.

## Item Doctor stability

- A malformed stack can no longer terminate the complete scan or repair run.
- Runtime and addon linkage failures are counted, logged with safe item context, skipped, and scanning continues.
- Nested container failures are isolated the same way.
- Limited-use items without stored-use data or readable old lore fall back to their registered maximum instead of failing dynamic-state capture.
- Unknown IDs remain report-only and are never guessed or replaced.

## Machine reliability

- Auto Enchanter and Auto Disenchanter keep inputs untouched when another plugin cancels an event or a compatibility operation fails.
- Input stacks are validated before one item is consumed from each slot.
- Output capacity is checked before committing inputs.
- Processing time is never allowed to become zero ticks.
- The Auto Enchanter validates the final enchantment count, not only the incoming book.
- The Auto Disenchanter verifies every vanilla enchantment was removed and stored before accepting the operation.
- Visible status icons explain missing inputs, incompatible enchantments, full outputs, event cancellation, or blocked integration failures.
- The existing optional AdvancedEnchantments bridge remains supported without a hard dependency.

## Compatibility

- Primary: Paper 26.2 / Minecraft 1.21.11
- Secondary: Purpur based on Paper 26.2
- Runtime: Java 25
- Slimefun-owned bytecode target: Java 21
- Folia: experimental under the existing Phase 1 limitations

No item IDs, storage schemas, block data, backpack formats, or database formats are changed by this release.

# Slimefun Legacy 4.1.17 — Addon Doctor and Networks Compatibility

- Added the optional `AddonDoctor` and immutable `AddonDoctorReport` public API for addon-owned runtime diagnostics.
- Added `/slimefun doctor addons status`, `scan`, and confirmation-gated `repair` commands.
- Isolates provider failures so one broken addon cannot stop other doctor reports or the core Doctor command.
- Validates addon names, counters, detail lists, and third-party provider output before displaying it.
- Added a reflective Networks bridge so the same Networks JAR can expose diagnostics on Legacy without linking the API on United or Gugu.
- Networks Doctor reports loaded node/controller integrity, chunk-index drift, drawer cache state, SQLite queue state, and detected core/runtime versions without force-loading chunks.
- Retains the Paper 26.2 / Minecraft 1.21.11 compatibility foundation, Java 25 runtime support, and Java 21 bytecode contract.
- No Slimefun database-format, item-ID, saved-data, or gameplay-format changes are included.

# Slimefun Legacy 4.1.16 — Compatibility Foundation

- Added a machine-readable support contract for Paper 26.2 / Minecraft 1.21.11, Purpur, experimental Folia, Java 25 runtime and Java 21 bytecode.
- Added a configurable Paper API compile override and a non-blocking candidate Paper API CI job.
- Strengthened public API comparison with deterministic surface reports, added-signature reporting and hard failures when API classes cannot be inspected.
- Added Java class-file verification so Slimefun-owned bytecode cannot accidentally exceed Java 21.
- Added sensitive dependency-boundary budgets, including a zero-import rule for CraftBukkit and NMS internals.
- Added a normalized `-Xlint:deprecation` report without treating intentional compatibility bridges as release failures.
- Added permanent Compatibility Foundation verification and integrated it into the complete Legacy verifier.
- Reworked addon compatibility CI into a known-good 4.1.15 baseline versus candidate comparison with classified results and separate logs.
- Formats the pinned 4.1.15 baseline checkout before compiling so historical Spotless drift cannot prevent addon comparisons.
- Corrected dependency injection so only the core `Slimefun`/`Slimefun4` artifact is replaced; SlimefunTranslation, InfinityExpansion, InfinityLib and addon dependencies are preserved.
- Replaced the archived Gugu Networks requirement with the maintained `wickidcow/SF_NetworksExp` fork and made `2.1.112-Legacy-Alpha1` the release-blocking Networks compatibility target.
- No gameplay, item ID, saved-data or database-format changes are included.

# Slimefun Legacy 4.1.15 — FastMachines Input Filling

- Extended the Enhanced Guide's **Fill Machine Inputs** action to the maintained FastMachines machine inventory.
- Resolves FastMachines recipe choices from its public `getRecipes()`, `getInputs()`, `getChoices()` and `getBaseItem()` contracts without a hard dependency.
- Revalidates every displayed alternative and output against an authoritative FastMachines recipe before moving items.
- Writes exclusively to the machine's declared 0–35 ingredient slots.
- Protects slots 36–53, including recipe previews, navigation, selection, information, energy and craft controls.
- Requires the expected 54-slot FastMachines layout and fails closed when a future addon version changes that contract.
- Retains protection, Folia ownership, viewer locking, full transfer simulation, commit validation and rollback.
- Added FastMachines choice, output-mismatch and unsafe-layout regression coverage plus a permanent source verifier.

# Slimefun Legacy 4.1.14 — Custom Machine Input-Fill Adapters

- Added a public `MachineInputFillAdapter` API for addon machines with custom recipe storage or GUI layouts.
- Added a priority-ordered adapter registry with safe replacement and removal by namespaced key.
- Added immutable resolved-transfer definitions with defensive ingredient and slot copying.
- Kept protection checks, region ownership, viewer locking, full transfer simulation and rollback inside Slimefun Legacy.
- Added a built-in Supreme `GenericMachine` adapter using its public `machineRecipes`, input/output getter and status-slot contracts.
- Prevents malformed adapters from writing to invalid, duplicate or protected slots.
- Keeps unsupported custom machines and unverified recipes browse-only.
- Added API, Supreme compatibility and source-invariant regression tests.

# Slimefun Legacy 4.1.13 — Addon AContainer Input Filling

- Extended **Fill Machine Inputs** to addon machines that inherit Slimefun's standard `AContainer` class.
- Removed the old core-addon ownership restriction without weakening machine or inventory safety checks.
- Verifies every displayed recipe against the target machine's registered runtime `MachineRecipe` list before enabling filling.
- Keeps public-method, reflected and guide-only recipes view-only when they are not registered by the actual container.
- Revalidates selected ingredient alternatives at transfer time and uses the registered recipe's authoritative amounts.
- Added order-independent matching for reordered inputs, duplicate ingredients and exact outputs.
- Blocks malformed addon containers whose declared input and output slots overlap.
- Added addon-container regression tests and a permanent Phase 4.1B-A source verifier.

# Slimefun Legacy Paper/Purpur Compatibility Maintenance — Round 2

- Replaced internal Paper damage-event constructors with supported `DamageSource` damage calls.
- Updated WorldEdit vector coordinate access to the current `x()`, `y()`, and `z()` record methods.
- Retained legacy `Config`, ticker, energy and BlockStorage JVM signatures for existing addons.
- Added compatibility-bridge regression tests and a permanent source verifier.
- Annotated legacy SQL constants correctly and enabled Java 25 native access for SQLite tests.

# Slimefun Legacy Core-Correctness Audit

- Corrected multiblock crafting to consume each recipe cell's declared amount.
- Dispatches clicks to every matching multiblock so overlapping structures no longer swallow interactions.
- Made Energy Regulator hologram/network ticks use the synchronized owner-thread path.
- Migrates legacy integer Multi Tool modes to stable Slimefun item-ID storage.
- Logs missing and invalid backpack identities instead of silently refusing to open them.
- Added a permanent audit verifier and documented which Slimefun 5/United fixes were already present, not applicable or intentionally deferred.

# Slimefun Legacy Phase 4.1A — Core GUI Machine Input Filling

- Added a native **Fill Machine Inputs** button to supported core `AContainer` recipes.
- Added one-set and maximum-safe-set transfer modes.
- Added exact placed-machine validation, protection checks and Folia region ownership checks.
- Added transactional player/machine inventory planning with rollback on commit failure.
- Added machine-viewer checks and ticker coordination to prevent races with processing or open menus.
- Preserved output slots, upgrades, controls, energy usage, processing time and Cargo behavior.
- Added partial-stack, duplicate-input, unrelated-item and maximum-set regression coverage.

# Slimefun Legacy Phase 4 — Cheat Guide Hierarchy Correction

- Changed `/sf cheat` to mirror the normal guide's real category icons, ordering and nested hierarchy.
- Removed the generated generic chest-folder view that hid or misgrouped some addons.
- Evaluates addon `FlexItemGroup` visibility using normal guide rules while retaining cheat-mode item spawning.
- Applied the correction to both the enhanced cheat guide and classic fallback.

# Slimefun Legacy Phase 4 — Universal Machine Recipes

- Added a supported addon-facing machine recipe provider API.
- Added normalized recipe, ingredient, alternative-choice, layout, timing and energy metadata models.
- Added native recipe browsing for `AContainer` machines and existing `RecipeDisplayItem` implementations.
- Added compatibility discovery for addons exposing public `getMachineRecipes()` methods.
- Migrated FastMachines recipe browsing to the universal provider system without private reflection.
- Added defensive recipe data copying and provider priority ordering.
- Added Phase 4 verification and API regression tests.

# Slimefun Legacy Folia Support — Phase 1


## Paper/Purpur Compatibility Maintenance

- Added the audited `.gugu-upstream-base` marker for safe Gugu update merges.
- Guarded the modern limited-crafting gamerule read used by Vanilla Auto-Crafters.
- Added a plain-text fallback to `/sf versions` so environment reports are never silently lost.
- Prevented profiler reports from mixing with or emptying after a newer profiling cycle starts.
- Added one complete Legacy verification command and Paper/Purpur-specific source invariants.

- Route every Folia machine tick to the region that owns its machine chunk while preserving the historical Paper ticker path.
- Prevent overlapping machine cycles and serialize shared addon `BlockTicker` instances across Folia regions.
- Make `BlockTicker` unique-cycle state safe under concurrent region dispatch.
- Add location- and entity-owned callback paths for storage, backpacks, inventories, and custom events.
- Retain Cargo and energy topology while restricting operations to nodes owned by the regulator's current Folia region.
- Add Folia startup warnings, static verification, a concurrency regression test, and detailed staging guidance.
- Keep Folia experimental; transactional cross-region Cargo and energy remain future work.

# Slimefun Legacy Fourth Maintenance Release

- Added Folia-safe concurrent state for Soulbound recovery, Elytra impact grace, and Slimefun bow projectiles.
- Added entity-owned cleanup with retirement callbacks for transient player and projectile state.
- Added a keep-inventory duplication guard and ItemStack snapshots for Soulbound recovery.
- Migrated limited-crafting, potion metadata, and food-level events to current Paper APIs.
- Made profiler averages zero-safe and independently resettable.
- Updated `/sf versions` for Java 21+ and Paper/Folia scheduler reporting.
- Declared Folia support and added Part 4 verification, tests, and release CI.

# Table of contents

## Gugu upstream sync safety

- Added a strict upstream health gate using Check Runs, Actions workflow runs, and commit statuses.
- Scheduled syncs now stop before merging when upstream checks are failed, pending, unavailable, or missing.
- Manual draft testing requires both `override_upstream_health` and a written `override_reason`.
- Replaced destructive upstream tree replacement with a history-preserving Git merge.
- Upstream updates now open a draft pull request and never auto-merge.
- Merge conflicts stop without overwriting fork files.
- English, Part 2, API annotation, formatting, test, and build checks gate each sync.
- Added schema-v3 database migration and rollback guidance for the July 2026 Gugu storage update.

- [Legacy Third Maintenance Release (26 Jul 2026)](#legacy-third-maintenance-release-26-jul-2026)
- [Legacy Second Maintenance Release (26 Jul 2026)](#legacy-second-maintenance-release-26-jul-2026)
- [Legacy Stability Release 1 Hotfix 1 (26 Jul 2026)](#legacy-stability-release-1-hotfix-1-26-jul-2026)
- [Legacy Stability Release 1 (26 Jul 2026)](#legacy-stability-release-1-26-jul-2026)
- [Release Candidate 38 (TBD)](#release-candidate-38-tbd)
- [Release Candidate 37 (25 Feb 2024)](#release-candidate-37-25-feb-2024)
- [Release Candidate 36 (20 Dec 2023)](#release-candidate-36-20-dec-2023)
- [Release Candidate 35 (07 Jul 2023)](#release-candidate-35-07-jul-2023)
- [Release Candidate 34 (20 Jun 2023)](#release-candidate-34-20-jun-2023)
- [Release Candidate 33 (07 Jan 2023)](#release-candidate-33-07-jan-2023)
- [Release Candidate 32 (26 Jun 2022)](#release-candidate-32-26-jun-2022)
- [Release Candidate 31 (14 Mar 2022)](#release-candidate-31-14-mar-2022)
- [Release Candidate 30 (31 Dec 2021)](#release-candidate-30-31-dec-2021)
- [Release Candidate 29 (07 Nov 2021)](#release-candidate-29-07-nov-2021)
- [Release Candidate 28 (06 Sep 2021)](#release-candidate-28-06-sep-2021)
- [Release Candidate 27 (03 Sep 2021)](#release-candidate-27-03-sep-2021)
- [Release Candidate 26 (20 Jul 2021)](#release-candidate-26-20-jul-2021)
- [Release Candidate 25 (20 Jun 2021)](#release-candidate-25-20-jun-2021)
- [Release Candidate 24 (03 Jun 2021)](#release-candidate-24-03-jun-2021)
- [Release Candidate 23 (19 May 2021)](#release-candidate-23-19-may-2021)
- [Release Candidate 22 (18 Apr 2021)](#release-candidate-22-18-apr-2021)
- [Release Candidate 21 (14 Mar 2021)](#release-candidate-21-14-mar-2021)
- [Release Candidate 20 (30 Jan 2021)](#release-candidate-20-30-jan-2021)
- [Release Candidate 19 (11 Jan 2021)](#release-candidate-19-11-jan-2021)
- [Release Candidate 18 (03 Dec 2020)](#release-candidate-18-03-dec-2020)
- [Release Candidate 17 (17 Oct 2020)](#release-candidate-17-17-oct-2020)
- [Release Candidate 16 (07 Sep 2020)](#release-candidate-16-07-sep-2020)
- [Release Candidate 15 (01 Aug 2020)](#release-candidate-15-01-aug-2020)
- [Release Candidate 14 (12 Jul 2020)](#release-candidate-14-12-jul-2020)
- [Release Candidate 13 (16 Jun 2020)](#release-candidate-13-16-jun-2020)
- [Release Candidate 12 (27 May 2020)](#release-candidate-12-27-may-2020)
- [Release Candidate 11 (25 Apr 2020)](#release-candidate-11-25-apr-2020)
- [Release Candidate 10 (28 Mar 2020)](#release-candidate-10-28-mar-2020)
- [Release Candidate 9 (07 Mar 2020)](#release-candidate-9-07-mar-2020)
- [Release Candidate 8 (06 Mar 2020)](#release-candidate-8-06-mar-2020)
- [Release Candidate 7 (06 Mar 2020)](#release-candidate-7-06-mar-2020)
- [Release Candidate 6 (16 Feb 2020)](#release-candidate-6-16-feb-2020)
- [Release Candidate 5 (09 Feb 2020)](#release-candidate-5-09-feb-2020)
- [Release Candidate 4 (06 Jan 2020)](#release-candidate-4-06-jan-2020)
- [Release Candidate 3 (21 Nov 2019)](#release-candidate-3-21-nov-2019)
- [Release Candidate 2 (29 Sep 2019)](#release-candidate-2-29-sep-2019)
- [Release Candidate 1 (26 Sep 2019)](#release-candidate-1-26-sep-2019)


## Legacy Third Maintenance Release (26 Jul 2026)

#### Additions
* Add schema 3 with versioned binary ItemStack storage for backpack, block, and universal inventories.
* Add legacy Bukkit object-stream and ItemMeta/skull-profile migration compatibility.
* Add public storage descriptor, SQLite migration, transaction rollback, real-database, and missing-world tests.

#### Changes
* Keep legacy String storage APIs as deprecated Base64 views while core adapters retain native binary values.
* Use MEDIUMBLOB on MySQL, BLOB on SQLite, and BYTEA on PostgreSQL.
* Publish the database version only after migration work succeeds and restore connection auto-commit state afterward.
* Resolve universal block locations lazily when their worlds become available.

#### Fixes
* Correct PostgreSQL universal inventory slot types and metadata insertion syntax.
* Escape apostrophes in generated SQL text values.
* Keep migration retries safe when a binary column was converted before a row-level failure.

#### Upgrade warning
* Back up every Slimefun database before first startup. Restore that backup before any downgrade to a schema-2 build.


## Legacy Second Maintenance Release (26 Jul 2026)

#### Fixes
* Replace the hardcoded `connectstate:` connector message with localized `Connected: ✔` / `Connected: ✕` output.
* Correct long-capacity energy writes to use long capacity and charge accessors.
* Correct slow SQL elapsed-time units and GitHub polling interval units.
* Keep recipe-choice, armor, radiation, research, teleport, machine, reactor, rune, and storage work on scheduler-owned threads.

#### Additions
* Add tracked global, location, entity, and asynchronous scheduling abstractions with centralized shutdown cancellation.
* Preserve legacy `BukkitTask` return compatibility through a scheduler-backed adapter.
* Add storage-neutral `BlockTicker` and resolved-container long-energy overloads without removing legacy signatures.
* Complete `@SlimefunAPI` / `@SlimefunInternal` classification for the binary-compatibility package boundaries.
* Add fail-closed protection compatibility policy and server-independent tests.
* Add static Part 2 verification and a dedicated second-maintenance release workflow.

#### Changes
* Migrate direct core scheduler usage behind the scheduler service, with explicit location/entity ownership where known.
* Move chat and action-bar handling to current Paper/Adventure APIs.
* Make cross-owner runtime state concurrent where maintenance tasks can execute on separate region or entity schedulers.

#### Compatibility
* Retain legacy ticker, storage, integer-energy, and `Slimefun.runSync(...)` descriptors for existing addons.
* Keep Paper tick timing while enabling region/entity scheduler routing on Folia-capable servers.


## Legacy Stability Release 1 Hotfix 1 (26 Jul 2026)

#### Fixes
* Keep `SlimefunChunkDataLoadEvent` on the primary server thread on Paper 26.2.
* Stop the Item Doctor from calling the async chunk loader during `ChunkLoadEvent`.
* Repair Slimefun machine inventories after block storage finishes loading, with bounded retries.
* Protect GEO and addon callers of `getChunkDataAsync` from the same synchronous-event exception.


## Legacy Stability Release 1 (26 Jul 2026)

#### Additions
* Add `/slimefun doctor` and `/sf doctor` for safe English item presentation scanning and repair.
* Add batched coverage for online inventories, loaded storage, dropped items, machines, nested containers, and all database backpacks.
* Add automatic repair hooks for player joins, inventory opens, chunk loads, and item pickup.
* Add release workflow packaging, checksums, addon compatibility CI, and public API compatibility reporting.

#### Changes
* Preserve charge, limited-use counts, spawner types, Soulbound state, Knowledge Tome owners, backpack identity, and safely mapped addon values during presentation repair.
* Enumerate and maintain database backpacks without replacing or evicting a backpack opened concurrently by a player.
* Retain the Cargo #1223 topology/allocation optimization and profiler-accounting corrections.

#### Fixes
* Reject duplicate and re-entrant backpack open requests and clean reservations after disconnects or failures.
* Add clean-shutdown tracking and pending-write status.
* Isolate repeatedly failing machine tickers with a cooldown circuit breaker and administrator retry controls.
* Skip unknown, untranslated, malformed, or ambiguous item state instead of guessing or destructively rebuilding it.

## Release Candidate 38 (TBD)

## Release Candidate 37 (25 Feb 2024)

#### Additions
* (API) Introduce SlimefunItemRegistryFinalizedEvent (#4099)
* Add update warning to /sf versions (#4096)
* Add new analytics service (#4067)

#### Changes
* Allow blocks to be dropped while in creative mode (#3934)
* Storage rewrite - Phase 1 (#4065)
* Temporarily disable senstive blocks check (#4077)
* Update MockBukkit to 1.20.4 along with existing tests (#4086)
* Move PlayerProfile saving off the main thread (#4119)

#### Fixes
* Fix contributor head being pullable (#4072)
* Fix backpack IDs not incrementing (#4081)
* Fix inventory being used when Slimefun block is broken (#4088)
* Fix items not being able to be placed on ancient altar (#4094)
* Update dough to fix item stacking issue (#4100)
* Fix slimefun block turning into a vanilla block if there are viewers (#4101)
* Fixes #4123 - Coal Generator will no longer be locked after researching (#4124)
* Fixes exhaustion when loading large profiles (#4127)
* Fixes guide search when using colored chat (#4125)
* Fix dupe glitch with backpacks (#4134)

## Release Candidate 36 (20 Dec 2023)

#### Additions
* Added e2e testing to PRs to better ensure compatibility
* Added compatibility to 1.20+
* Added rainbow armor
* Added grace periods to radiation
* Added cherry log to android woodcutter
* Added blackstone recipes to Grindstone and Ore Crusher (#3912)
* Added Enchanted Golden Apple recipe (suggestion #2147 from punished_Garett) (#3591)
* Added new flags for timings (#3246)
* Added yaw to GPS Waypoints
* (API) Add MultiBlockCraftEvent (#3928)
* (API) Add TalismanActivateEvent (#4045)

#### Changes
* Changed the radiation system
* Removed backwards compatibility
* (API) Improve performance for clearAllBlockInfoAtChunk
* Change Energized GPS Transmitter values to follow the pattern of previous tiers (#3915)
* Allowed the sword of beheading to drop piglin heads
* Improvements to BlockStorage handling (#3911)
* Moved builds to https://blob.build

#### Fixes
* Fix #3444
* Fix #3507
* Fix possible enchantment duplication
* Fix Different Time of Pan Recipes
* Fix some of the reported blocks not working (#3848)
* Fix Soulbound Runes not working (#3932)
* Fix #3836
* Fix unable to craft soulbound backpack with woven backpack with id (#3939)
* Fix getting radiated when not supposed to
* Fix geo miner voiding resources
* Fix sensitive blocks attached to sf blocks not dropping (1.19+)
* Fix breaking sf block with not unlocked item duping contents (#3976)
* Fix the case of SlimefunItem#itemhandlers
* Fix taking damage on head collision while wearing elytra cap (#3760)
* Fix heads showing as steve (#4027)
* Fix grappling hook not working due to bat dying (#3926)
* Fix freezer material
* Fix auto update
* Fix rate limiting issues (#4042)
* Fix orebfuscator plugin with blocks when gold panning (#3921)

## Release Candidate 35 (07 Jul 2023)

#### Additions
* Added `sounds.yml` file to configure sound effects for Slimefun
* Added preview builds to the repo, PRs will now have a build which testers can use
* (API) Added SlimefunBlockBreakEvent and SlimefunBlockPlaceEvent events for plugins/addons to implement
* (API) Added an efficient way to clear BlockStorage within a chunk - BlockStorage.clearAllBlockInfoAtChunk
* (API) Added DistinctiveItem, a way to distinguish your item with more than just ID
* (API) Added ExternallyInteractable, a way for addons to define "interactions" for blocks

#### Changes
* Moved all sound effects to the new sound system

#### Fixes
* Fixed recipe shift in multiblocks when items are disabled (#3286)
* Fixed backpack dupe within cargo (#3379)

## Release Candidate 34 (20 Jun 2023)
https://thebusybiscuit.github.io/builds/TheBusyBiscuit/Slimefun4/stable/#34

#### Additions
* Added "Cobbled Deepslate -> Gravel" recipe to the Grind Stone
* Added "Cobbled Deepslate -> Sand" recipe to the Ore Crusher
* (API) Added EnergyNet#getGenerators()
* (API) Added EnergyNet#getCapacitors()
* (API) Added EnergyNet#getConsumers()
* Added Bamboo as a fuel type for Tier 1 Androids
* Added "Basalt -> Blackstone" recipe to the Grind Stone
* Added a way to automate salt with the Ore Washer
* Added compatibility for Minecraft 1.20

#### Changes
* Removed 1.14.* and 1.15.* support
* The Climbing Pick now also works on:
  * Calcite
  * Deepslate
  * Dripstone blocks
  * Smooth Basalt
  * Tuff
  * Clay
  * Skulk
* Lumber Axe no longer works when shifting

#### Fixes
* Fixed #3741
* Fixed #3724
* Fixed #3462
* Fixed #3758
* Fixed #3701
* Fixed #3361
* Fixed #3254
* Fixed #3443
* Fixed #3511
* Fixed #3524
* Fixed #3657
* Fixed #3768
* Fixed #3414

## Release Candidate 33 (07 Jan 2023)
https://thebusybiscuit.github.io/builds/TheBusyBiscuit/Slimefun4/stable/#33

#### Additions
* (API) Added Tinted Glass to "GLASS_BLOCKS" tag
* (API) Added "WOOL_CARPETS" tag (for compatibility across MC 1.19/1.18 tags)
* Added a new language: Persian
* Added a new language: Romanian
* (API) Added a method for item groups to allow addons to choose if they want to allow items from other addons
* Added a new option to Eletric Gold Pans: "override-output-limit"
* Added "Mud -> Clay" recipe to the Auto Drier
* Added a third tier for Freezers
* Added Glow Berry Juice

#### Changes
* Tree Growth Accelerators can now actually cause the Tree to fully grow (1.17+ only)
* Slimefun now requires Java 16
* "Connected / Not connected" messages for cargo nodes are now sent via the actionbar
* "/sf stats" can no longer be used if researching is disabled
* "/sf research" can no longer be used if researching is disabled
* Removed the Hercules Pickaxe from Slimefun
* If CS-CoreLib is present, Slimefun will disable itself (previously it would just error)

#### Fixes
* Fixed #3597
* Fixed an issue related to "Bee Wings"
* Fixed #3573
* Fixed "round-robin" mode for cargo networks being very unreliable
* Fixed #3664
* Fixed #3651
* Fixed #3677
* Fixed #3705
* Fixed BlockPlacer being able to place disabled items

## Release Candidate 32 (26 Jun 2022)
https://thebusybiscuit.github.io/builds/TheBusyBiscuit/Slimefun4/stable/#32

#### Additions
* Added Organic Food for Seagrass
* Added Organic Fertilizer for Seagrass
* Added compatibility for Minecraft 1.19

#### Changes
* Removed support for ChestTerminal

#### Fixes
* Fixed #3445
* Fixed #3504
* Fixed #3534
* Fixed #3538
* Fixed #3548
* Fixed an issue with machines being placed below y=0

## Release Candidate 31 (14 Mar 2022)
https://thebusybiscuit.github.io/builds/TheBusyBiscuit/Slimefun4/stable/#31

#### Additions
* Added Armored Jetpack
* Added Cocoa Beans as a fuel type for the Bio-Generator
* Added Beetroots and Beetroot seeds as fuel types for the Bio-Generator
* Added small and big dripleaves as fuel types for the Bio-Generator
* Added Glow Berries as a fuel type for the Bio-Generator
* Added Glow Lichen as a fuel type for the Bio-Generator
* Added Spore Blossom as a fuel type for the Bio-Generator
* Added a new item setting for Freezers to allow them to use a 9:1 "vanilla" ratio instead of 1:1 (1:1 by default, like before)
* (API) Added `PlayerProfile#hasUnlockedEverything()` to check if a player has unlocked all researches
* (API) Added `Research#getUnlocalizedName()`
* Added support for the plugin "HuskTowns"
* Added support for Minecraft 1.18.2
* You can now pick up Slimefun blocks in creative mode using the middle mouse button
* `/sf search` no longer shows items in hidden item groups (can be overidden by a config setting)
* Fluid Pumps can now fill bottles with water
* (API) Added Shulker boxes to `ColoredMaterial` enum

#### Changes
* (API) `BiomeMapParser` is now `public`
* (API) `BiomeMap.fromJson` now allows you to specify if you want the BiomeMap to be parsed leniently
* Some translation updates

#### Fixes
* Fixed #3390
* Fixed research issues for vanilla items, e.g. Trident or Totem of Undying
* Fixed #3368
* Fixed #1315
* Fixed #3400
* Fixed rare issue where Slimefun would not load at all
* Fixed #3429
* Fixed "LogBlock" integration
* Fixed "Lands" integration
* Fixed #3133
* Fixed #3483
* Fixed #3469
* Fixed #3476
* Fixed #3487
* Fixed #3336 (again)

## Release Candidate 30 (31 Dec 2021)
https://thebusybiscuit.github.io/builds/TheBusyBiscuit/Slimefun4/stable/#30

#### Additions
* Added a ton of wiki links to the guide
* (API) Added "GRAVITY_AFFECTED_BLOCKS" tag
* (API) Added "Biome-Maps" for more in-depth GEO resource configuration (developers only for now)
* (API) Added some utility methods for Biome-Maps
* Added support for 1.18
* Added Talisman of Farmer

#### Changes
* GEO resource distributions have been slightly adjusted
* Salt can now also generate in the Nether (as a GEO resource)

#### Fixes
* Crimson and Warped Pressure Plates are now properly recognized as pressure plates
* Fixed #3336
* (API) Fixed `Parachute` constructor parameter being ignored
* Fixed #3385
* Fixed (Easter) Apple Pie recipe yielding (Christmas) Apple Pies

## Release Candidate 29 (07 Nov 2021)
https://thebusybiscuit.github.io/builds/TheBusyBiscuit/Slimefun4/stable/#29

#### Additions
* Added support for deepslate ores and copper with the Hercules' Pickaxe
* The Electric Crucible now also accepts Netherrack
* The Electric Crucible now also accepts Stone
* Added the ability to shift-click in the Cheat Sheet menu
* Added the ability to break blocks normally with a Lumber Axe when sneaking
* Added an option to allow Solar Generators to operate in "night-mode" in other dimensions
* Added `/sf debug <test case>` (This allows server owners to get more in-depth logging which they can forward to developers for better bug/lag investigations)
* Added an option to disable data backups on disable

#### Changes
* Massive performance improvements for Cargo networks
* (API) `SolarGenerator` has a new constructor to accept capacity

#### Fixes
* Fixed #3218
* Fixed #3241
* Fixed #3248
* Fixed #3273
* Fixed an exploit regarding the Smithing Table
* Fixed #3265
* Fixed #3264
* Fixed extreme knockback caused by the Explosive Bow
* Fixed #3313
* Fixed smithing table issue on 1.15 and lower

## Release Candidate 28 (06 Sep 2021)
https://thebusybiscuit.github.io/builds/TheBusyBiscuit/Slimefun4/stable/#28

#### Fixes
* Fixed Metrics
* Fixed some naming conventions and localization keys for RC-27

## Release Candidate 27 (03 Sep 2021)
https://thebusybiscuit.github.io/builds/TheBusyBiscuit/Slimefun4/stable/#27

### **Breaking Changes (API)**
This RC brings a lot of breaking changes to the API. For more info on why we did this and what happened [please refer to our PSA](https://github.com/Slimefun/Slimefun4/pull/3139)

* Category has been renamed to ItemGroup.
* All Category / ItemGroup variants have been relocated to `io.github.thebusybiscuit.slimefun4.api.items.groups`
* The SlimefunItem class has been relocated to `io.github.thebusybiscuit.slimefun4.api.items`
* The SlimefunItemStack class has been relocated to `io.github.thebusybiscuit.slimefun4.api.items`
* The ItemHandler class has been relocated to `io.github.thebusybiscuit.slimefun4.api.items`
* The RecipeType class has been relocated to `io.github.thebusybiscuit.slimefun4.api.recipes`
* Research classes have been moved from `io.github.thebusybiscuit.slimefun4.core.researching` to `io.github.thebusybiscuit.slimefun4.api.researches`
* The main class `SlimefunPlugin` was renamed to `Slimefun`
* CS-CoreLib2 was removed and replaced by dough

#### Additions
* A couple more items have their wiki page linked ingame now
* Added Orebfuscator compatibility
* You can now "sneak + left click" to only break one block at a time when using an explosive pickaxe or shovel
* The luck effect from Enhanced Furnaces now also applies to Raw Ore
* Locked items will now show the category in which they should be unlocked from
* Added 4 "Amethyst Shard -> 1 Amethyst Block" recipe to Electric Press
* Added 9 "Copper Ingot -> 1 Copper Block" recipe to Electric Press
* Added 9 "Raw Iron -> 1 Raw Iron Block" recipe to Electric Press
* Added 9 "Raw Gold -> 1 Raw Gold Block" recipe to Electric Press
* Added 9 "Raw Copper -> 1 Raw Copper Block" recipe to Electric Press

#### Changes
* Copper wire can no longer be placed down
* Slimefun chains can no longer be placed down
* (API) FlexCategories can now also appear in non-survival Slimefun guides
* Display items from Ancient Altars should no longer despawn so easily/fast
* Research message was modified to also show the category of the item

#### Fixes
* Fixed #3164
* Fixed #3177
* Fixed unbreakable Flint and Steel still being damaged in Ignition Chambers
* Fixed #2677
* Fixed Auto-Disenchanter exploit using mcMMO's "super ability" tools
* Fixed #3190
* Fixed #3203
* Fixed #3225
* Fixed #3206
* Fixed androids not respecting Worldborders
* Fixed Ender Lumps showing an incorrect recipe in the guide

## Release Candidate 26 (20 Jul 2021)
https://thebusybiscuit.github.io/builds/TheBusyBiscuit/Slimefun4/stable/#26

#### Additions
* Diamonds can now be ground into Carbon using a Grind Stone
* Deepslate ores can now be doubled using an Ore Crusher
* Tridents can now be crafted
* The Industrial Miner can now mine up to the minimum world limit (previously only until y=0)
* (API) Added SlimefunItemSpawnEvent and ItemSpawnReason
* Added "Amethyst Block -> 4 Amethyst Shards" recipe to the Grind Stone
* Added an option to the IndustrialMiner to configure if they can mine deepslate ores
* (API) Added `LimitedUseItem`

#### Changes
* The Industrial Miner now properly drops raw ores in 1.17+ instead of ore blocks

#### Fixes
* Fixed #2966
* Fixed Auto-Crafters bypassing the `doLimitedCrafting` gamerule
* Fixed "Talisman of Anvil" having issues with off-hand items
* Fixed #3136

## Release Candidate 25 (20 Jun 2021)
https://thebusybiscuit.github.io/builds/TheBusyBiscuit/Slimefun4/stable/#25

#### Additions
* Added "4 Charcoal -> 1 Coal" recipe to the Compressor
* Added a startup warning for when CS-CoreLib is still installed
* (API) Added WeaponUseHandler
* Added support for Minecraft 1.17
* Added "Deepslate -> Lava" recipe to the Crucible
* Added "Cobbled Deepslate -> Lava" recipe to the Crucible
* Added "Tuff -> Lava" recipe to the Crucible
* Added "Copper Ingot -> Copper Dust" recipe to the Ingot Pulverizer
* Added Goats as a milk source for the Produce Collector
* Added "Raw Iron -> Iron Dust" recipe to the Ore Crusher
* Added "Raw Gold -> Gold Dust" recipe to the Ore Crusher
* Added "Raw Copper -> Copper Dust" recipe to the Ore Crusher
* Debug Fish can now read Slimefun Tags for vanilla blocks
* The Icy Bow now gives a freezing effect on 1.17

#### Changes
* (API) Removed `SlimefunItem#getID()` (renamed to `SlimefunItem#getId()`)
* (API) Removed AsyncGeneratorProcessCompleteEvent
* (API) Removed AsyncMachineProcessCompleteEvent
* (API) Removed AsyncReactorProcessCompleteEvent

#### Fixes
* Fixed #3105
* Fixed #3116

## Release Candidate 24 (03 Jun 2021)
https://thebusybiscuit.github.io/builds/TheBusyBiscuit/Slimefun4/stable/#24

#### Additions
* (API) Added AsyncMachineOperationFinishEvent
* The speed of the Ancient Altar can now be configured in the `Items.yml` file
* The message "You do not have enough knowledge to understand this" now includes the name of the item you need to research.

#### Changes
* (API) Refactored "Machine Process" API
* (API) Deprecated AsyncGeneratorProcessCompleteEvent
* (API) Deprecated AsyncMachineProcessCompleteEvent
* (API) Deprecated AsyncReactorProcessCompleteEvent
* Error-Reports now show the date and time they were generated at
* Some performance optimizations to Cargo networks

#### Fixes
* Fixed #3064
* Fixed #2964
* Fixed #2979
* Fixed a permissions issue with `/sf charge`
* Fixed #3053
* Fixed #3075
* Fixed recipe types showing missing string message
* Fixed #3084
* Fixed #3085
* Fixed #3088
* Fixed #3087
* Fixed #3091
* Fixed #3086
* Fixed #3093
* Fixed #3095

## Release Candidate 23 (19 May 2021)
https://thebusybiscuit.github.io/builds/TheBusyBiscuit/Slimefun4/stable/#23

#### Additions
* Added "Quartz Block -> 4 Quartz" recipe to Grind Stone
* Added "8 Coal Blocks -> 9 Carbon" recipe to Compressor
* Added "8 Coal Blocks -> 9 Carbon" recipe to Carbon Press
* Added "Magical Lump Tier 2 -> 4 Magical Lump Tier 1" recipe to Grind Stone
* Added "Magical Lump Tier 3 -> 4 Magical Lump Tier 2" recipe to Grind Stone
* Added "Ender Lump Tier 2 -> 4 Ender Lump Tier 1" recipe to Grind Stone
* Added "Ender Lump Tier 3 -> 4 Ender Lump Tier 2" recipe to Grind Stone
* Added Tier 2 Auto-Enchanter
* Added Tier 2 Auto-Disenchanter
* (API) Added AsyncAutoEnchanterProcessEvent
* (API) Added Category#setTier() to modify a category's position in the guide
* Added the ability to disable auto (dis)enchanting with a lore - `use-ignored-lores` & `ignored-lores` in Items.yml
* Added an option to turn off the "researching animation" in the `config.yml`
* Added the option to turn off the "researching animation" within your Slimefun Guide
* Added Portable Teleporter

#### Changes
* Renamed "Solar Panel" to "Photovoltaic Cell" to avoid confusions with solar generators
* Photovoltaic Cells can no longer be placed
* Batteries can no longer be placed
* Tin Cans can no longer be placed
* Magical Glass can no longer be placed
* (API) Removed deprecated "SlimefunBlockHandler"
* Removed Automated Crafting Chamber
* Memory and performance improvements for Cargo and Energy networks

#### Fixes
* Fixed #2987
* Fixed #2989
* Fixed #2977
* Fixed #2999
* Fixed #2593
* Fixed #2937
* Fixed #2927
* Fixed #3007
* Fixed #3012
* Fixed #3013
* Fixed #3027
* Fixed #2978
* Fixed #3041
* Fixed #3036
* Possibly fixed #2927
* Fixed #3060

## Release Candidate 22 (18 Apr 2021)
https://thebusybiscuit.github.io/builds/TheBusyBiscuit/Slimefun4/stable/#22

#### Additions
* Added Vanilla Auto-Crafter
* Added Enhanced Auto-Crafter
* Added "Smart-Filling" mode to Cargo Input nodes
* Added "Netherite Ingot -> Netherite Block" recipe to Electric Press
* Added "Slimeballs -> Slime Block" recipe to Electric Press
* Added Armor Forge Auto-Crafter
* Auto-Crafters can now be turned on and off
* Added Produce Collector to automate Milk and Mushroom Stew
* Added a new message when constructing a Multiblock successfully
* Added Crafting Motor
* Block Placers can now place down cake
* Added support for the "FunnyGuilds" plugin
* Added "magma cream -> slime ball" recipe to the Freezer
* Added "2 magma blocks -> slime block" recipe to the Freezer
* Added configurable enchantment level limit for both auto enchanter and auto disenchanter
* (API) Added AutoEnchantEvent

#### Changes
* Changed item order in guide for the Villager Rune and Nether Goo (All runes are now grouped together)
* Ancient Pedestals can now be found under "Magical Gadgets"
* Removed all functionality from the old Automated Crafting Chamber
* Changed Cargo Motor texture
* Lowered "Magma block -> Sulfate" recipe to only require 1 magma block
* Small performance improvements

#### Fixes
* Fixed #1161
* Fixed #2862
* Fixed #2887
* Fixed items getting deleted when breaking enhanced furnaces
* Fixed #2895
* Fixed #2896
* Fixed #2899
* Fixed #2906
* Fixed #2903
* Fixed #2913
* Fixed #2914
* Fixed Auto-Crafters swallowing buckets when crafting cake
* Fixed Multimeter not working on Auto-Crafters
* Fixed #2650
* Fixed Slimefun items applying damage to items with an `unbreakable` tag
* Fixed #2930
* Fixed #2926
* Fixed Grappling Hook vanishing in creative mode
* Fixed #2944
* Fixed #2837
* Fixed #2942

## Release Candidate 21 (14 Mar 2021)
https://thebusybiscuit.github.io/builds/TheBusyBiscuit/Slimefun4/stable/#21

#### Additions
* Nether Wart Blocks can now be turned into Nether Warts using a Grind Stone
* Added an option to allow Talismans to send their notifications via the Actionbar
* (API) Added support for nested Categories
* (API) Added ExplosiveToolBreakBlocksEvent
* Added an option to enable/disable water in the nether via a crucible
* /sf versions now shows the Java version and some useful tooltips

#### Changes
* Deprecated Automatic Crafting Chamber
* Performance Improvements and Optimizations for Cobblestone/Stone/Basalt generators and mining androids
* Androids operating on a Cobblestone/Stone/Basalt generator now work faster
* (API) Improvements to the BlockBreakHandler
* (API) Deprecated SlimefunBlockHandler
* (API) Improved ItemSetting API and error handling

#### Fixes
* Fixed #2794
* Fixed #2793
* Fixed #2809
* Fixed a small exception which gets thrown when Slimefun is disabled due to an invalid environment
* Fixed #2810
* Fixed #2804
* Fixed #2817
* Fixed exceptions with inventories not being printed using the logger of the addon that caused it
* Fixed #2818
* Fixed a duplication glitch with the Woodcutter Android
* Fixed #2839
* Fixed #2849
* Fixed #2851
* Fixed #2852
* Fixed some issues with the Book Binder
* Fixed #2805
* Fixed #2861
* Fixed #2856
* Fixed #2876
* Fixed #2877
* Fixed #2878
* Fixed Mining Androids being broken
* Fixed #2883

## Release Candidate 20 (30 Jan 2021)
https://thebusybiscuit.github.io/builds/TheBusyBiscuit/Slimefun4/stable/#20

#### Additions
* Added a new language: Bulgarian
* Added a new language: Hebrew
* (API) Added AsyncProfileLoadEvent
* Added Talisman of the Wise
* Added Book Binder
* Added Tier 3 Electric Ore Grinder

#### Changes
* Massive performance improvements to holograms/armorstands
* Slimefun no longer requires CS-CoreLib to be installed

#### Fixes
* Fixed elevator floor order
* Fixed "block-explosions" (e.g. beds in Nether) not properly respecting explosion-resistant blocks
* Fixed #2560
* Fixed #2449
* Fixed #2511
* Fixed #2636
* Fixed a threading issue related to BlockStates and persistent data
* Fixed an error when the server was shutting down
* Fixed #2721
* Fixed #2662
* Fixed #2728
* Fixed some backpack opening issues
* Fixed Infused Hopper picking up items with a max pickup delay
* Fixed duplication issues related to holograms/armorstands
* Fixed #2754
* Fixed machines not respecting max size from inventories
* Fixed #2761
* Fixed #2460
* Fixed #2760
* Fixed #2771
* Fixed placeholders that did not get loaded yet not having a label
* Fixed #2679

## Release Candidate 19 (11 Jan 2021)
https://thebusybiscuit.github.io/builds/TheBusyBiscuit/Slimefun4/stable/#19

#### Additions
* Added Bee Armor (1.15+ only)
* (API) Added AndroidFarmEvent

#### Changes
* Performance optimizations for Cargo networks
* Removed an old version of bStats
* General performance improvements
* CraftBukkit is officially no longer supported, Slimefun will now be disabled on old builds of CraftBukkit
* Removed the deprecated ItemManipulationAPI for BlockMenus
* Removed the "Written Book" variant of the Slimefun Guide
* The Elevator has an Inventory menu now

#### Fixes
* Fixed a couple of compatibility issues with ItemsAdder
* Fixed #2575
* Fixed ghost blocks to some extent (ghost blocks will now drop and be replaced)
* Fixed #2636 (hotfix)
* Fixed #2647
* Fixed #2664
* Fixed #2655
* Fixed /sf timings --verbose not working correctly
* Fixed #2675

## Release Candidate 18 (03 Dec 2020)
https://thebusybiscuit.github.io/builds/TheBusyBiscuit/Slimefun4/stable/#18

#### Additions
* The Smelters Pick now also works on Ancient Debris
* (API) Added PlayerPreResearchEvent
* Added a config option to disable network visualizations
* (API) Added CoolerFeedPlayerEvent
* Added a config option to delete excess cargo network items
* Added an item setting to configure the Wind Staff velocity
* Added an item setting to the Infused Hopper to toggle it with redstone
* Added an item setting to prevent Reinforced Spawners from being changed by Spawn Eggs
* Added 4 bricks -> 1 brick block recipe to the Electric Press

#### Changes
* Removed 1.13 support
* Cooling Units can no longer be placed down
* Heating Coils can no longer be placed down
* Electric Motors can no longer be placed down
* Cargo Motors can no longer be placed down
* Magnets can no longer be placed down
* Electromagnets can no longer be placed down
* Performance improvements to Cargo network visualizations
* General performance improvements
* Improved performance for radioactive items
* Memory/GC improvements for the profiler
* Performance improvements for the Fluid Pump
* Removed EmeraldEnchants integration
* Memory and performance improvements for ticking blocks

#### Fixes
* Fixed #2448
* Fixed #2470
* Fixed #2478
* Fixed #2493
* Fixed a missing slot in the contributors menu
* Fixed color codes in script downloading screen
* Fixed #2505
* Fixed contributors not showing correctly
* Fixed #2469
* Fixed #2509
* Fixed #2499
* Fixed #2527
* Fixed #2519
* Fixed #2517
* Fixed Magician Talisman sometimes drawing invalid enchantments
* Fixed id conflicts for external Enchantment sources (e.g. plugins) for the Magician Talisman settings
* Fixed network visualizers spawning particles for other player heads
* Fixed #2418
* Fixed #2446
* Fixed CoreProtect not recognizing Slimefun blocks getting broken
* Fixed #2447
* Fixed #2558
* Fixed a duplication bug with the Block Placer
* Fixed Slimefun Guide Settings showing "last activity" as a negative number
* Fixed Armor Stands getting damaged/pushed by Explosive Bow
* Fixed Sword of Beheading dropping Zombie/Skeleton Skulls from Zombie/Skeleton subvariants
* Fixed #2518
* Fixed #2421
* Fixed #2574
* Fixed color in android script downloading screen
* Fixed #2576
* Fixed #2496
* Fixed #2585
* Fixed #2583

## Release Candidate 17 (17 Oct 2020)
https://thebusybiscuit.github.io/builds/TheBusyBiscuit/Slimefun4/stable/#17

#### Additions
* Added /sf charge
* Added Energized Energy Capacitor
* Added various new fuel types to the Coal Generator
* Added a config option for Grappling Hooks to not be consumed on use
* Added Talisman of the Caveman
* You can now convert any gold ingot into gold dust with slightly less returns
* Magical Zombie Pills now also work on Zombified Piglins
* (API) Added SlimefunGuideOpenEvent
* (API) Added "NotConfigurable" attribute to disable configurability
* Added Elytra Cap
* Added Planks to Sticks recipe to the Table Saw
* Added "slimefun.gps.bypass" permission to open GPS devices anywhere
* (API) Added custom tags for developers
* The range of the Seeker Pickaxe is now configurable
* Added Energy Connector
* Blackstone can now be turned into lava using a Crucible
* Basalt can now be turned into lava using a Crucible
* Added "Tainted Sheep" (You can dye a Sheep using Strange Nether Goo)
* Added mcMMO support/integration

#### Changes
* Improved Auto-Updater (Multi-Threading and more)
* General performance improvements
* /sf cheat now shows seasonal categories all year through
* GPS devices now require chest-access in that area to be used

#### Fixes
* Fixed #2300
* Fixed #2296
* Fixed colors of Cheat Sheet Slimefun Guide
* Fixed Cheat Sheet Slimefun Guide being unable to open the settings menu via shift + right click
* Fixed #2320
* Fixed some issues with ChestTerminal
* Fixed #2325
* Fixed Climbing Pick having no animation in creative mode
* Fixed #2322
* Fixed some cargo incompatibilities with overflowing inventories
* Fixed #2353
* Fixed #2359
* Fixed #2356
* Fixed #2358
* Fixed #2360
* Fixed #2351
* Fixed #2357
* Fixed Auto Enchanters being unaffected by speed modifications from addons
* Fixed Auto Disenchanters being unaffected by speed modifications from addons
* Fixed radioactive items still being radioactive when disabled
* Fixed #2391
* Fixed #2403
* Fixed #2405
* Fixed #2412
* Fixed #2238
* Fixed #2439
* Fixed #2420
* Fixed #2422
* Fixed #2433
* Fixed #2455
* Fixed #2450
* Fixed Steel Thrusters being used to milk cows
* Fixed #2424
* Fixed #2468
* Fixed #2414
* Fixed #2454
* Fixed #2457
* Fixed #2411
* Fixed #2423
* Fixed #2452
* Fixed a dupe bug with mcMMO

## Release Candidate 16 (07 Sep 2020)
https://thebusybiscuit.github.io/builds/TheBusyBiscuit/Slimefun4/stable/#16

#### Additions
* Added an option for Industrial Miners to mine Ancient Debris
* Added a new language: Korean
* (API) Added support for adding custom Piglin Barter drops
* (API) Added BlockPlacerPlaceEvent
* (API) Added ToolUseHandler
* Added "Sand -> Sandstone" recipe to the Electric Press
* Added "Red Sand -> Red Sandstone" recipe to the Electric Press
* Industrial Miners can now also mine Gilded Blackstone
* Added a config option to disable Players from burning when exposed to radiation
* Added a config option to drop excess items when using /sf give
* Added Strange Nether Goo
* Added Villager Rune
* Added Synthetic Shulker Shells
* Added Climbing Pick
* Added item breaking sounds to some slimefun tools

#### Changes
* Performance improvement for Programmable Android rotations
* Removed Gravel -> Flint recipe from the Grind stone
* Performance improvements for miner talismans
* Performance improvements for idling Enhanced Furnaces when using Paper
* Performance improvements for Rainbow Blocks
* Crafting a Rag now yields two items
* Small performance improvements for Slimefun guides
* Small performance improvements for Cargo networks
* Small performance improvements for Miner Androids
* Small performance improvements for all machines, especially Electric Smelteries
* Small performance improvements for Holograms
* Small performance improvements for Tree Growth Accelerators
* Small performance improvements for Reactors
* Electric machines now show their tier in the Inventory name too
* Removed "Fuel efficiency" attribute for androids, since that was pretty much always at 1.0 anyway...
* Performance improvements for energy networks
* (API) Rewritten Block-Energy API
* Removed "durability" setting from cargo nodes
* Small performance improvements for radiation
* Small performance improvements for Auto Disenchanters
* Magnesium Salt in Magnesium-Salt generators now lasts longer

#### Fixes
* Fixed Programmable Androids rotating in the wrong direction
* Fixed #2176
* Fixed #2164
* Fixed #2147
* Fixed #2179
* Fixed Reinforced Spawners not working sometimes
* Fixed Explosive Pickaxe not handling normal Shulker boxes correctly
* Fixed #2103
* Fixed #2184
* Fixed #2183
* Fixed #2181
* Fixed #2180
* Fixed #2122
* Fixed #2168
* Fixed #2203
* Fixed #2205
* Fixed #2209
* Fixed #2217
* Fixed Miner Talisman sending messages when drops were not even doubled
* Fixed #2077
* Fixed #2207
* Fixed ChestTerminal timings showing up as cargo nodes
* Fixed timings reports never arriving sometimes
* Fixed #2138
* Fixed #1951 (again)
* Fixed Electric Press not working
* Fixed #2240
* Fixed #2243
* Fixed #2249
* Fixed #1022
* Fixed #2208
* Fixed Fluid Pump treating low-level fluids like stationary fluids
* Fixed Fluid Pump not working on Bubble Columns
* Fixed #2251
* Fixed #2257
* Fixed #2260
* Fixed #2263
* Fixed #2265
* Fixed #2269
* Fixed #2266
* Fixed #2275
* Fixed Multi Tools consuming hunger points when holding a Wind Staff in your off hand
* Fixed Teleports getting stuck sometimes

## Release Candidate 15 (01 Aug 2020)
https://thebusybiscuit.github.io/builds/TheBusyBiscuit/Slimefun4/stable/#15

#### Additions
* Added "Bone Block -> Bone meal" recipe to the Grind Stone
* Added a [Metrics module](https://github.com/Slimefun/MetricsModule) which allows us to release updates to metrics (bStats) independently from the main plugin
* Added "Compressed Carbon -> Carbon" recipe to the Ore Crusher
* Added "Carbon -> Coal" recipe to the Ore Crusher
* Added an option to disable the message "Ignoring duplicate block"
* Added Iron Golem Assembler
* Added Reinforced Cloth
* Added Bee protection to Hazmat Suit
* Added Enchantment Rune
* Added Tape Measure
* Added a permission node for /sf debug_fish

#### Changes
* Refactored and reworked the Generator API
* Small performance improvements to Energy networks
* Big performance improvements to Cargo networks when using ChestTerminal
* Slight changes to /sf timings
* Changed recipe of Hazmat Suits
* Uranium can no longer be placed down
* Huge performance improvements when using Paper
* Optimized Cargo networks for Paper
* Optimized Multiblocks for Paper
* Optimized Enhanced Furnaces for Paper
* Optimized Programmable Androids for Paper
* General performance improvements for Talismans
* General performance improvements for GPS Emergency Transmitters
* General performance improvements for Infused Magnets
* Ancient Altars now support for protection plugins
* Ancient Pedestals now support for protection plugins

#### Fixes
* Fixed Slimefun Armor sometimes not applying its effects
* Fixed #2075
* Fixed #2093
* Fixed #2086
* Fixed #1894
* Fixed #2097
* Fixed Wither Assembler requiring more items than it actually consumes
* Fixed Metrics not updating automatically
* Fixed #2143
* Fixed #2145
* Fixed #2151
* Fixed old Talismans not working
* Fixed Talismans sometimes not getting consumed properly
* Fixed old Infused Magnets not working
* Fixed old GPS Emergency Transmitters not working
* Fixed #2156
* Fixed #2165
* Fixed #2162
* Fixed #2166

## Release Candidate 14 (12 Jul 2020)
https://thebusybiscuit.github.io/builds/TheBusyBiscuit/Slimefun4/stable/#14

#### Additions
* Added support for Minecraft 1.16
* Added a starting sound for the Ancient Altar
* Added config option to disable backwards compatibility and improve performance
* Added ReactorExplodeEvent to the API
* Compatibility mode status is now included in /sf versions
* Added Nether Quartz Ore Crusher Recipe
* Added a new language: Tagalog
* Added Magical Zombie Pills
* Added 1.13 compatibility to the Auto Drier
* Added Corals to the fuel list for the Bio Generator
* Added Clay -> Clay blocks recipe to the Electric Press
* (1.16+) Slimefun guide can now show Smithing Table recipes
* (1.16+) Added Nether Gold Ore recipe to the Ore Crusher
* (1.16+) Added Gilded Blackstone recipe to the Ore Crusher
* (1.16+) Added Shroomlights to the fuel list for the Bio Generator
* (1.16+) Added Warped and Crimson Fungus to the fuel list for the Bio Generator
* Added an AoE damage effect to the Explosive Bow
* Added runtime deprecation warnings for ItemHandlers and Attributes used by Addons
* Added a proper lag profiler
* Added per-plugin lag info to /sf timings
* Added Indonesian translations

#### Changes
* Coolant Cells now last twice as long
* Ticking blocks will now catch more errors caused by addons
* Changed the texture for the Nuclear Reactor
* Changed the texture for the Nether Star Reactor
* Crafting Tin cans now produces 8 items instead of 4
* Multi Tool lore now says "Crouch" instead of "Hold Shift"
* Items which cannot be distributed by a Cargo Net will be dropped on the ground now instead of getting deleted
* Slimefun no longer supports CraftBukkit
* Item Energy is now also stored persistently via NBT
* Performance improvements to GPS/GEO machines, especially Oil Pump and GEO Miner
* Performance improvements for ticking blocks
* Performance improvements to the Cargo Net
* performance improvements to the Energy Net
* Performance improvements to Rainbow Blocks
* Performance improvements to Androids
* performance improvements to Generators and Electric Machines
* Cargo timings will now be attributed to the corresponding node and not the Cargo manager
* Thunderstorms now count as night time for Solar Generators
* Coolant Cells can no longer be placed on the ground
* Crafting Nether Ice Coolant Cells now results in 4 items
* Moved Soulbound Backpack to the "Magical Gadgets" Category

#### Fixes
* Fixed #2005
* Fixed #2009
* Fixed a chunk caching issue for GEO resources
* Fixed Infused Magnet working even if you haven't researched it
* Fixed Rainbow blocks duplication glitch when timing the block break right
* Fixed #1855
* Fixed some issues with AsyncWorldEdit
* Fixed some problems with unregistered or fake worlds
* Fixed a rare concurrency issue with world saving
* Fixed some contributors showing up twice
* Fixed #2062
* Fixed Grappling hooks disappearing when fired at Item frames or paintings
* Fixed Grappling hooks not getting removed when the Player leaves
* Fixed Grappling hooks making Bat sounds
* Fixed #1959
* Fixed Melon Juice requiring Melons instead of Melon Slices
* Fixed Cargo networks not showing up in /sf timings
* Fixed /sf timings reporting slightly inaccurate timings
* Fixed concurrency-related issues with the profiling
* Fixed #2066
* Fixed Rainbow Glass Panes not properly connecting to blocks
* Fixed Androids turning in the wrong direction
* Fixed contributors losing their texture after restarts
* Fixed "korean" showing up as "null"
* Fixed an issue with moving androids getting stuck
* Fixed Cargo nodes sometimes preventing chunks from unloading
* Fixed #2081
* Fixed a NullPointerException when Generators throw an Error Report

## Release Candidate 13 (16 Jun 2020)
https://thebusybiscuit.github.io/builds/TheBusyBiscuit/Slimefun4/stable/#13

#### Additions
* Added Dried Kelp Blocks recipe to the Electric Press
* Added Bone Blocks recipe to the Electric Press
* Added thai translations
* Added Industrial Miner
* Added Advanced Industrial Miner
* Added Cocoa Organic Food
* Added Cocoa Fertilizer
* Added a configurable limit to the Pickaxe of Vein Mining
* Added Gold Ingot to Dust recipe to the Electric Ingot Pulverizer
* Added Saddles to possible fishing loot for the Fishing Android
* Added Name tags to possible fishing loot for the Fishing Android
* Added Nautilus Shell to possible fishing loot for the Fishing Android
* Added Bamboo to possible fishing loot for the Fishing Android

#### Changes
* Removed Digital Miner
* Removed Advanced Digital Miner
* Dried Kelp Blocks can now be used in the Coal Generator
* Crafting Organic Food/Fertilizer yields more output now
* Organic Food (Melon) now uses Melon Slices instead of Melon blocks
* The Seismic Axe now skips the first two blocks to clear your field of view
* Auto Disenchanting is now a tiny bit faster
* Small performance improvements
* Dried Kelp Blocks can now be used as fuel for Tier 1 Androids
* Androids now have a separate category in the Slimefun Guide
* Android Interface recipes now require steel ingots
* Changed and unified a couple of tooltips
* Changed tooltip on jetpacks and jet boots to say "Crouch" instead of "Hold Shift"

#### Fixes
* Fixed Ore Washer recipes showing up twice
* Fixed #1942
* Fixed a few memory leaks
* Fixed #1943
* Fixed Nuclear Reactors accepting Lava as coolant
* Fixed #1971
* Fixed #1976
* Fixed #1988
* Fixed #1985
* Fixed a missing texture in the Android Script Editor
* Fixed #1992
* Possibly fixed #1951
* Fixed tab completion for /sf give showing players instead of amounts
* Fixed #1993
* Fixed #1907
* Fixed research fireworks still dealing damage sometimes

## Release Candidate 12 (27 May 2020)
https://thebusybiscuit.github.io/builds/TheBusyBiscuit/Slimefun4/stable/#12

#### Additions
* Added Ukrainian translations
* Added /sf backpack to restore lost backpacks
* Added automated Unit Tests
* Added WaypointCreateEvent
* Added an option to call an explosion event when using explosive tools

#### Changes
* Little performance improvements
* Bandages, Rags and Splints will no longer be consumed if your health is full and you are not on fire
* Player Profiles (researches and stuff) are now loaded completely asynchronously
* The Infused Magnet can no longer be placed down
* AncientAltar speed can now be changed internally (not available for server owners yet)
* Finished Italian translations

#### Fixes
* Fixed #1824
* Fixed #1833
* Fixed #1834
* Fixed #1843
* Fixed #1873
* Fixed Electric Smeltery not prioritising recipes
* Fixed #1851
* Fixed #1891
* Fixed #1893
* Fixed #1897
* Fixed #1908
* Fixed #1903
* Fixed Organic Food/Fertilizer not being recognized
* Fixed #1883
* Fixed #1829
* Fixed some mojang.com connection errors
* Fixed some very weird SkullMeta serialization problems in 1.15
* Fixed #1914
* Fixed file errors with PerWorldSettingsService
* Fixed ChestTerminals deleting items from Cargo networks (TheBusyBiscuit/ChestTerminal#25)
* Fixed #1926
* Fixed #1933
* Fixed random errors because of Mojang's new player heads backend (Why... Mojang... why?)
* Fixed Butcher Androids doing incorrect amounts of damage
* Fixed #1935

## Release Candidate 11 (25 Apr 2020)
https://thebusybiscuit.github.io/builds/TheBusyBiscuit/Slimefun4/stable/#11

#### Additions
* Added GEOResourceGenerationEvent
* Added AncientAltarCraftEvent
* Added SlimefunGuide-Options API
* Added ItemSettings API
* Added 1.13 backwards compatibility
* Added "Magma Cream to Magma Blocks" recipe to the Electric Press
* Added "Magma Blocks to Sulfate" recipe
* You can now search for items from within the book variant of the Guide
* GEO Scans now support endlessly many different resources
* Added Output Chest support to the Composter

#### Changes
* Replaced GuideHandlers with FlexCategories
* Removed support for old EmeraldEnchants versions
* Updated the book variant of the guide to use the newer API
* Removed internal /sf elevator command
* Split whitelist.yml up into individual /world-settings/worldname.yml files
* Performance improvements
* Slimefun Guide runs much faster now and can better deal with many Categories and items
* Lots of API improvements
* Faulty addons are now identified more easily and will no longer break Slimefun's main content this quickly
* You can no longer /sf give yourself a Multiblock
* Addons have no longer access to Slimefuns default categories
* Updated seasonal Categories to have better icons
* Even more performance improvements
* Changed Ignition Chamber Recipe
* GEO Miner is now 2 seconds faster
* All Generators will now stop consuming fuel if no energy is needed
* /sf teleporter will now open your own Teleporter Menu if you specify no Player
* Added counter-measures against Players who design Cargo networks in a way that intentionally lags out servers
* API requests to Mojang are now spread across a longer time period to prevent rate-limits

#### Fixes
* Fixed error message when clicking empty slots in the Slimefun Guide
* Fixed #1779
* Fixed localized messages not showing in the book guide
* Fixed empty categories showing up when items inside were hidden
* Fixed ghost pages showing up when too many categories were disabled
* Fixed debug fish not showing the correct chunk timings
* Fixed heads with missing permissions placing down
* Fixed unpermitted items still showing up in the guide if researches are disabled
* Fixed unpermitted items in the book guide triggering the search function
* Fixed #1803
* Fixed #1806
* Fixed #1807
* Fixed Coolers accepting non-Juice items
* Fixed #1813
* Fixed #1814
* Fixed GEO Scanner being unable to deal with more than 28 different resources
* Fixed #893
* Fixed #1798
* Fixed #1490
* Fixed GPS Emergency Transmitters not working

## Release Candidate 10 (28 Mar 2020)
https://thebusybiscuit.github.io/builds/TheBusyBiscuit/Slimefun4/stable/#10

#### Additions
* Added some new charts to bStats
* Added a new language: Turkish
* Multiblocks that use Fences or Trap doors now accept all wood types
* Added Makeshift Smeltery
* Added Tree Growth Accelerator
* Added "Glass to Glass Panes" recipe to the Electric Press
* Added "Snowballs to Snow blocks" recipe to the Electric Press
* Added "Snow blocks to Ice" recipe to the Freezer
* You can now use Cooked Salmon in an Auto Drier to craft Fish Jerky
* The Lumber Axe can now strip logs too
* The Slimefun Guide can now remember what page of a Category or Minecraft Item you were on

#### Changes
* Removed some deprecated parts of the API
* Internal clean up and further documentation
* Changed Automatic Ignition Chamber to be a Dropper
* Teleporters are now significantly faster
* Item permissions have been moved to a separate permissions.yml file
* Salt now only requires 2 blocks of Sand
* Fireworks from researching no longer damages entities
* Very slight performance improvements for Cargo networks
* 4K-carat gold ingots can now be used in a workbench by default (overridden by Items.yml)
* The project license is now included in every build
* Moved EmeraldsEnchants integration from EmeraldEnchants to Slimefun

#### Fixes
* Fixed some languages showing numbers larger than 100%
* Fixed #1570
* Fixed #1686
* Fixed #1648
* Fixed #1397
* Fixed #1706
* Fixed #1710
* Fixed #1711
* Fixed Slimefun Guide showing shaped recipes incorrectly
* Fixed #1719
* Fixed death waypoints not having the correct texture
* Fixed Androids having no texture when moving
* Fixed Androids not taking fuel from interfaces
* Fixed #1721
* Fixed #1619
* Fixed #1768

## Release Candidate 9 (07 Mar 2020)
https://thebusybiscuit.github.io/builds/TheBusyBiscuit/Slimefun4/stable/#9

#### Fixes
* Fixed Solar Generators not working

## Release Candidate 8 (06 Mar 2020)
https://thebusybiscuit.github.io/builds/TheBusyBiscuit/Slimefun4/stable/#8

#### Fixes
* Fixed bStats Metrics not sending properly

## Release Candidate 7 (06 Mar 2020)
https://thebusybiscuit.github.io/builds/TheBusyBiscuit/Slimefun4/stable/#7

#### Additions
* Added translations for Recipe Types
* Added Rainbow Concrete
* Added Rainbow Glazed Terracotta
* Added more internal documentation

#### Changes
* Researches now use their namespaced keys in the Researches.yml
* A lot of API changes

#### Fixes
* Fixed #1553
* Fixed #1513
* Fixed #1557
* Fixed #1558
* Fixed a translation not showing properly
* Fixed #1577
* Fixed #1597
* Fixed disabled Slimefun Addons not showing under /sf versions
* Fixed #1613

## Release Candidate 6 (16 Feb 2020)
https://thebusybiscuit.github.io/builds/TheBusyBiscuit/Slimefun4/stable/#6

#### Additions
* Added a new language: Japanese
* Added a new language: Swedish
* Added a new language: Czech
* Added a new language: Portuguese (Brazil)
* Added a new language: Arabic

#### Changes
* /sf research now uses namespaced keys instead of ids

#### Fixes
* Fixed #1515
* Fixed back-button in guide-settings not working via commands
* Fixed #1516
* Fixed magician talisman not being able to enchant books

## Release Candidate 5 (09 Feb 2020)
https://thebusybiscuit.github.io/builds/TheBusyBiscuit/Slimefun4/stable/#5

#### Additions
* Added preset messages.yml files
* Added user-configurable localization
* Added many more options to the messages.yml
* Added custom model data support for Languages
* Added Grind Stone Recipes for Prismarine
* Added String to the Bio Reactor
* Added a config setting to limit how many Nodes a Network can have
* Added support for Furnaces and Brewing Stands to Cargo Networks
* Added Organic Food/Fertilizer for Dried Kelp
* Added many more strings to the messages.yml
* Added ability to translate messages for Players
* Added the ability to translate Researches
* Added StatusEffect API
* Added translatability to categories
* Added translatability to geo-resources

#### Changes
* Removed Solar Array
* A lot of internal cleanup
* Performance improvements for GEO Miner and Oil Pump
* General performance improvements
* Changed Startup console message
* Changed GEO-Resources API

#### Fixes
* Fixed #1355
* Fixed Localization mistakes
* Fixed #1366
* Fixed GitHub cache
* Fixed #1364
* Fixed Bio Reactor not accepting melons
* Fixed Cargo Networks particles being broken
* Fixed #1379
* Fixed #1212
* Fixed #114
* Fixed #1385
* Fixed #1390
* Fixed #1394
* Fixed #1313
* Fixed #1396
* Fixed Backpacks being placeable
* Fixed wrong file encoding for translations
* Fixed Minecraft recipes not showing correctly
* Fixed #1428
* Fixed #1435
* Fixed #1438
* Fixed Multi Tool functioning as unlimited Shears
* Fixed #1383
* Fixed Android Script Component textures

## Release Candidate 4 (06 Jan 2020)
https://thebusybiscuit.github.io/builds/TheBusyBiscuit/Slimefun4/stable/#4

#### Additions
* Added 1.15 support (1.14 and 1.15 are both supported)
* Added custom model support to Slimefun Guide and some Recipe Types
* Added Nether Gold Pan
* Added Iron Nuggets to Gold Pan drops
* Added CS-CoreLib version to the guide info
* Added AndroidMineEvent
* Added Electric Press
* Added Soulbound Trident
* Added "Andesite, Granite and Diorite to Gravel" recipes to the Grinder
* Added "nuggets to ingots and ingots to blocks" recipes to the Electric Press
* Added Salt to the GEO - Miner
* Added Magnesium Salt
* Added Magnesium-powered Generator
* Added "Gravel to Sand" recipe to the Grinder
* Added config option for circuit board drops
* Added player option to toggle research fireworks in the guide settings
* Added Kelp Cookies
* Added support for multiple recipes on vanilla items
* Added a "Craft last" button to the Automated Crafting Chamber
* Added more ore-doubling Recipes to the Ore Crusher
* Added Addons to the guide settings

#### Changes
* Revamped Guide Settings menu
* Changed some Category icons
* Changed Grappling Hook recipe
* Searching the guide now shows the Category of the item
* Contributors now also show their minecraft username (if possible)
* Changed teleporter sounds
* Electric Gold Pan now also supports Nether Gold Pan drops
* More performance improvements
* Improved Cargo performance
* Removed Nether Drill
* Tweaked Enhanced Furnace Recipes
* Changed tooltips for Radiation
* Oil Pump now shows its "Bucket -> Oil" recipe

#### Fixes
* Fixed Research Titles
* Fixed #1264
* Fixed #1261
* Fixed #1266
* Fixed #1272
* Fixed #1273
* Fixed christmas items
* Fixed Multi Tools
* Fixed credits not showing all contributors
* Fixed exception when viewing the second page of the credits
* Fixed #1269
* Fixed #1276
* Fixed GEO-Miner dupes
* Fixed Output Chest not working
* Fixed #1281
* Fixed #1280
* Fixed a lot of issues with Crucibles
* Fixed Grind Stone dupes
* Fixed #1316
* Fixed performance issues with Oil Pumps
* Fixed #1318
* Fixed #1298
* Fixed #1325
* Fixed #1295
* Fixed MultiBlocks not accepting different fence types
* Fixed #1337
* Fixed Applie Pie ID mismatch
* Fixed #1344
* Fixed #1349
* Fixed #1332
* Fixed #1356 and maybe other concurrency issues
* Fixed Ore Crusher's missing recipes
* Fixed #1354

## Release Candidate 3 (21 Nov 2019)
https://thebusybiscuit.github.io/builds/TheBusyBiscuit/Slimefun4/stable/#3

#### Additions
* Smeltery now shows some recipes in the guide
* Added Sweet Berry Juice
* MultiBlocks that require fences will now accept all types of wooden fences
* Added craftable Totems of Undying
* Added back some wiki-pages to the guide
* Added support for all new minecraft recipes to correctly display in the guide
* Added support for custom model data for items
* Added Output Chest support to the Table Saw
* Added Output Chest support to the Automated Panning Machine
* Added Jerky recipes to the Auto-Drier
* Added AutoDisenchantEvent
* Added "Flint to Cobblestone" Recipe to the Compressor


#### Changes
* Changed Ignition Chamber Recipe
* /sf cheat no longer allows you to spawn in MultiBlocks
* Removed Heavy Armor
* Massive performance improvements with a new item-id system
* Huge performance improvements with skippable tickers
* Changed Elytra Scale Recipe
* Revamped Reactor Access Port
* Performance improvements for multi tools
* Performance improvements for armor
* Performance improvements for the Slimefun Guide

#### Fixes
* Fixed Stone Chunk -> Cobblestone Recipe not working
* Fixed #1145
* Fixed #1157
* Fixed #1180
* Fixed Backpacks not working
* Fixed /sf cheat not showing locked categories
* Fixed #1200
* Fixed #1196
* Fixed #1153
* Fixed some food items
* Fixed multi tools not working
* Fixed #1202
* Fixed #1211
* Fixed #1219
* Fixed #1226
* Fixed #1224
* Fixed repair-cost getting wiped after disenchanting
* Fixed GPS transmitters transmitting wrong locations
* Fixed Ancient Altar allowing you to craft locked items

## Release Candidate 2 (29 Sep 2019)
https://thebusybiscuit.github.io/builds/TheBusyBiscuit/Slimefun4/stable/#2

#### Additions
* Added GEO - Miner
* Added more bStats Charts

#### Changes
* Reworked MultiBlocks
* Removed the Saw Mill

### Fixes
* Fixed Basic Machines not showing all recipes
* Fixed #1129
* Fixed #1130
* Fixed Auto-Updater for stable builds

## Release Candidate 1 (26 Sep 2019)
https://thebusybiscuit.github.io/builds/TheBusyBiscuit/Slimefun4/stable/#1

* First "stable" release since over a year. Stable builds will NOT receive support for bug reports since they are technically outdated.

---

## Archived development and validation records


---

### Archived source: `COMPATIBILITY_FOUNDATION.md`

# Slimefun Legacy 4.1.16 — Compatibility Foundation

> **4.1.17 inheritance:** Slimefun Legacy 4.1.17 retains this complete platform, bytecode, data-format, and gameplay compatibility contract. It adds only the optional addon-facing Doctor service API and does not change Slimefun storage formats.

Slimefun Legacy 4.1.16 is a maintenance-only release that formalizes the project's compatibility boundaries. It intentionally does not change gameplay, machine behavior, item IDs, saved data, or the database format.

## Supported platform contract

| Area | 4.1.16 contract |
| --- | --- |
| Primary server | Paper 26.2 / Minecraft 1.21.11 |
| Secondary server | Purpur based on Paper 26.2 |
| Experimental server | Folia based on Paper 26.2 |
| Build toolchain | Java 25 |
| Supported production runtime | Java 25 |
| Slimefun-owned bytecode | Java 21 maximum |
| Paper API compile baseline | `1.21.11-R0.1-SNAPSHOT` |
| Public plugin identity | `Slimefun` |
| Data format | Unchanged |
| Gameplay behavior | Unchanged |

The machine-readable source of truth is [`compatibility/support-contract.json`](../../compatibility/support-contract.json).

### About `api-version: '1.16'`

The Bukkit descriptor value remains `1.16` to preserve historical material handling and addon behavior. It is not a promise that Minecraft 1.16 is supported. The tested platform contract above and CI are the support boundary.

## Compatibility gates

### Public API surface

The public API workflow now:

- builds the candidate JAR;
- records every compatibility-protected public JVM signature;
- downloads a released baseline JAR;
- reports added and removed signatures;
- fails on an unapproved removal;
- fails if `javap` cannot inspect an API class instead of silently skipping it;
- publishes the candidate surface even when no released baseline exists.

Intentional removals require an exact entry in `scripts/api-removal-allowlist.txt` and should be accompanied by migration documentation.

### Java bytecode target

`scripts/check_bytecode_target.py` inspects the class-file headers inside the shaded JAR. Slimefun-owned classes must remain at Java 21 bytecode or lower even though CI and the supported server runtime use Java 25.

This protects addon tooling and prevents an accidental compiler configuration change from silently producing Java 25-only Slimefun classes.

### Sensitive dependency boundaries

`scripts/check_dependency_boundaries.py` prevents sensitive direct dependencies from spreading to new source files. The 4.1.16 baseline covers:

- Dough;
- GuizhanLib;
- WorldEdit;
- HikariCP;
- bStats;
- Unirest;
- CraftBukkit and Minecraft server internals.

Removing imports is always allowed. Adding a new importing file or increasing an existing file's sensitive import count fails verification until the architecture change is reviewed and the baseline is deliberately updated.

Direct CraftBukkit and NMS imports have a zero-import budget.

### Deprecation visibility

Compatibility CI compiles with `-Xlint:deprecation` and publishes:

- the complete compiler log;
- a normalized Markdown report grouped by source file.

The report is informational in 4.1.16. This distinction matters because deprecated public compatibility bridges may need to remain available for addons even after Legacy stops using them internally.

### Candidate Paper API compile

The Gradle build accepts an optional Paper API override:

```bash
./gradlew clean compileJava -PpaperApiVersion=1.21.11-R0.1-SNAPSHOT
```

Repository administrators can define the GitHub Actions variable `PAPER_API_CANDIDATE` to test a future Paper API. That job is intentionally non-blocking while the candidate API is unstable, but it gives early notice of source incompatibilities.

An optional `API_BASELINE_TAG` repository variable can pin the public API comparison to a specific GitHub release tag. Without it, the latest release is used.

### Addon compatibility matrix

The weekly and manually triggered compatibility workflow now performs a controlled two-JAR comparison for every addon:

1. check out the pinned 4.1.15 source, run its own `spotlessApply`, and build the known-good baseline JAR from commit `493587431dc831d4b8bc38649af6e22df74a15b0`;
2. build the addon in a fresh checkout copy against that known-good baseline JAR;
3. only after that succeeds, build a second fresh copy against the candidate Slimefun Legacy JAR produced by the current workflow;
4. publish the baseline log, candidate log, machine-readable JSON result and Markdown summary separately.

Only the core dependencies whose artifact name is exactly `Slimefun` or `Slimefun4` are replaced. Dependencies such as SlimefunTranslation, InfinityExpansion, InfinityLib and other Gugu addons remain untouched even when their Maven group contains the word `Slimefun`.

Results are classified as:

- `PASS` — the addon builds against both the known-good baseline and candidate;
- `BASELINE_BUILD_FAILED` — the addon also fails against 4.1.15, indicating an addon dependency, repository or build-environment problem rather than a new Legacy regression;
- `LEGACY_COMPATIBILITY_FAILED` — the addon builds against 4.1.15 but fails against the candidate, indicating a genuine candidate API regression;
- `INSTRUMENTATION_ERROR` — checkout or dependency-replacement infrastructure could not complete the comparison.

Required targets are release-blocking:

- `wickidcow/SF_FastMachines`;
- `wickidcow/SF_NetworksExp` targeting `2.1.112-Legacy-Alpha1`;
- `wickidcow/SF_SlimeTinkerIE2`;
- `wickidcow/SF_BetterChests`.

A curated set of public `SlimefunGuguProject` addons is also compiled as an advisory compatibility probe. Gugu failures remain visible in the GitHub Actions matrix and publish individual build logs, but archived or independently changing Gugu projects do not block a Slimefun Legacy release.

The required Networks target is the maintained Slimefun Legacy fork `wickidcow/SF_NetworksExp`. CI tracks its active `master` branch but requires the declared Gradle version to remain `2.1.112-Legacy-Alpha1`, so fixes within Alpha 1 can advance without silently switching the compatibility contract to a different release.

The Gugu advisory set includes FluffyMachines, FoxyMachines, SlimeTinker, FlowerPower, IDreamOfEasy, Gastronomicon, Bump, SlimeCustomizer, and EMCTech. Matrix concurrency is limited to four addon builds at a time to reduce remote-service and runner pressure.

## Verification

Run the complete source checks with:

```bash
python3 scripts/verify_legacy.py .
```

Build and verify the JAR with:

```bash
./gradlew spotlessApply --no-daemon
./gradlew clean build --no-daemon
python3 scripts/check_bytecode_target.py build/libs/Slimefun-4.1.16.jar --expected-java 21
```

Generate the deprecation report with:

```bash
mkdir -p build/reports
./gradlew clean compileJava -PslimefunDeprecationReport=true --no-daemon 2>&1 \
  | tee build/reports/deprecation-compile.log
python3 scripts/summarize_deprecations.py build/reports/deprecation-compile.log
```

## Deliberately excluded from 4.1.16

This release does not add:

- automatic plugin or addon downloads;
- automatic JAR replacement;
- database migrations;
- guide changes;
- machine logic changes;
- new storage backends;
- Cargo or energy behavior changes;
- new Folia cross-region transactions.

Those areas remain separate, reviewable projects built on top of this compatibility foundation.

---

### Archived source: `COMPATIBILITY_MAINTENANCE_ROUND2.md`

# Paper/Purpur Compatibility Maintenance — Round 2

This maintenance pass modernizes Slimefun Legacy's own Paper, Bukkit and WorldEdit calls while preserving the JVM signatures older addons were compiled against.

## Modernized internals

- Replaced direct construction of Paper's internal `EntityDamageByEntityEvent` with supported `DamageSource` and `LivingEntity.damage(...)` calls.
- Preserved direct and causing entities for Seismic Axe, Stomper Boots and Explosive Bow damage.
- Preserved cancellation-aware custom knockback for Seismic Axe and Explosive Bow.
- Updated WorldEdit block-vector coordinate access from deprecated `getBlockX/Y/Z()` and `getX/Y/Z()` methods to the current record accessors `x()/y()/z()`.
- Added missing `@Deprecated` annotations to legacy SQL schema constants and translated their documentation to English.
- Enabled Java 25 native access for Gradle SQLite storage tests.

## Addon compatibility retained

The following legacy signatures remain available and are covered by regression tests:

- `BlockTicker.tick(Block, SlimefunItem, Config)`
- `EnergyNetComponent.getCharge(Location, Config)`
- `EnergyNetProvider.getGeneratedOutput(Location, Config)`
- `EnergyNetProvider.willExplode(Location, Config)`
- `BlockStorage.getLocationInfo(Location)` returning the legacy `Config` view

The old CS-CoreLib `Config` class remains deprecated, but it is no longer marked for removal in Slimefun Legacy. New internal code continues to use `ASlimefunDataContainer`, `SlimefunBlockData`, binary inventory storage and the modern scheduler abstractions.

## Validation

Run:

```bash
python3 scripts/verify_legacy.py .
./gradlew spotlessApply clean build --no-daemon
```

The new `verify_compatibility_round2.py` check rejects removal of compatibility bridges and rejects reintroduction of internal Paper damage-event constructors or deprecated WorldEdit vector accessors.

---

### Archived source: `CORE_CORRECTNESS_AUDIT.md`

# Slimefun Legacy Core-Correctness Audit

This maintenance pass reviews active fixes from Slimefun 5, Slimefun United, Gugu and Folia-oriented work against the current Slimefun Legacy source. Paper and Purpur remain the primary runtime targets. Fixes are ported only when Legacy is missing the behavior and the change can preserve addon compatibility.

## Reviewed upstream fixes

- Slimefun 5 `e443570`: recipe-required multiblock consumption.
- Slimefun 5 `b63c725`: dispatch to all matching multiblocks.
- Slimefun 5 `fc79efb`: thread-correct Auto Enchanter/Disenchanter events.
- Slimefun 5 `be7316e`: backpack identity collisions and silent open failures.
- Slimefun 5 `0d7f954`: hidden-addon visibility recovery.
- Slimefun United `bd4b36a`: Energy Regulator hologram thread safety and bug-tracker URL validation.
- Slimefun United `5ca54ab`: string-based Multi Tool mode storage.
- Slimefun United `b8789d5`: Programmable Android texture preservation.

## Ported in this audit

### Recipe-amount-correct multiblocks

The Enhanced Crafting Table, Magic Workbench and Armor Forge now consume the amount declared in each matched recipe cell instead of always subtracting one. Their virtual output-capacity simulation uses the same recipe amounts, including consumable-container handling.

### All matching multiblock handlers

A click is now dispatched to every matching multiblock in reverse registration order. This prevents one overlapping structure definition from silently swallowing a click intended for another machine. Each machine still validates its own inventory and recipe before acting.

### Energy Regulator thread ownership

The Energy Regulator ticker is now synchronized. On Paper and Purpur this routes hologram/network mutation through the server-owned path; on Folia the existing scheduler abstraction keeps it on the owning region.

### Multi Tool mode migration

Multi Tool mode is now stored as the selected Slimefun item ID rather than a numeric list position. Existing integer-mode tools are migrated on first use. This keeps the selected mode stable if configuration reorders or inserts modes.

### Backpack resolution diagnostics

Backpack allocation already uses UUID identities and a persisted monotonic display counter, so the collision bug from older map-size allocation does not apply. This audit adds warnings when a current or legacy backpack identity resolves to missing or invalid storage instead of failing silently.

## Confirmed already present

- Auto Enchanter and Auto Disenchanter events derive their asynchronous flag from the current Paper/Purpur thread or Folia-owned region.
- `/sf versions` validates addon bug-tracker URLs and has a plain-text delivery fallback.
- Programmable Android movement preserves the stored base64 head texture.
- Backpack items use UUID storage identities, persisted numbering, duplicate-open reservations and viewer checks.
- The Vanilla Auto-Crafter protects modern gamerule reads.
- Profiler reports reject stale cycle completion.
- Cargo, energy and storage code already use Legacy's scheduler and transaction safeguards.

## Reviewed but not ported

### Addon-visibility reset

Slimefun 5's fix resets a per-player hidden-addon set. Legacy does not implement that data model; its guide reads registered item groups directly and catches addon visibility failures. Porting the setting-specific reset would add an unused subsystem.

### False multiblock “Assembled” message

The reviewed Slimefun 5 fix targets a structure-placement notification path that does not exist in this Legacy source. There is no equivalent message to correct.

### Anvil custom-name preservation

Legacy intentionally blocks Slimefun items from anvils. Slimefun 5's rename-preservation marker belongs to its translated-item/anvil architecture and would require changing established gameplay rather than correcting a current Legacy bug.

### Slimefun 5 package compatibility shim

Legacy retains the original `io.github.thebusybiscuit.slimefun4` API packages, so addons do not need a shim for a package relocation that never occurred here.

### Alternate SQL backends and redstone multiblock autocrafting

These are major features, not focused compatibility fixes. They remain separate future projects because the current storage and crafting systems are stable.

### Runtime lore migration audit

Slimefun 5's audit is tied to its translation-block migration. Legacy is English-first and uses a different presentation-repair system, so a direct port would report many intentional strings. A Legacy-specific addon lore diagnostic can be designed separately without affecting this core-correctness release.

## Validation

Run:

```bash
python3 scripts/verify_legacy.py .
./gradlew spotlessApply
./gradlew clean build
```

The audit verifier checks both newly ported fixes and the important protections already present in Legacy.

---

### Archived source: `CORE_PLATFORM_PHASE1A.md`

# Slimefun Legacy Core Platform Phase 1A

Core Platform Phase 1A returns development to the core compatibility and maintenance architecture. It creates a stable boundary for future Minecraft, Paper, Purpur, Folia, addon, and upstream-fork work without changing Slimefun item IDs, recipes, stored data, database schemas, or normal gameplay.

## Goals

- Make future Minecraft and server-platform updates require changes in one compatibility layer instead of scattered version checks.
- Give addons a supported way to ask what the current runtime can do.
- Track useful changes from Original Slimefun, Gugu, Slimefun5, Slimefun United, and Slimefun4Core without automatically importing unsafe code.
- Preserve the established Slimefun 4 addon API and production data.
- Keep Paper primary, Purpur supported, conventional Paper derivatives best-effort, and Folia experimental.

## Capability-based platform API

The new `PlatformCompatibilityService` is available through:

```java
PlatformCompatibilityService compatibility = Slimefun.getPlatformCompatibilityService();
```

Addons can check concrete runtime capabilities:

```java
if (compatibility.supports(PlatformCapability.DATA_COMPONENT_API)) {
    // Use the modern Paper data-component path.
} else {
    // Retain the compatible legacy path.
}
```

This is safer than checking `Bukkit.getName()`, comparing implementation package names, or assuming that every server with the same Minecraft version exposes the same APIs.

The immutable `PlatformProfile` reports:

- detected platform family;
- support level;
- raw and parsed Minecraft version;
- Java feature version;
- detected scheduler, Adventure, chunk-loading, and data-component capabilities.

`/sf versions` now includes this profile and capability inventory so addon bug reports can include the exact compatibility environment.

## Version parsing

`MinecraftVersionNumber` is a new semantic numeric version type. It parses normal release and pre-release identifiers such as `1.21.11`, `26.1`, and `1.21.2-pre2` without depending on enum ordering. Snapshot names are not guessed.

The historical `MinecraftVersion` enum remains intact for addon binary compatibility. Core startup maps the centrally parsed numeric version back to that enum only where the legacy API still requires it.

## Multi-fork upstream intake

`compatibility/upstream-sources.json` records every reviewed source and its allowed role:

| Source | Role | Intake policy |
| --- | --- | --- |
| Original Slimefun 4 | Historical API and data baseline | Compatibility reference |
| Slimefun Gugu | Maintained code upstream | Existing guarded merge workflow |
| Slimefun5 | Modern compatibility reference | Selective ports only |
| Slimefun United | Feature and architecture reference | Selective ports only |
| Slimefun4Core | Experimental design reference | Design review until independently proven |

`scripts/check_upstream_candidates.py` validates this registry and can query each configured GitHub branch. The weekly **Upstream Candidate Radar** workflow publishes an advisory report. It never merges, downloads, or replaces source files.

Candidate ideas are recorded in `compatibility/core-feature-backlog.json`. A candidate cannot become active merely by editing the manifest; it still requires implementation, compatibility review, tests, and release documentation.

## Feature candidates retained for later phases

Phase 1A records, but does not yet activate:

- a formal API deprecation and migration lifecycle;
- capability-routed scheduler and event bridges;
- optional core module dependencies and feature toggles;
- unified localization keys with guaranteed English fallback;
- deeper storage consistency and migration audits;
- optional staff multitool behavior from Slimefun United.

Core safety work comes before gameplay ports. This prevents feature imports from making later Paper, Purpur, Folia, database, or addon updates harder.

## Compatibility guarantees

This phase intentionally makes no changes to:

- Slimefun item IDs;
- research IDs;
- recipe definitions;
- block-storage keys;
- backpack or player-profile formats;
- database schema;
- Cargo or energy behavior;
- normal guide organization or controls.

Existing public APIs are retained. The new platform API is additive.

## Validation

Run the complete source verification:

```bash
python3 scripts/verify_legacy.py .
```

Run the Phase 1A verifier directly:

```bash
python3 scripts/verify_core_platform_phase1a.py .
```

Validate the upstream registry without network access:

```bash
python3 scripts/check_upstream_candidates.py --offline
```

---

### Archived source: `CORE_PLATFORM_PHASE1B.md`

# Core Platform Phase 1B

## Goal

Make the 4.1.19 capability foundation the single compatibility boundary used by Slimefun core and future addons, while retaining every existing public entry point required by current addons.

## Delivered

### Declarative addon requirements

`PlatformRequirements` and `PlatformCompatibilityReport` let an addon describe minimum Minecraft and Java versions, required runtime capabilities, and accepted platform families. The platform service returns every unmet requirement rather than failing on the first one.

### Additive service helpers

All new `PlatformCompatibilityService` methods are Java default methods. A third-party service implementation compiled against 4.1.19 continues to link because no new abstract interface method was introduced.

### Central runtime detector

`RuntimePlatformDetector` owns implementation-class, method, and Paper-family probes. Core code no longer repeats PaperLib, Folia class, or Bukkit server-version checks.

### Scheduler routing

`PaperScheduler` consumes the platform service in normal Slimefun startup. The original constructor remains, with detector fallback, so direct legacy construction remains source and binary compatible.

### Deprecation lifecycle

`@SlimefunDeprecated` supplements Java `@Deprecated` with a version, replacement, and optional earliest removal version. A deprecated API is not scheduled for removal unless `removalVersion` is explicitly populated.

### API signature baseline

`compatibility/api-signatures-4.1.19.txt` records all 991 public and protected declarations in compatibility-protected packages. `verify_api_compatibility.py` allows additive APIs but fails when an existing declaration is removed, moved, renamed, or changed.

## Retained bridges

- `FoliaSupport` and `FoliaSupport.isFolia()`
- `PaperScheduler(Plugin)`
- all existing `SlimefunScheduler` methods
- the historical `MinecraftVersion` enum
- existing `Slimefun.getPlatformCompatibilityService()` and `getSchedulerService()` accessors

## Non-goals

- no module toggles;
- no item, recipe, or guide redesign;
- no storage or database migration;
- no automatic upstream code merge;
- no claim that an addon is Folia-safe merely because Slimefun core is running on Folia.

## Next phase

Phase 1C should inventory version-gated gameplay code and replace only genuine API-availability decisions with named capabilities. Historical gameplay differences should remain explicit Minecraft-version behavior rather than being incorrectly generalized as platform capabilities.

---

### Archived source: `CORE_PLATFORM_PHASE1C.md`

# Core Platform Phase 1C

Phase 1C turns the Phase 1A/1B platform foundation into addon-facing update infrastructure while retaining the complete existing addon API.

## Runtime compatibility layer

- Additive compatibility declarations through Java registration, a provider interface, or an embedded JSON manifest.
- Runtime evaluation of tested Slimefun cores, platform requirements, required plugins, and optional integrations.
- Central optional-dependency discovery and guarded reflection.
- `/sf doctor compatibility`, startup summaries, and addon status in `/sf versions`.
- Undeclared legacy addons remain loadable. Diagnostics do not automatically disable plugins.

## Release compatibility gates

- Machine-readable core API registry and addon matrix.
- Dynamically generated GitHub Actions matrix rather than duplicated workflow YAML entries.
- Baseline/candidate source-build comparison.
- Precompiled-addon JVM linkage analysis for missing Slimefun classes, methods, and fields.
- Required and advisory tiers with explicit release behavior.
- Synthetic linkage-checker verification and permanent Phase 1C static invariants.

## Compatibility promise

Phase 1C is additive. It does not rename or remove existing APIs, item IDs, recipes, storage keys, database structures, saved-world formats, or scheduler bridges. The 4.1.19 public/protected API baseline remains enforced.

---

### Archived source: `CORE_PLATFORM_PHASE1D.md`

# Core Platform Phase 1D

Phase 1D makes Slimefun Legacy compatibility checks follow the release lifecycle instead of remaining tied to an old hard-coded build. The runtime and addon APIs from Phases 1A-1C remain unchanged.

## Rolling regression baseline

- Added `compatibility/release-baselines.json` as the single source of truth for candidate, previous-stable, and historical-floor compatibility baselines.
- Slimefun Legacy 4.1.22 uses **4.1.21** as the release-blocking previous-stable baseline.
- The previous-stable baseline is pinned to a Git ref so CI tests a reproducible core rather than whichever branch happens to be current.
- API compatibility and addon source/binary compatibility now read the same baseline registry.

## Historical compatibility floor

- Slimefun Legacy **4.1.15** is retained as a separate historical floor.
- Historical-floor comparisons are advisory and never block a release by themselves.
- This distinguishes a new regression from long-term ecosystem drift.

## CI lifecycle hardening

- Required addon failures only block release when the addon builds against the previous stable core and fails against the candidate.
- Weekly/manual CI also compares the candidate with the historical floor for visibility.
- Expanded the advisory addon matrix with additional Gugu ecosystem projects.
- Candidate Paper API probing remains non-blocking and continues to expose future Paper breakage early.

## Forward-compatible verification

- Phase 1A, 1B, and 1C source verifiers no longer require an explicit allow-list entry for every future Legacy release.
- They now verify that the current release is at least the phase's introduction version and that the original phase release documentation remains present.
- Phase 1D adds a permanent lifecycle verifier that prevents workflow YAML from silently reintroducing a hard-coded active baseline.

## Compatibility promise

Phase 1D changes release engineering and compatibility verification only. It does not rename or remove addon APIs, item IDs, recipes, storage keys, database structures, saved-world formats, or gameplay behavior.

## Next platform work

The next compatibility layer can build on this lifecycle foundation with optional capability adapters for Pylon/Rebar machine, storage, cargo, and energy endpoints plus a unified runtime failure registry.

---

### Archived source: `CORE_PLATFORM_PHASE1E.md`

# Core Platform Phase 1E — Runtime Stability & External Integration Foundation

Phase 1E is the Slimefun Legacy 4.1.23 runtime-hardening line. Its rule is simple: successful normal Slimefun behavior stays on the existing path; only failing optional callbacks are isolated.

## Part 1 — machine failure isolation

- Keeps the existing per-location machine circuit breaker and makes its failure threshold configurable.
- Tracks live failure owner, Slimefun item ID, location, cause, retry state and duplicate-report suppression.
- Catches failures inside deferred synchronized machine callbacks, not only failures thrown by the coordinator.
- Rate-limits repeated `BlockTicker.startNewTick()` lifecycle exceptions.
- Preserves ticker registrations and stored machine data while a failing location is paused.
- Adds `/sf doctor runtime` diagnostics.

## Part 2 — Rebar/Pylon adapter foundation

- Adds a capability-based provider API for inventory, storage, cargo, machine, energy and fluid bridges.
- Adds reflection-only Rebar/Pylon block discovery with no compile-time dependency.
- Classifies loaded external blocks conservatively as inventory/storage, cargo/logistics, machine/processor and fluid endpoints.
- Adds `/sf doctor integrations probe` for targeted block capability inspection.
- Does not enable cross-network cargo transfer or Rebar/Pylon energy exchange.

## Part 3 — guarded recovery and compatibility protection

- Adds circuit-breaker isolation for failing external provider status and block-inspection callbacks.
- Tracks provider, operation, cause, retry state and duplicate-report suppression without affecting normal Slimefun processing.
- Adds `/sf doctor runtime retry`, `/sf doctor runtime retry all`, `/sf doctor integrations retry <id|all>` and `/sf doctor integrations reload`.
- Adds explicit external-adapter failure thresholds/cooldowns in `config.yml`.
- Adds a hash guard for the green Part 2 versions of normal Slimefun Cargo, Energy, NetworkManager, Guide, `SlimefunItem`, `BlockTicker`, `AContainer` and `TickerTask` code. Part 3 fails verification if those normal core paths change.
- Keeps the 991-signature compatibility baseline as a release gate.

## Part 3.1 — `/sf versions` operator clarity

- Replaces raw runtime enum-style labels with plain-language compatibility results.
- Shows `✔ Compatible`, `⚠ Compatible with warnings`, `? Compatibility not verified`, `✕ Incompatible`, or `✕ Disabled` for every detected addon.
- Explains that an undeclared addon is loaded but not runtime-verified instead of presenting the internal `Undeclared` state without context.
- Keeps declaration source and detailed reasons available in hover text.
- Does not change addon loading, compatibility decisions, Cargo, Energy, machine processing, saved data, or any protected API signature.

## Normal Slimefun compatibility guarantee for Part 3

Part 3 does **not** modify Slimefun CargoNet, EnergyNet, NetworkManager, SlimefunGuide, SlimefunItem, BlockTicker, AContainer or the already-green Part 1 TickerTask. Healthy core machines, cargo networks, energy networks and addons continue using their existing execution paths.

The new failure/retry methods on the Phase 1E external integration service are Java default methods, so existing implementations do not need to recompile just to satisfy the new API surface.

## Rebar/Pylon safety boundary

Rebar/Pylon remain optional. Detection never implies interoperability. Slimefun does not automatically inject items into Rebar cargo networks and does not exchange energy with Rebar/Pylon unless a future adapter implements proven-compatible semantics.

## Compatibility

Phase 1E is additive. It does not change item IDs, recipes, storage keys, databases, saved-world formats, normal Cargo/Energy behavior, or compatibility-protected addon API signatures.

---

### Archived source: `CORE_PLATFORM_PHASE1F.md`

# Core Platform Phase 1F — Compatibility Intelligence

Phase 1F makes compatibility information useful to server operators without changing normal Slimefun gameplay or addon APIs.

## Part 1 — addon recognition and `/sf versions`

- Adds a runtime recognition registry for addon families already covered by the Legacy compatibility CI matrix.
- Separates exact runtime declarations from CI monitoring evidence.
- Replaces ambiguous undeclared/unrecognized output with three clear states:
  - `✔ Compatible` — the addon explicitly declared compatibility and passed runtime checks.
  - `◉ Known addon — Legacy CI monitored` — the addon family is covered by Legacy CI, but the exact installed JAR did not declare compatibility.
  - `? Slimefun addon — compatibility unknown` — Slimefun detected the addon, but it has neither a declaration nor a known Legacy CI mapping.
- Keeps incompatible and disabled states explicit and red.
- Sorts addon output alphabetically for stable diagnostics.

## Safety boundary

CI monitoring is evidence, not a guarantee for the exact installed addon build. Phase 1F does not promote undeclared addons to the `COMPATIBLE` API state and does not change addon loading behavior.

Normal Slimefun Cargo, Energy, machines, guide, storage, recipes, item IDs, databases, and saved-world formats are unchanged. The existing 991 protected API signatures and Phase 1E normal-core hash guard remain release gates.

## Part 2 — compatibility diagnostics and evidence

- Adds a recognition-only tier for known addon families that are not currently in Legacy CI.
- `/sf versions` now separates declared-compatible, CI-monitored, recognized-only, and truly unknown addons.
- `/sf doctor compatibility` now reports the evidence behind each result instead of repeating the raw API status.
- Per-addon diagnostics include declaration source, Legacy registry evidence, active machine-failure state, and a safe
  compatibility-layer linkage signal.
- The linkage signal only reports failures observed during safe compatibility inspection. It is not presented as a full
  bytecode proof; monitored addon builds still rely on GitHub source/binary compatibility CI for stronger evidence.
- Recognition-only aliases include Better Farming, DankTech2, Cultivation, Electric Spawners, ExtraTools,
  GeneticChickengineering, HotbarPets, Magic 8 Ball, MobCapturer, SFMobDrops, SlimefunAdvancements, SlimeGlue,
  SimpleMaterialGenerators, and SoulJars.

## Part 2.1 — compact `/sf versions` presentation

- Every addon uses one short compatibility word in the normal chat line: `Compatible`, `Known`, `Recognized`, `Warning`, `Unknown`, `Incompatible`, or `Disabled`.
- Detailed compatibility evidence, declaration source, CI/registry information, and diagnostic messages remain available by hovering the status word.
- Very long custom addon build strings are compacted only for chat display and retain their full exact version in hover text.
- `/sf doctor compatibility` remains the detailed multi-line report; `/sf versions` is intentionally the quick overview.

---

### Archived source: `CORE_PLATFORM_PHASE1G.md`

# Core Platform Phase 1G — Lifecycle, Runtime and Addon Hardening

Phase 1G modernizes Slimefun Legacy internals while deliberately preserving normal Slimefun gameplay and addon-facing behavior. The work is split into three logical parts but ships together in 4.1.25.

## Part 1 — Core lifecycle and scheduler foundation

- Adds a read-only `CoreLifecycleService` with startup/shutdown state and phase snapshots.
- Makes startup phases observable without reordering the existing initialization sequence.
- Makes shutdown cleanup ordered and failure-isolated: one failing cleanup step is logged but does not prevent later independent cleanup steps.
- Extends `SlimefunScheduler` with additive default methods for quiescing and health snapshots, preserving third-party scheduler implementation compatibility.
- `PaperScheduler` stops accepting new work before task cancellation and reports tracked task count plus region-owned execution mode.
- `ThreadService` now has an explicit shutdown lifecycle and correctly uses the configured fixed-delay period.

## Part 2 — Machine and storage runtime facades

- Adds `MachineRuntimeService` and immutable `MachineRuntimeSnapshot` as stable access points for ticker health and recovery.
- Adds read-only `StorageRuntimeService` and `StorageRuntimeSnapshot` for database/cache health.
- Moves Doctor status/recovery commands onto those facades where possible.
- Does not alter machine recipes, ticker semantics, Cargo, Energy, block storage keys, database schemas, or saved-world formats.

## Part 3 — Addon runtime compatibility and release hardening

- Adds `AddonRuntimeHealthService` to record failures that are already caught at guarded addon callback boundaries.
- Records compatibility-provider failures, addon item-load failures, and third-party integration callback failures for diagnostics.
- Does not automatically disable an addon or change the success path because of telemetry.
- Adds `/sf doctor core` and enriches `/sf doctor compatibility <addon>` with guarded callback health evidence.
- Keeps the 4.1.19 protected API baseline and the Phase 1E normal-core hash guard as release-blocking checks.

## Compatibility boundary

Phase 1G is infrastructure modernization. It does not change normal Slimefun Cargo or Energy semantics, machine processing, recipes, item IDs, research IDs, storage formats, database schemas, or saved-world formats. New addon-facing services are additive. Existing scheduler methods remain intact, and newly added scheduler lifecycle methods have compatibility-preserving defaults.

---

### Archived source: `ENHANCED_GUIDE.md`

# Slimefun Legacy Native Enhanced Guide — Phases 1–4.1B-C

This package replaces the default survival and cheat guide registrations with native enhanced implementations designed to feel familiar to JustEnoughGuide users while remaining inside Slimefun Legacy's normal guide API.

## Phase 1 — Guide experience

- JEG-style six-row category and item layouts.
- Configurable character-based menu formats.
- Paged smart search instead of the classic 35-result cap.
- Search by item name, Slimefun ID, addon, category, recipe type and lore.
- Search filters: `id:`, `addon:`, `group:` and `recipe:`.
- Persistent per-player bookmarks stored by Slimefun item ID.
- Item lore showing category, addon and Slimefun ID.
- Existing research, permissions, history, cheat-item and native recipe rendering behavior retained.

## Phase 2 — Shaped recipe preparation

- Transactional guide-to-dispenser filling for Enhanced Crafting Table, Magic Workbench and Armor Forge.
- Left-click prepares one recipe; shift-left-click prepares the maximum complete sets that fit.
- Machine-specific ingredient matching, backpack compatibility, protection checks and Folia ownership checks.
- No direct crafting and no nearby-storage access.

## Phase 3 — Advanced preparation and reports

- Unordered filling for Grind Stone, Smeltery, Ore Crusher, Compressor and Pressure Chamber.
- Ancient Altar pedestal preparation with optional safe catalyst selection.
- Full right-click ingredient reports and abbreviated missing-item button lore.
- Slimefun sub-recipe hints without recursive execution.
- Transactional planning, rollback and conservative altar ritual locking.

## Phase 4 — Universal machine recipe browsing

- Addon-facing `MachineRecipeProvider` registry and normalized recipe model.
- Direct integration through `MachineRecipeDisplayItem`.
- Automatic structured browsing for core and addon `AContainer` machines.
- Compatibility discovery for common public recipe methods and fields, including `getMachineRecipes()`, `getRecipeProcess()`, `getRecipes()`, `machineRecipes` and `recipes`.
- Supreme-style recipe objects are normalized through public input/output arrays, numbered getters, processing-time getters and chance metadata.
- Existing `RecipeDisplayItem` recipes receive the same paged browser.
- FastMachines support moved into the provider system while retaining alternatives and world filters.
- Optional processing-time, energy-use, layout and source metadata in recipe details.
- Defensive `ItemStack` copying so guide rendering cannot mutate addon recipes.

See [`docs/MACHINE_RECIPE_PROVIDER_API.md`](../MACHINE_RECIPE_PROVIDER_API.md) for addon integration examples.

## Phase 4.1A — Core GUI machine input filling

- Adds a **Fill Machine Inputs** button to verified `AContainer` recipes.
- Left-click transfers one complete recipe set; shift-left-click transfers the maximum safe number of complete sets.
- Requires the player to aim at the exact placed machine shown in the guide.
- Writes only the machine's declared input slots and never touches outputs, controls or upgrades.
- Coordinates with the machine ticker during the transaction and refuses machines that are open or already being viewed.
- Uses the same recipe-input and stack-merge matching services as the machine runtime, including virtual-item handling.
- Simulates the complete player/machine transfer before committing and restores both inventories after an unexpected failure.
- Preserves protection-plugin checks and Folia region ownership.
- Does not start operations, consume energy, generate outputs or access nearby storage.

## Phase 4.1B-A — Automatic addon `AContainer` filling

- Extends **Fill Machine Inputs** to addon machines that inherit the standard Slimefun `AContainer` implementation.
- Removes the previous Slimefun-core addon ownership restriction.
- Matches each displayed recipe back to the machine's actual registered `MachineRecipe` list before showing the fill button.
- Keeps reflected or guide-only addon recipes view-only when they cannot be verified against the runtime container recipes.
- Revalidates the player's selected ingredient alternatives when the transfer begins.
- Uses the amounts from the registered runtime recipe rather than trusting reflected guide metadata.
- Supports reordered inputs and duplicate ingredients through unique, order-independent recipe matching.
- Rejects addon machines whose declared input slots overlap their output slots.
- Retains all Phase 4.1A transaction, rollback, protection, ticker and Folia safety checks.

## Phase 4.1B-B — Custom machine input-fill adapters

- Adds a public `MachineInputFillAdapter` API for machines whose real recipe list or GUI layout is outside the standard `AContainer#getMachineRecipes()` contract.
- Addons can register adapters by namespaced key and priority without replacing the Enhanced Guide or accessing Legacy internals.
- Each adapter identifies supported machines, validates displayed recipes, resolves authoritative ingredients, declares writable input slots and declares protected output/control/status slots.
- Slimefun Legacy retains protection checks, Folia region ownership, placed-machine validation, viewer locking, inventory simulation, commit validation and rollback.
- Invalid, duplicate, out-of-range or input/protected-overlapping slot declarations are rejected before any inventory changes.
- Adds a built-in adapter for Supreme `GenericMachine` implementations that expose the public `machineRecipes` list and public recipe getters.
- Supreme output and status slots remain protected, and only recipes matching the guide display and selected alternatives receive filling support.
- Slimefun Legacy 4.1.15 adds a built-in FastMachines adapter using its public recipe, choice, wrapper and input-slot getters.
- FastMachines filling is limited to verified ingredient slots 0–35; preview and control slots 36–53 are always protected.
- A changed or unrecognized FastMachines inventory layout remains recipe-browser-only rather than being guessed.
- Unsupported custom machines remain recipe-browser-only until their addon registers a compatible adapter.



## Technical design

- Native guide registration; no reflection or private-field replacement.
- No updater, Pinyin index, Chinese alias layer or separate scheduler.
- Classic guide fallback through `plugins/Slimefun/enhanced-guide.yml`.
- Existing server configuration remains compatible through code defaults for added settings.

## Current boundary

The guide still does not automatically craft items, withdraw from nearby storage or recursively craft missing sub-components. Phase 4.1B-C fills verified standard containers, Supreme machines and the maintained FastMachines layout through registered adapters. Nearby-storage access, recursive crafting and machines that cannot expose an authoritative safe adapter remain outside this phase.

## Installation

1. Remove the JustEnoughGuide JAR before testing this native implementation.
2. Back up the Slimefun Legacy repository.
3. Extract the appropriate changed-files ZIP into the repository root and replace matching files.
4. Commit the files and run the normal GitHub Actions build.
5. Fully stop the test server, replace the Slimefun Legacy JAR and start it normally.
6. Test on a copied Paper server before production use.

To restore the classic guide, set `enabled: false` in `plugins/Slimefun/enhanced-guide.yml` and fully restart the server.
## Phase 4 compatibility correction

- Supreme and other addons that keep their real processing recipes outside `AContainer.getMachineRecipes()` now receive the Machine Recipes browser.
- Public collection methods, iterable/array sources, map values and public recipe-list fields are supported without private reflection.
- Standard `AContainer` recipes and addon-owned public recipe sources are merged when a machine uses both systems.
- Every recognized public source is inspected, so an empty compatibility getter cannot hide a populated recipe field.
- Numbered input/output getters remain available when an aggregate getter exists but returns no usable items.
- Recipe objects may expose aggregate inputs/outputs or numbered getters such as `getInput1()` and `getInput2()`.
- Existing FastMachines world filtering and alternative-choice handling remain isolated to its dedicated provider.

## Cheat guide category grouping

The enhanced and classic `/sf cheat` menus now reuse the normal guide's exact top-level categories, icons, ordering, nested groups and addon visibility rules. This preserves addon-designed navigation instead of replacing every plugin with a generic chest folder. Item clicks still use cheat mode, so normal clicks grant one item and shift-clicks grant a full stack where supported.

---

### Archived source: `FOLIA_PHASE1.md`

# Slimefun Legacy Folia Support — Phase 1

This phase establishes a conservative Folia execution boundary without changing Slimefun Legacy's established Paper behavior or removing legacy addon APIs.

## Implemented

- Central Folia runtime detection shared by scheduler and utility code.
- Location ownership and entity ownership checks in the scheduler API.
- Region-owned machine ticking on Folia, grouped by machine chunk.
- A non-overlapping machine cycle coordinator that waits for every scheduled region chunk before advancing `BlockTicker` cycle state.
- Per-`BlockTicker` serialization on Folia to protect addons that keep mutable state in a shared ticker instance.
- Thread-safe `BlockTicker.uniqueTick()` cycle transitions.
- Entity-owned backpack callbacks, inventory opening/closing, and player callback executors.
- Location-owned compatibility bridges for storage and block operations.
- Owner-aware custom-event asynchronous flags.
- Concurrent Cargo and energy network collections.
- A safe Folia network boundary: discovered topology may be retained, but Cargo and energy only read or mutate nodes owned by the current regulator region.
- Folia-aware shutdown and startup warnings.
- Static verification plus a concurrency regression test for `BlockTicker` cycle state.

## Paper compatibility

Paper continues to use the historical scheduler path:

- asynchronous tickers remain asynchronous;
- synchronized or viewed-inventory tickers are moved to Paper's primary thread;
- Bukkit scheduler cancellation and player inventory closure remain unchanged on Paper;
- no existing public method descriptor was removed.

## Folia behavior change for addons

On Folia, `BlockTicker#isSynchronized()` cannot mean "may access Bukkit asynchronously." Every block ticker is executed by the region that owns its machine location. CPU-only work may still be delegated to the async scheduler, but all Bukkit entity, inventory, block, chunk, and world work must be marshalled back to the correct owner.

A shared `BlockTicker` instance is serialized to reduce breakage in older addons that store mutable counters or temporary state on the ticker object. Addons should still migrate that state to per-location storage when practical.

## Intentional network safety boundary

Phase 1 does **not** attempt cross-region Cargo or energy transactions. A regulator only operates nodes currently owned by its execution region. Nodes outside that ownership boundary are retained as a deferred topology frontier and can be reconsidered after Folia merges regions, but no direct cross-region inventory or energy access occurs.

This prevents a partial implementation from introducing item duplication, item loss, cross-region thread exceptions, or inconsistent charge updates. True cross-region support requires a reserve/commit/rollback transaction coordinator and belongs in a later phase.

## Remaining work before calling Folia fully supported

1. Audit every core item, listener, menu, entity task, and storage callback under a live Folia thread checker.
2. Implement transactional cross-region Cargo transfers.
3. Implement snapshot/commit cross-region energy distribution.
4. Add region split/merge recovery tests and loaded/unloaded chunk tests.
5. Test every supported addon individually; the core cannot make direct unsafe Bukkit calls inside an addon safe.
6. Run duplication, shutdown, restart, database, profiler, and circuit-breaker stress tests on a staging Folia server.

## Staging checklist

- Use a copy of the production world and database.
- Start with Slimefun Legacy only, then add addons one at a time.
- Place identical machines in distant regions and confirm independent ticking.
- Test block menus while machines tick.
- Test Cargo and energy entirely inside one owned region.
- Confirm networks crossing a region boundary pause remote nodes rather than moving items or power unsafely.
- Watch logs for thread-access exceptions, duplicate machine cycles, failed callbacks, and circuit-breaker trips.
- Restart repeatedly and verify inventories, backpacks, energy charge, and block storage.

Folia remains **experimental** after this phase.

---

### Archived source: `FOURTH_MAINTENANCE_RELEASE.md`

# Slimefun Legacy — Fourth Maintenance Release

## Folia Event Safety & Paper API Cleanup

This release keeps standard Paper as the primary production platform while completing another focused pass toward Folia compatibility.

### Event-state safety

- Soulbound recovery state now uses concurrent storage.
- Soulbound items are snapshotted and are not re-added when `keepInventory` is active.
- Elytra impact grace state now uses a concurrent set, entity-owned delayed cleanup, and disconnect cleanup.
- Slimefun bow projectile state now uses a concurrent map and entity-owned retirement-safe cleanup.

### Paper API cleanup

- Vanilla Auto-Crafters use `GameRules.LIMITED_CRAFTING`.
- Auto Brewer and potion comparison code use the modern `PotionMeta` base-potion-type API.
- Wind Staff and Storm Staff use the current `FoodLevelChangeEvent` constructor.
- `/sf versions` now recommends Java 21 or newer and reports whether Slimefun is using Paper or Folia scheduler semantics.

### Profiler reliability

- Empty profiler windows now return zero instead of dividing by zero.
- Millisecond and nanosecond averages use independent sample counters and can no longer reset one another.

### Folia status

`folia-supported: true` is now declared because core scheduling is routed through the scheduler abstraction and the event-state paths covered by this release are ownership-aware. Folia deployment must still be tested on a copied server, and every installed addon must independently support Folia.

---

### Archived source: `GUGU_UPSTREAM_SYNC.md`

# Gugu Upstream Sync

Slimefun Legacy intentionally diverges from SlimefunGuguProject/Slimefun4. It contains English-only behavior, addon compatibility work, the Stability Release, the Second Maintenance Release, and the Third Maintenance Release. Upstream changes should therefore be merged and reviewed, not copied over the fork.

## Why the July 2026 storage update matters

The current upstream comparison contains the database schema v3 storage work:

- ItemStacks are stored as binary Paper serialization instead of legacy Java-object/Base64 text.
- Existing database rows are migrated while retaining legacy deserialization compatibility.
- MySQL, PostgreSQL, and SQLite inventory columns move to binary column types.
- Database migration and schema-version updates are performed transactionally.
- Universal block data remains unresolved when its world is not loaded, then resolves after that world becomes available.
- Storage API compatibility and migration tests are added.

This is valuable for modern Paper item metadata and database reliability, but it is not a low-risk cosmetic update. Once a production database has migrated to schema version 3, downgrading to a build that only understands schema version 2 is unsafe without restoring the pre-upgrade database backup.

## Integrated upstream baseline

Part 3 manually integrates the Gugu storage branch through upstream merge commit `ece7368e1d0b40bc95c63d2796117794fcaf190e`. The file `.gugu-upstream-base` records that revision.

When the source changes are uploaded without their original upstream Git parent, the sync script first creates an **ours merge** to connect that recorded upstream revision to the fork history without changing any source files. It then performs a normal merge of only commits newer than the recorded baseline. After a successful sync, the marker advances to the new upstream commit.

Do not delete or manually change `.gugu-upstream-base` unless intentionally re-establishing the upstream integration point. The workflow refuses rewritten or unrelated upstream history rather than guessing.

## Upstream health gate

Before any merge is attempted, `.github/workflows/sync-gugu-upstream.yml` evaluates the selected Gugu commit through GitHub's Check Runs, Actions workflow runs, and legacy commit-status APIs. The scheduled workflow is strict: failed, pending, unavailable, or missing health signals block the sync before Java setup, merging, branch pushes, or pull-request changes.

A manual workflow run may enable `override_upstream_health`, but `override_reason` is required. An override only permits creation of a reviewed **draft** test branch and pull request; it does not bypass Legacy's own English, API, Paper/Purpur, storage, guide, formatting, test, or build checks. The health report is uploaded as an artifact and embedded in the draft pull request.

The evaluator intentionally uses only the latest attempt for each check or workflow. A successful rerun can therefore clear an earlier failed attempt, while a currently queued or in-progress rerun remains blocked. Success, neutral, and skipped conclusions are accepted; failures, cancellation, timeout, startup failure, action-required, stale results, and unknown coverage are blocked unless manually overridden.

## Safe workflow

`.github/workflows/sync-gugu-upstream.yml` performs a real Git merge into `automation/gugu-upstream-sync` and opens or updates a **draft pull request**.

It deliberately does not:

- replace the source tree with `rsync --delete`;
- auto-merge the pull request;
- choose upstream versions of conflicted files;
- bypass the English-only guard;
- push anything when Git reports merge conflicts.

A clean merge must pass:

- `scripts/verify_english.py`;
- `scripts/verify_part2.py`;
- `scripts/verify_part3.py`;
- `scripts/verify_gugu_sync.py`;
- `scripts/check_api_annotations.py`;
- Spotless;
- the test suite;
- the full Gradle build.

## Running it

1. Push the completed Part 3 source to the repository's default branch.
2. Open **Actions → Sync Gugu Upstream → Run workflow**.
3. Leave `upstream_ref` as `master` unless testing a specific upstream branch.
4. Leave `override_upstream_health` disabled for normal updates. If a deliberately reviewed draft test must proceed despite upstream health, enable it and provide a concrete `override_reason`.
5. Review the upstream health artifact, generated draft pull request, and sync report.
6. Resolve any English wording, scheduler ownership, API compatibility, or addon compatibility issues in the PR branch.
7. Test the resulting JAR against a copy of the production Slimefun database.
8. Merge only after the backup/restore test succeeds.

## Production database procedure

Before the first server boot using the schema-v3 build:

1. Stop the server cleanly.
2. Back up the entire `plugins/Slimefun` directory.
3. Back up the SQLite file or external MySQL/PostgreSQL database independently.
4. Start a staging copy and allow the migration to complete.
5. Verify backpacks, block inventories, universal storage, skull/profile metadata, cargo, and addon machines.
6. Keep the pre-migration backup until the new build has run successfully for several restarts.

Do not test this migration for the first time on the live database.

## Local use

From a clean Git checkout:

```bash
scripts/sync_upstream.sh master automation/gugu-upstream-sync
```

To include the full local validation/build:

```bash
GUGU_SYNC_BUILD=1 scripts/sync_upstream.sh master automation/gugu-upstream-sync
```

If a merge conflict occurs locally, the script leaves the conflict for manual resolution. In GitHub Actions, the workflow aborts the merge and uploads the conflict report instead.

---

### Archived source: `PAPER_PURPUR_COMPATIBILITY.md`

# Paper and Purpur Compatibility Maintenance

Slimefun Legacy treats **Paper** and **Purpur** as its primary server platforms. Folia remains a supported secondary target, but compatibility changes must preserve normal Paper behavior first.

## Current maintenance layer

This maintenance layer adds three low-risk fixes inspired by active Slimefun 5 work while retaining Slimefun Legacy's existing API packages and addon behavior:

- Defensive reading of the `doLimitedCrafting` gamerule so a Paper/Purpur API transition cannot crash Auto-Crafter interaction handling.
- A plain-text fallback for `/sf versions` if rich Adventure component delivery fails.
- A profiler cycle guard that prevents empty reports when a new profiling cycle starts before the previous report finishes.

It also adds `scripts/verify_legacy.py`, which runs every English, API, storage, Folia, Enhanced Guide, Gugu sync, upstream-health, and Paper/Purpur compatibility invariant from one command.

The Gugu sync workflow now proves upstream health before importing code. Failed, pending, missing, or unavailable upstream checks are blocked by default, and any manual draft-only override requires a written reason.

## Slimefun 5 review policy

Slimefun 5 is monitored as a source of modern fixes, not as a replacement codebase. Changes should be ported only when they:

1. Solve a reproducible Paper/Purpur, addon, storage, guide, or performance problem.
2. Can be adapted without relocating Legacy's public API packages.
3. Preserve existing addon binary compatibility.
4. Pass the full Legacy verification suite and Gradle tests.
5. Do not rewrite stable systems merely to match another fork's architecture.

## Core-correctness audit

The focused core audit also ports recipe-amount-correct multiblock consumption, all-match multiblock dispatch, synchronized Energy Regulator ticks, Multi Tool ID-mode migration and backpack identity diagnostics. See [`CORE_CORRECTNESS_AUDIT.md`](CORE_CORRECTNESS_AUDIT.md) for the full disposition of reviewed Slimefun 5, United and Gugu changes.

## Compatibility Maintenance Round 2

Legacy now uses supported Paper `DamageSource` calls for internally generated combat damage and current WorldEdit vector accessors. The deprecated CS-CoreLib `Config` type remains available only as an addon compatibility surface and is no longer marked for removal from this fork. Reflection tests lock the historical ticker, energy and BlockStorage signatures in place while the core continues using modern storage containers.

Gradle storage tests now opt into Java 25 native access for SQLite JDBC, avoiding the restricted-native-access warning during CI. See [`COMPATIBILITY_MAINTENANCE_ROUND2.md`](COMPATIBILITY_MAINTENANCE_ROUND2.md).

## Compatibility Foundation (4.1.16)

The primary tested line is **Paper 26.2 / Minecraft 1.21.11 on Java 25**, with Purpur based on that Paper line supported and Folia remaining experimental. Slimefun-owned classes continue to target Java 21 bytecode.

The build now publishes public API surfaces, blocks unapproved signature removals, verifies bytecode class versions, prevents sensitive direct dependency imports from spreading, records deprecation warnings, and optionally compiles against a future Paper API supplied through the `PAPER_API_CANDIDATE` repository variable. See [`COMPATIBILITY_FOUNDATION.md`](COMPATIBILITY_FOUNDATION.md).

## Validation

Run:

```bash
python3 scripts/verify_legacy.py .
./gradlew spotlessCheck test build --no-daemon
```

The Gugu upstream synchronization workflow runs the same complete verifier before it opens or updates its draft pull request.

---

### Archived source: `PATCH_NOTES.md`

# Slimefun Legacy — Optional AdvancedEnchantments Compatibility

## Included machines

This patch updates the shared Slimefun enchantment machine implementation used by:

- Auto Enchanter I
- Auto Enchanter II
- Auto Disenchanter I
- Auto Disenchanter II
- InfinityExpansion2 Advanced Enchanter and Advanced Disenchanter
- InfinityExpansion2 Infinity Enchanter and Infinity Disenchanter

The InfinityExpansion2 tiers inherit Slimefun's core `AutoEnchanter` and `AutoDisenchanter` processing, so no InfinityExpansion2 source modification is needed for the current addon implementation.

## Optional integration

AdvancedEnchantments is not required:

- No direct AdvancedEnchantments imports are compiled into Slimefun.
- No `depend` or `softdepend` entry is added to `plugin.yml`.
- Slimefun checks for an enabled `AdvancedEnchantments` plugin after server startup.
- AEAPI is accessed through a guarded runtime bridge.
- Without AdvancedEnchantments, the original vanilla enchantment behavior remains active.
- API failures leave machine inputs untouched and log only the first compatibility warning.

## Machine behavior

### Enchanters

- Continue accepting vanilla enchanted books.
- Also accept standard AdvancedEnchantments books using the current `ae_book` item-data format.
- Apply custom enchantments through AEAPI and use the `ItemStack` returned by AEAPI.
- Preserve existing Slimefun enchant-count, enchant-level, ignored-lore, and override-level settings.
- Support books containing both vanilla stored enchantments and AE book metadata.

AE book success and destroy percentages are preserved as metadata when read, but automated application is deterministic. The machine applies the enchant through AEAPI rather than rolling the manual drag-and-drop book chances.

### Disenchanters

- Detect current AdvancedEnchantments item data (`ae_enchantment-*`).
- Remove custom enchantments through AEAPI when available.
- Verify removal before consuming either input.
- Produce an AE-compatible enchanted book with 100% success and 0% destroy chance.
- Preserve all remaining custom and vanilla enchantments on the output item.

Only one AE custom enchantment is extracted per operation. AE books are individually identified and non-stackable, while the machine has two output slots. Run the cleaned item through again with another plain book to extract the next custom enchantment. Once no AE enchantments remain, the normal Slimefun operation extracts the vanilla enchantments into one vanilla enchanted book.

## Paper event-thread fix

The patch also includes the required event-thread repair for:

- `AutoEnchantEvent`
- `AutoDisenchantEvent`
- `AsyncAutoEnchanterProcessEvent`

These events now mark themselves synchronous or asynchronous according to the thread that constructs them. This prevents Paper from rejecting the events when a viewed machine inventory causes its ticker to execute on the primary server thread.

## Files changed

1. `src/main/java/io/github/thebusybiscuit/slimefun4/api/events/AsyncAutoEnchanterProcessEvent.java`
2. `src/main/java/io/github/thebusybiscuit/slimefun4/api/events/AutoDisenchantEvent.java`
3. `src/main/java/io/github/thebusybiscuit/slimefun4/api/events/AutoEnchantEvent.java`
4. `src/main/java/io/github/thebusybiscuit/slimefun4/api/events/EventThreading.java` (new)
5. `src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/electric/machines/enchanting/AutoDisenchanter.java`
6. `src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/electric/machines/enchanting/AutoEnchanter.java`
7. `src/main/java/io/github/thebusybiscuit/slimefun4/integrations/AdvancedEnchantmentsIntegration.java` (new)
8. `src/main/java/io/github/thebusybiscuit/slimefun4/integrations/IntegrationsManager.java`

## Validation performed

- Java 21 AdvancedEnchantments bridge harness: passed.
- Java 21 compilation of the actual modified Auto Enchanter and Auto Disenchanter classes against API-compatible test stubs: passed.
- Dynamic event-thread harness for primary-thread, worker-thread, and server-unavailable construction: passed.
- 120-column, LF-ending, and trailing-whitespace checks on all changed files: passed.
- Optional-dependency check for direct AE imports and `plugin.yml` dependency entries: passed.

A full Gradle build could not be executed in the packaging environment because the checked-in wrapper requires Gradle 9.4.1 and the environment could not reach `services.gradle.org`. Run `./gradlew clean build` in GitHub Actions or another network-enabled development environment.

## Installation

### Changed-files archive

Extract it over the root of the same Slimefun Legacy source revision and allow the eight source files to be added/replaced.

### Full patched source

Build normally:

```bash
./gradlew clean build
```

The plugin JAR will be produced under `build/libs/`.

---

### Archived source: `PHASE4_1B_ADDON_ACONTAINER.md`

# Slimefun Legacy 4.1.13 — Phase 4.1B-A

## Goal

Automatically support safe guide-to-machine ingredient filling for addon machines that use Slimefun's standard `AContainer` architecture.

## Supported machines

A machine is eligible when all of the following are true:

1. The Slimefun item extends `AContainer`.
2. The displayed guide recipe can be matched to an actual recipe in `AContainer#getMachineRecipes()`.
3. The placed block has the exact same Slimefun item ID as the machine opened in the guide.
4. The machine exposes valid, unique input slots that do not overlap its output slots.
5. The player passes protection, region ownership and inventory-viewer checks.

This allows compatible machines from addons such as InfinityExpansion2, FluffyMachines, LiteXpansion, DynaTech and FoxyMachines to work automatically when they populate the normal `AContainer` recipe list.

Supreme's maintained `GenericMachine` is an important example of the boundary: it extends `AContainer`, but processes its own public `machineRecipes` list instead of the inherited registered recipe list. Its recipes remain browseable, but filling stays disabled until the custom-machine adapter phase can describe Supreme's real processing rules safely.

## Deliberate boundary

Some addons expose extra recipes through public fields or compatibility methods while processing them through custom inventory code. Those recipes remain visible in the recipe browser, but they do not receive a fill button unless the recipe also exists in the container's registered runtime recipe list.

FastMachines and fully custom inventories remain outside this phase. They require the later custom machine adapter API.

## Safety retained from Phase 4.1A

- One-set and maximum-safe-set modes
- Full player and machine inventory simulation
- Rollback after unexpected commit failure
- Exact placed-machine validation
- Protection-plugin checks
- Folia region ownership checks
- Open-inventory and ticker coordination
- Input-slot-only writes
- No output generation, direct processing, energy modification or nearby-storage access

---

### Archived source: `PHASE4_1B_CUSTOM_MACHINE_ADAPTERS.md`

# Phase 4.1B-B — Custom Machine Input-Fill Adapters

Slimefun Legacy 4.1.14 introduced **Fill Machine Inputs** to custom addon machines without weakening the transaction safeguards introduced in Phases 4.1A and 4.1B-A.

## Delivered

- Public `MachineInputFillAdapter`, `MachineInputFillRecipe`, and `MachineInputFillAdapterRegistry` APIs.
- Priority-ordered adapter selection by stable namespaced key.
- Standard `AContainer` support moved behind the same adapter contract.
- Built-in Supreme `GenericMachine` compatibility adapter.
- Built-in FastMachines compatibility adapter added in Slimefun Legacy 4.1.15.
- Authoritative recipe matching against the selected guide alternatives and outputs.
- Explicit writable input slots and protected output/control/status slots.
- Per-adapter maximum-fill limits and safety hooks.
- Existing exact-target, protection, region-thread, menu-viewer, simulation, commit-validation, and rollback safeguards retained.

## Supreme compatibility

Supreme `GenericMachine` classes keep their processing recipes in a public `machineRecipes` collection rather than the inherited `AContainer#getMachineRecipes()` list. The built-in adapter reads only that public contract and the recipe object's public input/output getters. It does not use `setAccessible`, private fields, or hard dependencies on Supreme classes.

The adapter:

- Requires a Supreme-package `GenericMachine` class that still extends `AContainer`.
- Matches the guide recipe to an actual custom processing recipe.
- Uses the custom recipe's ingredient amounts as the transfer authority.
- Uses the machine's declared input and output slots.
- Adds its public status slot to the protected-slot set.
- Leaves unmatched or incompatible recipes browse-only.

## FastMachines compatibility

Slimefun Legacy 4.1.15 connects the existing FastMachines recipe provider to a separate authoritative input-fill adapter. It reads the addon's public Kotlin/JVM getters without linking against FastMachines classes.

The adapter:

- Reads the machine's public `getRecipes()` and `getInputSlots()` contracts.
- Reads each recipe's public inputs and outputs.
- Preserves alternative ingredient choices and their individual required amounts.
- Revalidates the selected guide alternative against the complete authoritative choice group.
- Requires the maintained FastMachines 54-slot layout with ingredient slots 0–35.
- Protects slots 36–53, which contain previews, navigation, information, energy, selection and crafting controls.
- Fails closed and leaves recipes browse-only if the addon changes that verified inventory contract.

## Safety boundary

An adapter cannot directly commit inventory changes. It only returns a transfer definition. Slimefun Legacy validates that definition and performs the transaction.

Legacy rejects the transfer when:

- The target is not the exact expected Slimefun machine.
- The player lacks protection access or the block is not owned by the current Folia region.
- The menu is locked or currently viewed.
- The adapter fails its final safety hook.
- The recipe no longer matches the selected alternatives.
- Ingredient, input-slot, or protected-slot data is invalid.
- Input slots overlap protected slots.
- The player lacks ingredients or the machine cannot hold the requested sets.
- Commit validation fails; both inventories are restored.

## Deferred

- Additional addon-specific custom inventories discovered during server testing.
- Nearby storage withdrawal.
- Recursive sub-recipe crafting.
- Automatic machine processing or output generation.

---

### Archived source: `README.md`

# Slimefun Legacy Development History

This folder contains historical development notes, release notes, compatibility reviews, validation notes, and previous maintenance-phase documentation that used to live in the repository root.

They were moved here to keep the project root focused on the files contributors and server owners need most often. The documents themselves remain available for reference and CI verification.

## Core Platform phases

- `CORE_PLATFORM_PHASE1A.md` through `CORE_PLATFORM_PHASE1G.md`

## Release notes

- `SLIMEFUN_LEGACY_4.1.18.md` through `SLIMEFUN_LEGACY_4.1.25.md`
- `STABILITY_RELEASE.md`
- `SECOND_MAINTENANCE_RELEASE.md`
- `THIRD_MAINTENANCE_RELEASE.md`
- `FOURTH_MAINTENANCE_RELEASE.md`

## Compatibility and platform history

- `COMPATIBILITY_FOUNDATION.md`
- `COMPATIBILITY_MAINTENANCE_ROUND2.md`
- `PAPER_PURPUR_COMPATIBILITY.md`
- `FOLIA_PHASE1.md`
- `GUGU_UPSTREAM_SYNC.md`
- `CORE_CORRECTNESS_AUDIT.md`

## Guide, machine and validation history

- `ENHANCED_GUIDE.md`
- `PHASE4_1B_ADDON_ACONTAINER.md`
- `PHASE4_1B_CUSTOM_MACHINE_ADAPTERS.md`
- `PATCH_NOTES.md`
- `WORLD_EDIT_COORDINATE_FIX_VALIDATION.md`
- Slimefun Legacy 4.1.18 phase/validation/changed-file records

Current project documentation remains in `docs/`, while `README.md`, `CHANGELOG.md`, `CONTRIBUTING.md`, and `AGENTS.md` remain in the repository root.

---

### Archived source: `SECOND_MAINTENANCE_RELEASE.md`

# Slimefun Legacy — Second Maintenance Release

Status: implementation complete; dependency-resolved CI and staging-server validation required before deployment

This release finishes the Part 2 maintenance work without removing the legacy addon entry points protected by Slimefun Legacy's compatibility checks.

## Player-facing Cargo connector fix

Cargo and energy connector checks now consistently display:

- connected: `Connected: ✔`
- disconnected: `Connected: ✕`

The hardcoded `connectstate:` / `connectedstate:` wording is removed. Internal storage keys and network state are unchanged.

## Scheduler abstraction and ownership migration

Slimefun core scheduling now flows through the tracked `SlimefunScheduler` service.

Implemented scheduling modes:

- global immediate, delayed, and repeating tasks
- location-owned immediate, delayed, and repeating tasks
- entity-owned immediate, delayed, and repeating tasks
- asynchronous immediate, delayed, and repeating tasks
- scheduler-neutral `TaskHandle` cancellation
- centralized shutdown cancellation

Standard Paper retains Bukkit's tick-based timing. Folia-capable servers route known location- and entity-bound work through the corresponding region or entity scheduler.

The maintenance pass migrates the core ticker, storage loading, Item Doctor, armor and radiation processing, recipe-choice animation, teleports, research progression, machine animations, reactors, runes, Android work, Cargo/energy actions, holograms, chat callbacks, GitHub checks, profiler work, and command callbacks. Direct Bukkit scheduler usage is isolated to the scheduler implementation plus a deliberate shutdown fallback.

The historical `Slimefun.runSync(...)` signatures still return `BukkitTask`. A `LegacyBukkitTask` adapter preserves that return shape while routing execution through the tracked scheduler. New `runSyncAt(...)` and `runSyncFor(...)` overloads provide location and entity ownership to internal callers.

## Modern BlockTicker and energy overloads

### BlockTicker

A storage-neutral overload is available:

```java
tick(Block block, SlimefunItem item, ASlimefunDataContainer data)
```

It dispatches to the existing block or universal data overload. Existing `SlimefunBlockData`, `SlimefunUniversalData`, and deprecated `Config` override paths remain available for addon compatibility.

### Energy

Long-capacity energy operations now support already-resolved `ASlimefunDataContainer` instances for charge reads and set/add/remove mutations.

The long setter no longer uses legacy integer capacity or charge accessors. Capacity checks, clamping, overflow-safe addition, and texture updates use long values throughout, including capacities above `Integer.MAX_VALUE`.

## API and internal annotations

The release adds class-retained annotations:

- `@SlimefunAPI` for supported addon-facing contracts
- `@SlimefunInternal` for implementation details

The annotation inventory is complete for every public top-level type in the same package prefixes monitored by the binary API compatibility workflow:

- `io.github.thebusybiscuit.slimefun4.api`
- `io.github.thebusybiscuit.slimefun4.core.attributes`
- `io.github.thebusybiscuit.slimefun4.core.services.scheduling`
- `me.mrCookieSlime.Slimefun.Objects.handlers`
- `me.mrCookieSlime.Slimefun.api`

`scripts/check_api_annotations.py` prevents future public types in those boundaries from being left unclassified.

## Protection compatibility tests

Cargo nodes and legacy inventory blocks use one fail-closed protection policy.

The server-independent test matrix verifies:

- explicit bypass skips optional provider checks
- Slimefun's local denial remains authoritative
- provider allow and deny decisions are preserved
- provider runtime failures deny access
- missing or incompatible provider linkage denies access

This prevents broken optional protection integrations from silently granting access.

## Paper cleanup

The maintenance pass includes:

- Paper `AsyncChatEvent` with Adventure plain-text extraction
- Adventure action-bar messages instead of legacy Bungee action-bar dispatch
- thread-safe chat catcher, radiation grace-period, Cargo/altar/hook, elevator, and profiler state where scheduling can cross ownership boundaries
- entity- and location-owned callbacks for Bukkit world and inventory access
- nonblocking callable helpers backed by `CompletableFuture`
- `getTargetBlockExact(...)` for target lookup
- removal of legacy `scheduleSync...` and `BukkitRunnable` usage from core
- corrected wall-clock/nanosecond comparison in slow SQL detection
- corrected GitHub polling period units
- isolated suppression for the unit-test-only legacy `JavaPluginLoader` constructor

## Compatibility policy

This update is additive across the protected addon API surface. Legacy ticker overloads, storage bridges, integer energy methods, and `Slimefun.runSync(...)` descriptors remain present. New scheduler helpers and long-energy/container overloads do not replace existing public descriptors.

## Required release validation

Run the authoritative build in a dependency-enabled environment:

```bash
python3 scripts/verify_english.py .
python3 scripts/verify_chunk_load_threading.py .
python3 scripts/check_api_annotations.py
python3 scripts/verify_part2.py .
./gradlew spotlessCheck clean build --no-daemon
```

Then test the resulting JAR on a staging Paper server with the Albion addon set before production deployment.

---

### Archived source: `SLIMEFUN_LEGACY_4.1.18.md`

# Slimefun Legacy 4.1.18 — Guide & Runtime Stability

## Guide stability

- Guards classic and enhanced guide entry points, nested item groups, history restoration, search, bookmarks, pagination, item clicks, and addon `FlexItemGroup` menus.
- Blocks recursive calls and isolates addon runtime/linkage failures.
- Uses safe fallback icons and names for broken addon categories.
- Reports slow guide calls with player, mode, category key, category class, addon owner, nesting depth, and active call chain.
- Counts failures, recursion blocks, slow calls, fallbacks, and suppressed duplicate warnings, with periodic runtime summaries.

## Item Doctor stability

- A malformed stack can no longer terminate the complete scan or repair run.
- Runtime and addon linkage failures are counted, logged with safe item context, skipped, and scanning continues.
- Nested container failures are isolated the same way.
- Limited-use items without stored-use data or readable old lore fall back to their registered maximum instead of failing dynamic-state capture.
- Unknown IDs remain report-only and are never guessed or replaced.

## Machine reliability

- Auto Enchanter and Auto Disenchanter keep inputs untouched when another plugin cancels an event or a compatibility operation fails.
- Input stacks are validated before one item is consumed from each slot.
- Output capacity is checked before committing inputs.
- Processing time is never allowed to become zero ticks.
- The Auto Enchanter validates the final enchantment count, not only the incoming book.
- The Auto Disenchanter verifies every vanilla enchantment was removed and stored before accepting the operation.
- Visible status icons explain missing inputs, incompatible enchantments, full outputs, event cancellation, or blocked integration failures.
- The existing optional AdvancedEnchantments bridge remains supported without a hard dependency.

## Compatibility

- Primary: Paper 26.2 / Minecraft 1.21.11
- Secondary: Purpur based on Paper 26.2
- Runtime: Java 25
- Slimefun-owned bytecode target: Java 21
- Folia: experimental under the existing Phase 1 limitations

No item IDs, storage schemas, block data, backpack formats, or database formats are changed by this release.

---

### Archived source: `SLIMEFUN_LEGACY_4.1.18_CHANGED_FILES.txt`

Slimefun Legacy 4.1.18 final drop-in files

.github/workflows/finalize-slimefun-4.1.18.yml
scripts/apply_guide_runtime_phase1b.py
scripts/apply_release_4_1_18.py
scripts/package_release_4_1_18.py
scripts/verify_guide_runtime_phase1b.py
scripts/verify_legacy.py
scripts/verify_release_4_1_18.py
src/main/java/io/github/thebusybiscuit/slimefun4/api/items/groups/NestedItemGroup.java
src/main/java/io/github/thebusybiscuit/slimefun4/core/guide/GuideHistory.java
src/main/java/io/github/thebusybiscuit/slimefun4/core/guide/SlimefunGuide.java
src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/GuideRuntimeGuard.java
src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/electric/machines/enchanting/AutoDisenchanter.java
src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/electric/machines/enchanting/AutoEnchanter.java
src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/electric/machines/enchanting/EnchantmentMachineRuntime.java
SLIMEFUN_LEGACY_4.1.18.md
SLIMEFUN_LEGACY_4.1.18_CHANGED_FILES.txt
SLIMEFUN_LEGACY_4.1.18_VALIDATION.md
DROP-IN.txt

Generated and committed by the finalization workflow:
CHANGELOG.md
README.md
src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/ItemPresentationDoctor.java
src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/SurvivalSlimefunGuide.java
src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/EnhancedSurvivalSlimefunGuide.java

---

### Archived source: `SLIMEFUN_LEGACY_4.1.18_PHASE1A.md`

# Slimefun Legacy 4.1.18 — Guide & Runtime Stability, Phase 1A

## Included

This first 4.1.18 drop-in adds a central runtime boundary around public Slimefun Guide operations.

- Blocks the same guide operation from recursively reopening itself.
- Limits nested guide calls to 12 levels.
- Catches addon `RuntimeException`, `LinkageError`, and guide-specific `StackOverflowError` failures.
- Closes a broken menu instead of leaving the player trapped in it.
- Reports the guide mode, item-group key, group class, and owning addon in the console.
- Warns when a guide operation takes at least one second.
- Rate-limits repeated diagnostics to one message per minute for the same failure.
- Does not catch fatal JVM failures such as `OutOfMemoryError`.

## Scope

This phase protects the public `SlimefunGuide` entry points used by commands, guide history, addons, and custom category navigation.

The next 4.1.18 phase should move the same guard directly into classic and enhanced item-group rendering so every built-in category click and every `FlexItemGroup` call is protected at the final execution boundary.

---

### Archived source: `SLIMEFUN_LEGACY_4.1.18_PHASE1B.md`

# Slimefun Legacy 4.1.18 — Guide Runtime Stability Phase 1B

Phase 1B moves the Phase 1A runtime guard into the guide's internal navigation paths. This prevents addon menus from bypassing protection simply because they were opened from a click handler, nested category, bookmark, search page, or history entry instead of the public `SlimefunGuide` utility.

## Runtime behavior

- Guards direct `FlexItemGroup#open` calls in both classic and enhanced guides.
- Routes classic and enhanced category pagination back through the guarded public guide entry points.
- Guards guide history restoration and back-button dispatch for main menus, item groups, Slimefun items, vanilla recipe pages, and searches.
- Guards `NestedItemGroup` root opening and page navigation.
- Isolates nested subgroup visibility and icon failures so one malformed addon category can be skipped instead of terminating the whole menu.
- Adds barrier fallback icons and names when an addon category cannot render its icon.
- Guards the enhanced guide's private bookmark and smart-search pages.
- Routes item and recipe ingredient clicks through guarded display entry points.

## Diagnostics

A failure report now includes:

- player name and UUID;
- guide mode;
- operation being performed;
- current nesting depth;
- active guide call chain;
- item-group key and implementation class;
- owning addon name;
- exception type;
- elapsed time for slow guide calls.

Warnings remain rate-limited to prevent a broken category from flooding the console.

## Compatibility boundary

The existing public guide method descriptors remain unchanged. Phase 1B adds internal routing and defensive rendering without relocating guide APIs or changing addon registration contracts.

## Source updater

The classic and enhanced guide implementations are large, actively changing files. This package therefore includes an idempotent source updater and a GitHub Actions workflow rather than a patch file. The updater recognizes the current 4.1.17/4.1.18 guide structure, refuses an unexpected source layout, and marks successfully transformed files so rerunning it is safe.

---

### Archived source: `SLIMEFUN_LEGACY_4.1.18_PHASE1B_CHANGED_FILES.txt`

Slimefun Legacy 4.1.18 Phase 1B included/new files

.github/workflows/apply-guide-runtime-phase1b.yml
scripts/apply_guide_runtime_phase1b.py
scripts/verify_guide_runtime_phase1b.py
scripts/verify_legacy.py
src/main/java/io/github/thebusybiscuit/slimefun4/api/items/groups/NestedItemGroup.java
src/main/java/io/github/thebusybiscuit/slimefun4/core/guide/GuideHistory.java
src/main/java/io/github/thebusybiscuit/slimefun4/core/guide/SlimefunGuide.java
src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/GuideRuntimeGuard.java
SLIMEFUN_LEGACY_4.1.18_PHASE1B.md
SLIMEFUN_LEGACY_4.1.18_PHASE1B_VALIDATION.md
SLIMEFUN_LEGACY_4.1.18_PHASE1B_CHANGED_FILES.txt
DROP-IN.txt

Files generated in the repository by the included workflow

src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/SurvivalSlimefunGuide.java
src/main/java/io/github/thebusybiscuit/slimefun4/implementation/guide/enhanced/EnhancedSurvivalSlimefunGuide.java

---

### Archived source: `SLIMEFUN_LEGACY_4.1.18_PHASE1B_VALIDATION.md`

# Slimefun Legacy 4.1.18 Phase 1B Validation

## Completed locally

- Python syntax compilation passed for the source updater, Phase 1B verifier, and consolidated Legacy verifier.
- Source updater fixture test passed for the current classic-guide source patterns.
- Source updater fixture test passed for the current enhanced-guide source patterns.
- Reapplying the source updater produced no additional changes.
- The Phase 1B verifier passed against the transformed fixture tree.
- Focused Java 21 compilation passed for `GuideRuntimeGuard` using API-compatible stubs.
- Focused Java 21 compilation passed for `GuideHistory` using API-compatible stubs.
- Focused Java 21 compilation passed for `NestedItemGroup` using API-compatible stubs.
- The included GitHub Actions workflow parsed successfully as YAML.
- ZIP integrity and SHA-256 generation are performed during packaging.

## Build authority

The included workflow runs the repository's complete source verification, Spotless checks, Gradle tests, and build before committing the generated classic and enhanced guide files. That GitHub Actions result is the authoritative full-project validation.

## Safety boundaries retained

- Maximum guide call depth remains 12.
- Repeat recursion is detected by operation, mode, and item-group key.
- Only runtime guide failures, linkage failures, and stack overflows are intercepted.
- Fatal JVM errors are not swallowed.
- Player-facing failures close the broken menu and provide a short administrator-directed message.
- Individual visibility, icon, and name rendering failures use fallback values without closing a valid surrounding menu.
- Diagnostics are rate-limited to one equivalent warning per minute.

---

### Archived source: `SLIMEFUN_LEGACY_4.1.18_VALIDATION.md`

# Slimefun Legacy 4.1.18 validation

The drop-in contains permanent static verification for all new invariants and a finalization workflow that performs:

1. Idempotent classic/enhanced guide source generation.
2. Idempotent Item Doctor and release-document generation.
3. Spotless formatting.
4. The complete `scripts/verify_legacy.py` verification chain.
5. `spotlessCheck`, clean compilation, and tests using Java 25 with `-PprojectVersion=4.1.18`.
6. Release JAR selection, deterministic naming, and SHA-256 generation.
7. Artifact upload containing the JAR and checksum.

Local package checks cover Python syntax, updater idempotence against representative current source, Java delimiter balance, required source markers, workflow YAML parsing, ZIP integrity, and SHA-256 integrity. GitHub Actions remains the authoritative dependency-resolved Java compilation and test environment.

---

### Archived source: `SLIMEFUN_LEGACY_4.1.19.md`

# Slimefun Legacy 4.1.19 — Core Platform Foundation

## Core compatibility

- Added an addon-facing capability-based platform compatibility service.
- Added immutable Paper, Purpur, Folia, Paper-derivative, support-level, Java, Minecraft-version, and runtime-capability diagnostics.
- Added semantic Minecraft version parsing that is independent of historical enum ordering.
- Centralized startup version parsing while retaining the existing `MinecraftVersion` enum and public compatibility signatures.
- Aligned the startup Java recommendation with the Java 21 bytecode contract.
- Expanded `/sf versions` with the detected platform profile and capability inventory.

## Future update workflow

- Added a machine-readable registry for Original Slimefun, Gugu, Slimefun5, Slimefun United, and Slimefun4Core.
- Added a reviewed feature backlog so useful ideas can be scheduled without silently enabling them.
- Added an advisory upstream candidate checker and weekly GitHub Actions report.
- Kept the existing guarded Gugu merge workflow as the only code-merge upstream path.
- No workflow automatically merges, replaces, or downloads source into the Legacy branch.

## Compatibility

- No item IDs, research IDs, recipes, storage keys, database schemas, or gameplay behavior changed.
- Existing addon API signatures remain available.
- The new platform API is additive and covered by source and unit-test invariants.
- Paper remains primary, Purpur supported, conventional Paper derivatives best effort, and Folia experimental.

---

### Archived source: `SLIMEFUN_LEGACY_4.1.20.md`

# Slimefun Legacy 4.1.20 — Core Platform Phase 1B

This release continues the compatibility-first core modernization started in 4.1.19. It does not remove or rename existing addon APIs and does not change Slimefun items, recipes, storage, databases, or gameplay.

## Addon-facing compatibility requirements

Addons can now describe what they need and let Slimefun evaluate the running server:

```java
PlatformRequirements requirements = PlatformRequirements.builder()
        .minimumMinecraftVersion(1, 21, 11)
        .minimumJavaVersion(21)
        .requireCapability(PlatformCapability.PAPER_API)
        .acceptFamilies(PlatformFamily.PAPER, PlatformFamily.PURPUR)
        .build();

PlatformCompatibilityReport report =
        Slimefun.getPlatformCompatibilityService().check(requirements);

if (!report.isCompatible()) {
    getLogger().warning(report.describe());
}
```

The compatibility service also provides additive default helpers for Minecraft version comparisons, Java version checks, Paper compatibility, platform family checks, and Folia region-owned execution. Default methods preserve binary compatibility with any third-party implementation compiled against 4.1.19.

## Centralized runtime detection

Paper, Purpur, Folia, scheduler, Adventure, data-component, async chunk-loading, and player pick-block event probes now pass through one internal detector. Startup validation, scheduler routing, error reports, guide diagnostics, event threading, and optional Paper behavior consume that shared result instead of repeating PaperLib, server-name, or implementation-class checks.

The scheduler no longer freezes Folia detection in a static constant. The normal Slimefun instance injects the initialized platform service, while the original `PaperScheduler(Plugin)` constructor remains available as a compatibility bridge.

## API deprecation lifecycle

The new `@SlimefunDeprecated` annotation records:

- the first Legacy version that deprecated an API;
- the recommended replacement;
- an optional earliest removal version.

An empty removal version means removal is not scheduled. Java's normal `@Deprecated` annotation remains required. `FoliaSupport` is the first documented bridge: it remains callable, but new code should use the platform compatibility service or scheduler service.

## Compatibility guarantees

- Existing addon APIs remain present.
- `FoliaSupport.isFolia()` remains present.
- `PaperScheduler(Plugin)` remains present.
- The historical `MinecraftVersion` enum remains present.
- Existing scheduler interfaces and task handles remain present.
- No data format or gameplay behavior changes are included.

## Verification

Phase 1B adds permanent static checks that prevent new direct PaperLib checks, direct Paper/Folia implementation probes outside the detector, and region-scheduler calls outside the scheduler implementation. A checked-in 4.1.19 signature baseline verifies that all 991 compatibility-protected public and protected declarations remain present while additive APIs are allowed. Unit tests cover declarative requirements, complete incompatibility reporting, immutable results, and compatibility-service default methods.

---

### Archived source: `SLIMEFUN_LEGACY_4.1.21.md`

# Slimefun Legacy 4.1.21 — Core Platform Phase 1C

## Addon compatibility infrastructure

- Added addon compatibility declarations through explicit registration, a provider interface, or `slimefun-compatibility.json`.
- Added runtime diagnostics for tested core variants, platform requirements, required dependencies, and optional integrations.
- Added a centralized optional-dependency and guarded-reflection service.
- Added `/sf doctor compatibility` and compatibility status details to `/sf versions`.
- Kept undeclared addons loadable and treated inactive optional integrations as informational.

## Release gates

- Added a machine-readable representative addon matrix and core API registry.
- Added dynamic GitHub Actions matrix generation.
- Extended addon comparison to verify both source compilation and precompiled binary linkage.
- Added missing-class, missing-method, and missing-field detection across compatibility-protected Slimefun namespaces.
- Added permanent Phase 1C verification and synthetic linkage regression tests.

## Compatibility

- Existing addon APIs remain available.
- No item IDs, recipes, storage keys, database schemas, saved-world formats, or gameplay behavior changed.
- Required compatibility targets block candidate-only regressions; independently maintained probes remain advisory.

---

### Archived source: `SLIMEFUN_LEGACY_4.1.22.md`

# Slimefun Legacy 4.1.22 — Core Platform Phase 1D

## Stability and compatibility lifecycle

- Replaced the stale release-blocking 4.1.15 addon baseline with **4.1.21**, the previous stable Legacy release.
- Added a central `compatibility/release-baselines.json` registry so the active baseline is no longer duplicated in workflow YAML.
- Kept **4.1.15** as a separate advisory historical compatibility floor instead of deleting it.
- Unified the public API workflow and addon compatibility workflow around the same pinned previous-stable baseline.

## Better regression classification

- Required addons now block a candidate only when they build against 4.1.21 and fail against 4.1.22.
- A failure against the historical 4.1.15 floor is reported as compatibility drift and remains advisory.
- Baseline source refs are pinned for reproducible CI behavior.
- Added lifecycle summaries showing the candidate, previous stable baseline, and historical floor used by each run.

## Wider addon coverage

- Retains required Legacy probes for FastMachines, Networks Expansion, SlimeTinker IE2, and BetterChests.
- Retains representative checks for Networks, Infinity Expansion 2, DynaTech, Supreme, Magic Expansion, FluffyMachines, FastMachines, and SlimeTinker.
- Adds advisory Gugu checks for FoxyMachines, FlowerPower, IDreamOfEasy, Gastronomicon, Bump, SlimeCustomizer, and EMCTech.

## Future-proof verification

- Phase 1A-1C verifiers now accept future releases without manually adding every new version number.
- Added a permanent Phase 1D verifier for baseline lifecycle consistency.
- Candidate Paper API compilation stays available as an early-warning, non-blocking compatibility probe.

## Compatibility

- No existing addon API signatures were removed.
- No item IDs, research IDs, recipes, storage keys, database schemas, saved-world formats, or gameplay behavior changed.
- Paper 26.2 / Minecraft 1.21.11 remains the primary production target, Purpur remains supported, and Folia remains experimental.

---

### Archived source: `SLIMEFUN_LEGACY_4.1.23.md`

# Slimefun Legacy 4.1.23 — Core Platform Phase 1E (Development)

## Runtime stability

- Added live machine failure diagnostics with owner, location, item, cause and retry information.
- Added configurable machine failure thresholds and automatic temporary isolation for repeatedly failing machine locations.
- Protected deferred synchronized machine callbacks and rate-limited repeated ticker lifecycle failures.
- Added `/sf doctor runtime`, `/sf doctor runtime retry`, and `/sf doctor runtime retry all`.
- Retains ticker registrations and stored machine data during isolation/retry.

## Rebar/Pylon integration foundation

- Added an additive capability/provider API for external inventories, storage, cargo, machines, energy and fluids.
- Added reflection-only Rebar/Pylon block discovery with no hard dependency.
- Added `/sf doctor integrations` and `/sf doctor integrations probe`.
- Added independent failure isolation for external provider status/probe callbacks.
- Added `/sf doctor integrations retry <id|all>` and `/sf doctor integrations reload`.
- External systems remain discovery-only until a compatible provider explicitly implements transfer semantics.

## Clearer `/sf versions` compatibility report

- Replaced the ambiguous blue `[Undeclared]` label with `? Compatibility not verified`.
- Every addon now shows a readable compatibility result beside its name and version.
- Added an overall compatibility summary and a short explanation for addons that do not declare Legacy compatibility.
- Hover details still show the declaration source and exact warning/incompatibility reasons.

## Compatibility safeguards

- Existing 991 compatibility-protected public/protected API signatures remain a release gate.
- Part 3 includes a hash guard proving that normal Slimefun Cargo, Energy, NetworkManager, Guide, SlimefunItem, BlockTicker, AContainer and the green Part 1 TickerTask are unchanged.
- New external integration recovery API methods are additive Java default methods.
- No item IDs, recipes, storage keys, database schemas, saved-world formats, normal cargo behavior, or normal energy behavior changed.
- Rebar/Pylon cargo transfer and energy exchange remain disabled in 4.1.23.

---

### Archived source: `SLIMEFUN_LEGACY_4.1.24.md`

# Slimefun Legacy 4.1.24 — Core Platform Phase 1F (Development)

## Better addon compatibility reporting

- `/sf versions` now distinguishes declared compatibility, known CI-monitored addon families, compatibility warnings, unknown addon compatibility, disabled addons, and declared incompatibilities.
- Common addon runtime names such as Networks, InfinityExpansion2, MagicExpansion, Supreme, DynaTech, FluffyMachines, FoxyMachines, FlowerPower, IDreamOfEasy, Gastronomicon, Bump, SlimeCustomizer, EMCTech, BetterChests, NetworksExpansion, FastMachines, and SlimeTinker are recognized through the same addon families monitored by Legacy CI.
- CI-monitored addons are not falsely promoted to exact-build compatibility. The report explicitly explains that the installed JAR may differ from the build tested by CI.
- Unknown addons are described as Slimefun addons with unknown compatibility rather than as unrecognized/broken.
- Addon lines are sorted alphabetically for easier server audits.

## Compatibility

- Existing 991 compatibility-protected API signatures remain unchanged.
- Existing Phase 1E runtime protection and Rebar/Pylon discovery remain intact.
- No item IDs, recipes, research IDs, storage keys, database schemas, saved-world formats, Cargo behavior, Energy behavior, machine behavior, or guide behavior are changed by this update.

## Phase 1F Part 2 — compatibility evidence

- Adds `/sf doctor compatibility` evidence reporting with registry tier, declaration source, runtime load state, active
  machine-failure state, and safe compatibility-layer linkage signals.
- Adds a `Recognized` tier for addon families Legacy can identify but does not currently CI monitor.
- Adds recognition aliases for Better Farming, DankTech2, Cultivation, Electric Spawners, ExtraTools,
  GeneticChickengineering, HotbarPets, Magic 8 Ball, MobCapturer, SFMobDrops, SlimefunAdvancements, SlimeGlue,
  SimpleMaterialGenerators, and SoulJars.
- Recognition does not mark an addon compatible. Undeclared addons remain undeclared at the public API layer.

## Phase 1F Part 2.1 — compact version report

- `/sf versions` now keeps each addon to a compact `Name version — Status` line.
- Status output uses short words only: `Compatible`, `Known`, `Recognized`, `Warning`, `Unknown`, `Incompatible`, or `Disabled`.
- Hovering the status shows the full compatibility evidence and reason.
- Long custom build/version labels are shortened only on-screen; hovering the version shows the full exact build string.

---

### Archived source: `SLIMEFUN_LEGACY_4.1.25.md`

# Slimefun Legacy 4.1.25 — Core Platform Phase 1G (Development)

## Core lifecycle and scheduler foundation

- Adds observable startup/shutdown lifecycle state and phases.
- Adds ordered shutdown failure isolation so one cleanup failure cannot prevent unrelated later cleanup work.
- Adds scheduler quiesce/health reporting while retaining all existing scheduler APIs.
- Explicitly shuts down Slimefun-owned executor pools during plugin shutdown.
- Keeps Paper and experimental Folia scheduling behind the existing centralized scheduler abstraction.

## Machine and storage runtime modernization

- Adds stable machine-runtime and read-only storage-runtime facades.
- Adds immutable runtime snapshots for diagnostics and future core modernization.
- Moves Doctor health/recovery reporting toward these facades instead of exposing implementation details.
- Does not change machine processing, storage schemas, saved data, Cargo, or Energy behavior.

## Addon runtime hardening

- Adds non-invasive addon callback health telemetry for failures already contained by Slimefun.
- Tracks guarded compatibility-provider, addon item-load, and third-party integration callback failures.
- Adds `/sf doctor core` for lifecycle, scheduler, machine, storage, and addon callback health.
- Adds callback-health evidence to focused `/sf doctor compatibility <addon>` reports.
- No addon is automatically disabled by this telemetry.

## Compatibility guarantees

- The 991 compatibility-protected 4.1.19 API signatures remain release-gated.
- Phase 1E still hash-protects normal Cargo, Energy, NetworkManager, Guide, `SlimefunItem`, `BlockTicker`, `AContainer`, and `TickerTask` implementations.
- No item IDs, recipes, research IDs, storage keys, database schemas, saved-world formats, or normal gameplay semantics are intentionally changed.

---

### Archived source: `STABILITY_RELEASE.md`

# Slimefun Legacy — Stability Release 1

Build version: `Legacy-Stability-1-Hotfix-1`

This release is the first Albion Slimefun Legacy stability release. It combines compatibility safeguards, storage recovery tooling, machine fault isolation, backpack race protection, and Cargo performance work without introducing the separate Part 2 API modernization changes.

## Hotfix 1: Paper 26.2 chunk-load thread safety

This package includes the runtime fix for `SlimefunChunkDataLoadEvent may only be triggered synchronously`. The Item Doctor now waits for `SlimefunChunkDataLoadEvent` instead of requesting an asynchronous chunk load from `ChunkLoadEvent`. The shared `getChunkDataAsync` API also schedules unloaded chunk initialization on the primary server thread, preventing GEO systems and addons from triggering the same Paper exception. Slimefun machine menus are repaired after their database data is ready, using bounded two-tick retries without blocking a server tick.

## Main additions

### Slimefun Storage and Item Doctor

The operator command is available as either `/slimefun doctor` or `/sf doctor`.

| Command | Purpose |
| --- | --- |
| `/sf doctor status` | Shows clean-shutdown state, pending database writes, paused machine circuits, automatic repairs, and the current/last doctor run. |
| `/sf doctor hand` | Repairs the Slimefun item held by the executing player. |
| `/sf doctor inventory [player]` | Repairs an online player's inventory and ender chest. |
| `/sf doctor scan` | Runs a batched server-wide dry run. No items are changed. |
| `/sf doctor repair confirm` | Runs the batched server-wide repair. |

Permission: `slimefun.command.doctor` (operator by default).

The doctor identifies an item through its persistent Slimefun ID. It does not infer identity from translated text. It changes only visible display names and lore, then restores recognized dynamic presentation from the original item.

Preserved data includes:

- Slimefun item ID and persistent data
- enchantments, attributes, custom model data, stack amount, and material
- energy charge and remaining-use counts
- monster-spawner type
- Soulbound state
- Knowledge Tome owner identity
- current and legacy backpack identity and ownership
- items stored inside bundles and shulker boxes
- addon lore numbers and UUIDs when they map safely to the registered English template

Unknown Slimefun IDs, templates that remain translated, malformed state, and ambiguous dynamic lore are reported and skipped. The doctor does not guess.

### Repair coverage

The server-wide run covers:

- online player inventories and ender chests
- loaded chests, barrels, entities, machines, and dropped items
- loaded Slimefun block and universal inventories
- every Slimefun backpack stored in the configured database
- nested bundles and shulker boxes, up to four container levels

Offline player inventories are repaired when the player next joins. Unloaded world storage is repaired when its chunk or inventory is next loaded. These automatic paths are configurable under `stability.item-doctor` in `config.yml`.

## Stability work included

- Backpack duplicate-open protection and disconnect/failure cleanup
- Race-safe maintenance loading for database backpack scans
- Cargo network topology and allocation optimization for issue #1223
- Clean-shutdown marker and pending-write visibility
- Per-machine ticker circuit breaker with cooldown and administrator retry commands
- Viewer, ticker, and chunk lifecycle regression coverage
- Addon compatibility CI against the exact built Slimefun JAR
- Public API binary compatibility reporting

## Recommended rollout

1. Stop the server normally.
2. Back up the full server, all worlds, player data, and the complete `plugins/Slimefun` directory/database.
3. Install the release on a staging copy first.
4. Start the server and run `/sf doctor status`.
5. Run `/sf doctor scan` and review unknown IDs, unresolved templates, and failures.
6. Test representative machines, backpacks, Cargo networks, recipes, and addon items.
7. Run `/sf doctor repair confirm` only after the dry run looks correct.
8. Keep the server running until `/sf doctor status` reports `0` pending database writes.
9. Stop the server normally and start it again.
10. Spot-check repaired items in player storage, chests, machines, backpacks, shulkers, and bundles.

Do not use `/reload` during a repair. Do not force-kill the server while database writes are pending.

## Configuration

```yaml
stability:
  machine-circuit-breaker-cooldown-seconds: 300
  item-doctor:
    enabled: true
    repair-player-on-join: true
    repair-opened-inventories: true
    repair-chunks-on-load: true
    repair-picked-up-items: true
    inventories-per-tick: 12
```

Missing settings receive these defaults during configuration loading, so upgrades from an older `config.yml` still enable the intended behavior.

## Building

The manual GitHub workflow **Build Stability Release** performs the authoritative release build with Java 25 while targeting Java 21 bytecode. It runs English verification, Spotless, tests, the shaded build, source packaging, and SHA-256 generation.

Local command:

```bash
chmod +x gradlew
./gradlew spotlessApply --no-daemon
./gradlew spotlessCheck clean build -PprojectVersion=Legacy-Stability-1-Hotfix-1 --no-daemon
```

Expected primary artifact:

```text
build/libs/Slimefun-Legacy-Stability-1-Hotfix-1.jar
```

---

### Archived source: `THIRD_MAINTENANCE_RELEASE.md`

# Slimefun Legacy — Third Maintenance Release

## Storage Safety & Data Modernization

This release modernizes Slimefun Legacy's persistent ItemStack storage while retaining the older public String-based methods used by existing addons.

## Main changes

- Raises the database schema from version 2 to version 3.
- Stores backpack, block-menu, and universal inventory items as versioned binary ItemStack data.
- Uses Paper's native ItemStack binary format for new records.
- Reads and migrates legacy Base64/Bukkit object-stream items in place.
- Preserves legacy skull profiles, texture properties, old ItemMeta payloads, and persistent data where Paper can recover them.
- Adds binary inventory columns for MySQL, SQLite, and PostgreSQL.
- Adds a retry-safe migration that publishes schema version 3 only after every migration step succeeds.
- Defers universal-block location resolution when its world is temporarily unavailable.
- Corrects PostgreSQL inventory slot types and metadata SQL portability.

## Addon compatibility

The following descriptors remain available:

- `DataUtils.serializeItemStack(ItemStack): String`
- `DataUtils.deserializeItemStack(String): ItemStack`
- `RecordSet.getAll(): Map<FieldKey, String>`
- `RecordSet.get(FieldKey): String`
- `SqlUtils.buildKvStr(FieldKey, String): String`
- `SqlUtils.toSqlValStr(FieldKey, String): String`

New core storage code uses binary overloads internally. Older addons can continue consuming Base64 String views without being required to recompile for this release.

## Database safety

**Back up all Slimefun databases before the first startup with this release.**

The first startup upgrades inventory storage to schema 3. The version record is written only after the migration succeeds. Failed item rows prevent version publication so the migration can be inspected and retried.

MySQL can implicitly commit table-alter operations. Even though the row migration and schema-version publication are guarded, restoring a backup remains the only supported downgrade path.

Do not downgrade to a schema-2 build against a database that has been opened by this release. Restore the pre-upgrade backup first.

## Recommended upgrade procedure

1. Stop the server completely.
2. Copy the complete Slimefun plugin data directory and every configured SQL database.
3. Keep the backup outside the live server directory.
4. Install the new JAR and start the server once with players offline.
5. Wait for the database migration completion message.
6. Review the log for any item migration failures.
7. Test backpacks, Cargo storage, machine inventories, universal storage, custom skulls, and addon items.
8. Reopen the server only after those checks pass.

## Validation included

- Public storage API descriptor tests
- SQLite schema-2 to schema-3 end-to-end migration test
- Migration idempotency test
- Transaction rollback and version-publication ordering tests
- Optional copied-production SQLite migration test
- Legacy skull profile repair test
- Missing-world location deferral test
- MySQL, SQLite, and PostgreSQL schema invariants
- Part 1 and Part 2 regression checks

---

### Archived source: `WORLD_EDIT_COORDINATE_FIX_VALIDATION.md`

# WorldEdit Coordinate Accessor Fix

The prior maintenance build changed `getBlockX/Y/Z()` to `getX/Y/Z()`, but the current
WorldEdit `BlockVector3` API marks both getter families for removal.

This correction uses the record component accessors:

- `x()`
- `y()`
- `z()`

The compatibility verifier now rejects both deprecated getter families and requires
the record accessors.

Only WorldEdit coordinate access and related documentation/verification changed.
