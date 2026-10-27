# Chunk machine protections — combined Java diagnostic (NOT A RELEASE)

This draft integration is based on the unmerged Slimefun Legacy ticker-registration
fix [#478](https://github.com/wickidcow/Slimefun-Legacy/pull/478) at exact commit
`9937eeb1b7269f08027bf97d08368a2b2c68b89a`. It additionally imports
reviewed placement policy and diagnostics from
[#476](https://github.com/wickidcow/Slimefun-Legacy/pull/476) at
`550e9b36f25c2d2b40460404163f470655b31c96`, and the optional
fair ticker-budget work from [#477](https://github.com/wickidcow/Slimefun-Legacy/pull/477),
including its unload-time cursor eviction.

**Only this test branch is modified. None of the three PRs is merged
into `master` or released as 4.1.78.**

## Safe default behavior

In `plugins/Slimefun/config.yml`:

```yaml
machine-limiter:
  enabled: false
  max-ticking-machines-per-chunk: 256
  allow-bypass-permission: false

stability:
  chunk-ticker-budget:
    enabled: false
    max-tickers-per-chunk-per-cycle: 512
```

- Default settings do **not** reject any placement or throttle any existing
  machine callback.
- Duplicate ticker identities are always deduplicated, independent of the
  optional controls. Location plus optional UniversalBlock UUID remains
  the registration identity.
- The optional placement limiter rejects only new player-placed Slimefun
  ticking machines that would exceed the existing chunk registration count.
  It never deletes/pauses/edits existing item inventories.
- The optional runtime budget bounds the number of scheduled ticker-location
  callbacks per *Slimefun* cycle and uses a rotating window. The budget
  deliberately reduces throughput and is **not** a hard-time/MSPT watchdog.
  Existing machines are not destroyed or deregistered.
- Unloaded chunks release cached rotation snapshots instead of retaining
  unnecessary Java state indefinitely.

## Operator diagnostics

The existing `/sf chunkinfo top` command reports whether **new-machine
placement admission** and **existing-machine runtime throttling** are enabled,
with their separate configured limits. `/sf chunkinfo` for a loaded chunk
also reports the number of ticker callbacks selected each Slimefun cycle and
an estimated number of cycles to visit all registered machine identities.

These status fields come from the same pure `ChunkTickerBudgetPolicy` as the
TickerTask runtime selection. A zero, negative or missing cap safely defaults
to 512 callbacks; rotation estimation uses overflow-safe ceiling division.
The report is observational only, does not force-load chunks, and does not
claim a specific TPS/MSPT gain or exact elapsed-time completion.

## Integration tests

`TestCombinedChunkProtection` uses MockBukkit to register one physical
machine 500 times and another once. It proves that neither the placement
limit nor the rotation window counts duplicate registrations. Another case
proves distinct UniversalBlock UUIDs are retained. The packaged-config
test asserts that *both* safeguards and optional OP bypass are disabled
by default.

The separate candidate unit suites test exact-limit placement, invalid
configuration fallback, runtime round-robin fairness across 7,454 machines
and repeated machine registration. CI must also rerun Paper 1.21.11,
26.2 and 26.3, Purpur, Leaf and Folia checks with the *combined* sources;
earlier individual PR passes are not sufficient.

## Live acceptance

On a backed-up server that reproduces the reported 13,388-callback base:
1. Record actual Paper MSPT/TPS and three `/sf tick top` samples.
2. Verify total registered ticker locations per chunk after a restart.
   The profile alone does not prove actual duplicate registration.
3. With controls disabled, confirm baseline gameplay, inventories,
   Networks item conservation and addon behavior.
4. Enable only the placement limit with quota 2 in a dedicated test chunk.
   Verify a third ticking machine is refused and no item disappears.
5. Disable the placement test and enable only the runtime budget on a
   nonproduction copy of the heavy base. Compare throughput and
   inventory conservation over several cycles and a complete restart.
6. Confirm a chunk unload releases its cursor cache, and Folia-owned
   region execution remains protected by the existing scheduler checks.
7. Revert both controls immediately if an addon depends on every ticker
   executing each Slimefun cycle or if transfer latency becomes unacceptable.

For production performance relief, optimize Networks' expensive advanced
line transfers first; this diagnostic does not claim any measured TPS gain.
Never bypass anti-dupe ownership markers to obtain a performance improvement.
No Bedrock/Geyser support or resource-pack changes are in scope.
