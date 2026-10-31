"""Release contract: Slimefun Legacy must not handle Geyser/Bedrock assets."""
from pathlib import Path
import re
import unittest

from scripts.test_release_asset_immutability import ReleaseAssetImmutability

ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT / ".github/workflows/reproducible-release.yml"


class JavaOnlyReleaseScope(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.text = WORKFLOW.read_text(encoding="utf-8")

    def test_published_v4178_guidance_cannot_revert_to_candidate_status(self):
        guidance = (ROOT / "AGENTS.md").read_text(encoding="utf-8")
        self.assertIn(
            "Slimefun Legacy 4.1.78 and its coordinated 44-addon archive were published on 2026-10-27",
            guidance,
        )
        self.assertIn("That published release and all its assets are immutable", guidance)
        self.assertIn("Prepare only a **distinct new version**", guidance)
        self.assertNotRegex(guidance, r"4\\.1\\.78 is [^\\n.]*candidate")

    def test_no_external_platform_pack_handlers(self):
        self.assertNotRegex(self.text.lower(), r"bedrock|geyser")
        self.assertNotIn("--clobber", self.text)
        # The legacy script runner appends its source-root path as an argument.
        # unittest.main() interprets that argument as a test selector, not a
        # source directory. Keep this unittest in explicit module-style CI
        # invocations so it receives no unrelated positional arguments.
        active_verifier = (ROOT / "scripts/verify_legacy.py").read_text(encoding="utf-8")
        self.assertNotIn('"test_java_only_release_scope.py",', active_verifier)
        build_ci = (ROOT / ".github/workflows/build-ci.yml").read_text(encoding="utf-8")
        self.assertIn("python3 -m unittest scripts.test_java_only_release_scope -v", build_ci)
        self.assertIn("python3 -m unittest scripts.test_java_only_release_scope -v", self.text)
        self.assertNotIn('"test_optional_bedrock_release_workflow.py"', active_verifier)
        self.assertNotIn('"test_verify_release_bedrock_pack.py"', active_verifier)

    def test_three_java_release_assets_only(self):
        self.assertIn('gh release create "$TAG" "$JAR" "$BUNDLE" "$RESOURCE_PACK"', self.text)
        self.assertIn('if [[ "$ASSET_COUNT" -ne 3 ]]; then', self.text)
        self.assertIn('Canonical addon bundle', self.text)

    def test_publish_requires_current_source_and_native_evidence(self):
        self.assertIn('steps.runtime_evidence.outcome == \'success\'', self.text)
        self.assertIn('require_complete native-entity-retirement.yml push', self.text)
        self.assertIn('require_complete paper-26.3-full-stack.yml workflow_run', self.text)
        self.assertIn('test "$GITHUB_SHA" = "$(gh api', self.text)

    def test_published_bytes_are_verified_and_prior_release_unchanged(self):
        self.assertIn('gh release download "$TAG"', self.text)
        self.assertIn('Published Java release digest mismatch', self.text)
        self.assertIn('Published Java release bytes changed', self.text)
        self.assertIn('Release $TAG already exists at identical source', self.text)
        self.assertIn('if [[ "$ASSET_COUNT" -ne 3 ]]; then', self.text)
        self.assertNotIn('OPTIONAL_', self.text)
        java_pack_gate = (ROOT / 'scripts/verify_release_resource_pack.py').read_text(encoding='utf-8')
        self.assertIn('release layout requires three Java assets', java_pack_gate)
        self.assertIn('if [[ "$ASSET_COUNT" -ne 3 ]]', java_pack_gate)

    def test_release_verification_cannot_modify_published_assets(self):
        self.assertNotRegex(self.text, r"gh\s+release\s+(?:delete(?:-asset)?|edit|upload)\b")
        self.assertIn("name: Verify release assets without mutation", self.text)


    def test_both_clean_release_jars_check_complete_java21_package(self):
        checker = (ROOT / "scripts/check_bytecode_target.py").read_text(encoding="utf-8")
        self.assertIn('parser.add_argument("--all-classes"', checker)
        for stage in ("first", "second"):
            jar = f'"$RUNNER_TEMP/Slimefun-{stage}.jar"'
            owned = (
                f'python3 scripts/check_bytecode_target.py {jar} --expected-java 21 '
                f'--report "$RUNNER_TEMP/bytecode-{stage}.txt"'
            )
            packaged = (
                f'python3 scripts/check_bytecode_target.py {jar} --expected-java 21 --all-classes '
                f'--report "$RUNNER_TEMP/bytecode-{stage}-packaged.txt"'
            )
            self.assertEqual(1, self.text.count(owned), f"Missing owned {stage} check")
            self.assertEqual(1, self.text.count(packaged), f"Missing packaged {stage} check")
        self.assertIn(
            'cp "$RUNNER_TEMP/bytecode-first-packaged.txt" '
            '"$RUNNER_TEMP/bytecode-second-packaged.txt" build/release-verification/',
            self.text,
        )
        self.assertIn("name: reproducible-release-validation", self.text)
        self.assertIn("path: build/release-verification/", self.text)


if __name__ == "__main__":
    unittest.main()
