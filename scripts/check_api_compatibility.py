#!/usr/bin/env python3
"""Fail when a released Slimefun Legacy public JVM signature disappears."""
from __future__ import annotations

import argparse
import subprocess
import zipfile
from pathlib import Path

PUBLIC_PREFIXES = (
    "io/github/thebusybiscuit/slimefun4/api/",
    "io/github/thebusybiscuit/slimefun4/core/attributes/",
    "io/github/thebusybiscuit/slimefun4/core/services/scheduling/",
    "me/mrCookieSlime/Slimefun/Objects/handlers/",
    "me/mrCookieSlime/Slimefun/api/",
)


def public_signatures(jar: Path) -> set[str]:
    signatures: set[str] = set()
    failures: list[str] = []
    with zipfile.ZipFile(jar) as archive:
        classes = sorted(
            name[:-6].replace("/", ".")
            for name in archive.namelist()
            if name.endswith(".class")
            and "$" not in name
            and name.startswith(PUBLIC_PREFIXES)
        )

    if not classes:
        raise RuntimeError(f"No compatibility-protected API classes found in {jar}")

    for class_name in classes:
        result = subprocess.run(
            ["javap", "-public", "-classpath", str(jar), class_name],
            check=False,
            capture_output=True,
            text=True,
        )
        if result.returncode != 0:
            failures.append(f"{class_name}: {result.stderr.strip() or 'javap failed'}")
            continue
        for raw in result.stdout.splitlines():
            line = " ".join(raw.strip().split())
            if not line or line.startswith("Compiled from") or line == "}" or line.endswith("{"):
                continue
            if line.startswith(("public ", "protected ")):
                signatures.add(f"{class_name} :: {line}")

    if failures:
        raise RuntimeError("Could not inspect API classes:\n" + "\n".join(failures))
    return signatures


def write_surface(path: Path, signatures: set[str], label: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        f"# {label}\n# Signature count: {len(signatures)}\n" + "\n".join(sorted(signatures)) + "\n",
        encoding="utf-8",
    )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("baseline", type=Path)
    parser.add_argument("candidate", type=Path)
    parser.add_argument("--allowlist", type=Path, default=Path("scripts/api-removal-allowlist.txt"))
    args = parser.parse_args()

    baseline = public_signatures(args.baseline)
    candidate = public_signatures(args.candidate)
    allowed = set()
    if args.allowlist.exists():
        allowed = {
            line.strip()
            for line in args.allowlist.read_text(encoding="utf-8").splitlines()
            if line.strip() and not line.lstrip().startswith("#")
        }

    removed_all = baseline - candidate
    removed = sorted(removed_all - allowed)
    approved_removed = sorted(removed_all & allowed)
    added = sorted(candidate - baseline)
    stale_allowlist = sorted(allowed - removed_all)

    report = Path("build/reports/api-compatibility.txt")
    report.parent.mkdir(parents=True, exist_ok=True)
    lines = [
        "Slimefun Legacy public API compatibility report",
        f"Baseline signatures: {len(baseline)}",
        f"Candidate signatures: {len(candidate)}",
        f"Added signatures: {len(added)}",
        f"Removed signatures: {len(removed_all)}",
        "",
        "Unapproved removed public signatures:",
        *(removed or ["None"]),
        "",
        "Approved removed public signatures:",
        *(approved_removed or ["None"]),
        "",
        "Added public signatures:",
        *(added or ["None"]),
        "",
        "Stale allowlist entries:",
        *(stale_allowlist or ["None"]),
    ]
    report.write_text("\n".join(lines) + "\n", encoding="utf-8")
    write_surface(Path("build/reports/api-surface-baseline.txt"), baseline, "Baseline API surface")
    write_surface(Path("build/reports/api-surface-candidate.txt"), candidate, "Candidate API surface")

    if removed:
        print(f"Detected {len(removed)} unapproved public API removal(s):")
        print("\n".join(removed))
        return 1

    print(
        f"API compatibility passed: {len(baseline)} baseline signatures, "
        f"{len(added)} addition(s), {len(approved_removed)} approved removal(s)."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
