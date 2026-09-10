#!/usr/bin/env python3
"""Compile a maintained Slimefun addon against a Paper 26.3 candidate stack.

The probe is advisory and intentionally works with both Maven and Gradle projects.
It installs the exact Slimefun Legacy candidate into Maven Local, redirects normal
Slimefun core coordinates to that candidate, and redirects Bukkit/Spigot/Paper API
coordinates to the detected Paper 26.3 API before running the addon's normal
assemble/package path without tests.

This does not edit the source repository: CI runs it against an ephemeral clone.
"""

from __future__ import annotations

import argparse
import json
import os
import stat
import subprocess
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path
from typing import TextIO

PAPER_REPOSITORY = "https://repo.papermc.io/repository/maven-public/"
SLIMEFUN_PROBE_GROUP = "com.github.slimefun"
SLIMEFUN_PROBE_ARTIFACT = "Slimefun"
SLIMEFUN_PROBE_VERSION = "Paper-26.3-CI"
CORE_ARTIFACTS = {"slimefun", "slimefun4"}
CORE_GROUP_HINTS = ("slimefun", "thebusybiscuit", "starwishsama")
SERVER_API_COORDINATES = {
    ("io.papermc.paper", "paper-api"),
    ("org.spigotmc", "spigot-api"),
    ("org.bukkit", "bukkit"),
    ("org.bukkit", "bukkit-api"),
    ("org.purpurmc.purpur", "purpur-api"),
}

PASS = "PASS"
COMPILE_FAILED = "COMPILE_FAILED"
INSTRUMENTATION_ERROR = "INSTRUMENTATION_ERROR"
EXIT_CODES = {PASS: 0, COMPILE_FAILED: 20, INSTRUMENTATION_ERROR: 30}


@dataclass(frozen=True)
class BuildResult:
    status: str
    build_system: str
    command: list[str]
    exit_code: int
    core_dependencies_rewritten: int
    server_api_dependencies_rewritten: int
    core_dependency_injected: bool
    paper_dependency_injected: bool
    log_file: str


def local_name(tag: str) -> str:
    return tag.split("}")[-1]


def namespaced(namespace: str, name: str) -> str:
    return f"{{{namespace}}}{name}" if namespace else name


def is_core_slimefun_dependency(group: str, artifact: str) -> bool:
    normalized_group = group.strip().lower()
    normalized_artifact = artifact.strip().lower()
    return normalized_artifact in CORE_ARTIFACTS and any(
        hint in normalized_group for hint in CORE_GROUP_HINTS
    )


def is_server_api_dependency(group: str, artifact: str) -> bool:
    return (group.strip().lower(), artifact.strip().lower()) in SERVER_API_COORDINATES


def detect_build_system(project: Path) -> str:
    if (project / "pom.xml").is_file():
        return "maven"
    if (project / "build.gradle").is_file() or (project / "build.gradle.kts").is_file():
        return "gradle"
    raise RuntimeError("Unsupported addon build system: expected pom.xml or a Gradle build file")


def make_executable(path: Path) -> None:
    path.chmod(path.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)


def stream_command(command: list[str], *, cwd: Path, env: dict[str, str], log: TextIO) -> int:
    header = f"$ {' '.join(command)}\nWorking directory: {cwd}\n\n"
    print(header, end="")
    log.write(header)
    log.flush()

    process = subprocess.Popen(
        command,
        cwd=cwd,
        env=env,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        errors="replace",
        bufsize=1,
    )
    assert process.stdout is not None
    for line in process.stdout:
        print(line, end="")
        log.write(line)

    exit_code = process.wait()
    footer = f"\nProcess exit code: {exit_code}\n"
    print(footer, end="")
    log.write(footer)
    log.flush()
    return exit_code


def maven_properties(root: ET.Element) -> dict[str, str]:
    properties: dict[str, str] = {}
    for child in root:
        if local_name(child.tag) != "properties":
            continue
        for prop in child:
            properties[local_name(prop.tag)] = (prop.text or "").strip()
    return properties


def resolve_maven_property(value: str, properties: dict[str, str]) -> str:
    resolved = value.strip()
    seen: set[str] = set()
    for _ in range(10):
        if not (resolved.startswith("${") and resolved.endswith("}")):
            return resolved
        key = resolved[2:-1]
        if key in seen or key not in properties:
            return resolved
        seen.add(key)
        resolved = properties[key].strip()
    return resolved


def dependency_children(dependency: ET.Element) -> dict[str, ET.Element]:
    return {local_name(child.tag): child for child in dependency}


