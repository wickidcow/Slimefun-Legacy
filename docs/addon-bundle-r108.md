## Addon bundle refresh — revision 108

The refreshed **SF_Addons_1.21.11-26.3.zip** replaces MagicExpansion 1.1.7 with **MagicExpansion 1.1.8**, including upstream Build 13 gameplay/storage improvements and the English lore cleanup. The other 44 addon JARs and Slimefun Legacy 4.1.67 core JAR remain byte-for-byte unchanged.

MagicExpansion is the standalone release artifact from source `3a22d9e7944a60b647c9f498b821ede783072813`, SHA-256 `3c52e719450c167ccf8ec8872747f3749d923e5e91bf4f768f5566eaee735349`. It retains Java 21 bytecode and the 1.21.11 API floor. Its source also passed 76 tests against Slimefun Legacy 4.1.67 and a separate Paper 26.3 compilation; the published JAR passed the two-boot Paper 26.3 beta 143 / Slimefun 4.1.67 cargo, sword, and shop checks.

The refreshed ZIP updates the manifest, checksums, and compatibility notes. Eight refresh-guard tests and the existing archive/bytecode tests cover wrong inputs, changed source pins, preservation, and reproducibility. Publication rechecks the latest release, core digest, and live ZIP digest before replacement, so a concurrent bundle update must be reviewed and rebased.

The original revision-107 release validation above remains historical evidence. This targeted refresh does not claim all 45 addons were rebuilt against the unchanged published core. Controlled server checks do not establish live-client combat, every historical world, crash atomicity, or Folia safety. MagicExpansion still has documented inherited deprecated APIs outside the updated sword.
