#!/usr/bin/env python3
"""Read-only exact-source inventory. Source matches are not compiler diagnoses."""
import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import urllib.parse
import urllib.request

OWNER = "wickidcow"
TEXT_SUFFIXES = {".java", ".kt", ".kts", ".gradle", ".xml", ".toml", ".properties", ".md", ".py", ".sh", ".yml", ".yaml", ".json"}
PATTERNS = {"bukkit_chatcolor": r"org\.bukkit\.ChatColor|\bChatColor\.",
            "legacy_block_storage": r"\bBlockStorage\b",
            "legacy_item_text": r"\.(?:getLore|setLore|getDisplayName|setDisplayName)\(",
            "legacy_plugin_description": r"\.getDescription\(",
            "legacy_enchantment_lookup": r"Enchantment\.getByKey|Registry\.ENCHANTMENT",
            "asynchronous_scheduler_review": r"runTask(?:Timer|Later)?Asynchronously"}


def in_scope(repo: dict) -> bool:
    name = repo.get("name", "")
    return (repo.get("owner", {}).get("login") == OWNER and not repo.get("archived", True)
            and repo.get("private") is False
            and (bool(re.fullmatch(r"SF_[A-Za-z0-9_-]+", name))
                 or name in ("Slimefun-Legacy", "WorldEditSlimefun")))


def latest_run_metadata(runs: list) -> list:
    selected = {}
    for run in runs:
        key = (run.get("workflow_id"), run.get("event"), run.get("head_branch"))
        prior = selected.get(key)
        if prior is None or (run.get("id", 0), run.get("run_attempt", 1)) > (prior.get("id", 0), prior.get("run_attempt", 1)):
            selected[key] = run
    fields = ("id", "name", "head_sha", "head_branch", "event", "status", "conclusion", "html_url", "run_attempt")
    return [{k: row.get(k) for k in fields} for row in selected.values()]


def exact_runs(runs: list, sha: str) -> list:
    # A workflow_run follower records default-branch metadata even when it
    # checks out the triggering PR. Its tested source needs artifact evidence.
    return latest_run_metadata([run for run in runs
                                if run.get("head_sha") == sha and run.get("event") != "workflow_run"])


def workflow_run_candidates(runs: list) -> list:
    """Retain follower status for investigation, never as exact-source proof."""
    candidates = latest_run_metadata([run for run in runs if run.get("event") == "workflow_run"])
    for row in candidates:
        row["tested_source_verified"] = False
        row["source_verification_required"] = (
            "workflow_run head_sha may identify the default branch, not the tested source; "
            "inspect triggering-run provenance and retained artifact/source receipts"
        )
    return candidates


def api(path: str):
    if not path.startswith("/"):
        raise ValueError("Expected a GitHub API path")
    headers = {"Accept": "application/vnd.github+json", "User-Agent": "Slimefun-Legacy-read-only-maintenance-audit"}
    token = os.environ.get("GH_TOKEN")
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request("https://api.github.com" + path, headers=headers)
    with urllib.request.urlopen(req, timeout=45) as response:
        return json.load(response)


def pages(path: str) -> list:
    rows = []
    separator = "&" if "?" in path else "?"
    for number in range(1, 21):
        page = api(f"{path}{separator}per_page=100&page={number}")
        if not isinstance(page, list):
            raise ValueError("Expected a paginated GitHub list")
        rows.extend(page)
        if len(page) < 100:
            return rows
    raise RuntimeError("Pagination limit reached; inventory is incomplete")


def git(root: Path, *args: str) -> str:
    env = {k: v for k, v in os.environ.items() if k not in ("GH_TOKEN", "GITHUB_TOKEN")}
    env["GIT_TERMINAL_PROMPT"] = "0"
    env["GIT_LFS_SKIP_SMUDGE"] = "1"
    return subprocess.run(["git", "-C", str(root), *args], env=env, check=True,
                          capture_output=True, text=True, timeout=180).stdout


def replace_snapshot_source(staged: Path, current: Path) -> None:
    """Promote a complete new text snapshot without retaining deleted old files.

    A failed clone/copy leaves the prior snapshot untouched; a failed rename
    restores it. Refuse ambiguous leftover backups rather than erasing evidence.
    """
    if not staged.is_dir() or staged.is_symlink():
        raise ValueError("Staged source snapshot is not a directory")
    if current.is_symlink() or (current.exists() and not current.is_dir()):
        raise ValueError("Existing source snapshot is not a normal directory")
    backup = current.with_name(current.name + ".previous")
    if backup.exists() or backup.is_symlink():
        raise FileExistsError("Previous source snapshot backup requires manual inspection")
    had_previous = current.exists()
    if had_previous:
        os.replace(current, backup)
    try:
        os.replace(staged, current)
    except BaseException:
        if had_previous:
            os.replace(backup, current)
        raise
    if had_previous:
        shutil.rmtree(backup)


