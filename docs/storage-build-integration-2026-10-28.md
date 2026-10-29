# Storage hints and build modernization: combined validation only

Inputs:
- Core build/deprecation/Doctor candidate #499: f5656f43d220de1fac59e1492eb1f5248af129ab (tree f4e629375b5df37e1eed53d6bdf6c6fa20dc6462).
- Storage location-hint draft #494 plus failure/concurrency tests: e9e437a81f0d73a66ee986f86f4bad0d739d15ed.

Before combining, the original BlockDataController blob at #494's old base 4db3e6feedd2022300c0ddfacc71a0113c3282aa was compared with candidate #499. Both are baa9dfea6d3cbf426f0b1ee47b66b289a05db05c. The reviewed #494 controller can therefore be overlaid without discarding intervening controller changes. The helper, two test files and original safety document are copied by exact blob identity.

The storage draft's main PR build has 1,079 tests, no failures/errors and one skipped external-database test. All 34 hint-specific invocations passed. Its separate disposable MySQL/PostgreSQL workflow passed. One Folia Doctor attempt failed before startup because Paperclip rejected the downloaded Mojang JAR hash; that exact failed job passed on retry without changing checksums or source. Broader full-stack/addon evidence remains distinct.

This combined source must earn fresh mandatory CI, Java 21 bytecode, public API, 1.21.11/26.2/26.3 runtime, native database/storage recovery, Folia ownership and 44-addon evidence. The existing #499 success does not certify this additional storage change. Addon-only IE2/Supreme candidate tests also do not establish a complete updated release stack.

No persisted format, ID, recipe, migration bypass, version bump, deployment or release mutation is introduced by this integration branch. Do not infer TPS/MSPT gains from warm-cache unit tests. Keep draft until reviewed exact-source evidence and independent old-world/region-risk assessment are complete.
