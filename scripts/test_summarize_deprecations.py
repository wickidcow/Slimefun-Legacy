#!/usr/bin/env python3
"""Self-test the deprecation/removal warning summarizer and zero-warning gate."""

from __future__ import annotations

import subprocess
import sys
import tempfile
from pathlib import Path


def run(
    script: Path,
    log: Path,
    output: Path,
    *,
    fail_on_warnings: bool,
) -> subprocess.CompletedProcess[str]:
    command = [sys.executable, str(script), str(log), "--output", str(output)]
    if fail_on_warnings:
        command.append("--fail-on-warnings")
    return subprocess.run(command, check=False, text=True, capture_output=True)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    script = root / "scripts" / "summarize_deprecations.py"
    require(script.is_file(), "summarize_deprecations.py is missing")

    with tempfile.TemporaryDirectory() as tmp:
        temp = Path(tmp)
        warning_log = temp / "warnings.log"
        warning_report = temp / "warnings.md"
        warning_log.write_text(
            "\n".join(
                (
                    "/tmp/Foo.java:12: warning: [deprecation] oldMethod() has been deprecated",
                    "/tmp/Bar.java:34: warning: [removal] oldType has been deprecated and marked for removal",
                    "Note: unrelated compiler note",
                )
            )
            + "\n",
            encoding="utf-8",
        )

        result = run(script, warning_log, warning_report, fail_on_warnings=False)
        require(result.returncode == 0, f"informational report failed: {result.stderr}")
        report = warning_report.read_text(encoding="utf-8")
        require("Detected **2** explicit javac compatibility warning(s)." in report, "warning total is wrong")
        require("- Deprecation: **1**" in report, "deprecation count is wrong")
        require("- Removal: **1**" in report, "removal count is wrong")
        require("**deprecation**" in report and "**removal**" in report, "warning categories are missing")

        gated = run(script, warning_log, warning_report, fail_on_warnings=True)
        require(gated.returncode == 1, "warning gate did not fail when warnings were present")
        require("Compatibility warning gate: FAIL" in gated.stdout, "failure gate output is missing")

        clean_log = temp / "clean.log"
        clean_report = temp / "clean.md"
        clean_log.write_text("Note: compile completed without compatibility warnings\n", encoding="utf-8")
        clean = run(script, clean_log, clean_report, fail_on_warnings=True)
        require(clean.returncode == 0, f"clean warning gate failed: {clean.stderr}")
        clean_text = clean_report.read_text(encoding="utf-8")
        require("- Deprecation: **0**" in clean_text, "clean deprecation count is wrong")
        require("- Removal: **0**" in clean_text, "clean removal count is wrong")
        require("Compatibility warning gate: PASS" in clean.stdout, "success gate output is missing")

    print("Deprecation/removal summarizer self-test: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
