# Slimefun Legacy 4.1.29 — Core Platform Phase 1K Part 3

## Dependency Contract & Packaging Audit

Phase 1K Part 3 turns the dependency boundary established in Parts 1 and 2 into a release-blocking source contract.

### What is now protected

- Slimefun must not add a hard external plugin dependency in `plugin.yml`.
- Slimefun must not use `provides:` to impersonate another plugin.
- GuizhanLibPlugin remains an external dependency for addons that require it; Slimefun does not provide or emulate that plugin.
- Optional integration APIs remain `compileOnly` dependencies.
- Optional integration APIs exclude transitive dependencies so their libraries cannot silently leak into the Slimefun JAR.
- Optional integration APIs may not be promoted to `implementation` or published API dependencies.
- Authlib remains compile-only platform glue and is not bundled.
- Libraries intentionally bundled by Slimefun remain relocated into Slimefun's private namespace.
- Slimefun's private GuizhanLib classes remain relocated under `io.github.thebusybiscuit.slimefun4.libraries.guizhanlib` and are explicitly not a GuizhanLibPlugin replacement.

### Protected optional integrations

The verifier protects the current optional-plugin descriptor/build contract for:

- PlaceholderAPI
- WorldEdit
- ClearLag
- mcMMO
- ItemsAdder
- Vault
- Orebfuscator

`ChestTerminal` and `SlimeGlue` remain `loadBefore` ordering hints rather than hard dependencies.

### CI behavior

`scripts/verify_phase1k_dependency_contract.py` is executed by the normal `scripts/verify_legacy.py` suite. A future change that accidentally converts an optional integration into a bundled or hard dependency will fail CI before the release JAR is accepted.

### Runtime safety

Part 3 changes no gameplay or persistence behavior. It does not change Cargo, Energy, machines, recipes, item IDs, storage keys, databases, saved-world formats, or Item Doctor behavior. The change is a build/source safety gate only.
