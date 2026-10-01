from pathlib import Path
import hashlib,json,shutil,subprocess
root=Path('tools')
evidence=Path('coordinate-evidence');evidence.mkdir(exist_ok=True)
def blob(data):return hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()
expected={
 'scripts/compare_addon_slimefun_compatibility.py':'10da000f912aafbfc4ae60d3dcb6c04da3c83504',
 'scripts/compare_addon_slimefun_compatibility_base.py':'e97c2fc40cd239e0f251902b3b7d04484580cf99',
 'scripts/verify_legacy.py':'b5922dca73d283b914463cdf8ff6d51227c8997d'}
for name,sha in expected.items():assert blob((root/name).read_bytes())==sha,name
source=Path('scripts/test_canonical_core_build_dependency.py');assert blob(source.read_bytes())=='fd997273243f38dfeb653bfd6cc9fb05c969e201'
shutil.copy2(source,root/'scripts'/source.name)
result=subprocess.run(['python3',str(root/'scripts'/source.name)],capture_output=True,text=True)
(evidence/'negative-control.log').write_text(result.stdout+result.stderr)
assert result.returncode==1 and 'FAILED (failures=7)' in result.stderr,'Original comparator must reproduce exact normalization failures'
p=root/'scripts/compare_addon_slimefun_compatibility.py';text=p.read_text()
old='        if not base.is_core_slimefun_dependency(resolved_group, resolved_artifact):\n            continue\n'
new='''        # The maintained core has a distinct, exact coordinate; do not broaden
        # matching to arbitrary addons whose names merely contain "Slimefun".
        canonical_legacy = (resolved_group.lower(), resolved_artifact.lower()) == (
            "com.github.wickidcow", "slimefun-legacy"
        )
        if canonical_legacy:
            resolved_group, resolved_artifact = "com.github.slimefun", "Slimefun"
        elif not base.is_core_slimefun_dependency(resolved_group, resolved_artifact):
            continue
'''
assert text.count(old)==1;p.write_text(text.replace(old,new))
p=root/'scripts/compare_addon_slimefun_compatibility_base.py';text=p.read_text()
old='    return coreArtifact && coreGroup'
new="    def canonicalLegacy = group == 'com.github.wickidcow' && artifact == 'slimefun-legacy'\n    return canonicalLegacy || (coreArtifact && coreGroup)"
assert text.count(old)==1;p.write_text(text.replace(old,new))
p=root/'scripts/verify_legacy.py';text=p.read_text()
old='        "test_download_candidate_bundle.py",\n';assert text.count(old)==1
p.write_text(text.replace(old,old+'        "test_canonical_core_build_dependency.py",\n'))
after={
 'scripts/compare_addon_slimefun_compatibility.py':'cd7c598227b4ac4eb6e3307dc4ffb683c6875f1c',
 'scripts/compare_addon_slimefun_compatibility_base.py':'c616bbf5bcbf00e1a8919f786ffb587a3ac11562',
 'scripts/verify_legacy.py':'d3f0ef91fc084a0d1686584f7b82f30567bc4496',
 'scripts/test_canonical_core_build_dependency.py':'fd997273243f38dfeb653bfd6cc9fb05c969e201'}
for name,sha in after.items():assert blob((root/name).read_bytes())==sha,(name,blob((root/name).read_bytes()))
(evidence/'reviewed-blobs.json').write_text(json.dumps(after,indent=2)+'\n')
subprocess.run(['python3',str(root/'scripts'/source.name)],check=True)
