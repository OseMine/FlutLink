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
    pub last_error: Option<String>,
    /// Unix seconds of the last completed sync pass.
    pub last_synced_at: Option<i64>,
}

pub struct AppState {
    pub http_client: Client,
    pub accounts: RwLock<Vec<Account>>,
    pub sync: Arc<crate::sync::SyncEngine>,
}

impl AppState {
    pub fn new() -> Self {
        let http_client = Client::builder()
            .timeout(std::time::Duration::from_secs(60))
            .user_agent(concat!("FlutLink/", env!("CARGO_PKG_VERSION")))
            .build()
            .expect("failed to build HTTP client");
        Self {
            http_client,
            accounts: RwLock::new(Vec::new()),
            sync: Arc::new(crate::sync::SyncEngine::default()),
        }
    }

    pub fn set_accounts(&self, accounts: Vec<Account>) {
        if let Ok(mut guard) = self.accounts.write() {
            *guard = accounts;
        }
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

    /// Remove an account by username. Returns the updated list.
    pub fn remove(&self, username: &str) -> Vec<Account> {
        let mut guard = self.accounts.write().expect("accounts lock poisoned");
        guard.retain(|a| a.meta.username != username);
        if guard.iter().all(|a| !a.meta.is_active) {
            if let Some(first) = guard.first_mut() {
                first.meta.is_active = true;
            }
        }
        guard.clone()
    }

    /// Mark one account active. Returns the updated account if it exists.
    pub fn set_active(&self, username: &str) -> Option<Account> {
        let mut guard = self.accounts.write().expect("accounts lock poisoned");
        for account in guard.iter_mut() {
            account.meta.is_active = account.meta.username == username;
        }
        guard.iter().find(|a| a.meta.username == username).cloned()
    }
}
