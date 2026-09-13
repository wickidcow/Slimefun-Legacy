#!/usr/bin/env bash
set -euo pipefail

SLIMEFUN_JAR="${1:?Usage: addon_runtime_smoke.sh <slimefun-jar> <addon-jar> <runtime-plugin> [work-directory]}"
ADDON_JAR="${2:?Usage: addon_runtime_smoke.sh <slimefun-jar> <addon-jar> <runtime-plugin> [work-directory]}"
RUNTIME_PLUGIN="${3:?Usage: addon_runtime_smoke.sh <slimefun-jar> <addon-jar> <runtime-plugin> [work-directory]}"
WORK_DIR="${4:-build/addon-runtime-smoke}"
MC_VERSION="${PAPER_MINECRAFT_VERSION:-26.2}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EXPECTED_SLIMEFUN_VERSION="${SLIMEFUN_SMOKE_VERSION:-$(sed -n 's/^projectVersion=//p' "$REPO_ROOT/gradle.properties" | head -n 1 | tr -d '\r')}"
USER_AGENT="${PAPER_DOWNLOAD_USER_AGENT:-Slimefun-Legacy-CI/${EXPECTED_SLIMEFUN_VERSION} (https://github.com/wickidcow/Slimefun-Legacy)}"
STARTUP_TIMEOUT_SECONDS="${PAPER_SMOKE_STARTUP_TIMEOUT:-240}"
SHUTDOWN_TIMEOUT_SECONDS="${PAPER_SMOKE_SHUTDOWN_TIMEOUT:-60}"

for command in curl jq java python3; do
    if ! command -v "$command" >/dev/null 2>&1; then
        echo "Required command is unavailable: $command" >&2
        exit 1
    fi
done

if [[ ! -s "$SLIMEFUN_JAR" ]]; then
    echo "Slimefun JAR not found or empty: $SLIMEFUN_JAR" >&2
    exit 1
fi
if [[ ! -s "$ADDON_JAR" ]]; then
    echo "Addon JAR not found or empty: $ADDON_JAR" >&2
    exit 1
fi
if [[ -z "$EXPECTED_SLIMEFUN_VERSION" ]]; then
    echo "Could not resolve the expected Slimefun Legacy version." >&2
    exit 1
fi

rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR/plugins"
cp "$SLIMEFUN_JAR" "$WORK_DIR/plugins/Slimefun-Legacy-runtime-smoke.jar"
cp "$ADDON_JAR" "$WORK_DIR/plugins/${RUNTIME_PLUGIN}-runtime-smoke.jar"
printf 'eula=true\n' > "$WORK_DIR/eula.txt"
cat > "$WORK_DIR/server.properties" <<'PROPERTIES'
online-mode=false
level-name=addon-smoke-world
max-players=1
spawn-protection=0
view-distance=2
simulation-distance=2
pause-when-empty-seconds=-1
enable-query=false
enable-rcon=false
motd=Slimefun Legacy addon runtime smoke
PROPERTIES

BUILDS_URL="https://fill.papermc.io/v3/projects/paper/versions/${MC_VERSION}/builds"
BUILDS_RESPONSE="$(curl --fail-with-body -sS -H "User-Agent: ${USER_AGENT}" "$BUILDS_URL")"
if jq -e '.ok == false' >/dev/null 2>&1 <<<"$BUILDS_RESPONSE"; then
    jq -r '.message // "Paper downloads service returned an unknown error"' <<<"$BUILDS_RESPONSE" >&2
    exit 1
fi

PAPER_URL="$(jq -r 'first(.[] | select(.channel == "STABLE") | .downloads."server:default".url) // empty' <<<"$BUILDS_RESPONSE")"
PAPER_BUILD="$(jq -r 'first(.[] | select(.channel == "STABLE") | .id) // empty' <<<"$BUILDS_RESPONSE")"
if [[ -z "$PAPER_URL" || -z "$PAPER_BUILD" ]]; then
    echo "No stable Paper build is available for Minecraft ${MC_VERSION}." >&2
    exit 1
fi

