# 2026-10-28 build-tool and Folia fixture integration gate

This document records a **draft validation candidate**, not a merge or release
authorization. Do not publish a new Slimefun Legacy JAR or replace any v4.1.78
artifact based on this document.

## Exact input revisions

- Gradle 9.8.2 upgrade [#496](https://github.com/wickidcow/Slimefun-Legacy/pull/496),
  candidate head `6830ff30c046783e388d45053373d1d1c0ea0a7c`:
  17/17 source-head workflows passed, including Paper 1.21.11 / 26.2 / 26.3.
- Spotless 8.10.4 upgrade [#497](https://github.com/wickidcow/Slimefun-Legacy/pull/497),
  candidate head `c0267a5f540b686544036c492dd0ced2234129f4`:
  dedicated formatter-regression [run 40734957487](https://github.com/wickidcow/Slimefun-Legacy/actions/runs/40734957487)
  passed. Its two disposable Git worktrees (Spotless 7.0.2 and 8.10.4)
  produced **zero byte differences across tracked formatted Java files**.
  The newer plugin's configuration did not emit the deprecated
  `Configuration.setVisible(boolean)` warning.
- Folia retirement-fixture correction [#498](https://github.com/wickidcow/Slimefun-Legacy/pull/498),
  candidate head `dc55a4ff338768f168d6526eea4ea631a4258872`:
  [run 40734650818](https://github.com/wickidcow/Slimefun-Legacy/actions/runs/40734650818)
  passed the exact-head seven-job Resource Pack Doctor matrix, including
  Folia 26.2 build 7 and ten strict TCP-reset proof tests.

## This combined validation branch

The combined branch starts at the **Spotless draft head** and copies only
these three byte-identical files from the Doctor draft:

- `scripts/resource_pack_doctor_probe.py`
- `scripts/test_resource_pack_doctor_probe.py`
- `.github/workflows/resource-pack-doctor.yml`

No Java gameplay files, persisted item IDs, API symbols, crafting or energy
semantics, server data, or publishing credentials are changed by this
integration-only work.

## Acceptance conditions

- Exact-head full build, public Java addon API and 44-addon bundle validation
  on Paper/Purpur 1.21.11 and 26.2, Paper 26.3+, plus Folia/Leaf safety gates.
- `spotlessCheck` and Spotless 7-to-8 byte-for-byte formatter parity.
- Explicit deprecation diagnostics and unchanged Java 21 distributable bytecode.
- Resource Pack Doctor full three-boot player proof and strict failure on
  unrelated network/retirement errors.
- Re-review old-world storage safety and release asset immutability before
  authorizing **any** merge or release.

This is not proof of live-world migrations or cross-fork rollback. The source
changes remain staged in separate drafts pending review.
