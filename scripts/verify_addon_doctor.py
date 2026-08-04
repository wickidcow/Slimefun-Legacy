#!/usr/bin/env python3
"""Verify the Slimefun Legacy addon-doctor API and command bridge."""

from __future__ import annotations

from pathlib import Path
import sys

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else Path(__file__).resolve().parents[1]).resolve()
ERRORS: list[str] = []


def require(condition: bool, message: str) -> None:
    if not condition:
        ERRORS.append(message)


def read(relative: str) -> str:
    path = ROOT / relative
    require(path.is_file(), f"missing required file: {relative}")
    return path.read_text(encoding="utf-8") if path.is_file() else ""


api = read("src/main/java/io/github/thebusybiscuit/slimefun4/api/diagnostics/AddonDoctor.java")
report = read("src/main/java/io/github/thebusybiscuit/slimefun4/api/diagnostics/AddonDoctorReport.java")
service = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/services/stability/AddonDoctorService.java")
command = read("src/main/java/io/github/thebusybiscuit/slimefun4/core/commands/subcommands/DoctorCommand.java")
docs = read("docs/ADDON_DOCTOR_API.md")
test = read("src/test/java/io/github/thebusybiscuit/slimefun4/api/diagnostics/TestAddonDoctorReport.java")

require("@SlimefunAPI" in api, "AddonDoctor must be classified as supported API")
require("@SlimefunAPI" in report, "AddonDoctorReport must be classified as supported API")
require("AddonDoctorReport runDoctor(boolean repair)" in api, "doctor scan/repair contract changed")
require("Collections.unmodifiableList" in report, "doctor report details must be immutable")
require("Objects.requireNonNull" in report, "doctor report null validation is missing")
require("addonName cannot be blank" in report, "doctor report blank-name validation is missing")
require("getRegistrations(AddonDoctor.class)" in service, "ServicesManager provider discovery is missing")
require("catch (Throwable throwable)" in service, "provider failure isolation is missing")
require("getProviderName" in service, "safe third-party provider-name handling is missing")
require('case "addons" -> runAddonDoctors(sender, args);' in command, "doctor addons command is missing")
require("repair confirm" in command, "addon repair confirmation gate is missing")
require("MAX_ADDON_DETAIL_LINES" in command, "addon detail output limit is missing")
require("must not force-load chunks" in docs, "addon-doctor safety contract is not documented")
require("testRejectsNegativeCounters" in test, "negative-count regression test is missing")
require("testRejectsInvalidNamesAndDetails" in test, "null/blank report regression test is missing")

if ERRORS:
    print("Addon Doctor verification failed:")
    for error in ERRORS:
        print(" -", error)
    raise SystemExit(1)

print("Addon Doctor API verification passed.")
