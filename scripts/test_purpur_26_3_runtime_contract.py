#!/usr/bin/env python3
"""Offline compatibility contracts for the shared 26.2/Purpur/Folia-26.3 runtime smoke harness."""

from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

if __package__:
    from . import verify_folia_addon_runtime as verification
else:
    import verify_folia_addon_runtime as verification


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts" / "paper_family_26_2_runtime_smoke.sh"


def probe(software: str, minecraft: str) -> subprocess.CompletedProcess[str]:
    env = os.environ.copy()
    env["SERVER_SOFTWARE"] = software
    env["SERVER_MINECRAFT_VERSION"] = minecraft
    env["SLIMEFUN_SMOKE_VERSION"] = next(
        row.split("=", 1)[1]
        for row in (ROOT / "gradle.properties").read_text(encoding="utf-8").splitlines()
        if row.startswith("projectVersion=")
    )
    # The fake JAR fails before any download or world creation is attempted.
    return subprocess.run(
        ["bash", str(SCRIPT), str(ROOT / "missing-purpur-milestone-test.jar")],
        env=env,
        capture_output=True,
        text=True,
        check=False,
        timeout=10,
    )


class TestPurpur263RuntimeContract(unittest.TestCase):
    def test_supported_existing_26_2_targets_unchanged(self) -> None:
        for software in ("purpur", "folia", "leaf"):
            with self.subTest(software=software):
                result = probe(software, "26.2")
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("Slimefun JAR not found or empty", result.stderr)
                self.assertNotIn("Unsupported server/version combination", result.stderr)

    def test_purpur_and_folia_26_3_are_explicit_candidates(self) -> None:
        for software in ("purpur", "folia"):
            with self.subTest(software=software):
                result = probe(software, "26.3")
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("Slimefun JAR not found or empty", result.stderr)
                self.assertNotIn("Unsupported server/version combination", result.stderr)

    def test_leaf_26_3_is_not_prematurely_claimed(self) -> None:
        result = probe("leaf", "26.3")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Unsupported server/version combination", result.stderr)

    def test_future_purpur_versions_are_not_implicitly_certified(self) -> None:
        result = probe("purpur", "26.4")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Unsupported server/version combination", result.stderr)

    def test_build_pin_is_validated_against_official_manifest(self) -> None:
        source = SCRIPT.read_text(encoding="utf-8")
        self.assertIn("PURPUR_SMOKE_BUILD", source)
        self.assertIn("any(.builds.all[]?; tostring == $build)", source)
        self.assertIn("SERVER_BUILD=\"$PURPUR_SMOKE_BUILD\"", source)
        self.assertIn("${MC_VERSION} runtime smoke: PASS", source)

    def test_extra_addon_runtime_inputs_fail_closed_before_download(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            fake_core = root / "core.jar"
            fake_core.write_bytes(b"not-a-real-jar-but-nonempty")
            expected = root / "expected-addons.txt"
            expected.write_text("Example.jar\tExampleAddon\n", encoding="utf-8")

            env = os.environ.copy()
            env["SERVER_SOFTWARE"] = "folia"
            env["SERVER_MINECRAFT_VERSION"] = "26.3"
            env["SLIMEFUN_SMOKE_VERSION"] = next(
                row.split("=", 1)[1]
                for row in (ROOT / "gradle.properties").read_text(encoding="utf-8").splitlines()
                if row.startswith("projectVersion=")
            )
            env["SLIMEFUN_SMOKE_EXTRA_PLUGIN_DIR"] = str(root / "missing-addons")
            env["SLIMEFUN_SMOKE_EXPECT_PLUGINS_FILE"] = str(expected)

            result = subprocess.run(
                ["bash", str(SCRIPT), str(fake_core), str(root / "work")],
                env=env,
                capture_output=True,
                text=True,
                check=False,
                timeout=10,
            )

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("Extra plugin directory does not exist", result.stderr)
            self.assertFalse((root / "work" / "server.jar").exists())

class TestDeclaredAddonStartupRuntime(unittest.TestCase):
    """Execute the real shell harness with a deterministic offline process fixture."""

    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        self.scripts = self.root / "scripts"
        self.scripts.mkdir()
        self.script = self.scripts / SCRIPT.name
        self.script.write_text(SCRIPT.read_text(encoding="utf-8"), encoding="utf-8")
        shutil.copyfile(ROOT / "scripts/verify_folia_addon_runtime.py", self.scripts / "verify_folia_addon_runtime.py")
        # Network and Java are replaced only in this isolated test directory.
        # The actual harness input validation, copying, two-cycle lifecycle and
        # result checks execute unchanged. This is not native Folia evidence.
        (self.scripts / "runtime_download.sh").write_text(
            "runtime_download() {\n"
            "  if (( $# == 1 )); then printf '[]\\n'; else printf 'fixture-server' > \"$2\"; fi\n"
            "}\n",
            encoding="utf-8",
        )
        self.bin = self.root / "bin"
        self.bin.mkdir()
        self.executable("curl", "#!/usr/bin/env bash\nexit 90\n")
        self.executable(
            "jq",
            "#!/usr/bin/env bash\n"
            "case \"$*\" in\n"
            "  *'ok == false'*) exit 1 ;;\n"
            "  *'server:default'*) printf 'https://example.invalid/server.jar\\n' ;;\n"
            "  *'.channel'*) printf 'ALPHA\\n' ;;\n"
            "  *'.id'*) printf '3\\n' ;;\n"
            "  *) exit 91 ;;\n"
            "esac\n",
        )
        self.executable("sleep", "#!/usr/bin/env bash\n/bin/sleep 0.02\n")
        self.executable(
            "java",
            """#!/usr/bin/env bash
if [[ "${1:-}" == "-version" ]]; then
    printf 'openjdk version "25-fixture"\n' >&2
    exit 0
fi
boot=0
if [[ -f fixture-boot-count ]]; then read -r boot < fixture-boot-count; fi
boot=$((boot + 1))
printf '%s\n' "$boot" > fixture-boot-count
printf '[12:00:00 INFO]: [Slimefun] Enabling Slimefun v%s\n' "$SLIMEFUN_SMOKE_VERSION"
printf '[12:00:00 INFO]: [ExampleAddon] Enabling ExampleAddon v1.0\n'
if [[ "$boot" == "${FAKE_DISABLED_BOOT:-never}" ]]; then
    printf '[12:00:00 INFO]: [ExampleAddon] Disabling ExampleAddon v1.0\n'
fi
printf '[12:00:00 INFO]: Done (1.0s)! For help, type "help"\n'
while IFS= read -r line; do
    case "$line" in
      "sf doctor upgrade")
        printf 'Slimefun Upgrade Readiness\nOverall status: READY\nprevious shutdown Clean\n'
        ;;
      "stop")
        printf '[12:00:01 INFO]: Stopping server\n'
        printf '[12:00:01 INFO]: [ExampleAddon] Disabling ExampleAddon v1.0\n'
        exit 0
        ;;
    esac
done
""",
        )
        self.core = self.root / "core.jar"
        self.core.write_bytes(b"fixture-core")
        self.addons = self.root / "addons"
        self.addons.mkdir()
        addon_bytes = b"fixture-addon"
        (self.addons / "Example.jar").write_bytes(addon_bytes)
        self.expected = self.root / "expected.txt"
        self.expected.write_text("Example.jar\tExampleAddon\n", encoding="utf-8")
        self.selection = self.root / "selection.json"
        self.selection.write_text(json.dumps({
            "schema": 1,
            "scope": "folia-declared-startup-runtime-selection",
            "core_source_commit": "a" * 40,
            "bundle_sha256": "c" * 64,
            "bundle_revision": 144,
            "addon_count": 1,
            "addons": [{
                "jar": "Example.jar", "plugin": "ExampleAddon",
                "jar_sha256": hashlib.sha256(addon_bytes).hexdigest(),
                "repository": "wickidcow/Example", "commit": "b" * 40,
            }],
        }), encoding="utf-8")
        self.work = self.root / "work"

    def tearDown(self) -> None:
        self.temp.cleanup()

    def executable(self, name: str, content: str) -> None:
        target = self.bin / name
        target.write_text(content, encoding="utf-8")
        target.chmod(0o755)

    def run_harness(self, disabled_boot: str = "never", software: str = "folia", minecraft: str = "26.3") -> subprocess.CompletedProcess[str]:
        env = os.environ.copy()
        env.update({
            "PATH": str(self.bin) + os.pathsep + env["PATH"],
            "SERVER_SOFTWARE": software,
            "SERVER_MINECRAFT_VERSION": minecraft,
            "SLIMEFUN_SMOKE_VERSION": "4.1.76",
            "SLIMEFUN_SMOKE_EXTRA_PLUGIN_DIR": str(self.addons),
            "SLIMEFUN_SMOKE_EXPECT_PLUGINS_FILE": str(self.expected),
            "SLIMEFUN_SMOKE_SELECTION_FILE": str(self.selection),
            "SLIMEFUN_SMOKE_SOURCE_COMMIT": "a" * 40,
            "FAKE_DISABLED_BOOT": disabled_boot,
            "SERVER_SMOKE_STARTUP_TIMEOUT": "3",
            "SERVER_SMOKE_SHUTDOWN_TIMEOUT": "3",
        })
        return subprocess.run(
            ["bash", str(self.script), str(self.core), str(self.work)],
            env=env, capture_output=True, text=True, check=False, timeout=15,
        )


    def configure_leaf_v3_fixture(self, checksum: str) -> None:
        # Exercise the actual jq parser and the exact official host/path contract.
        (self.bin / "jq").unlink()
        builds = [
            {"id": 136, "channel": "ALPHA", "downloads": {"server:default": {
                "url": "https://dl-data.leafmc.one/leaf/26.2/136/leaf-26.2-136.jar",
                "checksums": {"sha256": checksum}
            }}},
            {"id": 135, "channel": "ALPHA", "downloads": {"server:default": {
                "url": "https://dl-data.leafmc.one/leaf/26.2/135/leaf-26.2-135.jar",
                "checksums": {"sha256": "0" * 64}
            }}},
        ]
        (self.scripts / "leaf-builds.json").write_text(json.dumps(builds), encoding="utf-8")
        (self.scripts / "runtime_download.sh").write_text(
            'runtime_download() {\n'
            '  case "$1" in\n'
            '    https://dl.leafmc.one/v3/projects/leaf/versions/26.2/builds) cat "$REPO_ROOT/scripts/leaf-builds.json" ;;\n'
            '    https://dl-data.leafmc.one/leaf/26.2/136/leaf-26.2-136.jar) printf fixture-server > "$2" ;;\n'
            '    *) return 90 ;;\n'
            '  esac\n'
            '}\n',
            encoding="utf-8",
        )

    def test_leaf_v3_latest_build_and_checksum_allow_both_boots(self) -> None:
        self.configure_leaf_v3_fixture(hashlib.sha256(b"fixture-server").hexdigest())
        result = self.run_harness(software="leaf", minecraft="26.2")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("Build: 136", (self.work / "runtime-build.txt").read_text())
        self.assertIn("Cycles: 2", (self.work / "smoke-result.txt").read_text())

    def test_leaf_v3_checksum_mismatch_blocks_runtime_evidence(self) -> None:
        self.configure_leaf_v3_fixture("0" * 64)
        result = self.run_harness(software="leaf", minecraft="26.2")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Leaf server JAR SHA-256 mismatch", result.stderr)
        self.assertFalse((self.work / "smoke-result.txt").exists())
        self.assertFalse((self.work / "fixture-boot-count").exists())

    def test_both_boots_allow_disable_only_after_normal_server_shutdown(self) -> None:
        result = self.run_harness()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("Cycles: 2", (self.work / "smoke-result.txt").read_text())
        receipt = json.loads((self.work / "installed-plugins.json").read_text())
        self.assertEqual(receipt["addon_count"], 1)
        self.assertEqual(receipt["core_jar_sha256"], hashlib.sha256(self.core.read_bytes()).hexdigest())

    def test_rejects_quiet_self_disable_on_first_boot(self) -> None:
        result = self.run_harness("1")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("disabled before normal server shutdown", result.stderr)
        self.assertFalse((self.work / "smoke-result.txt").exists())

    def test_rejects_quiet_self_disable_on_second_boot(self) -> None:
        result = self.run_harness("2")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("smoke second", result.stderr)
        self.assertIn("disabled before normal server shutdown", result.stderr)
        self.assertFalse((self.work / "smoke-result.txt").exists())

    def test_expected_list_duplicates_fail_before_server_download(self) -> None:
        self.expected.write_text("Example.jar\tExampleAddon\nExample.jar\tExampleAddon\n", encoding="utf-8")
        result = self.run_harness()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("duplicate JAR/plugin", result.stderr)
        self.assertFalse((self.work / "server.jar").exists())

    def test_log_check_rejects_missing_enable_and_disable_after_readiness(self) -> None:
        log = self.root / "runtime.log"
        for messages, diagnostic in (
            (["Done (1.0s)!", "Stopping server", "Enabling ExampleAddon v1.0"], "did not enable"),
            (["Enabling ExampleAddon v1.0", "Done (1.0s)!", "Slimefun Upgrade Readiness",
              "Disabling ExampleAddon v1.0", "Stopping server"], "disabled before"),
        ):
            with self.subTest(diagnostic=diagnostic):
                log.write_text("\n".join(messages) + "\n", encoding="utf-8")
                with self.assertRaisesRegex(ValueError, diagnostic):
                    verification.verify_log(log, self.expected)


if __name__ == "__main__":
    unittest.main()
