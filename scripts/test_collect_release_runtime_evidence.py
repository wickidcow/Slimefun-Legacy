"""Offline rejection controls for release evidence collection; no network or server."""
from __future__ import annotations

import copy
import hashlib
import io
import json
import re
import stat
import subprocess
import tempfile
import unittest
import warnings
import zipfile
from pathlib import Path
from unittest.mock import patch

from scripts import collect_release_runtime_evidence as collector


SOURCE = "a" * 40
REPOSITORY_ID = 100


def run_fixture(run_id=17, *, status="completed", conclusion="success", attempt=1,
                started="2026-10-18T10:00:00Z"):
    return {
        "id": run_id, "path": collector.NATIVE_WORKFLOW, "workflow_id": 31,
        "event": "push", "head_branch": "master", "head_sha": SOURCE,
        "status": status, "conclusion": conclusion, "run_attempt": attempt,
        "created_at": started, "run_started_at": started,
        "updated_at": "2026-10-18T10:10:00Z", "html_url": f"https://github.com/example/runs/{run_id}",
        "repository": {"id": REPOSITORY_ID, "full_name": collector.REPOSITORY},
        "head_repository": {"id": REPOSITORY_ID, "full_name": collector.REPOSITORY},
    }


def job_fixture():
    return {"id": 41, "run_id": 17, "run_attempt": 1, "head_sha": SOURCE,
            "name": "Build fixed paired storage test inputs", "status": "completed",
            "conclusion": "success", "started_at": "2026-10-18T10:01:00Z",
            "completed_at": "2026-10-18T10:09:00Z"}


def artifact_fixture():
    return {"id": 51, "name": "paired-storage-inputs", "expired": False,
            "digest": "sha256:" + "b" * 64, "size_in_bytes": 100,
            "created_at": "2026-10-18T10:08:00Z",
            "workflow_run": {"id": 17, "head_sha": SOURCE, "repository_id": REPOSITORY_ID,
                             "head_repository_id": REPOSITORY_ID}}


def make_zip(entries):
    output = io.BytesIO()
    with warnings.catch_warnings(), zipfile.ZipFile(output, "w") as archive:
        warnings.simplefilter("ignore", UserWarning)
        for name, data in entries:
            archive.writestr(name, data)
    return output.getvalue()


class GitHubTransportControls(unittest.TestCase):
    def test_http_429_retry_returns_exact_bytes(self):
        error = subprocess.CalledProcessError(
            1, ["gh", "api"], stderr=b"HTTP 429: Too Many Requests")
        result = subprocess.CompletedProcess(["gh", "api"], 0, stdout=b"archive")
        with patch.object(collector.subprocess, "run", side_effect=[error, result]) as command, \
                patch.object(collector.time, "sleep") as sleep:
            self.assertEqual(b"archive", collector.github_bytes("repos/example/artifacts/42/zip"))
        self.assertEqual(command.call_count, 2)
        sleep.assert_called_once_with(1)

    def test_transient_gateway_and_timeout_retry_with_backoff(self):
        errors = [subprocess.CalledProcessError(1, ["gh", "api"], stderr=b"HTTP 502: Bad Gateway"),
                  subprocess.TimeoutExpired(["gh", "api"], 180)]
        ok = subprocess.CompletedProcess(["gh", "api"], 0, stdout=b"unchanged")
        with patch.object(collector.subprocess, "run", side_effect=[*errors, ok]) as command, \
                patch.object(collector.time, "sleep") as sleep:
            self.assertEqual(b"unchanged", collector.github_bytes("repos/example/jobs"))
        self.assertEqual(command.call_count, 3)
        self.assertEqual([c.args[0] for c in sleep.call_args_list], [1, 2])

    def test_403_and_404_fail_immediately_without_retry(self):
        for error_message in (b"HTTP 403: Resource not accessible by integration",
                              b"HTTP 404: Not Found"):
            with self.subTest(error_message=error_message), \
                    patch.object(collector.subprocess, "run", side_effect=subprocess.CalledProcessError(
                        1, ["gh", "api"], stderr=error_message)) as command, \
                    patch.object(collector.time, "sleep") as sleep:
                with self.assertRaisesRegex(RuntimeError, "GitHub Actions evidence download failed"):
                    collector.github_bytes("repos/example/artifacts/42/zip")
                command.assert_called_once()
                sleep.assert_not_called()

    def test_exhausted_503_still_fails_closed(self):
        error = subprocess.CalledProcessError(
            1, ["gh", "api"], stderr=b"HTTP 503: Service Unavailable")
        with patch.object(collector.subprocess, "run", side_effect=error) as command, \
                patch.object(collector.time, "sleep") as sleep:
            with self.assertRaisesRegex(RuntimeError, "GitHub Actions evidence download failed"):
                collector.github_bytes("repos/example/actions/jobs")
        self.assertEqual(command.call_count, collector.GH_MAX_ATTEMPTS)
        self.assertEqual([c.args[0] for c in sleep.call_args_list], [1, 2, 4])