def set_dependency_coordinate(
    dependency: ET.Element,
    namespace: str,
    *,
    group: str,
    artifact: str,
    version: str,
) -> None:
    children = dependency_children(dependency)
    for name, value in (("groupId", group), ("artifactId", artifact), ("version", version)):
        node = children.get(name)
        if node is None:
            node = ET.SubElement(dependency, namespaced(namespace, name))
        node.text = value

    scope = children.get("scope")
    if scope is None:
        scope = ET.SubElement(dependency, namespaced(namespace, "scope"))
    scope.text = "provided"

    system_path = children.get("systemPath")
    if system_path is not None:
        dependency.remove(system_path)


def direct_dependencies(root: ET.Element, namespace: str) -> ET.Element:
    for child in root:
        if local_name(child.tag) == "dependencies":
            return child
    return ET.SubElement(root, namespaced(namespace, "dependencies"))


def ensure_paper_repository(root: ET.Element, namespace: str) -> None:
    repositories = None
    for child in root:
        if local_name(child.tag) == "repositories":
            repositories = child
            break
    if repositories is None:
        repositories = ET.SubElement(root, namespaced(namespace, "repositories"))

    for repository in repositories:
        if local_name(repository.tag) != "repository":
            continue
        for node in repository:
            if local_name(node.tag) == "url" and (node.text or "").rstrip("/") == PAPER_REPOSITORY.rstrip("/"):
                return

    repository = ET.SubElement(repositories, namespaced(namespace, "repository"))
    repository_id = ET.SubElement(repository, namespaced(namespace, "id"))
    repository_id.text = "papermc-paper-26-3-probe"
    url = ET.SubElement(repository, namespaced(namespace, "url"))
    url.text = PAPER_REPOSITORY


def append_direct_dependency(
    dependencies: ET.Element,
    namespace: str,
    *,
    group: str,
    artifact: str,
    version: str,
) -> None:
    dependency = ET.SubElement(dependencies, namespaced(namespace, "dependency"))
    set_dependency_coordinate(
        dependency,
        namespace,
        group=group,
        artifact=artifact,
        version=version,
    )


def patch_maven_project(project: Path, paper_api_version: str) -> tuple[int, int, bool, bool]:
    pom = project / "pom.xml"
    tree = ET.parse(pom)
    root = tree.getroot()
    namespace = root.tag.partition("}")[0].strip("{") if "}" in root.tag else ""
    if namespace:
        ET.register_namespace("", namespace)

    properties = maven_properties(root)
    core_rewritten = 0
    paper_rewritten = 0
    direct_core_found = False
    direct_paper_found = False
    root_dependencies = direct_dependencies(root, namespace)
    root_dependency_ids = {id(node) for node in root_dependencies if local_name(node.tag) == "dependency"}

    for dependency in root.iter():
        if local_name(dependency.tag) != "dependency":
            continue
        children = dependency_children(dependency)
        group_node = children.get("groupId")
        artifact_node = children.get("artifactId")
        if group_node is None or artifact_node is None:
            continue

        group = resolve_maven_property(group_node.text or "", properties)
        artifact = resolve_maven_property(artifact_node.text or "", properties)
        is_direct = id(dependency) in root_dependency_ids

        if is_core_slimefun_dependency(group, artifact):
            set_dependency_coordinate(
                dependency,
                namespace,
                group=SLIMEFUN_PROBE_GROUP,
                artifact=SLIMEFUN_PROBE_ARTIFACT,
                version=SLIMEFUN_PROBE_VERSION,
            )
            core_rewritten += 1
            direct_core_found = direct_core_found or is_direct
        elif is_server_api_dependency(group, artifact):
            set_dependency_coordinate(
                dependency,
                namespace,
                group="io.papermc.paper",
                artifact="paper-api",
                version=paper_api_version,
            )
            paper_rewritten += 1
            direct_paper_found = direct_paper_found or is_direct

    core_injected = not direct_core_found
    if core_injected:
        append_direct_dependency(
            root_dependencies,
            namespace,
            group=SLIMEFUN_PROBE_GROUP,
            artifact=SLIMEFUN_PROBE_ARTIFACT,
            version=SLIMEFUN_PROBE_VERSION,
        )

    paper_injected = not direct_paper_found
    if paper_injected:
        append_direct_dependency(
            root_dependencies,
            namespace,
            group="io.papermc.paper",
            artifact="paper-api",
            version=paper_api_version,
        )

    ensure_paper_repository(root, namespace)
    tree.write(pom, encoding="utf-8", xml_declaration=True)
    return core_rewritten, paper_rewritten, core_injected, paper_injected


