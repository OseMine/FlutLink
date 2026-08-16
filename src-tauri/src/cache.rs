use crate::error::{AppError, AppResult};
use crate::state::{UserQuota, WebDavEntry};
use std::collections::hash_map::DefaultHasher;
use std::hash::{Hash, Hasher};
use std::path::PathBuf;
use tauri::{AppHandle, Manager};

/// Offline cache for the file browser: folder listings and the storage quota
/// are persisted in the app data directory so the browser keeps working when
/// the server is unreachable.
///
/// Cache keys are namespaced by the account (and, for listings, the browsed
/// user) and hashed into a safe file name so arbitrary paths or user names can
/// never escape the cache directory.
fn cache_dir(app: &AppHandle) -> AppResult<PathBuf> {
    let dir = app
        .path()
        .app_data_dir()
        .map_err(|e| AppError::App(e.to_string()))?;
    let dir = dir.join("cache");
    std::fs::create_dir_all(&dir)?;
    Ok(dir)
}

fn file_name(scope: &str, namespace: &str, key: &str) -> String {
    let mut hasher = DefaultHasher::new();
    (namespace, key).hash(&mut hasher);
    format!("{}_{:016x}.json", scope, hasher.finish())
}

fn cache_key(path: &str) -> &str {
    if path.is_empty() {
        "/"
    } else {
        path
    }
}

/// Persist a folder listing for `namespace` (account + browsed user).
pub fn save_listing(
    app: &AppHandle,
    namespace: &str,
    path: &str,
    entries: &[WebDavEntry],
) -> AppResult<()> {
    let json = serde_json::to_string(entries).map_err(|e| AppError::Parse(e.to_string()))?;
    std::fs::write(
        cache_dir(app)?.join(file_name("listing", namespace, cache_key(path))),
        json,
    )?;
    Ok(())
}

/// Load a cached folder listing, or `None` when nothing was cached yet.
pub fn load_listing(
    app: &AppHandle,
    namespace: &str,
    path: &str,
) -> AppResult<Option<Vec<WebDavEntry>>> {
    let file = cache_dir(app)?.join(file_name("listing", namespace, cache_key(path)));
    if !file.exists() {
        return Ok(None);
    }
    let raw = std::fs::read_to_string(&file)?;
    let entries: Vec<WebDavEntry> =
        serde_json::from_str(&raw).map_err(|e| AppError::Parse(e.to_string()))?;
    Ok(Some(entries))
}

/// Persist the storage quota of the active account.
pub fn save_quota(app: &AppHandle, namespace: &str, quota: &UserQuota) -> AppResult<()> {
    let json = serde_json::to_string(quota).map_err(|e| AppError::Parse(e.to_string()))?;
    std::fs::write(
        cache_dir(app)?.join(file_name("quota", namespace, "/")),
        json,
    )?;
    Ok(())
}

/// Load the cached storage quota, or `None` when nothing was cached yet.
pub fn load_quota(app: &AppHandle, namespace: &str) -> AppResult<Option<UserQuota>> {
    let file = cache_dir(app)?.join(file_name("quota", namespace, "/"));
    if !file.exists() {
        return Ok(None);
    }
    let raw = std::fs::read_to_string(&file)?;
    let quota: UserQuota =
        serde_json::from_str(&raw).map_err(|e| AppError::Parse(e.to_string()))?;
    Ok(Some(quota))
}
