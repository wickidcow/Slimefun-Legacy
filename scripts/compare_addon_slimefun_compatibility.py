#!/usr/bin/env python3
"""Normalize legacy addon repository shapes before compatibility comparison.

The normal path still delegates to the Phase 1C comparison engine, which builds
an addon against the previous stable and candidate Slimefun Legacy JARs and
then performs baseline-proven binary linkage.

A few historical addons need narrowly-scoped normalization before that engine
can do its job:
- Maven coordinates from older Gugu-era forks may use the StarWishsama group.
- Old Gradle/Maven wrappers may have CRLF shebangs that Linux cannot execute.
- RykenSlimefunCustomizer content packs have no JVM build at all and are
  validated with their own maintained repository audit instead of receiving a
  fake bytecode/linkage result.
"""
from __future__ import annotations

import json
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

import compare_addon_slimefun_compatibility_base as base


PROPERTY_REFERENCE = re.compile(r"^\$\{([^}]+)\}$")
_original_patch_maven_dependency = base.patch_maven_dependency
_original_make_executable = base.make_executable

# Older Gugu-era Maven projects used com.github.StarWishsama:Slimefun4.
# Keep matching constrained to the existing Slimefun/Slimefun4 artifact names.
if "starwishsama" not in base.CORE_GROUP_HINTS:
    base.CORE_GROUP_HINTS = (*base.CORE_GROUP_HINTS, "starwishsama")

MAGIC_RSC_AUDIT = (
    ("scripts/validate_saveditems_yaml.py",),
    ("scripts/validate_script_refs.py",),
    ("scripts/audit_magic.py",),
    ("scripts/audit_magic_quality.py",),
    ("tools/verify_polyglot_runtime_fix.py",),
)


def local_name(tag: str) -> str:
    return tag.split("}")[-1]


def resolve_property(value: str, properties: dict[str, str]) -> str:
    """Resolve a coordinate that consists entirely of one Maven property."""
    resolved = value.strip()
    seen: set[str] = set()

    for _ in range(10):
        match = PROPERTY_REFERENCE.fullmatch(resolved)
        if match is None:
            return resolved

        key = match.group(1)
        if key in seen:
            return resolved
        seen.add(key)

        replacement = properties.get(key)
        if replacement is None:
            return resolved
        resolved = replacement.strip()

    return resolved


def normalize_property_backed_core_dependency(project: Path) -> None:
    pom = project / "pom.xml"
    if not pom.is_file():
        return

    tree = ET.parse(pom)
    root = tree.getroot()
    namespace = root.tag.partition("}")[0].strip("{") if "}" in root.tag else ""

    properties: dict[str, str] = {}
    for child in root:
        if local_name(child.tag) != "properties":
            continue
        for prop in child:
            properties[local_name(prop.tag)] = prop.text or ""

    changed = False
    for dependency in root.iter():
        if local_name(dependency.tag) != "dependency":
            continue

        children = {local_name(child.tag): child for child in dependency}
        group_node = children.get("groupId")
        artifact_node = children.get("artifactId")
        if group_node is None or artifact_node is None:
            continue

        raw_group = group_node.text or ""
        raw_artifact = artifact_node.text or ""
        resolved_group = resolve_property(raw_group, properties)
        resolved_artifact = resolve_property(raw_artifact, properties)

        if not base.is_core_slimefun_dependency(resolved_group, resolved_artifact):
            continue

        if raw_group.strip() != resolved_group:
            group_node.text = resolved_group
            changed = True
        if raw_artifact.strip() != resolved_artifact:
            artifact_node.text = resolved_artifact
            changed = True

    if changed:
        if namespace:
            ET.register_namespace("", namespace)
        tree.write(pom, encoding="utf-8", xml_declaration=True)


def patch_maven_dependency(project: Path, version: str) -> bool:
    normalize_property_backed_core_dependency(project)
    return _original_patch_maven_dependency(project, version)


def normalize_wrapper_and_make_executable(path: Path) -> None:
    """Make historical POSIX wrappers executable and remove CRLF shebangs."""
    data = path.read_bytes()
    normalized = data.replace(b"\r\n", b"\n")
    if normalized != data:
        path.write_bytes(normalized)
    _original_make_executable(path)


def is_magic_rsc_content_pack(source: Path) -> bool:
    """Recognize the maintained Ryken Magic content repository without guessing."""
    if any((source / name).is_file() for name in ("pom.xml", "build.gradle", "build.gradle.kts")):
        return False
    if not (source / "info.yml").is_file():
        return False
    return all((source / command[0]).is_file() for command in MAGIC_RSC_AUDIT)


def run_magic_rsc_content_audit(source: Path, report_dir: Path) -> int:
    """Run the content repository's own maintained audit and emit normal CI artifacts."""
    report_dir.mkdir(parents=True, exist_ok=True)
    log_path = report_dir / "content-validation.log"
    checks: list[dict[str, object]] = []
    passed = True

    with log_path.open("w", encoding="utf-8") as log:
        for relative_command in MAGIC_RSC_AUDIT:
            command = [sys.executable, *relative_command]
            rendered = " ".join(command)
            header = f"$ {rendered}\n"
            print(header, end="")
            log.write(header)
            result = subprocess.run(
                command,
                cwd=source,
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                errors="replace",
                check=False,
            )
            output = result.stdout or ""
            print(output, end="")
            log.write(output)
            log.write(f"\nProcess exit code: {result.returncode}\n\n")
            checks.append(
                {
                    "command": relative_command[0],
                    "exit_code": result.returncode,
                }
            )
            if result.returncode != 0:
                passed = False

    status = base.PASS if passed else base.BASELINE_BUILD_FAILED
    payload = {
        "status": status,
        "source": str(source),
        "source_commit": base.source_commit(source),
        "validation_mode": "content",
        "validation_profile": "magic-rsc",
        "checks": checks,
        "baseline": None,
        "candidate": None,
        "binary_linkage": None,
        "error": None,
    }
    (report_dir / "result.json").write_text(
        json.dumps(payload, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    (report_dir / "status.txt").write_text(status + "\n", encoding="utf-8")

    lines = [
        "## Addon compatibility comparison",
        "",
        f"**Result:** `{status}`",
        "",
        "**Validation mode:** `content` (RykenSlimefunCustomizer pack; no JVM bytecode/linkage applies)",
        "",
        (
            "The repository's own maintained Magic content audit passed."
            if passed
            else "The repository's own maintained Magic content audit failed; this is not evidence of a new Slimefun Legacy API regression."
        ),
        "",
        "| Content audit | Exit code |",
        "| --- | ---: |",
    ]
    for check in checks:
        lines.append(f"| `{check['command']}` | {check['exit_code']} |")
    lines.extend(["", "No Java source build or binary linkage test is applicable to this content-only repository."])
    (report_dir / "summary.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    return base.EXIT_CODES[status]


def maybe_run_content_audit() -> int | None:
    """Intercept content-only targets while preserving the normal comparator CLI."""
    if len(sys.argv) != 5:
        return None
    source = Path(sys.argv[1]).resolve()
    report_dir = Path(sys.argv[4]).resolve()
    if source.is_dir() and is_magic_rsc_content_pack(source):
        return run_magic_rsc_content_audit(source, report_dir)
    return None


base.patch_maven_dependency = patch_maven_dependency
base.make_executable = normalize_wrapper_and_make_executable


if __name__ == "__main__":
    content_result = maybe_run_content_audit()
    if content_result is not None:
        raise SystemExit(content_result)
    raise SystemExit(base.main())
