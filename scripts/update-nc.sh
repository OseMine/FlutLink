#!/usr/bin/env bash
set -euo pipefail

CLONE_DIR="/home/ubuntu/gh"
NEXTCLOUD_APPS="/home/ubuntu/nextcloud/apps"

mkdir -p "$CLONE_DIR"
cd "$CLONE_DIR"

sudo rm -rf FlutLink
git clone --depth 1 --branch v1.2.0 https://github.com/OseMine/FlutLink.git

sudo rm -rf "$NEXTCLOUD_APPS/flutcloud"
sudo mv FlutLink/flutcloud-app "$NEXTCLOUD_APPS/flutcloud"
sudo chown -R www-data:www-data "$NEXTCLOUD_APPS/flutcloud"

cd /home/ubuntu/nextcloud
sudo -u www-data php occ app:enable flutcloud
