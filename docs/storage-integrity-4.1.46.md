# Slimefun Legacy 4.1.46 Storage Integrity Diagnostics

Slimefun Legacy 4.1.46 includes an explicit, read-only storage integrity scan for administrators.

## Commands

- `/sf doctor storage status` shows the last completed scan and whether another scan is running.
- `/sf doctor storage scan` starts a backend ownership scan on Slimefun's existing storage read executor.

## What the scan checks

The scan compares primary block and universal records with the owners referenced by their secondary data and inventory tables. It reports secondary owners that do not have a matching primary record and includes a small bounded sample of identifiers for diagnosis.

## Safety model

The scan does not inspect physical world blocks, force-load chunks, mutate caches, delete rows, migrate records, or repair data. It records queued writes visible at the beginning and end of the scan and reports whether delayed-saving mode is enabled. Findings from a non-quiet scan are candidates only and should be confirmed by a later quiet-period scan before any repair feature is considered.

Repair is intentionally not part of this slice.
