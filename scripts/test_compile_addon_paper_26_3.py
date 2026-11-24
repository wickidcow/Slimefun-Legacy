#!/usr/bin/env python3
"""Self-tests for the centralized Paper 26.3 addon compile probe."""

from __future__ import annotations

import importlib.util
import io
import json
import os
import sys
import stat
import subprocess
import tempfile
import threading
import unittest
from unittest.mock import patch
import urllib.parse
import xml.etree.ElementTree as ET
import zipfile
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
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
    assert probe.is_core_slimefun_dependency("com.github.wickidcow", "Slimefun-Legacy")
    assert not probe.is_core_slimefun_dependency("com.github.someoneelse", "Slimefun-Legacy")
    assert not probe.is_core_slimefun_dependency("net.guizhanss", "SlimefunTranslation")
    assert probe.is_server_api_dependency("io.papermc.paper", "paper-api")
    assert probe.is_server_api_dependency("org.spigotmc", "spigot-api")
    assert probe.is_server_api_dependency("org.bukkit", "bukkit")
    assert not probe.is_server_api_dependency("com.sk89q.worldedit", "worldedit-bukkit")


def test_transient_repository_classification() -> None:
    assert probe.is_transient_repository_failure(
        "Could not transfer artifact from repo: Connection reset"
    )
    assert probe.is_transient_repository_failure(
        "Received status code 503 from server: Service Unavailable"
    )
    assert probe.is_transient_repository_failure("HTTP 429 Too Many Requests")
    # A provider that truncates a Maven API JAR after advertising Content-Length is
    # an interrupted download, not a compile/linkage error. Retry the exact artifact.
    assert probe.is_transient_repository_failure(
        "Could not transfer artifact WildStackerAPI:2023.2: "
        "Premature end of Content-Length delimited message body "
        "(expected: 41,270; received: 15,837)"
    )
    assert not probe.is_transient_repository_failure(
        "COMPILATION ERROR: cannot find symbol SlimefunItem"
    )


def test_maven_retry_backoff_remains_fail_closed() -> None:
    # Isolate network/build execution; simulate a transient 429 four times.
    # All five attempts must use the same Maven source inputs and never
    # convert a persistent failed build into PASS.
    with tempfile.TemporaryDirectory() as raw:
        root = Path(raw)
        commands = []
        delays = []

        def flaky_maven(command, *, cwd, env, log):
            commands.append(list(command))
            if len(commands) < 5:
                log.write("HTTP 429 Too Many Requests\n")
                return 1
            log.write("BUILD SUCCESS\n")
            return 0

        with (root / "transient.log").open("w+", encoding="utf-8") as log:
            with patch.object(probe, "stream_command", side_effect=flaky_maven), patch(
                "time.sleep", side_effect=delays.append
            ):
                code = probe.stream_maven_command(["mvn", "install:install-file"],
                                                  cwd=root, env={}, log=log)
        assert code == 0
        assert len(commands) == 5
        assert commands[0] == ["mvn", "install:install-file"]
        assert all(cmd == ["mvn", "-U", "install:install-file"] for cmd in commands[1:])
        assert delays == [10, 20, 30, 40]

        calls = []
        def real_source_error(command, *, cwd, env, log):
            calls.append(list(command))
            log.write("COMPILATION ERROR: cannot find symbol SlimefunItem\n")
            return 17

        with (root / "compile.log").open("w+", encoding="utf-8") as log:
            with patch.object(probe, "stream_command", side_effect=real_source_error), patch(
                "time.sleep", side_effect=AssertionError("Do not retry source errors")
            ):
                code = probe.stream_maven_command(["mvn", "package"], cwd=root, env={}, log=log)
        assert code == 17
        assert calls == [["mvn", "package"]]


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


