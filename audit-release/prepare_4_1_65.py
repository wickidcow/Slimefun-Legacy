from pathlib import Path
import copy
import hashlib
import json
import subprocess

BASE = 'dfb42878b39aae2e60dcd046f147f0911b1fb5bf'
PREVIOUS = 'e47a3035b35dfb93325d22ba03b2a69dd6cd6992'
EXPECTED = {
    'gradle.properties': '63dd3ed195ffd91fb49ed7788aa02af215d25277',
    'README.md': 'dbf7d6d3d3821fdfd9ac66a53e5e27b44ca15f69',
    'compatibility/addon-compatibility-matrix.json': 'ad4d7464e51c1c75d7e10c5b55c08cf4726a7947',
    'compatibility/core-api-registry.json': 'b5b5d7cbb4bf5bc58511912a57c7535cda39e03f',
    'compatibility/cross-fork-api-matrix.json': '2d6dafcc80f1d9f2cb9fe50885476a1b2d08ac11',
    'compatibility/support-contract.json': 'fea7c7525ae43e66d7c2d06597c2f1d4d0e5af97',
    'compatibility/release-baselines.json': 'f3fa2fb5b281b7a70e004703005e8043b35498c3',
}

def blob(data):
    return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()

for name, expected in EXPECTED.items():
    assert blob(Path(name).read_bytes()) == expected, f'Concurrent metadata change: {name}'
manifest_path = Path('compatibility/sfl-addon-release-matrix.json')
manifest_before = manifest_path.read_bytes()
assert blob(manifest_before) == '2b5b762949e7b3ec4d9fa413b13c9ad7cce02dd2'
manifest = json.loads(manifest_before)
assert manifest['bundle_revision'] == 106 and len(manifest['addons']) == 45
ledger_before = Path('compatibility/maintained-addon-test-ledger.json').read_bytes()

for name in EXPECTED:
    path = Path(name)
    if name.startswith('compatibility/') and name != 'compatibility/release-baselines.json':
        original = json.loads(path.read_text())
        changed = copy.deepcopy(original)
        assert changed['release'] == '4.1.64'
        changed['release'] = '4.1.65'
        path.write_text(json.dumps(changed, indent=2) + '\n')
        changed['release'] = original['release']
        assert changed == original

path = Path('compatibility/release-baselines.json')
original = json.loads(path.read_text())
changed = copy.deepcopy(original)
assert changed['candidate']['version'] == '4.1.64'
assert changed['previous_stable']['version'] == '4.1.63'
assert changed['previous_stable']['source']['ref'] == '2703e3a500b426849f3eb7806862bb4343bb9d6a'
changed['candidate']['version'] = '4.1.65'
changed['previous_stable']['version'] = '4.1.64'
changed['previous_stable']['source']['ref'] = PREVIOUS
assert changed['legacy_floor'] == original['legacy_floor']
assert changed['policy'] == original['policy']
path.write_text(json.dumps(changed, indent=2) + '\n')

path = Path('gradle.properties')
text = path.read_text()
assert text.count('projectVersion=4.1.64') == 1
text = text.replace('projectVersion=4.1.64', 'projectVersion=4.1.65')
text = text.replace('# Slimefun Legacy 4.1.64 - Cancelled-removal persistence, legacy colors and coordinated addons',
                    '# Slimefun Legacy 4.1.65 - Inventory snapshot and record-key ownership safety')
text = text.replace('# Coordinated addon source manifest revision: 104', '# Coordinated addon source manifest revision: 106')
path.write_text(text)

path = Path('README.md')
text = path.read_text()
old = 'Maintenance release line: **4.1.64 — Storage Deletion Safety & Coordinated Addon Updates**. The previous published stable baseline is **4.1.63**.'
new = 'Maintenance release line: **4.1.65 — Inventory Snapshot & Record-Key Safety**. The previous published stable baseline is **4.1.64**.'
assert text.count(old) == 1
text = text.replace(old, new)
text = text.replace('Slimefun Legacy 4.1.64 maintenance builds', 'Slimefun Legacy 4.1.65 maintenance builds')
path.write_text(text)

