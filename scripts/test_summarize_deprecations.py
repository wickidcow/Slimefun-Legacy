#!/usr/bin/env python3
"""Self-test warning parsing and fail-closed build-evidence handling."""

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
    require_successful_build: bool = False,
) -> subprocess.CompletedProcess[str]:
    command = [sys.executable, str(script), str(log), "--output", str(output)]
    if fail_on_warnings:
        command.append("--fail-on-warnings")
    if require_successful_build:
        command.append("--require-successful-build")
    return subprocess.run(command, check=False, text=True, capture_output=True)


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    script = root / "scripts" / "summarize_deprecations.py"
    require(script.is_file(), "summarize_deprecations.py is missing")
    cases = 0

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
        cases += 1

        gated = run(script, warning_log, warning_report, fail_on_warnings=True)
        require(gated.returncode == 1, "warning gate did not fail when warnings were present")
        require("Compatibility warning gate: FAIL" in gated.stdout, "failure gate output is missing")
        cases += 1

        clean_log = temp / "clean.log"
        clean_report = temp / "clean.md"
        clean_log.write_text("Note: compile completed without compatibility warnings\n", encoding="utf-8")
        clean = run(script, clean_log, clean_report, fail_on_warnings=True)
        require(clean.returncode == 0, f"clean warning gate failed: {clean.stderr}")
        clean_text = clean_report.read_text(encoding="utf-8")
        require("- Deprecation: **0**" in clean_text, "clean deprecation count is wrong")
        require("- Removal: **0**" in clean_text, "clean removal count is wrong")
        require("Compatibility warning gate: PASS" in clean.stdout, "success gate output is missing")
        cases += 1

        # Missing/unreadable/empty logs must fail, even for informational reports.
        empty = temp / "empty.log"
        empty.touch()
        whitespace = temp / "whitespace.log"
        whitespace.write_text(" \n\t\r\n", encoding="utf-8")
        unreadable = temp / "directory.log"
        unreadable.mkdir()
        for invalid_log in (temp / "missing.log", empty, whitespace, unreadable):
            for strict in (False, True):
                clean_report.write_text(clean_text, encoding="utf-8")
                invalid = run(script, invalid_log, clean_report, fail_on_warnings=strict)
                require(invalid.returncode == 2, f"invalid evidence accepted: {invalid_log.name}, strict={strict}")
                invalid_report = clean_report.read_text(encoding="utf-8")
                require("Validation unavailable or unsuccessful" in invalid_report, "invalid evidence was not reported")
                require("Detected **0**" not in invalid_report, "invalid evidence became a zero-warning report")
                require("- Deprecation: **0**" not in invalid_report, "stale clean report survived invalid input")
                require("gate: PASS" not in invalid.stdout, "invalid evidence passed the warning gate")
                cases += 1

        # A partial non-empty log is not proof that compilation completed successfully.
        for content in (
            "> Task :compileJava\n",
            "BUILD FAILED in 2s\n",
            "BUILD SUCCESSFUL in 1s\nBUILD FAILED in 2s\n",
            "Example text mentioning BUILD SUCCESSFUL in the middle of a sentence\n",
        ):
            clean_log.write_text(content, encoding="utf-8")
            partial = run(script, clean_log, clean_report, fail_on_warnings=True, require_successful_build=True)
            require(partial.returncode == 2, "incomplete or failed build accepted as successful")
            require("gate: PASS" not in partial.stdout, "incomplete build passed the warning gate")
            cases += 1

        clean_log.write_text("> Task :compileJava\nBUILD SUCCESSFUL in 2s\n", encoding="utf-8")
        successful = run(script, clean_log, clean_report, fail_on_warnings=True, require_successful_build=True)
        require(successful.returncode == 0, f"successful warning-free build rejected: {successful.stderr}")
        cases += 1

        with warning_log.open("a", encoding="utf-8") as log:
            log.write("BUILD SUCCESSFUL in 2s\n")
        warned = run(script, warning_log, warning_report, fail_on_warnings=True, require_successful_build=True)
        require(warned.returncode == 1, "successful build incorrectly bypassed warning enforcement")
        cases += 1

        # Reproduce the build-clean race using real file operations: a live log
        # descriptor can outlast its directory entry, leaving no readable evidence.
        deleted_log = temp / "deleted.log"
        with deleted_log.open("w", encoding="utf-8") as stream:
            deleted_log.unlink()
            stream.write("BUILD SUCCESSFUL in 1s\n")
        deleted = run(script, deleted_log, clean_report, fail_on_warnings=True, require_successful_build=True)
        require(deleted.returncode == 2, "deleted compiler log was accepted as clean")
        cases += 1

    print(f"Deprecation/removal summarizer self-test: PASS ({cases} cases)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
