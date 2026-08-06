#!/usr/bin/env python3
"""Check a precompiled addon JAR for missing Slimefun classes, methods, and fields.

This is a focused JVM class-file linkage check. It does not execute addon code and it
only inspects references into compatibility-protected Slimefun package families.
"""
from __future__ import annotations

import argparse
import json
import struct
import sys
import zipfile
from dataclasses import dataclass, field
from pathlib import Path
from typing import BinaryIO, Iterable

CORE_PREFIXES = (
    "io/github/thebusybiscuit/slimefun4/",
    "me/mrCookieSlime/",
    "com/xzavier0722/mc/plugin/slimefun4/",
    "city/norain/slimefun4/",
)


class ClassFormatError(RuntimeError):
    pass


class Reader:
    def __init__(self, data: bytes):
        self.data = data
        self.offset = 0

    def read(self, size: int) -> bytes:
        end = self.offset + size
        if end > len(self.data):
            raise ClassFormatError("Unexpected end of class file")
        value = self.data[self.offset:end]
        self.offset = end
        return value

    def u1(self) -> int:
        return self.read(1)[0]

    def u2(self) -> int:
        return struct.unpack(">H", self.read(2))[0]

    def u4(self) -> int:
        return struct.unpack(">I", self.read(4))[0]


@dataclass(frozen=True, order=True)
class MemberRef:
    owner: str
    name: str
    descriptor: str
    kind: str


@dataclass
class ClassInfo:
    name: str
    super_name: str | None
    interfaces: tuple[str, ...]
    fields: set[tuple[str, str]] = field(default_factory=set)
    methods: set[tuple[str, str]] = field(default_factory=set)
    references: set[MemberRef] = field(default_factory=set)
    class_references: set[str] = field(default_factory=set)


@dataclass
class LinkageResult:
    addon_jar: str
    core_jar: str
    baseline_core_jar: str | None
    addon_classes: int
    checked_class_references: int
    checked_member_references: int
    missing_classes: list[str]
    missing_methods: list[MemberRef]
    missing_fields: list[MemberRef]

    @property
    def passed(self) -> bool:
        return not self.missing_classes and not self.missing_methods and not self.missing_fields

    def to_json(self) -> dict[str, object]:
        def member(ref: MemberRef) -> dict[str, str]:
            return {
                "owner": ref.owner,
                "name": ref.name,
                "descriptor": ref.descriptor,
                "kind": ref.kind,
            }

        return {
            "status": "PASS" if self.passed else "FAIL",
            "addon_jar": self.addon_jar,
            "core_jar": self.core_jar,
            "baseline_core_jar": self.baseline_core_jar,
            "addon_classes": self.addon_classes,
            "checked_class_references": self.checked_class_references,
            "checked_member_references": self.checked_member_references,
            "missing_classes": self.missing_classes,
            "missing_methods": [member(ref) for ref in self.missing_methods],
            "missing_fields": [member(ref) for ref in self.missing_fields],
        }


def cp_utf8(pool: list[object | None], index: int) -> str:
    try:
        entry = pool[index]
    except IndexError as error:
        raise ClassFormatError(f"Invalid constant-pool index {index}") from error
    if not isinstance(entry, tuple) or entry[0] != "Utf8":
        raise ClassFormatError(f"Constant-pool entry {index} is not UTF-8")
    return entry[1]


def cp_class_name(pool: list[object | None], index: int) -> str:
    entry = pool[index]
    if not isinstance(entry, tuple) or entry[0] != "Class":
        raise ClassFormatError(f"Constant-pool entry {index} is not a class")
    return cp_utf8(pool, entry[1])


def skip_attributes(reader: Reader, count: int) -> None:
    for _ in range(count):
        reader.u2()
        reader.read(reader.u4())


