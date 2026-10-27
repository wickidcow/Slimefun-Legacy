# Opt-in workload budget for dense existing Slimefun machine chunks

## Problem

A real `/sf tick top` sample with 13,388 registered machine callbacks across
48 chunks recorded 526.82ms of aggregate callback work over **ten game ticks**,
not a single 526ms server tick. The most expensive `world` chunks were
`(-333,-348)` (7,454 blocks / 298.94ms), and `(-333,-349)`
(1,303 blocks / 159.75ms). Networks `NTW_EXPANSION_ADVANCED_LINE_TRANSFER_PLUS`
alone used 341.17ms over the ten-tick sample.

A normal Slimefun machine placement limit prevents **new machines**, but does
not protect the server from previously built industrial bases. This option
targets that *existing loaded workload* without rewriting any machine data.

## Config (disabled by default)

Set in `plugins/Slimefun/config.yml`:

```yaml
stability:
  chunk-ticker-budget:
    enabled: false
    max-tickers-per-chunk-per-cycle: 512
```

Setting `enabled: true` makes Slimefun execute up to the selected number of
registered machine callbacks from each loaded chunk during one Slimefun
ticker cycle (default `URID.custom-ticker-delay: 10` server ticks / 0.5sec).
When the chunk holds more than its budget, a circular cursor picks up
where the previous cycle left off. Every machine remains registered and
receives a turn; no machine is permanently frozen or selectively deleted.

At 7,454 registered machines and a per-cycle budget of 512, each machine is
eligible about once every **15 Slimefun cycles**, or roughly 7.5 seconds at the
default ticker delay. A gentler initial trial could use **2,048**, serving
every machine in approximately four cycles (2 seconds). This is intentional
processing slowdown, not a guarantee of a particular MSPT.

The scheduler reuses each chunk's frozen registration snapshot between
topology changes. It does not sort 7,454 blocks or walk the SQL/database
on each cycle, and never force-loads unloaded chunks. Native synchronization,
Folia region ownership, error-handling/circuit breakers, item storage,
ticker registration, and inventory view checks remain in the regular
TickerTask code path for each selected machine.

Disabling the option restores the exact legacy iteration behavior and drops
cached budget cursors. Invalid maximum values use a fail-closed default of
512, not an unlimited quota.

## Warnings

- Slowing a machine's ticker may slow cargo movement, energy generation,
  automatic crafting and transport in that chunk. Network balances should
  remain conserved, but live testing is mandatory before deployment.
- This is a **callback count** guard, not a true millisecond deadline.
  Paper's synchronized machine callbacks can execute on their server-owned
  thread after the asynchronous Slimefun coordinator queues them. Measuring
  the scheduling call's wall clock as a true TPS budget would be inaccurate.
- This does **not** modify or restore the cargo settings of individual
  addons. Networks line-transfer-specific `pushitem-tick`,
  `grabitem-tick` and `max-targets-per-tick` are better first-line
  optimizations because they affect the expensive transfers selectively.
- For maximum compatibility, this remains OFF by default. Paper/Purpur native
  smoke tests, Folia ownership, native item conservation across a full restart
  and identical pre/post storage state are required before promoting it.

## Practical performance process

1. Record three `/sf tick top` samples, whole-server MSPT/TPS and
   `/sf chunkinfo top` hotspots.
2. First tune `plugins/Networks/config.yml` (e.g. for advanced transfer plus
   `pushitem-tick: 4`, `grabitem-tick: 4`,
   `max-targets-per-tick: 4`) while preserving existing line distance.
3. If the already-built base is still overwhelming the server, test this
   optional core budget on a **backed-up staging server**, initially with
   `max-tickers-per-chunk-per-cycle: 2048`. Observe stalled crafting or power.
4. Reduce the cap only if needed, and watch for item throughput changes.
   Keep the two Slimefun and Networks layers independently reversible.
5. Confirm native storage persistence and transfers over a full restart.
   Do not use an experimental jar on a live server before passing those tests.

This is a Java-only development branch. It does not modify Geyser/Bedrock,
resource packs, Slimefun item IDs, inventories or stored database formats.
It must not trigger the held 4.1.78 release.
