# A successful addon enable is not proof its configuration loaded

## Runtime finding and gate correction

In priority-stack run 36783227345, all four addon enable lines and the clean-shutdown marker were observed, but FinalTECH's English YAML failed during the second boot on Paper 1.21.11, 26.2 and 26.3. The old gate only rejected enable/linkage exceptions and therefore returned success. That result is not a clean-restart certificate.

The shared full-stack smoke script now also rejects `InvalidConfigurationException` and the normal Cannot-load-YAML diagnostic. It uses the same predicate for classification and printed failure evidence. Existing linkage and enable checks remain intact, and benign startup/optional-dependency messages are not newly rejected.

A permanent source regression exercises the actual grep predicate on 16 log cases and three weakening controls. It also checks that detected errors return a failed cycle. It is registered in the normal invariant runner and a small read-only workflow, including PRs against feature branches.

## Evidence

Run 36785334366 passed all source invariants and the new regression. It downloaded the three original runtime artifacts by hash and checked both cycles: the old predicate accepted all six logs, while the corrected predicate rejected exactly the three second-boot configuration failures and accepted the first boots. Evidence artifact 11129052840, SHA-256 10b7545d04c25ed41bdaa25dd3fa045783352f80bafaa34e217db252b84f8c6a. All three promoted script blobs were independently verified. Initial temporary-workflow setup errors (Java default and a missing environment import) were corrected without changing the tested predicate or assertions.

FinalTECH PR #52, head 6a6adbe073a54c792653aca669362cf364ec853e, fixes the underlying language-file corruption. The corrected combined run 36785098780 used the same three Networks/IE2/Supreme candidates plus that fixed FinalTECH JAR and Legacy head 788b89e1 (test merge d600e077). With this exact shared error gate, two boot cycles passed for all four required addons on Paper 1.21.11 build 132, 26.2 build 129 and 26.3 build 140 BETA. No addon was omitted for a missing dependency. Downloaded logs had no ERROR/SEVERE or configuration exceptions, and the retained final English locale parsed successfully on all three versions.

Runtime artifacts: 11129467602 (1.21.11), 11129577411 (26.2), 11129123445 (26.3). These are a four-addon development subset, not the full canonical release bundle or a historical-world/gameplay certification.

## Integration scope

This is a test-only companion to PR #289, based on its concurrent head 62595cf11496b39d9b9857e9b304f48957b7ec99. The two modified script baselines still had the exact hashes tested before that head, so the concurrent item-envelope optimization is retained without rewriting it. No Java, item IDs, persistence schema, recipe, addon membership or release artifact changes here.

The stronger gate may expose configuration failures in older bundle members previously classified as green. Do not weaken it or reuse the earlier green label. Adopt and validate the fixed addon revision in the appropriate candidate bundle. A passing four-addon restart test does not certify an unchanged historical full bundle; the release matrix, historical-world and exact cross-fork checks remain separate gates. This companion does not merge PR #289, change its head or publish a stable release.
