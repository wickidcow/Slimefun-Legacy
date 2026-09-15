# 🩺 Doctor & Diagnostics

Slimefun Legacy's Doctor tools are designed to answer a simple question: **what layer is actually failing?** Core, addon, dependency, item data, machine runtime or optional integration?

Permission: `slimefun.command.doctor` (operator by default).

## Start here

```text
/sf doctor status
/sf doctor core
/sf doctor compatibility
/sf doctor dependencies
/sf doctor runtime
/sf doctor integrations
```

These read-only checks should be your first step after an upgrade or when an addon begins behaving strangely.

## What the major checks are for

| Check | Use it for |
| --- | --- |
| `status` | Shutdown state, pending writes, paused machine circuits and repair state |
| `core` | Core/platform health evidence |
| `compatibility` | Addon compatibility declarations and runtime evidence |
| `dependencies` | Declared plugin dependencies, missing/disabled requirements and provider aliases |
| `runtime` | Machine/runtime isolation and retry state |
| `integrations` | Optional external integration capabilities and failures |

## Storage & Item Doctor

Older translated or migrated item stacks can retain display metadata because Minecraft stores that information in the item itself.

Legacy's repair path is intentionally conservative. It identifies recognized items by persistent Slimefun ID and skips data it cannot safely interpret.

Useful commands:

```text
/sf doctor hand
/sf doctor inventory [player]
/sf doctor scan
/sf doctor repair confirm
```

### Safe repair workflow

1. Make a full backup.
2. Run `/sf doctor status`.
3. Run `/sf doctor scan` for a dry run.
4. Review unknown IDs, failures and skipped items.
5. Only then run `/sf doctor repair confirm` if the result is acceptable.

Never treat “0 repaired” as proof that a scan failed; a clean server may simply have nothing eligible to change.

## Legacy upgrade workflow

The unified upgrade view helps server owners understand old-world migration work without bypassing the guarded migration systems owned by Slimefun and its addons.

```text
/sf doctor upgrade
/sf doctor upgrade status
/sf doctor upgrade scan
/sf doctor upgrade plan
/sf doctor upgrade providers
```

Plain `/sf doctor upgrade` remains the existing core/runtime readiness snapshot. The subcommands above add migration discovery and planning around that snapshot.

`/sf doctor upgrade plan` also performs read-only persisted-storage audits. Those audits **do not create an execution fingerprint**. Every mutation lane still requires its own fresh native scan and explicit fingerprinted execute command.

### Recommended sequence

1. Make or verify a current full backup.
2. Run `/sf doctor upgrade status`.
3. Run `/sf doctor upgrade scan` and wait for the read-only traversal to finish.
4. Run `/sf doctor upgrade plan` and review every lane, including the persisted-storage summary.
5. Resolve anything shown as `NEEDS PROVIDER`, `MANUAL/BLOCKED`, unreadable storage, unknown IDs, missing targets or a traversal failure before treating the migration picture as clean.
6. For legacy item-ID candidates, run the exact `/sf doctor migrations scan <plugin>` command shown by Doctor.
7. For same-ID item-schema candidates, run `/sf doctor migrations schemas scan` and follow only the exact fingerprinted execution command it prints.
8. For addon-owned placed machines, run `/sf doctor migrations blocks scan <plugin>` for each relevant provider. This lane covers only currently loaded supported scope and never force-loads chunks.
9. For persisted block/universal legacy IDs, run `/sf doctor migrations schemas blocks scan`, then use only the fingerprinted execute command printed by that scan. Loaded/cached records remain protected and may require unloading/restarting before a later scan can rewrite them.
10. For legacy persisted inventory payloads, run `/sf doctor migrations schemas storage scan`, then use only the fingerprinted execute command printed by that scan. Unreadable legacy payloads block execution rather than being guessed or discarded.
11. Re-run `/sf doctor upgrade scan` and `/sf doctor upgrade plan` after migrations. A clean persisted-storage summary should show no ready, loaded/deferred or manual/blocked storage work.
12. Load representative old-server regions and re-run exact-machine scans before declaring world migration complete, because exact-machine providers intentionally do not force-load old chunks.

If the server restarts, an addon is updated/reloaded, storage changes, or migration providers change after discovery, run a fresh upgrade scan and create new fingerprints instead of reusing earlier assumptions.

### Migration lanes

| Lane | Native authorization command | Scope |
| --- | --- | --- |
| Legacy item IDs | `/sf doctor migrations scan <plugin>` | Traversed live/player/backpack item candidates owned by an addon provider |
| Same-ID item schemas | `/sf doctor migrations schemas scan` | Traversed item candidates with guarded schema probes/validation/migrators |
| Exact placed machines | `/sf doctor migrations blocks scan <plugin>` | Addon-owned machines in currently loaded supported scope |
| Persisted block IDs | `/sf doctor migrations schemas blocks scan` | Stored `BLOCK_RECORD` and `UNIVERSAL_RECORD` identities without loading chunks |
| Persisted item payloads | `/sf doctor migrations schemas storage scan` | Unloaded `BLOCK_INVENTORY` and `UNIVERSAL_INVENTORY` item payload formats |

The aggregate upgrade plan is deliberately not an authorization token. These lanes remain separate because they protect different storage/live-state invariants and have different revalidation requirements.

### Plan categories

| Category | Meaning |
| --- | --- |
| `READY NOW` | Doctor found a candidate whose current target/provider capabilities are available. It still requires the native fingerprint scan and explicit execution command. |
| `NEEDS VALIDATION` | The addon has the probe, validator and migrator needed, but persistent backing state must be revalidated before authorization. |
| `NEEDS PROVIDER` | The candidate is recognized but the required migration provider, probe, validator or migrator capability is missing or unsafe. |
| `LOADED/DEFERRED` | A persisted block-ID candidate is valid but currently cached/loaded, so storage-level rewrite is intentionally deferred. |
| `MANUAL/BLOCKED` | The target is missing, the schema is manual-only, stored data is unreadable, the ID/template is unresolved or the evidence is otherwise insufficient for guarded migration. |

A candidate marked `READY` by an addon probe is **not** treated as executable merely because the probe recognized it. Item-local same-ID migration requires both the probe and migrator to be present. Validation-backed migration requires the probe, validator and migrator.

The unified upgrade workflow never creates a combined execution token. Legacy-ID, same-ID schema, exact-machine, persisted block-ID and persisted item-payload fingerprints remain separate, short-lived and single-use. The plan itself is read-only and never calls an addon migrator or provider repair method.

## Dependency diagnostics

For a specific plugin:

```text
/sf doctor dependencies <plugin>
/sf doctor compatibility <plugin>
```

This is useful when an addon is disabled but its required library, provider alias or external plugin may be the real issue.

Doctor does **not** replace the server log. It does not claim to intercept every arbitrary plugin `onEnable` exception.

## Rebar/Pylon integration probe

When a compatible external runtime is present, Legacy can perform capability discovery without hard-linking experimental APIs.

Look at a nearby target block and use:

```text
/sf doctor integrations probe
```

The probe is diagnostic. It does not mean Slimefun automatically bridges every external cargo, fluid or electricity system.

## Recovery commands

Depending on the current build, runtime/integration isolation can expose retry/reload commands such as:

```text
/sf doctor runtime retry
/sf doctor runtime retry all
/sf doctor integrations retry <id|all>
/sf doctor integrations reload
```

Use retries only after understanding why the callback was isolated. Repeatedly re-enabling a crashing integration can hide the underlying problem.

## Keep the evidence

When asking for help, save the full console exception and Doctor output **before** restarting repeatedly. The first failure is often the most useful one.
