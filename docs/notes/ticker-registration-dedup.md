# Idempotent machine-ticker registration — performance and correctness candidate

## Why it matters

A heavily built base reported 13,388 Slimefun ticker callbacks over 48
chunks, including 7,454 registrations in one chunk. This number **does not
prove duplicate registrations exist**: it may represent legitimate machines.

An inspection of Slimefun Legacy's ticker core identified a separate
correctness issue that can make such a base worse:

- `TickerTask.enableTicker(Location, UUID)` constructs a new `TickLocation`
  for every call, then adds it to a `ConcurrentHashMap.newKeySet()`.
- `TickLocation` had **identity-based** equality and hashing. The same
  machine registered again (for instance after repeated data load or addon
  startup callbacks) therefore occupied *additional* slots in the set.
- Such accidental duplicates would cause the same location to be dispatched
  multiple times per Slimefun cycle, potentially repeating expensive
  inventory/network work and distorting `/sf chunkinfo` and `sf tick top`.
- Every call marked the cached global ticker snapshot dirty, even if the
  location was logically already registered; that forces a full expensive
  snapshot rebuild for thousands of machines.

## Change

- Define `TickLocation` value equality by its immutable physical
  `BlockPosition` and optional UniversalBlock UUID.
- A second registration with the same position and same universal UUID now
  returns false from the concurrent set's `add`, so it cannot create
  duplicate callbacks or force a global snapshot rebuild.
- Different world/XYZ positions, normal and universal types, and different
  universal UUIDs at the same location remain distinct in the data model.
- Avoid allocating a new concurrent set for a chunk that is already
  registered. Only genuine additions invalidate the ticker snapshot.
- Machine removal is now keyed by the stored integer block position rather
  than a `Location.equals` comparison that also includes offsets and yaw/pitch.
  This prevents ghost callbacks when addons pass a centered or rotated
  location to `disableTicker`.
- Location/UUID removal only marks the expensive global ticker snapshot dirty
  **if an actual registration or empty chunk entry was removed**. Repeated
  attempts to remove an absent machine never trigger a full snapshot copy.
- Existing machine data, inventory contents, energy, chunk unload, migrations
  and scheduler ownership continue using the original code paths.

No saved item, block, menu, PDC, database record or machine recipe format
is changed, and no ticker is disabled or removed intentionally. On the next
normal restart all machine registrations are rebuilt from stored state with
value equality. The patch introduces no extra work on each ticker callback.

## Regression coverage

The new MockBukkit unit suite verifies:

- 500 repeated normal registrations of the same world position leave one
  ticker registration and one callback slot.
- Repeated universal registrations with identical UUID deduplicate.
- Distinct world positions, normal vs universal blocks and different
  universal UUIDs remain distinct.
- Unregistering/re-registering a location does not retain stale callbacks.
- Repeated unknown-location and unknown-UUID removals do not dirty
  the cached global snapshot, while an actual removal still invalidates it.
- Block-center locations with nonzero yaw/pitch successfully remove the
  original registered ticker instead of leaving a stale callback.
- Idempotent registrations do not dirty the global cached ticker snapshot;
  a new, distinct machine does.

## Acceptance

Verify Java 21 core build, public API, Paper/Purpur 1.21.11/26.2/26.3,
Folia/Leaf region paths and full runtime/45-addon smoke. A comparison of
before/after `/sf chunkinfo top` and `/sf tick top` on a backed-up copy of
the production base will determine whether duplicate registrations were
contributing to real-world load. **No improvement is claimed before the
tests and measurement**.

This Java-only development fix does not alter Geyser/Bedrock integrations,
resource-pack assets or publication. Slimefun Legacy 4.1.78 stays on hold.
