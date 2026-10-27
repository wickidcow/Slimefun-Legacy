#!/usr/bin/env python3
"""Offline, negative-first tests of the unchanged v4.1.77 Geyser ZIP carry."""
import copy
import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parent))
from verify_sfl_release_assets import CARRIED_GEYSER_PACK, CARRIED_GEYSER_PIN, PolicyError, plan_release  # noqa: E402
from verify_v4178_bedrock_carry import CORE_ASSETS, TARGET_SOURCE, verify_releases  # noqa: E402


def asset(name, identity, size, digest):
    return {"id": identity, "name": name, "size": size, "digest": digest, "state": "uploaded"}


def fixtures(present=False):
    pin = CARRIED_GEYSER_PIN
    original = asset(CARRIED_GEYSER_PACK, pin["source_asset_id"], pin["size"], pin["digest"])
    source = {
        "id": pin["source_release_id"], "tag_name": pin["source_tag"],
        "draft": False, "prerelease": False, "immutable": False,
        "assets": [
            asset("Slimefun-Legacy4.1.77.jar", 668013023, 8684878, "sha256:" + "a" * 64),
            asset("SF_Addons_1.21.11-26.3.zip", 669524371, 28225404, "sha256:" + "b" * 64),
            asset("SlimefunLegacyRP.zip", 668013021, 9143959, "sha256:" + "c" * 64),
            original,
        ],
    }
    target = {
        "id": pin["target_release_id"], "tag_name": pin["target_tag"],
        "target_commitish": TARGET_SOURCE,
        "draft": False, "prerelease": False, "immutable": False,
        "assets": [asset(name, *values) for name, values in CORE_ASSETS.items()],
    }
    if present:
        target["assets"].append(asset(CARRIED_GEYSER_PACK, 800000078, pin["size"], pin["digest"]))
    latest = copy.deepcopy(target)
    return source, target, latest


class CarryForwardTests(unittest.TestCase):
    def test_missing_optional_pack_is_a_valid_read_only_preflight(self):
        s, t, l = fixtures()
        report = verify_releases(s, t, l)
        self.assertFalse(report["already_present"])
        self.assertIsNone(report["target_asset_id"])
        self.assertEqual(3, len(report["unchanged_java_assets"]))

    def test_exact_four_asset_copy_is_verified_without_mutation(self):
        s, t, l = fixtures(True)
        before = copy.deepcopy((s, t, l))
        report = verify_releases(s, t, l, require_present=True)
        self.assertTrue(report["already_present"])
        self.assertEqual(800000078, report["target_asset_id"])
        self.assertEqual(before, (s, t, l))
        self.assertIn(CARRIED_GEYSER_PACK, plan_release(t)["retained"])
        self.assertIn(CARRIED_GEYSER_PACK, plan_release(s)["retained"])

    def test_post_upload_must_have_exact_pack(self):
        s, t, l = fixtures()
        with self.assertRaises(PolicyError):
            verify_releases(s, t, l, require_present=True)

    def test_wrong_pack_digest_size_or_identity_fails(self):
        for field, value in (
            ("digest", "sha256:" + "f" * 64), ("size", 1),
            ("id", CARRIED_GEYSER_PIN["source_asset_id"]),
        ):
            s, t, l = fixtures(True)
            t["assets"][-1][field] = value
            with self.subTest(field=field), self.assertRaises(PolicyError):
                verify_releases(s, t, l, require_present=True)

    def test_source_asset_must_be_original_and_pinned(self):
        for field, value in (("id", 7), ("size", 3), ("digest", "sha256:" + "f" * 64)):
            s, t, l = fixtures()
            s["assets"][-1][field] = value
            with self.subTest(field=field), self.assertRaises(PolicyError):
                verify_releases(s, t, l)

    def test_existing_three_java_assets_must_never_change(self):
        for field, value in (("id", 9), ("size", 1), ("digest", "sha256:" + "c" * 64)):
            s, t, l = fixtures()
            t["assets"][0][field] = value
            with self.subTest(field=field), self.assertRaises(PolicyError):
                verify_releases(s, t, l)

    def test_only_the_exact_latest_4178_release_is_mutable(self):
        for key, value in (("tag_name", "v4.1.79"), ("id", 12), ("target_commitish", "0" * 40)):
            s, t, l = fixtures()
            t[key] = value
            with self.subTest(field=key), self.assertRaises(PolicyError):
                verify_releases(s, t, l)
        s, t, l = fixtures()
        l["tag_name"] = "v4.1.79"
        with self.assertRaises(PolicyError):
            verify_releases(s, t, l)

    def test_unknown_fourth_asset_still_blocks_cleanup_and_restore(self):
        s, t, l = fixtures()
        t["assets"].append(asset("not-the-original-bedrock.zip", 999, 50, "sha256:" + "d" * 64))
        with self.assertRaises(PolicyError):
            verify_releases(s, t, l)
        with self.assertRaises(PolicyError):
            plan_release(t)

    def test_cannot_preserve_pack_on_any_other_release(self):
        s, t, l = fixtures(True)
        t["tag_name"] = "v4.1.79"
        with self.assertRaises(PolicyError):
            plan_release(t)

    def test_immutable_release_rejects_upload(self):
        s, t, l = fixtures()
        t["immutable"] = True
        with self.assertRaises(PolicyError):
            verify_releases(s, t, l)


if __name__ == "__main__":
    unittest.main()
