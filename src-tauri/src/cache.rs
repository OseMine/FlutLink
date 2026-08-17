use crate::error::{AppError, AppResult};
use crate::state::{UserQuota, WebDavEntry};
use std::collections::hash_map::DefaultHasher;
use std::hash::{Hash, Hasher};
use std::path::{Path, PathBuf};
use std::time::SystemTime;
use tauri::{AppHandle, Manager};

/// Maximum number of cache files kept on disk. When a write pushes the cache
/// beyond this limit the oldest entries are evicted so the app data directory
/// does not grow unbounded while browsing many folders.
pub const MAX_CACHE_ENTRIES: usize = 500;

/// Offline cache for the file browser: folder listings and the storage quota
/// are persisted in the app data directory so the browser keeps working when
/// the server is unreachable.
///
/// Cache keys are namespaced by the account (and, for listings, the browsed
/// user) and hashed into a safe file name so arbitrary paths or user names can
/// never escape the cache directory. Every write runs the aging eviction (see
/// [`evict_oldest`]) to cap the number of cached entries.
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

/// Remove the oldest cache files so the directory holds at most `max_entries`
/// files. Recency is tracked via the file modification time, which is
/// refreshed on every successful write, so the least recently written entries
/// are evicted first. Returns the number of removed files.
fn evict_oldest(dir: &Path, max_entries: usize) -> usize {
    let Ok(entries) = std::fs::read_dir(dir) else {
        return 0;
    };
    let mut files: Vec<(SystemTime, PathBuf)> = Vec::new();
    for entry in entries.flatten() {
        let Ok(meta) = entry.metadata() else {
            continue;
        };
        if meta.is_file() {
            files.push((
                meta.modified().unwrap_or(SystemTime::UNIX_EPOCH),
                entry.path(),
            ));
        }
    }
    if files.len() <= max_entries {
        return 0;
    }
    files.sort_by_key(|(mtime, _)| *mtime);
    let remove = files.len() - max_entries;
    for (_, path) in files.into_iter().take(remove) {
        let _ = std::fs::remove_file(path);
    }
    remove
}

/// Persist a folder listing for `namespace` (account + browsed user).
pub fn save_listing(
    app: &AppHandle,
    namespace: &str,
    path: &str,
    entries: &[WebDavEntry],
) -> AppResult<()> {
    let dir = cache_dir(app)?;
    let json = serde_json::to_string(entries).map_err(|e| AppError::Parse(e.to_string()))?;
    std::fs::write(
        dir.join(file_name("listing", namespace, cache_key(path))),
        json,
    )?;
    evict_oldest(&dir, MAX_CACHE_ENTRIES);
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
    let dir = cache_dir(app)?;
    let json = serde_json::to_string(quota).map_err(|e| AppError::Parse(e.to_string()))?;
    std::fs::write(dir.join(file_name("quota", namespace, "/")), json)?;
    evict_oldest(&dir, MAX_CACHE_ENTRIES);
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

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs::File;
    use std::time::Duration;

    fn touch(path: &Path, mtime: SystemTime) {
        File::options()
            .write(true)
            .open(path)
            .expect("open cache file for touching")
            .set_modified(mtime)
            .expect("set cache file mtime");
    }

    #[test]
    fn evict_keeps_cache_at_or_under_the_limit() {
        let dir = std::env::temp_dir().join(format!("flutlink-cache-test-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).expect("create temp cache dir");

        let base = SystemTime::UNIX_EPOCH + Duration::from_secs(1_700_000_000);
        let limit = 10;
        for i in 0..50 {
            let file = dir.join(format!("entry-{i}.json"));
            std::fs::write(&file, format!("{i}")).expect("write cache file");
            touch(&file, base + Duration::from_secs(i));
        }

        let removed = evict_oldest(&dir, limit);

        let remaining: Vec<_> = std::fs::read_dir(&dir)
            .expect("read cache dir")
            .filter_map(Result::ok)
            .filter(|e| e.file_type().map(|t| t.is_file()).unwrap_or(false))
            .collect();
        assert_eq!(removed, 40);
        assert_eq!(remaining.len(), limit, "cache stays under the limit");
        for file in remaining {
            let name = file.file_name().into_string().expect("utf-8 name");
            let idx: u64 = name
                .trim_start_matches("entry-")
                .trim_end_matches(".json")
                .parse()
                .expect("parse index");
            assert!(
                idx >= 40,
                "oldest entries are evicted first, kept entry-{idx}"
            );
        }

        std::fs::remove_dir_all(&dir).expect("clean up temp cache dir");
    }

    #[test]
    fn evict_is_a_noop_below_the_limit() {
        let dir = std::env::temp_dir().join(format!("flutlink-cache-test2-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).expect("create temp cache dir");

        for i in 0..3 {
            std::fs::write(dir.join(format!("entry-{i}.json")), format!("{i}"))
                .expect("write cache file");
        }

        assert_eq!(evict_oldest(&dir, 10), 0);
        let count = std::fs::read_dir(&dir)
            .expect("read cache dir")
            .filter_map(Result::ok)
            .count();
        assert_eq!(count, 3, "nothing is evicted below the limit");

        std::fs::remove_dir_all(&dir).expect("clean up temp cache dir");
    }
}
