import copy
import json
from pathlib import Path
import unittest

from scripts.release_compatibility_policy import NOTICE_261, validate_notice, validate_scope

ROOT = Path(__file__).resolve().parents[1]


class CompatibilityClaimsTest(unittest.TestCase):
    def setUp(self):
        self.matrix = json.loads((ROOT / "compatibility/sfl-addon-release-matrix.json").read_text())

    def test_live_matrix_preserves_runtime_floor_and_separate_advisory(self):
        validate_scope(self.matrix)

    def test_generated_notice_carries_the_same_explicit_scope(self):
        text = (ROOT / ".github/workflows/build-sfl-addons-compat-bundle.yml").read_text()
        start = text.index("cat > bundle/COMPATIBILITY.txt <<EOF")
        end = text.index("\n          EOF", start)
        validate_notice(text[start:end])
        self.assertIn("python3 -m unittest scripts.test_release_compatibility_claims -v", text[:start])

    def test_broad_or_specific_261_runtime_claims_are_rejected(self):
        for version in ("26.1", "26.1.x", "26.1.2"):
            candidate = copy.deepcopy(self.matrix)
            candidate["compatibility"]["minecraft_versions"]["supported"].append(version)
            with self.subTest(version=version), self.assertRaises(ValueError):
                validate_scope(candidate)

    def test_runtime_or_world_claims_cannot_be_inferred_from_compile(self):
        for field in ("runtime_support", "world_migration_or_rollback_verified"):
            for value in (True, "false", None, 0):
                candidate = copy.deepcopy(self.matrix)
                candidate["compatibility"]["minecraft_versions"]["advisory"]["26.1.2"][field] = value
                with self.subTest(field=field, value=value), self.assertRaises(ValueError):
                    validate_scope(candidate)

    def test_missing_advisory_evidence_is_not_accepted(self):
        candidate = copy.deepcopy(self.matrix)
        del candidate["compatibility"]["minecraft_versions"]["advisory"]["26.1.2"]["validation"]
        with self.assertRaises(ValueError):
            validate_scope(candidate)

    def test_lost_runtime_floor_or_primary_line_is_rejected(self):
        for field, value in (("minimum", "1.21.10"), ("primary", "26.1.2")):
            candidate = copy.deepcopy(self.matrix)
            candidate["compatibility"]["minecraft_versions"][field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                validate_scope(candidate)

    def test_mutated_generated_notice_is_rejected(self):
        for text in ("", "- 26.1.x: supported", "- 26.1.2: supported", NOTICE_261 + "\n- 26.1.x: supported"):
            with self.subTest(text=text), self.assertRaises(ValueError):
                validate_notice(text)

    def test_validation_does_not_rewrite_addon_pins_or_manifest(self):
        before = copy.deepcopy(self.matrix)
        validate_scope(self.matrix)
        self.assertEqual(before, self.matrix)


    def test_maintained_build_only_repin_is_exact_and_preserves_native_pairs(self):
        # Source-only candidate: intentionally preserve historical native storage
        # fixtures, immutable published Networks/IE2 source and Supreme 1.0.20.
        self.assertEqual(153, self.matrix["bundle_revision"])
        self.assertEqual(44, len(self.matrix["addons"]))
        selected = {row["repository"]: row["source_commit"]
                    for row in self.matrix["addons"]}
        self.assertEqual(44, len(selected), "Duplicate addon repository")
        expected = {
            "wickidcow/SF_AlchimiaVitae": "5b1f9b2d5191243a18ff29f7601587bd01f31c70",
            "wickidcow/SF_BetterChests": "e80e395ca331bad234fd5733eb106da3b5656545",
            "wickidcow/SF_DankTech2": "d718633ab28c312360e90dd43aeb3a9f25dc3c2e",
            "wickidcow/SF_ElectricSpawners": "d28daba4fa77d7c927fc2716207c837602edd9cc",
            "wickidcow/SF_ExoticGarden": "3bd282d2a951d883d1dc222e53e35e7fc4e0a6f2",
            "wickidcow/SF_FNAmplifications": "017ee6a2008e618201ccb1a114b42aa9a7296be9",
            "wickidcow/SF_Gastronomicon": "31df51f791cccdad03a21df6186d327e58b6a468",
            "wickidcow/SF_GeneticChickEngineering": "5ce3c185c343d128f2b0ba033e908c56cebd5bb0",
            "wickidcow/SF_IDreamOfEasy": "91090c398ece30dbf90ddde6124a7f1faf0f05bb",
            "wickidcow/SF_LuckyBlocks": "9038919c7f8c9c143fec373230ca5847c326729f",
            "wickidcow/SF_MilitaryArsenal": "4f9d366e65be68b106c124f7dade2e78a5569490",
            "wickidcow/SF_SlimeEasy": "40e052a98a484bddd107d9a8564a7fbaf0da0b62",
            "wickidcow/SF_SlimefunAdvancements": "0dc1c98dda87803b9be65d2a97d6c328079473f0",
            "wickidcow/SF_SlimefunWarfare": "2a6ef2801e212720b623d8db3b668be9b9f3f450",
            "wickidcow/SF_SlimeGlue": "c3b024944c6520ef1f4ba51c21f02606fffd39a6",
            "wickidcow/SF_SoulJars": "8fbd6befc477bbbf9970afc79e538c6b1f028cd3",
        }
        for repository, sha in expected.items():
            with self.subTest(repository=repository):
                self.assertEqual(sha, selected[repository])
        self.assertEqual("ea5777ce58c45c3c3cd4789bd155164ccbb5336b",
                         selected["wickidcow/SF_NetworksExp"])
        self.assertEqual("fdadfa67bc68fb18e34ccfed773a23ba2efbd4ef",
                         selected["wickidcow/SF_InfinityExpansion2"])
        self.assertEqual("17693b1eec0ef15f198c86a5803f6e5dbd085074",
                         selected["wickidcow/SF_Supreme"])



if __name__ == "__main__":
    unittest.main()
