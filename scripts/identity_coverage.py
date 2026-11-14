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
CAPTURE_NAMES = ("identity-crafted-wire.bin", "identity-given-wire.bin",
                 "identity-produced-carbon.bin", "identity-produced-compressed-carbon.bin")
ENDER_PHASES = tuple("player-identity-ender-" + stage for stage in ("seed", "retire", "join", "restart"))

BACKPACK_PHASES = tuple("player-identity-backpack-" + stage for stage in ("saved", "opened", "restart", "loaded"))


def read_native_captures(captures_dir):
    """Fail closed if any required source-specific native capture is missing."""
    captures = {}
    for name in CAPTURE_NAMES:
        path = captures_dir / name
        if not path.is_file():
            raise ValueError("Missing captured actual output: " + name)
        captures[name] = path.read_bytes()
    return captures


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
    for phase in ("player-identity-give", "player-identity-craft", "player-identity-compressor", "player-identity-compressor-rejected", "player-identity-compressor-chain", "identity-output-restart") + ENDER_PHASES + BACKPACK_PHASES:
        expected = "RESOURCE_PACK_DOCTOR_PROBE_PASS " + phase + " | " + report["runtime"]["server_version"]
        if phases.get(phase) != expected:
            raise ValueError("Missing or wrong-runtime native phase: " + phase)
    # Bind each actual captured item to the *same* native execution receipt.
    # Phase names or nonempty files alone do not prove the captured bytes belong
    # to this invocation. Never promote template-only/synthetic evidence to PASS.
    required_names = CAPTURE_NAMES
    declared_hashes = evidence.get("captures_sha256")
    if not isinstance(declared_hashes, dict) or set(declared_hashes) != set(required_names):
        raise ValueError("Missing or extra native capture hash receipts")
    hashes = {}
    for filename in required_names:
        payload = captures.get(filename)
        if not isinstance(payload, bytes) or not payload:
            raise ValueError("Missing captured actual output: " + filename)
        digest = hashlib.sha256(payload).hexdigest()
        if not re.fullmatch(r"[0-9a-f]{64}", str(declared_hashes[filename])) or declared_hashes[filename] != digest:
            raise ValueError("Native capture does not match execution receipt: " + filename)
        hashes[filename] = digest
    items = {row["item_id"]: row for row in report["rows"]}
    cases = [(item, "give_command_17_plus_31", "player-identity-give")
             for item in ("SMALL_URANIUM", "COPPER_DUST", "CARBONADO")]
    cases += [("COPPER_WIRE", "enhanced_crafting_handler_given_ingredients", "player-identity-craft"),
              ("COPPER_WIRE", "native_item_bytes_process_restart", "identity-output-restart"),
              ("COPPER_WIRE", "player_ender_chest_reconnect_and_restart", "player-identity-ender-restart"),
              ("COPPER_WIRE", "backpack_database_save_and_restart", "player-identity-backpack-restart"),
              ("COPPER_WIRE", "backpack_public_api_reload_after_restart", "player-identity-backpack-loaded"),
              ("CARBON", "compressor_handler_coal_blocks_delayed_output", "player-identity-compressor"),
              ("COMPRESSED_CARBON", "compressor_handler_actual_carbon_input", "player-identity-compressor-chain")]
    for item, case, phase in cases:
        if item not in items:
            raise ValueError("Evidence item missing from registry: " + item)
        row = items[item]
        result["actual_cases"].append({
            **{key: row[key] for key in ("item_id", "plugin", "plugin_version")},
            "case": case, "status": "PASS", "phase": phase,
            "required_phases": (list(ENDER_PHASES) if phase == "player-identity-ender-restart" else
                                list(BACKPACK_PHASES) if phase in ("player-identity-backpack-restart", "player-identity-backpack-loaded") else
                                ["player-identity-compressor-rejected", phase, "identity-output-restart"]
                                if phase == "player-identity-compressor" else
                                ["player-identity-compressor", phase, "identity-output-restart"]
                                if phase == "player-identity-compressor-chain" else [phase]),
            "captured_sha256": (hashes if item == "COPPER_WIRE" else
                                {"identity-produced-carbon.bin": hashes["identity-produced-carbon.bin"]}
                                if item == "CARBON" else
                                {"identity-produced-compressed-carbon.bin":
                                    hashes["identity-produced-compressed-carbon.bin"]}
                                if item == "COMPRESSED_CARBON" else {}),
            "scope": "Specific fixture only; broad producer rows remain UNTESTED"})
    result["actual_case_counts"]["PASS"] = len(cases)
    return result



