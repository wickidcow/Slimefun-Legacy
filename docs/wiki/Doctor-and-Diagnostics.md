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
/sf doctor proxy
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
| `proxy` | Paper backend proxy-forwarding configuration and UUID-safety signals |

## Guide Recovery Center

The **Slimefun Recovery Center** is visible only to players with `slimefun.command.doctor` (operator by default; permission-plugin admins can be granted it explicitly). Normal players do not see the Recovery Center button.
The Recovery Center is a router over the same guarded Doctor services used by the commands; it does not introduce a second repair engine.

### Recovery Assistant / Recommended Recovery

The top **Recommended Recovery** button evaluates existing read-only Doctor, storage, machine, dependency, integration,
resource-pack and item-model state. It can route the operator to a scan, specialist diagnostic screen or an existing
confirmation flow, but it never performs a server-wide mutation merely because a problem was detected.

Important safety rule: **Legacy pack delivery and Slimefun item-model mappings are independent**. The assistant must
not recommend removing model mappings just because Slimefun Legacy's own pack sender is disabled.

### Resource-pack administration

The Recovery Center now groups these controls under **Resource Pack & Item Textures** so sender settings, model mappings,
and stored-item texture repair are not presented as the same operation.

The resource-pack section deliberately separates these operations:

- **Enable Legacy Pack Sender** — turns only Slimefun Legacy pack delivery on; it does not change item models.
- **Upgrade Items for Resource Pack** — opens a guided **Scan Pack Upgrade → Apply Pack Texture Upgrade** flow. The scan is read-only; the apply step adopts zero-valued bundled mappings and updates eligible stored Slimefun items only after confirmation.
- **Disable Legacy Pack Sender** — turns only Slimefun Legacy pack delivery off; it does not change item models.
- **Remove Resource-Pack Item Models** — opens a guided **Audit Model Removal → Remove Legacy Item Models** flow. The audit is read-only; the confirmed step removes exact bundled Legacy mappings. It does **not** unregister Slimefun items or machines.
- **Resource Pack Item Texture Repairs** — separately scans for and repairs eligible stale exact bundled model data on existing stored items after the operator reviews the scan and backup.

A **Current Resource-Pack State** card shows ownership, sender state and model counts at a glance. A separate
**Resource Pack Preflight** screen reports sender state, URL/SHA-1 validity, known external pack-manager
plugins, bundled/custom item-model counts, and lets an administrator test the configured Legacy pack only on themselves.

Two common configurations are both supported:

1. **Slimefun Legacy sends the pack** — enable the Legacy sender and point `resource-pack.url` at the official pack or
   your own hosted combined pack. Keep the matching Slimefun item-model mappings enabled.
2. **Another plugin/proxy sends your combined pack** — keep the Legacy sender disabled. Keep the Slimefun item-model
   mappings enabled when your combined pack contains the corresponding Slimefun models.

Do **not** remove Slimefun item-model mappings merely because the Legacy sender is disabled. Doctor cannot inspect the
contents of an ItemsAdder/Oraxen/proxy/server pack, so the server owner must decide whether that external pack contains
the matching Slimefun models. Remove mappings only when they are intentionally no longer used, after a backup and scan.

### Advanced System Health

The **Advanced System Health** screen is the read-mostly dashboard above the individual repair lanes. It gives one
screen with six live status cards:

- **Items** — latest Doctor findings, unknown IDs, item-model candidates and migration signals.
- **Storage** — backend readiness, pending writes, backpack save chains, previous clean shutdown and persistence state.
- **Machines** — ticker state, active failures, paused circuits and registered ticking locations.
- **Addons** — hard dependency problems, compatibility attention and addon callback failures.
- **Proxy** — Velocity/Bungee-compatible forwarding health and blocking identity risks.
- **Resource Pack** — explicit ownership mode, Legacy sender state, external pack managers and config contradictions.

The dashboard also shows **Planned Restart Readiness**. `READY` means Slimefun storage is ready, queued database writes
are zero, backpack save chains are idle and no server-wide Doctor traversal is active. It is guidance for a planned
restart or migration window, not a guarantee that unrelated plugins or the server platform cannot fail during shutdown.

#### Performance Health

