# Source-specific native IE2 GEO Quarry evidence

This contract supports issue [#581](https://github.com/wickidcow/Slimefun-Legacy/issues/581).
It does **not** authorize any repair for historical items under
[#578](https://github.com/wickidcow/Slimefun-Legacy/issues/578).

## Evidence source

InfinityExpansion2's merged [PR #72](https://github.com/wickidcow/SF_InfinityExpansion2/pull/72)
records a disposable native **actual** `GeoQuarry.process` producer fixture for
`SMALL_URANIUM`. The producer runs against a real registered GEO Quarry
`BlockMenu`, then tests inventory/entity identity, the saved-world output
after a second server JVM boot, and quantity conservation.

Use the files from the **same exact CI run**, retaining their original bytes:

- `release-runtime-evidence.json` — native seed/verify phase receipts and exact
  SHA-256 checksums for the core, server, IE2 addon, and native probe source.
- `plugins/NativeModernizationProbe/native-geo-uranium.bin` — actual captured
  one-item-type output from the machine, not a copy of the registered template.
- The exact built IE2 JAR referenced by that run.
- The exact `scripts/runtime/release/NativeModernizationProbe.java` source bytes
  checked out by that IE2 run (its SHA-256 must equal `probe_sha256` in the
  native receipt). Do not supply a different checkout or a rewritten source.
- The exact Slimefun Legacy core JAR and Paper server JAR used by the run.
- A Slimefun registry inventory from that **same** core/server pair.

The SHA-256 values of the core and server MUST match the target report. IE2's
earlier green native test used a pinned Legacy core **different** from the current
Legacy `master`. Its evidence must **not** be attached to a report built with
the current core. Re-run the IE2 native fixture against the target exact core
and server bytes instead. A server build number, tag, or version string is
not a replacement for a matching artifact digest.

## Optional import

```sh
python3 scripts/identity_coverage.py \
  --registry path/to/identity-registry.json \
  --core path/to/Slimefun.jar \
  --server path/to/server.jar \
  --output path/to/identity-coverage.json \
  --ie2-geo-evidence path/to/release-runtime-evidence.json \
  --ie2-geo-capture path/to/native-geo-uranium.bin \
  --ie2-geo-addon-jar path/to/SF_InfinityExpansion2.jar \
  --ie2-geo-probe-source path/to/NativeModernizationProbe.java
```

All four `--ie2-geo-*` arguments are required together. Incomplete,
symlinked, missing, modified, cross-core, cross-Paper, mismatched addon, or
mismatched native-probe source evidence fails closed. If the optional inputs are omitted, the normal report
remains unchanged.

The importer records a single `actual_cases` PASS only for the verified
`ie2_geo_quarry_actual_output_restart` fixture. It **never** turns a registry
item's broad `machine`, `cargo`, or `storage_restart` row into PASS. All
other source/producer combinations remain `UNTESTED` or their separately
supported state. The report contains only fixture metadata and digests, never
private PDC payloads, player IDs, or historical saved-item bytes.

## Remaining work

- Independently capture real Supreme Tech Generator outputs with exact native
  source, merged inventory, and restart receipts.
- Capture and compare actual Cargo transfers, Fluffy barrels and other storage
  holders, checking full, partially filled, and incompatible stacks.
- Verify a genuine old-versus-new AlbionMC saved-item pair with an untouched
  rollback copy and exact quantity/ownership conservation before designing an
  optional historical repair. Do not use fresh template equivalence as proof
  that older stored items have been repaired.