def test_maven_rewrites_legacy_fork_coordinate() -> None:
    with tempfile.TemporaryDirectory() as raw:
        root = Path(raw)
        pom = root / "pom.xml"
        pom.write_text(
            """<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>example</groupId><artifactId>addon</artifactId><version>1</version>
  <dependencies>
    <dependency><groupId>com.github.wickidcow</groupId><artifactId>Slimefun-Legacy</artifactId><version>4.1.60</version><scope>provided</scope></dependency>
  </dependencies>
</project>
""",
            encoding="utf-8",
        )

        core, paper, core_injected, paper_injected = probe.patch_maven_project(root, "26.3.build.8")
        assert core == 1
        assert paper == 0
        assert not core_injected
        assert paper_injected
        rows = dependencies(pom)
        assert ("com.github.slimefun", "Slimefun", "Paper-26.3-CI") in rows
        assert ("com.github.wickidcow", "Slimefun-Legacy", "4.1.60") not in rows


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


def test_shell_wrapper_normalization() -> None:
    with tempfile.TemporaryDirectory() as raw:
        wrapper = Path(raw) / "gradlew"
        wrapper.write_bytes(b"#!/bin/sh\r\necho wrapper\r\n")

        probe.prepare_shell_wrapper(wrapper)

        assert wrapper.read_bytes() == b"#!/bin/sh\necho wrapper\n"
        assert wrapper.stat().st_mode & stat.S_IXUSR


def test_ephemeral_wrapper_timeout_keeps_source_distribution_pin() -> None:
    with tempfile.TemporaryDirectory() as raw:
        root = Path(raw)
        properties = root / "gradle" / "wrapper" / "gradle-wrapper.properties"
        properties.parent.mkdir(parents=True)
        distribution = ("distributionUrl=https\\\\://services.gradle.org/distributions/"
                        "gradle-9.8.2-bin.zip\\n"
                        "distributionSha256Sum=old-version-pinned-sha\\n")
        properties.write_text(distribution + "networkTimeout=10000\\n", encoding="utf-8")
        assert probe.prepare_ephemeral_gradle_wrapper_timeout(root)
        fixed = properties.read_text(encoding="utf-8")
        assert fixed == distribution + "networkTimeout=120000\\n"
        assert not probe.prepare_ephemeral_gradle_wrapper_timeout(root)
        assert properties.read_text(encoding="utf-8") == fixed

        properties.write_text(distribution + "networkTimeout=240000\\n", encoding="utf-8")
        assert not probe.prepare_ephemeral_gradle_wrapper_timeout(root)
        assert properties.read_text(encoding="utf-8") == distribution + "networkTimeout=240000\\n"

        properties.write_text(distribution, encoding="utf-8")
        assert probe.prepare_ephemeral_gradle_wrapper_timeout(root)
        assert properties.read_text(encoding="utf-8") == distribution + "networkTimeout=120000\\n"

        for bad in ("networkTimeout=10000\\nnetworkTimeout=5000\\n",
                    "networkTimeout=ten-seconds\\n"):
            properties.write_text(distribution + bad, encoding="utf-8")
            try:
                probe.prepare_ephemeral_gradle_wrapper_timeout(root)
            except RuntimeError:
                pass
            else:
                raise AssertionError("Ambiguous or invalid wrapper timeout must fail closed")
            assert properties.read_text(encoding="utf-8") == distribution + bad
        properties.unlink()
        assert not probe.prepare_ephemeral_gradle_wrapper_timeout(root)


def test_gradle_init_script_guards_both_stacks() -> None:
    with tempfile.TemporaryDirectory() as raw:
        root = Path(raw)
        script = probe.write_gradle_init_script(root)
        text = script.read_text(encoding="utf-8")
        assert "details.useTarget(\"com.github.slimefun:Slimefun:${probeSlimefunVersion}\")" in text
        assert "details.useTarget(\"io.papermc.paper:paper-api:${probePaperVersion}\")" in text
        assert "maintainedJegVersion = '2.1.67'" in text
        assert "isMaintainedJegRedirect" in text
        assert "com.github.wickidcow.release:SF_JustEnoughGuide:${maintainedJegVersion}" in text
        assert "https://github.com/wickidcow/SF_JustEnoughGuide/releases/download" in text
        assert "includeGroup('com.github.wickidcow.release')" in text
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


