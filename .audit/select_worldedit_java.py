from pathlib import Path
import hashlib
p=Path('scripts/prepare_worldedit_runtime.py')
data=p.read_bytes()
sha=lambda b: hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()
assert sha(data)=='7e1fa78fd3186c9d16c2cfc3c54bbab85190c992'
s=data.decode()
s=s.replace('MAIN_CLASS = "com.sk89q.worldedit.bukkit.WorldEditPlugin"\n','MAIN_CLASS = "com.sk89q.worldedit.bukkit.WorldEditPlugin"\nMAX_CANDIDATES = 12\n\n\nclass BytecodeCompatibilityError(ValueError):\n    """A verified artifact is not eligible for the selected Java runtime."""\n')
s=s.replace('raise ValueError("WorldEdit bytecode exceeds this lane\'s Java runtime")','raise BytecodeCompatibilityError(\n                    f"WorldEdit class {name} requires Java {major - 44}, beyond Java {java_version}")')
old='''    version = select_version(versions, project_id, minecraft, allow_prerelease, version_id)
    file = select_file(version)
    payload = request_bytes(file["url"], MAX_DOWNLOAD, allowed_host="cdn.modrinth.com")
    report = validate_jar(payload, file, 21 if minecraft == "1.21.11" else 25)
'''
new='''    version, file, payload, report = select_compatible_artifact(
        versions, project_id, minecraft, allow_prerelease, version_id)
'''
assert s.count(old)==1;s=s.replace(old,new)
marker='\ndef stage(output: Path, minecraft: str, allow_prerelease: bool = False,\n'
addition='''
def select_compatible_artifact(versions: object, project_id: str, minecraft: str,
                               allow_prerelease: bool = False, version_id: str | None = None) -> tuple:
    # Published Minecraft support alone does not establish Java-21 eligibility.
    # Never fall back after a checksum, transport, identity or archive failure.
    remaining = versions
    rejected = []
    for _ in range(MAX_CANDIDATES):
        version = select_version(remaining, project_id, minecraft, allow_prerelease, version_id)
        file = select_file(version)
        payload = request_bytes(file["url"], MAX_DOWNLOAD, allowed_host="cdn.modrinth.com")
        try:
            report = validate_jar(payload, file, 21 if minecraft == "1.21.11" else 25)
        except BytecodeCompatibilityError as failure:
            if version_id is not None:
                raise  # An explicit pin never silently selects another version.
            rejected.append({"version_id": version["id"], "version_number": version.get("version_number"),
                             "reason": str(failure)})
            remaining = [row for row in remaining if row["id"] != version["id"]]
            continue
        report["bytecode_rejected_candidates"] = rejected
        return version, file, payload, report
    raise ValueError(f"No verified WorldEdit artifact fits this Java lane within {MAX_CANDIDATES} candidates")

'''
assert s.count(marker)==1;s=s.replace(marker,'\n'+addition+marker)
p.write_text(s)
assert sha(p.read_bytes())=='faf79abda0440084b390f0905e8ee6d5c5d49c49'
print('SELECTOR_SOURCE',sha(p.read_bytes()))