def snapshot(repo: dict, output: Path) -> dict:
    if not in_scope(repo):
        raise ValueError("Repository is outside the public maintained-source scope")
    name = repo["name"]
    full = f"{OWNER}/{name}"
    dest = output / name
    dest.mkdir(parents=True, exist_ok=True)
    result = {"repository": full, "default_branch": repo["default_branch"], "read_only": True,
              "source_matches_are_compiler_diagnostics": False, "source_snapshot_valid": False}
    try:
        branch = urllib.parse.quote(repo["default_branch"], safe="")
        sha = api(f"/repos/{full}/git/ref/heads/{branch}")["object"]["sha"]
        if not re.fullmatch(r"[0-9a-f]{40}", sha):
            raise ValueError("Invalid resolved commit")
        commit = api(f"/repos/{full}/git/commits/{sha}")
        result.update(source_commit=sha, tree_sha=commit["tree"]["sha"])
        run_page = api(f"/repos/{full}/actions/runs?head_sha={sha}&per_page=100")
        result["current_head_runs"] = exact_runs(run_page.get("workflow_runs", []), sha)
        result["workflow_run_candidates"] = workflow_run_candidates(run_page.get("workflow_runs", []))
        result["run_listing_truncated"] = run_page.get("total_count", 0) > 100
        result["issues"] = [{k: issue.get(k) for k in ("number", "title", "html_url", "updated_at")}
                            for issue in pages(f"/repos/{full}/issues?state=open") if "pull_request" not in issue]
        result["pull_requests"] = []
        for pr in pages(f"/repos/{full}/pulls?state=open"):
            row = {k: pr.get(k) for k in ("number", "title", "html_url", "draft", "updated_at")}
            row["head"] = {k: pr["head"].get(k) for k in ("sha", "ref")}
            row["base"] = {k: pr["base"].get(k) for k in ("sha", "ref")}
            runs = api(f"/repos/{full}/actions/runs?head_sha={pr['head']['sha']}&per_page=100")
            row["runs"] = exact_runs(runs.get("workflow_runs", []), pr["head"]["sha"])
            row["workflow_run_candidates"] = workflow_run_candidates(runs.get("workflow_runs", []))
            row["run_listing_truncated"] = runs.get("total_count", 0) > 100
            row["files"] = pages(f"/repos/{full}/pulls/{pr['number']}/files")
            result["pull_requests"].append(row)
        with tempfile.TemporaryDirectory(prefix="sfl-audit-") as temp, \
                tempfile.TemporaryDirectory(prefix=".source-stage-", dir=dest) as staged_dir:
            staged_source = Path(staged_dir) / "source"
            staged_source.mkdir()
            root = Path(temp)
            git(root, "init", "--quiet")
            git(root, "remote", "add", "origin", f"https://github.com/{full}.git")
            git(root, "fetch", "--quiet", "--depth=1", "--no-recurse-submodules", "origin", sha)
            git(root, "checkout", "--quiet", "--detach", sha)
            if git(root, "rev-parse", "HEAD").strip() != sha:
                raise RuntimeError("Source identity drift")
            if git(root, "rev-parse", "HEAD^{tree}").strip() != result["tree_sha"]:
                raise RuntimeError("Source tree mismatch")
            hits = {key: [] for key in PATTERNS}
            omitted = []
            copied = 0
            for rel in git(root, "ls-files", "-z").split("\0"):
                if not rel:
                    continue
                path = root / rel
                if path.is_symlink() or not path.is_file():
                    omitted.append(rel)
                    continue
                if path.suffix not in TEXT_SUFFIXES and path.name not in ("LICENSE", "LICENSE.txt", "gradlew", "mvnw"):
                    continue
                if path.stat().st_size > 3_000_000:
                    omitted.append(rel)
                    continue
                target = staged_source / rel
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(path, target)
                copied += 1
                if path.suffix in (".java", ".kt") and "src/main/" in rel:
                    for number, line in enumerate(path.read_text(encoding="utf-8", errors="replace").splitlines(), 1):
                        for key, expression in PATTERNS.items():
                            if re.search(expression, line):
                                hits[key].append({"path": rel, "line": number, "text": line.strip()[:300]})
            replace_snapshot_source(staged_source, dest / "source")
            result.update(copied_text_files=copied, omitted=omitted, source_review_candidates=hits,
                          source_snapshot_valid=True, snapshot_status="complete")
    except Exception as failure:
        result.update(snapshot_status="blocked", error=f"{type(failure).__name__}: {failure}")
    (dest / "inventory.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    return {k: result.get(k) for k in ("repository", "source_commit", "snapshot_status", "error", "copied_text_files")}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=Path("maintenance-audit"))
    args = parser.parse_args()
    repos = sorted((r for r in pages(f"/users/{OWNER}/repos?type=owner") if in_scope(r)), key=lambda r: r["name"])
    if not repos:
        raise SystemExit("No in-scope repositories found; refusing to claim a complete audit")
    args.output.mkdir(parents=True, exist_ok=True)
    results = []
    with ThreadPoolExecutor(max_workers=4) as pool:
        futures = {pool.submit(snapshot, repo, args.output): repo["name"] for repo in repos}
        for future in as_completed(futures):
            result = future.result()
            results.append(result)
            print(json.dumps(result), flush=True)
    report = {"scope": "Public non-archived wickidcow SF_* plus core and WorldEditSlimefun",
              "source_matches_are_compiler_diagnostics": False, "repositories": sorted(results, key=lambda r: r["repository"])}
    (args.output / "SUMMARY.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    blocked = [r for r in results if r["snapshot_status"] != "complete"]
    print(f"Inventoried {len(results)} repositories; {len(blocked)} blocked. No addon code was executed or changed.")
    if blocked:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
