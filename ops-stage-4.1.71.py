"""Stage a metadata-only release candidate on a new branch; never publish or merge."""
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import urllib.error
import urllib.request

REPO = 'wickidcow/Slimefun-Legacy'
SOURCE = '3f6999445ba14557b9d472e86e19c45999b6a6ac'
BASELINE = '682f26ef1c6a71c905091fb178bf4b3763bc1652'
BRANCH = 'release/4.1.71-revision126'
REGISTRIES = ['addon-compatibility-matrix.json', 'core-api-registry.json', 'cross-fork-api-matrix.json', 'support-contract.json']
NOTES = '''# Slimefun Legacy 4.1.71 release readiness

## Frozen scope

Prepare **4.1.71** with canonical **45-addon revision 126** from merged source `3f6999445ba14557b9d472e86e19c45999b6a6ac`. This is a new coordinated package identity, not an overwrite of the existing 4.1.70 release. Accept release blockers only; no unrelated features or addon increments.

The runtime changes are already merged: the Hologram Display Projector input-slot improvement and published JustEnoughGuide 2.1.72's offline-profile history repair. Preserve all 45 addon source pins, optional integrations, item/research identities, recipes, saved formats, migration defaults and server configuration. The resource-pack pin remains unchanged; publication must verify its exact bytes and reconcile the label/hash in the final notes rather than copy older release-page wording.

## Regression baseline

The required previous-stable baseline is the published **4.1.70** core at `682f26ef1c6a71c905091fb178bf4b3763bc1652`, not today's newer master. Its original core asset SHA-256 is `c3fc4bb3b983a285ec9c553418153aeb02a7bd9fb501e79d95320059d1dff9e7`. The earlier 4.1.69-to-4.1.70 persistence fixture stays immutable, including its published control bytes. The advisory 4.1.15 floor is unchanged.

## Completed precursor evidence

All ten returned workflows for merged revision-126 source `3f69994` succeeded. The actual canonical bundle from run **37673226443**, artifact **11506421359**, was downloaded and audited. Its outer SHA-256 is `bc2b5b39e67761887277d4cd6a0159877ff24209524656468d6051b43efae432`; its nested ZIP SHA-256 is `a0d55b94b49429cda2ae0f22e908feaab262a0c66652f2bf8dfee5e994ecf82e` (24,309,130 bytes).

All **45 JARs / 12,365 classes** passed integrity, manifest/source/version, entrypoint, duplicate-name, per-JAR checksum and Java-21/non-preview bytecode checks. Against published revision 125, the only changed compiled class is JEG's repaired GuideHistoryPatchListener. This is not a claim that every complete addon ZIP/JAR archive is byte-for-byte reproducible.

The downloaded final-source full-stack reports from run **37673226638** passed two boots with **45/45 addons enabled and zero dependency exclusions** on Paper **1.21.11 build 132 STABLE**, **26.2 build 132 STABLE**, and **26.3 build 159 BETA**. All six inspected console logs enable JEG 2.1.72 and contain no ERROR/SEVERE lines; the reports confirm no linkage/enable/configuration-load failures and clean-shutdown persistence on the second boot. The 26.3 server and WorldEdit provider are beta builds.

This evidence belongs to the precursor source, not the newly versioned candidate. Fresh exact-head and final-merge validation are required below.

## Required release gates

1. Pass the full current-head source invariants, build/tests, required addon/API baseline comparisons, gameplay correctness, database validation, runtime/proxy checks and applicable audits. Record skips accurately; do not weaken checks or relabel previous results.
2. Rebuild and inspect the canonical revision-126 bundle against the exact release source; verify all 45 pins and the JEG listener identity. Complete its matching full-stack runs.
3. After the reviewed merge, complete the unchanged reproducible-release pipeline: two clean byte-identical core builds, exact-source canonical bundle and pinned resource-pack verification. A push-triggered packaging check is not a published release.
4. Publish only through the existing gated release path under the unused v4.1.71 identity. Verify the tag and actual downloaded core/bundle/pack bytes, then finalize notes. Preserve all existing release assets and never distribute test probes.

## Limits and deployment

The supplied historical-owner-database test and a copied production-world playthrough have not been completed. Startup, persistence and callback tests do not certify every player menu, machine/Cargo transfer, Folia combination or cross-fork rollback. Do not turn the historical optional skip into a pass.

No live-server operation is included. Stop the server and back up worlds, databases, configuration and addon data before installation. Use a disposable copy first; replace intended JARs without duplicates and retain existing data. The optional pack is not automatically enabled. WorldEditSlimefun still requires a compatible external provider. SF_BetterStructuresLink remains separately scoped, outside the canonical 45 addons.
'''


