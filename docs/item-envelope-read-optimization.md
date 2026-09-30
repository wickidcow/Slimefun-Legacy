# Item-envelope read optimization — September 30, 2026

## Continuation checkpoint

This batch continues PR #289 from `788b89e17d4e8694bad2c4e0e5e9ac5b1e0662c6`. All 12 normal pull-request workflows for that predecessor were observed successful before this batch. Those results are not validation of the new head.

The original codec file was checked against Git blob `2e0ea508bfd5de38f5f3d134d2b03c723d372d90` before editing. This batch changes only item-envelope read allocations, adds regression tests, and documents the evidence. It does not merge or release the full compatibility work.

## Production changes

- Compare the four-byte `SF2\0` header using bounded array ranges instead of allocating a four-byte copy on every classification.
- Decode historical Base64 text directly from its byte array with the existing MIME decoder instead of first constructing a full-payload ASCII String and then converting it back to bytes inside the decoder.

The native writer is unchanged, byte for byte in source. The native payload copy passed to Paper is retained. The old Bukkit object-stream reader, its compatibility lock, ItemMeta alias restoration and the one-layer Base64-wrapped native path are unchanged. No shared mutable decoded-item cache is introduced.

This is an allocation reduction in item loading/classification, not a measured server TPS improvement or a change to machine tick budgets.

## Preserved boundaries

The change does not alter item/research IDs, persistent keys or types, ownership, backpack/machine identities, amounts, recipes, production rates, energy costs, ingredient staging, transfer order, schema, migration defaults, addon-facing signatures, release version or resource-pack mappings. It does not turn failed deserialization into an empty item.

Null/empty handling at the existing DataUtils boundary remains unchanged. A header-only record is still classified as native and then rejected by payload deserialization. Base64-wrapped native records remain legacy-classified for explicit migration accounting; merely reading one does not change the stored bytes. Recursive wrapping is not newly accepted.

## Permanent regression tests

`ItemStackDataCodecEnvelopeTest` adds eight JUnit tests covering:

1. Exact header recognition, all 256 values at each of four prefix positions, short/null input and caller-buffer immutability.
2. Classification parity across payload sizes through 65,536 bytes.
3. Every possible byte inserted at each position in a representative Base64 input, comparing the historical ASCII/MIME operation with direct MIME-byte decoding, including invalid-input exception classes.
4. Padded, unpadded and MIME-line-wrapped records through the real codec for native and historical object-stream representations.
5. Historical MIME tolerance, including ignored high-bit bytes, through both codec paths.
6. Corrupt/truncated and recursively wrapped payload rejection, preserved source bytes, restored ItemMeta aliases and a subsequent successful read.
7. Unchanged text-envelope migration classification and read-only behavior.
8. Independent returned items across repeated reads, including caller mutations to amount and persistent item ID.

Generated items carry an unregistered addon ID, owner UUID, custom name/lore, amount 37 and an exact FLOAT charge of 123.4567. The tests do not require the addon to be registered merely to retain its opaque identity. Existing broader compatibility tests remain unchanged.

Focused project command:

```sh
./gradlew test --tests 'com.xzavier0722.mc.plugin.slimefun4.storage.util.ItemStackDataCodec*Test' --no-daemon
```

Require the complete normal build, source/API guards, database tests and supported-server/addon checks for the new head as well.

## Local evidence and limitations

An independent Java 21 run used the exact before/after classifier bodies and decoder expressions extracted from the hash-checked source. No Bukkit substitutes or dependency stubs were used. It passed:

- 40,993 classifier comparisons.
- 149,122 decoder comparisons: 105,140 accepted inputs and 43,982 rejected inputs, with identical decoded bytes or exception classes.
- Input-buffer immutability and two negative controls: a broken three-byte-only header check and an incompatible strict Base64 decoder.

That run validates the changed JDK operations only. It is not a full Gradle/JUnit, Paper/MockBukkit, historical-world, database or cross-fork server-switch result. The local environment has Java 21 but cannot resolve GitHub for a project checkout and has no resolved project dependency build available. Report the new head's GitHub validation separately; do not reuse predecessor success as proof.

## Cross-fork and addon gates remain

The inspected Gugu codec at `SlimefunGuguProject/Slimefun4@f1722396066859c075dadafd2e908a7ff96af9a8` shares the native `SF2` envelope. The inspected United reader at `Slimefun-United/Slimefun-United@6bfe999ea203269a94795624bd4cb736d47479c8` expects Base64 Bukkit object streams and does not handle this native envelope. These are pinned source findings, not blanket compatibility certifications.

Retain native writes and historical reads. Do not globally downgrade serialization, automatically convert live worlds or claim United rollback without exact-build conversion/reader tests. Test fork switching on the same Minecraft version separately from Minecraft-version changes; a plugin rollback is not a world-version downgrade guarantee.

Keep the established execution order: finish the core checkpoint, then Networks, InfinityExpansion2, Supreme, FinalTECH and the remaining maintained addons. Preserve concurrent addon changes and pin exact addon revisions for the release bundle. Minecraft 1.21.11+ remains the runtime floor, not an age cutoff for saved items. Rebar/Pylon remain outside this workstream. Deliver installable raw plugin JARs only after their own validation; this batch does not publish an artifact.
