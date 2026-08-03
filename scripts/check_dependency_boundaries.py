#!/usr/bin/env python3
"""Prevent sensitive implementation dependencies from spreading to new source files."""
from __future__ import annotations

import argparse
import json
import re
from pathlib import Path

IMPORT = re.compile(r"^\s*import\s+(?:static\s+)?([^;]+);")


def scan_group(source_root: Path, prefixes: tuple[str, ...]) -> dict[str, int]:
    imports: dict[str, int] = {}
    for source in sorted(source_root.rglob("*.java")):
        count = 0
        for line in source.read_text(encoding="utf-8").splitlines():
            match = IMPORT.match(line)
            if match and any(match.group(1).startswith(prefix) for prefix in prefixes):
                count += 1
        if count:
            imports[source.as_posix()] = count
    return imports


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("root", nargs="?", type=Path, default=Path("."))
    parser.add_argument(
        "--baseline",
        type=Path,
        default=Path("scripts/dependency-boundary-baseline.json"),
    )
    args = parser.parse_args()

    root = args.root.resolve()
    baseline_path = args.baseline if args.baseline.is_absolute() else root / args.baseline
    baseline = json.loads(baseline_path.read_text(encoding="utf-8"))
    source_root = root / baseline["source_root"]

    failures: list[str] = []
    report_lines = ["Slimefun Legacy dependency-boundary report", ""]
    for group_name, policy in baseline["groups"].items():
        prefixes = tuple(policy["prefixes"])
        current_absolute = scan_group(source_root, prefixes)
        current = {
            str(Path(path).relative_to(root).as_posix()): count
            for path, count in current_absolute.items()
        }
        allowed_files = {str(path): int(count) for path, count in policy["allowed_files"].items()}
        total = sum(current.values())
        max_imports = int(policy["max_imports"])
        new_files = sorted(set(current) - set(allowed_files))
        increased_files = sorted(
            path for path, count in current.items() if count > allowed_files.get(path, 0)
        )

        report_lines.append(
            f"{group_name}: {total}/{max_imports} imports across {len(current)} approved file(s)"
        )
        if total > max_imports:
            failures.append(f"{group_name}: import budget grew from {max_imports} to {total}")
        if new_files:
            failures.append(f"{group_name}: new importing files: {', '.join(new_files)}")
        if increased_files:
            details = ", ".join(
                f"{path} ({allowed_files.get(path, 0)} -> {current[path]})"
                for path in increased_files
            )
            failures.append(f"{group_name}: per-file import count increased: {details}")

    report = root / "build/reports/dependency-boundaries.txt"
    report.parent.mkdir(parents=True, exist_ok=True)
    if failures:
        report_lines.extend(["", "Failures:", *[f"- {failure}" for failure in failures]])
    else:
        report_lines.extend(
            [
                "",
                "PASS: no sensitive dependency boundary expanded.",
                "Import removal is allowed; baseline expansion requires a reviewed compatibility change.",
            ]
        )
    report.write_text("\n".join(report_lines) + "\n", encoding="utf-8")

    if failures:
        print("Dependency-boundary verification failed:")
        for failure in failures:
            print(f"- {failure}")
        return 1

    print("Compatibility Foundation dependency boundaries passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
