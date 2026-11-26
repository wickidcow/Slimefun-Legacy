#!/usr/bin/env python3
"""Offline policy and real workflow-shell tests; GitHub CLI is always a fake."""
from __future__ import annotations

import copy
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parent))
from verify_sfl_release_assets import (  # noqa: E402
    BEDROCK_PACK, BEDROCK_PIN_PATH, BUNDLE, PACK, ROLLBACK, ROLLBACK_PIN, PolicyError, load_json,
    plan_release, verify_preserved,
)

ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT / ".github/workflows/cleanup-sfl-release-assets.yml"


def asset(name: str, identifier: int, digest: str = "a" * 64, size: int = 100) -> dict:
    return {"id": identifier, "name": name, "size": size, "digest": "sha256:" + digest, "state": "uploaded"}


def release(*, pack: bool = True, rollback: bool = True) -> dict:
    # Compact copy of public asset metadata; no byte-download claims here.
    assets = [
        asset("Slimefun-Legacy4.1.74.jar", 640213945,
              "72a524d7c0d6f75a9decbf06679515887273ae53673d3f5ee38f355bef952c96", 8668060),
        asset(BUNDLE, 642338930,
              "534768b57854fdc21280bda483266217a61bc140a48df3f246c71842c82b173d", 24444372),
    ]
    if pack:
        assets.append(asset(PACK, 640213951,
                            "1b856fff90fe78b7d5964a86a8ed281517d073a082db485d73f31cd07aa64a83", 9143959))
    if rollback:
        assets.append({key: ROLLBACK_PIN[key] for key in ("id", "name", "size", "digest")} | {"state": "uploaded"})
    return {"id": 412654205, "tag_name": "v4.1.74", "draft": False, "prerelease": False, "immutable": False,
            "assets": assets}


def bedrock_release(tag: str = "v4.1.75") -> dict:
    # Origin overlay and composed successor are deliberately distinct assets.
    # These identities are independent of the candidate's preservation pin.
    origin = tag == "v4.1.75"
    state = {
        "id": 415923695 if origin else 500000076,
        "tag_name": tag, "draft": False, "prerelease": False, "immutable": False,
        "assets": [
            asset(f"Slimefun-Legacy{tag[1:]}.jar", 650941345),
            asset(BUNDLE, 650941344),
            asset(PACK, 650941348),
            (asset("SFL_GEYSER_RP_experimental_v1.0.3.zip", 664360404,
                   "90d78fcf8382bdee1bce3a364515769cb8325c174b58c34b512039502cc50e51", 1265679)
             if origin else asset("SFL_GEYSER_RP_experimental_v1.0.3_complete.zip", 700000076,
                                  "60fb9fcfe19e7e5644ff7202fd83bcd2b73b9f7685f3a57797d73877153c014c", 2390264)),
        ],
    }
    return state


def run_block(text: str, name: str) -> str:
    section = text.split("- name: " + name, 1)[1]
    lines = section.split("        run: |\n", 1)[1].splitlines()
    result = []
    for line in lines:
        if line and not line.startswith("          "):
            break
        result.append(line[10:] if line else "")
    return "\n".join(result) + "\n"


