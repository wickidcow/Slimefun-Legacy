#!/usr/bin/env python3
"""Compare an addon against a known-good Slimefun baseline and the candidate JAR.

The comparison intentionally uses two fresh copies of the same addon checkout.
Only the core Slimefun dependency is replaced. Addon dependencies such as
SlimefunTranslation, InfinityExpansion, InfinityLib, Networks, or other Gugu
projects are never removed merely because their group contains "slimefun".
"""
from __future__ import annotations

import argparse
import json
import os
import shutil
import stat
import subprocess
import sys
import traceback
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path
from typing import TextIO

from check_addon_binary_linkage import (
    LinkageResult,
    analyze_linkage,
    write_report as write_linkage_report,
)

PASS = "PASS"
BASELINE_BUILD_FAILED = "BASELINE_BUILD_FAILED"
LEGACY_COMPATIBILITY_FAILED = "LEGACY_COMPATIBILITY_FAILED"
INSTRUMENTATION_ERROR = "INSTRUMENTATION_ERROR"

EXIT_CODES = {
    PASS: 0,
    BASELINE_BUILD_FAILED: 10,
    LEGACY_COMPATIBILITY_FAILED: 20,
    INSTRUMENTATION_ERROR: 30,
}

CORE_ARTIFACT_NAMES = {"slimefun", "slimefun4"}
CORE_GROUP_HINTS = ("slimefun", "thebusybiscuit")
COPY_IGNORE = shutil.ignore_patterns(
    ".gradle",
    "build",
    "target",
    ".idea",
    ".slimefun-legacy-ci.init.gradle",
)


@dataclass(frozen=True)
class BuildResult:
    label: str
    exit_code: int
    command: list[str]
    log_file: str
    dependency_replaced: bool
    output_jar: str | None


def is_core_slimefun_dependency(group: str, artifact: str) -> bool:
    normalized_group = group.strip().lower()
    normalized_artifact = artifact.strip().lower()
    return normalized_artifact in CORE_ARTIFACT_NAMES and any(
        hint in normalized_group for hint in CORE_GROUP_HINTS
    )


def make_executable(path: Path) -> None:
    path.chmod(path.stat().st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)


def copy_project(source: Path, destination: Path) -> None:
    if destination.exists():
        shutil.rmtree(destination)
    shutil.copytree(source, destination, symlinks=True, ignore=COPY_IGNORE)


def stream_command(
    command: list[str],
    *,
    cwd: Path,
    env: dict[str, str],
    log: TextIO,
) -> int:
    rendered = " ".join(command)
    header = f"$ {rendered}\nWorking directory: {cwd}\n\n"
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

    return_code = process.wait()
    footer = f"\nProcess exit code: {return_code}\n"
    print(footer, end="")
    log.write(footer)
    log.flush()
    return return_code


def detect_build_system(project: Path) -> str:
    if (project / "pom.xml").is_file():
        return "maven"
    if (project / "build.gradle").is_file() or (project / "build.gradle.kts").is_file():
        return "gradle"
    raise RuntimeError("Unsupported addon build system: expected pom.xml or a Gradle build file")


def patch_maven_dependency(project: Path, version: str) -> bool:
    pom = project / "pom.xml"
    tree = ET.parse(pom)
    root = tree.getroot()
    namespace = root.tag.partition("}")[0].strip("{") if "}" in root.tag else ""
    if namespace:
        ET.register_namespace("", namespace)

    changed = False
    for dependency in root.iter():
        if dependency.tag.split("}")[-1] != "dependency":
            continue

        children = {child.tag.split("}")[-1]: child for child in dependency}
        group_node = children.get("groupId")
        artifact_node = children.get("artifactId")
        group = (group_node.text or "") if group_node is not None else ""
        artifact = (artifact_node.text or "") if artifact_node is not None else ""
        if not is_core_slimefun_dependency(group, artifact):
            continue

        assert group_node is not None and artifact_node is not None
        group_node.text = "com.github.slimefun"
        artifact_node.text = "Slimefun"

        version_node = children.get("version")
        if version_node is None:
            version_node = ET.SubElement(
                dependency,
                f"{{{namespace}}}version" if namespace else "version",
            )
        version_node.text = version

        scope_node = children.get("scope")
        if scope_node is not None and (scope_node.text or "").strip() == "system":
            scope_node.text = "provided"

        system_path = children.get("systemPath")
        if system_path is not None:
            dependency.remove(system_path)
        changed = True

    if changed:
        tree.write(pom, encoding="utf-8", xml_declaration=True)
    return changed


