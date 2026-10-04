# Resource-pack Doctor migration validation

The Doctor probe exercises actual install/uninstall commands against disposable
Paper, Purpur and Folia servers. It starts from a fresh directory and runs three normal
boots, including the restart checkpoint before changing existing items.

## Initial verified native matrix

The expanded fixture passed locally against core source
`d9cbfb10df075cbc08b4a1e9b140b0dbd9f9237f`, with core JAR SHA-256
`a58799aa12b1e21d17c1ad6c4670ae41d049766ebd4289e8338247ccbb694973`.
This change adds test tooling and evidence, without changing production Java.

| Runtime | Java | Normal boots | Probe checkpoints |
| --- | --- | --- | --- |
| Paper 1.21.11 build 132 (`c5eb079`) | Temurin 21.0.12.1 | 3 passed | 7 passed |
| Purpur 1.21.11 build 2568 (`f57bd86`) | Temurin 21.0.12.1 | 3 passed | 7 passed |
| Paper 26.2 build 129 (`9240f58`) | Temurin 25.0.4.1 | 3 passed | 7 passed |
| Purpur 26.2 build 2633 (`3f5d9c0`) | Temurin 25.0.4.1 | 3 passed | 7 passed |
| Paper 26.3 build 145 (`6bc56ab`) | Temurin 25.0.4.1 | 3 passed | 7 passed |
| Folia 26.2 build 7 (`14b7fee`) | Temurin 25.0.4.1 | 3 passed | 9 passed |

The exact runtime downloads and SHA-256 hashes are pinned in
[`resource-pack-doctor-runtimes.json`](../compatibility/resource-pack-doctor-runtimes.json).
The local phase results, runtime Java versions and artifact hashes are retained in
[`resource-pack-doctor-evidence.json`](../compatibility/resource-pack-doctor-evidence.json).
Later fixture expansions and their separate results are described below.

## What the probe checks

- Preview leaves stored items, mapping configuration and authorization untouched.
- Confirmed install saves the restart checkpoint and disables delivery before
  changing runtime templates. Cleanup resumes after a normal restart.
- Cleanup preserves exact quantities, names, lore, charge, backpack/owner
  identities, opaque ItemsAdder-style PDC and unrelated model-component lanes.
- Loaded chests, nested bundles/shulkers, drops, persisted backpacks and unloaded
  block/universal inventory rows retain the expected items.
- Native binary and Base64 text payloads work alongside the retained Bukkit
  object-stream envelope. The original object-stream database payload is backed
  up byte-for-byte before replacement.
- A distant vanilla chest is repaired when its previously unloaded chunk loads.
- Uninstall leaves the cleaned contents intact and repeated cleanup makes zero
  repairs. A third normal boot verifies durable item/database contents and keeps
  a later external pack-ownership choice.

The object-stream fixture is generated using the retained historical encoding on
each tested server. It is not a captured item from an old production world.

## Folia ownership checks

The fixture uses the native region scheduler on every runtime. On Folia it asserts
that each world check owns its target chunk, does not run on the global thread,
and does not own the other test area. The distant chest is 8,192 blocks away on
both axes so these checks exercise separate regions. Dropped-item assertions also
verify entity ownership. The disposable Folia configuration uses two tick threads.

The Folia lane loads an additional old-model backpack through the normal gameplay
API. A confirmed resume and uninstall must each report its deferral while
preserving both its cached stack and its database row. After a normal restart
releases the gameplay cache claim, the authorized maintenance pass must clean the
stored item without a backpack deferral. In the expanded menu fixture below, the
ownerless virtual menu contributes another expected deferral. Exact item equality checks retain the original
quantity, name, lore, identities, charge and opaque PDC.

This follow-up passed locally on Folia 26.2-7, with regression runs on Paper
1.21.11-132/Java 21 and Paper 26.3-145/Java 25. The follow-up results are recorded
separately in the evidence JSON. It changes the fixture and CI coverage, not
production Java or migration policy.

## Loaded machine and virtual menu checks