printf 'Minecraft: %s\nPaper stable build: %s\nDownload: %s\nAddon: %s\n' \
    "$MC_VERSION" "$PAPER_BUILD" "$PAPER_URL" "$RUNTIME_PLUGIN" > "$WORK_DIR/paper-build.txt"
curl --fail-with-body -L -sS -H "User-Agent: ${USER_AGENT}" -o "$WORK_DIR/paper.jar" "$PAPER_URL"
test -s "$WORK_DIR/paper.jar"

normalize_log() {
    local source="$1"
    local destination="$2"
    python3 - "$source" "$destination" <<'PY'
from pathlib import Path
import re
import sys

source = Path(sys.argv[1])
destination = Path(sys.argv[2])
text = source.read_text(encoding="utf-8", errors="replace")
text = re.sub(r"\x1b\[[0-9;?]*[ -/]*[@-~]", "", text)
text = re.sub(r"§[0-9A-FK-ORa-fk-or]", "", text)
destination.write_text(text, encoding="utf-8")
PY
}

stop_process() {
    local pid="$1"
    local fd="$2"

    if kill -0 "$pid" >/dev/null 2>&1; then
        printf 'stop\n' >&"$fd" || true
    fi

    local deadline=$((SECONDS + SHUTDOWN_TIMEOUT_SECONDS))
    while kill -0 "$pid" >/dev/null 2>&1 && (( SECONDS < deadline )); do
        sleep 1
    done

    if kill -0 "$pid" >/dev/null 2>&1; then
        echo "Paper did not stop cleanly within ${SHUTDOWN_TIMEOUT_SECONDS}s; terminating it." >&2
        kill "$pid" >/dev/null 2>&1 || true
        sleep 2
    fi
}

