#!/usr/bin/env bash
#
# Sequentially upgrades a Nextcloud installation that is several major
# versions behind. Nextcloud only supports one-major upgrades at a time, so
# this walks 30 -> 31 -> 32 -> 33 -> 34 (nextcloud-31.0.14/32.0.14/33.0.8/34.0.3)
# and applies the common MariaDB/MySQL workarounds for big jumps:
#   - strict sql_mode -> "Field 'id' doesn't have a default value" (SQLSTATE 1364)
#   - legacy tables whose primary 'id' is not AUTO_INCREMENT ->
#     "Duplicate entry '0' for key 'PRIMARY'" (SQLSTATE 1062)
#
# Usage (the script re-executes itself with sudo when needed):
#   curl -sSL https://raw.githubusercontent.com/OseMine/FlutLink/main/scripts/upgrade-nextcloud.sh | sudo bash -s -- --nextcloud-root ~/nextcloud
#
# Options (only when saved to a file and executed):
#   -d, --nextcloud-root <path>   Nextcloud installation (folder containing occ).
#   -u, --web-user <user>         Web-server user; default "www-data".
#   -b, --db-name <name>          Database name; default "nextcloud".
#       --backup-data             Also tar the data/ directory before starting.
#   -n, --no-fixes                Skip the sql_mode/AUTO_INCREMENT workarounds.
#   -h, --help                    Show this help.
#
# The download/extraction directory defaults to /var/tmp/nextcloud-upgrade;
# override it with the FLUTLINK_WORK_DIR environment variable.
#
set -u

ARGS=("$@")

REPO_URL="https://download.nextcloud.com/server/releases"
VERSIONS=(31.0.14 32.0.14 33.0.8 34.0.3)
TARGET="${VERSIONS[${#VERSIONS[@]}-1]}"
WEB_USER="www-data"
DB_NAME="nextcloud"
BACKUP_DATA=0
NO_FIXES=0
ROOT=""
WORK="${FLUTLINK_WORK_DIR:-/var/tmp/nextcloud-upgrade}"
LOG="$HOME/flutlink-nc-upgrade.log"
ORIG_SQL_MODE=""
DB=""

die() { echo "Error: $*" >&2; exit 1; }
warn() { echo "Warning: $*" >&2; }
log() { printf '%s\n' "[$(date '+%F %T')] $*" | tee -a "$LOG"; }

usage() {
    sed -n '2,24p' "$0"
    exit 0
}

while [ $# -gt 0 ]; do
    case "$1" in
        -d|--nextcloud-root) ROOT="${2:-}"; shift 2 ;;
        -u|--web-user) WEB_USER="${2:-}"; shift 2 ;;
        -b|--db-name) DB_NAME="${2:-}"; shift 2 ;;
        --backup-data) BACKUP_DATA=1; shift ;;
        -n|--no-fixes) NO_FIXES=1; shift ;;
        -h|--help) usage ;;
        *) die "Unknown option: $1 (see --help)" ;;
    esac
done

if [ "$(id -u)" -ne 0 ] && [ -z "${FLUTLINK_UNDER_SUDO:-}" ]; then
    if [ -f "$0" ]; then
        echo "Not running as root - re-executing via sudo ..."
        exec sudo -E bash "$0" "${ARGS[@]}"
    fi
    echo "Error: this script needs root privileges (Nextcloud is usually owned by the web user)." >&2
    echo 'Re-run with: curl -sSL https://raw.githubusercontent.com/OseMine/FlutLink/main/scripts/upgrade-nextcloud.sh | sudo bash -s -- --nextcloud-root ~/nextcloud' >&2
    exit 1
fi


resolve_root() {
    local c=""
    if [ -n "$ROOT" ]; then
        [ -f "$ROOT/occ" ] || die "'$ROOT' does not contain the Nextcloud occ script."
        echo "$ROOT"
        return
    fi
    if [ -f "occ" ]; then
        echo "$(pwd)"
        return
    fi
    for c in "$HOME/nextcloud" /var/www/nextcloud /var/www/html /srv/nextcloud /usr/share/webapps/nextcloud; do
        if [ -f "$c/occ" ]; then
            echo "$c"
            return
        fi
    done
    die 'Could not locate the Nextcloud installation. Pass --nextcloud-root.'
}

major() {
    local v="$1"
    echo "${v%%.*}"
}

db_version() {
    sudo grep -m1 "'version'" "$ROOT/config/config.php" 2>/dev/null \
        | sed -n -E "s/.*'version'[[:space:]]*=>[[:space:]]*'([^']*)'.*/\1/p" \
        | sed -n '1p'
}

run_occ() {
    ( cd "$ROOT" && sudo -u "$WEB_USER" php occ "$@" )
}

