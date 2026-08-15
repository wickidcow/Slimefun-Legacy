#!/usr/bin/env bash
set -euo pipefail

PLUGIN_JAR="${1:?Usage: paper_runtime_smoke.sh <slimefun-jar> [work-directory]}"
WORK_DIR="${2:-build/paper-runtime-smoke}"
MC_VERSION="${PAPER_MINECRAFT_VERSION:-26.2}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EXPECTED_PLUGIN_VERSION="${SLIMEFUN_SMOKE_VERSION:-$(sed -n 's/^projectVersion=//p' "$REPO_ROOT/gradle.properties" | head -n 1 | tr -d '\r')}"
USER_AGENT="${PAPER_DOWNLOAD_USER_AGENT:-Slimefun-Legacy-CI/${EXPECTED_PLUGIN_VERSION} (https://github.com/wickidcow/Slimefun-Legacy)}"
STARTUP_TIMEOUT_SECONDS="${PAPER_SMOKE_STARTUP_TIMEOUT:-240}"
SHUTDOWN_TIMEOUT_SECONDS="${PAPER_SMOKE_SHUTDOWN_TIMEOUT:-60}"

if [[ -z "$EXPECTED_PLUGIN_VERSION" ]]; then
    echo "Could not resolve the expected Slimefun Legacy version." >&2
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

rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR/plugins"
cp "$PLUGIN_JAR" "$WORK_DIR/plugins/Slimefun-Legacy-smoke.jar"
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
motd=Slimefun Legacy CI runtime smoke
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

printf 'Minecraft: %s\nPaper stable build: %s\nDownload: %s\n' "$MC_VERSION" "$PAPER_BUILD" "$PAPER_URL" \
    > "$WORK_DIR/paper-build.txt"
curl --fail-with-body -L -sS -H "User-Agent: ${USER_AGENT}" -o "$WORK_DIR/paper.jar" "$PAPER_URL"
test -s "$WORK_DIR/paper.jar"

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
        if grep -Fq 'Error occurred while enabling Slimefun' "$console_log" 2>/dev/null; then
            break
        fi
        sleep 2
    done

    if [[ "$started" != true ]]; then
        echo "Paper runtime smoke ${label}: server did not reach Done." >&2
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
        echo "Paper runtime smoke ${label}: Paper exited with status ${server_status}." >&2
        cat "$console_log" >&2 || true
        return 1
    fi

    if ! grep -Fq "Enabling Slimefun v${EXPECTED_PLUGIN_VERSION}" "$console_log"; then
        echo "Paper runtime smoke ${label}: Slimefun ${EXPECTED_PLUGIN_VERSION} was not observed enabling." >&2
        cat "$console_log" >&2 || true
        return 1
    fi

    if grep -Eq 'Error occurred while enabling Slimefun|Could not load .*Slimefun' "$console_log"; then
        echo "Paper runtime smoke ${label}: Slimefun reported an enable/load failure." >&2
        cat "$console_log" >&2 || true
        return 1
    fi

    if ! grep -Fq 'Slimefun Upgrade Readiness' "$console_log"; then
        echo "Paper runtime smoke ${label}: /sf doctor upgrade did not produce its report." >&2
        cat "$console_log" >&2 || true
        return 1
    fi

    if grep -Eq 'Overall status:[[:space:]]+BLOCKED' "$console_log"; then
        echo "Paper runtime smoke ${label}: /sf doctor upgrade reported BLOCKED." >&2
        cat "$console_log" >&2 || true
        return 1
    fi

    if [[ "$require_previous_clean" == true ]] && ! grep -Eq 'previous shutdown[[:space:]]+Clean' "$console_log"; then
        echo "Paper runtime smoke ${label}: the second boot did not observe a clean prior Slimefun shutdown." >&2
        cat "$console_log" >&2 || true
        return 1
    fi

    if ! grep -Fq 'Stopping server' "$console_log"; then
        echo "Paper runtime smoke ${label}: a normal server shutdown was not observed." >&2
        cat "$console_log" >&2 || true
        return 1
    fi
}

run_cycle "first" false
run_cycle "second" true

cat > "$WORK_DIR/smoke-result.txt" <<EOF
Slimefun Legacy Paper runtime smoke: PASS
Slimefun Legacy: ${EXPECTED_PLUGIN_VERSION}
Minecraft: ${MC_VERSION}
Paper stable build: ${PAPER_BUILD}
Cycles: 2
Upgrade diagnostics: executed on both boots without BLOCKED status
Clean shutdown persistence: observed on second boot
EOF
cat "$WORK_DIR/smoke-result.txt"
