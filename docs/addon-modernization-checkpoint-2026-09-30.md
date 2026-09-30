# Addon modernization checkpoint — September 30, 2026

## Preserved contract

Minecraft 1.21.11+ is the runtime floor, not an age cutoff for saved items. Prioritize Paper/Purpur while retaining appropriate Leaf/Folia checks. Preserve IDs, research, typed PDC, ownership, inventory contents, recipes, output rates, transport order and rollback-relevant formats. Keep Doctor diagnostics read-only and migrations explicit. Do not reintroduce archived/integrated/excluded addons. No stable release, merge or production-server installation was performed in this batch.

## Exact ecosystem baseline

The release matrix defines 45 addons, including the non-SF-prefixed WorldEditSlimefun. The active SF_BetterStructuresLink repository is tracked separately, making 46 repositories. The complete snapshot verifies 4,894 file Git hashes, including 228 hidden/workflow files restored from their same pinned archives. Source inventory is not a compiler-warning or modernization-completion report.

Run 36782856742 compiled and packaged all 46 pinned repositories against the exact Legacy Doctor/storage core JAR (head 788b89e1, merge d600e077; SHA-256 48a4871ec83f7d7cdd0af5696ce1efb46c6437fa726575dc6805dc752c239778) and the resolver-selected 26.3-rc-3.build.1-alpha API. The existing generic probe skips unit tests and works on disposable source clones. It does not publish a release bundle or certify every addon gameplay path.

All 46 source/membership rows and results were independently checked against the initial snapshot, with only the three reviewed Networks/IE2/Supreme candidates overriding their original heads. FinalTECH's later locale fix is validated separately below; the broad compile used its preceding 0fedef35 revision. Result artifact 11129010883, SHA-256 40996ab02dd530577f5a05b0b77a8a6b1c25e519613188e2ced2a6a87c318a00. Complete source snapshot artifact 11126898841 preserves the baseline and per-file manifests.

## Implemented addon batches

| Repository / PR | Candidate | Changes | Actual focused/full tests |
| --- | --- | --- | --- |
| SF_NetworksExp #49 | cda26048b792900dd3dd16c1f6ca0075ed8de07b | Cache accessor metadata, not stored/live values; preserve fallback invocation and slot order while removing repeated discovery/stream allocation. | 47 passed, including 20 new cases; normal Legacy/United/Gugu matrix passed. |
| SF_InfinityExpansion2 #23 | 2aa423f8a389f4c719226d974197308736620d25 | Native PDC copy with unchanged target-wins rules; refuse failed metadata application instead of accepting incomplete conversion. | 8 new tests passed; original silent rejection reproduced; normal supported-API matrix passed. |
| SF_Supreme #19 | 24ef8251528403258ba0aa271a64766748c5efb7 | Remove local atomic counters/stream collection in quarry selection while preserving snapshot timing, inclusive boundaries, overflow and output identity. | 13 tests passed, including 80,000 old/new decision comparisons; normal core/API matrix passed. |
| SF_FinalTECH #52 | 6a6adbe073a54c792653aca669362cf364ec853e | Preserve valid Bukkit-saved English YAML; validate and back up limited syntax repair, refuse unrecoverable files. Keep merged Part 3 ticker work. | 20 tests passed, including 12 new locale/restart cases; original valid-document corruption reproduced; normal matrix passed. |

The addon changes add 53 new test cases in total. No item/research mapping, recipe, storage writer, energy cost, crafting speed or transfer-priority changes were introduced by these batches. IE2's existing explicit mapping remains opt-in; it was not globally enabled. Its real build still emitted 66 Kotlin warning lines, including presentation and historical storage bridges. Those remain visible follow-up work.

## Combined runtime verification

The initial four-addon smoke run exposed FinalTECH's second-boot YAML failure despite a green enable/linkage-only gate. That issue was fixed rather than dismissed. Run 36785098780 then booted the exact four normal candidate JARs together with the pinned Legacy core twice on Paper 1.21.11, 26.2 and 26.3 using the stricter configuration gate. All four were required to enable; zero dependency-gated addons. Downloaded logs and final language files were independently inspected and the failures were absent.

These results cover a fresh generated test server, clean restart and integration startup, not populated old-world upgrades, live Networks throughput, all Supreme generators, every IE2 conversion path or every Folia region interleaving. See runtime-configuration-error-gate.md for the gate evidence and retained limitations.

## Next coordinated work

Keep this tested candidate set pinned while reviewing the remaining addons in storage/machine/transport-sensitive batches. Continue presentation API cleanup only with behavior tests; retain compatibility bridges when old data or addon linkage needs them. Refresh the canonical candidate bundle from exact tested sources, test the full stack under the stricter gate, and retain separate actual historical-world and exact-build Gugu/United round trips. Native format overlap is not a blanket rollback guarantee. The current four-addon subset is not the final known-good 45-addon release.
