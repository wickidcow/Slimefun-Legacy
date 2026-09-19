#!/usr/bin/env bash
set -euo pipefail

SLIMEFUN_JAR="${1:?Usage: full_stack_runtime_smoke.sh <slimefun-jar> <addon-bundle.zip> [work-directory]}"
ADDON_BUNDLE="${2:?Usage: full_stack_runtime_smoke.sh <slimefun-jar> <addon-bundle.zip> [work-directory]}"
WORK_DIR="${3:-build/full-stack-runtime-smoke}"
MC_VERSION="${SERVER_MINECRAFT_VERSION:-26.3}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EXPECTED_SLIMEFUN_VERSION="${SLIMEFUN_SMOKE_VERSION:-$(sed -n 's/^projectVersion=//p' "$REPO_ROOT/gradle.properties" | head -n 1 | tr -d '\r')}"
USER_AGENT="${SERVER_DOWNLOAD_USER_AGENT:-Slimefun-Legacy-Full-Stack/${EXPECTED_SLIMEFUN_VERSION} (https://github.com/wickidcow/Slimefun-Legacy)}"
STARTUP_TIMEOUT_SECONDS="${SERVER_SMOKE_STARTUP_TIMEOUT:-420}"
SHUTDOWN_TIMEOUT_SECONDS="${SERVER_SMOKE_SHUTDOWN_TIMEOUT:-90}"

for command in curl jq java python3 unzip; do
    if ! command -v "$command" >/dev/null 2>&1; then
        echo "Required command is unavailable: $command" >&2
        exit 1
    fi
done

test -s "$SLIMEFUN_JAR"
test -s "$ADDON_BUNDLE"
rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR/plugins" "$WORK_DIR/bundle"
unzip -q "$ADDON_BUNDLE" -d "$WORK_DIR/bundle"
cp "$SLIMEFUN_JAR" "$WORK_DIR/plugins/Slimefun-Legacy-full-stack.jar"

python3 - "$WORK_DIR/bundle" "$WORK_DIR/plugins" "$WORK_DIR/expected-addons.txt" "$WORK_DIR/dependency-gated-addons.txt" <<'PY'
from pathlib import Path
import json
import re
import shutil
import sys
import zipfile

bundle = Path(sys.argv[1])
plugins = Path(sys.argv[2])
expected_out = Path(sys.argv[3])
gated_out = Path(sys.argv[4])
manifest_path = bundle / "SF_ADDON_MANIFEST.json"
if not manifest_path.is_file():
    raise SystemExit("Canonical addon bundle is missing SF_ADDON_MANIFEST.json")

manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
records = manifest.get("addons", [])
if not records:
    raise SystemExit("Canonical addon manifest contains no addons")

def parse_yaml_list(descriptor, key):
    lines = descriptor.splitlines()
    for index, line in enumerate(lines):
        match = re.match(rf"^(\s*){re.escape(key)}\s*:\s*(.*?)\s*$", line, flags=re.IGNORECASE)
        if not match:
            continue

        indent = len(match.group(1))
        value = match.group(2).strip()
        if value:
            if value.startswith("[") and value.endswith("]"):
                return [
                    item.strip().strip("'\"")
                    for item in value[1:-1].split(",")
                    if item.strip().strip("'\"")
                ]
            return [value.strip("'\"")]

        values = []
        for nested in lines[index + 1:]:
            if not nested.strip() or nested.lstrip().startswith("#"):
                continue
            nested_indent = len(nested) - len(nested.lstrip())
            if nested_indent <= indent:
                break
            item = re.match(r"^\s*-\s*['\"]?([^'\"#\r\n]+)", nested)
            if item:
                values.append(item.group(1).strip())
        return values
    return []

addons = []
seen_jars = set()
for record in records:
    jar_name = str(record.get("jar", "")).strip()
    if not jar_name or Path(jar_name).name != jar_name or not jar_name.endswith(".jar"):
        raise SystemExit(f"Invalid addon JAR name in manifest: {jar_name!r}")
    if jar_name in seen_jars:
        raise SystemExit(f"Duplicate addon JAR in manifest: {jar_name}")
    seen_jars.add(jar_name)

    source = bundle / jar_name
    if not source.is_file():
        raise SystemExit(f"Manifest-listed addon JAR is missing from bundle: {jar_name}")
    shutil.copy2(source, plugins / jar_name)

    with zipfile.ZipFile(source) as zf:
        descriptor = None
        for candidate in ("plugin.yml", "paper-plugin.yml", "paper-plugin.yaml"):
            if candidate in zf.namelist():
                descriptor = zf.read(candidate).decode("utf-8", errors="replace")
                break
        if descriptor is None:
            raise SystemExit(f"No plugin descriptor in {jar_name}")

        match = re.search(r"(?mi)^\s*name\s*:\s*['\"]?([^'\"#\r\n]+)", descriptor)
        if not match:
            raise SystemExit(f"No plugin name in descriptor for {jar_name}")

        addons.append((jar_name, match.group(1).strip(), parse_yaml_list(descriptor, "depend")))

