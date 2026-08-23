use crate::error::{AppError, AppResult};
use crate::state::{Account, AccountMeta};
use tauri::{AppHandle, Manager};

fn accounts_file(app: &AppHandle) -> AppResult<std::path::PathBuf> {
    let dir = app
        .path()
        .app_data_dir()
        .map_err(|e| AppError::App(e.to_string()))?;
    std::fs::create_dir_all(&dir)?;
    Ok(dir.join("accounts.json"))
}

fn keyring_user(meta: &AccountMeta) -> String {
    format!("{}@{}", meta.username, meta.instance_url)
}

/// Map a keyring failure to a user-actionable error. On Linux the Secret
/// Service is frequently the problem (no gnome-keyring/kwallet running), so
/// the message includes a hint instead of leaving the user with a bare
/// platform error.
fn map_keyring_error(e: keyring::Error, action: &str) -> AppError {
    #[cfg(target_os = "linux")]
    let hint = {
        if matches!(
            &e,
            keyring::Error::NoStorageAccess(_) | keyring::Error::PlatformFailure(_)
        ) {
            " On Linux this usually means the Secret Service is unavailable \
             (is gnome-keyring or KWallet running and unlocked?)."
        } else {
            ""
        }
    };
    #[cfg(not(target_os = "linux"))]
    let hint = "";
    AppError::Keyring(format!("{}: {}{}", action, e, hint))
}

/// Store the app token in the OS credential store (Windows Credential Manager,
/// macOS Keychain, Linux Secret Service).
pub fn save_token(meta: &AccountMeta, token: &str) -> AppResult<()> {
    let entry = keyring::Entry::new("flutlink", &keyring_user(meta))
        .map_err(|e| map_keyring_error(e, "could not open the credential store"))?;
    entry
        .set_password(token)
        .map_err(|e| map_keyring_error(e, "could not save the token"))
}

pub fn load_token(meta: &AccountMeta) -> AppResult<String> {
    let entry = keyring::Entry::new("flutlink", &keyring_user(meta))
        .map_err(|e| map_keyring_error(e, "could not open the credential store"))?;
    entry
        .get_password()
        .map_err(|e| map_keyring_error(e, "could not load the token"))
}

pub fn delete_token(meta: &AccountMeta) -> AppResult<()> {
    let entry = keyring::Entry::new("flutlink", &keyring_user(meta))
        .map_err(|e| map_keyring_error(e, "could not open the credential store"))?;
    entry
        .delete_credential()
        .map_err(|e| map_keyring_error(e, "could not delete the token"))
}

/// Persist account metadata (never the token) to the app data directory.
pub fn persist_accounts(app: &AppHandle, accounts: &[Account]) -> AppResult<()> {
    let metas: Vec<AccountMeta> = accounts.iter().map(|a| a.meta.clone()).collect();
    let json = serde_json::to_string_pretty(&metas).map_err(|e| AppError::Parse(e.to_string()))?;
    std::fs::write(accounts_file(app)?, json)?;
    Ok(())
}

/// Result of [`load_accounts`].
#[derive(Debug, Default)]
pub struct LoadAccountsResult {
    pub accounts: Vec<Account>,
    /// Instance URLs of persisted accounts that were NOT loaded because they
    /// point to a different server than the configured FlutCloud server (or
    /// because `FLUTCLOUD_URL` is not set at all). The frontend shows a hint.
    pub dropped: Vec<String>,
    /// `user@instance_url` identifiers of persisted accounts whose keyring
    /// token could not be read. They are skipped, but reported so the UI can
    /// explain the disappearance instead of the account silently vanishing.
    pub token_missing: Vec<String>,
}

/// Load account metadata from disk, restoring tokens from the OS keychain.
/// Accounts whose token is missing are skipped — and reported through
/// [`LoadAccountsResult::token_missing`] so they never disappear silently.
///
/// FlutLink is a dedicated client for the FlutCloud server only, so any
/// account persisted against a different instance is dropped — but the reason
/// is reported through [`LoadAccountsResult::dropped`] instead of being
/// silently swallowed.
pub fn load_accounts(app: &AppHandle) -> AppResult<LoadAccountsResult> {
    let path = accounts_file(app)?;
    if !path.exists() {
        return Ok(LoadAccountsResult::default());
    }
    let raw = std::fs::read_to_string(&path)?;
    let metas: Vec<AccountMeta> =
        serde_json::from_str(&raw).map_err(|e| AppError::Parse(e.to_string()))?;
    let mut dropped = Vec::new();
    let mut token_missing = Vec::new();
    let mut accounts = Vec::new();
    for meta in metas {
        if crate::flutcloud::assert_flutcloud_url(&meta.instance_url).is_err() {
            dropped.push(meta.instance_url);
            continue;
        }
        match load_token(&meta) {
            Ok(token) => accounts.push(Account { meta, token }),
            Err(_) => token_missing.push(keyring_user(&meta)),
        }
    }
    Ok(LoadAccountsResult {
        accounts,
        dropped,
        token_missing,
    })
}
