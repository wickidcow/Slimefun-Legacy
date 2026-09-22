# Slimefun Doctor Command Reference

The **Slimefun Doctor** is Slimefun Legacy's operator/admin diagnostics, repair and migration toolkit. The in-game
**Slimefun Recovery Center** exposes the safest common workflows, while this page is the complete command reference.

Most commands on this page require:

`slimefun.command.doctor`

That permission defaults to operators. The related `/sf tick ...` performance commands use
`slimefun.command.tick`, which also defaults to operators.

> **Rule of thumb:** run a read-only status/scan first, review the output, make a current backup before any repair or
> migration, and use only the exact confirmation or fingerprint Doctor prints for that run.

## Start here

| Command | Changes data? | What it does |
| --- | --- | --- |
| `/sf doctor status` | No | Shows Doctor state, previous clean shutdown, pending database writes, backpack save state, machine isolation, integration failures and the current/last server-wide run. |
| `/sf doctor report` | No | Prints the public-safe support summary for issue reports. `/sf doctor support` is an alias. |
| `/sf doctor scan` | No | Runs the batched server-wide dry run across reachable inventories, loaded storage/machines, dropped/nested items and backpacks. Ends with **Doctor Next Steps**. |
| `/sf doctor repair confirm` | **Yes** | Applies the conservative server-wide item presentation repair after a reviewed scan and backup. It does not replace the specialist migration/resource-pack lanes below. |

Recommended general sequence:

1. `/sf doctor status`
2. `/sf doctor report`
3. `/sf doctor scan`
4. Follow the **Doctor Next Steps** command for the specific finding.
5. Re-run `/sf doctor scan` after any repair or migration.

## Player and item repair

| Command | Changes data? | What it does |
| --- | --- | --- |
| `/sf doctor hand` | **Yes** | Repairs the safely recoverable presentation of the recognized Slimefun item in your main hand. |
| `/sf doctor inventory` | **Yes** | Repairs your online inventory and ender chest. |
| `/sf doctor inventory <player>` | **Yes** | Repairs an online player's inventory and ender chest. |

These commands preserve recognized functional Slimefun metadata and skip cases Doctor cannot prove are safe.

## Resource pack and item textures

These commands deliberately separate **resource-pack delivery**, **server item-model mappings**, and **stored ItemStack
texture metadata**.

| Command | Changes data? | What it does |
| --- | --- | --- |
| `/sf doctor item-models status` | No | Summarizes item-model repair/adoption state and candidate counts. |
| `/sf doctor item-models scan` | No | Finds stale exact Legacy bundled model values on reachable stored Slimefun items. |
| `/sf doctor item-models repair confirm` | **Yes** | Repairs only eligible stale exact bundled model data on reachable stored items. Custom/non-matching model data is preserved. |
| `/sf doctor item-models enable-pack scan` | No | Audits an established server before adopting Legacy's bundled item-model mappings. |
| `/sf doctor item-models enable-pack confirm` | **Yes** | Adopts currently-zero bundled mappings and updates eligible reachable stored items. Custom non-zero mappings are preserved. |
| `/sf doctor item-models remove-resourcepack-texture-ids` | No | Audits exact Legacy bundled mappings that could be removed. |
| `/sf doctor item-models remove-resourcepack-texture-ids confirm` | **Yes** | Resets only mappings that still exactly match Legacy's bundled values. It **does not unregister Slimefun items or machines**. |

The older `rollback-v52` spelling remains a compatibility alias for the resource-pack mapping-removal command, but
`remove-resourcepack-texture-ids` is the preferred name.

After confirmed mapping removal, stop normally, restart, run `/sf doctor item-models scan`, review the results, and
only then use `/sf doctor item-models repair confirm` if stale stored item data remains.

## Core, registry, chunk and compatibility diagnostics

| Command | Changes data? | What it does |
| --- | --- | --- |
| `/sf doctor core` | No | Core readiness, lifecycle, scheduler, registry, machine, storage and chunk/runtime health. `lifecycle` is an alias. |
| `/sf doctor registry` | No | Registered/enabled items, item groups, researches, tickers and addon registry ownership. |
| `/sf doctor chunks` | No | World/chunk/block-data lifecycle, loaded records, unknown IDs and machine/chunk coordination. `worlds` and `blocks` are aliases. |
| `/sf doctor compatibility` | No | Summarizes known addon compatibility/runtime evidence. |
| `/sf doctor compatibility <plugin>` | No | Shows compatibility details for one plugin/addon. |
| `/sf doctor compatibility api <plugin>` | No | Shows cross-fork/API compatibility evidence for a plugin. |
| `/sf doctor dependencies` | No | Lists missing, disabled, aliased or otherwise unhealthy declared dependencies. |
| `/sf doctor dependencies <plugin>` | No | Dependency resolution details for one plugin. |