bundle_jars = {path.name for path in bundle.glob("*.jar")}
if bundle_jars != seen_jars:
    extra = sorted(bundle_jars - seen_jars)
    missing = sorted(seen_jars - bundle_jars)
    raise SystemExit(f"Bundle/manifest JAR mismatch: extra={extra}, missing={missing}")

available_plugins = {"slimefun"}
available_plugins.update(name.casefold() for _, name, _ in addons)

expected = []
gated = []
for jar_name, name, dependencies in addons:
    missing_dependencies = [
        dependency for dependency in dependencies
        if dependency.casefold() not in available_plugins
    ]
    if missing_dependencies:
        gated.append((jar_name, name, ",".join(missing_dependencies)))
    else:
        expected.append((jar_name, name))

expected_out.write_text(
    "".join(f"{jar}\t{name}\n" for jar, name in expected),
    encoding="utf-8",
)
gated_out.write_text(
    "".join(f"{jar}\t{name}\t{missing}\n" for jar, name, missing in gated),
    encoding="utf-8",
)
print(
    f"Prepared {len(addons)} manifest-listed addon plugins for full-stack runtime smoke "
    f"({len(expected)} required-enable, {len(gated)} dependency-gated)"
)
for jar_name, name, missing in gated:
    print(f"Dependency-gated addon: {name} ({jar_name}) missing external hard dependency/dependencies: {missing}")
PY

printf 'eula=true\n' > "$WORK_DIR/eula.txt"
cat > "$WORK_DIR/server.properties" <<'PROPERTIES'
online-mode=false
level-name=full-stack-smoke-world
max-players=1
spawn-protection=0
view-distance=2
simulation-distance=2
pause-when-empty-seconds=-1
enable-query=false
enable-rcon=false
motd=Slimefun Legacy full-stack runtime smoke
PROPERTIES

BUILDS_URL="https://fill.papermc.io/v3/projects/paper/versions/${MC_VERSION}/builds"
BUILDS_RESPONSE="$(curl --fail-with-body -sS -H "User-Agent: ${USER_AGENT}" "$BUILDS_URL")"
if jq -e '.ok == false' >/dev/null 2>&1 <<<"$BUILDS_RESPONSE"; then
    jq -r '.message // "Paper downloads service returned an unknown error"' <<<"$BUILDS_RESPONSE" >&2
    exit 1
fi

if [[ "$MC_VERSION" == "26.2" ]]; then
    SERVER_URL="$(jq -r 'first(.[] | select(.channel == "STABLE") | .downloads."server:default".url) // empty' <<<"$BUILDS_RESPONSE")"
    SERVER_BUILD="$(jq -r 'first(.[] | select(.channel == "STABLE") | .id) // empty' <<<"$BUILDS_RESPONSE")"
    SERVER_CHANNEL="STABLE"
else
    SERVER_URL="$(jq -r 'if type == "array" and length > 0 then (max_by(.id) | .downloads."server:default".url // empty) else empty end' <<<"$BUILDS_RESPONSE")"
    SERVER_BUILD="$(jq -r 'if type == "array" and length > 0 then (max_by(.id) | .id // empty) else empty end' <<<"$BUILDS_RESPONSE")"
    SERVER_CHANNEL="$(jq -r 'if type == "array" and length > 0 then (max_by(.id) | .channel // "unknown") else "unknown" end' <<<"$BUILDS_RESPONSE")"
fi
if [[ -z "$SERVER_URL" || -z "$SERVER_BUILD" ]]; then
    echo "No usable Paper build available for Minecraft ${MC_VERSION}." >&2
    exit 1
fi

curl --fail-with-body -L -sS -H "User-Agent: ${USER_AGENT}" -o "$WORK_DIR/server.jar" "$SERVER_URL"
MANIFEST_ADDON_COUNT=$(( $(wc -l < "$WORK_DIR/expected-addons.txt") + $(wc -l < "$WORK_DIR/dependency-gated-addons.txt") ))
printf 'Minecraft: %s\nPaper build: %s\nChannel: %s\nManifest addons: %s\nRequired-enable addons: %s\nDependency-gated addons: %s\n' \
    "$MC_VERSION" "$SERVER_BUILD" "$SERVER_CHANNEL" "$MANIFEST_ADDON_COUNT" \
    "$(wc -l < "$WORK_DIR/expected-addons.txt")" "$(wc -l < "$WORK_DIR/dependency-gated-addons.txt")" > "$WORK_DIR/runtime-build.txt"

