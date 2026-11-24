# Historical FluffyMachines and Supreme typed NBT drift — native, read-only evidence

This work extends the published-release five-JVM world-save tests now integrated into the Doctor parent PR #616.

- FluffyMachines v26.2.23 → v26.2.24: prior proof covered **51 actual old addon items**, 45 native-byte matches and six `SIMILAR_NATIVE_DRIFT` cases, all originally passing Bukkit `isSimilar`.
- Supreme v1.0.20 → v1.0.21: prior proof covered **50 actual old addon items**, 38 native-byte matches and twelve `SIMILAR_NATIVE_DRIFT` cases, all originally passing Bukkit `isSimilar`.

**Do not infer these eighteen items are broken, non-stacking or automatically repairable.** This test-only change replaces that ambiguous diagnosis with three read-only, strict subtypes for each Bukkit-similar item with raw-native drift: `SIMILAR_TYPED_EQUAL_RAW_DRIFT`, `SIMILAR_TYPED_DIFFERENCE`, or `SIMILAR_TYPED_UNREADABLE`.

Comparison uses detached native snapshots normalized **only by stack amount**, a size/depth bounded canonical parser which sorts compound keys but retains every typed value, tag type, list/array order, PDC data and raw floating-point bits. An unavailable parser result is never repair permission. The exact immutable source receipts and physically saved chest inventory are checked across five independent JVM boots (old seed, old stabilization, unchanged-old baseline, updated addon candidate, updated replay), and original source identities, counts, material, full Bukkit ItemMeta, owner data, template integrity and restart conservation remain mandatory.

The verification script matches every individual report ID and category against the original manifest and rejects omitted, extra or changed items. Only the seed stage writes disposable world stock. Nothing in this PR changes Doctor's live repair classification, production backups, shops, physical chest writers, Cargo, SQL, machines or another player's items.

This is **not** evidence that every historical version or every item is safe to normalize; only actual old-world comparison and external AlbionMC clone staging can authorize future changes. DRAFT until exact-head full runtime, compatibility, and addon-bundle checks pass.