def write_gradle_init_script(project: Path) -> Path:
    script = project / ".slimefun-paper-26.3.init.gradle"
    script.write_text(
        r'''
def probePaperVersion = System.getenv('PAPER_API_VERSION')
def probeSlimefunVersion = 'Paper-26.3-CI'

def isCoreSlimefunDependency(groupValue, artifactValue) {
    def group = (groupValue ?: '').toLowerCase()
    def artifact = (artifactValue ?: '').toLowerCase()
    def coreArtifact = artifact == 'slimefun' || artifact == 'slimefun4'
    def coreGroup = group.contains('slimefun') || group.contains('thebusybiscuit') || group.contains('starwishsama')
    return coreArtifact && coreGroup
}

def isServerApiDependency(groupValue, artifactValue) {
    def group = (groupValue ?: '').toLowerCase()
    def artifact = (artifactValue ?: '').toLowerCase()
    return (group == 'io.papermc.paper' && artifact == 'paper-api') ||
           (group == 'org.spigotmc' && artifact == 'spigot-api') ||
           (group == 'org.bukkit' && (artifact == 'bukkit' || artifact == 'bukkit-api')) ||
           (group == 'org.purpurmc.purpur' && artifact == 'purpur-api')
}

allprojects { p ->
    p.repositories {
        mavenLocal()
        maven { url = uri('https://repo.papermc.io/repository/maven-public/') }
    }

    p.configurations.configureEach { configuration ->
        configuration.resolutionStrategy.eachDependency { details ->
            if (isCoreSlimefunDependency(details.requested.group, details.requested.name)) {
                details.useTarget("com.github.slimefun:Slimefun:${probeSlimefunVersion}")
                details.because('Paper 26.3 preflight must compile against the exact Slimefun Legacy candidate')
            } else if (isServerApiDependency(details.requested.group, details.requested.name)) {
                details.useTarget("io.papermc.paper:paper-api:${probePaperVersion}")
                details.because('Paper 26.3 preflight must not resolve an older Bukkit/Spigot/Paper API')
            }
        }
    }

    p.afterEvaluate {
        def compileOnly = p.configurations.findByName('compileOnly')
        if (compileOnly != null) {
            p.dependencies.add(compileOnly.name, "io.papermc.paper:paper-api:${probePaperVersion}")
            p.dependencies.add(compileOnly.name, "com.github.slimefun:Slimefun:${probeSlimefunVersion}")
        }
    }
}

gradle.projectsEvaluated {
    allprojects { p ->
        p.tasks.withType(org.gradle.api.tasks.compile.JavaCompile).configureEach { task ->
            task.classpath = p.files(System.getenv('SLIMEFUN_COMPATIBILITY_JAR')) + task.classpath
        }
    }
}
'''.strip()
        + "\n",
        encoding="utf-8",
    )
    return script


def install_slimefun_candidate(project: Path, jar: Path, env: dict[str, str], log: TextIO) -> int:
    return stream_command(
        [
            "mvn",
            "-B",
            "install:install-file",
            f"-Dfile={jar}",
            f"-DgroupId={SLIMEFUN_PROBE_GROUP}",
            f"-DartifactId={SLIMEFUN_PROBE_ARTIFACT}",
            f"-Dversion={SLIMEFUN_PROBE_VERSION}",
            "-Dpackaging=jar",
            "-DgeneratePom=true",
        ],
        cwd=project,
        env=env,
        log=log,
    )


def build_project(project: Path, slimefun_jar: Path, paper_api_version: str, report_dir: Path) -> BuildResult:
    build_system = detect_build_system(project)
    report_dir.mkdir(parents=True, exist_ok=True)
    log_path = report_dir / "compile.log"
    env = dict(os.environ)
    env["PAPER_API_VERSION"] = paper_api_version
    env["SLIMEFUN_COMPATIBILITY_JAR"] = str(slimefun_jar)
    env["SLIMEFUN_CORE_JAR"] = str(slimefun_jar)
    env["SLIMEFUN_LEGACY_JAR"] = str(slimefun_jar)

    core_rewritten = 0
    paper_rewritten = 0
    core_injected = False
    paper_injected = False
    command: list[str] = []

    with log_path.open("w", encoding="utf-8") as log:
        log.write(f"Paper API candidate: {paper_api_version}\n")
        log.write(f"Slimefun candidate: {slimefun_jar}\n")
        log.write(f"Build system: {build_system}\n\n")

        install_code = install_slimefun_candidate(project, slimefun_jar, env, log)
        if install_code != 0:
            return BuildResult(
                INSTRUMENTATION_ERROR,
                build_system,
                ["mvn", "install:install-file"],
                install_code,
                0,
                0,
                False,
                False,
                log_path.name,
            )

        if build_system == "maven":
            core_rewritten, paper_rewritten, core_injected, paper_injected = patch_maven_project(
                project, paper_api_version
            )
            wrapper = project / "mvnw"
            if wrapper.is_file():
                make_executable(wrapper)
                command = [str(wrapper), "-B", "-DskipTests", "package"]
            else:
                command = ["mvn", "-B", "-DskipTests", "package"]
        else:
            init_script = write_gradle_init_script(project)
            wrapper = project / "gradlew"
            if wrapper.is_file():
                make_executable(wrapper)
                command = [
                    str(wrapper),
                    "clean",
                    "assemble",
                    "--no-daemon",
                    "--no-build-cache",
                    "-I",
                    str(init_script),
                ]
            else:
                command = [
                    "gradle",
                    "clean",
                    "assemble",
                    "--no-daemon",
                    "--no-build-cache",
                    "-I",
                    str(init_script),
                ]

        exit_code = stream_command(command, cwd=project, env=env, log=log)
        status = PASS if exit_code == 0 else COMPILE_FAILED
        return BuildResult(
            status,
            build_system,
            command,
            exit_code,
            core_rewritten,
            paper_rewritten,
            core_injected,
            paper_injected,
            log_path.name,
        )


