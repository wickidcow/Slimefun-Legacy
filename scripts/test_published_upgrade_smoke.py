#!/usr/bin/env python3
"""Offline regression checks for the published-upgrade driver (not server gameplay)."""
import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile
import published_upgrade_smoke as subject

SCENARIO = "4.1.71-to-4.1.72"
OLD, NEW = subject.SCENARIOS[SCENARIO]


def bundle_bytes(change=None, expected=NEW):
    rows = []
    jars = {}
    for i in range(45):
        output = io.BytesIO()
        with zipfile.ZipFile(output, "w") as jar:
            jar.writestr("plugin.yml", f"name: Fixture{i}\nversion: '1.0.0'\n")
        name = f"SF_Fixture{i}1.0.0.jar"
        jars[name] = output.getvalue()
        rows.append({"jar": name, "version": "1.0.0", "sha256": hashlib.sha256(jars[name]).hexdigest(),
                     "repository": f"wickidcow/SF_Fixture{i}"})
    manifest = {"bundle_revision": expected.bundle_revision,
                "core_source_commit": expected.bundle_source, "addons": rows}
    if change:
        change(manifest, jars)
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w") as archive:
        archive.writestr("SF_ADDON_MANIFEST.json", json.dumps(manifest))
        for name, data in jars.items():
            archive.writestr(name, data)
    return output.getvalue()


def core_bytes(source=NEW.core_source, version=NEW.version, name="Slimefun", extra_properties=""):
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w") as archive:
        archive.writestr("git.properties", f"git.source.commit={source}\n{extra_properties}")
        archive.writestr("plugin.yml", f"name: {name}\nversion: '{version}'\n")
    return output.getvalue()


def phase_result(phase="upgrade", scenario=SCENARIO, platform="Paper", minecraft="26.3"):
    old, new = subject.SCENARIOS[scenario]
    expected = old.version if phase in ("seed", "baseline") else new.version
    return {"status": "PASS", "phase": phase, "scenario": scenario,
            "old_core_version": old.version, "new_core_version": new.version,
            "expected_core_version": expected, "core": expected,
            "addons_enabled": "45", "checked_items": "256", "minecraft": minecraft,
            "server_name": platform, "server_version": "Observed native server version"}


def purpur_metadata():
    return ({"project": "purpur", "version": "26.3", "builds": {"all": ["2645", "2646"]}},
            {"project": "purpur", "version": "26.3", "build": "2646", "result": "SUCCESS",
             "metadata": {"type": "experimental"}, "md5": "a" * 32})


def build(n, channel):
    return {"id": n, "channel": channel,
            "downloads": {"server:default": {"checksums": {"sha256": "a" * 64}}}}


class DriverTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)

    def tearDown(self):
        self.temp.cleanup()

    def inspect(self, modify=None, expected=NEW):
        file = self.root / "bundle.zip"
        file.write_bytes(bundle_bytes(modify, expected))
        return subject.inspect_bundle(file, expected)

    def test_synthetic_owner_matches_java_offline_identity(self):
        self.assertEqual("25f6dfb6-6d6b-3e4b-bdbe-1d7334f736eb", subject.OWNER)
        java = (subject.ROOT / "tests/published-upgrade/PublishedUpgradeFixture.java").read_text()
        self.assertIn('UUID.nameUUIDFromBytes(("OfflinePlayer:" + OWNER_NAME).getBytes(StandardCharsets.UTF_8))', java)
        self.assertIn('Bukkit.getOfflinePlayer(OWNER_NAME)', java)
        self.assertIn('!Bukkit.getOnlineMode()', java)

    def test_exact_synthetic_bundle_is_accepted(self):
        manifest, jars, plugins = self.inspect()
        self.assertEqual(45, len(jars))
        self.assertEqual(45, len(plugins.splitlines()))

    def test_each_published_side_requires_its_own_bundle_provenance(self):
        for scenario, sides in subject.SCENARIOS.items():
            for expected in sides:
                with self.subTest(scenario=scenario, version=expected.version):
                    manifest, jars, plugins = self.inspect(expected=expected)
                    self.assertEqual(expected.bundle_source, manifest["core_source_commit"])
                    self.assertEqual(45, len(jars))

    def test_baseline_bundle_source_is_not_skipped(self):
        with self.assertRaisesRegex(ValueError, "source"):
            self.inspect(lambda m, j: m.update(core_source_commit=NEW.bundle_source), expected=OLD)

    def test_historical_refresh_does_not_relabel_older_bundle_as_refreshed_core(self):
        old, _ = subject.SCENARIOS["4.1.69-to-4.1.70"]
        self.assertNotEqual(old.core_source, old.bundle_source)
        with self.assertRaisesRegex(ValueError, "source"):
            self.inspect(lambda m, j: m.update(core_source_commit=old.core_source), expected=old)

    def test_revision_string_cannot_impersonate_pinned_integer(self):
        with self.assertRaisesRegex(ValueError, "revision"):
            self.inspect(lambda m, j: m.update(bundle_revision=str(NEW.bundle_revision)))

    def test_wrong_source_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "source"):
            self.inspect(lambda m, j: m.update(core_source_commit="b" * 40))

    def test_wrong_revision_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "revision"):
            self.inspect(lambda m, j: m.update(bundle_revision=124))

    def test_missing_addon_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "45 addons"):
            self.inspect(lambda m, j: m["addons"].pop())

    def test_missing_jar_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "JAR set"):
            self.inspect(lambda m, j: j.pop(next(iter(j))))

    def test_extra_jar_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "JAR set"):
            self.inspect(lambda m, j: j.update({"unexpected.jar": b"not a plugin"}))

    def test_corrupted_jar_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "hash"):
            self.inspect(lambda m, j: j.update({next(iter(j)): b"bad bytes"}))

    def test_changed_manifest_version_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "version"):
            self.inspect(lambda m, j: m["addons"][0].update(version="1.0.1"))

    def test_duplicate_manifest_jar_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "Duplicate manifest"):
            self.inspect(lambda m, j: m["addons"].__setitem__(1, m["addons"][0]))

    def test_path_traversal_is_rejected(self):
        def change(m, j):
            old = m["addons"][0]["jar"]
            m["addons"][0]["jar"] = "../" + old
            j["../" + old] = j.pop(old)
        with self.assertRaisesRegex(ValueError, "Unsafe JAR"):
            self.inspect(change)

    def test_duplicate_plugin_name_is_rejected(self):
        def change(m, j):
            row = m["addons"][1]
            j[row["jar"]] = j[m["addons"][0]["jar"]]
            row["sha256"] = hashlib.sha256(j[row["jar"]]).hexdigest()
        with self.assertRaisesRegex(ValueError, "Duplicate plugin"):
            self.inspect(change)

    def test_plugin_name_case_cannot_hide_duplicate(self):
        def change(m, j):
            row = m["addons"][1]
            output = io.BytesIO()
            with zipfile.ZipFile(output, "w") as archive:
                archive.writestr("plugin.yml", "name: fIxTuRe0\nversion: '1.0.0'\n")
            j[row["jar"]] = output.getvalue()
            row["sha256"] = hashlib.sha256(j[row["jar"]]).hexdigest()
        with self.assertRaisesRegex(ValueError, "Duplicate plugin"):
            self.inspect(change)

    def test_all_published_core_sources_and_versions_are_accepted(self):
        path = self.root / "core.jar"
        for scenario, sides in subject.SCENARIOS.items():
            for expected in sides:
                with self.subTest(scenario=scenario, version=expected.version):
                    path.write_bytes(core_bytes(expected.core_source, expected.version))
                    subject.inspect_core(path, expected)

    def test_core_provenance_is_exact_not_a_substring(self):
        path = self.root / "core.jar"
        for source in ("HEAD", NEW.core_source + "extra", "prefix" + NEW.core_source, NEW.core_source.upper()):
            with self.subTest(source=source):
                path.write_bytes(core_bytes(source=source))
                with self.assertRaisesRegex(ValueError, "source"):
                    subject.inspect_core(path, NEW)

    def test_duplicate_core_source_property_is_rejected(self):
        path = self.root / "core.jar"
        path.write_bytes(core_bytes(extra_properties=f"git.source.commit={NEW.core_source}\n"))
        with self.assertRaisesRegex(ValueError, "source"):
            subject.inspect_core(path, NEW)

    def test_core_version_and_plugin_name_must_match(self):
        path = self.root / "core.jar"
        for version, name, message in [(OLD.version, "Slimefun", "version"),
                                       (NEW.version, "slimefun", "name"),
                                       (NEW.version + "-SNAPSHOT", "Slimefun", "version")]:
            with self.subTest(version=version, name=name):
                path.write_bytes(core_bytes(version=version, name=name))
                with self.assertRaisesRegex(ValueError, message):
                    subject.inspect_core(path, NEW)

    def test_baseline_core_rejects_candidate_source_even_with_baseline_version(self):
        path = self.root / "core.jar"
        path.write_bytes(core_bytes(version=OLD.version))
        with self.assertRaisesRegex(ValueError, "source"):
            subject.inspect_core(path, OLD)

    def test_stable_selection_is_not_list_order_dependent(self):
        result = subject.paper_build("26.2", [build(2, "STABLE"), build(9, "BETA"), build(5, "STABLE")])
        self.assertEqual(5, result["id"])

    def test_beta_is_not_silently_used_on_supported_floor(self):
        with self.assertRaisesRegex(ValueError, "No permitted"):
            subject.paper_build("1.21.11", [build(3, "BETA")])

    def test_explicit_26_3_beta_lane_is_supported(self):
        self.assertEqual("BETA", subject.paper_build("26.3", [build(3, "BETA")])["channel"])

    def test_stable_is_preferred_over_beta_on_26_3(self):
        self.assertEqual("STABLE", subject.paper_build("26.3", [build(3, "BETA"), build(2, "STABLE")])["channel"])

    def test_alpha_is_not_accepted(self):
        with self.assertRaisesRegex(ValueError, "No permitted"):
            subject.paper_build("26.3", [build(3, "ALPHA")])

    def test_server_checksum_is_required(self):
        value = build(3, "STABLE")
        value["downloads"]["server:default"]["checksums"] = {}
        with self.assertRaisesRegex(ValueError, "checksum"):
            subject.paper_build("26.2", [value])

    def test_valid_runtime_combinations(self):
        for minecraft in ("1.21.11", "26.2", "26.3"):
            for scenario in subject.SCENARIOS:
                subject.validate_runtime("paper", minecraft, None, scenario)
        subject.validate_runtime("purpur", "26.3", "2646", SCENARIO)

    def test_invalid_runtime_combinations_fail_before_download(self):
        for platform, minecraft, pin in [
            ("folia", "26.3", None), ("Paper", "26.3", None), ("paper", "1.20.6", None),
            ("paper", "26.3", "2646"), ("purpur", "26.2", "2646"),
            ("purpur", "26.3", None), ("purpur", "26.3", "latest"),
            ("purpur", "26.3", "2647"), ("purpur", "26.3", "02646"),
        ]:
            with self.subTest(platform=platform, minecraft=minecraft, pin=pin):
                with patch.object(subject, "fetch_json") as fetch:
                    with self.assertRaises(ValueError):
                        subject.prepare_server(self.root, platform, minecraft, pin, SCENARIO)
                    fetch.assert_not_called()

    def test_historical_pair_does_not_silently_gain_purpur_coverage(self):
        with patch.object(subject, "fetch_json") as fetch:
            with self.assertRaisesRegex(ValueError, "reviewed only"):
                subject.prepare_server(self.root, "purpur", "26.3", "2646", "4.1.69-to-4.1.70")
            fetch.assert_not_called()

    def test_official_purpur_pinned_metadata_is_accepted(self):
        metadata, detail = purpur_metadata()
        self.assertEqual("a" * 32, subject.validate_purpur_metadata("2646", metadata, detail))

    def test_missing_purpur_pin_does_not_fall_back_to_latest(self):
        metadata, detail = purpur_metadata()
        metadata["builds"] = {"all": ["2645", "2647"], "latest": "2647"}
        with self.assertRaisesRegex(ValueError, "not listed"):
            subject.validate_purpur_metadata("2646", metadata, detail)

    def test_wrong_purpur_project_version_build_and_result_are_rejected(self):
        for target, key, value in [(0, "project", "Purpur"), (0, "version", "26.2"),
                                   (1, "project", "paper"), (1, "version", "26.2"),
                                   (1, "build", "2647"), (1, "result", "FAILURE")]:
            with self.subTest(target=target, key=key, value=value):
                metadata, detail = purpur_metadata()
                (metadata if target == 0 else detail)[key] = value
                with self.assertRaises(ValueError):
                    subject.validate_purpur_metadata("2646", metadata, detail)

    def test_purpur_channel_and_checksum_are_required(self):
        for patch_values in ({"metadata": {"type": "stable"}}, {"md5": ""}, {"md5": "A" * 32}):
            with self.subTest(patch_values=patch_values):
                metadata, detail = purpur_metadata()
                detail.update(patch_values)
                with self.assertRaises(ValueError):
                    subject.validate_purpur_metadata("2646", metadata, detail)

    def test_purpur_bytes_are_verified_before_persisting(self):
        metadata, detail = purpur_metadata()
        with patch.object(subject, "fetch_json", side_effect=[metadata, detail]):
            with patch.object(subject.urllib.request, "urlopen", return_value=io.BytesIO(b"wrong server")):
                with self.assertRaisesRegex(ValueError, "checksum mismatch"):
                    subject.prepare_server(self.root, "purpur", "26.3", "2646", SCENARIO)
        self.assertFalse((self.root / "server.jar").exists())

    def test_exact_purpur_endpoint_and_frozen_sha256_are_recorded(self):
        metadata, detail = purpur_metadata()
        payload = core_bytes()
        detail["md5"] = hashlib.md5(payload).hexdigest()
        with patch.object(subject, "fetch_json", side_effect=[metadata, detail]) as fetch:
            with patch.object(subject.urllib.request, "urlopen", return_value=io.BytesIO(payload)) as opened:
                result = subject.prepare_server(self.root, "purpur", "26.3", "2646", SCENARIO)
        self.assertEqual("https://api.purpurmc.org/v2/purpur/26.3/2646/download", opened.call_args.args[0].full_url)
        self.assertEqual("https://api.purpurmc.org/v2/purpur/26.3/2646", fetch.call_args.args[0])
        self.assertEqual(hashlib.sha256(payload).hexdigest(), result["sha256"])
        self.assertEqual("pinned-experimental", result["channel"])
        self.assertEqual(payload, (self.root / "server.jar").read_bytes())

    def test_failure_report_is_not_a_pass(self):
        path = self.root / "report.txt"
        path.write_text("status=FAIL\nphase=baseline\n")
        with self.assertRaisesRegex(ValueError, "did not pass"):
            subject.parse_result(path)

    def test_empty_report_is_not_a_pass(self):
        path = self.root / "report.txt"
        path.write_text("")
        with self.assertRaisesRegex(ValueError, "did not pass"):
            subject.parse_result(path)

    def test_duplicate_status_cannot_hide_a_failed_phase(self):
        path = self.root / "report.txt"
        path.write_text("status=FAIL\nstatus=PASS\n")
        with self.assertRaisesRegex(ValueError, "Duplicate"):
            subject.parse_result(path)

    def test_malformed_result_line_is_rejected(self):
        path = self.root / "report.txt"
        path.write_text("status=PASS\nignored corruption\n")
        with self.assertRaisesRegex(ValueError, "Malformed"):
            subject.parse_result(path)

    def test_all_phases_are_bound_to_the_selected_scenario(self):
        for scenario in subject.SCENARIOS:
            for phase in subject.PHASES:
                with self.subTest(scenario=scenario, phase=phase):
                    subject.validate_phase_result(phase_result(phase, scenario), phase, scenario, "paper", "26.3")

    def test_wrong_or_missing_scenario_version_core_phase_and_addon_fields_fail(self):
        for key in ("status", "phase", "scenario", "old_core_version", "new_core_version",
                    "expected_core_version", "core", "addons_enabled"):
            for replacement in (None, "incorrect"):
                with self.subTest(key=key, replacement=replacement):
                    parsed = phase_result()
                    if replacement is None:
                        parsed.pop(key)
                    else:
                        parsed[key] = replacement
                    with self.assertRaises(ValueError):
                        subject.validate_phase_result(parsed, "upgrade", SCENARIO, "paper", "26.3")

    def test_historical_phase_result_cannot_pass_current_scenario(self):
        with self.assertRaises(ValueError):
            subject.validate_phase_result(phase_result(scenario="4.1.69-to-4.1.70"), "upgrade", SCENARIO, "paper", "26.3")

    def test_verification_phase_cannot_pass_without_sufficient_items(self):
        for count in ("0", "29", "invalid"):
            with self.subTest(count=count):
                parsed = phase_result()
                parsed["checked_items"] = count
                with self.assertRaises(ValueError):
                    subject.validate_phase_result(parsed, "upgrade", SCENARIO, "paper", "26.3")

    def test_native_runtime_platform_is_case_insensitive_but_must_match(self):
        subject.validate_phase_result(phase_result(platform="pUrPuR"), "upgrade", SCENARIO, "purpur", "26.3")
        with self.assertRaisesRegex(ValueError, "native server platform"):
            subject.validate_phase_result(phase_result(platform="Paper"), "upgrade", SCENARIO, "purpur", "26.3")

    def test_native_runtime_minecraft_and_version_must_be_present_and_exact(self):
        for patch_values in ({"minecraft": "26.2"}, {"minecraft": "26.3.1"}, {"minecraft": ""},
                             {"server_name": ""}, {"server_version": ""}):
            with self.subTest(patch_values=patch_values):
                parsed = phase_result()
                parsed.update(patch_values)
                with self.assertRaises(ValueError):
                    subject.validate_phase_result(parsed, "upgrade", SCENARIO, "paper", "26.3")

    def test_frozen_server_and_scenario_cannot_be_replaced_or_deleted(self):
        frozen = {}
        for name in ("server.jar", "fixture-scenario.properties"):
            (self.root / name).write_bytes(b"original")
            frozen[name] = subject.digest(self.root / name)
        subject.verify_frozen_inputs(self.root, frozen)
        for name in frozen:
            with self.subTest(name=name):
                (self.root / name).write_bytes(b"replacement")
                with self.assertRaisesRegex(ValueError, "Frozen fixture input changed"):
                    subject.verify_frozen_inputs(self.root, frozen)
                (self.root / name).unlink()
                with self.assertRaisesRegex(ValueError, "Frozen fixture input changed"):
                    subject.verify_frozen_inputs(self.root, frozen)
                (self.root / name).write_bytes(b"original")

    def test_download_checks_hash_before_persisting(self):
        path = self.root / "asset.jar"
        with patch.object(subject.urllib.request, "urlopen", return_value=io.BytesIO(b"bad")):
            with self.assertRaisesRegex(ValueError, "hash"):
                subject.download("https://example.invalid/artifact", path, "a" * 64, 3)
        self.assertFalse(path.exists())

    def test_download_rejects_oversized_payload(self):
        path = self.root / "asset.jar"
        with patch.object(subject.urllib.request, "urlopen", return_value=io.BytesIO(b"too big")):
            with self.assertRaisesRegex(ValueError, "size"):
                subject.download("https://example.invalid/artifact", path, "a" * 64, 3)
        self.assertFalse(path.exists())

    def test_driver_requires_disposable_ci_context(self):
        with patch.dict(subject.os.environ, {"GITHUB_ACTIONS": "false"}):
            with patch("sys.argv", ["driver", "--scenario", SCENARIO, "--minecraft", "1.21.11",
                                    "--work-dir", str(self.root / "server")]):
                with self.assertRaisesRegex(ValueError, "GitHub Actions"):
                    subject.main()
        self.assertFalse((self.root / "server").exists())

    def test_scenario_is_required_and_unknown_or_case_changed_values_are_rejected(self):
        for argument in ([], ["--scenario", "latest"], ["--scenario", "4.1.71-TO-4.1.72"]):
            with self.subTest(argument=argument):
                with patch("sys.argv", ["driver", "--minecraft", "26.3", "--work-dir", str(self.root), *argument]):
                    with patch("sys.stderr", new=io.StringIO()):
                        with self.assertRaises(SystemExit) as failure:
                            subject.main()
                self.assertEqual(2, failure.exception.code)


if __name__ == "__main__":
    unittest.main()
