#!/usr/bin/env bash
# Shared retry-safe public runtime downloads. No fallback to a different build.
# Consumers still validate metadata, version policy, hashes and plugin identity.
runtime_download() (
    set -euo pipefail
    if (( $# < 1 || $# > 2 )); then
        echo 'Usage: runtime_download <url> [destination]' >&2
        return 2
    fi
    local url="$1" destination="${2:-}" temporary
    local agent="${USER_AGENT:-Slimefun-Legacy-Validation/1.0} (https://github.com/wickidcow/Slimefun-Legacy)"
    if [[ -n "$destination" ]]; then
        temporary="$(mktemp "${destination}.partial.XXXXXX")"
    else
        temporary="$(mktemp "${TMPDIR:-/tmp}/slimefun-download.XXXXXX")"
    fi
    trap 'rm -f -- "$temporary"' EXIT
    # Always download to a file. Retried error bodies must not contaminate JSON
    # on stdout, and a failed transfer must never replace a known-good artifact.
    curl --fail --location --silent --show-error \
        --retry 4 --retry-delay 2 --retry-max-time 120 \
        --connect-timeout 15 --max-time 180 \
        --proto '=http,https' --proto-redir '=https' \
        -H "User-Agent: ${agent}" --output "$temporary" "$url" || {
            local status=$?
            echo "Runtime download failed after bounded retries (curl exit ${status}); no result was published." >&2
            return "$status"
        }
    if [[ ! -s "$temporary" ]]; then
        echo 'Runtime download was empty; no result was published.' >&2
        return 1
    fi
    if [[ -n "$destination" ]]; then
        mv -f -- "$temporary" "$destination"
    else
        cat -- "$temporary"
    fi
)
