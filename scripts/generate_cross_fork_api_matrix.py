#!/usr/bin/env python3
"""Validate and emit the Phase 1J external cross-fork source-probe matrix."""
from __future__ import annotations

import argparse
import json
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("matrix", type=Path)
    parser.add_argument("--github-output", type=Path)
    args = parser.parse_args()

    data = json.loads(args.matrix.read_text(encoding="utf-8"))
    if data.get("schema") != 1:
        raise SystemExit("Unsupported cross-fork matrix schema")
    cores = data.get("cores")
    if not isinstance(cores, list) or not cores:
        raise SystemExit("Cross-fork matrix has no core targets")

    output = []
    ids: set[str] = set()
    for core in cores:
        core_id = str(core.get("id", "")).strip()
        repository = str(core.get("repository", "")).strip()
        probes = core.get("probes")
        if not core_id or core_id in ids:
            raise SystemExit(f"Invalid or duplicate core id: {core_id!r}")
        if "/" not in repository:
            raise SystemExit(f"Invalid repository for {core_id}: {repository!r}")
        if not isinstance(probes, list) or not probes:
            raise SystemExit(f"No source probes configured for {core_id}")
        for probe in probes:
            if not str(probe.get("path", "")).startswith("src/main/java/"):
                raise SystemExit(f"Invalid source probe path for {core_id}: {probe.get('path')!r}")
            tokens = probe.get("tokens")
            if not isinstance(tokens, list) or not all(str(token).strip() for token in tokens):
                raise SystemExit(f"Invalid source probe tokens for {core_id}")
        ids.add(core_id)
        output.append(
            {
                "id": core_id,
                "display_name": str(core.get("display_name", core_id)),
                "repository": repository,
                "ref": str(core.get("ref", "")),
                "advisory": bool(core.get("advisory", True)),
            }
        )

    compact = json.dumps({"include": output}, separators=(",", ":"))
    print(compact)
    if args.github_output:
        with args.github_output.open("a", encoding="utf-8") as handle:
            handle.write(f"matrix={compact}\n")
            handle.write(f"enabled_count={len(output)}\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
