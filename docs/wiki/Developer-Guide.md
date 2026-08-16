# 🧑‍💻 Developer Guide

Slimefun Legacy aims to preserve the established Slimefun addon API while adding optional compatibility and integration APIs for modern servers.

## Design goal

A normal historical addon should not be forced to adopt Legacy-only APIs just to load. Legacy-specific declarations and services are intended to be **additive**.

## Compatibility declaration

Addons can optionally describe their tested environment through `AddonCompatibilityDeclaration`, `AddonCompatibilityProvider`, or an embedded `slimefun-compatibility.json`.

Example manifest shape:

```json
{
  "schema": 1,
  "tested_core_variants": ["legacy"],
  "minimum_minecraft": "1.21.11",
  "minimum_java": 21,
  "required_capabilities": ["PAPER_API"],
  "accepted_platform_families": ["PAPER", "PURPUR"]
}
```

See `docs/ADDON_COMPATIBILITY.md` and the schema in the repository before publishing a declaration.

## Optional dependency service

For optional integrations, prefer Legacy's centralized discovery/guarded invocation facilities where appropriate rather than scattering unsafe reflective calls through the addon.

See:

- `docs/PLATFORM_COMPATIBILITY_API.md`
- `docs/ADDON_DOCTOR_API.md`

## Machine recipe integration

Legacy exposes APIs intended to make addon machines easier for the Enhanced Guide and automation tooling to understand:

- `docs/MACHINE_RECIPE_PROVIDER_API.md`
- `docs/MACHINE_INPUT_FILL_ADAPTER_API.md`

Use these when your addon has custom inventories or recipes that cannot be inferred safely.

## Scheduler discipline

Do not assume every callback is safe on one global server thread. Paper remains the primary target, but location/entity ownership matters for Folia compatibility. Keep scheduler access behind appropriate abstractions and do not perform unsafe world access asynchronously.

## Preserve data identity

Changing IDs, persistent-data keys or saved block/item formats can destroy compatibility even when code compiles. Treat persistent identifiers as a public contract.

## Build and test strategy

Before releasing an addon for Legacy:

1. Compile against the intended Slimefun API/core.
2. Test on the current primary Paper target.
3. Start with a clean server.
4. Test an upgrade with representative saved data.
5. Exercise registration, Guide menus, recipes, storage and machines.
6. Run `/sf versions` and compatibility diagnostics.
7. If claiming Folia support, test Folia independently.

The original Slimefun developer wiki is still valuable for core concepts such as item registration, item groups, recipes and researches. Use it as conceptual background, then validate APIs against the current Legacy source and documentation.

## Compatibility is more than compilation

Legacy's own release process performs source-build probes and binary-linkage analysis for representative addons. Addon authors should think the same way: a successful compile does not prove an older precompiled JAR will link at runtime.