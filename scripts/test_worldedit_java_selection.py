"""Selection regression: only a verified Java-ceiling mismatch permits another candidate."""
import copy
import unittest
from unittest.mock import patch
import prepare_worldedit_runtime as runtime
from test_prepare_worldedit_runtime import file_record, jar_bytes, version

class JavaSelectionTests(unittest.TestCase):
    def setUp(self):
        self.new = jar_bytes(major=69)
        self.floor = jar_bytes(major=65)
        self.versions = [dict(version('new', game='1.21.11', date='2026-09-29T00:00:00Z'), files=[file_record(self.new)]),
                         dict(version('floor', game='1.21.11', date='2026-09-01T00:00:00Z'), files=[file_record(self.floor)])]
    def choose(self, rows=None, **kwargs):
        return runtime.select_compatible_artifact(self.versions if rows is None else rows,
                'officialProject', '1.21.11', **kwargs)
    def test_newer_java_candidate_is_recorded_then_verified_floor_selected(self):
        original = copy.deepcopy(self.versions)
        with patch.object(runtime, 'request_bytes', side_effect=[self.new,self.floor]):
            selected, _, payload, report = self.choose()
        self.assertEqual('floor', selected['id']); self.assertEqual(self.floor,payload)
        self.assertEqual('new', report['bytecode_rejected_candidates'][0]['version_id'])
        self.assertIn('Java 25', report['bytecode_rejected_candidates'][0]['reason'])
        self.assertEqual(original,self.versions)
    def test_explicit_incompatible_pin_never_falls_back(self):
        with patch.object(runtime,'request_bytes', return_value=self.new) as fetch:
            with self.assertRaises(runtime.BytecodeCompatibilityError): self.choose(version_id='new')
            self.assertEqual(1,fetch.call_count)
    def test_hash_failure_never_falls_back(self):
        rows=copy.deepcopy(self.versions);rows[0]['files'][0]['hashes']['sha512']='0'*128
        with patch.object(runtime,'request_bytes',return_value=self.new) as fetch:
            with self.assertRaisesRegex(ValueError,'checksum'): self.choose(rows)
            self.assertEqual(1,fetch.call_count)
    def test_network_failure_never_falls_back(self):
        with patch.object(runtime,'request_bytes',side_effect=OSError('offline')) as fetch:
            with self.assertRaises(OSError): self.choose()
            self.assertEqual(1,fetch.call_count)
    def test_foreign_plugin_identity_never_falls_back(self):
        bad=jar_bytes(name='AnotherPlugin',major=69)
        rows=copy.deepcopy(self.versions);rows[0]['files']=[file_record(bad)]
        with patch.object(runtime,'request_bytes',return_value=bad) as fetch:
            with self.assertRaisesRegex(ValueError,'identity'): self.choose(rows)
            self.assertEqual(1,fetch.call_count)
    def test_retry_is_bounded(self):
        with patch.object(runtime,'MAX_CANDIDATES',1),patch.object(runtime,'request_bytes',return_value=self.new):
            with self.assertRaisesRegex(ValueError,'within 1 candidates'): self.choose()
    def test_fallback_still_requires_exact_game_support(self):
        rows=copy.deepcopy(self.versions);rows[1]['game_versions']=['1.21.10']
        with patch.object(runtime,'request_bytes',return_value=self.new) as fetch:
            with self.assertRaisesRegex(ValueError,'explicitly supports'): self.choose(rows)
            self.assertEqual(1,fetch.call_count)
    def test_new_java_lane_keeps_the_newest_verified_artifact(self):
        rows=copy.deepcopy(self.versions)
        for row in rows: row['game_versions']=['26.3']
        with patch.object(runtime,'request_bytes',return_value=self.new) as fetch:
            selected,_,_,report=runtime.select_compatible_artifact(rows,'officialProject','26.3')
        self.assertEqual('new',selected['id']);self.assertEqual([],report['bytecode_rejected_candidates'])
        self.assertEqual(1,fetch.call_count)
    def test_invalid_primary_file_does_not_fall_back(self):
        rows=copy.deepcopy(self.versions);rows[0]['files'][0]['primary']=False
        with patch.object(runtime,'request_bytes') as fetch:
            with self.assertRaises(ValueError): self.choose(rows)
            fetch.assert_not_called()

if __name__ == '__main__': unittest.main(verbosity=2)
