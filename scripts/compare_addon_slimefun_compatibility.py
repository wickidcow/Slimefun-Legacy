#!/usr/bin/env python3
"""Normalize Maven Slimefun coordinates before running the compatibility comparator.

Some maintained addons keep the Slimefun group/artifact coordinates in Maven
properties. The underlying comparator intentionally matches only the core
Slimefun dependency, so resolve only property-backed groupId/artifactId values
that prove to be a Slimefun core coordinate and then delegate to the unchanged
comparison engine.

The preserved comparison engine remains responsible for the Phase 1C linkage
primitives: analyze_linkage, binary_linkage, find_built_addon_jar, and
write_linkage_report.
"""
from __future__ import annotations

import re
import xml.etree.ElementTree as ET
from pathlib import Path

import compare_addon_slimefun_compatibility_base as base


PROPERTY_REFERENCE = re.compile(r"^\$\{([^}]+)\}$")
_original_patch_maven_dependency = base.patch_maven_dependency


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


base.patch_maven_dependency = patch_maven_dependency


if __name__ == "__main__":
    raise SystemExit(base.main())
