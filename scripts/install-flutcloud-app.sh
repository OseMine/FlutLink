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
#   -r, --ref <ref>               Install a specific ref instead of the latest
#                                 release: a release tag ("v1.0.0") uses its
#                                 flutcloud-app.zip asset (falling back to the
#                                 tagged sources), a branch name uses the
#                                 current branch sources.
#   -u, --web-user <user>         Web-server user; default "www-data".
#   -c, --docker-container <id>   Nextcloud Docker container (occ via docker exec).
#   -C, --composer                Run "composer install --no-dev" in the app folder.
#   -n, --no-sudo                 Run occ/chown directly (as the web-server user or root).
#   -S, --skip-verify             Do not verify that the app is enabled afterwards.
#   -h, --help                    Show this help.
#
# When run interactively and no --nextcloud-root is given, the script asks
# you to confirm the auto-detected path or enter the one where you installed
# Nextcloud. Non-interactive runs (piped) skip the prompt.
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

has_tty() {
    local tty="${FLUTCLOUD_TTY:-/dev/tty}"
    [ -r "$tty" ] && [ -w "$tty" ] 2>/dev/null
}

prompt_user() {
    local message="$1" tty="${FLUTCLOUD_TTY:-/dev/tty}" ans=""
    printf '%s' "$message" > "$tty" 2>/dev/null || return 1
    read -r ans < "$tty" 2>/dev/null || return 1
    printf '%s' "$ans"
}

usage() {
    sed -n '2,23p' "$0"
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
    local detected="" ans=""
    if [ -n "$NEXTCLOUD_ROOT" ]; then
        [ -f "$NEXTCLOUD_ROOT/occ" ] || die "'$NEXTCLOUD_ROOT' does not contain the Nextcloud occ script."
        echo "$NEXTCLOUD_ROOT"
        return
    fi
    if [ -f "occ" ]; then
        detected="$(pwd)"
    else
        for c in /var/www/nextcloud /var/www/html /srv/nextcloud /usr/share/webapps/nextcloud; do
            if [ -f "$c/occ" ]; then
                detected="$c"
                break
            fi
        done
    fi
    if has_tty; then
        if [ -n "$detected" ]; then
            while :; do
                ans="$(prompt_user "Nextcloud installation found at '$detected'. Press Enter to use it, or enter a different path: ")"
                if [ -z "$ans" ]; then
                    echo "$detected"
                    return
                fi
                if [ -f "$ans/occ" ]; then
                    echo "$ans"
                    return
                fi
                echo "No occ script found in '$ans'." >&2
            done
        else
            while :; do
                ans="$(prompt_user "Could not locate your Nextcloud installation. Enter the path to the folder containing occ (e.g. /var/www/nextcloud): ")"
                if [ -z "$ans" ]; then
                    die 'No path given; aborting.'
                fi
                if [ -f "$ans/occ" ]; then
                    echo "$ans"
                    return
                fi
                echo "No occ script found in '$ans'." >&2
            done
        fi
    fi
    if [ -n "$detected" ]; then
        echo "$detected"
        return
    fi
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

occ_output() {
    local dir
    dir="$(pwd)"
    cd "$ROOT"
    if [ -n "$DOCKER_CONTAINER" ]; then
        docker exec -u "$WEB_USER" "$DOCKER_CONTAINER" php occ "$@" 2>&1
    elif [ "$NO_SUDO" = 1 ]; then
        php occ "$@" 2>&1
    else
        sudo -u "$WEB_USER" php occ "$@" 2>&1
    fi
    cd "$dir"
}

ROOT="$(resolve_root)"
echo "Nextcloud root: $ROOT"
ROOT="$(cd "$ROOT" && pwd)"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

ASSET="flutcloud-app.zip"
ZIP="$TMP/$ASSET"

# Downloads the packaged app from a GitHub release.
fetch_release_zip() {
    echo "Downloading $1 ..."
    curl -fsSL -A "$UA" -o "$ZIP" "$1"
}

FROM_RELEASE=0
if [ -n "$REF" ] && printf '%s' "$REF" | grep -Eq '^v[0-9]'; then
    echo "Installing flutcloud app from release $REF ..."
    if fetch_release_zip "https://github.com/$REPO/releases/download/$REF/$ASSET"; then
        FROM_RELEASE=1
    else
        warn "Release $REF has no flutcloud-app.zip; falling back to its repository sources."
    fi
elif [ -z "$REF" ]; then
    echo 'Installing flutcloud app from the latest release ...'
    if fetch_release_zip "https://github.com/$REPO/releases/latest/download/$ASSET"; then
        FROM_RELEASE=1
    else
        warn 'Falling back to the repository sources.'
    fi
fi

if [ "$FROM_RELEASE" = 1 ]; then
    # Release zip: the archive root contains the Nextcloud app directly.
    mkdir -p "$TMP/src"
    if command -v unzip >/dev/null 2>&1; then
        unzip -qo "$ZIP" -d "$TMP/src" || die 'Failed to extract flutcloud-app.zip (unzip).'
    elif command -v python3 >/dev/null 2>&1; then
        python3 -m zipfile -e "$ZIP" "$TMP/src" || die 'Failed to extract flutcloud-app.zip (python3).'
    else
        die 'Need "unzip" (or python3) to extract flutcloud-app.zip.'
    fi
    APP_SOURCE="$TMP/src"
else
    REF_VALUE="$(resolve_ref)"
    echo "Installing flutcloud app from ref: $REF_VALUE"

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
fi

[ -f "$APP_SOURCE/appinfo/info.xml" ] || die 'The downloaded archive does not contain appinfo/info.xml.'

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
OUTPUT="$(occ_output app:enable flutcloud)"
printf '%s\n' "$OUTPUT"
if printf '%s\n' "$OUTPUT" | grep -qi 'require upgrade'; then
    echo 'Nextcloud reports that an upgrade is required; running "occ upgrade" first ...'
    printf '%s\n' "$(occ_output upgrade)"
    echo 'Retrying app:enable ...'
    OUTPUT="$(occ_output app:enable flutcloud)"
    printf '%s\n' "$OUTPUT"
fi
if printf '%s\n' "$OUTPUT" | grep -qi 'not compatible with this version of the server'; then
    die 'The flutcloud app is not compatible with this Nextcloud version. The version range declared in flutcloud-app/appinfo/info.xml must include it.'
fi

if [ "$SKIP_VERIFY" != 1 ]; then
    echo 'Verifying the app is enabled ...'
    LISTING="$(occ_output app:list)"
    if printf '%s\n' "$LISTING" | grep -q flutcloud; then
        echo 'OK: the flutcloud app is enabled.'
    else
        die 'The flutcloud app did not show up in "occ app:list".'
    fi
fi

echo "Done. FlutLink can now connect to this server as a FlutCloud instance."
echo 'Optional: to also serve the iOS AltStore sources at <server>/ios/{pal,classic}, add the web-server rewrite from flutcloud-app/README.md.'