Performance Health ties the Doctor UI to the existing `/sf tick` diagnostics instead of creating a second profiler.
It exposes ticker state, rate, registered chunks/locations, targeted pauses, circuit-breaker pauses and active machine
failures. Buttons route to `/sf tick top`, `/sf tick at`, `/sf tick frozen` and machine/integration diagnostics.

Global Slimefun ticker freeze/resume is available only behind a confirmation screen. Freezing the ticker preserves
machine registrations and stored data; already-dispatched work may finish.

#### Upgrade Center

Upgrade Center is read-only orchestration over the existing migration system. It surfaces:

- legacy item-ID discovery;
- addon-owned same-ID schema discovery;
- exact placed-machine providers;
- persisted block/universal IDs;
- stored item payload formats;
- persisted machine Slimefun Item IDs;
- persisted backpack Slimefun Item IDs.

The GUI deliberately does **not** execute a migration fingerprint. Operators review the scan/plan output in chat and
use only the exact short-lived execution command printed by that native lane. The aggregate upgrade view never becomes
a universal authorization token.

#### Storage & Persistence Center

Storage & Persistence combines runtime persistence health with the existing storage-integrity and migration workflows.
It reports block/profile storage types, pending database writes, backpack save chains, uncertain backpack baselines,
previous clean shutdown, active Doctor work and read-only shutdown-backup readiness.

For SQLite-backed Slimefun data, Doctor can show whether the existing normal-shutdown backup service is configured,
how many backup ZIPs exist and the age of the newest backup. There is intentionally **no live Force Backup button**:
the existing backup runs after the normal database shutdown attempt, and Doctor does not create a competing live
snapshot path.

The existing `/sf doctor storage` two-pass/fingerprint workflow remains authoritative for storage-integrity repair.
The Operations Center does not bypass its matching quiet scans, fresh verification, mandatory repair backup or
destructive-repair guardrails.

#### Proxy & Player Identity

Proxy Health reuses `/sf doctor proxy` and adds an online-player picker for `/sf doctor proxy player <name>`. This lets
an operator compare the Bukkit UUID with the loaded Slimefun profile owner UUID and inspect research/backpack evidence
without editing player data.

This matters because broken forwarding can look like lost Slimefun progress even when the database itself is healthy.

#### Resource-pack ownership

Config schema version 2 adds `resource-pack.ownership-mode`:

| Mode | Meaning |
| --- | --- |
| `auto` | Backwards-compatible. Legacy sender behavior follows `resource-pack.enabled`. |
| `legacy` | Slimefun Legacy owns delivery of the configured official/custom ZIP. |
| `external` | ItemsAdder, Oraxen, a proxy/server pack, or another system owns delivery. Legacy's sender is disabled. |
| `none` | The server explicitly declares that no Slimefun-textured pack is intended. Legacy's sender is disabled. |

Ownership controls **delivery only**. It never rewrites `item-models.yml` or stored ItemStacks.

`external` is the normal mode for a combined pack: **Legacy sender OFF + matching Slimefun model mappings ON**.
`none` can make Doctor recommend reviewing exact Legacy bundled mappings that are still active, but Doctor still does
not remove them automatically. Cleanup remains the separate confirmed resource-pack item-model action.

### Additional Doctor screens

- **Player & Item Repair** — read-only hand/inventory inspection, confirmed held-item repair, confirmed self repair and an online-player repair picker.
- **Addon & Dependency Health** — addon compatibility evidence, missing/disabled hard dependencies, registered addon Doctor scans, registry health and cross-fork API diagnostics.
- **Runtime Recovery** — storage/runtime status, core/chunk health, machine failure detail, external integration status and guarded retry/reload confirmations.
- **Additional Recovery Tools** — full scan, names/lore repair, item-model scan, storage integrity, upgrade readiness, legacy-ID planning and addon schema migration probes.
- **Support & Diagnostics Summary** — compact live platform/storage/machine/pack/dependency/integration/last-scan state plus the current recommended next step.

`/sf doctor report` prints the same compact support-oriented command-line snapshot and the existing Doctor next-step
classification. This is useful when collecting information for an issue report.

Server-wide repair buttons keep confirmation screens and backup guidance; read-only diagnostics do not.


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
3. Run `/sf doctor scan` for a dry run. This also checks for stale bundled item-model values.
4. Review unknown IDs, item-model candidates, failures and skipped items.
5. Only then run `/sf doctor repair confirm` if the result is acceptable.