def write_gradle_init_script(project: Path) -> Path:
    init_script = project / ".slimefun-legacy-ci.init.gradle"
    init_script.write_text(
        """
def isCoreSlimefunDependency(dependency) {
    def group = (dependency.group ?: '').toLowerCase()
    def artifact = (dependency.name ?: '').toLowerCase()
    def coreArtifact = artifact == 'slimefun' || artifact == 'slimefun4'
    def coreGroup = group.contains('slimefun') || group.contains('thebusybiscuit')
    return coreArtifact && coreGroup
}
allprojects {
    afterEvaluate { p ->
        p.configurations.each { configuration ->
            def matches = configuration.dependencies.findAll { dependency ->
                isCoreSlimefunDependency(dependency)
            }
            matches.each { dependency ->
                configuration.dependencies.remove(dependency)
            }
            if (!matches.isEmpty()) {
                p.dependencies.add(
                    configuration.name,
                    p.files(System.getenv('SLIMEFUN_COMPATIBILITY_JAR'))
                )
            }
        }
    }
}

// A dependency plugin may inject a released Slimefun jar as a plain file dependency in its own
// afterEvaluate callback, after the normal dependency replacement above. Wait until every project
// has finished evaluation, then prepend the exact baseline/candidate jar to every JavaCompile
// classpath so the compatibility result cannot accidentally resolve symbols from a release jar.
gradle.projectsEvaluated {
    allprojects { p ->
        p.tasks.withType(org.gradle.api.tasks.compile.JavaCompile).configureEach { task ->
            task.classpath = p.files(System.getenv('SLIMEFUN_COMPATIBILITY_JAR')) + task.classpath
        }
    }
}
""".strip()
        + "\n",
        encoding="utf-8",
    )
    return init_script


def install_maven_jar(
    *,
    project: Path,
    jar: Path,
    version: str,
    env: dict[str, str],
    log: TextIO,
) -> int:
    return stream_command(
        [
            "mvn",
            "-B",
            "install:install-file",
            f"-Dfile={jar}",
            "-DgroupId=com.github.slimefun",
            "-DartifactId=Slimefun",
            f"-Dversion={version}",
            "-Dpackaging=jar",
            "-DgeneratePom=true",
        ],
        cwd=project,
        env=env,
        log=log,
    )


def find_built_addon_jar(project: Path) -> Path | None:
    candidates: list[Path] = []
    for directory in (project / "build/libs", project / "target"):
        if not directory.is_dir():
            continue
        for jar in directory.glob("*.jar"):
            lowered = jar.name.lower()
            if any(token in lowered for token in ("-sources", "-javadoc", "original-")):
                continue
            candidates.append(jar)

    if not candidates:
        return None
    return max(candidates, key=lambda path: (path.stat().st_size, path.stat().st_mtime_ns))


def build_project(
    *,
    label: str,
    project: Path,
    jar: Path,
    report_dir: Path,
) -> BuildResult:
    build_system = detect_build_system(project)
    env = dict(os.environ)
    log_path = report_dir / f"{label}.log"

    with log_path.open("w", encoding="utf-8") as log:
        log.write(f"Compatibility stage: {label}\n")
        log.write(f"Build system: {build_system}\n")
        log.write(f"Slimefun JAR: {jar}\n\n")

        if build_system == "maven":
            version = f"Legacy-{label.title()}-CI"
            replaced = patch_maven_dependency(project, version)
            if not replaced:
                raise RuntimeError("No core Slimefun Maven dependency was found to replace")

            install_code = install_maven_jar(
                project=project,
                jar=jar,
                version=version,
                env=env,
                log=log,
            )
            if install_code != 0:
                return BuildResult(
                    label,
                    install_code,
                    ["mvn", "install:install-file"],
                    log_path.name,
                    True,
                    None,
                )

            wrapper = project / "mvnw"
            if wrapper.exists():
                make_executable(wrapper)
                command = [str(wrapper), "-B", "-DskipTests", "package"]
            else:
                command = ["mvn", "-B", "-DskipTests", "package"]

            exit_code = stream_command(command, cwd=project, env=env, log=log)
            output_jar = find_built_addon_jar(project) if exit_code == 0 else None
            return BuildResult(
                label,
                exit_code,
                command,
                log_path.name,
                True,
                str(output_jar) if output_jar else None,
            )

        init_script = write_gradle_init_script(project)
        env["SLIMEFUN_COMPATIBILITY_JAR"] = str(jar)
        wrapper = project / "gradlew"
        if wrapper.exists():
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
        output_jar = find_built_addon_jar(project) if exit_code == 0 else None
        return BuildResult(
            label,
            exit_code,
            command,
            log_path.name,
            True,
            str(output_jar) if output_jar else None,
        )


