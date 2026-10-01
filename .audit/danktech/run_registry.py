from pathlib import Path
import hashlib,json,os,shutil,subprocess,sys,urllib.request
version=sys.argv[1]
inputs=Path('registry-inputs').resolve()
root=Path('registry-runtime');root.mkdir(exist_ok=True)
headers={'User-Agent':'DankTech2-Preservation-Regression/1.0 (https://github.com/wickidcow/SF_DankTech2)'}
request=urllib.request.Request(f'https://fill.papermc.io/v3/projects/paper/versions/{version}/builds',headers=headers)
with urllib.request.urlopen(request,timeout=60) as response: builds=json.load(response)
stable=[row for row in builds if row['channel']=='STABLE']
selected=max(stable or builds,key=lambda row:row['id'])
request=urllib.request.Request(selected['downloads']['server:default']['url'],headers=headers)
with urllib.request.urlopen(request,timeout=90) as response: (root/'paper.jar').write_bytes(response.read())
results=[]
for scenario in ['old-control','new-refusal','upgrade']:
    work=root/scenario
    (work/'plugins/DankTech2').mkdir(parents=True)
    for name in ['core.jar','RegistryRuntime.jar']: shutil.copy2(inputs/name,work/'plugins'/name)
    shutil.copy2(root/'paper.jar',work/'paper.jar')
    (work/'eula.txt').write_text('eula=true\n')
    (work/'server.properties').write_text('online-mode=false\nserver-ip=127.0.0.1\nserver-port=0\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\npause-when-empty-seconds=-1\n')
    registry=work/'plugins/DankTech2/dank_packs.yml'
    poisoned=b"'42':\n  last_user: ExistingOwner\n  item: [broken\n"
    if scenario!='upgrade': registry.write_bytes(poisoned)
    modes={'old-control':[('old.jar','control')], 'new-refusal':[('new.jar','refuse')],
           'upgrade':[('old.jar','seed'),('new.jar','verify'),('new.jar','verify')]}[scenario]
    for index,(jar,mode) in enumerate(modes):
        shutil.copy2(inputs/jar,work/'plugins/DankTech2.jar')
        log=work/f'{index}-{mode}.log'
        with log.open('w') as output:
            result=subprocess.run(['java','-Xms512M','-Xmx1536M',f'-Ddanktech.registryTest={mode}',
                '-jar','paper.jar','--nogui'],cwd=work,stdout=output,stderr=subprocess.STDOUT,timeout=240)
        text=log.read_text(errors='replace')
        assert result.returncode==0 and f'DANK_REGISTRY_{mode.upper()}_PASS' in text and 'DANK_REGISTRY_FAIL' not in text, log
        if scenario=='upgrade': assert 'ERROR]' not in text and 'SEVERE]' not in text,log
        if scenario=='new-refusal':
            assert registry.read_bytes()==poisoned, 'Unreadable registry was changed'
            assert 'DankTech2 saving data.' not in text
            assert 'DankTech2 is stopping to preserve the existing pack registry.' in text
        if scenario=='old-control': assert registry.read_bytes()!=poisoned, 'Old loader did not reproduce overwrite'
        results.append(dict(scenario=scenario,mode=mode,addon_sha256=hashlib.sha256((inputs/jar).read_bytes()).hexdigest(),
                            registry_sha256=hashlib.sha256(registry.read_bytes()).hexdigest(),result='PASS'))
(root/'results.json').write_text(json.dumps(dict(version=version,build=selected['id'],channel=selected['channel'],results=results),indent=2)+'\n')
print(json.dumps(results,indent=2))
