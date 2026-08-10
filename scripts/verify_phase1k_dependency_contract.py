#!/usr/bin/env python3
"""Verify Slimefun Legacy Phase 1K Part 3 dependency and packaging boundaries."""
from __future__ import annotations

import re
import sys
from pathlib import Path


OPTIONAL_PLUGIN_APIS = {
    "WorldEdit": ("libs.worldedit.core", "libs.worldedit.bukkit"),
    "mcMMO": ("libs.mcmmo",),
    "PlaceholderAPI": ("libs.placeholderapi",),
    "ClearLag": ("libs.clearlag.core",),
    "ItemsAdder": ("libs.itemsadder.api",),
    "Orebfuscator": ("libs.orebfuscator.api",),
    "Vault": ("libs.vault.api",),
}

EXPECTED_SOFT_DEPEND = tuple(OPTIONAL_PLUGIN_APIS)
EXPECTED_LOAD_BEFORE = ("ChestTerminal", "SlimeGlue")

PRIVATE_RELOCATIONS = (
    ('relocate("io.github.bakedlibs.dough", "io.github.thebusybiscuit.slimefun4.libraries.dough")', "Dough"),
    ('relocate("io.papermc.lib", "io.github.thebusybiscuit.slimefun4.libraries.paperlib")', "PaperLib"),
    ('relocate("kong.unirest", "io.github.thebusybiscuit.slimefun4.libraries.unirest")', "Unirest"),
    ('relocate("org.apache.commons.lang", "io.github.thebusybiscuit.slimefun4.libraries.commons.lang")', "Commons Lang"),
    ('relocate("net.guizhanss.guizhanlib", "io.github.thebusybiscuit.slimefun4.libraries.guizhanlib")', "GuizhanLib"),
    ('relocate("org.bstats", "io.github.thebusybiscuit.slimefun4.libraries.bstats")', "bStats"),
)


def read(root: Path, relative: str) -> str:
    path = root / relative
    if not path.is_file():
        raise FileNotFoundError(relative)
    return path.read_text(encoding="utf-8")


def top_level_list(yaml_text: str, key: str) -> list[str]:
    lines = yaml_text.splitlines()
    marker = f"{key}:"
    try:
        start = next(i for i, line in enumerate(lines) if line.strip() == marker and not line.startswith((" ", "\t")))
    except StopIteration:
        return []

    values: list[str] = []
    for line in lines[start + 1 :]:
        if line and not line.startswith((" ", "\t")):
            break
        match = re.match(r"^\s+-\s+([^#]+?)\s*$", line)
        if match:
            values.append(match.group(1).strip().strip("'\""))
    return values


def require(condition: bool, message: str, failures: list[str]) -> None:
    if not condition:
        failures.append(message)


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    failures: list[str] = []

    try:
        plugin_yml = read(root, "src/main/resources/plugin.yml")
        build_gradle = read(root, "build.gradle.kts")
    except FileNotFoundError as error:
        print(f"Phase 1K dependency contract verification failed: missing {error}")
        return 1

    require(not re.search(r"(?m)^depend\s*:", plugin_yml), "plugin.yml must not add hard plugin dependencies", failures)
    require(not re.search(r"(?m)^provides\s*:", plugin_yml), "plugin.yml must not provide/impersonate third-party plugins", failures)
    require("GuizhanLibPlugin" not in plugin_yml, "plugin.yml must not declare or provide GuizhanLibPlugin", failures)

    softdepend = tuple(top_level_list(plugin_yml, "softdepend"))
    load_before = tuple(top_level_list(plugin_yml, "loadBefore"))
    require(softdepend == EXPECTED_SOFT_DEPEND, f"softdepend changed: expected {EXPECTED_SOFT_DEPEND}, got {softdepend}", failures)
    require(load_before == EXPECTED_LOAD_BEFORE, f"loadBefore changed: expected {EXPECTED_LOAD_BEFORE}, got {load_before}", failures)

    for plugin_name, aliases in OPTIONAL_PLUGIN_APIS.items():
        for alias in aliases:
            compile_only = f"compileOnly({alias})"
            require(compile_only in build_gradle, f"{plugin_name} API must remain compileOnly: {alias}", failures)
            require(
                f"compileOnly({alias}) {{ exclude(group = \"*\", module = \"*\") }}" in build_gradle,
                f"{plugin_name} compileOnly API must exclude transitive dependencies: {alias}",
                failures,
            )
            require(f"implementation({alias})" not in build_gradle, f"{plugin_name} API must not become an implementation dependency", failures)
            require(f"api({alias})" not in build_gradle, f"{plugin_name} API must not become a published API dependency", failures)

    require(
        'compileOnly(libs.authlib) { exclude(group = "*", module = "*") }' in build_gradle,
        "Authlib must remain compileOnly and transitive-free",
        failures,
    )
    require("implementation(libs.authlib)" not in build_gradle, "Authlib must not be bundled", failures)

    for token, label in PRIVATE_RELOCATIONS:
        require(token in build_gradle, f"Private relocation missing for {label}", failures)

    require("implementation(libs.guizhanlib.updater)" in build_gradle, "Internal GuizhanLib updater dependency missing", failures)
    require("implementation(libs.guizhanlib.minecraft)" in build_gradle, "Internal GuizhanLib Minecraft dependency missing", failures)
    require(
        'relocate("net.guizhanss.guizhanlib", "io.github.thebusybiscuit.slimefun4.libraries.guizhanlib")' in build_gradle,
        "Internal GuizhanLib must remain relocated",
        failures,
    )

    report = root / "build/reports/phase1k-dependency-contract.txt"
    report.parent.mkdir(parents=True, exist_ok=True)

    if failures:
        report.write_text(
            "Phase 1K Part 3 dependency contract: FAIL\n" + "\n".join(f"- {failure}" for failure in failures) + "\n",
            encoding="utf-8",
        )
        print(report.read_text(encoding="utf-8"), end="")
        return 1

    report.write_text(
        "Phase 1K Part 3 dependency contract: PASS\n"
        "- Slimefun declares no hard external plugin dependency\n"
        "- Slimefun does not provide or impersonate third-party plugin identities\n"
        "- optional plugin APIs remain compileOnly with transitive dependencies excluded\n"
        "- optional plugin APIs are not bundled or published as implementation dependencies\n"
        "- private bundled libraries remain relocated into Slimefun's namespace\n"
        "- GuizhanLib remains an internal relocated library, not a GuizhanLibPlugin replacement\n",
        encoding="utf-8",
    )
    print(report.read_text(encoding="utf-8"), end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
