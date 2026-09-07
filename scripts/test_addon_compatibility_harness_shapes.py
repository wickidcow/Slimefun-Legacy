#!/usr/bin/env python3
"""Regression tests for historical/non-JVM addon compatibility harness shapes."""

from __future__ import annotations

import json
import os
import sys
import tempfile
from pathlib import Path

import compare_addon_slimefun_compatibility as wrapper
import compare_addon_slimefun_compatibility_base as base
import summarize_addon_compatibility_artifacts as aggregate


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def test_starwishsama_coordinate() -> None:
    require(
        base.is_core_slimefun_dependency("com.github.StarWishsama", "Slimefun4"),
        "StarWishsama Slimefun4 coordinate must be recognized as the core dependency",
    )
    require(
        not base.is_core_slimefun_dependency("com.github.StarWishsama", "ExtraUtils"),
        "StarWishsama group must not make non-core artifacts look like Slimefun",
    )


def test_crlf_wrapper_normalization(root: Path) -> None:
    wrapper_file = root / "gradlew"
    wrapper_file.write_bytes(b"#!/bin/sh\r\necho ok\r\n")
    wrapper.normalize_wrapper_and_make_executable(wrapper_file)
    data = wrapper_file.read_bytes()
    require(b"\r\n" not in data, "CRLF wrapper must be normalized to LF")
    require(os.access(wrapper_file, os.X_OK), "normalized wrapper must be executable")


def make_content_fixture(root: Path, exit_code: int) -> None:
    (root / "info.yml").write_text("id: Magic\nname: Magic\n", encoding="utf-8")
    commands = []
    for index, original in enumerate(wrapper.MAGIC_RSC_AUDIT):
        relative = Path(original[0])
        target = root / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(
            "import sys\nprint('content check')\nsys.exit(" + str(exit_code if index == 0 else 0) + ")\n",
            encoding="utf-8",
        )
        commands.append(relative)
    require(commands, "content fixture must contain at least one audit command")


def test_content_audit_success(root: Path) -> None:
    source = root / "content-pass"
    source.mkdir()
    make_content_fixture(source, 0)
    report = root / "content-pass-report"

    require(wrapper.is_magic_rsc_content_pack(source), "Magic RSC content fixture must be recognized")
    code = wrapper.run_magic_rsc_content_audit(source, report)
    require(code == base.EXIT_CODES[base.PASS], "passing content audit must return PASS")
    require((report / "status.txt").read_text(encoding="utf-8").strip() == base.PASS, "content status must be PASS")

    payload = json.loads((report / "result.json").read_text(encoding="utf-8"))
    require(payload.get("validation_mode") == "content", "content result must identify validation mode")
    require(payload.get("binary_linkage") is None, "content result must not claim binary linkage")
    summary = (report / "summary.md").read_text(encoding="utf-8")
    require("no JVM bytecode/linkage applies" in summary, "content summary must explain JVM linkage is not applicable")

    artifact = root / "addon-compatibility-content"
    artifact.mkdir()
    for filename in ("status.txt", "result.json", "summary.md"):
        (artifact / filename).write_bytes((report / filename).read_bytes())
    status, detail = aggregate.find_status(artifact)
    require(status == base.PASS, "aggregate must preserve content PASS")
    require(detail is not None and "content-only audit" in detail, "aggregate must label content-only validation")


def test_content_audit_failure(root: Path) -> None:
    source = root / "content-fail"
    source.mkdir()
    make_content_fixture(source, 7)
    report = root / "content-fail-report"

    code = wrapper.run_magic_rsc_content_audit(source, report)
    require(
        code == base.EXIT_CODES[base.BASELINE_BUILD_FAILED],
        "content audit failure must be a non-candidate baseline/content failure",
    )
    require(
        (report / "status.txt").read_text(encoding="utf-8").strip() == base.BASELINE_BUILD_FAILED,
        "failed content audit must not be classified as instrumentation or candidate regression",
    )


def test_content_detection_rejects_jvm_project(root: Path) -> None:
    source = root / "jvm-content-lookalike"
    source.mkdir()
    make_content_fixture(source, 0)
    (source / "build.gradle").write_text("plugins { id 'java' }\n", encoding="utf-8")
    require(
        not wrapper.is_magic_rsc_content_pack(source),
        "JVM project must never be diverted into content-only validation",
    )


def main() -> int:
    try:
        test_starwishsama_coordinate()
        with tempfile.TemporaryDirectory(prefix="sf-addon-harness-") as tmp:
            root = Path(tmp)
            test_crlf_wrapper_normalization(root)
            test_content_audit_success(root)
            test_content_audit_failure(root)
            test_content_detection_rejects_jvm_project(root)
    except Exception as error:
        print(f"Addon compatibility harness shape regression test failed: {error}", file=sys.stderr)
        return 1

    print("Addon compatibility harness shape regression tests passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
