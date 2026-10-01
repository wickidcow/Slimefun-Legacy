# Coordinated addon revision 98 — October 1, 2026

## Reconciled source selection

This candidate combines PR292 at `78ec1b049c9b54fdf7a108c59c028605af417b4f` with the separately reviewed PR293 WorldEdit/FN/LiteXpansion work at `7d6e06b4f7dad9060453bc4597df6225dee503fa`. Both earlier branches called their selections revision97; this combined manifest is revision98. Retain every Galactifun, SlimeHUD, advancement, storage, native-floor and item-component change already selected by PR292.

Exactly three of the45 addon pins change from the latest PR292:

| Addon | Exact source | Purpose |
| --- | --- | --- |
| FNAmplifications | `2eb07e7bca82d4d7621224e6f9e4942481e7e8f7` | Preserve unbinding tools and saved gem data on invalid/stale/rejected operations |
| LiteXpansion | `4dc69e1ba835b897e2fa38b9eb54bcbcee644abf` | Preserve rich Cargo Configurator components and existing codec/Doctor boundaries |
| MagicExpansion | `61a0ccfa67dcf7c74ffc2aa67bcc2bdfdbbd358e` | Reject incomplete barter payment and purchase the trade displayed on the current page |

The other42 pins and all membership/exclusion decisions are unchanged. No core production Java, item/research IDs, persistent schema, prices, machine rates, migration defaults or release version changes. Slimefun remains4.1.62. United-to-Legacy remains relevant; United reverse conversion is not a release gate. Minecraft1.21.11/Java21 remains the runtime floor, not a saved-data age limit.

## Exact-core project validation

[Run36845433915](https://github.com/wickidcow/Slimefun-Legacy/actions/runs/36845433915) reconstructed the combined selection and verified the inherited WorldEdit files had not changed concurrently before applying them. All retained core invariants and all76 WorldEdit provenance/selection/wiring tests passed.

The three exact addon sources then completed full Maven `clean verify` against the same hash-verified revision97 core. No project tests were skipped:

| Addon | Actual reported tests | Failures/errors/skips |
| --- | --- | --- |
| FNAmplifications |39|0/0/0|
| LiteXpansion |12|0/0/0|
| MagicExpansion |18|0/0/0|

Every final JAR passed the Java21 bytecode check. The core JAR SHA256 was `90e9f5ecfe306b70f6cf26d8ca7347ed4f65a07c3e9c6667cf42b1988522ba34`, from artifact11138864515. Evidence artifact11153510968 matched SHA256 `2fb4bf1d34799a43906d4d50252d5ff0a27932f59762bc8a0d580ec60d514f61`. Downloaded actual XML, source blobs, selection/ledger agreement and checksum reports were independently inspected.

The consolidated ledger now contains45 successful exact-source full-build outcomes and342 reported test entries across16 addons;29 addons still have no reported automated tests. Only the three updated rows receive this new run/core evidence. The remaining rows retain their historical source/run attribution. These totals are not one new all45 test run and do not certify complete machine/concurrency behavior.

The first integration harness attempt failed before source or project checks because Maven caching was requested before Maven repositories were cloned. Removing that optional cache configuration preserved every assertion and produced the successful run above.

## MagicExpansion real handler controls

[Run36845516642](https://github.com/wickidcow/SF_MagicExpansion/actions/runs/36845516642) exercised the actual private shop purchase handlers with real Bukkit inventories and an explicitly synthetic, no-network player facade. The same original and corrected addon JARs were used on Paper1.21.11 build132/Java21, Paper26.2 build129/Java25 and Paper26.3 beta140/Java25.

Each original-code run reproduced all three defects: duplicate-cost underpayment in both shop paths and the wrong first-page trade purchased from page2. Each corrected run passed seven scenarios: incomplete payment leaves items/counters unchanged; visible page selection; black-market reject-then-exact-pay; rich old-item metadata and real saved/reloaded limits; untouched off-hand; preserved free trade; and clamped-page agreement.

The tested corrected JAR SHA256 was `491619a0acf6a564cd2242ab84d62eddb6d7688e8623530af0ef6bc2d71db111`. Runtime evidence archives were downloaded and digest-checked:

-1.21.11 artifact11153710467: `8f2c05124405d7d212b74afa1933b2b4befe8bd88a7d1201bb9221b31256c781`.
-26.2 artifact11151919772: `8f66bb6c70eb884bd75cfbd7ac429512dfab3afe644c05bcb1d364e3563d982e`.
-26.3 artifact11153267082: `3e4f40b6b2199fdaca62f8836ff2a3cbf59845af1c0badfeb8ab6984ca10a452`.

Actual saved YAML retained the unregistered old item ID, name/lore, FLOAT charge123.4567, LONG count9007199254740993, byte-array data, exact quantities and purchase limits. The tests do not establish live-client clicks, Folia ownership, crash atomicity, external concurrency or corrupted-shop-file recovery. Those remain separate work.

Earlier attempts were blocked by Paper metadata HTTP504 and then a test-only dependency casing error. The successful run uses exact official server URLs and published hashes recovered from the already verified previous-release run36808369424. Only the probe descriptor casing changed; its classes and both addon JARs stayed unchanged. Paper's independent version-update checker still logged network errors on both original and corrected runs; do not describe these as entirely error-free console logs. No shop, linkage or database-constraint failure was observed in the successful controls.

MagicExpansion's normal [PR8 build36843518020](https://github.com/wickidcow/SF_MagicExpansion/actions/runs/36843518020) independently passed all three API compilation checks, the final1.21.11 full18-test build, all-base-class Java21 checks and test-library exclusion. Its stable4.1.61 API bootstrap verifies the actual published release checksum; it installs into the local Maven cache only. No stable release was published by that branch.

## Complete-stack and release boundary

The reviewed WorldEdit provider selector is now retained alongside all45 addons: correct source/project/loader/version, published hash, descriptor/archive/Java checks, explicit prerelease policy, and required provider plus WorldEditSlimefun activation on both boots. Do not drop SFWorldEdit because its provider is absent.

The newly promoted combined head still needs its own canonical45-addon archive, exact-source full-stack restarts and normal compatibility workflows. Earlier revision97 green checks and the isolated tests above are not substituted for new-head results. Keep the exact addon pins and JAR source evidence together. No main/master merge, version bump, stable publication, live-server install or automatic migration was performed in this reconciliation.
