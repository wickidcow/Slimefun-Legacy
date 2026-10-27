"""Offline setup-contract tests. Fake JAR bytes and mocked javac are not runtime evidence."""
from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch
import zipfile

SOURCE = Path(__file__).resolve().parent / "runtime/r135_fluffy_networks/run_probe.py"
SPEC = importlib.util.spec_from_file_location("native_storage_probe", SOURCE)
assert SPEC is not None and SPEC.loader is not None
probe = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(probe)


class NativeStorageProbeContracts(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name).resolve()
        (self.root / "gradle.properties").write_text("projectVersion=4.1.75\n", encoding="utf-8")
        self.work = self.root / "build/full-stack-26.3"
        self.plugins = self.work / "plugins"
        self.plugins.mkdir(parents=True)
        for name, data in {
            "smoke-result.txt": "runtime smoke: PASS\n",
            "server.jar": "synthetic server bytes",
            "eula.txt": "eula=true\n",
            "server.properties": "online-mode=false\nserver-ip=127.0.0.1\n",
        }.items():
            (self.work / name).write_text(data, encoding="utf-8")
        (self.plugins / "Slimefun-Legacy-full-stack.jar").write_bytes(b"synthetic core")
        self.java = self.root / "jdk/bin/java"
        self.java.parent.mkdir(parents=True)
        self.java.write_text("not executable", encoding="utf-8")
        self.java.with_name("javac").write_text("not executable", encoding="utf-8")
        self.sources = self.root / "synthetic-probe-source"
        self.sources.mkdir()
        (self.sources / "plugin.yml").write_text("name: SyntheticContractOnly\n", encoding="utf-8")
        self.bundle = self.root / "bundle.zip"
        self.manifest = {"bundle_revision": 139, "addons": []}
        self.populate(139)
        self.addCleanup(patch.stopall)
        patch.object(probe.Path, "cwd", return_value=self.root).start()
        patch.object(probe, "HERE", self.sources).start()
        self.compile = patch.object(
            probe.subprocess, "run",
            return_value=subprocess.CompletedProcess([], 0, "", ""),
        ).start()
        self.launch = patch.object(
            probe.subprocess, "Popen", side_effect=AssertionError("No server may run in offline contract tests")
        ).start()

    def populate(self, revision):
        self.manifest["bundle_revision"] = revision
        expected = dict(probe.EXPECTED_VERSIONS)
        expected["wickidcow/SF_NetworksExp"] = (
            ("SF_Networks1.0.54.jar", "c24064cbeafe4f112c7d46bf3de5617fa151d88d")
            if revision == 135 else
            ("SF_Networks1.0.57.jar", "f5486918ab7e115298b453dc23e26a2225aedaba")
            if revision == 139 else
            ("SF_Networks1.0.58.jar", "4ebecea309e89cc60fc4f6d932505312b5468ed1")
            if revision == 140 else
            ("SF_Networks1.0.59.jar", "560d590095206a8159d82c70e7cabe6d16aaa0d8")
            if revision in (141, 142) else
            ("SF_Networks1.0.62.jar", "79e4c0116cc4b4683c668a4920e67692f925b83f")
            if revision in (152, 153) else
            ("SF_Networks1.0.62.jar", "288640e6dbf907a6ba238d98af206aab2d56f92b")
            if revision == 151 else
            ("SF_Networks1.0.61.jar", "9bf9f48b49dbedbf9e617ca840d9ccd57cf0c717")
            if revision in (149, 150) else
            ("SF_Networks1.0.60.jar", "d41d362c8b71073dca41abc24f6ef6a5a8688fff")
            if revision in (143, 144, 145, 146, 147, 148) else
            ("SF_Networks1.0.55.jar", "bcdef34d362dc74aa1aab23c8961a629daa79950")
        )
        rows = []
        for repository, (filename, commit) in expected.items():
            binary = self.plugins / filename
            binary.write_bytes(("synthetic JAR: " + repository).encode("utf-8"))
            rows.append({"repository": repository, "jar": filename,
                         "commit": commit, "sha256": probe.sha256(binary)})
        self.manifest["addons"] = rows

    def prepare(self, work=None):
        with zipfile.ZipFile(self.bundle, "w") as archive:
            archive.writestr("SF_ADDON_MANIFEST.json", json.dumps(self.manifest))
        return probe.prepare(work or self.work, self.bundle, self.java)

    def reject(self, message):
        with self.assertRaisesRegex(RuntimeError, message):
            self.prepare()
        self.compile.assert_not_called()
        self.launch.assert_not_called()

    def test_r139_reaches_native_setup_with_exact_networks_pin(self):
        evidence = self.prepare()
        self.assertEqual(evidence["bundle_revision"], 139)
        self.assertEqual(evidence["status"], "STARTED")
        self.assertNotIn("result", evidence)  # Setup success must not claim a native PASS.
        networks = evidence["tested_addons"]["wickidcow/SF_NetworksExp"]
        self.assertEqual(networks["commit"], "f5486918ab7e115298b453dc23e26a2225aedaba")
        self.assertEqual(networks["jar"], "SF_Networks1.0.57.jar")
        self.compile.assert_called_once()
        self.launch.assert_not_called()

    def test_r135_is_still_supported(self):
        self.populate(135)
        evidence = self.prepare()
        self.assertEqual(evidence["tested_addons"]["wickidcow/SF_NetworksExp"]["jar"], "SF_Networks1.0.54.jar")

    def test_r136_is_still_supported(self):
        self.populate(136)
        self.assertEqual(self.prepare()["bundle_revision"], 136)

    def test_r137_is_still_supported(self):
        self.populate(137)
        self.assertEqual(self.prepare()["bundle_revision"], 137)

    def test_r138_is_still_supported(self):
        self.populate(138)
        self.assertEqual(self.prepare()["bundle_revision"], 138)

    def test_r140_is_supported_with_networks_1_0_58(self):
        self.populate(140)
        evidence = self.prepare()
        self.assertEqual(evidence["bundle_revision"], 140)
        networks = evidence["tested_addons"]["wickidcow/SF_NetworksExp"]
        self.assertEqual(networks["jar"], "SF_Networks1.0.58.jar")
        self.assertEqual(networks["commit"], "4ebecea309e89cc60fc4f6d932505312b5468ed1")

    def test_unknown_revision_is_rejected_instead_of_skipped(self):
        self.manifest["bundle_revision"] = 154
        self.reject("requires r135, r136, r137, r138, r139, r140, r141, r142, r143, r144, r145, r146, r147, r148, r149, r150, r151, r152 or r153")

    def test_non_integer_revisions_are_rejected(self):
        for value in (141.0, "141", 142.0, "142", 143.0, "143", 144.0, "144", 145.0, "145", 146.0, "146", 147.0, "147", 148.0, "148", 149.0, "149", 150.0, "150", 151.0, "151", 152.0, "152", 153.0, "153", True, None):
            with self.subTest(value=value):
                self.manifest["bundle_revision"] = value
                self.reject("requires r135, r136, r137, r138, r139, r140, r141, r142, r143, r144, r145, r146, r147, r148, r149, r150, r151, r152 or r153")

    def test_r141_requires_networks_1_0_59_and_its_exact_source(self):
        self.populate(141)
        evidence = self.prepare()
        self.assertEqual(evidence["bundle_revision"], 141)
        networks = evidence["tested_addons"]["wickidcow/SF_NetworksExp"]
        self.assertEqual(networks["jar"], "SF_Networks1.0.59.jar")
        self.assertEqual(networks["commit"], probe.NETWORK_VERSIONS[141][1])
        self.compile.reset_mock()
        self.manifest["addons"][1]["commit"] = probe.NETWORK_VERSIONS[140][1]
        self.reject("addon source mismatch")

    def test_wrong_networks_pin_is_rejected(self):
        self.manifest["addons"][1]["commit"] = "0" * 40
        self.reject("addon source mismatch")

    def test_r142_requires_retained_networks_1_0_59_and_exact_source(self):
        (self.root / "gradle.properties").write_text("projectVersion=4.1.76\n", encoding="utf-8")
        self.populate(142)
        evidence = self.prepare()
        self.assertEqual(evidence["bundle_revision"], 142)
        self.assertEqual(evidence["expected_core_version"], "4.1.76")
        networks = evidence["tested_addons"]["wickidcow/SF_NetworksExp"]
        self.assertEqual(networks["jar"], "SF_Networks1.0.59.jar")
        self.assertEqual(networks["commit"], "560d590095206a8159d82c70e7cabe6d16aaa0d8")
        self.compile.reset_mock()
        self.manifest["addons"][1]["commit"] = probe.NETWORK_VERSIONS[140][1]
        self.reject("addon source mismatch")

    def assert_final_networks_source_for_revision(self, revision):
        (self.root / "gradle.properties").write_text("projectVersion=4.1.76\n", encoding="utf-8")
        self.populate(revision)
        evidence = self.prepare()
        self.assertEqual(evidence["bundle_revision"], revision)
        self.assertEqual(evidence["expected_core_version"], "4.1.76")
        networks = evidence["tested_addons"]["wickidcow/SF_NetworksExp"]
        self.assertEqual(networks["jar"], "SF_Networks1.0.60.jar")
        self.assertEqual(networks["commit"], "d41d362c8b71073dca41abc24f6ef6a5a8688fff")
        self.assertNotIn("result", evidence)
        self.compile.reset_mock()
        self.manifest["addons"][1]["commit"] = probe.NETWORK_VERSIONS[142][1]
        self.reject("addon source mismatch")

    def test_r143_requires_final_networks_1_0_60_and_exact_release_source(self):
        self.assert_final_networks_source_for_revision(143)

    def test_r144_requires_unchanged_networks_1_0_60_and_exact_release_source(self):
        self.assert_final_networks_source_for_revision(144)

    def test_r145_requires_unchanged_networks_1_0_60_and_exact_release_source(self):
        self.assert_final_networks_source_for_revision(145)

    def test_r146_requires_unchanged_networks_1_0_60_and_exact_release_source(self):
        self.assert_final_networks_source_for_revision(146)

    def test_r147_requires_unchanged_networks_1_0_60_and_exact_release_source(self):
        self.assert_final_networks_source_for_revision(147)

    def test_r148_requires_unchanged_networks_1_0_60_and_exact_release_source(self):
        self.assert_final_networks_source_for_revision(148)

    def test_r149_requires_new_networks_1_0_61_and_exact_release_source(self):
        (self.root / "gradle.properties").write_text("projectVersion=4.1.76\n", encoding="utf-8")
        self.populate(149)
        evidence = self.prepare()
        self.assertEqual(evidence["bundle_revision"], 149)
        self.assertEqual(evidence["expected_core_version"], "4.1.76")
        networks = evidence["tested_addons"]["wickidcow/SF_NetworksExp"]
        self.assertEqual(networks["jar"], "SF_Networks1.0.61.jar")
        self.assertEqual(networks["commit"], "9bf9f48b49dbedbf9e617ca840d9ccd57cf0c717")
        self.assertNotIn("result", evidence)
        self.compile.reset_mock()
        self.manifest["addons"][1]["commit"] = probe.NETWORK_VERSIONS[148][1]
        self.reject("addon source mismatch")

    def test_r150_retains_published_networks_1_0_61_exact_source(self):
        (self.root / "gradle.properties").write_text("projectVersion=4.1.77\n", encoding="utf-8")
        self.populate(150)
        evidence = self.prepare()
        self.assertEqual(evidence["bundle_revision"], 150)
        self.assertEqual(evidence["expected_core_version"], "4.1.77")
        networks = evidence["tested_addons"]["wickidcow/SF_NetworksExp"]
        self.assertEqual(networks["jar"], "SF_Networks1.0.61.jar")
        self.assertEqual(networks["commit"], "9bf9f48b49dbedbf9e617ca840d9ccd57cf0c717")
        self.assertNotIn("result", evidence)
        self.compile.reset_mock()
        self.manifest["addons"][1]["commit"] = probe.NETWORK_VERSIONS[148][1]
        self.reject("addon source mismatch")

    def test_r151_networks_1_0_62_and_exact_source_before_native_setup(self):
        (self.root / "gradle.properties").write_text("projectVersion=4.1.78\n", encoding="utf-8")
        self.populate(151)
        evidence = self.prepare()
        self.assertEqual(evidence["bundle_revision"], 151)
        self.assertEqual(evidence["expected_core_version"], "4.1.78")
        networks = evidence["tested_addons"]["wickidcow/SF_NetworksExp"]
        self.assertEqual(networks["jar"], "SF_Networks1.0.62.jar")
        self.assertEqual(networks["commit"], probe.NETWORK_VERSIONS[151][1])
        self.assertNotIn("result", evidence)
        self.compile.reset_mock()
        self.manifest["addons"][1]["commit"] = probe.NETWORK_VERSIONS[150][1]
        self.reject("addon source mismatch")

    def test_r152_networks_1_0_62_requires_final_merged_source(self):
        (self.root / "gradle.properties").write_text("projectVersion=4.1.78\n", encoding="utf-8")
        self.populate(152)
        evidence = self.prepare()
        self.assertEqual(evidence["bundle_revision"], 152)
        self.assertEqual(evidence["expected_core_version"], "4.1.78")
        networks = evidence["tested_addons"]["wickidcow/SF_NetworksExp"]
        self.assertEqual(networks["jar"], "SF_Networks1.0.62.jar")
        self.assertEqual(networks["commit"], probe.NETWORK_VERSIONS[152][1])
        self.assertNotIn("result", evidence)
        self.compile.reset_mock()
        self.manifest["addons"][1]["commit"] = probe.NETWORK_VERSIONS[151][1]
        self.reject("addon source mismatch")

    def test_r153_requires_the_final_merged_networks_source(self):
        (self.root / "gradle.properties").write_text("projectVersion=4.1.78\n", encoding="utf-8")
        self.populate(153)
        evidence = self.prepare()
        self.assertEqual(evidence["bundle_revision"], 153)
        self.assertEqual(evidence["expected_core_version"], "4.1.78")
        networks = evidence["tested_addons"]["wickidcow/SF_NetworksExp"]
        self.assertEqual(networks["jar"], "SF_Networks1.0.62.jar")
        self.assertEqual(networks["commit"], probe.NETWORK_VERSIONS[153][1])
        self.assertNotIn("result", evidence)
        self.compile.reset_mock()
        self.manifest["addons"][1]["commit"] = probe.NETWORK_VERSIONS[151][1]
        self.reject("addon source mismatch")

    def test_wrong_fluffy_pin_is_rejected(self):
        self.manifest["addons"][0]["commit"] = "0" * 40
        self.reject("addon source mismatch")

    def test_wrong_jar_name_is_rejected(self):
        self.manifest["addons"][1]["jar"] = "SF_Networks1.0.54.jar"
        self.reject("addon source mismatch")

    def test_missing_required_addon_is_rejected(self):
        self.manifest["addons"].pop()
        self.reject("addon source mismatch")

    def test_altered_jar_bytes_are_rejected(self):
        (self.plugins / self.manifest["addons"][1]["jar"]).write_bytes(b"changed")
        self.reject("digest mismatch")

    def test_missing_jar_is_rejected(self):
        (self.plugins / self.manifest["addons"][1]["jar"]).unlink()
        self.reject("digest mismatch")

    def test_missing_digest_is_rejected(self):
        del self.manifest["addons"][1]["sha256"]
        self.reject("digest mismatch")

    def test_failed_double_boot_is_rejected(self):
        (self.work / "smoke-result.txt").write_text("runtime smoke: FAIL\n", encoding="utf-8")
        self.reject("baseline did not pass")

    def test_production_style_path_is_rejected(self):
        with self.assertRaisesRegex(RuntimeError, "outside a disposable"):
            self.prepare(self.root / "production-server")
        self.compile.assert_not_called()

    def test_stale_probe_jar_is_rejected(self):
        (self.plugins / "R135FluffyNetworksProbe.jar").write_bytes(b"stale")
        self.reject("overwrite a prior")

    def test_compile_failure_is_not_a_success_or_skip(self):
        self.compile.return_value = subprocess.CompletedProcess([], 1, "", "synthetic compile failure")
        with self.assertRaisesRegex(RuntimeError, "did not compile"):
            self.prepare()
        self.assertFalse((self.plugins / "R135FluffyNetworksProbe.jar").exists())
        self.launch.assert_not_called()


    def test_core_version_is_taken_from_the_checkout(self):
        evidence = self.prepare()
        self.assertEqual("4.1.75", evidence["source_core_version"])
        self.assertEqual("4.1.75", evidence["expected_core_version"])
        self.assertNotIn("result", evidence)

    def test_previous_core_version_can_be_explicitly_tested(self):
        (self.root / "gradle.properties").write_text("projectVersion=4.1.74\n", encoding="utf-8")
        evidence = self.prepare()
        self.assertEqual("4.1.74", evidence["source_core_version"])
        self.assertEqual("4.1.74", evidence["expected_core_version"])

    def test_explicit_runtime_core_version_can_differ_from_source(self):
        with zipfile.ZipFile(self.bundle, "w") as archive:
            archive.writestr("SF_ADDON_MANIFEST.json", json.dumps(self.manifest))
        evidence = probe.prepare(self.work, self.bundle, self.java, "4.1.74")
        self.assertEqual("4.1.75", evidence["source_core_version"])
        self.assertEqual("4.1.74", evidence["expected_core_version"])
        self.assertNotIn("result", evidence)

    def test_invalid_explicit_runtime_core_version_is_refused_before_compilation(self):
        with zipfile.ZipFile(self.bundle, "w") as archive:
            archive.writestr("SF_ADDON_MANIFEST.json", json.dumps(self.manifest))
        for value in ("", "4.1", "4.1.74-SNAPSHOT", "4.1.74 -Devil=true"):
            with self.subTest(value=value):
                self.compile.reset_mock()
                with self.assertRaisesRegex(RuntimeError, "valid expected runtime core version"):
                    probe.prepare(self.work, self.bundle, self.java, value)
                self.compile.assert_not_called()

    def test_missing_core_version_is_refused_before_compilation(self):
        (self.root / "gradle.properties").write_text("unrelated=value\n", encoding="utf-8")
        self.reject("one valid projectVersion")

    def test_duplicate_core_versions_are_refused(self):
        (self.root / "gradle.properties").write_text(
            "projectVersion=4.1.75\nprojectVersion=4.1.75\n", encoding="utf-8")
        self.reject("one valid projectVersion")

    def test_invalid_core_version_is_refused_before_compilation(self):
        for value in ("", "4.1", "4.1.75-SNAPSHOT", "4.1.75 -Devil=true", "4.1.75\\"):
            with self.subTest(value=value):
                (self.root / "gradle.properties").write_text("projectVersion=" + value + "\n", encoding="utf-8")
                self.reject("one valid projectVersion")

    def test_missing_properties_file_is_not_silently_defaulted(self):
        (self.root / "gradle.properties").unlink()
        with self.assertRaises(FileNotFoundError):
            self.prepare()
        self.compile.assert_not_called()
        self.launch.assert_not_called()

    def test_comments_and_crlf_preserve_one_explicit_version(self):
        (self.root / "gradle.properties").write_bytes(
            b"# projectVersion=0.0.0\r\n  projectVersion = 4.1.75  \r\nother=value\r\n")
        self.assertEqual("4.1.75", self.prepare()["expected_core_version"])

    def test_java_command_receives_exact_expected_core_version(self):
        evidence = self.prepare()
        with self.assertRaisesRegex(AssertionError, "No server may run"):
            probe.run(self.work, self.java, evidence, 1)
        command = self.launch.call_args.args[0]
        self.assertEqual(str(self.java), command[0])
        self.assertIn("-Dalbion.probe.slimefun.version=4.1.75", command)
        expected_networks_jar = probe.NETWORK_VERSIONS[evidence["bundle_revision"]][0]
        expected_networks_version = expected_networks_jar.removeprefix("SF_Networks").removesuffix(".jar")
        self.assertIn(f"-Dalbion.probe.networks.version={expected_networks_version}", command)
        self.assertNotIn("-Dalbion.probe.slimefun.version=4.1.74", command)
        self.assertEqual(self.work, self.launch.call_args.kwargs["cwd"])

    def test_invalid_launch_version_is_refused_without_starting_a_server(self):
        evidence = self.prepare()
        for value in (None, "", "4.1.75 -Devil=true", 475):
            with self.subTest(value=value):
                evidence["expected_core_version"] = value
                with self.assertRaisesRegex(RuntimeError, "valid expected core version"):
                    probe.run(self.work, self.java, evidence, 1)
                self.launch.assert_not_called()

    def test_java_still_requires_the_declared_version_and_exact_equality(self):
        java = (SOURCE.parent / "R135FluffyNetworksProbe.java").read_text(encoding="utf-8")
        self.assertIn('requireVersion("Slimefun", System.getProperty("albion.probe.slimefun.version"));', java)
        self.assertNotIn('requireVersion("Slimefun", "4.1.74");', java)
        self.assertIn('check(expected != null && expected.equals(plugin.getPluginMeta().getVersion()),', java)
        self.assertIn('check(plugin != null && plugin.isEnabled(),', java)



if __name__ == "__main__":
    unittest.main()