Never treat “0 repaired” as proof that a scan failed; a clean server may simply have nothing eligible to change.

### Doctor Next Steps

A completed `/sf doctor scan` ends with a **Slimefun Doctor Next Steps** section. It translates each finding into
the command that owns that repair lane instead of implying that `/sf doctor repair confirm` fixes everything.

Examples include:

- safe core name/lore repair → `/sf doctor repair confirm`;
- addon-owned same-ID schemas → `/sf doctor migrations schemas scan`, then the exact fingerprinted execute command it prints;
- stale bundled item-model data → `/sf doctor item-models scan` and `/sf doctor item-models repair confirm`;
- exact Legacy bundled resource-pack mappings → `/sf doctor item-models remove-resourcepack-texture-ids`;
- declared legacy item IDs → `/sf doctor migrations plan` / `providers`, then the provider-specific scan and execute command;
- unknown item IDs → `/sf doctor migrations unknown`;
- legacy or unknown placed-block identity → `/sf doctor upgrade plan`;
- traversal failures → `/sf doctor report`.

After `/sf doctor repair confirm`, Doctor prints Next Steps again. If core repair leaves protected data behind,
it explicitly sends the operator back to `/sf doctor scan`, which classifies the remaining problem and prints the
specialized command instead of repeatedly suggesting the generic repair command.

### Item-model compatibility repair

Slimefun Legacy 4.1.52 introduced a bundled hosted-pack model map. Servers that return affected IDs to `0` in
`plugins/Slimefun/item-models.yml` can use Doctor to find and remove the stale bundled model value from items
that were already created while the mapping was active. The normal `/sf doctor scan` now performs this same
read-only candidate test and reports the total under `Item-model candidates`; the dedicated command remains useful
for the per-ID breakdown and is still required for repair.

```text
/sf doctor item-models scan
/sf doctor item-models repair confirm
```

The repair is deliberately narrow:

- the stack must contain a currently registered Slimefun ID;
- that ID must currently be configured as `0` in `item-models.yml`;
- the stack's first CustomModelData float must exactly equal Slimefun Legacy's bundled value for that same ID;
- only that first bundled float is removed;
- additional model floats, flags, strings and colors are preserved;
- if the bundled float was the entire custom-model-data component, Doctor removes the empty component so the
  repaired item can match pre-model-map stacks again.

The item-model repair is **never run by automatic Item Doctor listeners**. It requires an operator-triggered scan
and an explicit `repair confirm`. The server-wide traversal covers online players, loaded inventories and machines,
dropped items, nested containers and all database backpacks. Unloaded world containers are not force-loaded.

Set the affected model entries to `0` before scanning. IDs that still have a non-zero configured mapping are
intentionally ignored because Doctor treats those as server-owner-approved model assignments.

#### Intentionally adopting the Slimefun Legacy resource pack on an existing server

Enabling `resource-pack.enabled: true` only controls delivery of the ZIP. It does **not** silently rewrite
existing `0` mappings in `item-models.yml`.

Before intentionally adopting the bundled Legacy item models on an established server, make a full offline backup
and run:

```text
/sf doctor item-models enable-pack scan
```

The scan is read-only. It reports bundled mappings already active, currently-zero mappings that can be enabled,
custom non-zero mappings that will be preserved, stored ItemStacks that can safely receive the bundled first model
float, and conflicting first model values that Doctor will not overwrite.

When the audit looks correct, keep players offline or use maintenance mode and run:

```text
/sf doctor item-models enable-pack confirm
```

The confirmed operation changes only `0` mappings to their exact bundled values. It then traverses online player
inventories, loaded storage/machines, dropped items, nested containers and every Slimefun database backpack, adding
the bundled first model float only when that slot is empty. Other CustomModelData lanes are preserved.

After completion, wait for `/sf doctor status` to show 0 pending database writes, stop the server normally, and
restart. The restart is required so registered Slimefun templates are rebuilt using the newly enabled mapping. Then
run `/sf doctor item-models enable-pack scan` again. A clean reachable migration reports zero adoption candidates.

Unloaded physical world containers are not force-loaded. Load those chunks and re-run the scan if needed.

