"""Regression contracts for privileged workflow_run checkout admission.

A workflow_run executes with the base repository's token and cache scope.
Never check out code from a pull_request-triggered upstream run in that context.
Direct pull_request workflows continue to test proposed source independently.
"""

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
AUDIT = ROOT / ".github/workflows/addon-compatibility-audit.yml"
FULL_STACK = ROOT / ".github/workflows/paper-26.3-full-stack.yml"


def job_condition(source: str, job: str) -> str:
    marker = f"  {job}:\n"
    start = source.index(marker) + len(marker)
    remaining = source[start:]
    next_job = re.search(r"(?m)^  [a-z][a-z0-9-]*:\s*$", remaining)
    section = remaining[:next_job.start()] if next_job else remaining
    return section.split("    runs-on:", 1)[0]


class WorkflowRunTrustBoundaryTest(unittest.TestCase):
    def test_privileged_jobs_accept_only_same_repository_push_runs(self):
        for path, jobs in (
            (AUDIT, ("audit-completed-run",)),
            (FULL_STACK, ("resolve-paper-26-3", "fetch-bundle")),
        ):
            source = path.read_text(encoding="utf-8")
            for job in jobs:
                with self.subTest(workflow=path.name, job=job):
                    condition = job_condition(source, job)
                    self.assertIn("github.event.workflow_run.event == 'push'", condition)
                    self.assertIn(
                        "github.event.workflow_run.head_repository.full_name == github.repository",
                        condition,
                    )

    def test_direct_pull_request_validation_is_preserved(self):
        audit = AUDIT.read_text(encoding="utf-8")
        stack = FULL_STACK.read_text(encoding="utf-8")
        self.assertIn("  pull_request:", audit)
        self.assertIn("  pull_request:", stack)
        self.assertIn("github.event_name == 'pull_request'", job_condition(audit, "self-test"))
        for job in ("resolve-paper-26-3", "fetch-bundle"):
            self.assertIn("github.event_name != 'workflow_run'", job_condition(stack, job))

    def test_failure_and_cancellation_policy_is_not_relaxed(self):
        audit = job_condition(AUDIT.read_text(encoding="utf-8"), "audit-completed-run")
        self.assertIn("github.event.workflow_run.conclusion != 'cancelled'", audit)
        self.assertIn("github.event.workflow_run.conclusion != 'skipped'", audit)
        stack = FULL_STACK.read_text(encoding="utf-8")
        for job in ("resolve-paper-26-3", "fetch-bundle"):
            self.assertIn("github.event.workflow_run.conclusion == 'success'", job_condition(stack, job))

    def test_no_unsafe_checkout_override_and_tests_are_executed(self):
        for path in (AUDIT, FULL_STACK):
            with self.subTest(workflow=path.name):
                source = path.read_text(encoding="utf-8")
                self.assertNotIn("allow-unsafe-pr-checkout: true", source)
                self.assertGreaterEqual(source.count("scripts/test_workflow_run_trust_boundary.py"), 2)


if __name__ == "__main__":
    unittest.main()