## Runtime and integrations

| Command | Changes data? | What it does |
| --- | --- | --- |
| `/sf doctor runtime` | No | Lists isolated/failing machines and circuit-breaker state. |
| `/sf doctor runtime retry` | Runtime state | Clears the isolation state for the machine you are looking at so it can retry normally. |
| `/sf doctor runtime retry all` | Runtime state | Clears machine isolation state globally. This does not rewrite machine storage. |
| `/sf doctor integrations` | No | Shows optional external integration capabilities and failures. |
| `/sf doctor integrations probe` | No | Probes the nearby supported external block/integration capabilities. |
| `/sf doctor integrations retry <id>` | Runtime state | Re-enables one isolated external integration callback/provider. |
| `/sf doctor integrations retry all` | Runtime state | Re-enables all isolated external integration callbacks/providers. |
| `/sf doctor integrations reload` | Runtime state | Reloads optional integration discovery/registration state. |

Use retry/reload only after reviewing the first exception that caused the isolation.

## Addon Doctor providers

| Command | Changes data? | What it does |
| --- | --- | --- |
| `/sf doctor addons status` | No | Lists registered addon Doctor providers and current availability. |
| `/sf doctor addons scan` | No | Runs registered addon Doctor providers in dry-run mode. |
| `/sf doctor addons repair confirm` | **Yes** | Requests each registered addon Doctor provider to perform its own guarded loaded-scope repair. |

Slimefun core does not guess addon-specific persistence formats. Addon Doctor providers own their own repair logic.

## Storage integrity

The storage integrity lane is intentionally stricter than ordinary Doctor repair.

| Command | Changes data? | What it does |
| --- | --- | --- |
| `/sf doctor storage status` | No | Shows the last scan, write boundary, two-pass confirmation state, preflight and last repair result. |
| `/sf doctor storage scan` | No | Starts a read-only backend ownership/integrity scan. |
| `/sf doctor storage plan [page]` | No | Shows the exact scope-qualified orphan-owner plan produced by confirmed scans. |
| `/sf doctor storage verify <full-fingerprint>` | No | Revalidates the exact SHA-256 plan and quiet-write boundary before repair. |
| `/sf doctor storage repair <full-fingerprint>` | **Yes — destructive** | Executes the currently verified exact storage repair plan. |

Do not shorten or reuse a storage repair fingerprint. If state changes, scan and verify again.

## Upgrade readiness

| Command | Changes data? | What it does |
| --- | --- | --- |
| `/sf doctor upgrade` | No | Shows the core/runtime upgrade-readiness snapshot. |
| `/sf doctor upgrade status` | No | Summarizes upgrade/migration readiness. |
| `/sf doctor upgrade scan` | No | Runs the read-only upgrade discovery traversal. |
| `/sf doctor upgrade plan` | No | Builds the combined read-only migration plan and routes each finding to its native authorization lane. |
| `/sf doctor upgrade providers` | No | Lists migration/schema providers relevant to an upgrade. |

The aggregate upgrade plan is **not** an execution token. Every mutation still requires its own native scan and
fingerprint/confirmation.

## Legacy item-ID migrations

Canonical route: `/sf doctor migrations ...` (`migrate` and `migration` are accepted route aliases).

| Command | Changes data? | What it does |
| --- | --- | --- |
| `/sf doctor migrations status` | No | Summarizes declared legacy ID mappings, registered targets and migration providers. |
| `/sf doctor migrations list [page]` | No | Lists declared old-ID → current-ID replacements. |
| `/sf doctor migrations unknown` | No | Correlates unknown IDs from the latest Doctor scan with declared mappings. |
| `/sf doctor migrations plan` | No | Builds a dry-run plan from the latest Doctor scan. |
| `/sf doctor migrations providers` | No | Lists enabled legacy-ID migration providers and validation state. |
| `/sf doctor migrations scan <plugin>` | No | Runs one provider's read-only migration scan and, when clean, creates a short-lived fingerprint. |
| `/sf doctor migrations execute <plugin> <fingerprint>` | **Yes** | Executes only the prepared provider plan whose mappings and fingerprint still match. |

Prepared migration plans are short-lived and single-use.

## Same-ID schema and persisted-data migrations

These lanes exist because a Slimefun ID can stay the same while an addon's stored schema changes.