def attach_ie2_geo_evidence(report, evidence, capture, addon_bytes, probe_source):
    """Accept only a source-specific, same-artifact native IE2 GEO Quarry proof.

    A PASS here covers one SMALL_URANIUM fixture, not the broad machine row.
    A different core/server pair, a static GEO template check, or a capture
    from another run must never promote producer coverage.
    """
    if not isinstance(evidence, dict):
        raise ValueError("IE2 native evidence must be an object")
    for key in ("core_sha256", "server_sha256"):
        if evidence.get(key) != report.get(key):
            raise ValueError("IE2 GEO provenance mismatch: " + key)
    if not isinstance(addon_bytes, bytes) or not addon_bytes:
        raise ValueError("Missing exact IE2 addon artifact")
    addon_digest = hashlib.sha256(addon_bytes).hexdigest()
    if evidence.get("ie2_sha256") != addon_digest:
        raise ValueError("IE2 GEO addon artifact mismatch")
    if (not isinstance(probe_source, bytes) or not probe_source
            or len(probe_source) > 1024 * 1024):
        raise ValueError("Missing or oversized IE2 GEO native probe source")
    if evidence.get("probe_sha256") != hashlib.sha256(probe_source).hexdigest():
        raise ValueError("IE2 GEO native probe source digest mismatch")

    geo = evidence.get("geo_quarry")
    expected_fields = {"schema", "item_id", "producer", "scope", "status", "phases", "capture"}
    if not isinstance(geo, dict) or set(geo) != expected_fields:
        raise ValueError("Missing or unexpected GEO producer evidence fields")
    if (geo.get("schema") != 1 or geo.get("item_id") != "SMALL_URANIUM"
            or geo.get("producer") != "IE2_GEO_QUARRY" or geo.get("status") != "PASS"
            or geo.get("scope") != "actual registered machine output; disposable fixture only"
            or geo.get("phases") != ["seed", "verify"]):
        raise ValueError("Invalid GEO source-specific producer claim")
    phases = evidence.get("phases")
    if not isinstance(phases, dict) or set(phases) != {"seed", "verify"}:
        raise ValueError("Incomplete IE2 GEO native restart phases")
    for phase in ("seed", "verify"):
        result = phases[phase]
        marker = ("PASS " + phase + ": real SMALL_URANIUM cheat, GEO guide display, "
                  "GeoQuarry.process,")
        if (not isinstance(result, str) or result.count(marker) != 1
                or "failures: 0" not in result):
            raise ValueError("Missing genuine IE2 GEO native phase: " + phase)

    receipt = geo.get("capture")
    if not isinstance(receipt, dict) or set(receipt) != {"filename", "sha256", "size_bytes"}:
        raise ValueError("Malformed IE2 GEO output capture receipt")
    if receipt["filename"] != "native-geo-uranium.bin":
        raise ValueError("Unexpected IE2 GEO capture filename")
    if (not isinstance(capture, bytes) or not capture or len(capture) > 1024 * 1024
            or type(receipt["size_bytes"]) is not int or receipt["size_bytes"] != len(capture)
            or receipt["sha256"] != hashlib.sha256(capture).hexdigest()):
        raise ValueError("IE2 GEO captured output bytes do not match native receipt")

    source = [row for row in report.get("rows", [])
              if row.get("item_id") == "SMALL_URANIUM" and row.get("producer") == "machine"]
    if len(source) != 1:
        raise ValueError("IE2 GEO registered item absent or ambiguous")
    result = copy.deepcopy(report)
    cases = result.setdefault("actual_cases", [])
    if any(case.get("case") == "ie2_geo_quarry_actual_output_restart" for case in cases):
        raise ValueError("Duplicate IE2 GEO native case")
    cases.append({
        **{key: source[0][key] for key in ("item_id", "plugin", "plugin_version")},
        "case": "ie2_geo_quarry_actual_output_restart",
        "status": "PASS", "required_phases": ["seed", "verify"],
        "captured_sha256": {"native-geo-uranium.bin": receipt["sha256"]},
        "addon_sha256": addon_digest, "probe_sha256": evidence["probe_sha256"],
        "scope": "Specific actual IE2 GEO fixture only; broad machine rows remain UNTESTED",
    })
    counts = result.setdefault("actual_case_counts", {"PASS": 0})
    counts["PASS"] = len(cases)
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
    parser.add_argument("--ie2-geo-evidence", type=Path)
    parser.add_argument("--ie2-geo-capture", type=Path)
    parser.add_argument("--ie2-geo-addon-jar", type=Path)
    parser.add_argument("--ie2-geo-probe-source", type=Path)
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
            captures = read_native_captures(args.captures_dir)
        report = attach_native_evidence(report, evidence, captures)
    ie2_inputs = (args.ie2_geo_evidence, args.ie2_geo_capture,
                  args.ie2_geo_addon_jar, args.ie2_geo_probe_source)
    if any(path is not None for path in ie2_inputs):
        if any(path is None for path in ie2_inputs):
            raise ValueError("IE2 GEO external evidence requires evidence, capture, addon and native probe source")
        if any(path.is_symlink() or not path.is_file() for path in ie2_inputs):
            raise ValueError("IE2 GEO external evidence requires ordinary local files")
        report = attach_ie2_geo_evidence(
            report, json.loads(args.ie2_geo_evidence.read_text()),
            args.ie2_geo_capture.read_bytes(), args.ie2_geo_addon_jar.read_bytes(),
            args.ie2_geo_probe_source.read_bytes())
    args.output.write_text(json.dumps(report, indent=2) + "\n")
    print(f"Identity coverage inventory: {report['registered_items']} registered items; "
          f"{report['counts']['UNTESTED']} UNTESTED combinations; 0 validated producers")
    if "actual_case_counts" in report:
        print(f"Executed native fixture cases: {report['actual_case_counts']['PASS']} PASS; broad coverage unchanged")


if __name__ == "__main__":
    main()
