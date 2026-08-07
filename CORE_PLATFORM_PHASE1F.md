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