def parse_class(data: bytes) -> ClassInfo:
    reader = Reader(data)
    if reader.u4() != 0xCAFEBABE:
        raise ClassFormatError("Invalid class-file magic")
    reader.u2()
    reader.u2()
    cp_count = reader.u2()
    pool: list[object | None] = [None] * cp_count
    index = 1
    while index < cp_count:
        tag = reader.u1()
        if tag == 1:
            length = reader.u2()
            pool[index] = ("Utf8", reader.read(length).decode("utf-8", errors="replace"))
        elif tag in (3, 4):
            reader.read(4)
            pool[index] = ("Number",)
        elif tag in (5, 6):
            reader.read(8)
            pool[index] = ("WideNumber",)
            index += 1
        elif tag == 7:
            pool[index] = ("Class", reader.u2())
        elif tag == 8:
            pool[index] = ("String", reader.u2())
        elif tag in (9, 10, 11):
            kind = {9: "Fieldref", 10: "Methodref", 11: "InterfaceMethodref"}[tag]
            pool[index] = (kind, reader.u2(), reader.u2())
        elif tag == 12:
            pool[index] = ("NameAndType", reader.u2(), reader.u2())
        elif tag == 15:
            pool[index] = ("MethodHandle", reader.u1(), reader.u2())
        elif tag == 16:
            pool[index] = ("MethodType", reader.u2())
        elif tag in (17, 18):
            pool[index] = ("Dynamic", reader.u2(), reader.u2())
        elif tag in (19, 20):
            pool[index] = ("ModuleOrPackage", reader.u2())
        else:
            raise ClassFormatError(f"Unsupported constant-pool tag {tag}")
        index += 1

    reader.u2()
    this_class = reader.u2()
    super_class = reader.u2()
    name = cp_class_name(pool, this_class)
    super_name = cp_class_name(pool, super_class) if super_class else None
    interfaces = tuple(cp_class_name(pool, reader.u2()) for _ in range(reader.u2()))

    fields: set[tuple[str, str]] = set()
    for _ in range(reader.u2()):
        reader.u2()
        field_name = cp_utf8(pool, reader.u2())
        descriptor = cp_utf8(pool, reader.u2())
        fields.add((field_name, descriptor))
        skip_attributes(reader, reader.u2())

    methods: set[tuple[str, str]] = set()
    for _ in range(reader.u2()):
        reader.u2()
        method_name = cp_utf8(pool, reader.u2())
        descriptor = cp_utf8(pool, reader.u2())
        methods.add((method_name, descriptor))
        skip_attributes(reader, reader.u2())

    skip_attributes(reader, reader.u2())

    class_references: set[str] = set()
    references: set[MemberRef] = set()
    for entry in pool[1:]:
        if not isinstance(entry, tuple):
            continue
        if entry[0] == "Class":
            class_references.add(cp_utf8(pool, entry[1]))
        elif entry[0] in ("Fieldref", "Methodref", "InterfaceMethodref"):
            owner = cp_class_name(pool, entry[1])
            name_and_type = pool[entry[2]]
            if not isinstance(name_and_type, tuple) or name_and_type[0] != "NameAndType":
                raise ClassFormatError("Invalid member reference")
            member_name = cp_utf8(pool, name_and_type[1])
            descriptor = cp_utf8(pool, name_and_type[2])
            kind = "field" if entry[0] == "Fieldref" else "method"
            references.add(MemberRef(owner, member_name, descriptor, kind))

    return ClassInfo(name, super_name, interfaces, fields, methods, references, class_references)


def load_classes(jar: Path) -> dict[str, ClassInfo]:
    classes: dict[str, ClassInfo] = {}
    with zipfile.ZipFile(jar) as archive:
        for entry in archive.infolist():
            if entry.is_dir() or not entry.filename.endswith(".class"):
                continue
            if entry.filename.startswith("META-INF/versions/"):
                continue
            info = parse_class(archive.read(entry))
            classes[info.name] = info
    return classes


def is_core_name(name: str) -> bool:
    base = name
    while base.startswith("["):
        base = base[1:]
    if base.startswith("L") and base.endswith(";"):
        base = base[1:-1]
    return base.startswith(CORE_PREFIXES)


def resolves_member(
    classes: dict[str, ClassInfo], owner: str, name: str, descriptor: str, kind: str
) -> bool:
    visited: set[str] = set()

    def visit(class_name: str) -> bool:
        if class_name in visited:
            return False
        visited.add(class_name)
        info = classes.get(class_name)
        if info is None:
            return False
        members = info.fields if kind == "field" else info.methods
        if (name, descriptor) in members:
            return True
        if kind == "method" and name == "<init>":
            return False
        if info.super_name and visit(info.super_name):
            return True
        return any(visit(interface) for interface in info.interfaces)

    return visit(owner)