def source_commit(project: Path) -> str | None:
    result = subprocess.run(
        ["git", "rev-parse", "HEAD"],
        cwd=project,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        check=False,
    )
    return result.stdout.strip() if result.returncode == 0 and result.stdout.strip() else None


def write_reports(
    report_dir: Path,
    project: Path,
    slimefun_jar: Path,
    paper_api_version: str,
    result: BuildResult | None,
    error: str | None,
) -> str:
    status = result.status if result is not None else INSTRUMENTATION_ERROR
    payload = {
        "schema": 1,
        "status": status,
        "source": str(project),
        "source_commit": source_commit(project),
        "paper_api_version": paper_api_version,
        "slimefun_candidate": slimefun_jar.name,
        "build_system": result.build_system if result else None,
        "command": result.command if result else None,
        "exit_code": result.exit_code if result else None,
        "core_dependencies_rewritten": result.core_dependencies_rewritten if result else 0,
        "server_api_dependencies_rewritten": result.server_api_dependencies_rewritten if result else 0,
        "core_dependency_injected": result.core_dependency_injected if result else False,
        "paper_dependency_injected": result.paper_dependency_injected if result else False,
        "error": error,
    }
    report_dir.mkdir(parents=True, exist_ok=True)
    (report_dir / "result.json").write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    (report_dir / "status.txt").write_text(status + "\n", encoding="utf-8")

    if status == PASS:
        explanation = "Addon source compiled successfully against the detected Paper 26.3 API and exact Slimefun Legacy candidate."
    elif status == COMPILE_FAILED:
        explanation = "Addon compilation failed under the Paper 26.3 candidate stack. Review compile.log before changing the production baseline."
    else:
        explanation = "The probe could not establish a valid Maven/Gradle candidate build. This is instrumentation, not confirmed addon incompatibility."

    lines = [
        "## Paper 26.3 addon compile probe",
        "",
        f"**Result:** `{status}`",
        "",
        explanation,
        "",
        f"- Paper API: `{paper_api_version}`",
        f"- Slimefun candidate: `{slimefun_jar.name}`",
    ]
    if result is not None:
        lines.extend(
            [
                f"- Build system: `{result.build_system}`",
                f"- Core dependency rewrites: `{result.core_dependencies_rewritten}`",
                f"- Server API dependency rewrites: `{result.server_api_dependencies_rewritten}`",
                f"- Injected direct Slimefun probe dependency: `{str(result.core_dependency_injected).lower()}`",
                f"- Injected direct Paper probe dependency: `{str(result.paper_dependency_injected).lower()}`",
            ]
        )
    if error:
        lines.extend(["", f"Error: `{error}`"])
    (report_dir / "summary.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    return status


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("project")
    parser.add_argument("slimefun_jar")
    parser.add_argument("paper_api_version")
    parser.add_argument("--report-dir", required=True)
    args = parser.parse_args()

    project = Path(args.project).resolve()
    slimefun_jar = Path(args.slimefun_jar).resolve()
    report_dir = Path(args.report_dir).resolve()

    if not project.is_dir():
        raise SystemExit(f"Paper 26.3 addon compile probe failed: not a directory: {project}")
    if not slimefun_jar.is_file():
        raise SystemExit(f"Paper 26.3 addon compile probe failed: Slimefun JAR not found: {slimefun_jar}")

    result: BuildResult | None = None
    error: str | None = None
    try:
        result = build_project(project, slimefun_jar, args.paper_api_version, report_dir)
    except (OSError, RuntimeError, ET.ParseError) as exc:
        error = f"{type(exc).__name__}: {exc}"
        print(error, file=sys.stderr)

    status = write_reports(report_dir, project, slimefun_jar, args.paper_api_version, result, error)
    return EXIT_CODES[status]


if __name__ == "__main__":
    raise SystemExit(main())
