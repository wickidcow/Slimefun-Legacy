# ADR 0001: Capability-based platform compatibility

- Status: Accepted
- Date: 2026-08-05

## Context

Slimefun Legacy contains historical Minecraft enums, Paper checks, Folia checks, implementation-class probes, and compatibility bridges from multiple upstream lines. Repeating these decisions throughout core and addons makes each server update harder and encourages brittle platform-name checks.

## Decision

Introduce one read-only platform compatibility service with:

- semantic numeric Minecraft parsing;
- an immutable platform profile;
- explicit support levels;
- conservative runtime capability probes;
- an additive addon-facing API exposed by `Slimefun`.

Retain the historical `MinecraftVersion` enum and existing compatibility methods. Migrate core decisions gradually instead of removing old signatures.

## Consequences

Future API or platform support can be added in one detector and exposed as a new capability. Addons can choose safe fallbacks without hard-linking implementation classes. There is some temporary duplication while old checks are migrated, but binary compatibility is preserved.
