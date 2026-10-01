from pathlib import Path
import hashlib,json,os,re,shutil,subprocess,sys,time
version=sys.argv[1]
root=Path('build')/('betterchests-native-'+version)
assert not root.exists(), 'Refuse to overwrite an existing runtime directory'
root.mkdir(parents=True);(root/'plugins').mkdir()
def download(url,path):
    subprocess.run(['curl','--fail','--location','--silent','--show-error','--retry','4','--retry-delay','2','--connect-timeout','15','--max-time','180','-H','User-Agent: Albion-Addon-Preservation/1.0 (https://github.com/wickidcow/Slimefun-Legacy)','-o',str(path)+'.partial',url],check=True)
    Path(str(path)+'.partial').replace(path)
metadata=root/'paper-builds.json'
download('https://fill.papermc.io/v3/projects/paper/versions/'+version+'/builds',metadata)
rows=json.loads(metadata.read_text());assert isinstance(rows,list) and rows
if version in ('1.21.11','26.2'):
    rows=[x for x in rows if x.get('channel')=='STABLE']
assert rows
build=max(rows,key=lambda x:x['id']);server=build['downloads']['server:default']
download(server['url'],root/'server.jar')
checksum=server.get('checksums',{}).get('sha256');assert checksum,server
assert hashlib.sha256((root/'server.jar').read_bytes()).hexdigest()==checksum
(root/'runtime-build.json').write_text(json.dumps({'minecraft':version,'build':build['id'],'channel':build['channel'],'sha256':checksum},indent=2))
core=Path('native-input/Slimefun-Legacy4.1.63.jar');assert hashlib.sha256(core.read_bytes()).hexdigest()=='993b257c54efe19b58cc8f4946d4d39387f7787fe263c9f27123343159344c42'
new=Path('native-input/SF_BetterChests1.0.3.jar');assert hashlib.sha256(new.read_bytes()).hexdigest()=='d78d0ce8b0fc20b2d882931d3ee988fd681cddc3bcb4d638b56abac54165d818'
old=Path('native-input/SF_BetterChests1.0.2.jar');assert old.is_file()
shutil.copy2(core,root/'plugins/Slimefun.jar');shutil.copy2('native-input/BetterChestsProbe.jar',root/'plugins/BetterChestsProbe.jar')
(root/'eula.txt').write_text('eula=true\n')
(root/'server.properties').write_text('online-mode=false\nlevel-name=probe-world\nlevel-type=minecraft:normal\nmax-players=1\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\npause-when-empty-seconds=-1\nserver-ip=127.0.0.1\nserver-port=25571\nenable-query=false\nenable-rcon=false\n')
results=[]
for phase,plugin in (('seed',old),('upgrade',new),('restart',new)):
    shutil.copy2(plugin,root/'plugins/BetterChests.jar')
    log=root/(phase+'.console.log')
    with log.open('w') as output:
        process=subprocess.Popen(['java','-Xms512M','-Xmx2G','-Dprobe.phase='+phase,'-jar','server.jar','--nogui'],cwd=root,stdin=subprocess.PIPE,stdout=output,stderr=subprocess.STDOUT,text=True)
        passed=False;deadline=time.monotonic()+300
        try:
            while time.monotonic()<deadline and process.poll() is None:
                text=log.read_text(errors='replace')
                if 'PROBE_FAIL '+phase in text:break
                if 'PROBE_PASS '+phase in text:passed=True;break
                time.sleep(1)
            if process.poll() is None:
                if passed:
                    process.stdin.write('sf doctor status\n');process.stdin.flush()
                    time.sleep(3)
                process.stdin.write('stop\n');process.stdin.flush()
            process.wait(timeout=90)
        finally:
            if process.poll() is None:process.kill();process.wait()
    text=log.read_text(errors='replace')
    normalized=re.sub(r'\x1b\[[0-9;?]*[ -/]*[@-~]','',text)
    normalized=re.sub(r'§[0-9A-FK-ORa-fk-or]','',normalized)
    (root/(phase+'.normalized.log')).write_text(normalized)
    assert passed and process.returncode==0,(phase,text[-16000:])
    assert not re.search(r'PROBE_FAIL|NoClassDefFoundError|NoSuchMethodError|IncompatibleClassChangeError|InvalidConfigurationException|Error occurred while enabling',normalized),(phase,text[-16000:])
    if phase!='seed':assert 'Previous clean shutdown: Yes' in normalized or re.search(r'previous shutdown\s+Clean',normalized),phase
    result={'phase':phase,'plugin_sha256':hashlib.sha256(plugin.read_bytes()).hexdigest(),'passed':True}
    results.append(result);print(result,flush=True)
(root/'result.json').write_text(json.dumps({'runtime':json.loads((root/'runtime-build.json').read_text()),'results':results,'scope':'Real item, block database, inventories, retained readers and three independent server processes; generated fixtures, not every customer world.'},indent=2))
