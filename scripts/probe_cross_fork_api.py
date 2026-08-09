#!/usr/bin/env python3
"""Compare representative common Slimefun API source markers between Legacy and an external fork checkout."""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path


def normalized(text: str) -> str:
    return re.sub(r"\s+", " ", text)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("matrix", type=Path)
    parser.add_argument("core_id")
    parser.add_argument("legacy_root", type=Path)
    parser.add_argument("external_root", type=Path)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()

    matrix = json.loads(args.matrix.read_text(encoding="utf-8"))
    core = next((entry for entry in matrix.get("cores", []) if entry.get("id") == args.core_id), None)
    if core is None:
        raise SystemExit(f"Unknown cross-fork core id: {args.core_id}")

    failures: list[str] = []
    lines = [
        f"# Cross-fork API source probe — {core.get('display_name', args.core_id)}",
        "",
        f"Repository: `{core.get('repository')}`",
        "",
    ]
    for probe in core.get("probes", []):
        rel = Path(probe["path"])
        legacy_file = args.legacy_root / rel
        external_file = args.external_root / rel
        if not legacy_file.is_file():
            failures.append(f"Legacy missing representative API file: {rel}")
            lines.append(f"- ❌ Legacy missing `{rel}`")
            continue
        if not external_file.is_file():
            failures.append(f"External fork missing representative API file: {rel}")
            lines.append(f"- ⚠️ External fork missing `{rel}`")
            continue

        legacy = normalized(legacy_file.read_text(encoding="utf-8"))
        external = normalized(external_file.read_text(encoding="utf-8"))
        missing_legacy = [token for token in probe["tokens"] if normalized(token) not in legacy]
        missing_external = [token for token in probe["tokens"] if normalized(token) not in external]
        if missing_legacy:
            failures.extend(f"Legacy missing token in {rel}: {token}" for token in missing_legacy)
        if missing_external:
            failures.extend(f"External fork missing token in {rel}: {token}" for token in missing_external)
        if missing_legacy or missing_external:
            lines.append(f"- ⚠️ `{rel}` differs on representative markers")
            for token in missing_legacy:
                lines.append(f"  - Legacy missing: `{token}`")
            for token in missing_external:
                lines.append(f"  - External missing: `{token}`")
        else:
            lines.append(f"- ✅ `{rel}` representative markers overlap")

    lines.extend(
        [
            "",
            "This source probe is a drift signal, not a binary-compatibility guarantee. Legacy's protected API",
            "baseline and addon source/binary compatibility matrix remain the release-blocking regression gates.",
            "",
            f"Result: **{'PASS' if not failures else 'DRIFT'}**",
        ]
    )
    report = "\n".join(lines) + "\n"
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(report, encoding="utf-8")
    print(report, end="")
    return 0 if not failures else 1


if __name__ == "__main__":
    raise SystemExit(main())
