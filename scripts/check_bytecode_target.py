#!/usr/bin/env python3
"""Verify that Slimefun-owned classes do not exceed the supported Java bytecode target."""
from __future__ import annotations

import argparse
import collections
import struct
import zipfile
from pathlib import Path

OWNED_PREFIXES = (
    "city/norain/slimefun4/",
    "com/xzavier0722/mc/plugin/slimefun4/",
    "io/github/thebusybiscuit/slimefun4/",
    "me/mrCookieSlime/",
    "net/guizhanss/slimefun4/",
)
EXCLUDED_PREFIXES = ("io/github/thebusybiscuit/slimefun4/libraries/",)


def java_for_major(major: int) -> int:
    return major - 44


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("jar", type=Path)
    parser.add_argument("--expected-java", type=int, required=True)
    parser.add_argument("--report", type=Path, default=Path("build/reports/bytecode-target.txt"))
    args = parser.parse_args()

    expected_major = args.expected_java + 44
    histogram: collections.Counter[int] = collections.Counter()
    offenders: list[tuple[str, int]] = []

    with zipfile.ZipFile(args.jar) as archive:
        for name in sorted(archive.namelist()):
            if not name.endswith(".class"):
                continue
            if not name.startswith(OWNED_PREFIXES) or name.startswith(EXCLUDED_PREFIXES):
                continue
            header = archive.read(name)[:8]
            if len(header) != 8 or header[:4] != b"\xca\xfe\xba\xbe":
                raise SystemExit(f"Invalid class header: {name}")
            major = struct.unpack(">H", header[6:8])[0]
            histogram[major] += 1
            if major > expected_major:
                offenders.append((name, major))

    if not histogram:
        raise SystemExit("No Slimefun-owned classes were found in the candidate JAR")

    report = args.report
    report.parent.mkdir(parents=True, exist_ok=True)
    lines = [
        "Slimefun Legacy bytecode target report",
        f"Expected maximum: Java {args.expected_java} (class major {expected_major})",
        "",
        "Class histogram:",
    ]
    for major, count in sorted(histogram.items()):
        lines.append(f"- Java {java_for_major(major)} / major {major}: {count}")
    if offenders:
        lines.extend(["", "Classes above target:"])
        lines.extend(f"- {name}: Java {java_for_major(major)} / major {major}" for name, major in offenders)
    else:
        lines.extend(["", "PASS: every Slimefun-owned class is within the configured target."])
    report.write_text("\n".join(lines) + "\n", encoding="utf-8")

    if offenders:
        print(f"Bytecode target failed: {len(offenders)} class(es) exceed Java {args.expected_java}.")
        return 1
    print(f"Bytecode target passed for {sum(histogram.values())} Slimefun-owned classes (Java {args.expected_java}).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