class GradleMinebenchRoutingTest(unittest.TestCase):
    def test_real_gradle_preserves_modules_and_other_repository_hosts(self) -> None:
        """Resolve through a local HTTP proxy using the actual generated init script."""
        requests = []
        target_modules = {("me.clip", "placeholderapi"), ("de.tr7zw", "item-nbt-api-plugin"),
                          ("de.tr7zw", "item-nbt-parent")}

        class MavenProxy(BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass

            def do_HEAD(self):
                self.do_GET()

            def do_GET(self):
                request = urllib.parse.urlsplit(self.path)
                host = request.hostname
                parts = request.path.removeprefix("/second/").strip("/").split("/")
                if len(parts) < 4:
                    self.send_error(404)
                    return
                group, artifact, version = ".".join(parts[:-3]), parts[-3], parts[-2]
                requests.append((host, group, artifact, version, parts[-1]))
                if host == "repo.minebench.de" and (group, artifact) in target_modules:
                    self.send_error(503, "Controlled unavailable mirror")
                    return
                if parts[-1].endswith(".pom"):
                    if (group, artifact) == ("de.tr7zw", "item-nbt-api-plugin"):
                        # The real published plugin POM inherits this parent.
                        # Inherit group/version too, requiring Gradle to parse it.
                        content = ("<project><modelVersion>4.0.0</modelVersion><parent>"
                                   "<groupId>de.tr7zw</groupId><artifactId>item-nbt-parent</artifactId>"
                                   "<version>" + version + "</version></parent>"
                                   "<artifactId>item-nbt-api-plugin</artifactId></project>").encode()
                    else:
                        packaging = "<packaging>pom</packaging>" if artifact == "item-nbt-parent" else ""
                        content = ("<project><modelVersion>4.0.0</modelVersion><groupId>" + group
                                   + "</groupId><artifactId>" + artifact + "</artifactId><version>"
                                   + version + "</version>" + packaging + "</project>").encode()
                elif parts[-1].endswith(".jar"):
                    stream = io.BytesIO()
                    with zipfile.ZipFile(stream, "w") as archive:
                        archive.writestr("origin.txt", host + ":" + group + ":" + artifact + ":" + version)
                    content = stream.getvalue()
                else:
                    self.send_error(404)
                    return
                self.send_response(200)
                self.send_header("Content-Length", str(len(content)))
                self.end_headers()
                if self.command != "HEAD":
                    self.wfile.write(content)

        server = ThreadingHTTPServer(("127.0.0.1", 0), MavenProxy)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            with tempfile.TemporaryDirectory(prefix="sfl-gradle-routing-") as raw:
                root = Path(raw)
                (root / "settings.gradle").write_text("rootProject.name = 'routing-regression'\n")
                (root / "build.gradle").write_text(r'''
repositories.clear()
def repositoryUrls = [
    'http://repo.minebench.de/', 'http://repo.minebench.de/second/',
    'http://declared-provider.invalid/', 'http://repo.minebench.de.unrelated.invalid/'
]
repositoryUrls.each { address ->
    repositories.maven { url = uri(address); allowInsecureProtocol = true }
}
configurations { routed; lookalike; unrelated }
def requested = [
    'me.clip:placeholderapi:2.11.6', 'de.tr7zw:item-nbt-api-plugin:2.15.1',
    'me.clip:other-module:7', 'de.tr7zw:other-module:8', 'other.group:placeholderapi:9'
]
dependencies {
    requested.each { routed it }
    lookalike 'me.clip:placeholderapi:2.11.7'
    unrelated 'de.tr7zw:item-nbt-api-plugin:2.15.2'
}
repositories.each { repository ->
    repository.content {
        if (repository.url.host == 'repo.minebench.de.unrelated.invalid') {
            includeVersion('me.clip', 'placeholderapi', '2.11.7')
        } else if (repository.url.host == 'declared-provider.invalid') {
            excludeVersion('me.clip', 'placeholderapi', '2.11.7')
        } else {
            excludeVersion('me.clip', 'placeholderapi', '2.11.7')
            excludeVersion('de.tr7zw', 'item-nbt-api-plugin', '2.15.2')
        }
    }
}
tasks.register('verifyRouting') {
    doLast {
        def resolved = [configurations.routed, configurations.lookalike, configurations.unrelated]
            .collectMany { it.resolvedConfiguration.resolvedArtifacts as List }
            .collect { artifact ->
                assert artifact.file.isFile()
                artifact.moduleVersion.id.toString()
            }.sort()
        assert resolved == (requested + ['me.clip:placeholderapi:2.11.7', 'de.tr7zw:item-nbt-api-plugin:2.15.2']).sort()
        println('MINEBENCH_ROUTING_PASS exact dependency coordinates retained')
    }
}
''', encoding="utf-8")
                script = probe.write_gradle_init_script(root)
                command = ["bash", str(SCRIPT.parent.parent / "gradlew"), "-p", str(root),
                           "verifyRouting", "--no-daemon", "--no-configuration-cache", "--no-build-cache",
                           "--refresh-dependencies", "-I", str(script),
                           "-Dhttp.proxyHost=127.0.0.1", "-Dhttp.proxyPort=" + str(server.server_port),
                           "-Dhttp.nonProxyHosts=", "--console=plain"]
                environment = dict(os.environ, PAPER_API_VERSION="26.3.routing-fixture")
                result = subprocess.run(command, text=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                        env=environment, timeout=180)
                self.assertEqual(0, result.returncode, result.stdout + "\nRequests: " + json.dumps(requests))
                self.assertIn("MINEBENCH_ROUTING_PASS", result.stdout)
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=5)
        jar_requests = {(host, group, artifact, version) for host, group, artifact, version, name in requests
                        if name.endswith(".jar")}
        self.assertFalse(any(host == "repo.minebench.de" and (group, artifact) in target_modules
                             for host, group, artifact, _, _ in requests), requests)
        pom_requests = {(host, group, artifact, version) for host, group, artifact, version, name in requests
                        if name.endswith(".pom")}
        self.assertTrue({("declared-provider.invalid", "de.tr7zw", "item-nbt-parent", "2.15.1"),
                         ("declared-provider.invalid", "de.tr7zw", "item-nbt-parent", "2.15.2")}
                        .issubset(pom_requests), pom_requests)
        self.assertTrue({("declared-provider.invalid", "me.clip", "placeholderapi", "2.11.6"),
                         ("declared-provider.invalid", "de.tr7zw", "item-nbt-api-plugin", "2.15.1"),
                         ("repo.minebench.de", "me.clip", "other-module", "7"),
                         ("repo.minebench.de", "de.tr7zw", "other-module", "8"),
                         ("repo.minebench.de", "other.group", "placeholderapi", "9"),
                         ("repo.minebench.de.unrelated.invalid", "me.clip", "placeholderapi", "2.11.7"),
                         ("declared-provider.invalid", "de.tr7zw", "item-nbt-api-plugin", "2.15.2")}
                        .issubset(jar_requests), jar_requests)


def main() -> int:
    test_coordinate_classification()
    test_transient_repository_classification()
    test_maven_retry_backoff_remains_fail_closed()
    test_maven_property_rewrite()
    test_maven_rewrites_legacy_fork_coordinate()
    test_maven_injects_missing_direct_dependencies()
    test_shell_wrapper_normalization()
    test_ephemeral_wrapper_timeout_keeps_source_distribution_pin()
    test_gradle_init_script_guards_both_stacks()
    result = unittest.TextTestRunner(verbosity=2).run(
        unittest.defaultTestLoader.loadTestsFromTestCase(GradleMinebenchRoutingTest))
    if not result.wasSuccessful():
        return 1
    print("Paper 26.3 addon compile probe self-tests passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
