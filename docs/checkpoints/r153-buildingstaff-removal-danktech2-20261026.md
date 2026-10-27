# Slimefun Legacy 4.1.78 / r153 diagnostic checkpoint — October 26, 2026

**DO NOT MERGE OR PUBLISH FROM THIS DRAFT.** This change is an incremental, exact-source preview layered on PR #488. The v4.1.77 release and published r150 bundle remain immutable.

## Why r153 is necessary

- Dellrall's Fluid Pump PR #485 and maintainer duplicate #486 each pass the isolated core, gameplay, public API and broad platform checks, but their canonical Java addon-bundle runs failed for BuildingStaff's missing MetaLib:46 and DisplayModelLib:3 dependencies. Downstream full-stack tests were skipped because no complete artifact existed. Thus, neither PR is fully green or currently merger-ready under the user's required-check rule.
- PR #488 carries the merged-source r152 Networks/JEG/Magic/IE2 preview and Fluid Pump core repair, but its addon build fails on the old DankTech2 1.1.03 WildStackerAPI:2023.2 Maven POM (BG-Software HTTP 504). The upstream Paper smoke tests that passed cannot substitute for a complete source-locked release bundle.
- DankTech2 PR #7 has independently passed its exact-head CI (54 unit tests, Paper 1.21.11, 26.2 and 26.3 builds, raw Java 21-bytecode JAR). It updates the provided-only WildStackerAPI to 2026.3. The PR was merged as commit `410aff9e4dae5372a5ac148684ba5eeec01c0f48`; **there is no independently verified v1.1.04 GitHub release asset yet**.

## Precisely scoped r153 staging changes

- Drop BuildingStaff from the maintained release bundle **because the operator does not use or want the addon**. Do not continue the custom BuildingStaff fork. Optional compatibility fixes for the original developer plugin may be considered separately.
- Select exactly 44 independently pinned Java addon sources. The only source pin changing from r152 is DankTech2 to its final merged 1.1.04 commit above. All other 43 selected source commits remain byte-for-byte the same.
- Remove the BuildingStaff-only install/CI candidate overrides, update Folia expected count, and extend the existing exact-source Magic and Networks gameplay probes to r153.
- Keep all resource pack directories, Slimefun persisted data, Java recipes, Bedrock/Geyser integrations (none touched), published artifacts, tags and protected branch refs unchanged.

## Acceptance before any merge or publication

1. Exact-head source-locked r153 compilation, 44 JAR receipts and ZIP integrity, including successful source resolution for DankTech2 1.1.04 and 2026.3 provided API.
2. Four-JVM candidate evidence: Paper/Purpur at supported versions, and separate Leaf/Folia admission plus native restart checks; do not infer gameplay support from startup-only smoke.
3. Native storage, industrial autocrafter, conservation and duplicate prevention; validate DankTech2 pickup/packing with real WildStacker 2026.3 and alternate RoseStacker on disposable backed-up worlds.
4. Public API vs v4.1.77, two clean byte-identical Slimefun core builds, release plugin/asset hashes, unchanged optional resource pack bytes and canonical archive reproducibility. Validate v1.1.04 standalone release asset before promoting any source pin.
5. Reconcile / collapse the unmerged draft PR chain into a single validated release candidate without bypassing required checks. Preserve Dellrall's attribution and do not merge PR #485 or #486 twice. Both remain blocked until their exact required workflows pass on an eligible base.

No release is authorized by a green diagnostic run alone.
