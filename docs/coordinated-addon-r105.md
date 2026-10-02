# Addon revision 105: JEG renderer recovery

This selection continues published core 4.1.64 at `e47a3035b35dfb93325d22ba03b2a69dd6cd6992`. All 45 addon members and exclusions remain. Only JEG advances to merged `89ce9a8b0e8ef9d1939256f1d80917af63e439e2`, version 2.1.70. All 44 other source selections remain byte-for-byte unchanged, including Networks, BetterChests, BuildingStaff, FluffyMachines and SlimeTinker. No core runtime code, release version, IDs, storage formats or migration defaults change.

## Report and honest diagnosis

The live `/sf open_guide` failure named JEG's `OnDisplay$ItemGroup` on Paper 26.2 with Legacy 4.1.63. Inspection proved that the published 2.1.69 JAR contains that class. The exact original also rendered successfully in real-Paper tests. The server's actual installed/remapped artifact was not supplied; neither a missing source file nor a particular cache/hot-replacement cause is established.

JEG 2.1.70 is a complete replacement with prevention and recovery boundaries. It links its 43 renderer classes through its owning loader before replacing any core guide, without initializing static actions before managers exist. Missing/foreign-loaded classes reject JEG early while retaining the existing guide, with specific stop/replace/restart instructions. The shaded build, CI and publisher now check every compiled JEG class against the final archive, including nested classes. Existing guide layout/ownership, recipe-use search, cheat controls and SlimeHUD behavior are retained. No player data is reset.

## Standalone output and verified tests

JEG PR17 is merged and release v2.1.70 is published with raw `SF_JustEnoughGuide2.1.70.jar`. Published asset 604659170 and the downloaded publisher artifact 11206198333 have SHA-256 `4f45bd1d7c3615b48f750a2dec3ae877d5539c1fe1c57fddee3b35144e4ef335`, identical to the independently runtime-tested candidate. The actual archive contains 830 Java21 base classes, retains the reported nested renderer, and passes CRC/descriptor checks. Binary comparison against published 2.1.69 shows only one added class and one changed lifecycle class; all other classes are identical.

Normal clean-PR CI 36956837509 passed the retained English/layout/SlimeHUD/deprecation guards and full Gradle build. Nine Python archive tests and eight standalone-JDK linkage assertions passed. These are not seventeen JUnit test cases, and Gradle has no Java unit-test source. The existing historical test ledger is not rewritten or its aggregate counts inflated for these differently reported checks.

Real-Paper run 36956670506 uses the exact published Legacy4.1.63 core, actual guide/history/category handlers and real inventories with an explicitly synthetic no-network player/profile. It tests complete original2.1.69, complete replacement2.1.70, and a deliberately damaged replacement missing the reported nested class. Each of Paper1.21.11 stable132/Java21, Paper26.2 stable129/Java25 and Paper26.3 beta140/Java25 passed: both complete JARs rendered four menus; the damaged JAR was refused before guide takeover and the core guide remained installed. These are not connected-client tests or reproduction of the user's unknown installed bytes.

All runtime archives were downloaded and inspected: 11206431513 (1.21.11), 11205414226 (26.2), 11206282136 (26.3). Their hashes and exact assertions are recorded in JEG PR17 comment5944636516. The deliberate damaged-JAR case intentionally logs a linkage error; do not count it as an unexplained passing startup failure.

## Bundle release boundary

This PR selects the verified JEG source; its canonical 45-addon build and exact-source full-stack checks must pass separately. The existing published 4.1.64 core asset/tag and its revision104 ZIP remain untouched by creating this candidate. Do not relabel the earlier bundle or 4.1.63-targeted guide tests as the new whole-stack result. Publish a refreshed addon-only ZIP only after its own validation and preservation checks.

For the reported server, install the standalone JAR while fully stopped, remove duplicate older JEG root JARs and restart normally. Keep the JustEnoughGuide data folder, bookmarks and Slimefun/world storage. Regenerate only cached JEG files in Paper's .paper-remapped directory while stopped if necessary. A core upgrade or storage migration is not required for this guide replacement; hot-replacing a running JAR remains unsupported.
