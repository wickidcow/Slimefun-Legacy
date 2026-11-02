"""Release-policy regressions for optional unchanged ZIP carry-forward.

No Geyser implementation or Bedrock protocol handling is added. The archive
must stay byte-identical to its reviewed original and historical release
assets must remain immutable.
"""
import unittest

from scripts.verify_sfl_release_assets import (
    BUNDLE,
    CARRIED_GEYSER_PACK,
    CARRIED_GEYSER_PIN,
    PACK,
    PolicyError,
    plan_release,
)


def _asset(name, identity, *, size=200, digest="sha256:" + "a" * 64):
    return {
        "id": identity,
        "name": name,
        "size": size,
        "digest": digest,
        "state": "uploaded",
    }


def _release(tag="v4.1.79", release_id=500000079, pack_id=700000079):
    pin = CARRIED_GEYSER_PIN
    return {
        "id": release_id,
        "tag_name": tag,
        "draft": False,
        "prerelease": False,
        "immutable": False,
        "assets": [
            _asset("Slimefun-Legacy" + tag[1:] + ".jar", 700000001),
            _asset(BUNDLE, 700000002),
            _asset(PACK, 700000003),
            _asset(
                CARRIED_GEYSER_PACK, pack_id,
                size=pin["size"], digest=pin["digest"],
            ),
        ],
    }


class NextReleaseCarryPolicy(unittest.TestCase):
    def test_next_release_preserves_four_distinct_release_assets(self):
        report = plan_release(_release())
        self.assertEqual("v4.1.79", report["tag"])
        self.assertEqual(
            {"Slimefun-Legacy4.1.79.jar", BUNDLE, PACK, CARRIED_GEYSER_PACK},
            set(report["retained"]),
        )
        self.assertEqual([], report["remove"])

    def test_published_prior_release_identity_remains_allowed_unchanged(self):
        report = plan_release(_release(
            "v4.1.78",
            CARRIED_GEYSER_PIN["target_release_id"],
            CARRIED_GEYSER_PIN["target_asset_id"],
        ))
        self.assertIn(CARRIED_GEYSER_PACK, report["retained"])

    def test_unreviewed_future_tag_is_not_implicitly_authorized(self):
        with self.assertRaisesRegex(PolicyError, "approved only"):
            plan_release(_release("v4.1.80"))

    def test_changed_archive_digest_is_rejected(self):
        candidate = _release()
        candidate["assets"][-1]["digest"] = "sha256:" + "0" * 64
        with self.assertRaisesRegex(PolicyError, "unchanged"):
            plan_release(candidate)

    def test_changed_archive_size_is_rejected(self):
        candidate = _release()
        candidate["assets"][-1]["size"] += 1
        with self.assertRaisesRegex(PolicyError, "unchanged"):
            plan_release(candidate)

    def test_source_and_previous_release_ids_cannot_be_reused(self):
        for release_id, pack_id in (
            (CARRIED_GEYSER_PIN["target_release_id"], 700000079),
            (500000079, CARRIED_GEYSER_PIN["target_asset_id"]),
            (CARRIED_GEYSER_PIN["source_release_id"], 700000079),
            (500000079, CARRIED_GEYSER_PIN["source_asset_id"]),
        ):
            with self.subTest(release_id=release_id, pack_id=pack_id):
                with self.assertRaisesRegex(PolicyError, "distinct"):
                    plan_release(_release(release_id=release_id, pack_id=pack_id))


if __name__ == "__main__":
    unittest.main()
