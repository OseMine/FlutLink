//! #410: app-wide settings persisted in the app-data directory.
//!
//! Currently this holds the "share notifications enabled" toggle and the set of
//! share ids already notified per account. Kept as a tiny standalone file
//! (rather than part of the account config) so the sync worker can read/write
//! it in place without going through the account persistence round-trip.

use std::collections::BTreeMap;
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

/// Load persisted settings; a missing/corrupt file resolves to defaults so a
/// broken write never disables notifications silently.
pub fn load(app: &AppHandle) -> AppSettings {
    let path = match settings_file(app) {
        Ok(p) => p,
        Err(_) => return AppSettings::default(),
    };
    if !path.exists() {
        return AppSettings::default();
    }
    let raw = std::fs::read_to_string(&path).unwrap_or_default();
    serde_json::from_str::<AppSettings>(&raw).unwrap_or_default()
}

/// Atomically persist settings.
pub fn save(app: &AppHandle, settings: &AppSettings) -> AppResult<()> {
    let path = settings_file(app)?;
    let json = serde_json::to_string_pretty(settings).map_err(AppError::Json)?;
    crate::persist::atomic_write(&path, &json)
}

/// #410: check each account's shares for new ones and emit a notification for
/// every newly seen share. Called once per sync tick from `SyncEngine::run_all`.
pub async fn check_share_notifications(
    app: &AppHandle,
    accounts: &[Account],
    settings: &mut AppSettings,
) {
    if !settings.share_notify_enabled {
        return;
    }
    let state = app.state::<AppState>();
    for account in accounts {
        let key = crate::sync::account_key(account);
        let seen = settings.share_seen.entry(key.clone()).or_default();
        let shares =
            match crate::nextcloud::ocs::list_shares(&state.http_client, account, None, None).await
            {
                Ok(s) => s,
                Err(_) => continue, // stay silent on server errors — same pattern as quota checks
            };
        for share in &shares {
            if seen.contains(&share.id) {
                continue;
            }
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
        }
        seen.retain(|id| shares.iter().any(|s| s.id == *id));
    }
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
