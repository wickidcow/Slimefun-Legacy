#!/usr/bin/env bash
set -euo pipefail

PLUGIN_JAR="${1:?Usage: paper_family_runtime_smoke.sh <slimefun-jar> [work-directory]}"
SOFTWARE="${SERVER_SOFTWARE:-paper}"
MC_VERSION="${SERVER_MINECRAFT_VERSION:-26.2}"
WORK_DIR="${2:-build/paper-family-${MC_VERSION}-runtime-smoke}"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
EXPECTED_PLUGIN_VERSION="${SLIMEFUN_SMOKE_VERSION:-$(sed -n 's/^projectVersion=//p' "$REPO_ROOT/gradle.properties" | head -n 1 | tr -d '\r')}"
USER_AGENT="${SERVER_DOWNLOAD_USER_AGENT:-Slimefun-Legacy-CI/${EXPECTED_PLUGIN_VERSION} (https://github.com/wickidcow/Slimefun-Legacy)}"
ALLOW_UNAVAILABLE="${SERVER_ALLOW_UNAVAILABLE:-false}"
STARTUP_TIMEOUT_SECONDS="${SERVER_SMOKE_STARTUP_TIMEOUT:-240}"
SHUTDOWN_TIMEOUT_SECONDS="${SERVER_SMOKE_SHUTDOWN_TIMEOUT:-60}"

case "$SOFTWARE" in
    paper) SOFTWARE_NAME="Paper" ;;
    purpur) SOFTWARE_NAME="Purpur" ;;
    folia) SOFTWARE_NAME="Folia" ;;
    leaf) SOFTWARE_NAME="Leaf" ;;
    *)
        echo "Unsupported runtime software: $SOFTWARE" >&2
        exit 1
        ;;
esac

if [[ -z "$MC_VERSION" ]]; then
    echo "SERVER_MINECRAFT_VERSION must not be empty." >&2
    exit 1
fi

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
cat > "$WORK_DIR/server.properties" <<PROPERTIES
online-mode=false
level-name=smoke-world
max-players=1
spawn-protection=0
view-distance=2
simulation-distance=2
pause-when-empty-seconds=-1
enable-query=false
enable-rcon=false
motd=Slimefun Legacy ${MC_VERSION} ${SOFTWARE_NAME} runtime smoke
PROPERTIES

SERVER_BUILD=""
SERVER_URL=""
SERVER_CHANNEL=""

mark_unavailable() {
    local reason="$1"
    {
        printf 'Software: %s\n' "$SOFTWARE_NAME"
        printf 'Minecraft: %s\n' "$MC_VERSION"
        printf 'Availability: unavailable\n'
        printf 'Reason: %s\n' "$reason"
        printf 'Java: %s\n' "$(java -version 2>&1 | head -n 1)"
    } > "$WORK_DIR/runtime-build.txt"

    cat > "$WORK_DIR/smoke-result.txt" <<EOF
Slimefun Legacy ${SOFTWARE_NAME} ${MC_VERSION} runtime smoke: SKIP
Reason: ${reason}
No compatibility pass is claimed for this server fork/version.
EOF
    cat "$WORK_DIR/smoke-result.txt"

    if [[ "$ALLOW_UNAVAILABLE" == "true" ]]; then
        exit 0
    fi

    echo "$reason" >&2
    exit 1
}

fetch_json() {
    local url="$1"
    local response
    if ! response="$(curl --fail-with-body -sS -L -H "User-Agent: ${USER_AGENT}" "$url")"; then
        return 1
    fi
    printf '%s' "$response"
}

