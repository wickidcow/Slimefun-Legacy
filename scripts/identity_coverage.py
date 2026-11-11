#!/usr/bin/env python3
"""Inventory coverage gaps; registry presence must never count as producer validation."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import zipfile

PRODUCERS = ("cheat", "crafting", "machine", "cargo", "storage_restart", "unclassified")
STATUSES = ("PASS", "FAIL", "UNTESTED", "NOT_APPLICABLE", "BLOCKED")


def build_report(registry, source, core_sha256, server_sha256):
    if registry.get("schema") != 1:
        raise ValueError("Unsupported registry schema")
    if not re.fullmatch(r"[0-9a-f]{40}", source):
        raise ValueError("Missing exact core source")
    if any(not re.fullmatch(r"[0-9a-f]{64}", value) for value in (core_sha256, server_sha256)):
        raise ValueError("Missing artifact checksum")
    runtime = {key: registry.get(key) for key in ("software", "minecraft", "server_version")}
    if any(not isinstance(value, str) or not value.strip() for value in runtime.values()):
        raise ValueError("Missing runtime identity")
    items = registry.get("items")
    if not isinstance(items, list) or not items:
        raise ValueError("Empty registry is not coverage")
    rows, seen = [], set()
    for item in items:
        identity = {key: item.get(key) for key in ("item_id", "plugin", "plugin_version")}
        if any(not isinstance(value, str) or not value.strip() for value in identity.values()):
            raise ValueError("Incomplete registered item identity")
        key = identity["item_id"]
        if key in seen:
            raise ValueError("Duplicate registered item ID: " + key)
        seen.add(key)
        # Unknown applicability and undiscovered producers stay explicit, never N/A or PASS.
        for producer in PRODUCERS:
            rows.append({**identity, "producer": producer, "status": "UNTESTED",
                         "applicability": "UNKNOWN", "evidence": [],
                         "reason": "Actual producer output has not been captured"})
    rows.sort(key=lambda row: (row["plugin"], row["item_id"], row["producer"]))
    return {"schema": 1, "scope": "currently_registered_items_only",
            "core_source": source, "core_sha256": core_sha256, "server_sha256": server_sha256,
            "runtime": runtime, "registered_items": len(seen),
            "counts": {status: len(rows) if status == "UNTESTED" else 0 for status in STATUSES},
            "rows": rows}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("registry", "core", "server", "output"):
        parser.add_argument("--" + name, type=Path, required=True)
    args = parser.parse_args()
    with zipfile.ZipFile(args.core) as jar:
        props = dict(line.split("=", 1) for line in jar.read("git.properties").decode().splitlines()
                     if "=" in line and not line.startswith("#"))
    source = props.get("git.source.commit", "")
    if source != props.get("git.commit.id"):
        raise ValueError("Core source provenance mismatch")
    report = build_report(json.loads(args.registry.read_text()), source,
                          hashlib.sha256(args.core.read_bytes()).hexdigest(),
                          hashlib.sha256(args.server.read_bytes()).hexdigest())
    args.output.write_text(json.dumps(report, indent=2) + "\n")
    print(f"Identity coverage inventory: {report['registered_items']} registered items; "
          f"{report['counts']['UNTESTED']} UNTESTED combinations; 0 validated producers")


if __name__ == "__main__":
    main()
