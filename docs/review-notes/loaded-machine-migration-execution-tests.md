# Loaded-machine migration execution regression coverage

This test-only follow-up protects the service-level behavior introduced by the exact loaded-machine migration lane.

Covered invariants:

- a candidate is handed to the migrator only after successful immediate revalidation;
- changed addon-owned state is skipped without mutation;
- provider mapping/version snapshots must still match the approved plan;
- MIGRATED, BLOCKED, FAILED and changed-state outcomes remain separately accounted;
- provider exceptions fail closed rather than escaping the Doctor execution boundary;
- missing/unloaded worlds are skipped without invoking addon code;
- execution does not leave a reusable prepared authorization behind.

No production behavior or public API is changed by this follow-up.
