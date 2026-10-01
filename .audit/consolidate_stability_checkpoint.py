from pathlib import Path
import base64,hashlib,io,json,subprocess,xml.etree.ElementTree as ET,zipfile

CORE='wickidcow/Slimefun-Legacy'
PARENT='ed0618b442cf0b74c62f2d3990c32727ed376e78'
root=Path('candidate')
out=Path('checkpoint-evidence');out.mkdir(exist_ok=True)

def api(path):
    return subprocess.run(['gh','api',path],capture_output=True,check=True,timeout=180).stdout

def archive(repo,id,digest):
    raw=api(f'repos/{repo}/actions/artifacts/{id}/zip')
    assert hashlib.sha256(raw).hexdigest()==digest,(repo,id)
    return zipfile.ZipFile(io.BytesIO(raw))

def blob(data):return hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()

def single_json(z,suffix):
    paths=[n for n in z.namelist() if n.endswith(suffix)];assert len(paths)==1,paths
    return json.loads(z.read(paths[0]))

def xml_totals(z):
    totals=dict(tests=0,failures=0,errors=0,skipped=0)
    for name in z.namelist():
        if name.endswith('.xml') and Path(name).name.startswith('TEST-'):
            test=ET.fromstring(z.read(name));assert test.tag=='testsuite'
            for key in totals:totals[key]+=int(test.get(key,'0'))
    return totals

with archive(CORE,11135721640,'68f9bb246abfb9cb547e6b009314245743b11a96006eda660b1e63bee3aa3747') as z:
    rows=single_json(z,'maintained-test-results.json')
assert len(rows)==45
index={row['repository']:row for row in rows};assert len(index)==45
for row in rows:
    row['evidence']=dict(repository=CORE,run_id=36800704863,artifact_id=11135721640,kind='Full project build/test audit summary')

rechecks=[
 ('networksexp',11136600886,'8a5c4128abb722bfe4be3b3ee632d0a2c2ae9792ea3e208e7d5f07e8f709835d',36802625180),
 ('infinityexpansion2',11135473622,'c974c6d8a506ca7e4febb3ed9e7bb94f624f61144d48060acc9a6b29ae6f1a0b',36802625180),
 ('supreme',11136104481,'01b4e0f9487be3038a6f3a7fe1bfc0f430bcb9271d325d7974ec3282f1447556',36802625180),
 ('dracfunreborn',11136448295,'78e79b5f3dc05cb11266d4e53a84b847ed995ce4f289ddcfba0de963e0d995b2',36802625180),
 ('magicexpansion',11136900056,'6b90259ba61f31c41b317de103c6fb99137554a64f5069186d9fe712e5d2f0e0',36803033374),
 ('militaryarsenal',11136098280,'f81fb0e36c7333e11f48651298244fd2754e803e7c78fa939f877274535e4245',36803033374),
 ('mobdrops',11136083439,'b328666267b37027669acdd78a5dd14ad4ca882a5afb71c61de7cc2c8a26bf5e',36803033374),
 ('rykenslimecustomizer',11136478789,'f8b97270c1c88d264bc43ed57e056a6e26fd6480601dbbb9536d6aca153812c5',36803033374)]
for slug,id,digest,run in rechecks:
    with archive(CORE,id,digest) as z:
        row=single_json(z,'result.json');totals=xml_totals(z)
        assert row['slug']==slug and row['result']=='PASS'
        assert totals['tests']==row['reported_entries'] and all(totals[k]==0 for k in ['failures','errors','skipped'])
        row['evidence']=dict(repository=CORE,run_id=run,artifact_id=id,sha256=digest,kind='Full build plus individually checked XML')
        row['setup_notes']=single_json(z,'setup-adapters.json') if any(n.endswith('setup-adapters.json') for n in z.namelist()) else []
        index[row['repository']]=row

# These two promoted heads are validated by a complete exact-core build plus
# source-blob identity checks, not mislabeled as a checkout of the final commit.
specials=[
 ('wickidcow/SF_FinalTECH','7392ee61f4af3cbd779ee3c5267131d721311b67',11135622265,'eb6e48c6235f3c24eedbc8d5df1a4f0ee074a9a614bce2ca4affb4da7a339568',36801120276,34,'validated-blobs.json'),
 ('wickidcow/SF_CrystamaeHistoria','0ab1916025ffcdca48e36623b5a1752a4757ebf5',11135822494,'80c4f498bbf7c45afac83117d37e51383a96faaa2d1d411d452b920810d5fd3e',36802165446,13,'reviewed-blobs.json')]
