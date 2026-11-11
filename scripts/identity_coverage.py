#!/usr/bin/env python3
"""Inventory coverage gaps; registry presence must never count as producer validation."""
import argparse
import copy
import hashlib
import json
from pathlib import Path
import re
import zipfile

PRODUCERS = ("cheat", "crafting", "machine", "cargo", "storage_restart", "unclassified")
STATUSES = ("PASS", "FAIL", "UNTESTED", "NOT_APPLICABLE", "BLOCKED")
ENDER_PHASES = tuple("player-identity-ender-" + stage for stage in ("seed", "retire", "join", "restart"))

BACKPACK_PHASES = tuple("player-identity-backpack-" + stage for stage in ("saved", "opened", "restart", "loaded"))

def attach_native_evidence(report, evidence, captures):
    """Record narrowly defined executed cases without promoting whole producer categories."""
    if evidence.get("result") != "PASS" or evidence.get("boots") != 3:
        raise ValueError("Incomplete native run")
    for key in ("core_sha256", "server_sha256"):
        if evidence.get(key) != report[key]:
            raise ValueError("Native evidence provenance mismatch: " + key)
    result = copy.deepcopy(report)
    result["actual_cases"] = []
    result["actual_case_counts"] = {"PASS": 0}
    if evidence.get("connected_player_checks") is False:
        return result
    if evidence.get("connected_player_checks") is not True:
        raise ValueError("Missing connected-player scope")
    phases = evidence.get("phases", {})
    for phase in ("player-identity-give", "player-identity-craft", "identity-output-restart") + ENDER_PHASES + BACKPACK_PHASES:
        expected = "RESOURCE_PACK_DOCTOR_PROBE_PASS " + phase + " | " + report["runtime"]["server_version"]
        if phases.get(phase) != expected:
            raise ValueError("Missing or wrong-runtime native phase: " + phase)
    hashes = {}
    for filename in ("identity-crafted-wire.bin", "identity-given-wire.bin"):
        payload = captures.get(filename)
        if not isinstance(payload, bytes) or not payload:
            raise ValueError("Missing captured actual output: " + filename)
        hashes[filename] = hashlib.sha256(payload).hexdigest()
    items = {row["item_id"]: row for row in report["rows"]}
    cases = [(item, "give_command_17_plus_31", "player-identity-give")
             for item in ("SMALL_URANIUM", "COPPER_DUST", "CARBONADO")]
    cases += [("COPPER_WIRE", "enhanced_crafting_handler_given_ingredients", "player-identity-craft"),
              ("COPPER_WIRE", "native_item_bytes_process_restart", "identity-output-restart"),
              ("COPPER_WIRE", "player_ender_chest_reconnect_and_restart", "player-identity-ender-restart"),
              ("COPPER_WIRE", "backpack_database_save_and_restart", "player-identity-backpack-restart"),
              ("COPPER_WIRE", "backpack_public_api_reload_after_restart", "player-identity-backpack-loaded")]
    for item, case, phase in cases:
        if item not in items:
            raise ValueError("Evidence item missing from registry: " + item)
        row = items[item]
        result["actual_cases"].append({
            **{key: row[key] for key in ("item_id", "plugin", "plugin_version")},
            "case": case, "status": "PASS", "phase": phase,
            "required_phases": (list(ENDER_PHASES) if phase == "player-identity-ender-restart" else
                                list(BACKPACK_PHASES) if phase in ("player-identity-backpack-restart", "player-identity-backpack-loaded") else [phase]),
            "captured_sha256": hashes if item == "COPPER_WIRE" else {},
            "scope": "Specific fixture only; broad producer rows remain UNTESTED"})
    result["actual_case_counts"]["PASS"] = len(cases)
    return result


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
    parser.add_argument("--native-evidence", type=Path)
    parser.add_argument("--captures-dir", type=Path)
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
    if args.native_evidence:
        evidence = json.loads(args.native_evidence.read_text())
        captures = {}
        if evidence.get("connected_player_checks") is True:
            if args.captures_dir is None:
                raise ValueError("Missing captures directory")
            captures = {name: (args.captures_dir / name).read_bytes()
                        for name in ("identity-crafted-wire.bin", "identity-given-wire.bin")}
        report = attach_native_evidence(report, evidence, captures)
    args.output.write_text(json.dumps(report, indent=2) + "\n")
    print(f"Identity coverage inventory: {report['registered_items']} registered items; "
          f"{report['counts']['UNTESTED']} UNTESTED combinations; 0 validated producers")
    if "actual_case_counts" in report:
        print(f"Executed native fixture cases: {report['actual_case_counts']['PASS']} PASS; broad coverage unchanged")


if __name__ == "__main__":
    main()