def analyze_linkage(
    addon_jar: Path, core_jar: Path, baseline_core_jar: Path | None = None
) -> LinkageResult:
    addon_classes = load_classes(addon_jar)
    core_classes = load_classes(core_jar)
    baseline_classes = load_classes(baseline_core_jar) if baseline_core_jar else None
    missing_classes: set[str] = set()
    missing_methods: set[MemberRef] = set()
    missing_fields: set[MemberRef] = set()
    checked_classes: set[str] = set()
    checked_members: set[MemberRef] = set()

    for addon_class in addon_classes.values():
        if is_core_name(addon_class.name):
            continue
        for referenced_class in addon_class.class_references:
            if not is_core_name(referenced_class):
                continue
            checked_classes.add(referenced_class)
            if referenced_class not in core_classes:
                missing_classes.add(referenced_class)

        for reference in addon_class.references:
            if not is_core_name(reference.owner):
                continue
            checked_members.add(reference)
            if reference.owner not in core_classes:
                missing_classes.add(reference.owner)
                continue
            if resolves_member(
                core_classes,
                reference.owner,
                reference.name,
                reference.descriptor,
                reference.kind,
            ):
                continue

            # A Methodref may name a Slimefun subclass even when the actual member
            # comes from Bukkit, Paper, or another external superclass. When a
            # known-good baseline is available, only classify a missing member as
            # a regression if that baseline resolved it inside the Slimefun JAR.
            if baseline_classes is not None and not resolves_member(
                baseline_classes,
                reference.owner,
                reference.name,
                reference.descriptor,
                reference.kind,
            ):
                continue

            if reference.kind == "field":
                missing_fields.add(reference)
            else:
                missing_methods.add(reference)

    return LinkageResult(
        addon_jar=str(addon_jar),
        core_jar=str(core_jar),
        baseline_core_jar=str(baseline_core_jar) if baseline_core_jar else None,
        addon_classes=len(addon_classes),
        checked_class_references=len(checked_classes),
        checked_member_references=len(checked_members),
        missing_classes=sorted(missing_classes),
        missing_methods=sorted(missing_methods),
        missing_fields=sorted(missing_fields),
    )


def write_report(result: LinkageResult, report_dir: Path) -> None:
    report_dir.mkdir(parents=True, exist_ok=True)
    payload = result.to_json()
    (report_dir / "result.json").write_text(
        json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    (report_dir / "status.txt").write_text(
        ("PASS" if result.passed else "FAIL") + "\n", encoding="utf-8"
    )

    lines = [
        "## Addon binary linkage",
        "",
        f"**Result:** `{'PASS' if result.passed else 'FAIL'}`",
        "",
        f"Addon classes inspected: **{result.addon_classes}**",
        "",
        f"Slimefun class references checked: **{result.checked_class_references}**",
        "",
        f"Slimefun member references checked: **{result.checked_member_references}**",
    ]
    if result.missing_classes:
        lines.extend(["", "### Missing classes", ""])
        lines.extend(f"- `{name}`" for name in result.missing_classes)
    if result.missing_methods:
        lines.extend(["", "### Missing methods", ""])
        lines.extend(
            f"- `{ref.owner}.{ref.name}{ref.descriptor}`" for ref in result.missing_methods
        )
    if result.missing_fields:
        lines.extend(["", "### Missing fields", ""])
        lines.extend(
            f"- `{ref.owner}.{ref.name}:{ref.descriptor}`" for ref in result.missing_fields
        )
    (report_dir / "summary.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("addon_jar", type=Path)
    parser.add_argument("core_jar", type=Path)
    parser.add_argument("report_dir", type=Path)
    parser.add_argument(
        "--baseline-core",
        type=Path,
        help="Known-good core used to distinguish removed Slimefun members from external inherited members",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    paths = [args.addon_jar, args.core_jar]
    if args.baseline_core:
        paths.append(args.baseline_core)
    for path in paths:
        if not path.is_file():
            print(f"Missing JAR: {path}", file=sys.stderr)
            return 2
    try:
        result = analyze_linkage(
            args.addon_jar.resolve(),
            args.core_jar.resolve(),
            args.baseline_core.resolve() if args.baseline_core else None,
        )
        write_report(result, args.report_dir.resolve())
    except (OSError, zipfile.BadZipFile, ClassFormatError) as error:
        print(f"Binary linkage instrumentation error: {error}", file=sys.stderr)
        return 2
    return 0 if result.passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
