#!/usr/bin/env bash
#
# Installs the FlutLink desktop client from the latest GitHub release on
# Linux and macOS.
#
# Usage:
#   curl -sL https://raw.githubusercontent.com/OseMine/FlutLink/main/scripts/install-flutlink.sh | bash
#
# Options (only when saved to a file and executed):
#   -t, --tag <ref>     Install a specific release tag (e.g. "v0.1.0").
#   -d, --dir <path>    Directory used for the downloaded installer.
#   -n, --no-run        Only download (and verify); do not install.
#   -S, --no-verify     Skip the SHA-256 checksum verification (not recommended).
#   -h, --help          Show this help.
#
set -euo pipefail

REPO="OseMine/FlutLink"
UA="FlutLink-install-script (sh; +https://github.com/OseMine/FlutLink)"
TAG=""
DOWNLOAD_DIR=""
NORUN=0
NOVERIFY=0

die() { echo "Error: $*" >&2; exit 1; }
warn() { echo "Warning: $*" >&2; }

usage() {
    sed -n '2,13p' "$0"
    exit 0
}

while [ $# -gt 0 ]; do
    case "$1" in
        -t|--tag) TAG="${2:-}"; shift 2 ;;
        -d|--dir) DOWNLOAD_DIR="${2:-}"; shift 2 ;;
        -n|--no-run) NORUN=1; shift ;;
        -S|--no-verify) NOVERIFY=1; shift ;;
        -h|--help) usage ;;
        *) die "Unknown option: $1 (see --help)" ;;
    esac
done

# --- JSON parsing (jq preferred, python3 fallback) --------------------------
HAVE_JQ=0; HAVE_PY=0
command -v jq >/dev/null 2>&1 && HAVE_JQ=1
command -v python3 >/dev/null 2>&1 && HAVE_PY=1
[ "$HAVE_JQ" = 1 ] || [ "$HAVE_PY" = 1 ] || die "Need 'jq' or 'python3' to parse the GitHub release metadata."

json_tag() {
    if [ "$HAVE_JQ" = 1 ]; then
        jq -r '.tag_name'
    else
        python3 -c 'import json,sys;print(json.load(sys.stdin)["tag_name"])'
    fi
}

json_assets() {
    if [ "$HAVE_JQ" = 1 ]; then
        jq -r '.assets[] | .name + "|" + (.digest // "") + "|" + .browser_download_url'
    else
        python3 -c 'import json,sys;d=json.load(sys.stdin);[print(a["name"]+"|"+(a.get("digest") or "")+"|"+a["browser_download_url"]) for a in d["assets"]]'
    fi
}

pick_asset() {
    local list="$1" pattern="$2"
    printf '%s\n' "$list" | grep -E "$pattern" | sed -n '1p' || true
}

get_release_json() {
    local url
    if [ -n "$TAG" ]; then
        url="https://api.github.com/repos/$REPO/releases/tags/$TAG"
    else
        url="https://api.github.com/repos/$REPO/releases/latest"
    fi
    curl -fsSL -A "$UA" "$url"
}

# --- SHA-256 verification ----------------------------------------------------
verify_sha256() {
    local file="$1" expected="$2" actual=""
    if command -v sha256sum >/dev/null 2>&1; then
        actual="$(sha256sum "$file" | awk '{print $1}')"
    elif command -v shasum >/dev/null 2>&1; then
        actual="$(shasum -a 256 "$file" | awk '{print $1}')"
    else
        warn "Neither sha256sum nor shasum is available; skipping checksum verification."
        return 0
    fi
    actual_lc="$(printf '%s' "$actual" | tr '[:upper:]' '[:lower:]')"
    expected_lc="$(printf '%s' "$expected" | tr '[:upper:]' '[:lower:]')"
    if [ "$actual_lc" != "$expected_lc" ]; then
        die "SHA-256 mismatch for $file. Refusing to run the installer."
    fi
    echo "SHA-256 verified."
}

# --- Installation --------------------------------------------------------------
install_macos() {
    local file="$1" mount app dest
    mount="$(hdiutil attach -nobrowse -readonly -quiet "$file" | awk '/\/Volumes\//{print $NF}' | tail -n 1)"
    [ -n "$mount" ] || die "Could not mount the DMG."
    trap 'hdiutil detach "$mount" -quiet >/dev/null 2>&1 || true' EXIT
    app="$(find "$mount" -maxdepth 2 -name '*.app' | sed -n '1p')"
    [ -n "$app" ] || die "No .app bundle found in the DMG."
    dest="/Applications/$(basename "$app")"
    if [ ! -d "$dest" ]; then
        ditto "$app" "$dest"
    fi
    echo "Installed $dest"
    trap - EXIT
    hdiutil detach "$mount" -quiet
}

