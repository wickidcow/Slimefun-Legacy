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
