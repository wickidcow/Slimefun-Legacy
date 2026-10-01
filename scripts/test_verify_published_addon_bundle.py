#!/usr/bin/env python3
"""Synthetic archive/name regressions; not server runtime certification."""
import contextlib
import hashlib
import io
import json
import tempfile
import unittest
import warnings
import zipfile
from pathlib import Path
from verify_published_addon_bundle import main, valid_public_name, validate_bundle


class PublishedBundleTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.path = Path(self.temp.name) / 'addons.zip'
        self.jar = self.make_jar()
        self.name = 'SF_SFWorldEdit1.0.6_(1.21.11-26.3).jar'
        self.manifest = {'compatibility': {'minecraft_versions': {'minimum': '1.21.11', 'primary': '26.3', 'supported': ['1.21.11', '26.2', '26.3']}},
            'addons': [{'repository': 'wickidcow/WorldEditSlimefun', 'jar': self.name, 'sha256': hashlib.sha256(self.jar).hexdigest()}]}

    def make_jar(self, descriptor=True):
        out = io.BytesIO()
        with zipfile.ZipFile(out, 'w') as jar:
            jar.writestr('plugin.yml' if descriptor else 'README.txt', 'name: Fixture\nversion: 1.0.6\n')
        return out.getvalue()

    def write(self, extras=(), data=None):
        with warnings.catch_warnings():
            warnings.simplefilter('ignore', UserWarning)
            with zipfile.ZipFile(self.path, 'w') as archive:
                archive.writestr('SF_ADDON_MANIFEST.json', json.dumps(self.manifest))
                archive.writestr(self.name, self.jar if data is None else data)
                for name, content in extras: archive.writestr(name, content)

    def test_current_worldedit_name_is_accepted(self):
        self.write()
        self.assertEqual(1, validate_bundle(self.path)['addon_count'])

    def test_old_hard_coded_regex_reproduces_rejection(self):
        import re
        self.assertIsNone(re.fullmatch(r'SF_SFWorldEdit\d+(?:\.\d+)+_\(1\.21\.11-26\.2\)\.jar', self.name))
        self.assertTrue(valid_public_name(self.name, '1.21.11', '26.3'))

    def test_old_range_cannot_masquerade_as_current(self):
        self.assertFalse(valid_public_name(self.name.replace('26.3','26.2'), '1.21.11', '26.3'))

    def test_declared_future_target_controls_suffix(self):
        self.assertTrue(valid_public_name(self.name.replace('26.3','26.4'), '1.21.11', '26.4'))
        self.assertFalse(valid_public_name(self.name, '1.21.11', '26.4'))

    def test_standard_and_dracfun_names_stay_accepted(self):
        for name in ['SF_Networks1.0.46.jar','SF_FluffyMachines26.2.12.jar','SFL_DracFun-Reborn2.0.4.jar']:
            self.assertTrue(valid_public_name(name,'1.21.11','26.3'), name)

    def test_paths_and_qualifiers_are_rejected(self):
        for name in ['../SF_Test1.0.jar','nested/SF_Test1.0.jar','SF_Test1.0-SNAPSHOT.jar','SF_Test1.0.jar.zip','SF_SFWorldEdit1.0.6.jar']:
            self.assertFalse(valid_public_name(name,'1.21.11','26.3'), name)

    def test_extra_jar_is_rejected(self):
        self.write([('SF_Extra1.0.jar',self.jar)])
        with self.assertRaisesRegex(ValueError,'set mismatch'): validate_bundle(self.path)

    def test_nested_jar_is_rejected(self):
        self.write([('nested/SF_Extra1.0.jar',self.jar)])
        with self.assertRaisesRegex(ValueError,'set mismatch'): validate_bundle(self.path)

    def test_duplicate_archive_entries_are_rejected(self):
        self.write([(self.name,self.jar)])
        with self.assertRaisesRegex(ValueError,'Duplicate archive'): validate_bundle(self.path)

    def test_duplicate_manifest_records_are_rejected(self):
        self.manifest['addons']*=2; self.write()
        with self.assertRaisesRegex(ValueError,'Duplicate JAR'): validate_bundle(self.path)

    def test_empty_manifest_is_rejected(self):
        self.manifest['addons']=[]; self.write()
        with self.assertRaises(ValueError): validate_bundle(self.path)

    def test_missing_manifest_jar_is_rejected(self):
        self.manifest['addons'][0]['jar']='SF_Other1.0.jar'; self.write()
        with self.assertRaises(KeyError): validate_bundle(self.path)

    def test_checksum_mismatch_is_rejected(self):
        self.manifest['addons'][0]['sha256']='0'*64; self.write()
        with self.assertRaisesRegex(ValueError,'checksum mismatch'): validate_bundle(self.path)

    def test_missing_checksum_is_rejected(self):
        self.manifest['addons'][0].pop('sha256'); self.write()
        with self.assertRaisesRegex(ValueError,'SHA-256'): validate_bundle(self.path)

    def test_matching_hash_does_not_make_nonjar_installable(self):
        self.jar=b'not a plugin'; self.manifest['addons'][0]['sha256']=hashlib.sha256(self.jar).hexdigest(); self.write()
        with self.assertRaises(zipfile.BadZipFile): validate_bundle(self.path)

    def test_descriptor_is_required(self):
        self.jar=self.make_jar(False); self.manifest['addons'][0]['sha256']=hashlib.sha256(self.jar).hexdigest(); self.write()
        with self.assertRaisesRegex(ValueError,'descriptor'): validate_bundle(self.path)

    def test_unsafe_outer_path_is_rejected_without_extraction(self):
        self.write([('../escape.txt',b'bad')])
        with self.assertRaisesRegex(ValueError,'Unsafe'): validate_bundle(self.path)
        self.assertFalse((self.path.parent.parent/'escape.txt').exists())

    def test_primary_must_belong_to_supported_versions(self):
        self.manifest['compatibility']['minecraft_versions']['primary']='26.4'; self.write()
        with self.assertRaisesRegex(ValueError,'compatibility'): validate_bundle(self.path)

    def test_input_remains_byte_identical(self):
        self.write(); before=self.path.read_bytes(); validate_bundle(self.path)
        self.assertEqual(before,self.path.read_bytes())

    def test_cli_returns_failure_for_missing_input(self):
        with contextlib.redirect_stderr(io.StringIO()): self.assertEqual(1,main([str(self.path)]))


if __name__ == '__main__':
    unittest.main()
