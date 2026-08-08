# Slimefun Legacy 4.1.18 validation

The drop-in contains permanent static verification for all new invariants and a finalization workflow that performs:

1. Idempotent classic/enhanced guide source generation.
2. Idempotent Item Doctor and release-document generation.
3. Spotless formatting.
4. The complete `scripts/verify_legacy.py` verification chain.
5. `spotlessCheck`, clean compilation, and tests using Java 25 with `-PprojectVersion=4.1.18`.
6. Release JAR selection, deterministic naming, and SHA-256 generation.
7. Artifact upload containing the JAR and checksum.

Local package checks cover Python syntax, updater idempotence against representative current source, Java delimiter balance, required source markers, workflow YAML parsing, ZIP integrity, and SHA-256 integrity. GitHub Actions remains the authoritative dependency-resolved Java compilation and test environment.