FAKE_GH = r'''#!/usr/bin/env python3
import json, os, pathlib, sys
args = sys.argv[1:]
state_path = pathlib.Path(os.environ['MOCK_STATE'])
with open(os.environ['MOCK_CALLS'], 'a') as calls:
    calls.write(json.dumps(args) + '\n')
state = json.loads(state_path.read_text())
repo = 'repos/wickidcow/Slimefun-Legacy'
if args == ['api', repo + '/releases/latest']:
    print(json.dumps(state))
elif args == ['api', repo + '/releases/' + str(state['id'])]:
    replacement = os.environ.get('MOCK_REPLACE_AFTER')
    if replacement:
        state = json.loads(pathlib.Path(replacement).read_text())
        state_path.write_text(json.dumps(state))
    print(json.dumps(state))
elif len(args) == 4 and args[:3] == ['api', '--method', 'DELETE']:
    if os.environ.get('MOCK_FAIL_DELETE'):
        sys.exit(8)
    expected_prefix = repo + '/releases/assets/'
    if not args[3].startswith(expected_prefix):
        sys.exit(9)
    identity = int(args[3][len(expected_prefix):])
    previous = len(state['assets'])
    state['assets'] = [a for a in state['assets'] if a['id'] != identity]
    if len(state['assets']) != previous - 1:
        sys.exit(10)
    state_path.write_text(json.dumps(state))
# Compatibility solely for the separately retained old-workflow reproduction.
elif args[:2] == ['release', 'list']:
    print(state['tag_name'])
elif args[:2] == ['release', 'view']:
    print('\n'.join(a['name'] for a in state['assets']))
elif args[:2] == ['release', 'delete-asset']:
    state['assets'] = [a for a in state['assets'] if a['name'] != args[3]]
    state_path.write_text(json.dumps(state))
else:
    sys.stderr.write('Unsupported mock command: ' + repr(args))
    sys.exit(11)
'''


def exercise_shell(state: dict, *, replacement: dict | None = None, fail_delete: bool = False,
                   script: str | None = None) -> tuple[subprocess.CompletedProcess, dict, list]:
    with tempfile.TemporaryDirectory(prefix="sfl-assets-test-") as tmp:
        directory = Path(tmp)
        (directory / "mockbin").mkdir()
        mock = directory / "mockbin/gh"
        mock.write_text(FAKE_GH, encoding="utf-8")
        mock.chmod(0o755)
        (directory / "scripts").mkdir()
        shutil.copy2(ROOT / "scripts/verify_sfl_release_assets.py", directory / "scripts")
        (directory / "compatibility").mkdir()
        shutil.copy2(BEDROCK_PIN_PATH, directory / "compatibility")
        state_file = directory / "state.json"
        state_file.write_text(json.dumps(state), encoding="utf-8")
        calls_file = directory / "calls.jsonl"
        env = {
            "PATH": str(directory / "mockbin") + os.pathsep + os.path.dirname(sys.executable) + ":/usr/bin:/bin",
            "HOME": str(directory),
            "GITHUB_REPOSITORY": "wickidcow/Slimefun-Legacy",
            "MOCK_STATE": str(state_file),
            "MOCK_CALLS": str(calls_file),
        }
        if sys.flags.optimize:
            env["PYTHONOPTIMIZE"] = str(sys.flags.optimize)
        if replacement is not None:
            replacement_path = directory / "replacement.json"
            replacement_path.write_text(json.dumps(replacement), encoding="utf-8")
            env["MOCK_REPLACE_AFTER"] = str(replacement_path)
        if fail_delete:
            env["MOCK_FAIL_DELETE"] = "1"
        if script is None:
            script = run_block(WORKFLOW.read_text(), "Normalize latest stable Slimefun Legacy release assets")
        result = subprocess.run(["bash", "-c", script], cwd=directory, env=env,
                                capture_output=True, text=True, timeout=10)
        calls = [json.loads(line) for line in calls_file.read_text().splitlines()] if calls_file.exists() else []
        return result, json.loads(state_file.read_text()), calls


def deletions(calls: list) -> list:
    return [call for call in calls if 'DELETE' in call or 'delete-asset' in call]


