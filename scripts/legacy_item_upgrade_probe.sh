#!/usr/bin/env bash
# Disposable real-server writer/reader test. Never run against production data.
set -euo pipefail
MODE="${1:?Usage: legacy_item_upgrade_probe.sh write|read <work-dir> [core.jar fixture.tar.gz]}"
WORK_DIR="$(realpath -m "${2:?Missing disposable work directory}")"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$REPO_ROOT/scripts/runtime_download.sh"
MC_VERSION="${SERVER_MINECRAFT_VERSION:-1.20.6}"
SOFTWARE="${SERVER_SOFTWARE:-paper}"
USER_AGENT='Slimefun-Legacy-Upgrade-Fixture/1.0 (https://github.com/wickidcow/Slimefun-Legacy)'
JAVA_RUNNER="${JAVA_RUNNER:-java}"
[[ "$MODE" == write || "$MODE" == read ]] || exit 2
[[ "$SOFTWARE" == paper || "$SOFTWARE" == purpur ]] || exit 2
# Existing work must be explicitly removed by the caller, never by this harness.
if [[ -e "$WORK_DIR" ]]; then echo 'Refusing to overwrite an existing fixture directory' >&2; exit 2; fi
mkdir -p "$WORK_DIR/plugins" "$WORK_DIR/evidence"
if [[ "$MODE" == read ]]; then
    CORE="$(realpath "${3:?Missing exact candidate JAR}")"
    FIXTURE="$(realpath "${4:?Missing historical fixture archive}")"
    python3 - "$FIXTURE" "$WORK_DIR" <<'PY'
import pathlib, sys, tarfile
source, destination = sys.argv[1], pathlib.Path(sys.argv[2])
with tarfile.open(source) as archive:
    for entry in archive.getmembers():
        path = pathlib.PurePosixPath(entry.name)
        if path.is_absolute() or '..' in path.parts or entry.issym() or entry.islnk():
            raise SystemExit('Unsafe fixture archive member')
    archive.extractall(destination, filter='data')
PY
    cp "$CORE" "$WORK_DIR/plugins/Slimefun-Legacy.jar"
    sha256sum "$CORE" > "$WORK_DIR/evidence/core.sha256"
    # Separate exact codec/world preservation from the established optional
    # automatic presentation repair. Both configurations are tested below.
    mkdir -p "$WORK_DIR/plugins/Slimefun"
    cat > "$WORK_DIR/plugins/Slimefun/config.yml" <<'DOCTOR'
stability:
  item-doctor:
    enabled: true
    repair-player-on-join: false
    repair-opened-inventories: false
    repair-chunks-on-load: false
    repair-picked-up-items: false
DOCTOR
else
    [[ "$MC_VERSION" == 1.20.6 && "$SOFTWARE" == paper ]] || exit 2
fi
if [[ "$SOFTWARE" == paper ]]; then
    runtime_download "https://fill.papermc.io/v3/projects/paper/versions/${MC_VERSION}/builds" "$WORK_DIR/evidence/server-metadata.json"
else
    runtime_download "https://api.purpurmc.org/v2/purpur/${MC_VERSION}/latest" "$WORK_DIR/evidence/server-metadata.json"
fi
python3 - "$WORK_DIR" "$MC_VERSION" "$SOFTWARE" <<'PY'
import json, pathlib, sys
root, version, software = pathlib.Path(sys.argv[1]), sys.argv[2], sys.argv[3]
metadata = json.loads((root/'evidence/server-metadata.json').read_text())
if software == 'paper':
    if not isinstance(metadata, list): raise SystemExit('Unexpected Paper metadata')
    candidates = [row for row in metadata if row.get('channel') == 'STABLE' or version == '26.3']
    if not candidates: raise SystemExit('No permitted exact-version Paper build')
    build = max(candidates, key=lambda row: int(row['id']))
    download = build['downloads']['server:default']
    url, checksum, algorithm = download['url'], download['checksums']['sha256'], 'sha256'
    build_id, channel = str(build['id']), build['channel']
else:
    if metadata.get('version') != version or metadata.get('project') != 'purpur': raise SystemExit('Wrong Purpur metadata')
    build_id, channel = str(metadata['build']), 'Purpur'
    url = f'https://api.purpurmc.org/v2/purpur/{version}/{build_id}/download'
    checksum, algorithm = metadata['md5'], 'md5'
if not url.startswith('https://'): raise SystemExit('Non-HTTPS runtime download refused')
proof = dict(minecraft=version, software=software, build=build_id, channel=channel, url=url, checksum=checksum, algorithm=algorithm)
(root/'evidence/server-source.json').write_text(json.dumps(proof, indent=2))
(root/'server-url.txt').write_text(url)
PY
runtime_download "$(cat "$WORK_DIR/server-url.txt")" "$WORK_DIR/server.jar"
python3 - "$WORK_DIR" <<'PY'
import hashlib, json, pathlib, sys, zipfile
root=pathlib.Path(sys.argv[1]); proof=json.loads((root/'evidence/server-source.json').read_text()); data=(root/'server.jar').read_bytes()
if hashlib.new(proof['algorithm'], data).hexdigest() != proof['checksum']: raise SystemExit('Server artifact checksum mismatch')
with zipfile.ZipFile(root/'server.jar') as archive:
    if archive.testzip() is not None: raise SystemExit('Corrupt server artifact')