def source_commit(source: Path) -> str | None:
    result = subprocess.run(
        ["git", "rev-parse", "HEAD"],
        cwd=source,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        check=False,
    )
    if result.returncode != 0:
        return None
    return result.stdout.strip() or None


def compatibility_explanation(
    status: str,
    candidate_result: BuildResult | None,
    binary_linkage: LinkageResult | None,
) -> str:
    if status == PASS:
        return (
            "The addon compiled against both the known-good baseline and the candidate "
            "Slimefun Legacy JAR, and the precompiled baseline addon passed binary linkage."
        )
    if status == BASELINE_BUILD_FAILED:
        return (
            "The addon did not compile against the known-good baseline. This is an addon "
            "dependency, repository, or build-environment failure and is not evidence of "
            "a new Slimefun Legacy regression."
        )
    if status == LEGACY_COMPATIBILITY_FAILED:
        if candidate_result is not None and candidate_result.exit_code != 0:
            return (
                "The addon compiled against the known-good baseline but its source build "
                "failed against the candidate Slimefun Legacy JAR."
            )
        if binary_linkage is not None and not binary_linkage.passed:
            return (
                "The addon compiled against both JARs, but the addon binary built against "
                "the known-good baseline references baseline Slimefun symbols that are "
                "missing from the candidate. Review `binary-linkage/summary.md`."
            )
        return "The candidate compatibility stage failed; review the attached reports."
    return (
        "The comparison could not be completed because the test harness could not identify "
        "or replace the core Slimefun dependency, or another infrastructure error occurred."
    )


