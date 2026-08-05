# ADR 0002: Reviewed multi-fork upstream intake

- Status: Accepted
- Date: 2026-08-05

## Context

Slimefun Legacy can benefit from Original Slimefun, Gugu, Slimefun5, Slimefun United, and Slimefun4Core. These projects have different APIs, data assumptions, support targets, maturity, and goals. Treating every repository as a merge-compatible upstream would risk English regressions, API breaks, data loss, and incompatible gameplay changes.

## Decision

Maintain a machine-readable source registry with a role and review policy for every fork. Query source revisions through an advisory workflow, but never automatically merge or replace files. Record feature ideas in a separate backlog that cannot enable code by itself.

Gugu retains its existing guarded merge workflow. Every other source is selective-port or reference-only unless a later ADR explicitly changes that policy.

## Consequences

New ideas are visible without creating an unsafe dependency on another fork's history. Ports require an explicit implementation, tests, compatibility review, and release notes. The process is slower than automatic copying but substantially safer for production worlds and addon ecosystems.
