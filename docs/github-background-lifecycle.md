# GitHub background service shutdown

## Defect and correction

A GitHub release request can finish after Slimefun has disabled and cleared its singleton. The previous release callback
updated its cached tag and called `Slimefun.getVersion()` before checking whether the plugin remained enabled. This
produced `IllegalStateException: Cannot invoke static method, Slimefun instance is null.` Deferred global and player
notifications also consulted the current singleton, so an old callback could use a replacement plugin's scheduler or
deliver a notification after its original owner had stopped.

`GitHubService` now captures its original plugin, scheduler, lifecycle service, configuration object, logger and installed
version at startup. Its lifetime is terminal: repeated start calls do not duplicate tasks, and a stopped service cannot
be restarted or rebound. Work is eligible only while that original owner remains the singleton, is enabled, is STARTING
or RUNNING, and its captured scheduler accepts tasks. STARTING remains eligible because normal core startup starts
GitHub services before marking the core RUNNING. The captured configuration object still receives normal reloads.

Slimefun stops the GitHub service at the beginning of `onDisable()`, including before the existing partial-startup and
unit-test early return. The normal shutdown coordinator isolates cancellation failures from later shutdown steps.

## Publication and task ownership

A shared synchronized gate serializes service shutdown with release state, notifications, contributor results and cache
commits. HTTP requests and waits for UUID/skin futures execute outside that gate. Once a stopped request completes, its
result is discarded. Interrupted requests preserve the interrupt flag and do not publish cached fallback data or
schedule a retry.

The service tracks its own scheduled handles. Shutdown cancels those handles; completed one-shot work and retired entity
tasks remove their entries. Registration handles completion or retirement before the native scheduler returns a handle,
and cancels a handle returned after a reentrant stop. A scheduler rejection is ignored only when a fresh lifecycle check
shows the service has become inactive; a rejection while active remains visible.

Release callbacks, deferred global callbacks and player callbacks each check the original lifetime. Player permissions
and message delivery execute in the player's scheduler context. Contributor UUID/skin lookup initiation, publication,
cache saving and retry scheduling follow the same ownership rules. Internal asynchronous work uses the captured logger
and resources rather than late static singleton access.

Task cancellation does not establish instantaneous termination of an underlying HTTP/profile transport. The guarantee
tested here is rejection of late owned results and scheduling, with shutdown able to proceed while a remote result is
still pending.

## Existing behavior retained

The release-only check still starts asynchronously and repeats hourly. Version parsing, the compact installed/latest
version notice, the official GitHub release link, operator/permission eligibility, late-join notification and once-per-tag
delivery remain covered by active controls. Contributor lookup opt-out, blocked names, timeouts, request limits, cache
formats and retry delays retain their existing behavior. The already pinned Authlib dependency is added only to the
test configuration so controlled profile tests can execute Dough's real conversion without a native server.

Public constructors and existing JVM method descriptors remain. This batch does not change item IDs, recipes, persistent
keys, inventories, database schemas, storage adapters, serialization formats or the database shutdown order.

## Regression evidence

The original 27 release-callback test invocations ran against unchanged source
`bf8a1159219500f572f8b356bc6fe554aae5d339`: **11 lifecycle failures and 16 passing active controls**. The failures include
the observed null-singleton exception, notification after disable and replacement-owner rebinding. The original test
source SHA-256 is `50d12b8fc87eb28859ad2934a475da5aae6a1feba7848591b6f12e41d3d06b93`; its JUnit XML SHA-256 is
`61fce3232131adc02f0f1a67a7a182cd17c7233df38d2d1229427f048d7ec77c`.

The expanded fixed suite passed **73 invocations with zero failures, errors or skips**:

| Suite | Invocations | Behavior exercised |
| --- | ---: | --- |
| `TestGitHubReleaseLifecycle` | 47 | Real release callback, original-owner lifecycle, actual early disable hook, deferred callbacks, task cancellation/removal and submission races, permission context, version/link/once-per-tag controls. |
| `TestGitHubConnectorLifecycle` | 15 | Actual request/cache branches, pending success/failure after stop, cached fallback, replacement owner, preserved interruption and callback-triggered stop before cache commit. |
| `TestGitHubTaskLifecycle` | 11 | Actual contributor worker, controlled UUID/skin futures, real YAML cache saves, active opt-out/blocked-name controls, late results/rate limits, retry ownership and interruption. |

The complete local JUnit suite subsequently reported **782 invocations: 781 passed, one pre-existing external-database
fixture test skipped, zero failures/errors**. The complete source-invariant command also passed. Normal PR and platform
CI evidence belongs to the associated pull request and must be checked for its exact source commit.

Tests use controlled transport/futures, queued scheduler delivery and isolated cache files. Latches and bounded future
waits establish ordering; the tests make no external HTTP/profile requests and use no timing sleeps. The scheduler
fixtures establish ownership/delegation and stale-result behavior, rather than native concurrent Folia execution.

## Separate storage investigation

The saved late GitHub exception does not establish a cause for earlier database/WAL observations. This lifecycle patch
contains no persistence fix. A separate diagnostic uses the pinned public Slimefun 4.1.71 binary, normal delayed metadata
writes, immediate orderly shutdown and preserved raw DB/WAL/SHM snapshots. Its result must be recorded independently of
the GitHub regression tests. Metadata-only fixture results do not certify inventory persistence, crash durability,
historical player worlds or cross-fork upgrades.
