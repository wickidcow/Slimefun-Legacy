from pathlib import Path
import hashlib,json,shutil,subprocess,sys,urllib.request
version=sys.argv[1];inputs=Path('cryst-inputs').resolve();root=Path('cryst-runtime');root.mkdir()
headers={'User-Agent':'CrystamaeHistoria-Preservation-Regression/1.0'}
with urllib.request.urlopen(urllib.request.Request(f'https://fill.papermc.io/v3/projects/paper/versions/{version}/builds',headers=headers),timeout=60) as response:builds=json.load(response)
stable=[b for b in builds if b['channel']=='STABLE'];selected=max(stable or builds,key=lambda b:b['id'])
with urllib.request.urlopen(urllib.request.Request(selected['downloads']['server:default']['url'],headers=headers),timeout=90) as response:(root/'paper.jar').write_bytes(response.read())
scenarios=[(kind,file) for file in ['player_stats.yml','spells.yml'] for kind in ['old-control','new-refusal']]
scenarios += [('new-refusal',file) for file in ['blocks.yml','generic-stories.yml','block_colors.yml']]
scenarios += [('upgrade',None)]
results=[]
for kind,bad_file in scenarios:
    name=kind+('-'+bad_file.removesuffix('.yml') if bad_file else '')
    work=root/name;data=work/'plugins/CrystamaeHistoria';data.mkdir(parents=True)
    for jar in ['core.jar','CrystRuntime.jar']:shutil.copy2(inputs/jar,work/'plugins'/jar)
    shutil.copy2(root/'paper.jar',work/'paper.jar');(work/'eula.txt').write_text('eula=true\n')
    (work/'server.properties').write_text('online-mode=false\nserver-ip=127.0.0.1\nserver-port=0\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\npause-when-empty-seconds=-1\n')
    (data/'player_stats.yml').write_text('# Original player data\n')
    (data/'spells.yml').write_text('HEAL: false\nunknown_external: Owner setting\n')
    poison=b"original: [unfinished\n"
    if bad_file:(data/bad_file).write_bytes(poison)
    originals={name:(data/name).read_bytes() for name in ['player_stats.yml','spells.yml']}
    cycles={'old-control':[('old.jar','control')],'new-refusal':[('new.jar','refuse')],
            'upgrade':[('old.jar','seed'),('new.jar','verify'),('new.jar','verify')]}[kind]
    snapshots=[]
    for index,(jar,mode) in enumerate(cycles):
        shutil.copy2(inputs/jar,work/'plugins/CrystamaeHistoria.jar')
        log=work/f'{index}-{mode}.log'
        with log.open('w') as output:
            run=subprocess.run(['java','-Xms512M','-Xmx1536M',f'-Dcrystamae.progressTest={mode}','-jar','paper.jar','--nogui'],cwd=work,stdout=output,stderr=subprocess.STDOUT,timeout=240)
        text=log.read_text(errors='replace')
        assert run.returncode==0 and f'CRYSTAMAE_PROGRESS_{mode.upper()}_PASS' in text and 'CRYSTAMAE_PROGRESS_FAIL' not in text,log
        if kind=='old-control':assert (data/bad_file).read_bytes()!=poison,'Unsafe original load was not reproduced'
        if kind=='new-refusal':
            assert (data/bad_file).read_bytes()==poison,'Unreadable input was replaced'
            assert 'Crystamae saving data.' not in text and 'Error occurred while disabling CrystamaeHistoria' not in text
            for name,payload in originals.items():assert (data/name).read_bytes()==payload,(kind,name)
        if kind=='upgrade':
            assert 'ERROR]' not in text and 'SEVERE]' not in text,log
            snapshot={name:(data/name).read_bytes() for name in ['player_stats.yml','spells.yml']}
            snapshots.append(snapshot)
            for name,payload in snapshot.items():(work/f'{index}-{mode}-{name}').write_bytes(payload)
        results.append(dict(scenario=name,mode=mode,result='PASS',addon_sha256=hashlib.sha256((inputs/jar).read_bytes()).hexdigest()))
    if kind=='upgrade':assert snapshots[0]==snapshots[1]==snapshots[2],'Existing progress/settings bytes changed on upgrade or restart'
(root/'results.json').write_text(json.dumps(dict(version=version,build=selected['id'],channel=selected['channel'],results=results),indent=2)+'\n')
print(json.dumps(results,indent=2))