for repo,commit,id,digest,run,count,manifest_name in specials:
    with archive(repo,id,digest) as z:
        totals=xml_totals(z);assert totals==dict(tests=count,failures=0,errors=0,skipped=0),totals
        blobs=single_json(z,manifest_name)
        for name,expected in blobs.items():
            actual=json.loads(api(f'repos/{repo}/contents/{name}?ref={commit}'))
            assert actual['sha']==expected,(repo,name,actual['sha'],expected)
        row=index[repo]
        row.update(commit=commit,result='PASS',reported_entries=count,failures=0,errors=0,skips=0,
            evidence=dict(repository=repo,run_id=run,artifact_id=id,sha256=digest,
                kind='Full exact-core build and XML; promoted changed-source blobs verified',source_blobs=blobs))

matrix_path=root/'compatibility/sfl-addon-release-matrix.json'
matrix=json.loads(matrix_path.read_text());assert matrix['bundle_revision']==95
assert subprocess.check_output(['git','-C',str(root),'rev-parse','HEAD'],text=True).strip()==PARENT
changes={
 'wickidcow/SF_DankTech2':'1566348da2bb319aa178dd331e2ca2c4ca3d26ce',
 'wickidcow/SF_CultivationLegacy':'4d2ad689ec70c04bfc1dfbe37c8dfd9c59a4fec0',
 'wickidcow/SF_CrystamaeHistoria':'0ab1916025ffcdca48e36623b5a1752a4757ebf5',
 'wickidcow/SF_RykenSlimeCustomizer':'6b165f9efafad8e016e80cb3b146127c6314d6c6'}
for addon in matrix['addons']:
    if addon['repository'] in changes:addon['source_commit']=changes[addon['repository']]
    assert addon['source_commit']==index[addon['repository']]['commit'],addon
matrix['bundle_revision']=96
matrix_path.write_text(json.dumps(matrix,indent=2)+'\n')

# Restore only reviewed tooling from the successful negative-control/source-guard run.
with archive(CORE,11135398645,'e08125567b0e038d586af131594ba8e5fc231c7fdeba1895ee015867341243c3') as z:
    tooling=json.loads(z.read('coordinate-blobs.json'))
    for name,expected in tooling.items():
        data=z.read('tooling/'+name);assert blob(data)==expected
        (root/name).write_bytes(data)

ledger_rows=[]
for addon in matrix['addons']:
    row=index[addon['repository']];assert row['result']=='PASS' and not(row['failures'] or row['errors'] or row['skips'])
    ledger_rows.append(dict(repository=row['repository'],slug=row['slug'],source_commit=row['commit'],
        full_build_result=row['result'],reported_test_entries=row['reported_entries'],failures=row['failures'],
        errors=row['errors'],skips=row['skips'],evidence=row['evidence'],setup_notes=row.get('setup_notes',[]),
        coverage='Existing automated test entries' if row['reported_entries'] else 'No automated test entries reported; not gameplay certification'))
summary=dict(addons=len(ledger_rows),full_build_passed=len(ledger_rows),
    reported_test_entries=sum(r['reported_test_entries'] for r in ledger_rows),
    addons_with_reported_tests=sum(r['reported_test_entries']>0 for r in ledger_rows),
    addons_without_reported_tests=sum(r['reported_test_entries']==0 for r in ledger_rows),failures=0,errors=0,skips=0)
assert summary==dict(addons=45,full_build_passed=45,reported_test_entries=227,
                    addons_with_reported_tests=12,addons_without_reported_tests=33,failures=0,errors=0,skips=0),summary
ledger=dict(schema=1,bundle_revision=96,core_parent=PARENT,
    tested_core_sha256='ffbdfcbf0eab274eaaf56916f8ddc3f12347e76a7b07fa4b8d8fe25eabac7ace',
    summary=summary,limitations=[
      'Results consolidate multiple exact-source runs; not one single all-green workflow run.',
      'The final candidate bundle and full stack must independently validate this manifest.',
      'No tests reported means no automated test coverage, not complete gameplay proof.',
      'MobDrops tests use their declared Paper26.2/MockBukkit4.116.1 environment; baseline compile is separate.',
      'FinalTECH and final Crystamae builds used verified changed-source blobs before clean promotion.',
      'Generated old-addon storage fixtures are not captured historical player worlds.',
      'Deprecated APIs and broader machine/performance audits remain.'],addons=ledger_rows)
