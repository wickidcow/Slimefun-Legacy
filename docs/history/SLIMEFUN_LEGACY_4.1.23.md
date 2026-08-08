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