set_sql_mode() {
    [ "$NO_FIXES" = 1 ] && return 0
    [ -z "$DB" ] && return 0
    local out
    out="$($DB -N -e "SELECT @@GLOBAL.sql_mode;" 2>/dev/null)" || return 0
    case "$out" in
        *STRICT*)
            ORIG_SQL_MODE="$out"
            log "Relaxing strict sql_mode (will restore '$ORIG_SQL_MODE' at the end)."
            $DB -e "SET GLOBAL sql_mode='NO_ENGINE_SUBSTITUTION';" 2>/dev/null || warn 'Could not change sql_mode.'
            ;;
    esac
}

restore_sql_mode() {
    [ -n "$ORIG_SQL_MODE" ] || return 0
    [ -z "$DB" ] && return 0
    log "Restoring sql_mode: $ORIG_SQL_MODE"
    $DB -e "SET GLOBAL sql_mode='$ORIG_SQL_MODE';" 2>/dev/null || warn 'Could not restore sql_mode (a server restart also resets it).'
    ORIG_SQL_MODE=""
}

fix_auto_increment() {
    [ "$NO_FIXES" = 1 ] && return 0
    [ -z "$DB" ] && { warn 'No mariadb/mysql client found; skipping the AUTO_INCREMENT repair.'; return 0; }
    log 'Repairing primary "id" columns that are not AUTO_INCREMENT ...'
    local altered=0 stmt=""
    while IFS= read -r stmt; do
        [ -z "$stmt" ] && continue
        log "  ALTER: $stmt"
        if $DB "$DB_NAME" -e "$stmt" >/dev/null 2>&1; then
            altered=$((altered + 1))
        else
            warn "ALTER failed for: $stmt"
        fi
    done < <($DB -N "$DB_NAME" -e "
SELECT CONCAT('ALTER TABLE \`', c.table_name, '\` MODIFY \`', c.column_name, '\` ', c.column_type, ' NOT NULL AUTO_INCREMENT;')
FROM information_schema.columns c
WHERE c.table_schema='$DB_NAME'
  AND c.column_name='id'
  AND c.column_key='PRI'
  AND c.extra NOT LIKE '%auto_increment%'
  AND c.data_type IN ('tinyint','smallint','mediumint','int','bigint')
  AND (SELECT COUNT(*) FROM information_schema.statistics s
       WHERE s.table_schema='$DB_NAME' AND s.table_name=c.table_name
         AND s.index_name='PRIMARY') = 1;")
    log "Repair done ($altered column(s) fixed)."
}

fetch_code() {
    local VN="$1" archive
    if command -v unzip >/dev/null 2>&1; then
        archive="$WORK/nextcloud-$VN.zip"
        log "Downloading $REPO_URL/nextcloud-$VN.zip ..."
        curl -fSL -C - --retry 3 -o "$archive" "$REPO_URL/nextcloud-$VN.zip" || die "Download of $VN failed."
    else
        archive="$WORK/nextcloud-$VN.tar.bz2"
        log "Downloading $REPO_URL/nextcloud-$VN.tar.bz2 ..."
        curl -fSL -C - --retry 3 -o "$archive" "$REPO_URL/nextcloud-$VN.tar.bz2" || die "Download of $VN failed."
    fi
}

extract_code() {
    local VN="$1" outdir="$2"
    rm -rf "$outdir"
    mkdir -p "$outdir"
    if command -v unzip >/dev/null 2>&1; then
        ( cd "$outdir" && unzip -q "$WORK/nextcloud-$VN.zip" ) || die "Extracting $VN failed."
    else
        ( cd "$outdir" && tar -xjf "$WORK/nextcloud-$VN.tar.bz2" ) || die "Extracting $VN failed."
    fi
    [ -d "$outdir/nextcloud" ] || die "The $VN archive did not contain a 'nextcloud' folder."
}

apply_and_upgrade() {
    local VN="$1" want_major="$2"
    local attempt=0 code=0 now="" now_major="" logtmp="$WORK/occ-upgrade-$VN.log"

    log "Replacing the Nextcloud code with $VN ..."
    rsync -a "$WORK/x-$VN/nextcloud/" "$ROOT/" || die "rsync of $VN into $ROOT failed."
    chown -R "$WEB_USER:$WEB_USER" "$ROOT" || warn "chown of $ROOT failed (may still work if $WEB_USER can read it)."

    fix_auto_increment

    while :; do
        log "Running occ upgrade (DB -> $VN) ..."
        : > "$logtmp"
        run_occ upgrade 2>&1 | tee -a "$LOG" >> "$logtmp"
        code=${PIPESTATUS[0]}
        echo "occ upgrade (DB -> $VN) exited with code $code (full output: $logtmp)" | tee -a "$LOG"
        if [ "$code" -eq 0 ]; then
            break
        fi
        if grep -Eq "Field 'id' doesn't have a default value|Duplicate entry '0' for key 'PRIMARY'" "$logtmp"; then
            attempt=$((attempt + 1))
            if [ "$attempt" -gt 3 ]; then
                tail -n 30 "$logtmp" | tee -a "$LOG"
                die "occ upgrade to $VN keeps failing on the id/PRIMARY issue even after the AUTO_INCREMENT repair."
            fi
            log 'Upgrade hit a legacy-id issue; repairing AUTO_INCREMENT and retrying ...'
            fix_auto_increment
            continue
        fi
        tail -n 30 "$logtmp" | tee -a "$LOG"
        die "occ upgrade to $VN failed (see the last 30 lines above and $LOG)."
    done
    rm -f "$logtmp"

    now="$(db_version)"
    now_major="$(major "$now")"
    if [ "$now_major" != "$want_major" ]; then
        die "After $VN the DB version should be $want_major.x but config.php says '$now'."
    fi
    log "OK: database is now at $now."
}

disk_ok() {
    local avail
    avail="$(df -k "$WORK" 2>/dev/null | awk 'NR==2 {print $4}')"
    [ -n "${avail:-}" ] && [ "${avail:-0}" -gt 3000000 ]
}

command -v curl >/dev/null 2>&1 || die 'curl is required.'
command -v rsync >/dev/null 2>&1 || die 'rsync is required (sudo apt-get install -y rsync).'
command -v php >/dev/null 2>&1 || die 'php is not in PATH (are you on the Nextcloud server?).'
command -v sudo >/dev/null 2>&1 || die 'sudo is required.'

ROOT="$(resolve_root)"
ROOT="$(cd "$ROOT" && pwd)" || die "Cannot cd into '$ROOT'."
[ -f "$ROOT/config/config.php" ] || die "No config.php found in '$ROOT'."

mkdir -p "$WORK"
[ -d "$WORK" ] || die "Could not create the work directory '$WORK'."

if command -v mariadb >/dev/null 2>&1; then
    DB="sudo mariadb"
elif command -v mysql >/dev/null 2>&1; then
    DB="sudo mysql"
else
    warn 'Neither mariadb nor mysql was found; the sql_mode/AUTO_INCREMENT workarounds are disabled.'
fi
trap 'restore_sql_mode >/dev/null 2>&1' EXIT

log '==========================================================='
log "FlutLink sequential Nextcloud upgrade -> target $TARGET"
log "Nextcloud root : $ROOT"
log "Web user       : $WEB_USER"
log "Database name  : $DB_NAME"
log "Work dir       : $WORK"
log "Log file       : $LOG"
log '==========================================================='

if cp "$ROOT/config/config.php" "$ROOT/config/config.php.bak.$(date +%Y%m%d%H%M%S)"; then
    log 'Backed up config.php.'
else
    warn 'Could not back up config.php.'
fi

if ! disk_ok; then
    warn "Less than ~3 GB free on $WORK; the upgrade needs space for downloads and extraction."
fi

CUR="$(db_version)"
[ -n "$CUR" ] || die 'Could not read the installed version from config/config.php.'
CUR_MAJOR="$(major "$CUR")"
log "Current DB version: $CUR (major $CUR_MAJOR)"

if [ "$CUR_MAJOR" -ge "${TARGET%%.*}" ]; then
    log "Already at or above the target version; nothing to do."
    exit 0
fi

set_sql_mode

for VN in "${VERSIONS[@]}"; do
    VM="${VN%%.*}"
    if [ "$VM" -le "$CUR_MAJOR" ]; then
        log "Skipping $VN (already at major $CUR_MAJOR)."
        continue
    fi
    log "=== Step: upgrade to $VN (DB major $CUR_MAJOR -> $VM) ==="
    fetch_code "$VN"
    extract_code "$VN" "$WORK/x-$VN"
    apply_and_upgrade "$VN" "$VM"
    CUR_MAJOR="$VM"
    rm -rf "$WORK/x-$VN" "$WORK/nextcloud-$VN.zip" "$WORK/nextcloud-$VN.tar.bz2"
done

log '=== Finalizing ==='
restore_sql_mode
run_occ maintenance:mode --off || warn 'Could not switch maintenance mode off.'
for app in flutcloud files_lock impersonate registration; do
    if run_occ app:enable "$app" >/dev/null 2>&1; then
        log "Enabled app: $app"
    else
        warn "app:enable $app did not apply (app not present?)."
    fi
done

FINAL="$(db_version)"
log "Final installed version: $FINAL"
log 'Verify from a client: curl -u <user>:<token> https://<host>/ocs/v2.php/cloud/capabilities?format=json'
log 'Done.'
