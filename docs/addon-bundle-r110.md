## Addon bundle refresh — revision 110

The refreshed **SF_Addons_1.21.11-26.3.zip** replaces MagicExpansion 1.1.8 with **MagicExpansion 1.1.9**. The other 44 addon JARs and the published Slimefun Legacy 4.1.68 core JAR remain byte-for-byte unchanged.

MagicExpansion now detects PyroFishing/PyroFishingPro, BetterFish/BetterFishing, EvenMoreFish, CustomFishing and UltimateFishing. Its default `AUTO` mode leaves fishing catches, bait and effects to those enabled plugins, and retains full MagicExpansion fishing when none are detected. Both rod families use the same gate. Admins can add other plugin names or explicitly select `COMPATIBILITY` or `FULL`; `/magicexpansion fishing` reports the current state. Existing items, IDs, recipes and machines are retained.

The standalone release is built on the Paper 1.21.11 / Slimefun 4.1.61 baseline with Java 21 bytecode. All 97 tests also passed against the published 4.1.68 core. The baseline-built fishing candidate passed core binary linkage and two isolated Paper 26.3 build 143 server boots covering actual rod handlers, pending/cancelled catches, bait/XP preservation, cargo, sword, shop and persistence checks. Exact source and artifact hashes are pinned in `compatibility/bundle-refresh-r110.json`.

The targeted refresh validates the archive, manifest and checksums and rejects unrelated source-pin changes. Publication rechecks the latest release plus the current core and live bundle digests, preventing a stale refresh from replacing a newer release. The original revision-109 validation remains historical evidence; this refresh does not claim all 45 addons were rebuilt for the unchanged published core.

Named-plugin detection uses MockBukkit checks; real-Paper tests use a no-network player facade. Actual third-party fishing plugin JAR combinations, live-client fishing, arbitrary historical worlds, concurrency and Folia region behavior remain outside the verified scope. The compatibility gate is coexistence, not a fish-conversion bridge.
