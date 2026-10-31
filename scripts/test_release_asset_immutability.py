"""Execute the real release shell steps against a local, stateful GitHub stub.

No network, token, real Git tag, or real release is used by these regressions.
"""
from pathlib import Path
import hashlib
import json
import os
import re
import subprocess
import tempfile
import textwrap
import unittest

ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT / ".github/workflows/reproducible-release.yml"

GH_STUB = r'''#!/usr/bin/env python3
import json
import os
from pathlib import Path
import shutil
import sys

root = Path(os.environ["FIXTURE_ROOT"])
args = sys.argv[1:]
with (root / "gh-calls.jsonl").open("a", encoding="utf-8") as out:
    out.write(json.dumps(args) + "\n")
state_path = root / "remote.json"
state = json.loads(state_path.read_text(encoding="utf-8"))
if args[:2] == ["release", "view"]:
    if "--json" in args:
        print("\n".join(asset["name"] for asset in state["assets"]))
    else:
        print("Existing immutable release")
elif args[:2] == ["release", "delete-asset"]:
    # Model the old destructive behavior locally so the regression proves
    # preservation, rather than merely refusing to simulate the bug.
    state["assets"] = [asset for asset in state["assets"] if asset["name"] != args[3]]
    state_path.write_text(json.dumps(state), encoding="utf-8")
    (root / "remote" / args[3]).unlink()
elif args[:2] == ["release", "download"]:
    name = args[args.index("--pattern") + 1]
    destination = Path(args[args.index("--dir") + 1])
    shutil.copyfile(root / "remote" / name, destination / name)
elif args and args[0] == "api":
    if args[1].endswith("/git/ref/heads/master"):
        print(os.environ["GITHUB_SHA"])
    else:
        print(json.dumps(state))
else:
    sys.exit("Unexpected GitHub command in read-only fixture: " + repr(args))
'''


