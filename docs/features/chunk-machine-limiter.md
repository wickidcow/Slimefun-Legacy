# Optional Slimefun per-chunk machine limiter (development candidate)

The built-in machine limiter is a HeadLimiter-style **placement limit** for
Slimefun's registered ticking machines. It is **OFF by default** and is configured
in `plugins/Slimefun/config.yml`. It requires no third-party limiter plugin.

## Config

```yaml
machine-limiter:
  enabled: false
  max-ticking-machines-per-chunk: 256
  allow-bypass-permission: false
```

Enable it only after selecting an appropriate quota for your server. For example,
change `enabled` to `true` and use `max-ticking-machines-per-chunk: 256`
to allow up to 256 registered ticking machines in **each 16×16 world chunk**.
The **257th new ticking machine** is refused, with a red message showing the
current registrations and configured maximum. You can adjust the number.
Invalid values below 1 fall back to 256 rather than silently allowing
unlimited construction.

Setting `allow-bypass-permission: true` permits players with
`slimefun.machine-limiter.bypass` (OP by default) to exceed the quota.
That bypass is deliberately **off by default**, including for operators.

Use `/sf reload` (or a normal restart) after editing config.yml, then check
`/sf chunkinfo` to see the number of registered ticking machines, the
placement limit and whether admission is enabled. Use
`/sf chunkinfo top` to locate dense chunks. The count used by the limiter
comes from the same chunk-indexed core ticker registry as `/sf chunkinfo`.
No filesystem/database scan or `sf tick top` profiling is performed on
every placement.

## What is counted

- Registered `BlockTicker` machines from **Slimefun and compatible addons**,
  including Networks, Supreme and InfinityExpansion2, and ticker registrations
  for UniversalBlock machines.
- Each location already registered in the target chunk consumes a slot,
  whether the ticker is sync, async, manually paused or running normally.
- Vanilla blocks, player heads, guide-only items, cosmetic blocks, cables
  and non-ticking Slimefun blocks **do not** consume a machine quota slot.
- Every world has its own independent per-chunk count.

The limiter is consulted for **SlimefunBlockPlaceEvent** player placements,
before the normal Slimefun block registration, inventory creation and
block-place callbacks. It does not attempt to limit arbitrary WorldEdit edits,
third-party direct storage/controller writes, chunk migration or programmatic
machines installed without firing a cancellable Slimefun placement event.
Server owners should continue to review large automated builds and addon APIs
for bypasses.

## Existing bases are not altered

**Enabling the limit never deletes, suspends, relocates or replaces an existing
machine, chunk or inventory.** Already-over-limit chunks remain playable.
New machine placements there are refused until enough registered machines
have been removed. The limiter does not change the performance of already
running machines and cannot by itself fix an existing slow base.

Example production profile: 13,388 ticking Slimefun block callbacks over ten
ticks, with 129 advanced Networks line-transfer machines totaling 341.17ms.
This placement cap prevents additional crowding, but fixing that existing
runtime hotspot also requires Networks machine optimization, relocation or
careful reduction of loaded machines. Use `/sf tick top` to compare timings;
its values are callback durations, not standalone server MSPT.

## Acceptance

Test with an empty chunk and a small quota such as 2, first with the feature
disabled, then enabled. Verify placements 1 and 2 are accepted and placement
3 is cancelled without consuming the item or registering any machine. Break
one of the two machines and confirm another can be placed. Verify the
permission bypass remains inactive until explicitly enabled. Repeat with a
ticking machine from a maintained addon, a non-ticking Slimefun decoration,
a universal machine where supported, and concurrent placements on Paper,
Purpur, Leaf and Folia in the server's supported threading model. Ensure no
existing placed machine is disabled.

This is a Java-only development feature. It does not add Bedrock/Geyser
integration, change machine/item IDs or stored formats, alter the resource
pack, or authorize publishing Slimefun Legacy 4.1.78.
