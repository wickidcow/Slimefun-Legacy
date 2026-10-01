#!/usr/bin/env python3
"""Read-only branch/PR/source-pin inventory. Never merges, pushes or deletes refs.

Classification uses commit ancestry, exact tree equality and merged-PR head
identity. It is NOT a semantic code review or gameplay certification.
"""
from __future__ import annotations
import argparse
import datetime as dt
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time
from urllib.error import HTTPError
from urllib.request import Request, urlopen

OWNER = 'wickidcow'


def api(path: str):
    if not path.startswith(f'/repos/{OWNER}/'):
        raise ValueError('Only explicitly owned repository endpoints are allowed')
    headers = {'Accept': 'application/vnd.github+json', 'User-Agent': 'SFL-read-only-branch-audit'}
    token = os.environ.get('GH_TOKEN')
    if token:
        headers['Authorization'] = f'Bearer {token}'
    for attempt in range(3):
        try:
            with urlopen(Request('https://api.github.com' + path, headers=headers), timeout=45) as response:
                return json.load(response)
        except HTTPError as exc:
            if exc.code not in (429, 500, 502, 503, 504) or attempt == 2:
                raise RuntimeError(f'GitHub HTTP {exc.code}: {path}') from None
            time.sleep(2 ** (attempt + 1))
    raise RuntimeError('Unreachable retry state')


def git(root: Path, *args: str, check: bool = True) -> subprocess.CompletedProcess:
    return subprocess.run(['git', '-c', 'core.hooksPath=/dev/null', '-C', str(root), *args],
                          text=True, capture_output=True, timeout=180, check=check,
                          env={**os.environ, 'GIT_TERMINAL_PROMPT': '0'})


def classify(tip: str, default_tip: str, same_tree: bool, contained: bool,
             matching_prs: list[dict]) -> str:
    # An open PR wins over automated cleanup advice even when content looks integrated.
    if any(pr['state'] == 'open' for pr in matching_prs):
        return 'open_pr_preserve'
    if tip == default_tip:
        return 'same_commit_as_default'
    if contained:
        return 'ancestor_of_default'
    if same_tree:
        return 'same_tree_different_history'
    if any(pr['merged'] and pr['head_sha'] == tip for pr in matching_prs):
        return 'merged_pr_exact_tip'
    if matching_prs:
        return 'unique_tip_after_closed_pr'
    return 'unique_tip_no_pr'


INTEGRATED = {'same_commit_as_default', 'ancestor_of_default',
              'same_tree_different_history', 'merged_pr_exact_tip'}


def all_prs(repo: str) -> list[dict]:
    result = []
    page = 1
    while True:
        batch = api(f'/repos/{repo}/pulls?state=all&per_page=100&page={page}')
        if not isinstance(batch, list):
            raise ValueError(f'Unexpected PR result for {repo}')
        for item in batch:
            if (item.get('head', {}).get('repo') or {}).get('full_name') != repo:
                continue
            result.append({'number': item['number'], 'title': item['title'],
                           'state': item['state'], 'merged': bool(item.get('merged_at')),
                           'head': item['head']['ref'], 'head_sha': item['head']['sha'],
                           'url': item['html_url'], 'updated_at': item['updated_at']})
        if len(batch) < 100:
            return result
        page += 1


def inspect_repo(repo: str, pin: str | None) -> dict:
    if not repo.startswith(OWNER + '/') or repo.count('/') != 1:
        raise ValueError(f'Repository outside owner scope: {repo}')
    meta = api('/repos/' + repo)
    default = meta['default_branch']
    prs = all_prs(repo)
    with tempfile.TemporaryDirectory(prefix='sfl-branch-audit-') as directory:
        root = Path(directory) / 'source.git'
        subprocess.run(['git', '-c', 'core.hooksPath=/dev/null', 'clone', '--quiet', '--bare',
                        '--filter=blob:none', '--no-tags', f'https://github.com/{repo}.git', str(root)],
                       check=True, capture_output=True, text=True, timeout=240,
                       env={**os.environ, 'GIT_TERMINAL_PROMPT': '0'})
        base = 'refs/heads/' + default
        default_tip = git(root, 'rev-parse', base).stdout.strip()
        default_tree = git(root, 'rev-parse', base + '^{tree}').stdout.strip()
        branches = []
        lines = git(root, 'for-each-ref', '--format=%(refname:strip=2)\t%(objectname)\t%(committerdate:iso-strict)',
                    'refs/heads').stdout.splitlines()
        for line in lines:
            name, sha, date = line.split('\t')
            if name == default:
                continue
            counts = git(root, 'rev-list', '--left-right', '--count', base + '...' + sha).stdout.split()
            behind, ahead = map(int, counts)
            ancestry = git(root, 'merge-base', '--is-ancestor', sha, base, check=False)
            if ancestry.returncode not in (0, 1):
                raise RuntimeError('Unable to establish commit ancestry')
            tree = git(root, 'rev-parse', sha + '^{tree}').stdout.strip()
            associated = [pr for pr in prs if pr['head'] == name]
            category = classify(sha, default_tip, tree == default_tree, ancestry.returncode == 0, associated)
            changed = []
            if category not in INTEGRATED:
                changed = git(root, 'diff', '--name-only', '--no-renames', base, sha).stdout.splitlines()
            branches.append({'name': name, 'sha': sha, 'committed_at': date,
                             'ahead': ahead, 'behind': behind, 'tree_sha': tree,
                             'classification': category, 'pull_requests': associated,
                             'tree_difference_file_count': len(changed) if category not in INTEGRATED else None,
                             'tree_difference_paths_first_60': changed[:60]})
        pin_check = None
        if pin:
            exists = git(root, 'cat-file', '-e', pin + '^{commit}', check=False).returncode == 0
            pin_check = {'commit': pin, 'available_in_fetched_history': exists}
            if exists:
                pin_check['same_tree_as_default'] = git(root, 'rev-parse', pin + '^{tree}').stdout.strip() == default_tree
                pin_check['contained_in_default'] = git(root, 'merge-base', '--is-ancestor', pin, base, check=False).returncode == 0
        return {'repository': repo, 'archived': meta['archived'], 'default_branch': default,
                'default_commit': default_tip, 'default_tree': default_tree, 'bundle_pin': pin_check,
                'branches': branches, 'open_prs': [pr for pr in prs if pr['state'] == 'open']}