release = Path('docs/releases/4.1.65.md')
assert not release.exists()
release.write_text('''# Slimefun Legacy 4.1.65

This maintenance release carries the merged inventory-snapshot and record-key ownership protections while retaining the complete 45-addon revision-106 selection. The previous published stable regression baseline is 4.1.64 at `e47a3035b35dfb93325d22ba03b2a69dd6cd6992`.

## Inventory and backpack save safety

Inventory snapshots represent the contents already acknowledged by storage. Their public exports and list-constructor inputs previously shared mutable item/pair objects with that baseline. An external mutation could make an unsaved quantity or metadata change appear already saved. The corrected implementation owns its baseline and returns detached exports, so the normal block-inventory, universal-storage and backpack save paths still detect those changes.

Exports remain editable and existing addon subclass getter hooks remain supported. Ordinary core comparisons use the private baseline without cloning every item again; copies occur at ownership/export boundaries. Empty snapshot entries no longer share a publicly mutable sentinel. Item IDs, exact metadata, independently recorded amounts and the established comparison rules are preserved. This fix cannot reconstruct changes lost before it was installed.

## Record-key ownership

Record keys now own empty input collections just as they already owned nonempty collections. Immutable empty inputs no longer prevent supported additions, and caller/shared collection mutations no longer silently change a key or bypass its cached identity invalidation. Field semantics, condition ordering, public mutation methods, textual identity and database formats remain unchanged. The convenience constructors avoid allocating an extra throwaway collection at that copying boundary.

## Coordinated addons

The canonical bundle retains all 45 tested revision-106 source selections, including Slimefun Legacy Guide/JustEnoughGuide 2.1.70, ExtraHeads 1.0.4 and FluffyMachines 26.2.14. The supported-floor JEG distributable build is retained alongside the separate 26.3 compilation probe. This release does not invent new addon versions or remove any maintained plugin to obtain a successful build. Individual plugin releases remain raw installable JARs; the ZIP is the aggregate bundle.

## Preservation and validation scope

No item/research IDs, saved key types, codecs, storage schemas, backpack identities, recipes, menus, capacities, machine production rates, transfer priorities or migration defaults are changed by these fixes. No world-wide conversion or presentation rebuild is enabled. Minecraft 1.21.11 is the runtime floor, not a cutoff for the age of saved items. Paper 26.3 is the primary target; Paper/Purpur are prioritized, while Folia/derivative gameplay and cross-region addon safety remain qualified.

The merged fixes have original-code regression controls, exact-source project tests and native Paper item comparisons. The snapshot tests cover all three inventory controller families with reopened SQLite test stores and separately check real Paper cloning/serialization, including exact old IDs, names/lore, charge, large counts, byte arrays and nested owner data. These are generated fixtures, not a certification of every captured customer world, populated machine, crash, future API or cross-fork rollback. No measured server-TPS gain is claimed.

The versioned release must pass its own core/API/platform checks, complete same-source addon build and restart checks, and reproducible publication workflow. Earlier PR evidence is not relabeled as the eventual release artifact. Release status and verified asset hashes belong to the actual publication record.

## Safe installation

Stop the server normally and back up worlds, player data, all plugin directories and databases. Test the matching core and intended addon JARs on a disposable copy first. Replace the existing core/addon JARs instead of leaving old and new copies together; do not install unwanted addons merely because they are in the bundle. Do not use live reload for this upgrade.

Keep existing item/addon registrations and migration settings unless deliberately changing them. Review startup diagnostics and `/sf doctor` before any optional repair or conversion. A refused or unreadable record is a recovery problem, not an empty inventory to overwrite. Plugin rollback does not promise Minecraft world-version downgrade compatibility.
''')

assert manifest_path.read_bytes() == manifest_before
assert Path('compatibility/maintained-addon-test-ledger.json').read_bytes() == ledger_before
files = list(EXPECTED) + [str(release)]
changed_files = subprocess.check_output(['git', 'diff', '--name-only'], text=True).splitlines()
assert set(changed_files) == set(EXPECTED), changed_files
Path('evidence/metadata-files.json').write_text(json.dumps(files, indent=2) + '\n')
Path('evidence/scope.json').write_text(json.dumps(dict(base=BASE, previous_stable=PREVIOUS,
    version='4.1.65', addon_revision=106, addons=45, addon_manifest_blob=blob(manifest_before),
    addon_ledger_unchanged=True, runtime_sources_changed=False), indent=2) + '\n')
print('Scoped release metadata prepared; runtime sources, all 45 addon pins and historical evidence remain unchanged.')
