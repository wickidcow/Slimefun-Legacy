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