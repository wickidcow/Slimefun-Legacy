from pathlib import Path
import hashlib,json,shutil,subprocess,sys,urllib.request
version=sys.argv[1]
inputs=Path('progress-inputs').resolve()
root=Path('progress-runtime');root.mkdir(exist_ok=True)
headers={'User-Agent':'Cultivation-Player-Data-Preservation/1.0'}
with urllib.request.urlopen(urllib.request.Request(f'https://fill.papermc.io/v3/projects/paper/versions/{version}/builds',headers=headers),timeout=60) as response:
    builds=json.load(response)
stable=[row for row in builds if row['channel']=='STABLE'];selected=max(stable or builds,key=lambda row:row['id'])
with urllib.request.urlopen(urllib.request.Request(selected['downloads']['server:default']['url'],headers=headers),timeout=90) as response:
    (root/'paper.jar').write_bytes(response.read())
results=[]
scenarios=[(kind,file) for file in ['exp.yml','codex.yml','config.yml'] for kind in ['old-control','new-refusal']]+[('upgrade',None)]
for kind,bad_file in scenarios:
    scenario=kind+('-'+bad_file.removesuffix('.yml') if bad_file else '')
    work=root/scenario;(work/'plugins/Cultivation').mkdir(parents=True)
    for name in ['core.jar','ProgressRuntime.jar']:shutil.copy2(inputs/name,work/'plugins'/name)
    shutil.copy2(root/'paper.jar',work/'paper.jar')
    (work/'eula.txt').write_text('eula=true\n')
    (work/'server.properties').write_text('online-mode=false\nserver-ip=127.0.0.1\nserver-port=0\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\npause-when-empty-seconds=-1\n')
    data=work/'plugins/Cultivation'
    (data/'config.yml').write_text("auto-update: false\ndebug-messages: false\nowner-note: \"Owner's config\"\n")
    for name in ['exp.yml','codex.yml']:(data/name).write_text('# Original empty player file\n')
    poison=b"'2f017f3a-8442-4ef2-9fba-456789abcdef':\n  original: [unfinished\n"
    if bad_file:(data/bad_file).write_bytes(poison)
    original={name:(data/name).read_bytes() for name in ['exp.yml','codex.yml']}
    cycles={'old-control':[('old.jar','control')],'new-refusal':[('new.jar','refuse')],
            'upgrade':[('old.jar','seed'),('new.jar','verify'),('new.jar','verify')]}[kind]
    snapshots=[]
    for index,(jar,mode) in enumerate(cycles):
        shutil.copy2(inputs/jar,work/'plugins/Cultivation.jar')
        log=work/f'{index}-{mode}.log'
        with log.open('w') as output:
            run=subprocess.run(['java','-Xms512M','-Xmx1536M',f'-Dcultivation.progressTest={mode}','-jar','paper.jar','--nogui'],cwd=work,stdout=output,stderr=subprocess.STDOUT,timeout=240)
        text=log.read_text(errors='replace')
        assert run.returncode==0 and f'CULTIVATION_PROGRESS_{mode.upper()}_PASS' in text and 'CULTIVATION_PROGRESS_FAIL' not in text,log
        if kind=='old-control':assert (data/bad_file).read_bytes()!=poison,'Old unsafe loader was not reproduced'
        if kind=='new-refusal':
            assert (data/bad_file).read_bytes()==poison,'Malformed source was replaced'
            for name,payload in original.items():assert (data/name).read_bytes()==payload,(scenario,name)
            assert 'Cultivation saving data.' not in text
        if kind=='upgrade':
            assert 'ERROR]' not in text and 'SEVERE]' not in text,log
            snapshot={name:(data/name).read_bytes() for name in ['exp.yml','codex.yml']}
            snapshots.append(snapshot)
            for name,payload in snapshot.items():(work/f'{index}-{mode}-{name}').write_bytes(payload)
        results.append(dict(scenario=scenario,mode=mode,result='PASS',addon_sha256=hashlib.sha256((inputs/jar).read_bytes()).hexdigest()))
    if kind=='upgrade':assert snapshots[0]==snapshots[1]==snapshots[2],'Valid persisted player data changed on upgrade or restart'
(root/'results.json').write_text(json.dumps(dict(version=version,build=selected['id'],channel=selected['channel'],results=results),indent=2)+'\n')
print(json.dumps(results,indent=2))