install_linux_appimage() {
    local file="$1" bin_dir target
    if [ -d "$HOME/.local/bin" ]; then
        bin_dir="$HOME/.local/bin"
    elif [ -w /usr/local/bin ]; then
        bin_dir="/usr/local/bin"
    else
        bin_dir="$HOME/.local/bin"
        mkdir -p "$bin_dir"
    fi
    target="$bin_dir/FlutLink.AppImage"
    cp -f "$file" "$target"
    chmod +x "$target"
    echo "Installed $target"
}

install_linux_deb() {
    local file="$1" cmd=()
    if command -v sudo >/dev/null 2>&1; then
        cmd=(sudo)
    elif command -v pkexec >/dev/null 2>&1; then
        cmd=(pkexec)
    else
        die "Neither sudo nor pkexec is available to install the .deb."
    fi
    "${cmd[@]}" dpkg -i "$file"
}

install_artifact() {
    local file="$1" platform="$2"
    case "$platform" in
        macos) install_macos "$file" ;;
        linux)
            case "$file" in
                *.AppImage) install_linux_appimage "$file" ;;
                *.deb) install_linux_deb "$file" ;;
                *) die "Unsupported Linux installer: $file" ;;
            esac
            ;;
    esac
}

# --- Main ---------------------------------------------------------------------
case "$(uname -s)" in
    Linux) PLATFORM=linux ;;
    Darwin) PLATFORM=macos ;;
    *) die "Unsupported OS: $(uname -s) (use the PowerShell script on Windows)." ;;
esac
echo "Platform: $PLATFORM"

case "$(uname -m)" in
    aarch64|arm64) IS_ARM=1 ;;
    *) IS_ARM=0 ;;
esac

echo "Querying GitHub for the latest release ..."
RELEASE_JSON="$(get_release_json)"
TAG_NAME="$(printf '%s\n' "$RELEASE_JSON" | json_tag)"
echo "Release: $TAG_NAME"
ASSETS="$(printf '%s\n' "$RELEASE_JSON" | json_assets)"

ASSET=""
case "$PLATFORM" in
    macos)
        if [ "$IS_ARM" = 1 ]; then
            ASSET="$(pick_asset "$ASSETS" '^[^|]*_aarch64\.dmg\|')"
        fi
        if [ -z "$ASSET" ]; then
            ASSET="$(pick_asset "$ASSETS" '^[^|]*_x64\.dmg\|')"
        fi
        if [ -z "$ASSET" ]; then
            ASSET="$(pick_asset "$ASSETS" '^[^|]*\.dmg\|')"
        fi
        ;;
    linux)
        ASSET="$(pick_asset "$ASSETS" '^[^|]*\.AppImage\|')"
        if [ -z "$ASSET" ]; then
            ASSET="$(pick_asset "$ASSETS" '^[^|]*\.deb\|')"
        fi
        ;;
esac

[ -n "$ASSET" ] || die "No installer asset found for $PLATFORM in $TAG_NAME."

ASSET_NAME="$(printf '%s' "$ASSET" | awk -F'|' '{print $1}')"
ASSET_DIGEST="$(printf '%s' "$ASSET" | awk -F'|' '{print $2}')"
ASSET_URL="$(printf '%s' "$ASSET" | awk -F'|' '{print $3}')"
[ -n "$ASSET_URL" ] || die "No download URL for asset $ASSET_NAME."

if [ -n "$DOWNLOAD_DIR" ]; then
    INSTALL_DIR="$DOWNLOAD_DIR"
else
    INSTALL_DIR="${TMPDIR:-/tmp}/FlutLink"
fi
mkdir -p "$INSTALL_DIR"
INSTALLER="$INSTALL_DIR/$ASSET_NAME"

if [ -f "$INSTALLER" ]; then
    echo "Using cached $INSTALLER"
else
    echo "Downloading $ASSET_NAME ..."
    curl -fsSL -A "$UA" -o "$INSTALLER" "$ASSET_URL"
fi

if [ "$NOVERIFY" != 1 ]; then
    if [ -n "$ASSET_DIGEST" ]; then
        EXPECTED="${ASSET_DIGEST#sha256:}"
        verify_sha256 "$INSTALLER" "$EXPECTED"
    else
        warn "GitHub did not provide a digest for this asset; skipping checksum verification."
    fi
fi

if [ "$NORUN" = 1 ]; then
    echo "Downloaded installer: $INSTALLER"
else
    install_artifact "$INSTALLER" "$PLATFORM"
    echo "FlutLink ${TAG_NAME#v} installed."
fi
