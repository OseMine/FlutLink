//! Crash-safe persistence helpers shared by the config and cache writers
//! (L17-F3): `accounts.json`, `sync-folders.json` and the offline cache must
//! never be left truncated by a crash mid-write, and corrupt leftovers must
//! be quarantined instead of being silently overwritten by the next persist.

use crate::error::AppResult;
use std::io::Write;
use std::path::{Path, PathBuf};
use std::time::SystemTime;

/// Atomically write `json` to `path`: temp file + fsync + rename. The rename
/// is atomic on all supported platforms, so a crash can never leave a
/// half-written target file behind (same pattern as the sync journals).
pub fn atomic_write(path: &Path, json: &str) -> AppResult<()> {
    let tmp = path.with_extension(format!("tmp-{}", std::process::id()));
    {
        let mut file = std::fs::File::create(&tmp)?;
        file.write_all(json.as_bytes())?;
        file.sync_all()?;
    }
    std::fs::rename(&tmp, path)?;
    Ok(())
}

/// Rename a corrupt/unreadable file out of the way as
/// `<original name>.corrupt-<unix time>` so it stays available for diagnosis
/// while the caller starts over with defaults — the next persist then writes
/// a fresh file instead of silently destroying the evidence.
pub fn quarantine_corrupt_file(path: &Path) {
    let unix = SystemTime::now()
        .duration_since(SystemTime::UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0);
    let mut quarantined = path.as_os_str().to_os_string();
    quarantined.push(format!(".corrupt-{unix}"));
    let _ = std::fs::rename(path, PathBuf::from(quarantined));
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn atomic_write_replaces_content_and_leaves_no_temp_files() {
        let dir =
            std::env::temp_dir().join(format!("flutlink-persist-test-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).expect("create temp dir");

        let path = dir.join("config.json");
        atomic_write(&path, "{\"v\":1}").expect("write");
        atomic_write(&path, "{\"v\":2}").expect("rewrite");

        let count = std::fs::read_dir(&dir)
            .expect("read dir")
            .filter_map(Result::ok)
            .count();
        assert_eq!(count, 1, "only the final file remains");
        assert_eq!(std::fs::read_to_string(&path).unwrap(), "{\"v\":2}");

        std::fs::remove_dir_all(&dir).expect("clean up temp dir");
    }

    #[test]
    fn quarantine_renames_the_file_with_a_suffix() {
        let dir =
            std::env::temp_dir().join(format!("flutlink-persist-test2-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(&dir).expect("create temp dir");

        let path = dir.join("accounts.json");
        std::fs::write(&path, "{broken").expect("seed corrupt file");
        quarantine_corrupt_file(&path);

        assert!(!path.exists(), "the original name is free again");
        let leftovers: Vec<_> = std::fs::read_dir(&dir)
            .expect("read dir")
            .filter_map(Result::ok)
            .map(|e| e.file_name().to_string_lossy().into_owned())
            .collect();
        assert_eq!(leftovers.len(), 1);
        assert!(
            leftovers[0].starts_with("accounts.json.corrupt-"),
            "quarantined copy keeps a diagnostic name: {}",
            leftovers[0]
        );

        std::fs::remove_dir_all(&dir).expect("clean up temp dir");
    }
}