This command adopts model mappings; it does not decide how the client ZIP is delivered. Servers using the built-in
sender can set `resource-pack.enabled: true`. Servers using ItemsAdder or another combined-pack manager can leave
Legacy's sender disabled while still using the bundled mapping values in their combined pack.

#### Removing exact Legacy resource-pack item models

`/sf doctor item-models remove-resourcepack-texture-ids` removes only mappings that still exactly match Slimefun Legacy's bundled model map. This covers both mappings intentionally adopted through the current Doctor workflow and mappings left by the historical v4.1.52 migration. Custom or non-matching model values are preserved.

Slimefun Legacy v4.1.52 briefly migrated existing `0` item-model entries to the bundled hosted-pack model map.
That changed the metadata of newly created Slimefun items and could make them stop matching pre-existing stacks in
storage systems or machines that compare complete ItemStacks.

This automatic zero-to-bundled migration has been removed. Existing zero mappings are never force-upgraded again.

For a server that was already affected:

```text
/sf doctor item-models remove-resourcepack-texture-ids
/sf doctor item-models remove-resourcepack-texture-ids confirm
```

The first command is a read-only audit. The confirmed removal resets only mappings that still exactly equal
Slimefun Legacy's bundled model values; unrelated custom model values are preserved. The older
`/sf doctor item-models rollback-v52` spelling remains supported as a backwards-compatible alias. After the removal, stop the
server normally and restart before repairing stored stacks, because registered item templates were constructed
earlier in the old runtime.

After restart:

```text
/sf doctor item-models scan
/sf doctor item-models repair confirm
```

The repair then removes only the exact stale bundled first model float from reachable Slimefun ItemStacks across
online inventories, loaded storage/machines, dropped items, nested containers and all Slimefun database backpacks.
It does not force-load unloaded chunks and it does not rewrite addon-specific encoded databases that are not stored
as ordinary ItemStacks; those continue through the appropriate Doctor migration lane.

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
| Persisted machine Item IDs | `/sf doctor migrations schemas storage ids scan` | Stored canonical/legacy Slimefun Item IDs in machine inventory payloads |
| Persisted backpack Item IDs | `/sf doctor migrations schemas storage backpacks scan` | Stored canonical/legacy Slimefun Item IDs in backpack payloads, with cached profiles deferred |

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

## Proxy / forwarding diagnostics

Use:

```text
/sf doctor proxy
```

This read-only check inspects the backend configuration that controls player-information forwarding. It reports whether the server appears to be using Velocity modern forwarding, Bungee-compatible legacy forwarding, conflicting forwarding modes, or an offline backend with no supported forwarding configured.

The Bungee-compatible result intentionally does not claim a specific proxy brand. From the Paper backend alone, the same forwarding mode can be used by BungeeCord, Waterfall, or Velocity legacy forwarding.

For Velocity modern forwarding, Doctor checks that `proxies.velocity.enabled` is enabled and that a forwarding secret is present without ever printing the secret. It also warns if `settings.bungeecord` is enabled at the same time.

Slimefun player profiles are keyed by the UUID presented by Bukkit/Paper. Correct proxy UUID forwarding is therefore required for research, backpacks, and other player-owned Slimefun data to follow the same player identity across reconnects and backend switches.

Doctor can validate backend configuration evidence, but it cannot prove proxy-side settings, firewall rules, or direct-backend network isolation. Use a real proxy switch/reconnect smoke test before declaring a proxy setup supported.

While a player is connected through the proxy, operators can inspect the identity that Paper and Slimefun actually received:

```text
/sf doctor proxy player <name>
/sf doctor proxy player <name> <research-key>
```

The player view is read-only. It reports the Bukkit UUID, Slimefun profile-owner UUID, whether those UUIDs match, unlocked research count and backpack count. Without a research key it also prints one currently locked research that can be used for a controlled persistence test. Supplying a research key reports whether that exact research is locked or unlocked.

The automated proxy smoke uses this view on Minecraft 26.2 to perform two real offline-mode client sessions through **Velocity modern forwarding**. It records the client-facing UUID, verifies the same UUID reaches Paper/Slimefun, unlocks one research through the normal `/sf research` command, disconnects, reconnects and requires that research to remain unlocked. Waterfall is retained only as an archived startup/configuration/status-passthrough compatibility lane and is not treated as the supported modern proxy target.

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
