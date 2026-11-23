# Slimefun Doctor: classify native drift without changing historical items

This draft extends the successful, source-pinned five-JVM published item handoffs for Networks v1.0.65→v1.0.66 and InfinityExpansion2 v2.0.21→v2.0.22. Earlier test receipts showed 34 of 53 Networks items and 4 of 53 IE2 items were Bukkit `isSimilar` while raw `serializeAsBytes` differed; they were reported as `SIMILAR_NATIVE_DRIFT`. This was **not evidence that the items failed to stack or needed repairs**.

For those already stack-similar items, this test now checks *detached copies* after normalizing only their amounts to one. It compares the full native NBT structure with compound field order ignored but **every typed value, tag type, nested key/value, list order, array and numeric bit pattern preserved**. The redacted `classification.tsv` report and aggregate CI logs give three mutually exclusive outcomes:

- `SIMILAR_TYPED_EQUAL_RAW_DRIFT`: structurally equal typed native values, different serialized bytes; no replacement needed or authorized
- `SIMILAR_TYPED_DIFFERENCE`: real typed native difference despite Bukkit similarity; **refuse rewriting**, investigate actual component provenance
- `SIMILAR_TYPED_UNREADABLE`: unavailable or over-budget structural evidence; **refuse rewriting** and investigate source

The original invariant checks remain: exactly the same two physical chests on five independent JVM boots, original source snapshots and quantities, addon registrations, owner/PDC safety, candidate receipts, restart replay and unmodified current registered templates. Only the seed boot may write physical chest slots. Classification is a read-only test: it **does not change** Doctor's live repair policy, does not migrate QuickShop/Cargo/chests/quantum storage, and does not promise that any historical mismatch can be repaired safely.

Coverage is a bounded sample, not all past releases or all addon IDs. FluffyMachines and Supreme native histories are independently covered in #630 and #634; they can adopt this same read-only subtype report once their current release-bundle checks finish. Cross-addon GEO output parity remains separate.