proof['sha256']=hashlib.sha256(data).hexdigest()
(root/'evidence/server-source.json').write_text(json.dumps(proof, indent=2))
PY
printf 'eula=true\n' > "$WORK_DIR/eula.txt"
cat > "$WORK_DIR/server.properties" <<'PROPS'
online-mode=false
server-ip=127.0.0.1
level-name=upgrade-world
level-type=minecraft:flat
generate-structures=false
max-players=1
view-distance=2
simulation-distance=2
spawn-protection=0
pause-when-empty-seconds=-1
spawn-animals=false
spawn-monsters=false
enable-query=false
enable-rcon=false
PROPS
run_cycle() (
    set -euo pipefail
    label="$1" action="$2"
    proof="$WORK_DIR/plugins/LegacyItemUpgradeProbe/${action}-success.json"
    if [[ -n "$action" ]]; then rm -f "$proof"; fi
    fifo="$WORK_DIR/${label}.stdin"
    mkfifo "$fifo"; exec 3<>"$fifo"
    (cd "$WORK_DIR"; exec "$JAVA_RUNNER" -Xms512M -Xmx2G -jar server.jar --nogui <&3 > "evidence/${label}.log" 2>&1) &
    pid=$!
    cleanup() { if kill -0 "$pid" 2>/dev/null; then kill "$pid" 2>/dev/null || true; fi; exec 3>&-; rm -f "$fifo"; }
    trap cleanup EXIT
    ready=false
    deadline=$((SECONDS + 300))
    while kill -0 "$pid" 2>/dev/null && ((SECONDS < deadline)); do
        if grep -Fq 'Done (' "$WORK_DIR/evidence/${label}.log"; then ready=true; break; fi
        if grep -Fq 'Error occurred while enabling' "$WORK_DIR/evidence/${label}.log"; then break; fi
        sleep 2
    done
    [[ "$ready" == true ]] || { cat "$WORK_DIR/evidence/${label}.log" >&2; exit 1; }
    if [[ -n "$action" ]]; then
        if [[ "$action" == automatic ]]; then sleep 10; fi
        printf 'legacyitemprobe %s\n' "$action" >&3
        deadline=$((SECONDS + 60))
        while [[ ! -s "$proof" ]] && kill -0 "$pid" 2>/dev/null && ((SECONDS < deadline)); do
            if grep -Fq 'LEGACY_UPGRADE_PROBE_FAIL' "$WORK_DIR/evidence/${label}.log"; then break; fi
            sleep 1
        done
        [[ -s "$proof" ]] || { cat "$WORK_DIR/evidence/${label}.log" >&2; exit 1; }
        cp "$proof" "$WORK_DIR/evidence/${label}.json"
    fi
    printf 'stop\n' >&3
    deadline=$((SECONDS + 60))
    while kill -0 "$pid" 2>/dev/null && ((SECONDS < deadline)); do sleep 1; done
    if kill -0 "$pid" 2>/dev/null; then echo 'Server did not stop cleanly' >&2; exit 1; fi
    wait "$pid"
    if grep -Eq 'Error occurred while enabling|NoClassDefFoundError|NoSuchMethodError|AbstractMethodError|IncompatibleClassChangeError|LEGACY_UPGRADE_PROBE_FAIL' "$WORK_DIR/evidence/${label}.log"; then
        cat "$WORK_DIR/evidence/${label}.log" >&2; exit 1
    fi
)
if [[ "$MODE" == write ]]; then
    run_cycle bootstrap ''
    mkdir -p "$WORK_DIR/probe-classes"
    CP="$(find "$WORK_DIR/libraries" -name '*.jar' -printf '%p:' | sed 's/:$//')"
    [[ -n "$CP" ]] || { echo 'Historical server did not supply its libraries' >&2; exit 1; }
    javac --release 21 -encoding UTF-8 -cp "$CP" -d "$WORK_DIR/probe-classes" "$REPO_ROOT/scripts/upgrade-fixture/LegacyItemUpgradeProbe.java"
    cp "$REPO_ROOT/scripts/upgrade-fixture/plugin.yml" "$WORK_DIR/probe-classes/plugin.yml"
    jar --create --file "$WORK_DIR/plugins/LegacyItemUpgradeProbe.jar" -C "$WORK_DIR/probe-classes" .
    run_cycle historical-write write
    (cd "$WORK_DIR"; find plugins/LegacyItemUpgradeProbe -type f -print0 | sort -z | xargs -0 sha256sum > fixture.sha256)
    tar -czf "$WORK_DIR/fixture-1.20.6.tar.gz" -C "$WORK_DIR" upgrade-world plugins/LegacyItemUpgradeProbe.jar plugins/LegacyItemUpgradeProbe fixture.sha256
else
    (cd "$WORK_DIR"; sha256sum -c fixture.sha256)
    run_cycle upgraded-first read
    run_cycle upgraded-second read
    (cd "$WORK_DIR"; sha256sum -c fixture.sha256)
    # Explicitly enable the existing presentation repair and require its result
    # to match a real Doctor reference, then verify a clean restart is idempotent.
    python3 - "$WORK_DIR/plugins/Slimefun/config.yml" <<'PYTHON'
import pathlib, sys
path = pathlib.Path(sys.argv[1])
text = path.read_text()
assert text.count('repair-chunks-on-load: false') == 1
path.write_text(text.replace('repair-chunks-on-load: false', 'repair-chunks-on-load: true'))
PYTHON
    run_cycle automatic-first automatic
    run_cycle automatic-second automatic
    (cd "$WORK_DIR"; sha256sum -c fixture.sha256)
fi
cat "$WORK_DIR/evidence/server-source.json"
echo "Legacy item fixture ${MODE}: PASS on ${SOFTWARE} ${MC_VERSION}"
