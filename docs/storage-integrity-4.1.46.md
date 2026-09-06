# Slimefun Legacy 4.1.46 Storage Integrity Diagnostics

Slimefun Legacy 4.1.46 includes explicit storage integrity diagnostics plus a deliberately gated orphan-secondary repair path for administrators.

## Commands

- `/sf doctor storage status` shows the last completed scan, write-boundary state, current two-pass confirmation status, latest repair-preflight state, and last destructive repair result.
- `/sf doctor storage scan` starts a read-only backend ownership scan on Slimefun's existing storage read executor.
- `/sf doctor storage plan [page]` renders the exact scope-qualified orphan-owner set that reached two-pass confirmation. The plan is paged to avoid flooding chat.
- `/sf doctor storage verify <full-fingerprint>` performs the final read-only revalidation pass against the complete SHA-256 plan fingerprint.
- `/sf doctor storage repair <full-fingerprint>` executes the guarded destructive repair for the currently verified plan.

## What the scan checks

The scan compares primary block and universal records with the owners referenced by their secondary data and inventory tables. It reports secondary owners that do not have a matching primary record and includes a small bounded sample of identifiers for diagnosis.

Each scan also samples both the normal database write queue and the deferred delayed-saving queue at the beginning and end of the scan. A scan is eligible for confirmation only when both queues are empty at both boundaries.

## Two-pass confirmation

A single scan is never treated as sufficient repair evidence. Pass 1 records the exact orphan-owner sets from block data, block inventory, universal data, and universal inventory. Pass 2 requires another exact matching quiet scan at least 30 seconds later. Matching counts alone are not enough; the owner identifiers must match exactly.

A changed candidate set restarts confirmation at pass 1. A noisy or failed scan invalidates confirmation. Confirmation is runtime-only and is not persisted across restart.

## Read-only repair plan

After a valid 2/2 confirmation, `/sf doctor storage plan` can render the complete confirmed candidate set. Entries are grouped deterministically by storage scope and paged at 20 entries per page. The plan also has a stable SHA-256 fingerprint of the exact scope-qualified candidate set so administrators can tell whether two rendered plans describe the same candidates.

The plan contains owner keys for secondary storage scopes. An owner can therefore appear once per affected scope. The plan does not claim that an owner key represents only one physical database row; repair removes every secondary row belonging to that owner in the affected scope.

## Final fingerprint preflight

`/sf doctor storage verify <full-fingerprint>` only starts when the supplied 64-character SHA-256 fingerprint exactly matches the currently confirmed non-empty plan. It then performs another fresh backend ownership scan. The exact candidate set must still produce the same fingerprint and both the active write queue and deferred delayed-saving queue must be empty at both scan boundaries.

A changed candidate set fails verification and restarts two-pass confirmation at 1/2. A noisy verification scan fails closed and resets confirmation. A wrong fingerprint is rejected before a backend scan starts.

Successful verification is runtime-only and short-lived for 60 seconds. Starting any later integrity scan invalidates the previous successful preflight. Once a destructive repair attempt is accepted, the successful verification is consumed and cannot be reused.

## Guarded destructive repair

`/sf doctor storage repair <full-fingerprint>` is the only destructive command in this workflow. It requires the current exact 2/2 plan and an unexpired successful final verification for the same fingerprint.

Immediately before mutation Slimefun acquires its tracked storage-read gate and a controller-wide write-submission gate. The write gate also checks the active/queued write executors, not only the scoped write map. While both gates are held, Slimefun re-scans the backend and requires the exact candidate fingerprint to match again.

Repair then checks whether any planned owner is still represented by loaded Slimefun block or universal data. If so, repair fails closed rather than deleting data that may still represent a live machine. This check does not force-load chunks and does not scan physical world blocks.

### Delayed-writing restriction in 4.1.46

For the destructive repair command, `delayedWriting.enable` in `block-storage.yml` must be `false` at server startup. Read-only scan, plan, and verification continue to work with delayed writing enabled.

This restriction is deliberate. Deferred delayed-saving tasks are maintained separately from the normal write-submission barrier, so 4.1.46 refuses destructive repair instead of risking a deferred mutation recreating a row after it was removed. After changing the setting, restart the server and repeat the scan/plan/verify flow before repair.

## Mandatory backup

Before any deletion, repair writes a lossless backup under the plugin `storage-repair-backups` directory. The file records the plan fingerprint and every selected secondary row. String and binary field values are type-tagged and Base64 encoded so stored inventory payloads remain lossless.

If the backup directory or file cannot be created, repair stops before deleting anything. The backup path is reported after a successful repair and is retained when a later delete step fails.

## What repair can delete

Repair can delete rows only from these secondary scopes:

- `BLOCK_DATA`
- `BLOCK_INVENTORY`
- `UNIVERSAL_DATA`
- `UNIVERSAL_INVENTORY`

`BLOCK_RECORD` and `UNIVERSAL_RECORD` primary roots are never repair targets. Repair also does not delete chunk roots, profiles, backpacks, research data, or unrelated storage scopes.

After deletion, Slimefun checks that every targeted owner is gone before releasing the storage gates. It performs a new ownership scan, resets the prior confirmation state, logs the fingerprint, affected row count, and backup path, and recommends starting a new post-repair scan.

If deletion throws after some rows have already changed, the command reports `DELETE_FAILED`, preserves the backup path, and warns the administrator not to retry blindly. Automatic rollback is intentionally not attempted because storage adapters may not share one portable transaction model.

## Safety model and limits

The full flow is therefore:

`scan -> quiet pass 1/2 -> exact matching quiet pass 2/2 -> review plan -> verify fingerprint -> guarded repair`

Scan, plan, and verify remain read-only. Repair is explicit and destructive, but only for the exact secondary owner set that survived every gate above.

No stage force-loads chunks solely for diagnosis or repair. Because repair intentionally avoids a physical world scan, an unloaded physical block whose primary storage root is already missing is not reconstructed or inferred by this command. The stable two-pass evidence, final fingerprint revalidation, loaded-cache refusal, mandatory backup, and final read/write barriers are the safeguards used instead.
