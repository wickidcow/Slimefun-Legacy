#!/usr/bin/env bash
set -euo pipefail

PLUGIN_JAR="${1:?Usage: paper_26_3_runtime_smoke.sh <slimefun-jar> [work-directory]}"
WORK_DIR="${2:-build/paper-26.3-runtime-smoke}"
MC_VERSION="${SERVER_MINECRAFT_VERSION:-26.3}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EXPECTED_PLUGIN_VERSION="${SLIMEFUN_SMOKE_VERSION:-$(sed -n 's/^projectVersion=//p' "$REPO_ROOT/gradle.properties" | head -n 1 | tr -d '\r')}"
USER_AGENT="${SERVER_DOWNLOAD_USER_AGENT:-Slimefun-Legacy-26.3-Preflight/${EXPECTED_PLUGIN_VERSION} (https://github.com/wickidcow/Slimefun-Legacy)}"
STARTUP_TIMEOUT_SECONDS="${SERVER_SMOKE_STARTUP_TIMEOUT:-300}"
SHUTDOWN_TIMEOUT_SECONDS="${SERVER_SMOKE_SHUTDOWN_TIMEOUT:-60}"

if [[ "$MC_VERSION" != "26.3" ]]; then
    echo "This pre-release runtime harness is intentionally scoped to Minecraft 26.3; got ${MC_VERSION}." >&2
    exit 1
fi

for command in curl jq java; do
    if ! command -v "$command" >/dev/null 2>&1; then
        echo "Required command is unavailable: $command" >&2
        exit 1
    fi
done

if [[ ! -s "$PLUGIN_JAR" ]]; then
    echo "Slimefun JAR not found or empty: $PLUGIN_JAR" >&2
    exit 1
fi

BUILDS_URL="https://fill.papermc.io/v3/projects/paper/versions/${MC_VERSION}/builds"
BUILDS_RESPONSE="$(curl --fail-with-body -sS -H "User-Agent: ${USER_AGENT}" "$BUILDS_URL")"
if jq -e '.ok == false' >/dev/null 2>&1 <<<"$BUILDS_RESPONSE"; then
    jq -r '.message // "PaperMC downloads service returned an unknown error"' <<<"$BUILDS_RESPONSE" >&2
    exit 1
fi

SERVER_URL="$(jq -r 'if type == "array" and length > 0 then (max_by(.id) | .downloads."server:default".url // empty) else empty end' <<<"$BUILDS_RESPONSE")"
SERVER_BUILD="$(jq -r 'if type == "array" and length > 0 then (max_by(.id) | .id // empty) else empty end' <<<"$BUILDS_RESPONSE")"
SERVER_CHANNEL="$(jq -r 'if type == "array" and length > 0 then (max_by(.id) | .channel // "unknown") else "unknown" end' <<<"$BUILDS_RESPONSE")"
if [[ -z "$SERVER_URL" || -z "$SERVER_BUILD" ]]; then
    echo "No Paper pre-release build is available for Minecraft ${MC_VERSION}." >&2
    exit 1
fi

rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR/plugins"
cp "$PLUGIN_JAR" "$WORK_DIR/plugins/Slimefun-Legacy-26.3-preflight.jar"
printf 'eula=true\n' > "$WORK_DIR/eula.txt"
cat > "$WORK_DIR/server.properties" <<'PROPERTIES'
online-mode=false
level-name=smoke-world
max-players=1
spawn-protection=0
view-distance=2
simulation-distance=2
pause-when-empty-seconds=-1
enable-query=false
enable-rcon=false
motd=Slimefun Legacy Paper 26.3 pre-release smoke
PROPERTIES

printf 'Software: Paper\nMinecraft: %s\nBuild: %s\nChannel: %s\nDownload: %s\nJava: %s\n' \
    "$MC_VERSION" "$SERVER_BUILD" "$SERVER_CHANNEL" "$SERVER_URL" "$(java -version 2>&1 | head -n 1)" \
    > "$WORK_DIR/runtime-build.txt"