def write_outputs(out: Path, payload: dict) -> None:
    out.mkdir(parents=True, exist_ok=True)
    (out / 'branch-audit.json').write_text(json.dumps(payload, indent=2) + '\n', encoding='utf-8')
    rows = ['# Slimefun addon branch audit', '', 'Observed: ' + payload['observed_at'], '',
            'Read-only inventory. No refs were deleted or rewritten. Ancestry/tree/PR matching does not certify gameplay.',
            'Integrated branches still require protection and active-work checks before deletion. Unmatched or divergent work is retained.', '',
            '| Repository | Non-default branches | Integrated evidence | Preserve/review | Open PRs |',
            '| --- | ---: | ---: | ---: | ---: |']
    for item in payload['repositories']:
        if 'error' in item:
            rows.append(f"| {item['repository']} | ERROR | — | {item['error'].replace('|', '/')} | — |")
            continue
        branches = item['branches']
        integrated = sum(b['classification'] in INTEGRATED for b in branches)
        rows.append(f"| {item['repository']} | {len(branches)} | {integrated} | {len(branches)-integrated} | {len(item['open_prs'])} |")
    rows += ['', '## Branches requiring preservation or further review', '']
    for item in payload['repositories']:
        pending = [b for b in item.get('branches', []) if b['classification'] not in INTEGRATED]
        if not pending:
            continue
        rows += ['### ' + item['repository'], '']
        for b in pending:
            refs = ', '.join(f"#{p['number']} ({p['state']}{', merged' if p['merged'] else ''})" for p in b['pull_requests']) or 'no PR found'
            rows.append(f"- `{b['name']}` — `{b['sha']}`; {b['classification']}; {b['ahead']} ahead / {b['behind']} behind; {refs}.")
        rows.append('')
    (out / 'branch-audit.md').write_text('\n'.join(rows) + '\n', encoding='utf-8')


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--matrix', type=Path, default=Path('compatibility/sfl-addon-release-matrix.json'))
    parser.add_argument('--output', type=Path, default=Path('branch-audit'))
    parser.add_argument('--extra', action='append', default=[])
    args = parser.parse_args()
    matrix = json.loads(args.matrix.read_text(encoding='utf-8'))
    sources = {row['repository']: row['source_commit'] for row in matrix['addons']}
    for repo in args.extra:
        sources.setdefault(repo, None)
    payload = {'observed_at': dt.datetime.now(dt.timezone.utc).isoformat(),
               'source_commit': os.environ.get('GITHUB_SHA'),
               'bundle_revision': matrix['bundle_revision'], 'mode': 'read-only',
               'scope': 'All manifest members plus explicitly named related repositories',
               'limitations': ['Ancestry/tree/PR inventory, not semantic review of every historical patch.',
                              'No branch-protection or active-session deletion approval is inferred.'],
               'repositories': []}
    errors = 0
    for number, (repo, pin) in enumerate(sorted(sources.items()), start=1):
        try:
            result = inspect_repo(repo, pin)
            print(f"{number}/{len(sources)} {repo}: {len(result['branches'])} branches, {len(result['open_prs'])} open PRs", flush=True)
        except Exception as exc:
            errors += 1
            # Do not print subprocess stdout/stderr or environment credentials.
            result = {'repository': repo, 'error': f'{type(exc).__name__}: {str(exc)[:300]}'}
            print(f'{number}/{len(sources)} {repo}: FAILED; preserved as incomplete', flush=True)
        payload['repositories'].append(result)
        write_outputs(args.output, payload)
    payload['complete'] = errors == 0
    payload['repository_count'] = len(sources)
    payload['errors'] = errors
    write_outputs(args.output, payload)
    return 1 if errors else 0


if __name__ == '__main__':
    raise SystemExit(main())
