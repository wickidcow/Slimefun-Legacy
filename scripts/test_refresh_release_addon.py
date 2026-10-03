"""Archive refresh guard tests; fixtures are not real plugin runtime tests."""
import hashlib
import io
import json
import tempfile
import unittest
import zipfile
from pathlib import Path

from refresh_release_addon import refresh


class RefreshReleaseAddonTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.original, self.addon, self.output = [self.root / n for n in ('old.zip', 'new.jar', 'updated.zip')]
        self.old_name, self.new_name, self.other_name = 'SF_MagicExpansion1.1.7.jar', 'SF_MagicExpansion1.1.8.jar', 'SF_Other1.0.jar'
        self.addon.write_bytes(self.jar('1.1.8'))
        self.config = dict(old_bundle_sha256='', new_sha256=self.sha(self.addon.read_bytes()),
                           core_source_commit='a' * 40, bundle_revision=108, expected_addons=2,
                           repository='wickidcow/SF_MagicExpansion', old_jar=self.old_name,
                           new_jar=self.new_name, old_commit='b' * 40, new_commit='c' * 40,
                           new_version='1.1.8', new_distributable_api='1.21.11-R0.1-SNAPSHOT',
                           new_build_core_version='4.1.61', new_paper_26_3_api='26.3-test',
                           release_tag='v4.1.67', compatibility_note='Explicit validation scope.')
        self.members = {self.old_name: self.jar('1.1.7'), self.other_name: self.jar('1.0'),
                        'COMPATIBILITY.txt': b'Existing notes.\n'}
        self.manifest = dict(core_source_commit='a' * 40,
                            compatibility={'minecraft_versions': {'minimum': '1.21.11', 'primary': '26.3',
                                                                   'supported': ['1.21.11', '26.3']}},
                            addons=[dict(repository='wickidcow/SF_MagicExpansion', commit='b' * 40,
                                         jar=self.old_name, sha256=self.sha(self.members[self.old_name])),
                                    dict(repository='wickidcow/SF_Other', commit='d' * 40,
                                         jar=self.other_name, sha256=self.sha(self.members[self.other_name]))])
        self.matrix = dict(bundle_revision=108, addons=[dict(repository=r['repository'], source_commit=c)
                           for r, c in zip(self.manifest['addons'], ['c' * 40, 'd' * 40])])
        self.write_original()

    @staticmethod
    def sha(data):
        return hashlib.sha256(data).hexdigest()

    @staticmethod
    def jar(version):
        out = io.BytesIO()
        with zipfile.ZipFile(out, 'w') as z:
            z.writestr('plugin.yml', f'name: Fixture\nversion: {version}\n')
            z.writestr('Fixture.class', bytes.fromhex('cafebabe00000041'))
        return out.getvalue()

    def write_original(self):
        self.members['SF_ADDON_MANIFEST.json'] = json.dumps(self.manifest).encode()
        self.members['SHA256SUMS.txt'] = ''.join(f'{self.sha(b)}  {n}\n' for n, b in self.members.items()
                                                 if n.endswith('.jar')).encode()
        with zipfile.ZipFile(self.original, 'w') as z:
            for name, data in self.members.items():
                z.writestr(name, data)
        self.config['old_bundle_sha256'] = self.sha(self.original.read_bytes())

    def run_refresh(self):
        return refresh(self.config, self.matrix, self.original, self.addon, self.output)

    def test_refresh_is_reproducible_and_preserves_unrelated_bytes_and_metadata(self):
        original = self.original.read_bytes()
        result = self.run_refresh()
        first = self.output.read_bytes()
        self.run_refresh()
        self.assertEqual(first, self.output.read_bytes())
        self.assertEqual(original, self.original.read_bytes())
        self.assertEqual(1, result['unchanged_addons'])
        with zipfile.ZipFile(self.output) as z:
            self.assertNotIn(self.old_name, z.namelist())
            self.assertEqual(self.addon.read_bytes(), z.read(self.new_name))
            self.assertEqual(self.members[self.other_name], z.read(self.other_name))
            m = json.loads(z.read('SF_ADDON_MANIFEST.json'))
            self.assertEqual(self.manifest['addons'][1], m['addons'][1])
            self.assertEqual('a' * 40, m['core_source_commit'])

    def test_changed_published_zip_is_rejected_before_output(self):
        self.config['old_bundle_sha256'] = '0' * 64
        with self.assertRaisesRegex(ValueError, 'bundle changed'):
            self.run_refresh()
        self.assertFalse(self.output.exists())

    def test_changed_addon_is_rejected(self):
        self.addon.write_bytes(self.addon.read_bytes() + b'changed')
        with self.assertRaisesRegex(ValueError, 'addon checksum'):
            self.run_refresh()

    def test_unrelated_source_pin_change_is_rejected(self):
        self.matrix['addons'][1]['source_commit'] = 'e' * 40
        with self.assertRaisesRegex(ValueError, 'Other source pins'):
            self.run_refresh()

    def test_old_addon_identity_change_is_rejected(self):
        self.manifest['addons'][0]['commit'] = 'e' * 40
        self.write_original()
        with self.assertRaisesRegex(ValueError, 'Old addon identity'):
            self.run_refresh()

    def test_descriptor_version_mismatch_is_rejected_even_with_matching_checksum(self):
        self.addon.write_bytes(self.jar('1.1.9'))
        self.config['new_sha256'] = self.sha(self.addon.read_bytes())
        with self.assertRaisesRegex(ValueError, 'version mismatch'):
            self.run_refresh()

    def test_wrong_core_provenance_is_rejected(self):
        self.config['core_source_commit'] = 'f' * 40
        with self.assertRaisesRegex(ValueError, 'Core provenance'):
            self.run_refresh()

    def test_input_cannot_be_overwritten(self):
        self.output = self.original
        with self.assertRaisesRegex(ValueError, 'must not overwrite'):
            self.run_refresh()


if __name__ == '__main__':
    unittest.main()