run_cycle() {
    local label="$1"
    local require_previous_clean="$2"
    local console_log="$WORK_DIR/${label}.console.log"
    local normalized_log="$WORK_DIR/${label}.normalized.log"
    local input_fifo="$WORK_DIR/${label}.stdin"

    rm -f "$input_fifo"
    mkfifo "$input_fifo"
    exec 3<>"$input_fifo"

    (
        cd "$WORK_DIR"
        java -Xms512M -Xmx2G -jar paper.jar --nogui <&3 > "${label}.console.log" 2>&1
    ) &
    local server_pid=$!
    local startup_deadline=$((SECONDS + STARTUP_TIMEOUT_SECONDS))
    local started=false

    while kill -0 "$server_pid" >/dev/null 2>&1 && (( SECONDS < startup_deadline )); do
        if grep -Fq 'Done (' "$console_log" 2>/dev/null; then
            started=true
            break
        fi
        if grep -Fq "Error occurred while enabling ${RUNTIME_PLUGIN}" "$console_log" 2>/dev/null; then
            break
        fi
        if grep -Fq 'Error occurred while enabling Slimefun' "$console_log" 2>/dev/null; then
            break
        fi
        sleep 2
    done

    if [[ "$started" != true ]]; then
        echo "Addon runtime smoke ${RUNTIME_PLUGIN}/${label}: server did not reach Done." >&2
        stop_process "$server_pid" 3
        wait "$server_pid" >/dev/null 2>&1 || true
        exec 3>&-
        cat "$console_log" >&2 || true
        return 1
    fi

    printf 'sf doctor status\n' >&3
    printf 'sf doctor registry\n' >&3
    printf 'sf doctor compatibility %s\n' "$RUNTIME_PLUGIN" >&3
    sleep 6
    stop_process "$server_pid" 3

    local server_status=0
    wait "$server_pid" || server_status=$?
    exec 3>&-
    normalize_log "$console_log" "$normalized_log"

    if (( server_status != 0 )); then
        echo "Addon runtime smoke ${RUNTIME_PLUGIN}/${label}: Paper exited with status ${server_status}." >&2
        cat "$console_log" >&2 || true
        return 1
    fi

    if ! grep -Fq "Enabling Slimefun v${EXPECTED_SLIMEFUN_VERSION}" "$normalized_log"; then
        echo "Addon runtime smoke ${RUNTIME_PLUGIN}/${label}: expected Slimefun ${EXPECTED_SLIMEFUN_VERSION} did not enable." >&2
        cat "$normalized_log" >&2 || true
        return 1
    fi
    if ! grep -Fq "Enabling ${RUNTIME_PLUGIN} v" "$normalized_log"; then
        echo "Addon runtime smoke ${RUNTIME_PLUGIN}/${label}: addon enable line was not observed." >&2
        cat "$normalized_log" >&2 || true
        return 1
    fi
    if grep -Fq "Error occurred while enabling ${RUNTIME_PLUGIN}" "$normalized_log"; then
        echo "Addon runtime smoke ${RUNTIME_PLUGIN}/${label}: addon reported an enable failure." >&2
        cat "$normalized_log" >&2 || true
        return 1
    fi
    if grep -Fq "Error occurred while enabling Slimefun" "$normalized_log"; then
        echo "Addon runtime smoke ${RUNTIME_PLUGIN}/${label}: Slimefun reported an enable failure." >&2
        cat "$normalized_log" >&2 || true
        return 1
    fi

    if ! grep -Fq "Addon Compatibility Evidence: ${RUNTIME_PLUGIN} v" "$normalized_log"; then
        echo "Addon runtime smoke ${RUNTIME_PLUGIN}/${label}: targeted compatibility diagnostics did not recognize the addon." >&2
        cat "$normalized_log" >&2 || true
        return 1
    fi
    if ! grep -Fq 'Runtime load: Plugin enabled' "$normalized_log"; then
        echo "Addon runtime smoke ${RUNTIME_PLUGIN}/${label}: compatibility diagnostics did not report an enabled runtime." >&2
        cat "$normalized_log" >&2 || true
        return 1
    fi

    if ! grep -Eq -- "-[[:space:]]+${RUNTIME_PLUGIN}[[:space:]]+v[^[:space:]]+[[:space:]].*items[[:space:]]+[1-9][0-9]*/[1-9][0-9]*" "$normalized_log"; then
        echo "Addon runtime smoke ${RUNTIME_PLUGIN}/${label}: no non-zero Slimefun registry ownership line was observed." >&2
        cat "$normalized_log" >&2 || true
        return 1
    fi

    if [[ "$require_previous_clean" == true ]] && ! grep -Fq 'Previous clean shutdown: Yes' "$normalized_log"; then
        echo "Addon runtime smoke ${RUNTIME_PLUGIN}/${label}: second boot did not observe a clean prior Slimefun shutdown." >&2
        cat "$normalized_log" >&2 || true
        return 1
    fi
    if ! grep -Fq 'Stopping server' "$normalized_log"; then
        echo "Addon runtime smoke ${RUNTIME_PLUGIN}/${label}: normal server shutdown was not observed." >&2
        cat "$normalized_log" >&2 || true
        return 1
    fi
}

run_cycle "first" false
run_cycle "second" true

REGISTRY_LINE="$(grep -E -- "-[[:space:]]+${RUNTIME_PLUGIN}[[:space:]]+v[^[:space:]]+[[:space:]].*items[[:space:]]+[1-9][0-9]*/[1-9][0-9]*" "$WORK_DIR/second.normalized.log" | tail -n 1 | sed 's/^[[:space:]]*//')"
COMPATIBILITY_LINE="$(grep -F "Addon Compatibility Evidence: ${RUNTIME_PLUGIN} v" "$WORK_DIR/second.normalized.log" | tail -n 1 | sed 's/^[[:space:]]*//')"

cat > "$WORK_DIR/smoke-result.txt" <<EOF
Required addon Paper runtime smoke: PASS
Addon: ${RUNTIME_PLUGIN}
Slimefun Legacy: ${EXPECTED_SLIMEFUN_VERSION}
Minecraft: ${MC_VERSION}
Paper stable build: ${PAPER_BUILD}
Cycles: 2
Bukkit enable: observed on both boots
Slimefun compatibility recognition: observed on both boots
Slimefun registry ownership: non-zero registered item count observed on both boots
Clean shutdown persistence: observed on second boot
Registry evidence: ${REGISTRY_LINE}
Compatibility evidence: ${COMPATIBILITY_LINE}
EOF
cat "$WORK_DIR/smoke-result.txt"
