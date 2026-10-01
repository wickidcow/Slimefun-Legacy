# Doctor: existing-item upgrades and inventory recovery

## Owner workflow

The Minecraft 1.21.11 runtime floor is not an age limit on saved Slimefun items. Keep the original world, player data, plugin data and matching addon registrations. Do not recreate items from guide templates, change item IDs, regenerate backpack UUIDs or delete an unreadable inventory to make an upgrade appear successful.

Before testing an upgrade, stop the server and make a consistent backup of its worlds/player data, Slimefun and addon data, configurations and database. For external databases, use a consistent database backup as well; a copy of a live SQLite main file alone may omit pending journal/WAL state. Perform the initial upgrade on a disposable server copy with the exact candidate and addon versions. Run only one Slimefun implementation at a time.

Start with these diagnostic commands:

```text
/sf doctor status
/sf doctor storage status
/sf doctor storage recovery
/sf doctor upgrade scan
```

After the read-only scan reports completion, inspect its proposed upgrade routes:

```text
/sf doctor upgrade plan
/sf doctor upgrade providers
/sf doctor addons
```

The existing upgrade workflow distinguishes legacy-ID mappings, same-ID schemas, placed machines and persisted formats. A diagnostic scan is not authorization to rewrite every item. Only use an exact provider's supported repair/execute command with the fresh fingerprint produced by its scan. A missing provider or unknown item ID is not permission to guess a replacement or erase its payload. Restoring the matching addon/preset may be the appropriate remedy; the report cannot determine that from an identity alone.

`/sf doctor repair confirm` is the existing presentation-repair route. It repairs eligible names/lore; it is not a universal decoder repair, recovery-hold reset, forced migration retry or replacement for a missing addon. Resource-pack/model cleanup is a separate explicit operation and is not requested by these diagnostic commands.

## New recovery report

`/sf doctor storage recovery [page]` reports current block, universal and backpack load holds and pending universal migration identities. It requires `slimefun.command.doctor` before collecting identity information. Summary warnings also appear in the standard Doctor and storage-status views, and tab completion includes `recovery`.

The report is read-only. It does not read database records, resolve/load worlds or chunks, retry migrations, create destination IDs, clear guards, touch items or produce repair-authorizing fingerprints. It shows at most 200 sampled entries, 20 per page. Counts include all observed entries but may overlap: a source can have both a load hold and a pending migration. The snapshot is detached and immutable; it is not an atomic view across controllers. In-flight loads may appear, and separate page requests can observe different live states.

Owner keys are retained exactly in the snapshot. Only displayed copies are sanitized for color/control/Unicode line-separator characters and shortened for bounded chat output. A shortened display is not an executable identifier. Consult the original console failure for full details. Missing storage controllers are reported as unavailable, not as an empty healthy report. An empty memory report is not a scan or certification of historical world data.

## Recovering an affected inventory

Retain the backup and original records. Identify the exact failure from the console and the relevant addon/preset/ID from the report. Use an applicable supported repair or restore an intact copy while the server is stopped; do not modify the live database alongside running inventory writes. Then use normal complete loading to revalidate the inventory. The existing loader releases its own hold only after that load succeeds. This command deliberately offers no force-clear action.

For a pending universal migration, record both source and destination IDs. An ambiguous acknowledgement may mean the transaction already committed. Do not remove one side or allocate a new destination. Existing retry logic retains and verifies the same destination identity. Queue-idle/clean-shutdown status alone does not prove that every held inventory recovered.

On the test copy, compare original amounts, typed persistent data, ownership/backpack identities, stored contents, and machine behavior after clean shutdown and restart. Keep real historical-world upgrade evidence separate from fresh fixtures and from plugin-switch tests. Direct rollback to another fork requires an exact-build round trip; native-format overlap with Gugu does not certify an entire world, and the inspected United reader's older format remains a separate compatibility boundary.

## Implementation and verified pre-promotion evidence

This batch adds bounded immutable recovery snapshots to the existing controllers and a permission-gated Adventure component report. It does not change load/save/migration behavior, item IDs, research, recipes, codec output, schema, migration defaults, world data or addon implementations. No production compatibility suppression or dependency allowlist was expanded.

Run [36754557052](https://github.com/wickidcow/Slimefun-Legacy/actions/runs/36754557052) passed the full real Java 25/Gradle build and source invariants. Its downloaded archive SHA-256, actual JUnit XML and ten formatted Java blob hashes were checked independently. The new snapshot suite passed 12 tests, the command suite passed 10, and the existing 10 controller-read cases passed with added hold/recovery assertions. The full report had 465 entries, 464 reported passing, one pre-existing historical-database-fixture skip, no failures/errors. As in the baseline, one passing entry is an empty external-database factory, not external database execution. The explicit javac deprecation/removal report was 0/0.

Tests cover permission-before-capture, invalid arguments, bounded/deterministic sampling, immutable views, raw identity retention, sanitization, component messages, incomplete-vs-unavailable status, unchanged pending UUID/acknowledgement fields, and hold disappearance only after the existing full-load recovery succeeds. They do not certify live Folia interleavings or arbitrary historic worlds.

Artifact `11115723389`, SHA-256 `eda87ec89b11d8aa23799ab4e0412cbf9f80cb4e517cbe710f30e302f91c5b47`. The first attempt was correctly rejected for a new Dough formatting import. The code was changed to native Adventure components, and the dependency guard was retained unchanged. Ten tested Java files and this owner guide are promoted; temporary validation workflow/transport files are excluded. Normal PR checks must independently validate the resulting candidate, including the concurrent master guide-folder fix. No merge or official release is implied by the isolated result.