normalize_log() {
    python3 - "$1" "$2" <<'PY'
from pathlib import Path
import re
import sys
text = Path(sys.argv[1]).read_text(encoding="utf-8", errors="replace")
text = re.sub(r"\x1b\[[0-9;?]*[ -/]*[@-~]", "", text)
text = re.sub(r"§[0-9A-FK-ORa-fk-or]", "", text)
Path(sys.argv[2]).write_text(text, encoding="utf-8")
PY
}

stop_process() {
    local pid="$1"
    local fd="$2"
    if kill -0 "$pid" >/dev/null 2>&1; then printf 'stop\n' >&"$fd" || true; fi
    local deadline=$((SECONDS + SHUTDOWN_TIMEOUT_SECONDS))
    while kill -0 "$pid" >/dev/null 2>&1 && (( SECONDS < deadline )); do sleep 1; done
    if kill -0 "$pid" >/dev/null 2>&1; then kill "$pid" >/dev/null 2>&1 || true; sleep 2; fi
}

run_cycle() {
    local label="$1"
    local require_previous_clean="$2"
    local console="$WORK_DIR/${label}.console.log"
    local normalized="$WORK_DIR/${label}.normalized.log"
    local fifo="$WORK_DIR/${label}.stdin"
    rm -f "$fifo"; mkfifo "$fifo"; exec 3<>"$fifo"
    (cd "$WORK_DIR"; java -Xms768M -Xmx3G -jar server.jar --nogui <&3 > "${label}.console.log" 2>&1) &
    local pid=$!
    local deadline=$((SECONDS + STARTUP_TIMEOUT_SECONDS))
    local started=false
    while kill -0 "$pid" >/dev/null 2>&1 && (( SECONDS < deadline )); do
        if grep -Fq 'Done (' "$console" 2>/dev/null; then started=true; break; fi
        if grep -Fq 'Error occurred while enabling Slimefun' "$console" 2>/dev/null; then break; fi
        sleep 2
    done
    if [[ "$started" != true ]]; then
        stop_process "$pid" 3; wait "$pid" >/dev/null 2>&1 || true; exec 3>&-
        cat "$console" >&2 || true
        return 1
    fi

    printf 'sf doctor status\n' >&3
    printf 'sf doctor registry\n' >&3
    sleep 8
    stop_process "$pid" 3
    local status=0; wait "$pid" || status=$?; exec 3>&-
    normalize_log "$console" "$normalized"
    if (( status != 0 )); then cat "$normalized" >&2; return 1; fi

    if ! grep -Fq "Enabling Slimefun v${EXPECTED_SLIMEFUN_VERSION}" "$normalized"; then
        echo "Expected Slimefun ${EXPECTED_SLIMEFUN_VERSION} did not enable" >&2; return 1
    fi
    if grep -Eq 'Error occurred while enabling|NoClassDefFoundError|NoSuchMethodError|AbstractMethodError|IncompatibleClassChangeError' "$normalized"; then
        echo "Full-stack linkage/enable failure detected on ${MC_VERSION}/${label}" >&2
        grep -E 'Error occurred while enabling|NoClassDefFoundError|NoSuchMethodError|AbstractMethodError|IncompatibleClassChangeError' "$normalized" >&2 || true
        return 1
    fi

    while IFS=$'\t' read -r jar plugin; do
        if ! grep -Fq "Enabling ${plugin} v" "$normalized"; then
            echo "Expected addon did not enable: ${plugin} (${jar})" >&2
            return 1
        fi
    done < "$WORK_DIR/expected-addons.txt"

    if [[ "$require_previous_clean" == true ]] && ! grep -Fq 'Previous clean shutdown: Yes' "$normalized" && ! grep -Eq 'previous shutdown[[:space:]]+Clean' "$normalized"; then
        echo "Second boot did not confirm a clean prior Slimefun shutdown" >&2
        return 1
    fi
}

run_cycle first false
run_cycle second true

cat > "$WORK_DIR/smoke-result.txt" <<EOF
Slimefun Legacy full-stack runtime smoke: PASS
Minecraft: ${MC_VERSION}
Paper build: ${SERVER_BUILD}
Channel: ${SERVER_CHANNEL}
Slimefun Legacy: ${EXPECTED_SLIMEFUN_VERSION}
Manifest source: SF_ADDON_MANIFEST.json
Manifest-defined addon JARs: $(( $(wc -l < "$WORK_DIR/expected-addons.txt") + $(wc -l < "$WORK_DIR/dependency-gated-addons.txt") ))
Required-enable addon JARs: $(wc -l < "$WORK_DIR/expected-addons.txt")
Dependency-gated addon JARs: $(wc -l < "$WORK_DIR/dependency-gated-addons.txt")
Cycles: 2
All required addon enable lines: observed
Known external hard dependencies: reported separately
Linkage/enable failures: none
Clean shutdown persistence: observed on second boot
EOF
cat "$WORK_DIR/smoke-result.txt"
