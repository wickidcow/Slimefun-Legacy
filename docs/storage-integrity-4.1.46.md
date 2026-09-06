# Slimefun Legacy 4.1.46 Storage Integrity Diagnostics

Slimefun Legacy 4.1.46 includes explicit, read-only storage integrity diagnostics for administrators.

## Commands

- `/sf doctor storage status` shows the last completed scan, write-boundary state, and current two-pass confirmation status.
- `/sf doctor storage scan` starts a backend ownership scan on Slimefun's existing storage read executor.
- `/sf doctor storage plan [page]` renders the exact scope-qualified orphan-owner set that reached two-pass confirmation. The plan is paged to avoid flooding chat.

## What the scan checks

The scan compares primary block and universal records with the owners referenced by their secondary data and inventory tables. It reports secondary owners that do not have a matching primary record and includes a small bounded sample of identifiers for diagnosis.

Each scan also samples both the normal database write queue and the deferred delayed-saving queue at the beginning and end of the scan. A scan is eligible for confirmation only when both queues are empty at both boundaries.

## Two-pass confirmation

A single scan is never treated as sufficient repair evidence. Pass 1 records the exact orphan-owner sets from block data, block inventory, universal data, and universal inventory. Pass 2 requires another exact matching quiet scan at least 30 seconds later. Matching counts alone are not enough; the owner identifiers must match exactly.

A changed candidate set restarts confirmation at pass 1. A noisy or failed scan invalidates confirmation. Confirmation is runtime-only and is not persisted across restart.

## Read-only repair plan

After a valid 2/2 confirmation, `/sf doctor storage plan` can render the complete confirmed candidate set. Entries are grouped deterministically by storage scope and paged at 20 entries per page. The plan also has a stable SHA-256 fingerprint of the exact scope-qualified candidate set so administrators can tell whether two rendered plans describe the same candidates.

The plan contains owner keys for secondary storage scopes. An owner can therefore appear once per affected scope. The command does not claim that an owner key represents only one physical database row; a future repair implementation would need to handle every secondary row belonging to that owner in that scope.

## Safety model

The scan and plan do not inspect physical world blocks, force-load chunks, mutate caches, delete rows, migrate records, rewrite data, or repair anything. Rendering a plan does not authorize deletion and is not a transaction barrier. Any future repair command must perform fresh storage validation before changing data rather than trusting an old plan indefinitely.

Actual repair remains intentionally excluded from this slice.
