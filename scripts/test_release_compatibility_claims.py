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


    def test_maintained_repin_is_exact_and_preserves_native_pairs(self):
        # Reviewed r156 source selection: preserve historical native storage
        # fixtures and immutable published Networks/IE2 artifact locks. The
        # candidate matrix intentionally advances beyond those release sources.
        self.assertEqual(156, self.matrix["bundle_revision"])
        self.assertEqual(44, len(self.matrix["addons"]))
        selected = {row["repository"]: row["source_commit"]
                    for row in self.matrix["addons"]}
        self.assertEqual(44, len(selected), "Duplicate addon repository")
        expected = {
            "wickidcow/SF_AlchimiaVitae": "f684b1c0b62cd4118db8eb3a536a042d5007c284",
            "wickidcow/SF_BetterChests": "e80e395ca331bad234fd5733eb106da3b5656545",
            "wickidcow/SF_CultivationLegacy": "ab97e9d1a89a57bfca3801fce7cef75020fd9bf0",
            "wickidcow/SF_HotbarPets": "54c802438a6819e6f3b0eea411e675a3eb0855dc",
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
            "wickidcow/SF_SlimeHUD": "cb3479646fe8be343cbb55f122cf0e45b1cbc201",
        }
        for repository, sha in expected.items():
            with self.subTest(repository=repository):
                self.assertEqual(sha, selected[repository])
        self.assertEqual("1d46a1afd04ceae7f04c7e0849397350ce273ec5",
                         selected["wickidcow/SF_NetworksExp"])
        self.assertEqual("1cc5d9962ea7d11e282240ef29a741e681a1fbd4",
                         selected["wickidcow/SF_InfinityExpansion2"])
        self.assertEqual("f03ed0c75f1df16aa484d25bf3a1b8bcd20d9b7c",
                         selected["wickidcow/SF_Supreme"])



    def test_r156_retains_previous_merged_release_sources(self):
        # FlowerPower / ExtraHeads keep existing reviewed source identities.
        # The six r155 addon pins point to reviewed, published commits,
        # never draft commits. Published/historical storage locks are separate.
        selected = {row["repository"]: row["source_commit"]
                    for row in self.matrix["addons"]}
        self.assertEqual(44, len(selected))
        self.assertEqual(156, self.matrix["bundle_revision"])
        expected = {
            "wickidcow/SF_CultivationLegacy": "ab97e9d1a89a57bfca3801fce7cef75020fd9bf0",
            "wickidcow/SF_HotbarPets": "54c802438a6819e6f3b0eea411e675a3eb0855dc",
            "wickidcow/SF_FlowerPower": "d8c3f2c8ec66b7c08e0ceb8b23f710f020b5c36f",
            "wickidcow/SF_ExtraHeads": "b1e8a1f11692b44d359eec863f3be5be0f061516",
            "wickidcow/SF_FluffyMachines": "c2646d6ba232e71ce8c4639e414b78a4529790c1",
            "wickidcow/SF_NetworksExp": "1d46a1afd04ceae7f04c7e0849397350ce273ec5",
            "wickidcow/SF_InfinityExpansion2": "1cc5d9962ea7d11e282240ef29a741e681a1fbd4",
            "wickidcow/SF_Supreme": "f03ed0c75f1df16aa484d25bf3a1b8bcd20d9b7c",
        }
        for repository, sha in expected.items():
            with self.subTest(repository=repository):
                self.assertEqual(sha, selected[repository])
        # r156 selects the immutable official v1.0.3/v1.0.8 Git tag commits,
        # not the earlier postmerge pre-release source or mutable master.
        # Native bundle byte receipts and release asset digest acceptance stay gated.
        self.assertEqual("0eb00ea7516be822a3b50b9872400d9892996b8e",
                         selected["wickidcow/SF_CrystamaeHistoria"])
        self.assertEqual("1fff7080cb8bcd00eeb1555f433cc0bf8e99c172",
                         selected["wickidcow/SF_Galactifun"])
        self.assertNotIn("wickidcow/SF_BuildingStaff", selected)



if __name__ == "__main__":
    unittest.main()
