//! #427: recently opened remote files ("Zuletzt geöffnete Dateien").
//!
//! A small, append-on-open journal kept in the app data directory. Entries are
//! deduplicated by remote path and capped at [`MAX_HISTORY`] entries. Writes go
//! through the shared atomic-write helper so a crash can never truncate the
//! file.

use std::path::{Path, PathBuf};

use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Manager};

use crate::error::{AppError, AppResult};

/// A recently opened remote file, newest first.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct FileHistoryEntry {
    /// Absolute remote path of the file.
    pub path: String,
    /// Base file name shown in the UI.
    pub name: String,
    /// Unix seconds when the file was last opened.
    pub opened_at: i64,
}

/// Maximum number of entries kept (newer entries push older ones out).
pub const MAX_HISTORY: usize = 20;

/// Serialises `record_open` and `clear` so an in-flight atomic write (temp+
/// rename) can never resurrect the journal right after it was cleared
/// (L24-N4). Both operations are short and synchronous, so a plain mutex is
/// enough.
static HISTORY_LOCK: std::sync::Mutex<()> = std::sync::Mutex::new(());

fn history_file(app: &AppHandle) -> AppResult<PathBuf> {
    let dir = app
        .path()
        .app_data_dir()
        .map_err(|e| AppError::App(e.to_string()))?;
    std::fs::create_dir_all(&dir)?;
    Ok(dir.join("file-history.json"))
}

fn now_secs() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_secs() as i64)
        .unwrap_or(0)
}

/// Read the persisted history. Missing or corrupt files yield an empty list —
/// a corrupt journal never blocks opening files.
pub fn load(app: &AppHandle) -> AppResult<Vec<FileHistoryEntry>> {
    let path = history_file(app)?;
    if !path.exists() {
        return Ok(Vec::new());
    }
    let raw = std::fs::read_to_string(&path)?;
    let entries = serde_json::from_str::<Vec<FileHistoryEntry>>(&raw).unwrap_or_default();
    Ok(entries)
}

/// Remember that `remote_path` was opened (best-effort, never blocks the open
/// itself). Moves an existing entry for the same path to the top.
pub fn record_open(app: &AppHandle, remote_path: &str) {
    let Ok(_guard) = HISTORY_LOCK.lock() else {
        return;
    };
    let Some(name) = Path::new(remote_path)
        .file_name()
        .and_then(|n| n.to_str())
        .filter(|n| !n.is_empty())
    else {
        return;
    };
    let mut entries = load(app).unwrap_or_default();
    entries.retain(|e| e.path != remote_path);
    entries.insert(
        0,
        FileHistoryEntry {
            path: remote_path.to_string(),
            name: name.to_string(),
            opened_at: now_secs(),
        },
    );
    entries.truncate(MAX_HISTORY);
    if let Ok(path) = history_file(app) {
        if let Ok(json) = serde_json::to_string_pretty(&entries) {
            let _ = crate::persist::atomic_write(&path, &json);
        }
    }
}

/// Delete the whole history. Also removes any leftover atomic-write temp files
/// (`.tmp-*`), so a stale temp can never be renamed over the (now absent)
/// journal later (L24-N4).
pub fn clear(app: &AppHandle) -> AppResult<()> {
    let Ok(_guard) = HISTORY_LOCK.lock() else {
        return Ok(());
    };
    let path = history_file(app)?;
    if let Some(dir) = path.parent() {
        let name = path.file_name().map(|n| n.to_string_lossy().into_owned());
        if let Ok(read) = std::fs::read_dir(dir) {
            for entry in read.flatten() {
                let fname = entry.file_name();
                let Some(fname) = fname.to_str() else {
                    continue;
                };
                let Some(base) = &name else { continue };
                if fname == *base || fname.starts_with(&format!("{base}.tmp-")) {
                    let _ = std::fs::remove_file(entry.path());
                }
            }
        }
    }
    if path.exists() {
        std::fs::remove_file(path)?;
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn keeps_at_most_max_entries() {
        // parse-only checks avoid touching the fs
        let ser = |n: usize| {
            let mut entries: Vec<FileHistoryEntry> = Vec::new();
            for i in 0..n {
                entries.push(FileHistoryEntry {
                    path: format!("/f{i}.txt"),
                    name: format!("f{i}.txt"),
                    opened_at: i as i64,
                });
            }
            entries.truncate(MAX_HISTORY);
            serde_json::to_string(&entries).unwrap()
        };
        let parsed: Vec<FileHistoryEntry> = serde_json::from_str(&ser(50)).unwrap();
        assert_eq!(parsed.len(), MAX_HISTORY);
        assert_eq!(parsed[0].name, "f0.txt");
    }
}