| Command family | Changes data? | Purpose |
| --- | --- | --- |
| `/sf doctor migrations schemas status` | No | Schema migration provider/status summary. |
| `/sf doctor migrations schemas scan` | No | Read-only same-ID item-schema scan. |
| `/sf doctor migrations schemas execute <plugin> <fingerprint>` | **Yes** | Executes one prepared same-ID schema migration. |
| `/sf doctor migrations blocks status` | No | Exact placed-machine migration-provider status. |
| `/sf doctor migrations blocks scan <plugin>` | No | Scans currently loaded supported addon-owned machines without force-loading chunks. |
| `/sf doctor migrations blocks execute <plugin> <fingerprint>` | **Yes** | Executes the exact prepared placed-machine plan. |
| `/sf doctor migrations schemas blocks status` | No | Persisted block/universal ID migration state. |
| `/sf doctor migrations schemas blocks scan` | No | Storage-level read-only persisted block-ID scan without loading chunks. |
| `/sf doctor migrations schemas blocks execute <fingerprint>` | **Yes** | Executes the prepared persisted block-ID plan. |
| `/sf doctor migrations schemas storage status` | No | Persisted item-payload migration state. |
| `/sf doctor migrations schemas storage scan` | No | Read-only persisted inventory payload/schema scan. |
| `/sf doctor migrations schemas storage execute <fingerprint>` | **Yes** | Executes the prepared persisted item-payload migration. |
| `/sf doctor migrations schemas storage ids status` | No | Persisted machine Item-ID migration state. |
| `/sf doctor migrations schemas storage ids scan` | No | Read-only persisted machine Item-ID audit. |
| `/sf doctor migrations schemas storage ids execute <fingerprint>` | **Yes** | Executes the exact prepared persisted machine Item-ID plan. |
| `/sf doctor migrations schemas storage backpacks status` | No | Persisted backpack Item-ID migration state. |
| `/sf doctor migrations schemas storage backpacks scan` | No | Read-only backpack Item-ID audit; live/cached backpacks are deferred. |
| `/sf doctor migrations schemas storage backpacks execute <fingerprint>` | **Yes** | Executes the exact prepared persisted backpack Item-ID plan. |

Always use the execute command Doctor itself prints after the corresponding scan. Provider IDs and fingerprint
positions vary by migration lane; do not guess them.

## Proxy and player identity

| Command | Changes data? | What it does |
| --- | --- | --- |
| `/sf doctor proxy` | No | Checks Paper/backend forwarding evidence for Velocity modern, Bungee-compatible forwarding, conflicts and unsafe offline-backend configuration. |
| `/sf doctor proxy player <name>` | No | Compares the online player's Bukkit UUID with the Slimefun profile owner, research count and backpack count. |
| `/sf doctor proxy player <name> <research-key>` | No | Adds the exact research locked/unlocked state to the identity report. |

Doctor never prints the Velocity forwarding secret.

## InfinityExpansion2 convenience bridge

When InfinityExpansion2 is installed, Slimefun Legacy can route to its Doctor migration bridge:

| Command | Changes data? | What it does |
| --- | --- | --- |
| `/sf doctor ie2 status` | No | Shows IE1 → IE2 migration status. |
| `/sf doctor ie2 scan` | No | Runs the IE2 migration dry run. |
| `/sf doctor ie2 migrate` | Addon-owned repair | Runs the installed IE2 migration path. |
| `/sf doctor ie2 refresh` | Runtime/provider state | Refreshes the IE2 bridge/provider state. |

The installed addon remains authoritative for its migration engine.

## Related performance commands

These are not `/sf doctor` commands. They use `slimefun.command.tick`.

| Command | What it does |
| --- | --- |
| `/sf tick query` | Full Slimefun ticker state. |
| `/sf tick top` | Collects a live profiler sample and ranks current ticker cost. |
| `/sf tick at` | Inspects the Slimefun ticker/machine at the block you are looking at. |
| `/sf tick frozen` | Lists targeted pauses/frozen ticker state. |
| `/sf tick rate` | Shows/works with the current Slimefun ticker rate controls. |

The Recovery Center's **Performance Health** screen links to these existing commands rather than maintaining a second
profiler.

## Safe operating pattern

For a problem you do not yet understand:

```text
/sf doctor status
/sf doctor report
/sf doctor scan
```

Then follow the specific **Doctor Next Steps** output. For performance problems, add:

```text
/sf tick top
```

For any command containing `confirm`, `execute`, `repair <fingerprint>`, or an addon migration action, make a
current backup first and use the exact command from the immediately preceding scan/plan.

## Related pages

- [Doctor & Diagnostics](Doctor-and-Diagnostics.md)
- [Commands & Permissions](Commands-and-Permissions.md)
- [Server Owner Guide](Server-Owner-Guide.md)
- [Troubleshooting](Troubleshooting.md)