curl --fail-with-body -L -sS -H "User-Agent: ${USER_AGENT}" -o "$WORK_DIR/server.jar" "$SERVER_URL"
test -s "$WORK_DIR/server.jar"

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
        kill "$pid" >/dev/null 2>&1 || true
        sleep 2
    fi
}

run_cycle() {
    local label="$1"
    local require_previous_clean="$2"
    local console_log="$WORK_DIR/${label}.console.log"
    local input_fifo="$WORK_DIR/${label}.stdin"

    rm -f "$input_fifo"
    mkfifo "$input_fifo"
    exec 3<>"$input_fifo"

    (
        cd "$WORK_DIR"
        java -Xms512M -Xmx2G -jar server.jar --nogui <&3 > "${label}.console.log" 2>&1
    ) &
    local server_pid=$!
    local startup_deadline=$((SECONDS + STARTUP_TIMEOUT_SECONDS))
    local started=false

    while kill -0 "$server_pid" >/dev/null 2>&1 && (( SECONDS < startup_deadline )); do
        if grep -Fq 'Done (' "$console_log" 2>/dev/null; then
            started=true
            break
        fi
        if grep -Fq 'Error occurred while enabling Slimefun' "$console_log" 2>/dev/null; then
            break
        fi
        sleep 2
    done

    if [[ "$started" != true ]]; then
        echo "Paper 26.3 preflight ${label}: server did not reach Done." >&2
        stop_process "$server_pid" 3
        wait "$server_pid" >/dev/null 2>&1 || true
        exec 3>&-
        cat "$console_log" >&2 || true
        return 1
    fi

    printf 'sf doctor upgrade\n' >&3
    sleep 4
    stop_process "$server_pid" 3

    local server_status=0
    wait "$server_pid" || server_status=$?
    exec 3>&-

    if (( server_status != 0 )); then
        echo "Paper 26.3 preflight ${label}: server exited with status ${server_status}." >&2
        cat "$console_log" >&2 || true
        return 1
    fi

    if ! grep -Fq "Enabling Slimefun v${EXPECTED_PLUGIN_VERSION}" "$console_log"; then
        echo "Paper 26.3 preflight ${label}: expected Slimefun ${EXPECTED_PLUGIN_VERSION} did not enable." >&2
        cat "$console_log" >&2 || true
        return 1
    fi

    if grep -Eq 'Error occurred while enabling Slimefun|Could not load .*Slimefun|NoClassDefFoundError|NoSuchMethodError' "$console_log"; then
        echo "Paper 26.3 preflight ${label}: load/linkage failure detected." >&2
        cat "$console_log" >&2 || true
        return 1
    fi

    if ! grep -Fq 'Slimefun Upgrade Readiness' "$console_log"; then
        echo "Paper 26.3 preflight ${label}: /sf doctor upgrade did not produce its report." >&2
        cat "$console_log" >&2 || true
        return 1
    fi

    if grep -Eq 'Overall status:[[:space:]]+BLOCKED' "$console_log"; then
        echo "Paper 26.3 preflight ${label}: /sf doctor upgrade reported BLOCKED." >&2
        cat "$console_log" >&2 || true
        return 1
    fi

    if [[ "$require_previous_clean" == true ]] && ! grep -Eq 'previous shutdown[[:space:]]+Clean' "$console_log"; then
        echo "Paper 26.3 preflight ${label}: second boot did not observe a clean prior shutdown." >&2
        cat "$console_log" >&2 || true
        return 1
    fi
}

run_cycle "first" false
run_cycle "second" true

cat > "$WORK_DIR/smoke-result.txt" <<EOF
Slimefun Legacy Paper 26.3 pre-release runtime smoke: PASS
Slimefun Legacy: ${EXPECTED_PLUGIN_VERSION}
Minecraft: ${MC_VERSION}
Paper build: ${SERVER_BUILD}
Channel: ${SERVER_CHANNEL}
Runtime Java: $(java -version 2>&1 | head -n 1)
Cycles: 2
Upgrade diagnostics: executed on both boots without BLOCKED status
Clean shutdown persistence: observed on second boot
EOF
cat "$WORK_DIR/smoke-result.txt"
