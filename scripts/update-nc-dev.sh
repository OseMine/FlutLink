#!/usr/bin/env bash
# Deploys the current development version (main branch) of the FlutCloud
# Nextcloud app to a server's Nextcloud instance and enables it with occ.
#
# Usage:
#   scripts/update-nc-dev.sh
#
# Environment (all optional, with defaults):
#   FLUTCLOUD_NC_UPDATE_URL     git URL of the FlutLink repository
#                               (default: https://github.com/OseMine/FlutLink.git)
#   FLUTCLOUD_NC_UPDATE_BRANCH  branch to deploy (default: main)
#   FLUTCLOUD_NC_UPDATE_DIR     scratch dir for the git clone (default: /home/ubuntu/gh)
#   FLUTCLOUD_NC_UPDATE_APPS    Nextcloud apps folder (default: /home/ubuntu/nextcloud/apps)
#   FLUTCLOUD_NC_UPDATE_ROOT    Nextcloud root containing occ (default: /home/ubuntu/nextcloud)
#   FLUTCLOUD_NC_UPDATE_APP     server-side app folder name (default: flutcloud)
#   FLUTCLOUD_NC_UPDATE_USER    web-server user (default: www-data)
set -euo pipefail

REPO_URL="${FLUTCLOUD_NC_UPDATE_URL:-https://github.com/OseMine/FlutLink.git}"
BRANCH="${FLUTCLOUD_NC_UPDATE_BRANCH:-main}"
CLONE_DIR="${FLUTCLOUD_NC_UPDATE_DIR:-/home/ubuntu/gh}"
APPS="${FLUTCLOUD_NC_UPDATE_APPS:-/home/ubuntu/nextcloud/apps}"
NEXTCLOUD_ROOT="${FLUTCLOUD_NC_UPDATE_ROOT:-/home/ubuntu/nextcloud}"
APP_ID="${FLUTCLOUD_NC_UPDATE_APP:-flutcloud}"
WEB_USER="${FLUTCLOUD_NC_UPDATE_USER:-www-data}"

mkdir -p "$CLONE_DIR"
cd "$CLONE_DIR"

sudo rm -rf FlutLink
git clone --depth 1 --branch "$BRANCH" "$REPO_URL" FlutLink

sudo rm -rf "$APPS/$APP_ID"
sudo mv "FlutLink/flutcloud-app" "$APPS/$APP_ID"
sudo chown -R "$WEB_USER:$WEB_USER" "$APPS/$APP_ID"

cd "$NEXTCLOUD_ROOT"
sudo -u "$WEB_USER" php occ app:enable "$APP_ID"