if [[ "$SOFTWARE" == "paper" || "$SOFTWARE" == "folia" ]]; then
    BUILDS_URL="https://fill.papermc.io/v3/projects/${SOFTWARE}/versions/${MC_VERSION}/builds"
    if ! BUILDS_RESPONSE="$(fetch_json "$BUILDS_URL")"; then
        mark_unavailable "${SOFTWARE_NAME} downloads service has no usable Minecraft ${MC_VERSION} build metadata."
    fi

    if jq -e '.ok == false' >/dev/null 2>&1 <<<"$BUILDS_RESPONSE"; then
        mark_unavailable "$(jq -r '.message // "PaperMC downloads service returned an unknown error"' <<<"$BUILDS_RESPONSE")"
    fi

    if ! jq -e 'type == "array" and length > 0' >/dev/null 2>&1 <<<"$BUILDS_RESPONSE"; then
        mark_unavailable "No ${SOFTWARE_NAME} build is published for Minecraft ${MC_VERSION}."
    fi

    if [[ "$SOFTWARE" == "paper" ]]; then
        SERVER_URL="$(jq -r '([.[] | select(.channel == "STABLE")] | if length > 0 then max_by(.id) else max_by(.id) end) | .downloads."server:default".url // empty' <<<"$BUILDS_RESPONSE")"
        SERVER_BUILD="$(jq -r '([.[] | select(.channel == "STABLE")] | if length > 0 then max_by(.id) else max_by(.id) end) | .id // empty' <<<"$BUILDS_RESPONSE")"
        SERVER_CHANNEL="$(jq -r '([.[] | select(.channel == "STABLE")] | if length > 0 then max_by(.id) else max_by(.id) end) | .channel // "unknown"' <<<"$BUILDS_RESPONSE")"

        if [[ -z "$SERVER_URL" || -z "$SERVER_BUILD" ]]; then
            SERVER_URL="$(jq -r 'max_by(.id) | .downloads."server:default".url // empty' <<<"$BUILDS_RESPONSE")"
            SERVER_BUILD="$(jq -r 'max_by(.id) | .id // empty' <<<"$BUILDS_RESPONSE")"
            SERVER_CHANNEL="$(jq -r 'max_by(.id) | .channel // "unknown"' <<<"$BUILDS_RESPONSE")"
        fi
    else
        SERVER_URL="$(jq -r 'max_by(.id) | .downloads."server:default".url // empty' <<<"$BUILDS_RESPONSE")"
        SERVER_BUILD="$(jq -r 'max_by(.id) | .id // empty' <<<"$BUILDS_RESPONSE")"
        SERVER_CHANNEL="$(jq -r 'max_by(.id) | .channel // "unknown"' <<<"$BUILDS_RESPONSE")"
    fi

    if [[ -z "$SERVER_URL" || -z "$SERVER_BUILD" ]]; then
        mark_unavailable "No downloadable ${SOFTWARE_NAME} server JAR is published for Minecraft ${MC_VERSION}."
    fi
elif [[ "$SOFTWARE" == "purpur" ]]; then
    PURPUR_META_URL="https://api.purpurmc.org/v2/purpur/${MC_VERSION}"
    if ! PURPUR_META="$(fetch_json "$PURPUR_META_URL")"; then
        mark_unavailable "Purpur downloads service has no usable Minecraft ${MC_VERSION} build metadata."
    fi
    SERVER_BUILD="$(jq -r '.builds.latest // empty' <<<"$PURPUR_META")"
    if [[ -z "$SERVER_BUILD" || "$SERVER_BUILD" == "null" ]]; then
        mark_unavailable "No Purpur build is published for Minecraft ${MC_VERSION}."
    fi
    SERVER_URL="https://api.purpurmc.org/v2/purpur/${MC_VERSION}/${SERVER_BUILD}/download"
    SERVER_CHANNEL="latest"
else
    LEAF_VERSION_URL="https://api.leafmc.one/v2/projects/leaf/versions/${MC_VERSION}"
    if ! LEAF_VERSION="$(fetch_json "$LEAF_VERSION_URL")"; then
        mark_unavailable "Leaf downloads service has no usable Minecraft ${MC_VERSION} build metadata."
    fi
    SERVER_BUILD="$(jq -r '(.builds // []) | max // empty' <<<"$LEAF_VERSION")"
    if [[ -z "$SERVER_BUILD" || "$SERVER_BUILD" == "null" ]]; then
        mark_unavailable "No Leaf build is published for Minecraft ${MC_VERSION}."
    fi

    LEAF_BUILD_URL="https://api.leafmc.one/v2/projects/leaf/versions/${MC_VERSION}/builds/${SERVER_BUILD}"
    if ! LEAF_BUILD="$(fetch_json "$LEAF_BUILD_URL")"; then
        mark_unavailable "Leaf build ${SERVER_BUILD} metadata is unavailable for Minecraft ${MC_VERSION}."
    fi
    LEAF_FILENAME="$(jq -r '.downloads.application.name // ([.downloads[]? | .name] | first) // empty' <<<"$LEAF_BUILD")"
    SERVER_CHANNEL="$(jq -r '.channel // "unknown"' <<<"$LEAF_BUILD")"
    if [[ -z "$LEAF_FILENAME" || "$LEAF_FILENAME" == "null" ]]; then
        mark_unavailable "Leaf did not publish a server JAR for Minecraft ${MC_VERSION} build ${SERVER_BUILD}."
    fi
    SERVER_URL="https://api.leafmc.one/v2/projects/leaf/versions/${MC_VERSION}/builds/${SERVER_BUILD}/downloads/${LEAF_FILENAME}"
fi

