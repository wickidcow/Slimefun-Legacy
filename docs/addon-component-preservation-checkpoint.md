# Coordinated addon component-preservation checkpoint

Continues PR #292 from `656f569327c1d7e706cb23604dba024ddbc17c54` without changing core production classes, item registration, recipes, storage encodings or migration policy. All 45 maintained inclusions and explicit exclusions remain intact. Minecraft 1.21.11 remains the floor; old saved-item support is not removed with obsolete server-version code.

## Exact source changes

| Addon | Prior candidate | New candidate | Scoped correction |
| --- | --- | --- | --- |
| Networks | `41befeb633baabd1e8ee6bc2fdd2b3a66694b643` | `5bc81686aa8e98275872d18e0ed39f83a3667c7c` | Native quantum-item lore preserves existing components and the historical tail layout; avoids repeated lore retrieval/conversion. |
| InfinityExpansion2 | `2aa423f8a389f4c719226d974197308736620d25` | `41318f5930b9af12059bd85a7057db7a05aa0691` | Infinity Matrix owner-line updates accept old missing lore and retain unrelated rich components. Ownership data and flight behavior stay unchanged. |
| Supreme | `24ef8251528403258ba0aa271a64766748c5efb7` | `d315b7ee3e495e215ddf22eb4686d8a088c25f4f` | Append only newly generated gear-description lines without flattening existing lore; keep enchantments/effects, spacer and append behavior. |

All three were validated against coordinated Legacy merge `9e8de71adf70e9a361a74c21afe5e7006c85faf6`. No item/research IDs, typed PDC values, amounts, owners, storage contents, encoding, recipes, rates, energy costs, transfer priorities, Doctor migration controls or resource-pack mappings were changed. Component data that cannot be represented in legacy strings is intentionally retained instead of being discarded. No normal gameplay formula is retuned.

## Test evidence before integration

| Addon | Exact-core validation | Actual full test executions | New cases |
| --- | --- | ---: | ---: |
| Networks | 36798950819 | 81 passed, no failures/errors/skips | 10 |
| InfinityExpansion2 | 36799119424 | 18 passed, no failures/errors/skips | 10 |
| Supreme | 36799413708 | 21 passed, no failures/errors/skips | 8 |

Networks first reproduced rich-lore loss in the original production implementation. Each suite includes 250 deterministic comparisons with its former legacy-string algorithm for representable lore, in addition to rich/missing-lore and protected-metadata cases. Total new cases are 28, with 750 old-layout comparisons. Tests exercise real item metadata and production helpers; they are not live server performance measurements or captured historical worlds. Existing data/Doctor/transport/quarry regressions remain included.

All three normal promoted PR builds subsequently passed: Networks `36799535137`, IE2 `36799588908`, Supreme `36799838066`. Their normal version matrices supplement the explicitly pinned-core validations; they do not establish final 45-addon runtime behavior until this new manifest is built and exercised.

Downloaded archive hashes and actual JUnit XML were independently checked, and all ten promoted source/build/test files match the reviewed local Git blob hashes. Exact evidence archives:

- Networks `11135331252`: SHA-256 `aff46d29cb88d5b0f96ad18ad700da79d3c3c6cc53d0286773c38a46485c9148`.
- IE2 `11134843642`: SHA-256 `47a568e85ef23d54cad358bd80f1249d4d92d8c50891fcf165291a470c403479`.
- Supreme `11134623677`: SHA-256 `3d6be0ac2472a705ad4c1ff36a3ff739f6602426d42987716a208e257247639c`.

Networks strict Java deprecation/removal compilation passed. IE2 still shows 66 Kotlin warning lines in other code. Supreme's verbose compiler report displays 100 deprecation diagnostics and may be capped, so it is not a complete remaining-warning count. Neither addon is described as fully deprecation-free. Test dependencies are not shipped, and production suppression/dependency boundaries were not relaxed.

## Reproducible complete bundle inputs

Manifest revision 94 pins all 45 addon sources. Exactly three sources advance to the candidates above; the other 42 are the exact source commits in the successfully built revision-93 archive from run `36796166184`. The downloaded archive's 45 JAR checksums, CRCs and base-class Java21 headers were verified locally, and its source revisions match the independently collected 45-repository source inventory. Pinning replaces 38 moving HEAD resolutions with their already-tested commits; it does not add/remove addons or invent updated versions.

The original matrix blob was checked as `8f6f50a01a009b17236ff1c23c43f6bb02f4dc4d`. The updated matrix is `1f0ae07562d8f07de2a0aa02fd52b7b5a856b954`, with all naming and exclusion settings preserved. Existing full-stack PR selection requires the new exact head/merge checkout, complete set, explicit source pins and checksums; a failed new bundle cannot fall back to the older release. The complete new build, Java21 distribution checks and 1.21.11/26.2/26.3 full-stack boots must independently pass.

This integration changes only the source manifest and this checkpoint. No core version bump, master merge, stable publication or production-server install occurs. Further FinalTECH and maintained-addon modernization, real historical-world upgrades, appropriate Folia concurrency tests and exact fork transitions remain separate work. Forward upgrades from older United/Gugu items remain relevant; reverse conversion to United is not a release gate under the current owner-confirmed scope.