def require(ok, message):
    if not ok:
        raise RuntimeError(message)


def replace_once(text, old, new):
    require(text.count(old) == 1, 'Expected exactly one source marker: ' + old)
    return text.replace(old, new, 1)


def transform_registry(text):
    before = json.loads(text)
    require(before['release'] == '4.1.70', 'Unexpected registry release')
    result = replace_once(text, '"release": "4.1.70"', '"release": "4.1.71"')
    after = json.loads(result)
    after['release'] = before['release']
    require(after == before, 'Registry policy/content changed')
    return result


def transform_baseline(text):
    before = json.loads(text)
    require(before['candidate']['version'] == '4.1.70' and before['previous_stable']['version'] == '4.1.69', 'Unexpected baseline pair')
    result = replace_once(text, '"version": "4.1.70"', '"version": "4.1.71"')
    result = replace_once(result, '"version": "4.1.69"', '"version": "4.1.70"')
    result = replace_once(result, 'd430eda74525b31328660bc3d3e36e13c9869353', BASELINE)
    result = replace_once(result, before['previous_stable']['purpose'], 'Primary regression baseline: the published 4.1.70 core, not newer master builds. Required addons that work here but fail against the candidate block release.')
    after = json.loads(result)
    require(after['policy'] == before['policy'] and after['legacy_floor'] == before['legacy_floor'], 'Baseline policy/floor changed')
    require(after['previous_stable']['release_blocking'] is True, 'Baseline gate weakened')
    return result


def transform_readme(text):
    require('4.1.70' in text, 'README missing current release')
    result = text.replace('4.1.70', '4.1.71')
    result = result.replace('Coordinated Core and Addon Stabilization', 'Projector and Guide Maintenance')
    result = result.replace('**4.1.69 core refresh**', '**4.1.70**')
    result, count = re.subn(r'^Slimefun Legacy 4\.1\.71 uses the delivered 4\.1\.69 core refresh.*$',
        'Slimefun Legacy 4.1.71 uses the published 4.1.70 source at `' + BASELINE + '` as its release-blocking compatibility baseline. Required addons that work against published 4.1.70 but regress against the 4.1.71 candidate block release. Newer master builds are not substituted for that released baseline. The historical 4.1.15 floor remains advisory for long-term drift visibility. CI-only addon repository coverage may be broader than `/sf versions`; runtime recognition stays curated and never treats CI monitoring as proof for an exact installed addon build.', result, flags=re.M)
    require(count == 1, 'Unexpected README baseline paragraph')
    result = result.replace('previous stable regression baseline is the delivered 4.1.69 core refresh', 'previous stable regression baseline is published 4.1.70')
    require('docs/release-candidates/4.1.71.md' in result and BASELINE in result, 'README candidate/baseline links missing')
    return result


def api(path, data=None):
    headers = {'Authorization': 'Bearer ' + os.environ['GH_TOKEN'], 'Accept': 'application/vnd.github+json', 'X-GitHub-Api-Version': '2022-11-28', 'User-Agent': 'Slimefun-Legacy-Release-Preparation'}
    body = None if data is None else json.dumps(data).encode()
    if body is not None:
        headers['Content-Type'] = 'application/json'
    request = urllib.request.Request('https://api.github.com/repos/' + REPO + '/' + path, data=body, headers=headers, method='GET' if data is None else 'POST')
    with urllib.request.urlopen(request, timeout=45) as response:
        return json.load(response)


