# Coordinated integration checkpoint — September 30, 2026 (US Eastern)

Continues PR #292 at c1e04521 without changing production item or storage code.

## Implemented

- The proxy identity capture now waits for the research candidate/result line after UUID matching. The actual Bash capture loop is exercised with incrementally arriving logs. The original loop fails two tests; the correction passes all eight without weakening reconnect or unlocked-research assertions.
- Candidate full-stack PR runs wait for the successful addon bundle from the exact same PR head. They verify the actual core merge checkout, complete addon set, explicit source pins, unique JAR names and SHA-256 values. A failed/missing candidate never falls back to an older release archive. Ten fixture tests protect selection and validation. Scheduled/manual release compatibility remains a separate input mode.
- Full-stack runtime now includes Paper 1.21.11/Java 21 as well as 26.2 and 26.3/Java 25.
- Manifest revision 93 selects SlimeEasy 913a609c and Networks 41befeb6. All 45 inclusions and exclusions are preserved.
- SlimeEasy is rebuilt with its real Gradle build against the 1.21.11 native baseline after the separate newer-API probe. Only that final baseline-built JAR is collected. The exact candidate core replaces the old development library in the disposable build checkout; no source repository library is replaced.
- Candidate and native-floor compile logs are retained even for failed jobs. Failed builds still block aggregate packaging.

## Networks reconciliation

Networks 41befeb633baabd1e8ee6bc2fdd2b3a66694b643 combines PR #49's seven exact paths with all 17 master commits through fdbcb20b, including the pusher-registration hotfix. The two changed-file sets are disjoint. The normal Build Networks Universal run 36795617703 passed for the combined head. This does not claim all machines have been load-tested.

## Evidence

Integration validation run 36796008502 passed the original-loop negative control, corrected tests and all source invariants. Downloaded evidence 11132919269 matched SHA-256 554bd661367e27ad8c90e204be449f6bfef2ffefb172bd1af050c6cbbb7f75be. All eight reviewed source blobs were independently matched to local source.

The same audit collected 4,727 source/configuration/documentation files from all 45 maintained addon repositories at recorded exact revisions. Source archive 11133229044 matched SHA-256 04a7855e6bd2d4ca9db959d08ed8cf3bb5de607a7a0d3d6e6a9ec2edf71fa908. This is a complete maintained-source inventory for further review, not a claim that every file has been manually audited or every deprecated API removed.

The temporary audit and transport files are excluded from this integration. The promoted head must pass its own full bundle and runtime checks. Existing historical-world limitations still apply. No version bump, stable release, production installation or automatic data conversion is performed here.
