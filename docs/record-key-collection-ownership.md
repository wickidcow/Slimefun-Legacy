# RecordKey collection ownership

## Correction and scope

The three-argument RecordKey constructor copied nonempty field/condition collections but retained an empty caller collection directly. Consequently an immutable empty input rejected later addField/addCondition operations, while a mutable empty input could be modified by either the caller or another key sharing the same object. Such external mutations also bypass the key's cached-text invalidation.

The constructor now owns both collections for empty and nonempty inputs. The convenience constructors pass immutable empty inputs to that copying boundary instead of allocating an additional throwaway collection. Existing mutable key APIs, field set semantics, condition order, textual identity for equivalent valid construction, equals/hash behavior and unmodifiable getter views remain unchanged. Pair elements retain the original shallow-copy semantics; this is not deep immutability or thread-safety for arbitrary concurrent callers.

No database schema, stored item/machine/backpack identity, serialized item format, research ID, recipe, machine rate, save timing or migration setting is changed. This patch does not rewrite existing records. It does not fix every possible mutable queue-key interaction or establish that this constructor defect caused any particular production world's data loss. External callers must use the key's supported add methods rather than expecting mutation of a previously supplied collection to change a key.

## Actual validation

Isolated run36960909723 checked out the current maintenance baseline, added the permanent regression class, and first exercised the unchanged constructor. All13 tests ran; eight failed assertions with zero errors/skips. After the scoped constructor correction, all13 passed, including1000 independent constructions from shared empty inputs. Cases cover immutable empty collections, caller-to-key and key-to-caller mutation, separate keys, existing nonempty copying, order, cached identity and public views.

The full uncached core build, existing source invariants, formatting, production deprecation/removal check, Java21 bytecode and artifact metadata/packaging checks passed. Downloaded artifact11208330043 matched SHA256 `57f2aed3e512cd1deb3ce74dd76537658f5e094e1ac0c12008300e41203775e8`. Actual inspection of125 corrected XML reports found542 reported entries, zero failures/errors and one existing historical-database-fixture skip. The new test class has13/0/0/0. This is generated in-memory regression coverage, not a captured historical-world or connected-player test.

The exact promoted production blob is `c2f32181a4367a8eba2cfeaac8eb98b742f4558b`; test blob `cd9ae0f54b037c16ad5db84997ab09c7163b8773`. Both were verified against the downloaded tested source. Other formatter changes and temporary audit workflows are excluded. The first audit attempt stopped before tests because the new test package directory was missing; adding the directory corrected only setup and did not weaken an assertion.

## Integration boundary

This is separate from the addon-only revision106 reconciliation and its JEG packaging correction. It must pass its own normal core/API/platform/whole-stack gates before merge. The published4.1.64 core/tag and addon ZIP are unchanged by opening the PR, and no new core version or release is published. Remaining caller and populated-storage coverage must be reported separately rather than inferred from this small constructor fix.
