#!/usr/bin/env python3
"""Compile a maintained Slimefun addon against a Paper candidate stack.

The probe is advisory and works with Maven and Gradle projects. It installs the
exact Slimefun Legacy candidate into Maven Local, redirects upstream Slimefun
coordinates and the maintained com.github.wickidcow:Slimefun-Legacy coordinate
to that candidate, and redirects Bukkit/Spigot/Paper/Purpur API coordinates to
the selected Paper API before running the addon's normal assemble/package path
without tests.

Gradle projects may intentionally emit Java 21 bytecode while current Paper API
artifacts are published as Java 25 variants. The probe therefore selects
resolvable dependency variants as a Java 25 consumer without changing the
addon's own compiler release, targetCompatibility, or Kotlin jvmTarget.

CI runs this against an ephemeral clone; the addon repository is never edited.
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
LEGACY_FORK_COORDINATE = ("com.github.wickidcow", "slimefun-legacy")
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

TRANSIENT_REPOSITORY_FAILURE_MARKERS = (
    "429",
    "too many requests",
    "bad gateway",
    "service unavailable",
    "gateway timeout",
    "connection reset",
    "read timed out",
    "connection timed out",
    "remote host terminated",
    "temporary failure in name resolution",
)


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
    if (normalized_group, normalized_artifact) == LEGACY_FORK_COORDINATE:
        return True
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


def prepare_shell_wrapper(path: Path) -> None:
    """Make a checked-out Maven/Gradle wrapper executable on Linux.

    A few maintained legacy forks committed their shell wrapper with CRLF line
    endings. Executing a CRLF shebang directly on Linux reports ENOENT because
    the kernel looks for an interpreter named "/bin/sh\r". The compatibility
    probe runs in a disposable checkout, so normalize only the wrapper copy
    before executing it.
    """
    data = path.read_bytes()
    if b"\r\n" in data:
        path.write_bytes(data.replace(b"\r\n", b"\n"))
    make_executable(path)


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


def is_transient_repository_failure(text: str) -> bool:
    lowered = text.lower()
    return any(marker in lowered for marker in TRANSIENT_REPOSITORY_FAILURE_MARKERS)


def stream_maven_command(
    command: list[str],
    *,
    cwd: Path,
    env: dict[str, str],
    log: TextIO,
    max_attempts: int = 3,
) -> int:
    retry_command = list(command)
    last_code = 1

    for attempt in range(1, max_attempts + 1):
        log.flush()
        log_path = Path(log.name)
        start_offset = log_path.stat().st_size if log_path.exists() else 0

        last_code = stream_command(retry_command, cwd=cwd, env=env, log=log)
        if last_code == 0:
            return 0

        log.flush()
        with log_path.open("rb") as current_log:
            current_log.seek(start_offset)
            attempt_text = current_log.read().decode("utf-8", errors="replace")

        if not is_transient_repository_failure(attempt_text) or attempt >= max_attempts:
            return last_code

        if "-U" not in retry_command:
            retry_command = [retry_command[0], "-U", *retry_command[1:]]

        delay = attempt * 5
        message = (
            f"\nTransient Maven repository/network failure detected; retrying "
            f"attempt {attempt + 1}/{max_attempts} in {delay}s.\n"
        )
        print(message, end="")
        log.write(message)
        log.flush()

        import time

        time.sleep(delay)

    return last_code


def maven_properties(root: ET.Element) -> dict[str, str]:
    properties: dict[str, str] = {}
    for child in root:
        if local_name(child.tag) == "properties":
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
    repositories = next(
        (child for child in root if local_name(child.tag) == "repositories"),
        None,
    )
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
    repository_id.text = "papermc-paper-candidate-probe"
    url = ET.SubElement(repository, namespaced(namespace, "url"))
    url.text = PAPER_REPOSITORY


def ensure_maven_deprecation_lint(root: ET.Element, namespace: str) -> None:
    build = next((child for child in root if local_name(child.tag) == "build"), None)
    if build is None:
        build = ET.SubElement(root, namespaced(namespace, "build"))

    plugins = next((child for child in build if local_name(child.tag) == "plugins"), None)
    if plugins is None:
        plugins = ET.SubElement(build, namespaced(namespace, "plugins"))

    compiler = None
    for plugin in plugins:
        if local_name(plugin.tag) != "plugin":
            continue
        children = {local_name(child.tag): child for child in plugin}
        artifact = children.get("artifactId")
        if artifact is not None and (artifact.text or "").strip() == "maven-compiler-plugin":
            compiler = plugin
            break

    if compiler is None:
        compiler = ET.SubElement(plugins, namespaced(namespace, "plugin"))
        group = ET.SubElement(compiler, namespaced(namespace, "groupId"))
        group.text = "org.apache.maven.plugins"
        artifact = ET.SubElement(compiler, namespaced(namespace, "artifactId"))
        artifact.text = "maven-compiler-plugin"

    compiler_children = {local_name(child.tag): child for child in compiler}
    configuration = compiler_children.get("configuration")
    if configuration is None:
        configuration = ET.SubElement(compiler, namespaced(namespace, "configuration"))

    config_children = {local_name(child.tag): child for child in configuration}
    show = config_children.get("showDeprecation")
    if show is None:
        show = ET.SubElement(configuration, namespaced(namespace, "showDeprecation"))
    show.text = "true"

    compiler_args = config_children.get("compilerArgs")
    if compiler_args is None:
        compiler_args = ET.SubElement(configuration, namespaced(namespace, "compilerArgs"))

    existing = {(arg.text or "").strip() for arg in compiler_args if local_name(arg.tag) == "arg"}
    for value in ("-Xlint:deprecation", "-Xlint:removal"):
        if value not in existing:
            arg = ET.SubElement(compiler_args, namespaced(namespace, "arg"))
            arg.text = value


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
    root_dependency_ids = {
        id(node) for node in root_dependencies if local_name(node.tag) == "dependency"
    }

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
    ensure_maven_deprecation_lint(root, namespace)
    tree.write(pom, encoding="utf-8", xml_declaration=True)
    return core_rewritten, paper_rewritten, core_injected, paper_injected


def write_gradle_init_script(project: Path) -> Path:
    script = project / ".slimefun-paper-26.3.init.gradle"
    script.write_text(
        r'''
def probePaperVersion = System.getenv('PAPER_API_VERSION')
def probeSlimefunVersion = 'Paper-26.3-CI'
def probeRuntimeJvm = 25
def maintainedJegVersion = '2.1.67'

def isCoreSlimefunDependency(groupValue, artifactValue) {
    def group = (groupValue ?: '').toLowerCase()
    def artifact = (artifactValue ?: '').toLowerCase()
    def canonicalLegacyFork = group == 'com.github.wickidcow' && artifact == 'slimefun-legacy'
    def coreArtifact = artifact == 'slimefun' || artifact == 'slimefun4'
    def coreGroup = group.contains('slimefun') || group.contains('thebusybiscuit') || group.contains('starwishsama')
    return canonicalLegacyFork || (coreArtifact && coreGroup)
}

def isMaintainedJegRedirect(groupValue, artifactValue) {
    def group = (groupValue ?: '').toLowerCase()
    def artifact = (artifactValue ?: '').toLowerCase()
    return group == 'com.github.balugaq' && artifact == 'justenoughguide'
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
        ivy {
            name = 'maintainedJegRelease'
            url = uri('https://github.com/wickidcow/SF_JustEnoughGuide/releases/download')
            patternLayout {
                artifact('v[revision]/[artifact][revision].jar')
            }
            metadataSources {
                artifact()
            }
            content {
                includeGroup('com.github.wickidcow.release')
            }
        }
    }

    p.configurations.configureEach { configuration ->
        configuration.resolutionStrategy.eachDependency { details ->
            if (isCoreSlimefunDependency(details.requested.group, details.requested.name)) {
                details.useTarget("com.github.slimefun:Slimefun:${probeSlimefunVersion}")
                details.because('Paper candidate preflight must compile against the exact Slimefun Legacy candidate')
            } else if (isMaintainedJegRedirect(details.requested.group, details.requested.name)) {
                details.useTarget("com.github.wickidcow.release:SF_JustEnoughGuide:${maintainedJegVersion}")
                details.because('Maintained compatibility probes use the released JEG fork instead of stale upstream JitPack commits')
            } else if (isServerApiDependency(details.requested.group, details.requested.name)) {
                details.useTarget("io.papermc.paper:paper-api:${probePaperVersion}")
                details.because('Paper candidate preflight must not resolve an older Bukkit/Spigot/Paper API')
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
        // Paper 26.2+ API variants require Java 25. Some maintained addons still
        // intentionally emit Java 21 bytecode. Override only the consumer variant
        // attribute used for dependency selection; do not touch compiler targets.
        p.configurations.configureEach { configuration ->
            if (configuration.canBeResolved) {
                try {
                    configuration.attributes.attribute(
                        org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE,
                        probeRuntimeJvm
                    )
                } catch (RuntimeException ignored) {
                    // Tooling configurations such as Paperweight/Mache may already
                    // be locked by projectsEvaluated. They are not the addon's Java
                    // compile classpath, so leave them untouched and continue.
                    p.logger.info("Paper 26.3 probe left locked configuration '${configuration.name}' unchanged")
                }
            }
        }

        p.tasks.withType(org.gradle.api.tasks.compile.JavaCompile).configureEach { task ->
            task.classpath = p.files(System.getenv('SLIMEFUN_COMPATIBILITY_JAR')) + task.classpath
            task.options.compilerArgs.addAll(['-Xlint:deprecation', '-Xlint:removal'])
        }
    }
}
'''.strip()
        + "\n",
        encoding="utf-8",
    )
    return script


def install_slimefun_candidate(project: Path, jar: Path, env: dict[str, str], log: TextIO) -> int:
    return stream_maven_command(
        [
            "mvn",
            "-B",
            "-Dmaven.wagon.http.retryHandler.count=3",
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


def build_project(
    project: Path,
    slimefun_jar: Path,
    paper_api_version: str,
    report_dir: Path,
) -> BuildResult:
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
                prepare_shell_wrapper(wrapper)
                command = [
                    str(wrapper),
                    "-B",
                    "-Dmaven.wagon.http.retryHandler.count=3",
                    "-DskipTests",
                    "package",
                ]
            else:
                command = [
                    "mvn",
                    "-B",
                    "-Dmaven.wagon.http.retryHandler.count=3",
                    "-DskipTests",
                    "package",
                ]
        else:
            init_script = write_gradle_init_script(project)
            wrapper = project / "gradlew"
            if wrapper.is_file():
                prepare_shell_wrapper(wrapper)
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

        if build_system == "maven":
            exit_code = stream_maven_command(command, cwd=project, env=env, log=log)
        else:
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
    (report_dir / "result.json").write_text(
        json.dumps(payload, indent=2) + "\n",
        encoding="utf-8",
    )
    (report_dir / "status.txt").write_text(status + "\n", encoding="utf-8")

    if status == PASS:
        explanation = (
            "Addon source compiled successfully against the selected Paper API "
            "and exact Slimefun Legacy candidate."
        )
    elif status == COMPILE_FAILED:
        explanation = (
            "Addon compilation failed under the Paper candidate stack. Review "
            "compile.log before changing the production baseline."
        )
    else:
        explanation = (
            "The probe could not establish a valid Maven/Gradle candidate build. "
            "This is instrumentation, not confirmed addon incompatibility."
        )

    lines = [
        "## Paper candidate addon compile probe",
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
        raise SystemExit(f"Paper addon compile probe failed: not a directory: {project}")
    if not slimefun_jar.is_file():
        raise SystemExit(f"Paper addon compile probe failed: Slimefun JAR not found: {slimefun_jar}")

    result: BuildResult | None = None
    error: str | None = None
    try:
        result = build_project(project, slimefun_jar, args.paper_api_version, report_dir)
    except (OSError, RuntimeError, ET.ParseError) as exc:
        error = f"{type(exc).__name__}: {exc}"
        print(error, file=sys.stderr)

    status = write_reports(
        report_dir,
        project,
        slimefun_jar,
        args.paper_api_version,
        result,
        error,
    )
    return EXIT_CODES[status]


if __name__ == "__main__":
    raise SystemExit(main())
