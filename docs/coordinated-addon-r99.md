# Coordinated addon revision 99 — October 1, 2026

## Scope

Continue revision98 at `b65978497a9178cdcc9c93030087c50ce02b249b`. Exactly one of the45 addon pins changes: MagicExpansion from `61a0ccfa67dcf7c74ffc2aa67bcc2bdfdbbd358e` to `4532a3c2adfef0136200fed97551433daaf11625` (SF_MagicExpansion PR9, stacked on PR8). All other44 source pins, membership/exclusions, existing core production code and all previous preservation changes remain unchanged.

The core remains4.1.62 and the addon remains1.1.7. No item/research IDs, saved item keys/types, prices, recipes, machine rates, core/addon storage schemas or migration defaults change. GuGu/old-data preservation and the1.21.11/Java21 runtime floor remain in scope; United reverse conversion is not a release gate.

## Shop safety implementation

The original forgiving load could turn malformed YAML into an empty shop and then overwrite the damaged file during saveAll. The new store validates complete records, retains original paths and source-byte baselines, refuses stale/missing/externally changed files, and writes only after exact item/cost/quota serialization checks and staged atomic replacement. Invalid shops are unavailable rather than reset, while healthy neighbors remain usable. Failed deletion does not remove the live record.

Editor sessions now refer to the exact selected shop/trade and preserve accumulated purchase usage. Same-reward trades and later pages no longer redirect edits/deletions. Reload or definition changes invalidate stale sessions. Internal async-chat creation/limit actions are applied once on the server thread, with synchronous event cancellation and identity-bound pending tokens.

See the addon's `docs/shop-file-editor-preservation.md` for operator recovery and limits. This is not a transaction spanning player inventory, reward delivery, quota persistence and disk; no hostile-process filesystem CAS, power-loss/directory-fsync, Folia or arbitrary external-concurrency guarantee is claimed. Corrupted black-market files remain a distinct follow-up.

## Real tests, not a compile-only claim

MagicExpansion validation36857208372 ran complete Maven builds against both stable Legacy4.1.61 and the exact revision98 core. Each passed61 tests with no failures/errors/skips:31 file,12 editor and18 existing payment/page cases. All457 base classes meet Java21 and test/probe libraries are absent. Evidence11159054228 SHA256 `3c63c4abe84abffc33713b11d6d35b550e2c02e0e4fa28126c3864b48554e7ea` was downloaded; actual XML and all six source blobs were checked.

The exact promoted addon independently passed normal PR build36857851831 and its61-test baseline build. Downloaded XML11160160722 matched SHA256 `f9fe7ffcd112d22faca368e745e744344ce94c86542455592aba3a91a8daf875`.

Real-Paper run36856039051 reproduced the original destructive overwrite, then passed ten corrected first-boot scenarios and a separate second server process on1.21.11 stable132/Java21,26.2 stable129/Java25 and26.3 beta140/Java25. The same candidate JAR was used in all lanes. Actual shop/editor/chat handlers, Bukkit inventories, scheduler and files were exercised with explicitly synthetic no-network player/view facades. Saved YAML preserves old unregistered IDs, exact FLOAT123.4567, LONG9007199254740993, byte arrays, nested owner data, name/lore, amounts and purchase usage through restart. Corrupt bytes remain unchanged.

All three runtime archives were independently downloaded/digest-checked:11158462793 (1.21.11),11159032502 (26.2),11158877670 (26.3). Expected refused-load diagnostics are intentional; these are not globally error-free logs or a captured historical-world test.

Pinned MockBukkit's FLOAT-to-DOUBLE YAML conversion, unsupported nested-container tag and byte-array reference equality were independently diagnosed. The production exact-round-trip guard was never relaxed. Permanent negative cases protect refusal of those representations; real Paper supplies successful typed-data coverage.

## Exact-source integration and retained runtime helper

Core-hosted run36858132668 checked out addon4532a3c2, reran all61 project tests against core JAR SHA256 `55be5129bfb70e64fc16fbe53147c7ee6d3cccf7734844e6f1dda539444a59e0`, verified Java21 output and executed the retained `scripts/smoke_shop_safety.sh` on1.21.11/Java21 through both boots. It passed the source/ledger identity checks and all core invariants before staging only two JSON metadata files.

Evidence11160231411 matched SHA256 `44a2afa9387072659f92517f07b1626edf5dfb60edffa8a405b8dd3f1ca27501`. Actual XML, helper logs, stored snapshots and both metadata blobs were inspected. The complete ledger now contains385 reported project-test entries across16 addons;29 have no unit-test report entries, without ruling out separately recorded native/runtime/static coverage. Only this row receives new source/core/run attribution; this is not one new all45 test run.

## Bundle-core provenance correction

The canonical addon workflow previously built its core without SOURCE_COMMIT/SOURCE_DATE_EPOCH, despite the normal build already setting both. Its revision98 development core therefore carried git.source.commit=unknown. The canonical core job now pins the actual checked-out source identity before Gradle, disables build/configuration caches for that build and checks bytecode plus release-artifact metadata/packaging before upload. The existing full source-invariant runner now enforces these operations and their order within the core job.

Validation36858733398 first demonstrated that the new source guard rejects the original workflow, then passed all core invariants, the full uncached Gradle build, Java21 and artifact checks, and an independent second clean core build. The two actual JARs matched byte-for-byte and SHA256 `3d14bfcd62f322c41f84a6c5922c070ee9154262e49d4d8fbd0424a90fa7c846`. Both standard/dedicated commit fields matched that validation source40431ab4; build time matched its commit timestamp rather than epoch0.

Downloaded evidence11160297291 SHA256 `38e1b057c586787bf4db9f25332c8e851b028df7b89cfae33803593d71059e71` was checked along with the exact workflow/guard diff and all120 XML reports:493 reported entries, zero failures/errors, one existing historical-database-fixture skip. An empty external database factory entry is not external database coverage. Explicit production deprecation/removal report was zero; unrelated test/runtime warnings are not included in that claim.

The first provenance test harness had interpolation/indentation errors before the intended build. Moving the literal checked patch to a temporary Python helper corrected the harness without changing the intended source patch or removing any check. Temporary validation/transport helpers are not promoted.

## Release boundary

The combined revision99 head still needs its own canonical45-addon archive and normal supported-platform/full-stack results. The independent dual-build evidence above is not a stable-release publication, nor a substitute for the final versioned reproducible-release workflow. Do not substitute revision98 complete-stack success for the new combined artifacts.

No default-branch merge, plugin/core version bump, stable JAR/ZIP release, production install or automatic migration has occurred. Broader machine/performance audits and data-sensitive addon work remain; this checkpoint does not certify every SF_ plugin as completely modernized.
