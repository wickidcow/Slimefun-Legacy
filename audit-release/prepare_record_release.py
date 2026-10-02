from pathlib import Path
import json
import subprocess

paths = [
    'README.md', 'gradle.properties',
    'compatibility/addon-compatibility-matrix.json',
    'compatibility/core-api-registry.json',
    'compatibility/cross-fork-api-matrix.json',
    'compatibility/support-contract.json',
    'compatibility/release-baselines.json',
]
for name in paths:
    path = Path(name)
    text = path.read_text()
    if name == 'compatibility/release-baselines.json':
        document = json.loads(text)
        assert document['candidate']['version'] == '4.1.65'
        assert document['previous_stable']['version'] == '4.1.64'
        assert document['previous_stable']['source']['ref'] == 'e47a3035b35dfb93325d22ba03b2a69dd6cd6992'
        document['candidate']['version'] = '4.1.66'
        document['previous_stable']['version'] = '4.1.65'
        document['previous_stable']['source']['ref'] = '82427167200e1b349b53c8c81f2373a5b36e340f'
        text = json.dumps(document, indent=2) + '\n'
    elif name.startswith('compatibility/'):
        assert text.count('"release": "4.1.65"') == 1
        text = text.replace('"release": "4.1.65"', '"release": "4.1.66"')
    else:
        assert '4.1.65' in text
        text = text.replace('4.1.65', '4.1.66').replace('4.1.64', '4.1.65')
        text = text.replace('Inventory Snapshot & Record-Key Safety', 'Record Condition Ownership Safety')
        text = text.replace('Inventory snapshot and record-key ownership safety', 'Record condition ownership and retained-view safety')
    path.write_text(text)
notes = Path('docs/releases/4.1.66.md')
notes.write_text('''# Slimefun Legacy 4.1.66

This maintenance release isolates mutable query-condition entries from record-key identity while preserving the complete 45-addon revision-106 selection. The previous published stable baseline is 4.1.65 at `82427167200e1b349b53c8c81f2373a5b36e340f`.

## Record selection safety

An externally changed condition pair could redirect a generated SQL condition while the cached key still described the original record. RecordKey now owns constructor entries and returns detached entries through its structurally read-only live view. Explicit addCondition operations, condition order, duplicate and opaque values, public descriptors, and normal key equality/text rules remain intact. A caller can edit an exported pair without modifying the underlying query.

Retained sublists delegate structural-change checks to the real linked backing list. Root views remain live and fresh sublists remain available after supported additions. This preserves single-threaded invalidation behavior, not a guarantee for concurrent mutation of query keys.

## Existing data and addons

No item/research IDs, field names or types, inventory codecs, SQL schemas, backpack/storage identities, recipes, menus, capacities, machine output rates, transfer priorities, save cadence or migration defaults change. No data conversion or repair is automatically enabled, and this fix cannot reconstruct earlier misdirected operations.

All 45 revision-106 addon source selections remain unchanged and must be rebuilt against the exact release core. Existing standalone addon versions are retained; no new addon version is invented merely to refresh the aggregate ZIP. The core is published as a raw drop-in JAR and the full addon set as SF_Addons_1.21.11-26.3.zip.

## Validation scope

The correction has focused ownership/view regression tests, original-code negative controls and generated JDBC SQLite SELECT/UPDATE/DELETE tests using the actual SQL renderer and reopened disk stores. Its release requires the versioned core/API checks, full maintained compiler, matching 45-addon build, supported-platform restart checks and byte-identical clean-build publication checks. Earlier patch or PR evidence is not relabeled as the release's results.

These tests do not certify every captured customer world, populated machine, live-client interaction, MySQL/PostgreSQL deployment, Folia region interaction, crash, future server API or cross-fork rollback. Minecraft 1.21.11 remains the runtime floor, not the age limit of saved items. Paper 26.3 remains the primary target with Paper/Purpur prioritized and derivative-server safety qualified. No measured TPS gain is claimed.

## Installation

Stop the server normally and back up worlds, player data, plugin directories and databases. Test the matching core and intended addons on a disposable copy. Replace existing JARs rather than leaving duplicate old/new files, and do not use live reload. Keep existing registrations and migration settings unless deliberately changing them. Review startup logs and /sf doctor before optional repairs; unreadable records are recovery tasks, not empty inventories to overwrite.
''')
paths.append(str(notes))
base = '6a927285cb2d7a73e72818289d7b0cd5f9e05b8f'
for path in ['src/main', 'src/test', 'compatibility/sfl-addon-release-matrix.json', 'compatibility/maintained-addon-test-ledger.json']:
    assert not subprocess.check_output(['git', 'diff', base, '--', path]), path
for name in paths:
    target = Path('evidence/release-source') / name
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_bytes(Path(name).read_bytes())
Path('evidence/release-paths.json').write_text(json.dumps(paths, indent=2) + '\n')
