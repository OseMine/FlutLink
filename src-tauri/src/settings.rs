//! #410: app-wide settings persisted in the app-data directory.
//!
//! Currently this holds the "share notifications enabled" toggle and the set of
//! share ids already notified per account. Kept as a tiny standalone file
//! (rather than part of the account config) so the sync worker can read/write
//! it in place without going through the account persistence round-trip.

use std::collections::btree_map::Entry;
use std::collections::BTreeMap;
use std::collections::HashSet;
use std::path::PathBuf;

use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Manager};

use crate::error::{AppError, AppResult};
use crate::state::{Account, AppState};

/// Persisted application settings (small, read-modify-written by the sync
/// worker on every notification check).
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AppSettings {
    /// Whether the backend should emit a desktop notification when a new share
    /// appears for one of the configured accounts.
    #[serde(default = "default_true")]
    pub share_notify_enabled: bool,
    /// share id (`id` field from the OCS shares listing) per account key that
    /// has already been announced, to avoid re-notifying every tick.
    #[serde(default)]
    pub share_seen: BTreeMap<String, Vec<u64>>,
}

fn default_true() -> bool {
    true
}

impl Default for AppSettings {
    fn default() -> Self {
        Self {
            share_notify_enabled: true,
            share_seen: BTreeMap::new(),
        }
    }
}

fn settings_file(app: &AppHandle) -> AppResult<PathBuf> {
    let dir = app
        .path()
        .app_data_dir()
        .map_err(|e| crate::error::AppError::App(e.to_string()))?;
    std::fs::create_dir_all(&dir)?;
    Ok(dir.join("settings.json"))
}

/// Load persisted settings; a missing file resolves to defaults so a broken
/// write never disables notifications silently. A **corrupt** file is
/// quarantined (like accounts.json / journals) instead of being silently
/// overwritten by the next persist (L24-N2).
pub fn load(app: &AppHandle) -> AppSettings {
    let path = match settings_file(app) {
        Ok(p) => p,
        Err(_) => return AppSettings::default(),
    };
    if !path.exists() {
        return AppSettings::default();
    }
    let raw = std::fs::read_to_string(&path).unwrap_or_default();
    match serde_json::from_str::<AppSettings>(&raw) {
        Ok(settings) => settings,
        Err(_) => {
            crate::persist::quarantine_corrupt_file(&path);
            AppSettings::default()
        }
    }
}

/// Atomically persist settings.
pub fn save(app: &AppHandle, settings: &AppSettings) -> AppResult<()> {
    let path = settings_file(app)?;
    let json = serde_json::to_string_pretty(settings).map_err(AppError::Json)?;
    crate::persist::atomic_write(&path, &json)
}

/// #410: check each account's shares for new ones and emit a notification for
/// every newly seen share. Called once per sync tick from `SyncEngine::run_all`.
/// Returns `true` when `settings.share_seen` changed (the caller should then
/// persist); `false` when nothing was mutated so the worker can skip the write
/// (L24-N2: avoid unconditional rewrites).
///
/// The first time an account is seen (`share_seen` has no entry yet), the
/// current share ids are **seeded silently** — otherwise a fresh install or a
/// cleared settings file would fire a notification for every pre-existing
/// share on the first tick.
pub async fn check_share_notifications(
    app: &AppHandle,
    accounts: &[Account],
    settings: &mut AppSettings,
) -> bool {
    if !settings.share_notify_enabled {
        return false;
    }
    let state = app.state::<AppState>();
    let mut changed = false;
    for account in accounts {
        let key = crate::sync::account_key(account);
        let shares =
            match crate::nextcloud::ocs::list_shares(&state.http_client, account, None, None).await
            {
                Ok(s) => s,
                Err(_) => continue, // stay silent on server errors — same pattern as quota checks
            };
        let share_ids: HashSet<u64> = shares.iter().map(|s| s.id).collect();

        match settings.share_seen.entry(key.clone()) {
            Entry::Vacant(entry) => {
                // First tick for this account: seed without notifying, so an
                // existing share library never triggers a notification storm.
                entry.insert(share_ids.iter().copied().collect());
                changed = true;
            }
            Entry::Occupied(mut entry) => {
                let seen = entry.get_mut();
                for share in &shares {
                    if share_ids.contains(&share.id) && !seen.contains(&share.id) {
                        let label = share_label(share);
                        let owner = share.uid_owner.clone().unwrap_or_default();
                        let owner = owner.trim();
                        crate::sync::notify(
                            app,
                            "FlutLink Shares",
                            &format!(
                                "{} shared '{}'.",
                                if owner.is_empty() { "Someone" } else { owner },
                                label
                            ),
                        );
                        seen.push(share.id);
                        changed = true;
                    }
                }
                // Prune ids that no longer exist. Hash-set lookup keeps this
                // O(n) instead of the previous O(n²) nested scan; only mutate
                // when there is actually something to remove (so an empty
                // listing does not wipe the seen-set and cause re-notifications).
                let before = seen.len();
                seen.retain(|id| share_ids.contains(id));
                if seen.len() != before {
                    changed = true;
                }
            }
        }
    }
    changed
}

/// Human-readable label for a share notification.
fn share_label(share: &crate::state::Share) -> String {
    if share.share_type == 3 {
        // Public link share.
        return share
            .url
            .clone()
            .unwrap_or_else(|| "public link".to_string());
    }
    share
        .path
        .clone()
        .or_else(|| share.share_with_displayname.clone())
        .or_else(|| share.share_with.clone())
        .unwrap_or_else(|| "shared item".to_string())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn default_settings_enable_share_notifications() {
        let s = AppSettings::default();
        assert!(s.share_notify_enabled);
        assert!(s.share_seen.is_empty());
    }

    #[test]
    fn parse_round_trips() {
        let json = r#"{"shareNotifyEnabled":false,"shareSeen":{"a@b":[]}}"#;
        let s: AppSettings = serde_json::from_str(json).unwrap();
        assert!(!s.share_notify_enabled);
        let back = serde_json::to_string(&s).unwrap();
        let again: AppSettings = serde_json::from_str(&back).unwrap();
        assert!(!again.share_notify_enabled);
    }
}