def main():
    require(os.environ.get('GITHUB_REPOSITORY') == REPO and os.environ.get('GITHUB_ACTIONS') == 'true', 'Wrong execution environment')
    actual = subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip()
    require(actual == SOURCE, 'Wrong source checkout')
    require(api('git/ref/heads/master')['object']['sha'] == SOURCE, 'Master advanced; review newer work before staging')
    require(api('git/ref/tags/v4.1.70')['object']['sha'] == BASELINE, 'Published baseline tag changed')
    require(not any(x['ref'] == 'refs/tags/v4.1.71' for x in api('git/matching-refs/tags/v4.1.71')), 'Release identity already used')
    require(not any(x['ref'] == 'refs/heads/' + BRANCH for x in api('git/matching-refs/heads/' + BRANCH)), 'Candidate branch already exists')
    frozen = {p: Path(p).read_bytes() for p in ['compatibility/sfl-addon-release-matrix.json', 'compatibility/resource-pack-release.json']}
    matrix = json.loads(frozen['compatibility/sfl-addon-release-matrix.json'])
    require(matrix['bundle_revision'] == 126 and len(matrix['addons']) == 45, 'Unexpected canonical set')
    require(next(a['source_commit'] for a in matrix['addons'] if a['repository'] == 'wickidcow/SF_JustEnoughGuide') == '8863c2609fa7bbda7cacc1ee29e229d05850b0a6', 'Unexpected JEG pin')
    changes = {}
    props = Path('gradle.properties').read_text()
    props = replace_once(props, 'projectVersion=4.1.70', 'projectVersion=4.1.71')
    props = replace_once(props, '# Slimefun Legacy 4.1.70 - Coordinated core and maintained addon stabilization', '# Slimefun Legacy 4.1.71 - Projector and guide maintenance')
    props = replace_once(props, '# Coordinated addon source manifest revision: 125', '# Coordinated addon source manifest revision: 126')
    changes['gradle.properties'] = props
    for name in REGISTRIES:
        path = 'compatibility/' + name
        changes[path] = transform_registry(Path(path).read_text())
    changes['compatibility/release-baselines.json'] = transform_baseline(Path('compatibility/release-baselines.json').read_text())
    changes['README.md'] = transform_readme(Path('README.md').read_text())
    path = 'docs/release-candidates/4.1.71.md'
    require(not Path(path).exists(), 'Readiness ledger already exists')
    changes[path] = NOTES
    for path, text in changes.items():
        Path(path).parent.mkdir(parents=True, exist_ok=True)
        Path(path).write_text(text)
    require(all(Path(p).read_bytes() == data for p, data in frozen.items()), 'Addon or resource-pack pin changed')
    subprocess.run(['git', 'diff', '--check'], check=True)
    subprocess.run(['python3', 'scripts/verify_legacy.py', '.'], check=True)
    changed = set(subprocess.check_output(['git','diff','--name-only'], text=True).splitlines())
    new = set(subprocess.check_output(['git','ls-files','--others','--exclude-standard'], text=True).splitlines())
    require(changed == set(changes) - {'docs/release-candidates/4.1.71.md'} and new == {'docs/release-candidates/4.1.71.md'}, 'Unexpected worktree changes')
    require(api('git/ref/heads/master')['object']['sha'] == SOURCE, 'Master moved during verification')
    # Create a fresh branch through Git Data API; never advance master or an existing branch.
    base = api('git/commits/' + SOURCE)
    entries = [{'path': p, 'mode': '100644', 'type': 'blob', 'content': text} for p,text in sorted(changes.items())]
    tree = api('git/trees', {'base_tree': base['tree']['sha'], 'tree': entries})
    commit = api('git/commits', {'message': 'chore(release): prepare coordinated 4.1.71 with addon revision 126\n\nAssign a new distribution identity to the validated revision-126 source, including the merged projector UI improvement and published JEG 2.1.72. Advance the regression baseline to the actually published 4.1.70 source. Align release metadata and readiness documentation only; preserve production Java, all addon pins, pack pin, compatibility policies and historical test fixtures.\n\nThe full source-invariant verifier passed in the staging job. Fresh exact-candidate build/runtime/database/API/bundle checks and post-merge reproducible packaging remain required; this commit is not publication. No existing release asset or live-server data was changed.', 'tree': tree['sha'], 'parents': [SOURCE]})
    api('git/refs', {'ref': 'refs/heads/' + BRANCH, 'sha': commit['sha']})
    Path('staging-result.json').write_text(json.dumps({'source':SOURCE,'candidate':commit['sha'],'branch':BRANCH,'tree':tree['sha'],'files':sorted(changes),'source_invariants':'PASS','published':False},indent=2)+'\n')
    print(Path('staging-result.json').read_text())


if __name__ == '__main__':
    main()
