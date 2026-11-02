import os
import unittest
from unittest.mock import patch

from scripts.release_cleanup_authorization import main, require_authorization


class ReleaseCleanupAuthorizationTest(unittest.TestCase):
    def test_explicit_confirmed_master_dispatch_is_accepted(self):
        require_authorization("workflow_dispatch", "refs/heads/master", "true")

    def test_automatic_events_are_rejected_even_with_confirmation(self):
        for event in ("push", "pull_request", "pull_request_target", "workflow_run", "schedule", "release", ""):
            with self.subTest(event=event), self.assertRaises(ValueError):
                require_authorization(event, "refs/heads/master", "true")

    def test_non_master_refs_are_rejected(self):
        for ref in ("refs/heads/main", "refs/heads/feature", "refs/tags/v4.1.78", "master", ""):
            with self.subTest(ref=ref), self.assertRaises(ValueError):
                require_authorization("workflow_dispatch", ref, "true")

    def test_confirmation_is_fail_closed(self):
        for value in ("false", "", "True", "TRUE", "1", "yes", " true", "true\n", True, None):
            with self.subTest(value=value), self.assertRaises(ValueError):
                require_authorization("workflow_dispatch", "refs/heads/master", value)

    def test_missing_environment_cannot_authorize_cleanup(self):
        with patch.dict(os.environ, {}, clear=True), self.assertRaises(SystemExit):
            main()

    def test_entrypoint_rejects_automatic_successful_bundle_event(self):
        with patch.dict(os.environ, {
            "GITHUB_EVENT_NAME": "workflow_run", "GITHUB_REF": "refs/heads/master",
            "CONFIRM_RELEASE_CLEANUP": "true",
        }, clear=True), self.assertRaises(SystemExit):
            main()


if __name__ == "__main__":
    unittest.main()
