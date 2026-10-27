#!/usr/bin/env python3
"""Guard against reintroducing retired addons into active CI and bundle rosters."""
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
RETIRED = "wickidcow/SF_BuildingStaff"


def load(relative):
    return json.loads((ROOT / relative).read_text(encoding="utf-8"))


class RetiredAddonExclusionTest(unittest.TestCase):
    def test_not_selected_in_live_matrices(self):
        for path in ("compatibility/sfl-addon-release-matrix.json",
                     "compatibility/addon-compatibility-matrix.json"):
            with self.subTest(path=path):
                self.assertNotIn(RETIRED, {row["repository"] for row in load(path)["addons"]})

    def test_recorded_as_intentionally_excluded(self):
        release = load("compatibility/sfl-addon-release-matrix.json")
        coverage = load("compatibility/addon-compatibility-exclusions.json")
        self.assertTrue(release["excluded_from_bundle"].get(RETIRED))
        self.assertIn(RETIRED, {row["repository"] for row in coverage["exclusions"]})

    def test_no_ci_special_case_reintroduces_building_staff(self):
        for path in (".github/workflows/build-sfl-addons-compat-bundle.yml",
                     ".github/workflows/paper-26.3-addon-compile.yml"):
            with self.subTest(path=path):
                workflow = (ROOT / path).read_text(encoding="utf-8").lower()
                self.assertNotIn("buildingstaff", workflow)
                self.assertNotIn("install_rebar_api.sh", workflow)

    def test_curated_docs_do_not_advertise_it(self):
        for path in ("docs/ADDONS.md", "docs/wiki/Addons.md"):
            with self.subTest(path=path):
                document = (ROOT / path).read_text(encoding="utf-8").lower()
                # An explicit retirement notice is not an advertised addon.
                self.assertNotIn("| buildingstaff |", document)
                self.assertNotIn("https://github.com/wickidcow/sf_buildingstaff", document)


if __name__ == "__main__":
    unittest.main()
