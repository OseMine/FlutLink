use crate::error::{AppError, AppResult};
use crate::state::{UserQuota, WebDavEntry};
use sha2::{Digest, Sha256};
use std::fmt::Write as _;
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
/// user) and hashed with SHA-256 into a safe file name (#286): the digest is
/// stable across app restarts and Rust versions (unlike `DefaultHasher`,
/// whose algorithm may change) and cannot collide across different inputs.
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
    let mut hasher = Sha256::new();
    hasher.update(namespace.as_bytes());
    hasher.update([0]);
    hasher.update(key.as_bytes());
    let mut hex = String::with_capacity(64);
    for byte in hasher.finalize() {
        let _ = write!(hex, "{byte:02x}");
    }
    format!("{}_{hex}.json", scope)
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

/// Atomically write cache content: temp file + rename, so a crash mid-write
/// can never leave a truncated JSON file behind (#286).
fn atomic_write(path: &Path, json: &str) -> AppResult<()> {
    let tmp = path.with_extension(format!("tmp-{}", std::process::id()));
    std::fs::write(&tmp, json)?;
    std::fs::rename(&tmp, path)?;
    Ok(())
}

/// Load a cached JSON document. A corrupt or unreadable file is deleted and
/// reported as a miss instead of failing the request (#286) — the cache is a
/// best-effort offline fallback, never a source of hard errors.
fn load_json<T: serde::de::DeserializeOwned>(path: &Path) -> AppResult<Option<T>> {
    if !path.exists() {
        return Ok(None);
    }
    let parsed = std::fs::read_to_string(path)
        .map_err(|e| AppError::Parse(e.to_string()))
        .and_then(|raw| serde_json::from_str(&raw).map_err(|e| AppError::Parse(e.to_string())));
    match parsed {
        Ok(value) => Ok(Some(value)),
        Err(_) => {
            let _ = std::fs::remove_file(path);
            Ok(None)
        }
    }
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
    atomic_write(
        &dir.join(file_name("listing", namespace, cache_key(path))),
        &json,
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
    load_json(&cache_dir(app)?.join(file_name("listing", namespace, cache_key(path))))
}

/// Persist the storage quota of the active account.
pub fn save_quota(app: &AppHandle, namespace: &str, quota: &UserQuota) -> AppResult<()> {
    let dir = cache_dir(app)?;
    let json = serde_json::to_string(quota).map_err(|e| AppError::Parse(e.to_string()))?;
    atomic_write(&dir.join(file_name("quota", namespace, "/")), &json)?;
    evict_oldest(&dir, MAX_CACHE_ENTRIES);
    Ok(())
}

/// Load the cached storage quota, or `None` when nothing was cached yet.
pub fn load_quota(app: &AppHandle, namespace: &str) -> AppResult<Option<UserQuota>> {
    load_json(&cache_dir(app)?.join(file_name("quota", namespace, "/")))
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

    #[test]
    fn cache_file_names_are_stable_and_collision_free() {
        let a1 = file_name("listing", "user@host|alice", "/Documents");
        let a2 = file_name("listing", "user@host|alice", "/Documents");
        assert_eq!(a1, a2, "the same key must map to the same file name");

        let b = file_name("listing", "user@host|alice", "/Documents ");
        assert_ne!(a1, b, "different keys must not collide");

        let c = file_name("listing", "user@host|bob", "/Documents");
        assert_ne!(a1, c, "different namespaces must not collide");

        let d = file_name("quota", "user@host|alice", "/");
        assert_ne!(a1, d, "different scopes must not collide");
    }

    #[test]
    fn corrupt_cache_file_is_deleted_and_reports_a_miss() {
        let dir = std::env::temp_dir().join(format!("flutlink-cache-test3-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).expect("create temp cache dir");

        let path = dir.join(file_name("listing", "ns", "/"));
        std::fs::write(&path, "{\"truncated\":").expect("write broken json");

        let loaded: Option<Vec<WebDavEntry>> = load_json(&path).expect("no hard error");
        assert!(loaded.is_none(), "corrupt content is reported as a miss");
        assert!(!path.exists(), "the corrupt file is removed");

        std::fs::remove_dir_all(&dir).expect("clean up temp cache dir");
    }

    #[test]
    fn atomic_write_leaves_no_temp_files_behind() {
        let dir = std::env::temp_dir().join(format!("flutlink-cache-test4-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).expect("create temp cache dir");

        let path = dir.join(file_name("quota", "ns", "/"));
        atomic_write(&path, "{\"used\":1}").expect("atomic write");
        atomic_write(&path, "{\"used\":2}").expect("rewrite");

        let count = std::fs::read_dir(&dir)
            .expect("read cache dir")
            .filter_map(Result::ok)
            .count();
        assert_eq!(count, 1, "only the final file remains");

        let raw = std::fs::read_to_string(&path).unwrap();
        assert_eq!(raw, "{\"used\":2}");

        std::fs::remove_dir_all(&dir).expect("clean up temp cache dir");
    }
}
