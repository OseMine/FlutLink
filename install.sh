#!/usr/bin/env bash
#
# FlutLink install wrapper.
#
# Installs the FlutCloud Nextcloud app on the server when a Nextcloud
# installation (a folder containing occ) is found, otherwise installs the
# FlutLink desktop client. The actual installers live in scripts/ and are
# fetched from this repository, so this wrapper always runs the current
# version.
#
# Usage:
#   curl -sSL https://raw.githubusercontent.com/OseMine/FlutLink/main/install.sh | bash
#   ./install.sh --path ~/nextcloud            # force the server app install
#   ./install.sh --nextcloud-root ~/nextcloud  # same
#   ./install.sh --tag v0.1.0 --no-run         # client, specific release, download only
#
set -euo pipefail

BASE="https://raw.githubusercontent.com/OseMine/FlutLink/main/scripts"

ROOT_ARG=""
PASSTHROUGH=()

while [ $# -gt 0 ]; do
    case "$1" in
        --path|--nextcloud-root)
            [ $# -ge 2 ] || { echo "Error: $1 requires a path argument." >&2; exit 2; }
            ROOT_ARG="$2"
            shift 2
            ;;
        *) PASSTHROUGH+=("$1"); shift ;;
    esac
done

detect_occ() {
    local dir="$PWD"
    while [ "$dir" != "/" ]; do
        if [ -f "$dir/occ" ]; then
            echo "$dir"
            return 0
        fi
        dir="$(dirname "$dir")"
    done
    local c
    for c in "$HOME/nextcloud" "$HOME/htdocs/nextcloud" /var/www/nextcloud /var/www/html /srv/nextcloud /usr/share/webapps/nextcloud; do
        if [ -f "$c/occ" ]; then
            echo "$c"
            return 0
        fi
    done
    return 1
}

SERVER_MODE=0
if [ -n "$ROOT_ARG" ]; then
    SERVER_MODE=1
else
    DETECTED="$(detect_occ || true)"
    if [ -n "$DETECTED" ]; then
        SERVER_MODE=1
    fi
fi

if [ "$SERVER_MODE" = 1 ]; then
    if [ -n "$ROOT_ARG" ]; then
        PASSTHROUGH+=(--nextcloud-root "$ROOT_ARG")
    fi
    echo "Installing the FlutCloud Nextcloud app (server) ..."
    curl -sSL "$BASE/install-flutcloud-app.sh" | bash -s -- "${PASSTHROUGH[@]}"
else
    echo "Installing the FlutLink desktop client ..."
    curl -sSL "$BASE/install-flutlink.sh" | bash -s -- "${PASSTHROUGH[@]}"
fi
