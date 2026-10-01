from pathlib import Path
import hashlib,json,re,shutil,subprocess,time
root=Path('build/buildingstaff-provider');assert not root.exists()
root.mkdir(parents=True);(root/'plugins').mkdir()
def download(url,path):
    subprocess.run(['curl','--fail','--location','--silent','--show-error','--retry','4','--connect-timeout','15','--max-time','180','-H','User-Agent: Albion-Addon-Preservation/1.0 (https://github.com/wickidcow/Slimefun-Legacy)','-o',str(path)+'.partial',url],check=True)
    Path(str(path)+'.partial').replace(path)
download('https://fill.papermc.io/v3/projects/paper/versions/26.2/builds',root/'builds.json')
rows=json.loads((root/'builds.json').read_text());build=max((r for r in rows if r.get('channel')=='STABLE'),key=lambda r:r['id'])
server=build['downloads']['server:default'];download(server['url'],root/'server.jar')
assert hashlib.sha256((root/'server.jar').read_bytes()).hexdigest()==server['checksums']['sha256']
inputs=json.loads(Path('provider-input/inputs.json').read_text())
for name,digest in inputs['jars'].items():
    p=Path('provider-input')/name;assert hashlib.sha256(p.read_bytes()).hexdigest()==digest
    shutil.copy2(p,root/'plugins'/name)
(root/'server.properties').write_text('online-mode=false\nlevel-name=provider-world\nlevel-type=minecraft:normal\nserver-ip=127.0.0.1\nserver-port=25572\nmax-players=1\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\npause-when-empty-seconds=-1\nenable-query=false\nenable-rcon=false\n')
(root/'eula.txt').write_text('eula=true\n')
results=[]
for phase in ('initial','restart'):
    log=root/(phase+'.console.log')
    with log.open('w') as output:
        p=subprocess.Popen(['java','-Xms768M','-Xmx3G','-Dprobe.phase='+phase,'-jar','server.jar','--nogui'],cwd=root,stdin=subprocess.PIPE,stdout=output,stderr=subprocess.STDOUT,text=True)
        passed=False
        try:
            deadline=time.monotonic()+360
            while time.monotonic()<deadline and p.poll() is None:
                text=log.read_text(errors='replace')
                if 'PROBE_FAIL '+phase in text:break
                if 'PROBE_PASS '+phase in text:passed=True;break
                time.sleep(1)
            if p.poll() is None:
                if passed:p.stdin.write('sf doctor status\n');p.stdin.flush();time.sleep(3)
                p.stdin.write('stop\n');p.stdin.flush()
            p.wait(timeout=90)
        finally:
            if p.poll() is None:p.kill();p.wait()
    text=log.read_text(errors='replace');text=re.sub(r'\x1b\[[0-9;?]*[ -/]*[@-~]|§[0-9A-FK-ORa-fk-or]','',text)
    (root/(phase+'.normalized.log')).write_text(text)
    assert passed and p.returncode==0,(phase,text[-24000:])
    assert not re.search(r'PROBE_FAIL|NoClassDefFoundError|NoSuchMethodError|IncompatibleClassChangeError|InvalidConfigurationException|Error occurred while enabling',text),(phase,text[-16000:])
    if phase=='restart':assert 'Previous clean shutdown: Yes' in text or re.search(r'previous shutdown\s+Clean',text)
    results.append({'phase':phase,'passed':True,'assertions':re.findall(r'PROBE_PASS .* assertions=(\d+)',text)})
    print(results[-1],flush=True)
(root/'result.json').write_text(json.dumps({'runtime':{'minecraft':'26.2','build':build['id'],'channel':build['channel']},'inputs':inputs,'results':results,'scope':'Actual Rebar/Pylon, Bukkit blocks, inventories and separate processes; explicit synthetic no-network Player facade.'},indent=2)+'\n')
