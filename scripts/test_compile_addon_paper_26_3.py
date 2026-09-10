#!/usr/bin/env python3
"""Self-tests for the centralized Paper 26.3 addon compile probe."""

from __future__ import annotations

import importlib.util
import sys
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path

SCRIPT = Path(__file__).with_name("compile_addon_paper_26_3.py")
SPEC = importlib.util.spec_from_file_location("paper_probe", SCRIPT)
if SPEC is None or SPEC.loader is None:
    raise SystemExit("Could not load compile_addon_paper_26_3.py")
probe = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = probe
SPEC.loader.exec_module(probe)


def local_name(tag: str) -> str:
    return tag.split("}")[-1]


def dependencies(pom: Path) -> list[tuple[str, str, str]]:
    root = ET.parse(pom).getroot()
    rows = []
    for dependency in root.iter():
        if local_name(dependency.tag) != "dependency":
            continue
        values = {local_name(child.tag): (child.text or "").strip() for child in dependency}
        rows.append((values.get("groupId", ""), values.get("artifactId", ""), values.get("version", "")))
    return rows


def test_coordinate_classification() -> None:
    assert probe.is_core_slimefun_dependency("com.github.slimefun", "Slimefun")
    assert probe.is_core_slimefun_dependency("com.github.SlimefunGuguProject", "Slimefun4")
    assert probe.is_core_slimefun_dependency("com.github.StarWishsama", "Slimefun4")
    assert not probe.is_core_slimefun_dependency("net.guizhanss", "SlimefunTranslation")
    assert probe.is_server_api_dependency("io.papermc.paper", "paper-api")
    assert probe.is_server_api_dependency("org.spigotmc", "spigot-api")
    assert probe.is_server_api_dependency("org.bukkit", "bukkit")
    assert not probe.is_server_api_dependency("com.sk89q.worldedit", "worldedit-bukkit")


def test_maven_property_rewrite() -> None:
    with tempfile.TemporaryDirectory() as raw:
        root = Path(raw)
        pom = root / "pom.xml"
        pom.write_text(
            """<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<project xmlns=\"http://maven.apache.org/POM/4.0.0\">
  <modelVersion>4.0.0</modelVersion>
  <groupId>example</groupId><artifactId>addon</artifactId><version>1</version>
  <properties>
    <paper.version>1.21.11-R0.1-SNAPSHOT</paper.version>
    <sf.group>com.github.SlimefunGuguProject</sf.group>
    <sf.artifact>Slimefun4</sf.artifact>
    <sf.version>old</sf.version>
  </properties>
  <dependencies>
    <dependency><groupId>io.papermc.paper</groupId><artifactId>paper-api</artifactId><version>${paper.version}</version><scope>provided</scope></dependency>
    <dependency><groupId>${sf.group}</groupId><artifactId>${sf.artifact}</artifactId><version>${sf.version}</version><scope>provided</scope></dependency>
  </dependencies>
</project>
""",
            encoding="utf-8",
        )

        core, paper, core_injected, paper_injected = probe.patch_maven_project(root, "26.3.build.7")
        assert core == 1
        assert paper == 1
        assert not core_injected
        assert not paper_injected
        rows = dependencies(pom)
        assert ("com.github.slimefun", "Slimefun", "Paper-26.3-CI") in rows
        assert ("io.papermc.paper", "paper-api", "26.3.build.7") in rows
        text = pom.read_text(encoding="utf-8")
        assert probe.PAPER_REPOSITORY in text


def test_maven_injects_missing_direct_dependencies() -> None:
    with tempfile.TemporaryDirectory() as raw:
        root = Path(raw)
        pom = root / "pom.xml"
        pom.write_text(
            """<project xmlns=\"http://maven.apache.org/POM/4.0.0\">
  <modelVersion>4.0.0</modelVersion>
  <groupId>example</groupId><artifactId>addon</artifactId><version>1</version>
  <dependencies>
    <dependency><groupId>org.bstats</groupId><artifactId>bstats-bukkit</artifactId><version>3.0.0</version></dependency>
  </dependencies>
</project>
""",
            encoding="utf-8",
        )

        core, paper, core_injected, paper_injected = probe.patch_maven_project(root, "26.3.build.8")
        assert core == 0
        assert paper == 0
        assert core_injected
        assert paper_injected
        rows = dependencies(pom)
        assert ("com.github.slimefun", "Slimefun", "Paper-26.3-CI") in rows
        assert ("io.papermc.paper", "paper-api", "26.3.build.8") in rows


def test_gradle_init_script_guards_both_stacks() -> None:
    with tempfile.TemporaryDirectory() as raw:
        root = Path(raw)
        script = probe.write_gradle_init_script(root)
        text = script.read_text(encoding="utf-8")
        assert "details.useTarget(\"com.github.slimefun:Slimefun:${probeSlimefunVersion}\")" in text
        assert "details.useTarget(\"io.papermc.paper:paper-api:${probePaperVersion}\")" in text
        assert "org.spigotmc" in text
        assert "org.bukkit" in text
        assert "org.purpurmc.purpur" in text
        assert "SLIMEFUN_COMPATIBILITY_JAR" in text
        assert "TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE" in text
        assert "probeRuntimeJvm = 25" in text
        assert "options.release" not in text
        assert "targetCompatibility" not in text
        assert "jvmTarget" not in text
        assert probe.PAPER_REPOSITORY in text


def main() -> int:
    test_coordinate_classification()
    test_maven_property_rewrite()
    test_maven_injects_missing_direct_dependencies()
    test_gradle_init_script_guards_both_stacks()
    print("Paper 26.3 addon compile probe self-tests passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