class CollectionControls(unittest.TestCase):
    def test_complete_collection_paginates_without_losing_rows(self):
        pages = [{"total_count": 101, "jobs": [{"id": i} for i in range(1, 101)]},
                 {"total_count": 101, "jobs": [{"id": 101}]}]
        with patch.object(collector, "github_json", side_effect=pages) as query:
            result = collector.collection("repos/example/actions/jobs?filter=latest", "jobs")
        self.assertEqual(101, len(result["jobs"]))
        self.assertTrue(query.call_args_list[1].args[0].endswith("&per_page=100&page=2"))

    def test_truncated_duplicate_and_changed_collections_are_rejected(self):
        first = {"total_count": 101, "jobs": [{"id": i} for i in range(1, 101)]}
        cases = [
            [{"total_count": 2, "jobs": [{"id": 1}]}],
            [{"total_count": 2, "jobs": [{"id": 1}, {"id": 1}]}],
            [first, {"total_count": 102, "jobs": [{"id": 101}, {"id": 102}]}],
        ]
        for pages in cases:
            with self.subTest(pages=len(pages)), patch.object(collector, "github_json", side_effect=pages):
                with self.assertRaises(ValueError):
                    collector.collection("repos/example/jobs", "jobs")

    def test_exact_successful_latest_attempt_is_selected(self):
        current = run_fixture(attempt=2)
        with patch.object(collector, "collection", return_value={"workflow_runs": [current]}), \
                patch.object(collector, "github_json", side_effect=[current, current]):
            selected = collector.latest_run(collector.REPOSITORY, SOURCE,
                                             collector.NATIVE_WORKFLOW, {"push"})
        self.assertEqual(2, selected["run_attempt"])

    def test_latest_failure_or_pending_execution_cannot_fall_back_to_green(self):
        for status, conclusion in [("completed", "failure"), ("completed", "cancelled"),
                                   ("in_progress", None), ("queued", None)]:
            latest = run_fixture(18, status=status, conclusion=conclusion,
                                 started="2026-10-18T11:00:00Z")
            with self.subTest(status=status, conclusion=conclusion), \
                    patch.object(collector, "collection", return_value={"workflow_runs": [run_fixture(), latest]}), \
                    patch.object(collector, "github_json", return_value=latest):
                with self.assertRaisesRegex(ValueError, "not completed successfully"):
                    collector.latest_run(collector.REPOSITORY, SOURCE,
                                         collector.NATIVE_WORKFLOW, {"push"})

    def test_skipped_trigger_does_not_mask_successful_exact_source_execution(self):
        completed = run_fixture()
        skipped = run_fixture(18, conclusion="skipped", started="2026-10-18T11:00:00Z")
        for row in (completed, skipped):
            row["path"] = collector.FULL_STACK_WORKFLOW
            row["event"] = "workflow_run"
        with patch.object(collector, "collection", return_value={"workflow_runs": [completed, skipped]}), \
                patch.object(collector, "github_json", side_effect=[completed, completed]):
            selected = collector.latest_run(collector.REPOSITORY, SOURCE,
                                            collector.FULL_STACK_WORKFLOW, {"workflow_run"})
        self.assertEqual(completed["id"], selected["id"])

    def test_skipped_trigger_cannot_hide_newer_failure_or_pending_execution(self):
        for status, conclusion in [("completed", "failure"), ("in_progress", None)]:
            latest = run_fixture(18, status=status, conclusion=conclusion,
                                 started="2026-10-18T11:00:00Z")
            skipped = run_fixture(19, conclusion="skipped", started="2026-10-18T12:00:00Z")
            with self.subTest(status=status, conclusion=conclusion), \
                    patch.object(collector, "collection",
                                 return_value={"workflow_runs": [run_fixture(), latest, skipped]}), \
                    patch.object(collector, "github_json", return_value=latest):
                with self.assertRaisesRegex(ValueError, "not completed successfully"):
                    collector.latest_run(collector.REPOSITORY, SOURCE,
                                         collector.NATIVE_WORKFLOW, {"push"})

    def test_skipped_only_does_not_supply_runtime_evidence(self):
        skipped = run_fixture(conclusion="skipped")
        with patch.object(collector, "collection", return_value={"workflow_runs": [skipped]}):
            with self.assertRaisesRegex(ValueError, "No eligible non-skipped runtime workflow"):
                collector.latest_run(collector.REPOSITORY, SOURCE,
                                     collector.NATIVE_WORKFLOW, {"push"})

    def test_a_later_rerun_of_an_older_run_is_not_hidden_by_a_newer_id(self):
        rerun = run_fixture(16, conclusion="failure", attempt=2,
                            started="2026-10-18T11:00:00Z")
        with patch.object(collector, "collection", return_value={"workflow_runs": [run_fixture(), rerun]}), \
                patch.object(collector, "github_json", return_value=rerun):
            with self.assertRaises(ValueError):
                collector.latest_run(collector.REPOSITORY, SOURCE, collector.NATIVE_WORKFLOW, {"push"})

    def test_attempt_change_during_selection_is_rejected(self):
        original, changed = run_fixture(), run_fixture(attempt=2)
        with patch.object(collector, "collection", return_value={"workflow_runs": [original]}), \
                patch.object(collector, "github_json", side_effect=[original, changed]):
            with self.assertRaisesRegex(ValueError, "changed"):
                collector.latest_run(collector.REPOSITORY, SOURCE, collector.NATIVE_WORKFLOW, {"push"})

    def test_successful_retry_preserves_each_endpoints_distinct_timestamps(self):
        current = run_fixture(attempt=2)
        attempt = copy.deepcopy(current)
        attempt["created_at"] = "2026-10-18T10:02:00Z"
        attempt["updated_at"] = "2026-10-18T10:10:01Z"
        with patch.object(collector, "collection", return_value={"workflow_runs": [current]}), \
                patch.object(collector, "github_json", side_effect=[current, attempt]):
            selected = collector.latest_run(collector.REPOSITORY, SOURCE,
                                             collector.NATIVE_WORKFLOW, {"push"})
        self.assertEqual(attempt["created_at"], selected["created_at"])
        self.assertEqual(current["created_at"], selected["run_endpoint_snapshot"]["created_at"])
        self.assertEqual(current["updated_at"], selected["run_endpoint_snapshot"]["updated_at"])

    def test_wrong_repository_source_event_and_workflow_are_rejected(self):
        for field, value in [("head_sha", "b" * 40), ("head_branch", "topic"),
                             ("event", "pull_request"), ("path", "other.yml"),
                             ("run_attempt", True)]:
            run = run_fixture()
            run[field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                collector.validate_run(run, collector.REPOSITORY, SOURCE, collector.NATIVE_WORKFLOW, {"push"})
        run = run_fixture()
        run["head_repository"]["full_name"] = "other/repository"
        with self.assertRaises(ValueError):
            collector.validate_run(run, collector.REPOSITORY, SOURCE, collector.NATIVE_WORKFLOW, {"push"})


class ArtifactControls(unittest.TestCase):
    def select(self, artifact=None, job=None, run=None):
        return collector.artifact_for_job(
            {"artifacts": [artifact or artifact_fixture()]}, "paired-storage-inputs",
            {"jobs": [job or job_fixture()]}, "Build fixed paired storage test inputs", run or run_fixture(),
        )

    def test_artifact_from_retained_successful_earlier_attempt_is_valid(self):
        self.assertEqual(51, self.select(run=run_fixture(attempt=2))["id"])

    def test_artifact_from_before_the_retained_job_is_rejected(self):
        artifact = artifact_fixture()
        artifact["created_at"] = "2026-10-18T09:00:00Z"
        with self.assertRaisesRegex(ValueError, "retained successful job"):
            self.select(artifact=artifact)

    def test_failed_skipped_wrong_source_and_future_attempt_jobs_are_rejected(self):
        for field, value in [("conclusion", "failure"), ("conclusion", "skipped"),
                             ("head_sha", "b" * 40), ("run_id", 99), ("run_attempt", 2)]:
            job = job_fixture()
            job[field] = value
            with self.subTest(field=field, value=value), self.assertRaises(ValueError):
                self.select(job=job)

    def test_wrong_expired_or_undigested_artifact_is_rejected(self):
        for field, value in [("expired", True), ("digest", None), ("id", True)]:
            artifact = artifact_fixture()
            artifact[field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                self.select(artifact=artifact)
        artifact = artifact_fixture()
        artifact["workflow_run"]["head_sha"] = "b" * 40
        with self.assertRaises(ValueError):
            self.select(artifact=artifact)

    def extract(self, raw, metadata=None):
        metadata = metadata or {"id": 51, "name": "paired-storage-inputs", "size_in_bytes": len(raw),
                                "digest": "sha256:" + hashlib.sha256(raw).hexdigest()}
        with tempfile.TemporaryDirectory() as temporary:
            destination = Path(temporary) / "evidence"
            receipt = collector.extract_artifact(raw, metadata, destination)
            return receipt, {str(path.relative_to(destination)): path.read_bytes()
                             for path in destination.rglob("*") if path.is_file()}

    def test_valid_download_is_verified_before_extracting_original_bytes(self):
        raw = make_zip([("inputs.json", b'{"source":"exact"}\n'), ("nested/receipt.json", b"{}")])
        receipt, files = self.extract(raw)
        self.assertEqual(hashlib.sha256(raw).hexdigest(), receipt["sha256"])
        self.assertEqual(b'{"source":"exact"}\n', files["inputs.json"])

    def test_wrong_archive_hash_size_and_crc_are_rejected(self):
        raw = make_zip([("inputs.json", b"ORIGINAL")])
        metadata = {"id": 51, "name": "inputs", "digest": "sha256:" + hashlib.sha256(raw).hexdigest(),
                    "size_in_bytes": len(raw)}
        for field, value in [("digest", "sha256:" + "c" * 64), ("size_in_bytes", len(raw) + 1)]:
            changed = dict(metadata, **{field: value})
            with self.subTest(field=field), self.assertRaises(ValueError):
                self.extract(raw, changed)
        corrupt = raw.replace(b"ORIGINAL", b"CORRUPT!")
        with self.assertRaises((ValueError, zipfile.BadZipFile)):
            self.extract(corrupt)

    def test_paths_duplicates_aliases_and_symlinks_are_rejected(self):
        for name in ["../escape", "/absolute", "a/../escape", "a\\escape", "./alias", "a//alias"]:
            with self.subTest(name=name), self.assertRaises(ValueError):
                self.extract(make_zip([(name, b"bad")]))
        with self.assertRaises(ValueError):
            self.extract(make_zip([("same", b"one"), ("same", b"two")]))
        symlink = zipfile.ZipInfo("link")
        symlink.create_system = 3
        symlink.external_attr = (stat.S_IFLNK | 0o777) << 16
        with self.assertRaises(ValueError):
            self.extract(make_zip([(symlink, b"../escape")]))

    def test_changed_results_are_rejected_before_passing(self):
        run = run_fixture()
        jobs = {"jobs": [job_fixture()]}
        artifacts = {"total_count": 1, "artifacts": [artifact_fixture()]}
        with patch.object(collector, "latest_run", return_value=run), \
                patch.object(collector, "jobs_snapshot", return_value={"jobs": []}):
            with self.assertRaisesRegex(ValueError, "job results changed"):
                collector.require_unchanged(collector.REPOSITORY, SOURCE, collector.NATIVE_WORKFLOW,
                                             {"push"}, (run, jobs, artifacts), collector.NATIVE_ARTIFACT_JOBS)
        changed = copy.deepcopy(artifacts)
        changed["artifacts"][0]["id"] = 52
        with patch.object(collector, "latest_run", return_value=run), \
                patch.object(collector, "jobs_snapshot", return_value=jobs), \
                patch.object(collector, "collection", return_value=changed):
            with self.assertRaisesRegex(ValueError, "artifact metadata changed"):
                collector.require_unchanged(collector.REPOSITORY, SOURCE, collector.NATIVE_WORKFLOW,
                                             {"push"}, (run, jobs, artifacts),
                                             {"paired-storage-inputs": "Build fixed paired storage test inputs"})


class CollectorCommandControls(unittest.TestCase):
    def execute(self, *, native_error=None, full_stack_error=None, native_status="PASS_EXACT_RELEASE_NATIVE_PAIR",
                full_stack_status="PASS", change_release_file=False):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            core, bundle = root / "core.jar", root / "bundle.zip"
            core.write_bytes(b"exact-release-core")
            bundle.write_bytes(b"exact-release-bundle")
            output = root / "collected"
            bundle_run = run_fixture()
            bundle_run["path"] = collector.BUNDLE_WORKFLOW

            def native_check(*args):
                if native_error:
                    raise native_error
                return {"status": native_status}

            def full_stack_check(*args):
                if full_stack_error:
                    raise full_stack_error
                if change_release_file:
                    core.write_bytes(b"different-release-core")
                return {"status": full_stack_status}

            arguments = ["collector", "--repository", collector.REPOSITORY, "--source", SOURCE,
                         "--bundle-run", "17", "--core", str(core), "--bundle", str(bundle),
                         "--output", str(output)]
            with patch("sys.argv", arguments), patch.object(collector, "github_json", return_value=bundle_run), \
                    patch.object(collector, "collect_workflow", return_value=({}, {}, {})) as collect, \
                    patch.object(collector, "require_unchanged"), \
                    patch("scripts.verify_release_runtime_evidence.verify_native_evidence", side_effect=native_check), \
                    patch("scripts.verify_release_full_stack_evidence.verify_full_stack_evidence",
                          side_effect=full_stack_check), patch("builtins.print"):
                error = None
                try:
                    code = collector.main()
                except ValueError as failure:
                    code, error = None, str(failure)
            return code, error, json.loads((output / "release-runtime-evidence.json").read_text()), collect.call_count

    def test_command_requires_both_acceptances_and_preserves_the_result(self):
        code, error, receipt, calls = self.execute()
        self.assertEqual(0, code)
        self.assertIsNone(error)
        self.assertEqual("PASS", receipt["status"])
        self.assertFalse(receipt["publication_authorization"])
        self.assertEqual(2, calls)

    def test_native_failure_stops_before_full_stack_and_preserves_failure(self):
        for arguments in [{"native_error": ValueError("wrong tested pair")}, {"native_status": "FAIL"}]:
            with self.subTest(arguments=list(arguments)):
                code, error, receipt, calls = self.execute(**arguments)
                self.assertIsNone(code)
                self.assertIsNotNone(error)
                self.assertEqual("FAIL", receipt["status"])
                self.assertNotIn("full_stack", receipt)
                self.assertEqual(1, calls)

    def test_full_stack_failure_cannot_be_hidden_by_native_success(self):
        for arguments in [{"full_stack_error": ValueError("wrong tested bundle")}, {"full_stack_status": "FAIL"}]:
            with self.subTest(arguments=list(arguments)):
                code, error, receipt, calls = self.execute(**arguments)
                self.assertIsNone(code)
                self.assertIsNotNone(error)
                self.assertEqual("FAIL", receipt["status"])
                self.assertEqual(2, calls)

    def test_release_file_change_during_collection_blocks_acceptance(self):
        code, error, receipt, calls = self.execute(change_release_file=True)
        self.assertIsNone(code)
        self.assertIn("Release files changed", error)
        self.assertEqual("FAIL", receipt["status"])


class ReleaseDispatchControls(unittest.TestCase):
    """Validate manual and automatic exact-master release gates without GitHub writes."""

    def setUp(self):
        workflow = Path(__file__).resolve().parents[1] / ".github/workflows/reproducible-release.yml"
        self.workflow = workflow.read_text(encoding="utf-8")
        rows = list(re.finditer(
            r"^      - name: (?P<name>[^\n]+)\n(?P<body>.*?)(?=^      - |\Z)",
            self.workflow, re.MULTILINE | re.DOTALL,
        ))
        self.steps = {row["name"]: row["body"] for row in rows}
        self.assertEqual(len(rows), len(self.steps), "Release step names must remain unique")

    def condition(self, name):
        body = self.steps[name]
        match = re.search(r"^        if: (.+)$", body, re.MULTILINE)
        if match is None:
            return ""
        value = match[1].strip()
        if value not in (">-", "|", ">"):
            return value
        continuation = []
        for line in body[match.end():].splitlines():
            if not line.strip():
                continue
            if not line.startswith("          "):
                break
            continuation.append(line.strip())
        return " ".join(continuation)

    @staticmethod
    def allows(condition, *, event="workflow_dispatch", publish="", outcome="success",
               ref="refs/heads/master"):
        """Evaluate only explicit, known release-guard comparisons; reject unknown syntax."""
        if not condition:
            return True
        context = {
            "github.event_name": event,
            "github.ref": ref,
            "inputs.publish": publish,
            "steps.runtime_evidence.outcome": outcome,
        }
        tokens = []
        offset = 0
        token_re = re.compile(r"\s*(\(|\)|&&|\|\||==|!=|'[^']*'|[A-Za-z_][A-Za-z_0-9.]*)")
        while offset < len(condition):
            m = token_re.match(condition, offset)
            if m is None:
                raise AssertionError("Unsupported release guard near: " + condition[offset:])
            tokens.append(m[1])
            offset = m.end()
        pos = 0

        def pop():
            nonlocal pos
            if pos >= len(tokens):
                raise AssertionError("Incomplete release guard")
            value = tokens[pos]
            pos += 1
            return value

        def peek():
            return tokens[pos] if pos < len(tokens) else None

        def atom():
            if peek() == "(":
                pop()
                result = either()
                if pop() != ")":
                    raise AssertionError("Unbalanced release guard parentheses")
                return result
            variable, operator, raw = pop(), pop(), pop()
            if variable not in context or operator not in ("==", "!="):
                raise AssertionError("Unsupported release-guard comparison")
            if raw.startswith("'") and raw.endswith("'"):
                value = raw[1:-1]
            elif raw in ("true", "false"):
                value = raw == "true"
            else:
                raise AssertionError("Unsupported release-guard value: " + raw)
            equal = context[variable] == value
            return equal if operator == "==" else not equal

        def both():
            result = atom()
            while peek() == "&&":
                pop()
                next_result = atom()
                result = result and next_result
            return result

        def either():
            result = both()
            while peek() == "||":
                pop()
                next_result = both()
                result = result or next_result
            return result

        verdict = either()
        if pos != len(tokens):
            raise AssertionError("Trailing release-guard tokens")
        return verdict

    def test_manual_dispatch_declares_boolean_publish_default_false(self):
        dispatch = re.search(
            r"^  workflow_dispatch:\n(?P<body>.*?)(?=^  [a-z_]+:|\Z)",
            self.workflow, re.MULTILINE | re.DOTALL,
        )
        self.assertIsNotNone(dispatch)
        publish = re.search(
            r"^      publish:\n(?P<body>.*?)(?=^      [a-z_]+:|\Z)",
            dispatch["body"], re.MULTILINE | re.DOTALL,
        )
        self.assertIsNotNone(publish, "Explicit manual dispatch must retain its publish input")
        self.assertRegex(publish["body"], r"(?m)^        type: boolean$")
        self.assertRegex(publish["body"], r"(?m)^        default: false$")
        self.assertRegex(publish["body"], r"(?m)^        description: .+false.+validat")

    def test_all_release_mutations_require_successful_exact_master_evidence(self):
        mutation_steps = {name for name, body in self.steps.items()
                          if re.search(r"\bgh\s+release\s+(create|upload|edit|delete|delete-asset)\b", body)}
        self.assertEqual(
            {"Publish exact validated Java-only core release"},
            mutation_steps, "Release mutation moved beyond the reviewed guarded steps",
        )
        verifier = "Verify release assets without mutation"
        self.assertIn(verifier, self.steps, "Must retain read-only post-publication verification")
        # Keep the existing authorization truth table on both steps while
        # narrowing the allowed mutation surface to the create-only publisher.
        guarded_steps = mutation_steps | {verifier}
        publish_step = self.steps["Publish exact validated Java-only core release"]
        self.assertIn('test "$GITHUB_REF" = refs/heads/master', publish_step)
        self.assertIn('test "$GITHUB_SHA" = "$(gh api', publish_step)
        for name in guarded_steps:
            step = self.steps[name]
            self.assertIn('RELEASE_APPROVED: ${{ inputs.publish }}', step)
            self.assertIn('test "$GITHUB_EVENT_NAME" = workflow_dispatch', step)
            self.assertIn('test "$RELEASE_APPROVED" = true', step)
            self.assertIn('test "$GITHUB_REF" = refs/heads/master', step)
        # Even a fully green master push must never publish or edit release assets.
        # A manual dispatch must opt in explicitly and target the current master.
        for name in guarded_steps:
            for event in ("workflow_dispatch", "push", "pull_request", "schedule"):
                for publish in ("", False, True, "false", "true"):
                    for outcome in ("", None, "success", "failure", "cancelled", "skipped"):
                        for ref in ("refs/heads/master", "refs/heads/feature"):
                            with self.subTest(step=name, event=event, publish=publish,
                                              outcome=outcome, ref=ref):
                                expected = (
                                    outcome == "success"
                                    and event == "workflow_dispatch"
                                    and publish is True
                                    and ref == "refs/heads/master"
                                )
                                self.assertEqual(
                                    expected,
                                    self.allows(self.condition(name), event=event,
                                                publish=publish, outcome=outcome, ref=ref),
                                )

    def test_reintroduced_verifier_mutations_fail_closed(self):
        name = "Verify release assets without mutation"
        original = self.steps[name]
        for command in ("create", "upload", "edit", "delete", "delete-asset"):
            with self.subTest(command=command), patch.dict(
                    self.steps, {name: original + f'\n          gh release {command} "$TAG"\n'}):
                with self.assertRaisesRegex(AssertionError, "Release mutation moved"):
                    self.test_all_release_mutations_require_successful_exact_master_evidence()

    def test_missing_readonly_verifier_fails_closed(self):
        steps = dict(self.steps)
        del steps["Verify release assets without mutation"]
        with patch.dict(self.steps, steps, clear=True):
            with self.assertRaisesRegex(AssertionError, "Must retain read-only"):
                self.test_all_release_mutations_require_successful_exact_master_evidence()

    def test_unknown_or_broken_release_guards_fail_closed(self):
        for expr in ("github.unsupported == true", "inputs.publish == yes",
                     "github.event_name == 'push' && invalid()",
                     "(github.event_name == 'push'"):
            with self.subTest(expression=expr):
                with self.assertRaises(AssertionError):
                    self.allows(expr)

    def test_validation_dispatch_keeps_every_java_release_check(self):
        checks = [
            "Verify Slimefun Legacy invariants", "First clean release build",
            "Second clean release build", "Require byte-for-byte reproducibility",
            "Require exact-source validated canonical addon bundle",
            "Await exact-source native and full-stack runtime results",
            "Require runtime evidence for the exact release files",
            "Require pinned official resource pack",
        ]
        for publish in ("", False, True):
            for name in checks:
                with self.subTest(step=name, publish=publish):
                    self.assertTrue(self.allows(self.condition(name), publish=publish))
                    self.assertNotRegex(self.steps[name], r"(?m)^        continue-on-error: true$")
        runtime = self.steps["Require runtime evidence for the exact release files"]
        for argument in ("scripts/collect_release_runtime_evidence.py", '--source "$GITHUB_SHA"',
                         '--bundle-run "$RELEASE_BUNDLE_RUN_ID"', '--core "dist/${OUTPUT_NAME}"',
                         '--bundle "dist/${CANONICAL_ADDON_BUNDLE}"'):
            self.assertIn(argument, runtime)
        native = self.steps["Await exact-source native and full-stack runtime results"]
        self.assertIn("require_complete native-entity-retirement.yml push", native)
        self.assertIn("require_complete paper-26.3-full-stack.yml workflow_run", native)
        positions = [self.workflow.index("      - name: " + name) for name in checks]
        self.assertEqual(sorted(positions), positions)
        self.assertLess(positions[-1], self.workflow.index(
            "      - name: Publish exact validated Java-only core release"))

    def test_runtime_wait_ignores_only_skipped_triggers_and_keeps_fail_closed_outcomes(self):
        step = self.steps["Await exact-source native and full-stack runtime results"]
        self.assertIn('select(.conclusion != "skipped")', step)
        self.assertIn('if [[ "$state" == success ]]', step)
        self.assertIn('if [[ "$state" == failure || "$state" == cancelled', step)
        self.assertIn('No successful exact-source $label runtime run; release refused', step)

    def test_runtime_evidence_failure_is_always_retained(self):
        name = "Preserve release runtime evidence"
        self.assertEqual("always()", self.condition(name))
        self.assertIn("uses: actions/upload-artifact@", self.steps[name])
        self.assertIn("path: build/release-runtime-evidence/", self.steps[name])

    def test_readonly_completion_only_on_nonpublishing_dispatch(self):
        condition = self.condition("Mark candidate validation complete")
        for event in ("workflow_dispatch", "push", "pull_request"):
            for publish in ("", False, True):
                with self.subTest(event=event, publish=publish):
                    expected = event == "workflow_dispatch" and publish is not True
                    self.assertEqual(expected, self.allows(condition, event=event, publish=publish))

    def test_workflow_runs_dispatch_contract_before_building(self):
        step = "Verify release dispatch and evidence controls"
        self.assertIn("python3 -m unittest scripts.test_collect_release_runtime_evidence -v",
                      self.steps[step])
        self.assertLess(self.workflow.index("      - name: " + step),
                        self.workflow.index("      - name: First clean release build"))


if __name__ == "__main__":
    unittest.main()