class ReleaseAssetImmutability(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = WORKFLOW.read_text(encoding="utf-8")

    @classmethod
    def shell_step(cls, name):
        marker = "      - name: " + name + "\n"
        if marker not in cls.workflow:
            raise AssertionError("Missing actual workflow step: " + name)
        block = cls.workflow.split(marker, 1)[1]
        block = re.split(r"^      - name: ", block, maxsplit=1, flags=re.MULTILINE)[0]
        return textwrap.dedent(block.split("        run: |\n", 1)[1])

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        for directory in ("bin", "dist", "remote", "tmp", "docs/releases"):
            (self.root / directory).mkdir(parents=True)
        self.env = {
            **os.environ,
            "PATH": str(self.root / "bin") + os.pathsep + os.environ["PATH"],
            "FIXTURE_ROOT": str(self.root),
            "RUNNER_TEMP": str(self.root / "tmp"),
            "GITHUB_STEP_SUMMARY": str(self.root / "summary.md"),
            "GITHUB_EVENT_NAME": "workflow_dispatch",
            "GITHUB_REF": "refs/heads/master",
            "GITHUB_SHA": "a" * 40,
            "GITHUB_REPOSITORY": "fixture/not-a-real-repository",
            "RELEASE_APPROVED": "true",
            "LEGACY_ARTIFACT_VERSION": "4.1.78",
            "OUTPUT_NAME": "Slimefun-Legacy4.1.78.jar",
            "CANONICAL_ADDON_BUNDLE": "SF_Addons_1.21.11-26.3.zip",
            "CANONICAL_RESOURCE_PACK": "SlimefunLegacyRP.zip",
            "CANONICAL_GEYSER_PACK": "SlimefunLegacyRP_Geyser.zip",
            "GH_TOKEN": "fixture-only-no-network",
        }
        (self.root / "docs/releases/4.1.78.md").write_text("Fixture notes", encoding="utf-8")
        for command, content in {
            "gh": GH_STUB,
            "git": '#!/bin/sh\nprintf "%s\\n" "$GITHUB_SHA"\n',
        }.items():
            path = self.root / "bin" / command
            path.write_text(content, encoding="utf-8")
            path.chmod(0o755)
        self.state = {"assets": []}
        for key in ("OUTPUT_NAME", "CANONICAL_ADDON_BUNDLE", "CANONICAL_RESOURCE_PACK", "CANONICAL_GEYSER_PACK"):
            name = self.env[key]
            data = ("immutable fixture: " + name).encode("utf-8")
            (self.root / "dist" / name).write_bytes(data)
            self.add_asset(name, data)

    def add_asset(self, name, data):
        (self.root / "remote" / name).write_bytes(data)
        self.state["assets"].append({"name": name, "digest": "sha256:" + hashlib.sha256(data).hexdigest()})
        self.write_state()

    def write_state(self):
        (self.root / "remote.json").write_text(json.dumps(self.state), encoding="utf-8")

    def remote_snapshot(self):
        return {
            path.name: path.read_bytes()
            for path in (self.root / "remote").iterdir()
        }, (self.root / "remote.json").read_bytes()

    def run_step(self, name):
        return subprocess.run(
            ["bash", "-c", self.shell_step(name)],
            cwd=self.root, env=self.env, text=True, capture_output=True, timeout=20,
        )

    def verify_assets(self):
        # Accept the historical name only so this same test can be run against
        # the pre-fix workflow to demonstrate the destructive regression.
        name = "Verify release assets without mutation"
        if "      - name: " + name + "\n" not in self.workflow:
            name = "Normalize and verify release assets"
        return self.run_step(name)

    def assert_read_only(self, snapshot):
        self.assertEqual(snapshot, self.remote_snapshot(), "Published asset bytes or metadata changed")
        calls = [json.loads(line) for line in (self.root / "gh-calls.jsonl").read_text().splitlines()]
        for call in calls:
            self.assertIn(call[:2], [["release", "view"], ["release", "download"], ["api", "repos/fixture/not-a-real-repository/releases/tags/v4.1.78"], ["api", "repos/fixture/not-a-real-repository/git/ref/heads/master"]])

    def test_existing_release_and_obsolete_assets_are_untouched(self):
        self.add_asset("SFL_AddonsNDependencies.zip", b"original historical archive")
        self.add_asset("SF_Existing.jar", b"original published addon")
        before = self.remote_snapshot()
        publish = self.run_step("Publish exact validated Java-only core release")
        self.assertEqual(0, publish.returncode, publish.stdout + publish.stderr)
        self.assertIn("already exists at identical source", publish.stdout)
        result = self.verify_assets()
        self.assert_read_only(before)
        self.assertNotEqual(0, result.returncode, "Unexpected existing asset layout must fail without repair")

    def test_expected_four_assets_pass_without_mutation(self):
        before = self.remote_snapshot()
        result = self.verify_assets()
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assert_read_only(before)
        self.assertEqual(4, len(list((self.root / "verify-release-bytes").iterdir())))

    def test_extra_custom_asset_fails_without_mutation(self):
        self.add_asset("operator-notes.txt", b"keep this exact published file")
        before = self.remote_snapshot()
        result = self.verify_assets()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Expected exactly four release assets", result.stderr)
        self.assert_read_only(before)

    def test_missing_asset_fails_without_mutation(self):
        missing = self.state["assets"].pop()
        (self.root / "remote" / missing["name"]).unlink()
        self.write_state()
        before = self.remote_snapshot()
        result = self.verify_assets()
        self.assertNotEqual(0, result.returncode)
        self.assert_read_only(before)

    def test_wrong_digest_fails_without_mutation(self):
        self.state["assets"][0]["digest"] = "sha256:" + "0" * 64
        self.write_state()
        before = self.remote_snapshot()
        result = self.verify_assets()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Published Java release digest mismatch", result.stderr)
        self.assert_read_only(before)

    def test_wrong_download_bytes_fail_without_mutation(self):
        (self.root / "remote" / self.env["OUTPUT_NAME"]).write_bytes(b"different download bytes")
        before = self.remote_snapshot()
        result = self.verify_assets()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Published Java release bytes changed", result.stderr)
        self.assert_read_only(before)


if __name__ == "__main__":
    unittest.main()
