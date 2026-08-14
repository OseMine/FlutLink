#!/usr/bin/env bash
#
# Installs the FlutCloud Nextcloud app on a Nextcloud server (Ubuntu/Debian
# or any Linux with the occ script) from the FlutLink repository, then
# enables it with occ and verifies it.
#
# Usage:
#   curl -sL https://raw.githubusercontent.com/OseMine/FlutLink/main/scripts/install-flutcloud-app.sh | bash
#
# Options (only when saved to a file and executed):
#   -d, --nextcloud-root <path>   Nextcloud installation (folder containing occ).
#   -r, --ref <ref>               Git ref (release tag like "v0.1.0" or branch).
#   -u, --web-user <user>         Web-server user; default "www-data".
#   -c, --docker-container <id>   Nextcloud Docker container (occ via docker exec).
#   -C, --composer                Run "composer install --no-dev" in the app folder.
#   -n, --no-sudo                 Run occ/chown directly (as the web-server user or root).
#   -S, --skip-verify             Do not verify that the app is enabled afterwards.
#   -h, --help                    Show this help.
#
set -euo pipefail

REPO="OseMine/FlutLink"
UA="FlutCloud-install-script (sh; +https://github.com/OseMine/FlutLink)"
NEXTCLOUD_ROOT=""
REF=""
WEB_USER="www-data"
DOCKER_CONTAINER=""
COMPOSER=0
NO_SUDO=0
SKIP_VERIFY=0

die() { echo "Error: $*" >&2; exit 1; }
warn() { echo "Warning: $*" >&2; }

usage() {
    sed -n '2,20p' "$0"
    exit 0
}

while [ $# -gt 0 ]; do
    case "$1" in
        -d|--nextcloud-root) NEXTCLOUD_ROOT="${2:-}"; shift 2 ;;
        -r|--ref) REF="${2:-}"; shift 2 ;;
        -u|--web-user) WEB_USER="${2:-}"; shift 2 ;;
        -c|--docker-container) DOCKER_CONTAINER="${2:-}"; shift 2 ;;
        -C|--composer) COMPOSER=1; shift ;;
        -n|--no-sudo) NO_SUDO=1; shift ;;
        -S|--skip-verify) SKIP_VERIFY=1; shift ;;
        -h|--help) usage ;;
        *) die "Unknown option: $1 (see --help)" ;;
    esac
done

resolve_root() {
    if [ -n "$NEXTCLOUD_ROOT" ]; then
        [ -f "$NEXTCLOUD_ROOT/occ" ] || die "'$NEXTCLOUD_ROOT' does not contain the Nextcloud occ script."
        echo "$NEXTCLOUD_ROOT"
        return
    fi
    if [ -f "occ" ]; then
        echo "$(pwd)"
        return
    fi
    for c in /var/www/nextcloud /var/www/html /srv/nextcloud /usr/share/webapps/nextcloud; do
        if [ -f "$c/occ" ]; then
            echo "$c"
            return
        fi
    done
    die 'Could not locate the Nextcloud installation (no occ found). Pass --nextcloud-root.'
}

resolve_ref() {
    if [ -n "$REF" ]; then
        echo "$REF"
        return
    fi
    local tag=""
    tag="$(curl -fsSL -A "$UA" "https://api.github.com/repos/$REPO/releases/latest" 2>/dev/null | grep -o '"tag_name":[[:space:]]*"[^"]*"' | sed 's/.*"\([^"]*\)"$/\1/' | sed -n '1p' || true)"
    if [ -n "$tag" ]; then
        echo "$tag"
    else
        warn 'Could not query the latest release; falling back to the "main" branch.'
        echo "main"
    fi
}

run_occ() {
    local dir
    dir="$(pwd)"
    cd "$ROOT"
    if [ -n "$DOCKER_CONTAINER" ]; then
        docker exec -u "$WEB_USER" "$DOCKER_CONTAINER" php occ "$@"
    elif [ "$NO_SUDO" = 1 ]; then
        php occ "$@"
    else
        sudo -u "$WEB_USER" php occ "$@"
    fi
    cd "$dir"
}

ROOT="$(resolve_root)"
echo "Nextcloud root: $ROOT"
ROOT="$(cd "$ROOT" && pwd)"

REF_VALUE="$(resolve_ref)"
echo "Installing flutcloud app from ref: $REF_VALUE"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

case "$REF_VALUE" in
    v[0-9]*) ARCHIVE_URL="https://github.com/$REPO/archive/refs/tags/$REF_VALUE.tar.gz" ;;
    *) ARCHIVE_URL="https://github.com/$REPO/archive/refs/heads/$REF_VALUE.tar.gz" ;;
esac

echo "Downloading $ARCHIVE_URL ..."
curl -fsSL -A "$UA" -o "$TMP/flutcloud.tar.gz" "$ARCHIVE_URL"
mkdir -p "$TMP/src"
tar -xzf "$TMP/flutcloud.tar.gz" -C "$TMP/src"

APP_SOURCE="$(find "$TMP/src" -maxdepth 2 -type d -name flutcloud-app | sed -n '1p')"
[ -n "$APP_SOURCE" ] || die 'flutcloud-app folder not found in the downloaded archive.'

DEST="$ROOT/apps/flutcloud"
echo "Installing app to $DEST ..."

NEED_SUDO=0
if [ "$NO_SUDO" != 1 ] && [ "$(id -u)" != 0 ] && ! [ -w "$ROOT/apps" ]; then
    NEED_SUDO=1
    if ! command -v sudo >/dev/null 2>&1; then
        die "'$ROOT/apps' is not writable and 'sudo' is not available; run the script with --no-sudo as a user with write access (e.g. root)."
    fi
fi

if [ "$NEED_SUDO" = 1 ]; then
    sudo mkdir -p "$DEST"
    sudo cp -a "$APP_SOURCE/." "$DEST/"
else
    mkdir -p "$DEST"
    cp -a "$APP_SOURCE/." "$DEST/"
fi

if [ "$COMPOSER" = 1 ]; then
    if command -v composer >/dev/null 2>&1; then
        echo 'Generating the Composer autoloader ...'
        if [ "$NEED_SUDO" = 1 ]; then
            ( cd "$DEST" && sudo -E env COMPOSER_ALLOW_SUPERUSER=1 composer install --no-dev )
        else
            ( cd "$DEST" && composer install --no-dev )
        fi
    else
        warn 'composer not found; skipping autoloader generation (not required).'
    fi
fi

if [ -z "$DOCKER_CONTAINER" ]; then
    if [ "$NO_SUDO" = 1 ]; then
        chown -R "$WEB_USER:$WEB_USER" "$DEST" || warn "chown on $DEST failed; the app may still work if $WEB_USER can read it."
    else
        sudo chown -R "$WEB_USER:$WEB_USER" "$DEST" || warn "chown on $DEST failed; the app may still work if $WEB_USER can read it."
    fi
fi

echo 'Enabling the app: php occ app:enable flutcloud'
run_occ app:enable flutcloud

if [ "$SKIP_VERIFY" != 1 ]; then
    echo 'Verifying the app is enabled ...'
    OUTPUT="$(run_occ app:list)"
    if printf '%s\n' "$OUTPUT" | grep -q flutcloud; then
        echo 'OK: the flutcloud app is enabled.'
    else
        die 'The flutcloud app did not show up in "occ app:list".'
    fi
fi

echo "Done. FlutLink can now connect to this server as a FlutCloud instance."
