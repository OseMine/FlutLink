#!/usr/bin/env bash
# Deploys the latest released version of the FlutCloud Nextcloud app to a
# server's Nextcloud instance and enables it with occ.
#
# Usage:
#   scripts/update-nc.sh [tag]
#
# The optional positional argument selects a specific release tag (e.g.
# "v1.2.0"); without it the latest GitHub release is deployed.
#
# Environment (all optional, with defaults — same as update-nc-dev.sh):
#   FLUTCLOUD_NC_UPDATE_REPO   github.com owner/repo (default: OseMine/FlutLink)
#   FLUTCLOUD_NC_UPDATE_APPS   Nextcloud apps folder (default: /home/ubuntu/nextcloud/apps)
#   FLUTCLOUD_NC_UPDATE_ROOT   Nextcloud root containing occ (default: /home/ubuntu/nextcloud)
#   FLUTCLOUD_NC_UPDATE_APP    server-side app folder name (default: flutcloud)
#   FLUTCLOUD_NC_UPDATE_USER   web-server user (default: www-data)
set -euo pipefail

REPO="${FLUTCLOUD_NC_UPDATE_REPO:-OseMine/FlutLink}"
UA="FlutLink-update-script (sh; +https://github.com/$REPO)"
APPS="${FLUTCLOUD_NC_UPDATE_APPS:-/home/ubuntu/nextcloud/apps}"
NEXTCLOUD_ROOT="${FLUTCLOUD_NC_UPDATE_ROOT:-/home/ubuntu/nextcloud}"
APP_ID="${FLUTCLOUD_NC_UPDATE_APP:-flutcloud}"
WEB_USER="${FLUTCLOUD_NC_UPDATE_USER:-www-data}"

TAG="${1:-}"
if [ -z "$TAG" ]; then
    TAG="$(curl -fsSL -A "$UA" "https://api.github.com/repos/$REPO/releases/latest" | sed -n 's/.*"tag_name"[[:space:]]*:[[:space:]]*"\([^"]*\)"[,}]/\1/p' | sed -n '1p')"
fi
[ -n "$TAG" ] || { echo "Error: could not determine the latest release tag." >&2; exit 1; }
echo "Deploying release $TAG"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

curl -fsSL -A "$UA" -o "$TMP/flutcloud-app.zip" "https://github.com/$REPO/releases/download/$TAG/flutcloud-app.zip"
mkdir -p "$TMP/src"
if command -v unzip >/dev/null 2>&1; then
    unzip -qo "$TMP/flutcloud-app.zip" -d "$TMP/src"
else
    python3 -m zipfile -e "$TMP/flutcloud-app.zip" "$TMP/src" || { echo "Error: need 'unzip' (or python3) to extract flutcloud-app.zip." >&2; exit 1; }
fi

sudo rm -rf "$APPS/$APP_ID"
sudo mv "$TMP/src" "$APPS/$APP_ID"
sudo chown -R "$WEB_USER:$WEB_USER" "$APPS/$APP_ID"

cd "$NEXTCLOUD_ROOT"
sudo -u "$WEB_USER" php occ app:enable "$APP_ID"