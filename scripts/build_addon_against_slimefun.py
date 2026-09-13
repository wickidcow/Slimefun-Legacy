#!/usr/bin/env python3
"""Build one addon against an exact Slimefun Legacy candidate JAR for runtime smoke testing."""

from __future__ import annotations

import argparse
import shutil
import sys
from pathlib import Path

# Importing this wrapper installs its Maven-coordinate normalization into the shared base engine.
import compare_addon_slimefun_compatibility  # noqa: F401
import compare_addon_slimefun_compatibility_base as compatibility


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("slimefun_jar", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--github-output", type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    source = args.source.resolve()
    slimefun_jar = args.slimefun_jar.resolve()
    output = args.output.resolve()

    if not source.is_dir():
        print(f"Addon source directory does not exist: {source}", file=sys.stderr)
        return 1
    if not slimefun_jar.is_file() or slimefun_jar.stat().st_size == 0:
        print(f"Slimefun candidate JAR does not exist or is empty: {slimefun_jar}", file=sys.stderr)
        return 1

    if output.exists():
        shutil.rmtree(output)
    output.mkdir(parents=True)
    project = output / "project"
    report = output / "report"
    report.mkdir(parents=True)

    try:
        compatibility.copy_project(source, project)
        result = compatibility.build_project(
            label="runtime",
            project=project,
            jar=slimefun_jar,
            report_dir=report,
        )
    except Exception as error:
        print(f"Addon runtime build instrumentation failed: {error}", file=sys.stderr)
        return 1

    if result.exit_code != 0:
        print(f"Addon build failed with exit code {result.exit_code}. See {report / result.log_file}", file=sys.stderr)
        return 1
    if result.output_jar is None:
        print("Addon build completed but no runtime JAR was found.", file=sys.stderr)
        return 1

    built_jar = Path(result.output_jar)
    if not built_jar.is_file() or built_jar.stat().st_size == 0:
        print(f"Built addon JAR is missing or empty: {built_jar}", file=sys.stderr)
        return 1

    staged_jar = output / "addon.jar"
    shutil.copy2(built_jar, staged_jar)
    print(f"Built runtime addon JAR: {staged_jar}")
    print(f"Build log: {report / result.log_file}")

    if args.github_output:
        args.github_output.parent.mkdir(parents=True, exist_ok=True)
        with args.github_output.open("a", encoding="utf-8") as github_output:
            github_output.write(f"jar={staged_jar}\n")
            github_output.write(f"build_log={report / result.log_file}\n")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
