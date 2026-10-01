from pathlib import Path
import hashlib,json,os,shutil,subprocess,sys,xml.etree.ElementTree as ET
root=Path.cwd();source=root/'addon';output=root/'suite-build';evidence=root/'suite-evidence';evidence.mkdir(exist_ok=True)
repo,commit,slug=sys.argv[1:]
actual=subprocess.check_output(['git','-C',str(source),'rev-parse','HEAD'],text=True).strip()
assert actual==commit
report=dict(repository=repo,commit=commit,slug=slug,result='FAIL',reports=[],reported_entries=0,failures=0,errors=0,skips=0)
code=1
try:
    for guard in ['verify_pack_registry_safety.py','verify_player_data_safety.py','verify_persisted_data_safety.py']:
        path=source/'scripts'/guard
        if path.exists():subprocess.run([sys.executable,str(path)],cwd=source,check=True,timeout=60)
    with (evidence/'instrumentation.log').open('w') as log:
        setup=subprocess.run([sys.executable,'tools/scripts/build_addon_against_slimefun.py',str(source),'suite-inputs/core.jar',str(output)],stdout=log,stderr=subprocess.STDOUT,timeout=1000)
    if setup.returncode:raise RuntimeError(f'Exact-core build preparation failed: {setup.returncode}')
    project=output/'project';env=dict(os.environ)
    env['SLIMEFUN_COMPATIBILITY_JAR']=str((root/'suite-inputs/core.jar').resolve())
    if (project/'pom.xml').exists():
        wrapper=project/'mvnw'
        if wrapper.exists():wrapper.chmod(wrapper.stat().st_mode|0o111)
        command=[str(wrapper) if wrapper.exists() else 'mvn','-B','--no-transfer-progress','clean','verify','-DskipTests=false','-Dmaven.test.skip=false']
        report['build_system']='maven'
    else:
        wrapper=project/'gradlew'
        if wrapper.exists():wrapper.chmod(wrapper.stat().st_mode|0o111)
        command=[str(wrapper) if wrapper.exists() else 'gradle','clean','build','--no-daemon','--no-build-cache','--no-configuration-cache','-I',str(project/'.slimefun-legacy-ci.init.gradle')]
        report['build_system']='gradle'
    report['command']=command
    with (evidence/'full-build.log').open('w') as log:
        result=subprocess.run(command,cwd=project,env=env,stdout=log,stderr=subprocess.STDOUT,timeout=1000)
    code=result.returncode
    if code==0:report['result']='PASS'
except Exception as error:
    report['error']=str(error)
    print(str(error),file=sys.stderr)
finally:
    project=output/'project'
    if project.exists():
        for path in sorted(project.rglob('TEST-*.xml')):
            parts=path.parts
            if not any(marker in parts for marker in ['test-results','surefire-reports','failsafe-reports']):continue
            name=str(path.relative_to(project));target=evidence/'test-results'/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(path,target)
            try:
                suite=ET.parse(path).getroot()
                if suite.tag=='testsuite':suites=[suite]
                elif suite.tag=='testsuites':suites=list(suite.findall('testsuite'))
                else:raise ValueError('Unknown test report root')
                totals={key:sum(int(s.get(key,'0')) for s in suites) for key in ['tests','failures','errors','skipped']}
                report['reported_entries']+=totals['tests'];report['failures']+=totals['failures'];report['errors']+=totals['errors'];report['skips']+=totals['skipped']
                report['reports'].append(dict(path=name,**totals))
            except Exception as error:
                report['result']='FAIL';report['report_error']=f'{name}: {error}';code=1
    if report['failures'] or report['errors']:report['result']='FAIL';code=1
    report['coverage']='Reported automated test entries' if report['reported_entries'] else 'No automated test entries reported; build success is not gameplay coverage'
    report['core_sha256']=hashlib.sha256((root/'suite-inputs/core.jar').read_bytes()).hexdigest()
    (evidence/'result.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(report,indent=2))
raise SystemExit(code)
