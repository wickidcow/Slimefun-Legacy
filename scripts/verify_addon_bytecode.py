#!/usr/bin/env python3
"""Check a distributable addon's Java floor, including shaded base classes.

For a multi-release JAR, overlays above the requested Java version are not
loaded by that runtime and are reported separately. This checks class headers,
not API linkage, Minecraft compatibility, or gameplay behavior.
"""
from __future__ import annotations

import argparse
import collections
import json
import re
import sys
import zipfile
from pathlib import Path


def multi_release(manifest: str) -> bool:
    """Read the main manifest section, including continuation lines."""
    unfolded: list[str] = []
    for line in manifest.splitlines():
        if not line:
            break
        if line.startswith(" ") and unfolded:
            unfolded[-1] += line[1:]
        else:
            unfolded.append(line)
    return any(
        key.strip().casefold() == "multi-release" and value.strip().casefold() == "true"
        for line in unfolded if ":" in line
        for key, value in [line.split(":", 1)]
    )


def inspect_jar(path: Path, expected_java: int) -> dict:
    if expected_java < 8:
        raise ValueError("The configured addon Java floor must be at least 8")
    histogram: collections.Counter[int] = collections.Counter()
    problems: list[dict] = []
    ignored = 0
    base_count = 0
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError("Duplicate ZIP entries make class selection ambiguous")
        manifest = (archive.read("META-INF/MANIFEST.MF").decode("utf-8")
                    if "META-INF/MANIFEST.MF" in names else "")
        is_multi_release = multi_release(manifest)
        for name in sorted(names):
            if not name.endswith(".class"):
                continue
            if name.startswith("META-INF/versions/"):
                versioned = re.fullmatch(r"META-INF/versions/([1-9][0-9]*)/(.+)", name)
                if (not is_multi_release or not versioned
                        or int(versioned.group(1)) < 9 or int(versioned.group(1)) > expected_java):
                    ignored += 1
                    continue
            else:
                base_count += 1
            data = archive.read(name)
            if len(data) < 8 or data[:4] != b"\xca\xfe\xba\xbe":
                raise ValueError(f"Invalid class header: {name}")
            minor, major = int.from_bytes(data[4:6], "big"), int.from_bytes(data[6:8], "big")
            if major < 45:
                raise ValueError(f"Invalid class major version {major}: {name}")
            histogram[major] += 1
            if major > expected_java + 44 or minor == 65535:
                problems.append(dict(class_name=name, major=major, minor=minor))
    if not base_count:
        raise ValueError("No base classes were found in the distributable addon JAR")
    return dict(jar=path.name, maximum_java=expected_java, maximum_major=expected_java + 44,
                result="FAIL" if problems else "PASS", base_classes=base_count,
                checked_classes=sum(histogram.values()), ignored_versioned_classes=ignored,
                multi_release=is_multi_release, class_major_histogram=dict(sorted(histogram.items())),
                incompatible_classes=problems)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("jar", type=Path)
    parser.add_argument("--expected-java", type=int, default=21)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args(argv)
    try:
        report = inspect_jar(args.jar, args.expected_java)
    except (OSError, ValueError, UnicodeError, RuntimeError, zipfile.BadZipFile) as exc:
        report = dict(jar=args.jar.name, maximum_java=args.expected_java, result="FAIL", error=str(exc))
    if args.report is not None:
        # A failed/missing input must replace stale passing evidence too.
        try:
            args.report.parent.mkdir(parents=True, exist_ok=True)
            args.report.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        except OSError as exc:
            print(f"Addon bytecode report could not be written: {exc}", file=sys.stderr)
            return 1
    if report["result"] == "FAIL":
        print(f"Addon bytecode validation failed: {args.jar}", file=sys.stderr)
        if "error" in report:
            print(report["error"], file=sys.stderr)
        else:
            failures = report["incompatible_classes"]
            print(f"{len(failures)} classes exceed Java {args.expected_java} or require preview mode.", file=sys.stderr)
            for failure in failures[:20]:
                print(f"{failure['class_name']}: major={failure['major']}, minor={failure['minor']}", file=sys.stderr)
        return 1
    print(f"Addon bytecode validation passed: {args.jar.name}; {report['checked_classes']} classes checked against Java {args.expected_java}.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
