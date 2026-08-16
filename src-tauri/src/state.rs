use std::sync::{Arc, RwLock};

use reqwest::Client;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AccountMeta {
    pub username: String,
    pub instance_url: String,
    pub display_name: Option<String>,
    pub is_admin: bool,
    pub is_active: bool,
}

#[derive(Debug, Clone)]
pub struct Account {
    pub meta: AccountMeta,
    pub token: String,
}

impl Account {
    pub fn base_url(&self) -> String {
        self.meta.instance_url.trim_end_matches('/').to_string()
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct WebDavEntry {
    pub name: String,
    /// Decoded logical path relative to the user's files root, e.g. "/Photos/2024".
    pub path: String,
    pub is_dir: bool,
    pub size: Option<u64>,
    pub mtime: Option<String>,
    pub etag: Option<String>,
    pub content_type: Option<String>,
    /// True when this entry lives under a folder named `resources` (read-only virtual links).
    pub is_resource: bool,
    /// True when this entry lives under a folder named `parts` (write-enabled).
    pub is_part: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct OcsUser {
    pub id: String,
    pub display_name: Option<String>,
    pub is_admin: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct UserQuota {
    pub total: Option<u64>,
    pub used: Option<u64>,
    pub free: Option<u64>,
    pub relative: Option<f64>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct UserDetails {
    pub id: String,
    pub display_name: Option<String>,
    pub email: Option<String>,
    pub quota: Option<UserQuota>,
    pub groups: Vec<String>,
    pub enabled: bool,
}

/// A share (public link or user/group share) as returned by the OCS files
/// sharing API (`/apps/files_sharing/api/v1/shares`).
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Share {
    pub id: u64,
    /// OCS shareType: 0 = user, 1 = group, 3 = public link.
    pub share_type: u32,
    pub path: Option<String>,
    /// Username/group name for user/group shares; empty for links.
    pub share_with: Option<String>,
    pub share_with_displayname: Option<String>,
    pub permissions: Option<u32>,
    pub url: Option<String>,
    pub has_password: Option<bool>,
    pub expiration: Option<String>,
}

/// A persisted bidirectional sync between a local folder and a cloud folder.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SyncFolder {
    pub id: String,
    pub account_key: String,
    pub local_path: String,
    pub remote_path: String,
    pub paused: bool,
}

/// Progress of a file transfer (upload/download) or a bulk operation (delete),
/// emitted to the frontend via the `file://progress` event channel.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TransferProgress {
    /// "upload" | "download" | "delete"
    pub direction: String,
    /// Remote path currently being transferred.
    pub path: String,
    /// Zero-based index of the current file within the operation.
    pub index: u64,
    /// Total number of files in the operation.
    pub total_files: u64,
    /// Bytes (or files, for delete) done for the current unit.
    pub transferred: u64,
    /// Total bytes (or files) of the current unit.
    pub total: u64,
    /// 0.0 .. 100.0
    pub percent: f64,
}

/// Combined folder + live status payload sent to the frontend.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SyncFolderStatus {
    pub folder_id: String,
    pub account_key: String,
    pub local_path: String,
    pub remote_path: String,
    pub paused: bool,
    /// "idle" | "syncing" | "paused" | "error"
    pub state: String,
    pub pending_uploads: u64,
    pub pending_downloads: u64,
    pub pending_deletes: u64,
    pub failures: u64,
    pub last_error: Option<crate::sync::PassError>,
    /// Unix seconds of the last completed sync pass.
    pub last_synced_at: Option<i64>,
}

pub struct AppState {
    pub http_client: Client,
    pub accounts: RwLock<Vec<Account>>,
    pub sync: Arc<crate::sync::SyncEngine>,
    /// Instance URLs of persisted accounts hidden by `load_accounts` because
    /// they point to a different server than the configured FlutCloud server.
    pub filtered_accounts: RwLock<Vec<String>>,
}

impl AppState {
    pub fn new() -> Self {
        let http_client = Client::builder()
            // No total timeout: large WebDAV transfers (uploads/downloads, sync)
            // legitimately take longer than 60 s. Only the connect phase and each
            // single read are bounded, so a slow-but-progressing transfer never
            // aborts while a stalled connection is still detected.
            .connect_timeout(std::time::Duration::from_secs(30))
            .read_timeout(std::time::Duration::from_secs(60))
            .user_agent(concat!("FlutLink/", env!("CARGO_PKG_VERSION")))
            .build()
            .expect("failed to build HTTP client");
        Self {
            http_client,
            accounts: RwLock::new(Vec::new()),
            sync: Arc::new(crate::sync::SyncEngine::default()),
            filtered_accounts: RwLock::new(Vec::new()),
        }
    }

    pub fn set_accounts(&self, accounts: Vec<Account>) {
        if let Ok(mut guard) = self.accounts.write() {
            *guard = accounts;
        }
    }

    pub fn set_filtered_accounts(&self, urls: Vec<String>) {
        if let Ok(mut guard) = self.filtered_accounts.write() {
            *guard = urls;
        }
    }

    pub fn filtered_accounts(&self) -> Vec<String> {
        self.filtered_accounts
            .read()
            .map(|guard| guard.clone())
            .unwrap_or_default()
    }

    pub fn accounts_snapshot(&self) -> Vec<Account> {
        self.accounts
            .read()
            .map(|guard| guard.clone())
            .unwrap_or_default()
    }

    /// The currently active account, falling back to the first one if none is flagged.
    pub fn current(&self) -> Option<Account> {
        let guard = self.accounts.read().ok()?;
        guard
            .iter()
            .find(|a| a.meta.is_active)
            .or_else(|| guard.first())
            .cloned()
    }

    /// Insert or replace an account. Returns the updated list.
    pub fn upsert(&self, account: Account) -> Vec<Account> {
        let mut guard = self.accounts.write().expect("accounts lock poisoned");
        guard.retain(|a| {
            !(a.meta.instance_url == account.meta.instance_url
                && a.meta.username == account.meta.username)
        });
        guard.push(account);
        guard.clone()
    }

    /// Remove an account by username + instance URL. Returns the updated list.
    pub fn remove(&self, username: &str, instance_url: &str) -> Vec<Account> {
        let mut guard = self.accounts.write().expect("accounts lock poisoned");
        guard.retain(|a| !(a.meta.username == username && a.meta.instance_url == instance_url));
        if guard.iter().all(|a| !a.meta.is_active) {
            if let Some(first) = guard.first_mut() {
                first.meta.is_active = true;
            }
        }
        guard.clone()
    }

    /// Mark one account active (identified by username + instance URL).
    /// Returns the updated account if it exists.
    pub fn set_active(&self, username: &str, instance_url: &str) -> Option<Account> {
        let mut guard = self.accounts.write().expect("accounts lock poisoned");
        for account in guard.iter_mut() {
            account.meta.is_active =
                account.meta.username == username && account.meta.instance_url == instance_url;
        }
        guard
            .iter()
            .find(|a| a.meta.username == username && a.meta.instance_url == instance_url)
            .cloned()
    }
}