The expanded fixture creates two actual Electric Furnace block menus in the
separate regions, seeds an existing old-model stack in each output slot and waits
for the normal inventory-save acknowledgement before starting cleanup. A confirmed
resume must clean both loaded menus and persist their exact expected contents.
Uninstall and the third normal boot verify that the saved contents remain intact.
The nearby machine is also checked through its reloaded live menu after restart.

An additional universal inventory uses the existing Android menu preset with an
inventory trait but no block trait or location. Paper cleans its loaded inventory
and saves it. Folia reports it as deferred and preserves its cached item and exact
original database bytes, including through uninstall and restart. This exercises
the rule that a loaded ownerless menu cannot be repaired by global Folia work.
Once the gameplay-owned backpack is added, Folia reports two expected deferrals;
after restart only the ownerless virtual menu remains deferred.

Both location and chunk keys in the unloaded block fixture now use the canonical
storage syntax. The earlier fixture used noncanonical semicolon-separated
coordinates; its previous result demonstrated stored-item rewriting but did not
exercise a valid unloaded block-record key.

| Follow-up runtime | Normal boots | Probe checkpoints |
| --- | --- | --- |
| Paper 26.3 build 145 | 3 passed | 9 passed |
| Folia 26.2 build 7 | 3 passed | 11 passed |

These local results use the same core JAR listed above and are recorded under
`loaded_menu_followup` in the evidence JSON. Every CI runtime now includes the
loaded-machine and virtual-menu phases. No production implementation changed.

The first expanded CI run passed all five Paper/Purpur lanes but exposed a fixture
setup gap on Folia: an existing empty chunk could still have an unfinished
Slimefun data container when the machine was created. Doctor correctly deferred
that chunk. The fixture now completes and asserts the normal chunk-data load on
the owning region before creating the machine, so this phase exercises ready
loaded menus deterministically. A fresh local Folia run with this preparation
passed all three boots and eleven checkpoints; its evidence is recorded in
`folia_chunk_readiness_recheck`.

## Connected-player follow-up

The Paper 26.2 build 129 and Folia 26.2 build 7 lanes now use a real offline-mode
socket client against their disposable loopback server. The client reuses the
protocol implementation already pinned by the proxy identity workflow:
`1.66.2+complexity.26.2.3`, release archive SHA-256
`8b72d382e3cc06779b2b54fc3e402291f46df3bc28fcc2c189b1fc25b3895dab`.
Its package version is checked before connecting. CI verifies the archive checksum
and installs it with lifecycle scripts disabled.

The additional phases verify:

- Before authorization, a connected player's seeded inventory and ender chest
  retain their old model data. The client disconnects and the server stops normally.
- After install and restart, the reconnecting player's saved stacks are still
  old when the join event arrives, then the authorized delayed handler cleans them.
  External models and unknown-addon stacks remain exactly unchanged.
- Opening a real `PlayerBackpack` through the connected player's normal inventory
  API fires the native open event. The backpack, player inventory and cursor are
  cleaned, and the existing backpack UUID's database row is saved.
- A naturally colliding dropped item fires the native pickup event with its old
  metadata still present. The player receives exactly one expected cleaned stack,
  with its original amount and metadata, and the item entity disappears.
- After uninstall, disconnect and another normal server boot, the client reconnects
  and verifies the player inventory, ender chest and saved backpack contents.

The fixture observes actual join/open/pickup/quit events; it never calls the event
handlers directly or synthesizes Bukkit events. Every player assertion requires
the entity's owning thread, and Folia assertions reject the global tick thread.
Inventory opening is initiated through the server API; this is not a graphical
client click or resource-pack rendering test. The disposable server allows two
slots because Folia's pending login was rejected with the previous one-slot limit.

| Connected-player runtime | Normal boots | Total probe checkpoints | Player checkpoints |
| --- | --- | --- | --- |
| Paper 26.2 build 129 | 3 passed | 14 passed | 5 passed |
| Folia 26.2 build 7 | 3 passed | 16 passed | 5 passed |

These local results use the unchanged core JAR listed above and are recorded under
`connected_player_followup` in the evidence JSON. No production implementation or
addon pin changed. The other four native runtime lanes retain their existing
coverage; connected-player results are specific to the two 26.2 builds above.

## Cancelled events and disconnect follow-up

The two connected-player lanes now include three more native phases:

- A fixture listener cancels backpack opening at `LOWEST`, before Doctor's open
  handler. The menu stays closed; the live backpack, player inventory and cursor
  retain their old model data, and the persisted backpack payload stays byte-for-byte
  identical. Removing cancellation and opening that same backpack then exercises
  the existing successful cleanup and persistence checks.
- A naturally colliding item has its pickup cancelled at `LOWEST`, before Doctor's
  pickup handler. The original item entity and exact stack remain in the world,
  and the player's inventory stays empty. The next phase allows that same entity
  to be collected and requires one cleaned stack with no loss or duplication.
- After the install restart, a real player reconnects with the old saved inventory
  and ender chest, then is disconnected after two ticks, before the 20-tick join
  cleanup delay. A parallel probe through the same scheduler contract confirms
  Folia retirement or Paper execution with an offline player. After 40 global
  ticks, the fixture verifies that this work settled and the player is gone.
  Reconnecting again must still expose the old saved stacks at the join event,
  followed by successful authorized cleanup.

The test client accepts a server-initiated disconnect only in the explicit
retirement phase; server-side completion and a clean client exit are still
required. Each connection has a separate retained log. These are controlled
cancellation and disconnect cases, not a claim about every possible event order
or entity-lifecycle race. In particular, cancellation after Doctor's handler and
teleports between scheduling and execution are not covered here.

| Follow-up runtime | Normal boots | Total probe checkpoints | Player checkpoints |
| --- | --- | --- | --- |
| Paper 26.2 build 129 | 3 passed | 17 passed | 8 passed |
| Folia 26.2 build 7 | 3 passed | 19 passed | 8 passed |

The local results, using the same unchanged core JAR, are recorded under
`cancelled_event_followup` in the evidence JSON. These phases are part of the
permanent Paper/Folia 26.2 CI lanes. No production implementation changed.

## Reproduction and CI

`Resource Pack Doctor Migration` builds one core JAR for the exact checked-out
source and runs all six pinned runtimes. Each lane verifies the server checksum,
compiles the fixture with Java 25 targeting Java 21 bytecode, and runs the server
on the matrix's specified JVM. Logs, phase markers and JSON evidence are retained.

For a local run, use a new disposable directory:

```sh
python3 scripts/resource_pack_doctor_probe.py \
  --server-jar /path/to/pinned-server.jar \
  --core /path/to/Slimefun.jar \
  --java-home /path/to/jdk25 \
  --runtime-java-home /path/to/runtime-jdk \
  --work-dir /path/to/new-disposable-directory
```

`--server-template` can replace `--server-jar` when cached server code dependencies
are available. The template contributes only its server JAR, libraries, cache and
patched versions; it never contributes an existing world. `--server-cache` can
provide a Paperclip download cache when using `--server-jar`. Paperclip validates
its original-server payload before applying patches. The harness refuses to
reuse an existing work directory. Never install the fixture plugin on production.
Add `--expect-folia` for the Folia lane; it also requires the native ownership and
gameplay-backpack deferral checks. Task/ownership failures in any boot log fail the
probe even if a phase marker was already written.

For the connected-player phases on a 26.2 runtime, also pass
`--protocol-module /path/to/pinned-client/node_modules/minecraft-protocol` and make
Node available on PATH. The harness selects a temporary loopback port, connects
one test client at a time, and retains each connection's log with the server
evidence. Boot two includes a forced disconnect and another reconnect.

## Remaining release evidence

This matrix does not validate client rendering, a real combined ItemsAdder pack,
captured historical production-world data or addon-private storage. Folia coverage
now includes region-owned containers/drops, unloaded inventory rows, maintenance
backpacks, gameplay-cache deferral, loaded Electric Furnace menus and an ownerless
Android universal menu. Native connected-player join, backpack-open and pickup
now have coverage on Paper/Folia 26.2, including early event cancellation and a
disconnect before delayed join cleanup. Connected players on 1.21.11 and 26.3,
teleport/chunk-unload races, late event cancellation and concurrent viewers still
need dedicated native coverage; this fixture does not certify every addon or menu
implementation. Ordinary Folia startup
checks are separate evidence. Passing this fixture does not certify every stored
item or every external pack installation.