class PolicyTests(unittest.TestCase):
    def test_observed_bedrock_origin_and_approved_successor_are_preserved(self):
        for tag in ("v4.1.75", "v4.1.76"):
            with self.subTest(tag=tag):
                state = bedrock_release(tag)
                plan = plan_release(state)
                self.assertEqual(4, len(plan["retained"]))
                self.assertEqual(state["assets"][-1], plan["retained"][state["assets"][-1]["name"]])
                self.assertEqual([], plan["remove"])

    def test_source_overlay_and_complete_candidate_cannot_swap_release_roles(self):
        for tag, other in (("v4.1.75", "v4.1.76"), ("v4.1.76", "v4.1.75")):
            with self.subTest(tag=tag):
                state = bedrock_release(tag)
                state["assets"][-1] = bedrock_release(other)["assets"][-1]
                with self.assertRaises(PolicyError):
                    plan_release(state)

    def test_complete_candidate_name_cannot_wrap_overlay_bytes(self):
        state = bedrock_release("v4.1.76")
        source = bedrock_release()["assets"][-1]
        state["assets"][-1].update(size=source["size"], digest=source["digest"])
        with self.assertRaises(PolicyError):
            plan_release(state)

    def test_bedrock_origin_requires_original_release_and_asset_ids(self):
        for altered in ("release", "asset"):
            with self.subTest(altered=altered):
                state = bedrock_release()
                if altered == "release":
                    state["id"] += 1
                else:
                    state["assets"][-1]["id"] += 1
                with self.assertRaises(PolicyError):
                    plan_release(state)

    def test_bedrock_only_allows_reviewed_bytes_and_filename(self):
        for tag in ("v4.1.75", "v4.1.76"):
            for key, value in [("size", 1265680), ("digest", "sha256:" + "0" * 64),
                               ("name", "GEYSER_SFL_RP_Experimental_v0.0.2.zip"),
                               ("name", "GEYSER_SFL_RP_v0.0.3.zip"),
                               ("name", "Geyser-Spigot-RP-alpha-v0.0.3.zip")]:
                with self.subTest(tag=tag, key=key, value=value):
                    state = bedrock_release(tag)
                    state["assets"][-1][key] = value
                    with self.assertRaises(PolicyError):
                        plan_release(state)

    def test_previous_bedrock_asset_is_not_retained_by_the_current_pin(self):
        historical = [
            asset("GEYSER_SFL_RP_Experimental_v0.0.1.zip", 664136278,
                  "ba4fa277fb71a33ca557e0206ea51c40680593952a5d47fbd849b335c9fe09bf", 226576),
            asset("GEYSER_SFL_RP_v0.0.2.zip", 664289959,
                  "b27941425a646ba97c4789ecf2841e20f08663e79b3d538b552516d40cc67ee1", 1329705),
        ]
        for tag in ("v4.1.75", "v4.1.76"):
            for previous in historical:
                with self.subTest(tag=tag, name=previous["name"]):
                    state = bedrock_release(tag)
                    state["assets"][-1] = previous
                    with self.assertRaises(PolicyError):
                        plan_release(state)

    def test_bedrock_is_not_implicitly_approved_on_other_tags(self):
        for tag in ("v4.1.74", "v4.1.77", "v5.0.0"):
            with self.subTest(tag=tag):
                with self.assertRaises(PolicyError):
                    plan_release(bedrock_release(tag))

    def test_bedrock_preservation_never_makes_core_or_bundle_optional(self):
        for index in (0, 1):
            with self.subTest(index=index):
                state = bedrock_release()
                state["assets"].pop(index)
                with self.assertRaises(PolicyError):
                    plan_release(state)

    def test_carried_bedrock_replacement_or_disappearance_is_reported(self):
        before = bedrock_release("v4.1.76")
        for change in ("id", "missing"):
            with self.subTest(change=change):
                after = bedrock_release("v4.1.76")
                if change == "id":
                    after["assets"][-1]["id"] += 1
                else:
                    after["assets"].pop()
                with self.assertRaises(PolicyError):
                    verify_preserved(before, after)

    def test_bedrock_pin_invalid_metadata_is_refused(self):
        original_pin = load_json(BEDROCK_PIN_PATH)
        changes = [("schema_version", True), ("repository", "other/repository"),
                   ("asset_name", "another.zip"), ("release_id", True), ("asset_id", 0),
                   ("archive_path", "../unreviewed.zip"), ("stability", "stable"),
                   ("size_bytes", -1), ("sha256", "invalid"), ("release_tag", "latest"),
                   ("carry_forward_tags", ["v4.1.75"]), ("carry_forward_tags", ["v4.1.76", "v4.1.76"]),
                   ("carry_forward_tags", "v4.1.76"), ("carry_forward_tags", []),
                   ("carry_forward_tags", ["latest"]), ("carry_forward_tags", ["v4.1.77"])]
        with tempfile.TemporaryDirectory() as temporary:
            pin_path = Path(temporary) / "pin.json"
            for key, value in changes:
                with self.subTest(key=key, value=value):
                    pin_path.write_text(json.dumps(original_pin | {key: value}))
                    with patch("verify_sfl_release_assets.BEDROCK_PIN_PATH", pin_path):
                        with self.assertRaises(PolicyError):
                            plan_release(bedrock_release())

    def test_bedrock_source_provenance_cannot_change_or_use_numeric_coercion(self):
        original_pin = load_json(BEDROCK_PIN_PATH)
        candidates = [None, {}, [], original_pin["source_assets"][:1],
                      list(reversed(original_pin["source_assets"])),
                      original_pin["source_assets"] + [original_pin["source_assets"][0]]]
        for index in (0, 1):
            for key, value in (("release_id", True), ("asset_id", 0),
                               ("asset_id", float(original_pin["source_assets"][index]["asset_id"])),
                               ("size_bytes", float(original_pin["source_assets"][index]["size_bytes"])),
                               ("sha256", "0" * 64), ("release_tag", "v4.1.76"),
                               ("asset_name", "unreviewed.zip"), ("extra", True)):
                changed = copy.deepcopy(original_pin["source_assets"])
                changed[index][key] = value
                candidates.append(changed)
        with tempfile.TemporaryDirectory() as temporary:
            pin_path = Path(temporary) / "pin.json"
            for sources in candidates:
                with self.subTest(sources=sources):
                    pin_path.write_text(json.dumps(original_pin | {"source_assets": sources}))
                    with patch("verify_sfl_release_assets.BEDROCK_PIN_PATH", pin_path):
                        with self.assertRaises(PolicyError):
                            plan_release(bedrock_release())

    def test_observed_four_asset_release_preserves_rollback(self):
        plan = plan_release(release())
        self.assertEqual(4, len(plan["retained"]))
        self.assertEqual([], plan["remove"])

    def test_two_asset_base_is_valid(self):
        self.assertEqual(2, len(plan_release(release(pack=False, rollback=False))["retained"]))

    def test_three_asset_final_is_valid(self):
        self.assertEqual(3, len(plan_release(release(rollback=False))["retained"]))

    def test_pending_pack_with_pinned_rollback_is_valid(self):
        self.assertEqual(3, len(plan_release(release(pack=False))["retained"]))

    def test_future_version_requires_exact_matching_core(self):
        state = release(rollback=False)
        state.update(id=500000001, tag_name="v4.1.75")
        with self.assertRaises(PolicyError):
            plan_release(state)
        state["assets"][0]["name"] = "Slimefun-Legacy4.1.75.jar"
        self.assertEqual(3, len(plan_release(state)["retained"]))

    def test_rollback_pin_fields_cannot_change(self):
        for key, value in [("id", 99), ("size", 23), ("digest", "sha256:" + "0" * 64)]:
            with self.subTest(key=key):
                state = release()
                state["assets"][-1][key] = value
                with self.assertRaises(PolicyError):
                    plan_release(state)

    def test_rollback_is_not_approved_on_another_release(self):
        state = release()
        state["id"] += 1
        with self.assertRaises(PolicyError):
            plan_release(state)

    def test_arbitrary_fourth_asset_is_not_accepted(self):
        state = release(rollback=False)
        state["assets"].append(asset("unexpected.zip", 99))
        with self.assertRaises(PolicyError):
            plan_release(state)

    def test_other_rollback_names_are_not_implicitly_approved(self):
        state = release()
        state["assets"][-1]["name"] = "SF_Addons_r999_1.21.11-26.3.zip"
        with self.assertRaises(PolicyError):
            plan_release(state)

    def test_missing_required_assets_fail(self):
        for index in (0, 1):
            with self.subTest(index=index):
                state = release()
                state["assets"].pop(index)
                with self.assertRaises(PolicyError):
                    plan_release(state)

    def test_duplicate_names_and_ids_fail(self):
        for key in ("id", "name"):
            with self.subTest(key=key):
                state = release()
                state["assets"][1][key] = state["assets"][0][key]
                with self.assertRaises(PolicyError):
                    plan_release(state)

    def test_draft_and_prerelease_and_unsafe_tag_fail(self):
        for key, value in [("draft", True), ("prerelease", True), ("tag_name", "v4.1.75-rc1")]:
            with self.subTest(key=key):
                state = release()
                state[key] = value
                with self.assertRaises(PolicyError):
                    plan_release(state)

    def test_invalid_asset_fields_fail(self):
        for key, value in [("state", "starter"), ("id", True), ("id", -1), ("name", "path/a.jar"),
                           ("name", "bad\nname.jar"), ("size", 0), ("digest", None)]:
            with self.subTest(key=key, value=value):
                state = release()
                state["assets"][0][key] = value
                with self.assertRaises(PolicyError):
                    plan_release(state)

    def test_established_legacy_families_only_are_planned(self):
        state = release()
        names = ["SF_Test1.0.1.jar", "SFL_AddonsNDependencies.zip", "SFL_AddonsNDependencies.sha256",
                 "SFL_Addons_1.21.11-26.3.zip", "SlimefunLegacy-ResourcePack-4.0.0.zip"]
        state["assets"].extend(asset(name, index + 1) for index, name in enumerate(names))
        self.assertEqual(names, [a["name"] for a in plan_release(state)["remove"]])
        self.assertEqual(4, len(plan_release(state)["retained"]))

    def test_immutable_release_cannot_have_deletion_plan(self):
        state = release()
        state["immutable"] = True
        self.assertEqual([], plan_release(state)["remove"])
        state["assets"].append(asset("SF_Test1.0.jar", 1))
        with self.assertRaises(PolicyError):
            plan_release(state)

    def test_clean_post_snapshot_matches(self):
        before = release()
        before["assets"].append(asset("SF_Test1.0.jar", 1))
        self.assertEqual([], verify_preserved(before, release())["remove"])

    def test_post_snapshot_missing_rollback_fails(self):
        with self.assertRaises(PolicyError):
            verify_preserved(release(), release(rollback=False))

    def test_post_snapshot_replaced_core_or_bundle_or_pack_fails(self):
        for index in (0, 1, 2):
            with self.subTest(index=index):
                after = release()
                after["assets"][index]["id"] += 1
                with self.assertRaises(PolicyError):
                    verify_preserved(release(), after)

    def test_post_snapshot_altered_digest_or_size_fails(self):
        for key, value in [("digest", "sha256:" + "0" * 64), ("size", 900)]:
            with self.subTest(key=key):
                after = release()
                after["assets"][1][key] = value
                with self.assertRaises(PolicyError):
                    verify_preserved(release(), after)

    def test_post_snapshot_other_release_fails(self):
        after = release(rollback=False)
        after["id"] += 1
        with self.assertRaises(PolicyError):
            verify_preserved(release(rollback=False), after)

    def test_post_snapshot_leftover_legacy_fails(self):
        state = release()
        state["assets"].append(asset("SF_Test1.0.jar", 1))
        with self.assertRaises(PolicyError):
            verify_preserved(state, state)

    def test_download_counts_and_order_are_not_identity(self):
        after = release()
        after["assets"].reverse()
        for item in after["assets"]:
            item["download_count"] = 1000000
        self.assertEqual(4, len(verify_preserved(release(), after)["retained"]))

    def test_duplicate_json_keys_fail(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "state.json"
            path.write_text('{"id":1,"id":2}')
            with self.assertRaises(PolicyError):
                load_json(path)

    def test_cli_failure_does_not_emit_a_new_delete_list(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory = Path(tmp)
            before = directory / "before.json"
            before.write_text('{"assets":')
            deletion_file = directory / "delete-ids.txt"
            result = subprocess.run([sys.executable, str(ROOT / "scripts/verify_sfl_release_assets.py"),
                                     "plan", str(before), "--delete-ids", str(deletion_file)],
                                    capture_output=True, text=True, timeout=10)
            self.assertNotEqual(0, result.returncode)
            self.assertFalse(deletion_file.exists())


class WorkflowShellTests(unittest.TestCase):
    def test_bedrock_origin_and_successor_run_without_any_deletion(self):
        for tag in ("v4.1.75", "v4.1.76"):
            with self.subTest(tag=tag):
                state = bedrock_release(tag)
                result, after, calls = exercise_shell(state)
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertEqual(state, after)
                self.assertEqual([], deletions(calls))

    def test_bedrock_is_preserved_while_only_established_legacy_assets_are_removed(self):
        state = bedrock_release("v4.1.76")
        state["assets"].append(asset("SF_Test1.0.jar", 1))
        result, after, calls = exercise_shell(state)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(bedrock_release("v4.1.76"), after)
        self.assertEqual(["1"], [call[-1].rsplit("/", 1)[-1] for call in deletions(calls)])

    def test_changed_or_unapproved_bedrock_blocks_before_any_deletion(self):
        for tag, key, value in [
                ("v4.1.75", "id", 664360405),
                ("v4.1.75", "size", 1265680),
                ("v4.1.76", "digest", "sha256:" + "0" * 64),
                ("v4.1.76", "name", "GEYSER_SFL_RP_Experimental_v0.0.1.zip"),
                ("v4.1.76", "name", "GEYSER_SFL_RP_v0.0.2.zip"),
                ("v4.1.76", "name", "GEYSER_SFL_RP_v0.0.3.zip"),
                ("v4.1.76", "name", "Geyser-Spigot-RP-alpha-v0.0.3.zip"),
                ("v4.1.77", "state", "uploaded")]:
            with self.subTest(tag=tag, key=key):
                state = bedrock_release(tag)
                state["assets"][-1][key] = value
                state["assets"].append(asset("SF_Test1.0.jar", 1))
                result, after, calls = exercise_shell(state)
                self.assertNotEqual(0, result.returncode)
                self.assertEqual(state, after)
                self.assertEqual([], deletions(calls))

    def test_overlay_and_complete_candidate_swaps_block_before_any_deletion(self):
        for tag, other in (("v4.1.75", "v4.1.76"), ("v4.1.76", "v4.1.75")):
            for append in (False, True):
                with self.subTest(tag=tag, append=append):
                    state = bedrock_release(tag)
                    replacement = bedrock_release(other)["assets"][-1]
                    if append:
                        state["assets"].append(replacement)
                    else:
                        state["assets"][-1] = replacement
                    state["assets"].insert(0, asset("SF_Test1.0.jar", 1))
                    result, after, calls = exercise_shell(state)
                    self.assertNotEqual(0, result.returncode)
                    self.assertEqual(state, after)
                    self.assertEqual([], deletions(calls))

    def test_extra_bedrock_or_unknown_asset_blocks_all_deletions(self):
        for name in ("GEYSER_SFL_RP_Experimental_v0.0.1.zip", "GEYSER_SFL_RP_v0.0.2.zip", "GEYSER_SFL_RP_v0.0.3.zip",
                     "Geyser-Spigot-RP-alpha-v0.0.3.zip", "unexpected.zip"):
            with self.subTest(name=name):
                state = bedrock_release()
                state["assets"].extend([asset("SF_Test1.0.jar", 1), asset(name, 2)])
                result, after, calls = exercise_shell(state)
                self.assertNotEqual(0, result.returncode)
                self.assertEqual(state, after)
                self.assertEqual([], deletions(calls))

    def test_external_carried_bedrock_replacement_is_reported(self):
        before = bedrock_release("v4.1.76")
        replacement = bedrock_release("v4.1.76")
        replacement["assets"][-1]["id"] += 1
        result, _, calls = exercise_shell(before, replacement=replacement)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Retained assets changed", result.stderr)
        self.assertEqual([], deletions(calls))

    def test_observed_release_runs_without_any_deletion(self):
        original = release()
        result, after, calls = exercise_shell(original)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(original, after)
        self.assertEqual([], deletions(calls))
        self.assertIn(["api", "repos/wickidcow/Slimefun-Legacy/releases/412654205"], calls)

    def test_removes_only_planned_legacy_ids_and_preserves_four_assets(self):
        state = release()
        state["assets"].extend([asset("SF_Test1.0.jar", 1), asset("SFL_AddonsNDependencies.zip", 2)])
        result, after, calls = exercise_shell(state)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(release(), after)
        self.assertEqual(["1", "2"], [call[-1].rsplit("/", 1)[-1] for call in deletions(calls)])

    def test_unknown_asset_blocks_all_deletions_including_other_legacy(self):
        state = release()
        state["assets"].extend([asset("SF_Test1.0.jar", 1), asset("unapproved.zip", 2)])
        result, after, calls = exercise_shell(state)
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(state, after)
        self.assertEqual([], deletions(calls))

    def test_wrong_rollback_digest_blocks_before_deletion(self):
        state = release()
        state["assets"][-1]["digest"] = "sha256:" + "0" * 64
        state["assets"].append(asset("SF_Test1.0.jar", 1))
        result, after, calls = exercise_shell(state)
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(state, after)
        self.assertEqual([], deletions(calls))

    def test_missing_core_blocks_before_deletion(self):
        state = release()
        state["assets"].pop(0)
        state["assets"].append(asset("SF_Test1.0.jar", 1))
        result, after, calls = exercise_shell(state)
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(state, after)
        self.assertEqual([], deletions(calls))

    def test_failed_delete_stops_without_touching_retained_assets(self):
        state = release()
        state["assets"].append(asset("SF_Test1.0.jar", 1))
        result, after, calls = exercise_shell(state, fail_delete=True)
        self.assertNotEqual(0, result.returncode)
        self.assertEqual(state, after)
        self.assertEqual(1, len(deletions(calls)))

    def test_external_rollback_disappearance_is_reported(self):
        result, _, calls = exercise_shell(release(), replacement=release(rollback=False))
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Retained assets changed", result.stderr)
        self.assertEqual([], deletions(calls))

    def test_external_core_replacement_is_reported(self):
        replacement = release()
        replacement["assets"][0]["id"] += 1
        result, _, calls = exercise_shell(release(), replacement=replacement)
        self.assertNotEqual(0, result.returncode)
        self.assertEqual([], deletions(calls))

    def test_two_and_three_asset_layouts_still_work(self):
        for pack in (False, True):
            with self.subTest(pack=pack):
                state = release(pack=pack, rollback=False)
                result, after, calls = exercise_shell(state)
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertEqual(state, after)
                self.assertEqual([], deletions(calls))

    def test_workflow_permissions_and_manual_master_authorization_remain_present(self):
        text = WORKFLOW.read_text()
        self.assertIn("permissions:\n  contents: read", text)
        self.assertIn("needs: policy-tests", text)
        self.assertIn("github.event_name == 'workflow_dispatch'", text)
        self.assertIn("inputs.confirm_existing_release_asset_cleanup == true", text)
        self.assertIn("github.event_name != 'pull_request'", text)
        self.assertIn("github.ref == 'refs/heads/master'", text)
        self.assertNotIn("\n  workflow_run:\n", text)
        self.assertNotIn("github.event.workflow_run.", text)
        self.assertIn("cancel-in-progress: false", text)


if __name__ == "__main__":
    unittest.main(verbosity=2)
