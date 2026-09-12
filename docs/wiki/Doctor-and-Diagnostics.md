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

## Upgrading old addon items

Use the separate Item Upgrade Doctor when the **Slimefun item ID itself may have changed**, for example after moving from an older addon generation to a maintained replacement. This is different from the normal presentation repair above.

```text
/sf doctor item-upgrade scan
/sf doctor item-upgrade status
/sf doctor item-upgrade fix <scan-fingerprint>
```

The workflow is intentionally guided:

1. Run `/sf doctor item-upgrade scan`. This is read-only.
2. Review every `READY`, `BLOCKED` and `NO MAPPING` result.
3. Make sure you have a current backup. If making that backup requires stopping/restarting the server, run the scan again afterward and use the new fingerprint.
4. Run the exact `fix <fingerprint>` command printed by Doctor. The fingerprint expires after 10 minutes and is single-use.
5. After the fix completes, use `/sf doctor status` and wait for pending database writes to reach `0` before a normal shutdown.

### What Item Upgrade Doctor can fix

- Old addon item IDs when the addon has explicitly declared a trusted old-ID → current-ID mapping.
- IE1 → InfinityExpansion2 item mappings published by IE2.
- Historical DynaTech item IDs when the maintained DynaTech build publishes its verified mapping table.
- Safely recoverable translated/Chinese visible item names and lore using the existing Item Doctor presentation safeguards.
- Nested item stacks inside supported bundles/containers and Slimefun backpacks encountered by the scan.

### What the result labels mean

- `READY` — Doctor has a declared mapping, the current target item exists, and the item can be handled by the generic item-only upgrader.
- `BLOCKED` — Doctor knows the proposed target but refuses the generic rewrite, for example because the target is missing or the underlying Minecraft material changed.
- `NO MAPPING` — the old ID is unknown to the installed addons. Doctor will not guess what it should become.

A `BLOCKED` or `NO MAPPING` item is deliberately left unchanged. Addon maintainers can publish additional verified mappings later, after which the server owner can scan again.

### Item-only safety boundary

`/sf doctor item-upgrade fix` changes **item stacks only**. It does not rewrite placed Slimefun block IDs, Cargo networks, Energy networks, machine block records or addon database schemas. Broader addon-owned persistence migrations remain under `/sf doctor migrations ...` and their own provider/fingerprint safeguards.

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
