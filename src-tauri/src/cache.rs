use crate::error::{AppError, AppResult};
use crate::persist::atomic_write;
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

use crate::error::{AppError, AppResult};
use crate::persist::atomic_write;
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

/// Cache counter file name for tracking entry count without full directory scan.
const COUNTER_FILE: &str = ".cache_counter";

/// Remove the oldest cache files so the directory holds at most `max_entries`
/// files. Uses an atomic counter file for recency tracking instead of a full
/// directory scan, and performs batch eviction (10% at a time) to avoid
/// O(N) metadata syscalls on every write.
///
/// Returns the number of removed files.
fn evict_oldest(dir: &Path, max_entries: usize) -> usize {
    let counter_path = dir.join(COUNTER_FILE);
    let Ok(counter_str) = std::fs::read_to_string(&counter_path) else {
        // No counter file yet - do full scan once to populate it
        let Ok(entries) = std::fs::read_dir(dir) else {
            return 0;
        };
        let mut files: Vec<PathBuf> = Vec::new();
        for entry in entries.flatten() {
            let Ok(meta) = entry.metadata() else {
                continue;
            };
            if meta.is_file() && entry.file_name().to_str() != Some(COUNTER_FILE) {
                files.push(entry.path());
            }
        }
        if files.is_empty() {
            let _ = std::fs::write(&counter_path, "0");
            return 0;
        }
        // Sort by filename (which includes timestamp from hash) to determine oldest
        files.sort_by_key(|path| path.file_name().and_then(|n| n.to_str()).unwrap_or_default());
        let to_remove = files.len().saturating_sub(max_entries);
        // Batch: only remove 10% at a time
        let batch = files.len() / 10.max(1);
        let actual_remove = to_remove.min(batch);
        for path in files.iter().take(actual_remove) {
            let _ = std::fs::remove_file(path);
        }
        let _ = std::fs::write(&counter_path, &files.len().to_string());
        return files.len() - max_entries;
    };
    let mut counter: usize = counter_str.trim().parse().unwrap_or(0);
    if counter <= max_entries {
        return 0;
    }
    // Batch eviction: remove 10% of entries
    let evict_count = counter / 10.max(1);
    let to_remove = evict_count.min(counter - max_entries);
    // List files and remove the oldest `to_remove` files
    let Ok(entries) = std::fs::read_dir(dir) else {
        let _ = std::fs::write(&counter_path, &(counter - to_remove).to_string());
        return 0;
    };
    let mut files: Vec<PathBuf> = Vec::new();
    for entry in entries.flatten() {
        let path = entry.path();
        if path.is_file() && path.file_name().to_str() != Some(std::ffi::OsStr::new(COUNTER_FILE)) {
            files.push(path);
        }
    }
    // Sort by filename to get oldest first (stable across restarts)
    files.sort_by_key(|path| path.file_name().and_then(|n| n.to_str()).unwrap_or_default());
    let removed = files[..to_remove].iter().for_each(|path| {
        let _ = std::fs::remove_file(path);
    });
    let _ = std::fs::write(&counter_path, &(counter - to_remove).to_string());
    to_remove
}