ledger_path=root/'compatibility/maintained-addon-test-ledger.json'
ledger_path.write_text(json.dumps(ledger,indent=2)+'\n')
notes='''# Stability and full-test checkpoint — bundle revision 96

This candidate preserves the entire revision-95 source set, including concurrent component-preservation fixes, and changes only four addon pins: DankTech2, Cultivation, CrystamaeHistoria and RykenSlimeCustomizer. All 45 maintained sources remain pinned; inclusions/exclusions and version 4.1.62 are unchanged.

## Three actual persisted-data failure paths corrected

DankTech2 PR4 refuses unreadable pack registries before registering handlers. Cultivation PR5 refuses unreadable experience, codex and owner configuration. CrystamaeHistoria PR4 strictly loads five managed files and explicitly stops because InfinityLib otherwise catches its startup exception while leaving it enabled. These candidates serialize completely before staged file replacement and preserve existing file symlinks/POSIX modes. No IDs, typed data, inventories, recipes, rates or gameplay formulas are changed.

All 38 new permanent YAML/filesystem tests passed. Actual old-addon controls and corrected runtimes completed 72 server cycles across Paper1.21.11/Java21, Paper26.2/Java25 and Paper26.3beta140/Java25: DankTech2 15, Cultivation27 and CrystamaeHistoria30. Corrupt original files remained unchanged under the corrected loaders. Valid old pack contents/progress/settings were byte-identical after upgrade and restart within each server line. These are generated old-addon fixtures, not every historical world, third-party item subclass or absolute power-loss guarantees. See the addon PRs and their retained evidence documents for exact logs and hashes.

## Complete build/test coverage ledger

`compatibility/maintained-addon-test-ledger.json` records all 45 sources and the exact evidence behind each result. All full-build outcomes are resolved successfully. The existing suites report 227 entries across 12 addons, with zero failures/errors/skips. The other 33 addons report no automated test entries; their build success is not a gameplay certificate. Results combine exact-source runs and verified promoted source blobs, not one single 45-job run.

The five original audit failures were resolved without weakening gameplay assertions: DracFun uses its declared Gradle9 toolchain, MobDrops uses its declared Paper26.2 test API and unchanged MockBukkit4.116.1, and the exact-core build comparator now recognizes only the canonical maintained Legacy coordinate. Ryken PR8 replaces an unavailable upstream guide dependency with the published maintained compile-only API; normal repository and exact-core builds pass, and the guide implementation is not shaded. Ten new comparator regressions reject unrelated addon names and preserve existing fork/property/scope behavior. This is build tooling, not an item or storage conversion.

## Full-stack and release boundary

The prior revision-93 bundle passed two real boots of all45 maintained addons on each server line when supplied with the proper external WorldEdit provider:7.3.19 for the Java21 floor and7.4.6-beta-02 for newer Java25 lines. That provider is not added to the maintained release ZIP. Later component updates and the four new preservation/build-source pins require a new exact-source bundle and three-version runtime validation; the old archive is not substituted for this revision.

Remaining work includes deprecated-API cleanup, wider machine behavior/load/concurrency tests, and captured historical-world validation. No master merge, version bump, stable release, production installation or automatic migration is performed by this checkpoint. Individual plugin artifacts remain raw JARs; the aggregate bundle remains the existing45-addon ZIP.
'''
notes_path=root/'docs/stability-and-full-test-checkpoint.md';notes_path.write_text(notes)
changed=list(tooling)+['compatibility/sfl-addon-release-matrix.json','compatibility/maintained-addon-test-ledger.json','docs/stability-and-full-test-checkpoint.md']
reviewed={name:blob((root/name).read_bytes()) for name in changed}
(out/'reviewed-blobs.json').write_text(json.dumps(reviewed,indent=2)+'\n')
(out/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')
for name in changed:
    target=out/'reviewed-source'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((root/name).read_bytes())
print(json.dumps(summary,indent=2))