def write_report(
    *,
    report_dir: Path,
    status: str,
    source: Path,
    baseline_jar: Path,
    candidate_jar: Path,
    baseline_result: BuildResult | None,
    candidate_result: BuildResult | None,
    binary_linkage: LinkageResult | None,
    error: str | None,
) -> None:
    payload = {
        "status": status,
        "source": str(source),
        "source_commit": source_commit(source),
        "baseline_jar": str(baseline_jar),
        "candidate_jar": str(candidate_jar),
        "baseline": baseline_result.__dict__ if baseline_result else None,
        "candidate": candidate_result.__dict__ if candidate_result else None,
        "binary_linkage": binary_linkage.to_json() if binary_linkage else None,
        "error": error,
    }
    (report_dir / "result.json").write_text(
        json.dumps(payload, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    (report_dir / "status.txt").write_text(status + "\n", encoding="utf-8")

    baseline_code = baseline_result.exit_code if baseline_result else "not run"
    candidate_code = candidate_result.exit_code if candidate_result else "not run"
    linkage_status = (
        "pass"
        if binary_linkage and binary_linkage.passed
        else ("fail" if binary_linkage else "not run")
    )

    lines = [
        "## Addon compatibility comparison",
        "",
        f"**Result:** `{status}`",
        "",
        compatibility_explanation(status, candidate_result, binary_linkage),
        "",
        "| Stage | Exit code/result | Log |",
        "| --- | ---: | --- |",
        f"| Known-good baseline source build | {baseline_code} | `baseline.log` |",
        f"| Candidate Legacy source build | {candidate_code} | `candidate.log` |",
        f"| Precompiled-addon binary linkage | {linkage_status} | `binary-linkage/summary.md` |",
        "",
        f"Baseline JAR: `{baseline_jar.name}`",
        "",
        f"Candidate JAR: `{candidate_jar.name}`",
    ]

    if binary_linkage is not None and not binary_linkage.passed:
        lines.extend(
            [
                "",
                "### Baseline-proven missing symbols",
                "",
                f"- Classes: **{len(binary_linkage.missing_classes)}**",
                f"- Methods: **{len(binary_linkage.missing_methods)}**",
                f"- Fields: **{len(binary_linkage.missing_fields)}**",
            ]
        )
    if error:
        lines.extend(["", "### Harness error", "", "```text", error, "```"])

    (report_dir / "summary.md").write_text("\n".join(lines) + "\n", encoding="utf-8")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("addon_source", type=Path)
    parser.add_argument("baseline_jar", type=Path)
    parser.add_argument("candidate_jar", type=Path)
    parser.add_argument("report_dir", type=Path)
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    source = args.addon_source.resolve()
    baseline_jar = args.baseline_jar.resolve()
    candidate_jar = args.candidate_jar.resolve()
    report_dir = args.report_dir.resolve()
    report_dir.mkdir(parents=True, exist_ok=True)

    for path, label in (
        (source, "addon source"),
        (baseline_jar, "baseline Slimefun JAR"),
        (candidate_jar, "candidate Slimefun JAR"),
    ):
        if not path.exists():
            error = f"Missing {label}: {path}"
            write_report(
                report_dir=report_dir,
                status=INSTRUMENTATION_ERROR,
                source=source,
                baseline_jar=baseline_jar,
                candidate_jar=candidate_jar,
                baseline_result=None,
                candidate_result=None,
                binary_linkage=None,
                error=error,
            )
            print(error, file=sys.stderr)
            return EXIT_CODES[INSTRUMENTATION_ERROR]

    baseline_result: BuildResult | None = None
    candidate_result: BuildResult | None = None
    binary_linkage: LinkageResult | None = None

    try:
        baseline_project = report_dir / "work" / "baseline"
        copy_project(source, baseline_project)
        baseline_result = build_project(
            label="baseline",
            project=baseline_project,
            jar=baseline_jar,
            report_dir=report_dir,
        )
        if baseline_result.exit_code != 0:
            status = BASELINE_BUILD_FAILED
            write_report(
                report_dir=report_dir,
                status=status,
                source=source,
                baseline_jar=baseline_jar,
                candidate_jar=candidate_jar,
                baseline_result=baseline_result,
                candidate_result=None,
                binary_linkage=None,
                error=None,
            )
            return EXIT_CODES[status]

        candidate_project = report_dir / "work" / "candidate"
        copy_project(source, candidate_project)
        candidate_result = build_project(
            label="candidate",
            project=candidate_project,
            jar=candidate_jar,
            report_dir=report_dir,
        )

        status = PASS if candidate_result.exit_code == 0 else LEGACY_COMPATIBILITY_FAILED
        if status == PASS:
            if not baseline_result.output_jar:
                raise RuntimeError("Baseline addon build succeeded but no addon JAR was found")
            binary_linkage = analyze_linkage(
                Path(baseline_result.output_jar),
                candidate_jar,
                baseline_jar,
            )
            write_linkage_report(binary_linkage, report_dir / "binary-linkage")
            if not binary_linkage.passed:
                status = LEGACY_COMPATIBILITY_FAILED

        write_report(
            report_dir=report_dir,
            status=status,
            source=source,
            baseline_jar=baseline_jar,
            candidate_jar=candidate_jar,
            baseline_result=baseline_result,
            candidate_result=candidate_result,
            binary_linkage=binary_linkage,
            error=None,
        )
        return EXIT_CODES[status]

    except Exception as exc:  # noqa: BLE001 - must classify harness failures
        error = f"{type(exc).__name__}: {exc}\n\n{traceback.format_exc()}"
        write_report(
            report_dir=report_dir,
            status=INSTRUMENTATION_ERROR,
            source=source,
            baseline_jar=baseline_jar,
            candidate_jar=candidate_jar,
            baseline_result=baseline_result,
            candidate_result=candidate_result,
            binary_linkage=binary_linkage,
            error=error,
        )
        print(error, file=sys.stderr)
        return EXIT_CODES[INSTRUMENTATION_ERROR]


if __name__ == "__main__":
    raise SystemExit(main())