printf 'Software: %s\nMinecraft: %s\nBuild: %s\nChannel: %s\nDownload: %s\nJava: %s\n' \
    "$SOFTWARE_NAME" "$MC_VERSION" "$SERVER_BUILD" "$SERVER_CHANNEL" "$SERVER_URL" "$(java -version 2>&1 | head -n 1)" \
    > "$WORK_DIR/runtime-build.txt"

if ! curl --fail-with-body -L -sS -H "User-Agent: ${USER_AGENT}" -o "$WORK_DIR/server.jar" "$SERVER_URL"; then
    mark_unavailable "${SOFTWARE_NAME} server JAR download failed for Minecraft ${MC_VERSION} build ${SERVER_BUILD}."
fi

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
        echo "${SOFTWARE_NAME} did not stop cleanly within ${SHUTDOWN_TIMEOUT_SECONDS}s; terminating it." >&2
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
        echo "${SOFTWARE_NAME} runtime smoke ${label}: server did not reach Done." >&2
        stop_process "$server_pid" 3
        wait "$server_pid" >/dev/null 2>&1 || true
        exec 3>&-
        cat "$console_log" >&2 || true
        return 1
    fi

    printf 'sf doctor upgrade\n' >&3
    sleep 4
    printf 'sf versions\n' >&3
    sleep 4
    stop_process "$server_pid" 3

    local server_status=0
    wait "$server_pid" || server_status=$?
    exec 3>&-

    if (( server_status != 0 )); then
        echo "${SOFTWARE_NAME} runtime smoke ${label}: server exited with status ${server_status}." >&2
        cat "$console_log" >&2 || true
        return 1
    fi

    if ! grep -Fq "Enabling Slimefun v${EXPECTED_PLUGIN_VERSION}" "$console_log"; then
        echo "${SOFTWARE_NAME} runtime smoke ${label}: Slimefun ${EXPECTED_PLUGIN_VERSION} was not observed enabling." >&2
        cat "$console_log" >&2 || true
        return 1
    fi

    if grep -Eq 'Error occurred while enabling Slimefun|Could not load .*Slimefun' "$console_log"; then
        echo "${SOFTWARE_NAME} runtime smoke ${label}: Slimefun reported an enable/load failure." >&2
        cat "$console_log" >&2 || true
        return 1
    fi

    if ! grep -Fq 'Slimefun Upgrade Readiness' "$console_log"; then
        echo "${SOFTWARE_NAME} runtime smoke ${label}: /sf doctor upgrade did not produce its report." >&2
        cat "$console_log" >&2 || true
        return 1
    fi

    if grep -Eq 'Overall status:[[:space:]]+BLOCKED' "$console_log"; then
        echo "${SOFTWARE_NAME} runtime smoke ${label}: /sf doctor upgrade reported BLOCKED." >&2
        cat "$console_log" >&2 || true
        return 1
    fi

    if grep -Fq 'Command exception: /sf versions' "$console_log"; then
        echo "${SOFTWARE_NAME} runtime smoke ${label}: /sf versions threw a command exception." >&2
        cat "$console_log" >&2 || true
        return 1
    fi

    if ! grep -Fq 'Slimefun server environment:' "$console_log"; then
        echo "${SOFTWARE_NAME} runtime smoke ${label}: /sf versions did not produce its environment report." >&2
        cat "$console_log" >&2 || true
        return 1
    fi

    if [[ "$require_previous_clean" == true ]] && ! grep -Eq 'previous shutdown[[:space:]]+Clean' "$console_log"; then
        echo "${SOFTWARE_NAME} runtime smoke ${label}: the second boot did not observe a clean prior Slimefun shutdown." >&2
        cat "$console_log" >&2 || true
        return 1
    fi

    if ! grep -Fq 'Stopping server' "$console_log"; then
        echo "${SOFTWARE_NAME} runtime smoke ${label}: a normal server shutdown was not observed." >&2
        cat "$console_log" >&2 || true
        return 1
    fi
}

run_cycle "first" false
run_cycle "second" true

cat > "$WORK_DIR/smoke-result.txt" <<EOF
Slimefun Legacy ${SOFTWARE_NAME} ${MC_VERSION} runtime smoke: PASS
Slimefun Legacy: ${EXPECTED_PLUGIN_VERSION}
Minecraft: ${MC_VERSION}
${SOFTWARE_NAME} build: ${SERVER_BUILD}
Channel: ${SERVER_CHANNEL}
Runtime Java: $(java -version 2>&1 | head -n 1)
Cycles: 2
Upgrade diagnostics: executed on both boots without BLOCKED status
/sf versions diagnostics: executed on both boots without command exception
Clean shutdown persistence: observed on second boot
EOF
cat "$WORK_DIR/smoke-result.txt"
