#!/usr/bin/env python3
"""Verify core-owned and optionally every packaged class against the Java bytecode target."""
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
    parser.add_argument("--all-classes", action="store_true", help="Also validate shaded and external classes")
    parser.add_argument("--report", type=Path, default=Path("build/reports/bytecode-target.txt"))
    args = parser.parse_args()

    expected_major = args.expected_java + 44
    histogram: collections.Counter[int] = collections.Counter()
    offenders: list[tuple[str, int]] = []
    unsupported_minors: list[tuple[str, int, int]] = []
    invalid_majors: list[tuple[str, int]] = []
    scope = "packaged" if args.all_classes else "Slimefun-owned"

    with zipfile.ZipFile(args.jar) as archive:
        for name in sorted(archive.namelist()):
            if not name.endswith(".class"):
                continue
            if not args.all_classes and (not name.startswith(OWNED_PREFIXES) or name.startswith(EXCLUDED_PREFIXES)):
                continue
            header = archive.read(name)[:8]
            if len(header) != 8 or header[:4] != b"\xca\xfe\xba\xbe":
                raise SystemExit(f"Invalid class header: {name}")
            minor, major = struct.unpack(">HH", header[4:8])
            histogram[major] += 1
            if major > expected_major:
                offenders.append((name, major))
            if major < 45:
                invalid_majors.append((name, major))
            # JVMS 4.1: major >= 56 uses minor 65535 for release-specific preview
            # features; other nonzero minors are invalid. A stable distributable
            # must load without --enable-preview. Majors 45..55 allow any minor.
            if major >= 56 and minor != 0:
                unsupported_minors.append((name, major, minor))

    if not histogram:
        raise SystemExit(f"No {scope} classes were found in the candidate JAR")

    report = args.report
    report.parent.mkdir(parents=True, exist_ok=True)
    lines = [
        "Slimefun Legacy bytecode target report",
        f"Expected maximum: Java {args.expected_java} (class major {expected_major})",
        f"Inspection scope: {scope} classes",
        "",
        "Class histogram:",
    ]
    for major, count in sorted(histogram.items()):
        lines.append(f"- Java {java_for_major(major)} / major {major}: {count}")
    if offenders:
        lines.extend(["", "Classes above target:"])
        lines.extend(f"- {name}: Java {java_for_major(major)} / major {major}" for name, major in offenders)
    if unsupported_minors:
        lines.extend(["", "Classes requiring preview features or using invalid minor versions:"])
        for name, major, minor in unsupported_minors:
            reason = "preview bytecode is not distributable" if minor == 65535 else "invalid minor version"
            lines.append(f"- {name}: Java {java_for_major(major)} / version {major}.{minor}: {reason}")
    if invalid_majors:
        lines.extend(["", "Class headers with invalid JVM major versions:"])
        lines.extend(f"- {name}: major {major} is below 45" for name, major in invalid_majors)
    if not offenders and not unsupported_minors and not invalid_majors:
        lines.extend(["", f"PASS: every {scope} class is within the configured target."] )
    report.write_text("\n".join(lines) + "\n", encoding="utf-8")

    if offenders:
        print(f"Bytecode target failed: {len(offenders)} class(es) exceed Java {args.expected_java}.")
        return 1
    if unsupported_minors:
        print(f"Bytecode target failed: {len(unsupported_minors)} class(es) use preview or invalid minor versions.")
        return 1
    if invalid_majors:
        print(f"Bytecode target failed: {len(invalid_majors)} class(es) have invalid JVM majors.")
        return 1
    print(f"Bytecode target passed for {sum(histogram.values())} {scope} classes (Java {args.expected_java}).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
