import unittest
from scripts.audit_maintained_sources import in_scope, exact_runs


class AuditScopeTest(unittest.TestCase):
    def repo(self, name="SF_Test", **changes):
        repo = {"name": name, "owner": {"login": "wickidcow"}, "private": False, "archived": False}
        repo.update(changes)
        return repo

    def test_only_explicit_public_maintained_scope(self):
        for name in ("SF_NetworksExp", "SF_FinalTECH", "Slimefun-Legacy", "WorldEditSlimefun"):
            self.assertTrue(in_scope(self.repo(name)))
        for name in ("pylon", "rebar", "AlbionMC-Control", "Rebar_VaultWorks", "SF_../escape", "OtherAddon"):
            self.assertFalse(in_scope(self.repo(name)))

    def test_archived_private_foreign_and_missing_metadata_are_rejected(self):
        self.assertFalse(in_scope(self.repo(archived=True)))
        self.assertFalse(in_scope(self.repo(private=True)))
        self.assertFalse(in_scope(self.repo(owner={"login": "other"})))
        self.assertFalse(in_scope({"name": "SF_Test"}))

    def test_historical_failure_does_not_override_current_head(self):
        rows = [{"id": 1, "workflow_id": 9, "head_sha": "old", "conclusion": "failure"},
                {"id": 2, "workflow_id": 9, "head_sha": "new", "conclusion": "success"}]
        self.assertEqual("success", exact_runs(rows, "new")[0]["conclusion"])
        self.assertEqual([], exact_runs(rows, "absent"))

    def test_latest_retry_and_event_are_kept_separate(self):
        rows = [{"id": 1, "workflow_id": 9, "head_sha": "same", "event": "push", "run_attempt": 1, "conclusion": "failure"},
                {"id": 1, "workflow_id": 9, "head_sha": "same", "event": "push", "run_attempt": 2, "conclusion": "success"},
                {"id": 2, "workflow_id": 9, "head_sha": "same", "event": "pull_request", "conclusion": None}]
        results = {r["event"]: r for r in exact_runs(rows, "same")}
        self.assertEqual("success", results["push"]["conclusion"])
        self.assertIsNone(results["pull_request"]["conclusion"])


if __name__ == "__main__":
    unittest.main()
