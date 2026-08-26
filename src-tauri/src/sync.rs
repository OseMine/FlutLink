use std::collections::{BTreeMap, BTreeSet};
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::RwLock;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Emitter, Manager};
use tauri_plugin_notification::NotificationExt;
use tokio::sync::Notify;

use crate::error::{AppError, AppResult};
use crate::nextcloud::webdav;
use crate::state::{Account, AppState, SyncFolder, SyncFolderStatus};

/// Maximum number of file operations applied in a single pass. Anything left
/// over is continued on the next worker tick.
pub const MAX_OPS_PER_PASS: usize = 200;
/// How often the worker scans for changes when nothing triggered it.
pub const SYNC_INTERVAL_SECS: u64 = 10;

/// Snapshot of one synced file at the moment both sides were identical.
/// Comparing the current side against this record is what turns "which side
/// changed?" into a safe two-way sync decision.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct JournalEntry {
    pub local_size: u64,
    pub local_mtime: i64,
    pub remote_size: u64,
    pub remote_mtime: i64,
    /// Directory marker: entries for synced folders carry no sizes/mtimes.
    /// Defaulted so journals written before this field stay readable.
    #[serde(default)]
    pub is_dir: bool,
    /// Sub-second component of the local mtime (nanos since epoch).
    /// `None` on journal entries written before this field existed; the
    /// comparison falls back to whole seconds for those, so legacy journals
    /// never cause a spurious mass re-upload.
    #[serde(default)]
    pub local_mtime_nanos: Option<i64>,
    /// Remote etag observed when both sides were identical. HTTP dates only
    /// carry second precision, so two remote changes within the same second
    /// (same size) are only detectable via the etag.
    #[serde(default)]
    pub remote_etag: Option<String>,
}

/// rel path (relative to the sync root, `/`-separated) → last synced state.
pub type Journal = BTreeMap<String, JournalEntry>;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
struct LocalEntry {
    is_dir: bool,
    size: u64,
    mtime: i64,
    /// Nanos since epoch; `0` when the platform could not provide them.
    mtime_nanos: i64,
}

#[derive(Debug, Clone)]
struct RemoteEntry {
    is_dir: bool,
    size: u64,
    mtime: i64,
    etag: Option<String>,
}

#[derive(Debug, Clone, PartialEq)]
enum Action {
    Upload(String),
    UploadConflict {
        rel: String,
        target: String,
    },
    Download(String),
    DeleteRemote(String),
    DeleteLocal(String),
    DeleteRemoteDir(String),
    EnsureDir(String),
    /// Local folder vs. remote file: move the remote file aside, keep the folder.
    MoveRemoteConflict {
        rel: String,
        target: String,
    },
    /// Local file vs. remote folder: move the local file aside locally.
    MoveLocalConflict {
        rel: String,
        target: String,
    },
    Skip(String),
    /// Both sides exist and are identical but the journal has no record yet
    /// (first sync). Record the current state so a later one-sided deletion
    /// is recognized as a delete instead of resurrecting the file.
    Seed(String),
}

/// Stable identifier binding a sync folder to one account.
pub fn account_key(account: &Account) -> String {
    format!("{}@{}", account.meta.username, account.meta.instance_url)
}

fn now_secs() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs() as i64)
        .unwrap_or(0)
}

fn parse_mtime(http_date: Option<&str>) -> i64 {
    httpdate::parse_http_date(http_date.unwrap_or_default())
        .ok()
        .and_then(|t| t.duration_since(UNIX_EPOCH).ok())
        .map(|d| d.as_secs() as i64)
        .unwrap_or(0)
}

/// Compute the rel path of `full_path` below `root`. Both are `/`-style paths
/// (leading slashes allowed). Returns `Some("")` for the root itself and
/// `None` for anything outside the root (segment-boundary safe).
fn rel_below(root: &str, full_path: &str) -> Option<String> {
    let root = root.trim_matches('/');
    let full = full_path.trim_matches('/');
    if root.is_empty() {
        return Some(full.to_string());
    }
    if full == root {
        return Some(String::new());
    }
    full.strip_prefix(&format!("{}/", root))
        .map(|r| r.to_string())
}

/// Absolute (leading `/`) WebDAV path for `rel` inside this folder.
fn remote_rel(folder: &SyncFolder, rel: &str) -> String {
    let root = folder.remote_path.trim_matches('/');
    if rel.is_empty() {
        return format!("/{}", root);
    }
    format!("/{}/{}", root, rel)
}

fn conflict_name(rel: &str) -> String {
    match rel.rfind('.') {
        Some(idx) if idx > 0 && !rel[idx + 1..].contains('/') => {
            let (stem, ext) = rel.split_at(idx);
            format!("{} (conflict copy){}", stem, ext)
        }
        _ => format!("{} (conflict copy)", rel),
    }
}

/// `conflict_name` variant with a counter so the second and later conflicts on
/// the same name never overwrite each other.
fn conflict_name_n(rel: &str, n: u64) -> String {
    if n <= 1 {
        return conflict_name(rel);
    }
    match rel.rfind('.') {
        Some(idx) if idx > 0 && !rel[idx + 1..].contains('/') => {
            let (stem, ext) = rel.split_at(idx);
            format!("{} (conflict copy {}){}", stem, n, ext)
        }
        _ => format!("{} (conflict copy {})", rel, n),
    }
}

/// Pick a conflict-copy target that neither exists on the local or remote side
/// nor is already claimed by another op in this pass. Local conflict copies are
/// created with a local rename, so they must not collide with existing local
/// files (e.g. an earlier conflict copy) or they would be overwritten.
fn unique_conflict_target(
    rel: &str,
    local: &BTreeMap<String, LocalEntry>,
    remote: &BTreeMap<String, RemoteEntry>,
    used: &mut BTreeMap<String, bool>,
) -> String {
    let mut n = 0u64;
    loop {
        n += 1;
        let candidate = conflict_name_n(rel, n);
        if !local.contains_key(&candidate)
            && !remote.contains_key(&candidate)
            && !used.contains_key(&candidate)
        {
            used.insert(candidate, true);
            return candidate;
        }
    }
}
            return candidate;
        }
    }
}

fn parent_of(rel: &str) -> String {
    match rel.rfind('/') {
        Some(idx) => rel[..idx].to_string(),
        None => String::new(),
    }
}

fn should_skip_name(name: &str) -> bool {
    let lower = name.to_ascii_lowercase();
    name.starts_with('.') || name.starts_with("~$") || name.ends_with('~') || lower == "thumbs.db"
}

/// Skip a rel path when its last segment is hidden (`should_skip_name`).
/// Used for remote entries so both sync directions skip the same names.
fn should_skip_rel(rel: &str) -> bool {
    let name = rel.rsplit('/').next().unwrap_or(rel);
    should_skip_name(name)
}

/// Result of [`walk_local`]: the collected entries plus a completeness flag.
struct LocalWalk {
    map: BTreeMap<String, LocalEntry>,
    /// `false` when at least one entry could not be read (unreadable
    /// directory, vanished file, non-UTF-8 name). A partial snapshot must
    /// never drive destructive operations — a missing entry would otherwise
    /// look like "locally deleted" and wipe the remote copy.
    complete: bool,
}

/// Recursively collect local files below `root` as rel → entry.
///
/// Symbolic links are skipped by default. When `follow_symlinks` is enabled,
/// links are dereferenced (like Dropbox) while symlink loops and repeated
/// targets are skipped via canonical-path cycle protection.
///
/// Read errors do NOT silently drop entries: they mark the walk incomplete so
/// the caller can fail closed (no deletes) instead of misreading an unreadable
/// subtree as "gone".
async fn walk_local(root: &Path, follow_symlinks: bool) -> LocalWalk {
    let mut map = BTreeMap::new();
    let mut complete = true;
    let mut stack = vec![root.to_path_buf()];
    let mut visited_dirs: BTreeSet<std::path::PathBuf> = BTreeSet::new();
    if follow_symlinks {
        if let Ok(canon) = root.canonicalize() {
            visited_dirs.insert(canon);
        }
    }
    while let Some(dir) = stack.pop() {
        let mut entries = match tokio::fs::read_dir(&dir).await {
            Ok(entries) => entries,
            Err(_) => {
                complete = false;
                continue;
            }
        };
        loop {
            let entry = match entries.next_entry().await {
                Ok(Some(entry)) => entry,
                Ok(None) => break,
                Err(_) => {
                    complete = false;
                    break;
                }
            };
            let name = entry.file_name();
            // Non-UTF-8 names cannot be represented in the rel-path maps (and
            // would corrupt the journal keys); exclude them consistently on
            // both sides by marking the walk incomplete.
            let Some(name_str) = name.to_str().map(String::from) else {
                complete = false;
                continue;
            };
            if should_skip_name(&name_str) {
                continue;
            }
            let path = entry.path();
            let is_link = match entry.metadata().await {
                Ok(meta) => meta.file_type().is_symlink(),
                Err(_) => {
                    complete = false;
                    false
                }
            };
            if is_link && !follow_symlinks {
                continue;
            }
            // Dereference symlinks to reach the target metadata.
            let meta = if is_link {
                match tokio::fs::metadata(&path).await {
                    Ok(meta) => meta,
                    Err(_) => {
                        complete = false;
                        continue;
                    }
                }
            } else {
                match entry.metadata().await {
                    Ok(meta) => meta,
                    Err(_) => {
                        complete = false;
                        continue;
                    }
                }
            };
            let Some(rel) = rel_from(root, &path) else {
                complete = false;
                continue;
            };
            if meta.is_dir() {
                // Canonicalize every directory to stop symlink loops / dups.
                if follow_symlinks {
                    if let Ok(canon) = path.canonicalize() {
                        if !visited_dirs.insert(canon) {
                            continue;
                        }
                    }
                }
                stack.push(path);
                map.insert(
                    rel,
                    LocalEntry {
                        is_dir: true,
                        size: 0,
                        mtime: 0,
                        mtime_nanos: 0,
                    },
                );
            } else if meta.is_file() {
                let (mtime, mtime_nanos) = stat_mtime(&meta);
                map.insert(
                    rel,
                    LocalEntry {
                        is_dir: false,
                        size: meta.len(),
                        mtime,
                        mtime_nanos,
                    },
                );
            }
        }
    }
    LocalWalk { map, complete }
}

fn rel_from(base: &Path, path: &Path) -> Option<String> {
    path.strip_prefix(base)
        .unwrap_or(path)
        .components()
        .map(|c| c.as_os_str().to_str())
        .collect::<Option<Vec<_>>>()
        .map(|parts| parts.join("/"))
}

/// `(mtime secs, mtime nanos since epoch)` of a file; `(0, 0)` when unknown.
fn stat_mtime(meta: &std::fs::Metadata) -> (i64, i64) {
    meta.modified()
        .ok()
        .and_then(|t| t.duration_since(UNIX_EPOCH).ok())
        .map(|d| (d.as_secs() as i64, d.as_nanos() as i64))
        .unwrap_or((0, 0))
}

/// True when the local file state still matches the journal record. Uses the
/// sub-second mtime when both sides provide it; legacy entries (no nanos)
/// fall back to whole-second comparison.
fn local_unchanged(journal: &JournalEntry, local: &LocalEntry) -> bool {
    journal.local_size == local.size
        && journal.local_mtime == local.mtime
        && match (journal.local_mtime_nanos, local.mtime_nanos) {
            (Some(j_nanos), l_nanos) if l_nanos > 0 => j_nanos == l_nanos,
            _ => true,
        }
}

/// True when the remote file state still matches the journal record. The etag
/// only participates when both sides know one — servers without etag support
/// keep working through the size/mtime comparison.
fn remote_unchanged(journal: &JournalEntry, remote: &RemoteEntry) -> bool {
    journal.remote_size == remote.size
        && journal.remote_mtime == remote.mtime
        && match (&journal.remote_etag, &remote.etag) {
            (Some(j_etag), Some(r_etag)) if !r_etag.is_empty() => j_etag == r_etag,
            _ => true,
        }
}

/// Remote listing plus safety metadata.
struct RemoteListing {
    entries: BTreeMap<String, RemoteEntry>,
    /// Directories whose listing contained entries that were filtered out by
    /// the skip rules (hidden files, temp files). Such directories must never
    /// be deleted remotely: invisible children would be destroyed with them.
    /// Contains the rel path of the directory itself, not of the child.
    dirty_dirs: BTreeSet<String>,
}

/// Recursively list the remote sync root (BFS with Depth-1 PROPFIND).
async fn list_remote(
    client: &reqwest::Client,
    account: &Account,
    root: &str,
) -> AppResult<RemoteListing> {
    let semaphore = tokio::sync::Semaphore::new(8);
    let mut map = BTreeMap::new();
    let mut dirty_dirs = BTreeSet::new();
    let mut pending = vec![root.trim_matches('/').to_string()];
    while let Some(dir) = pending.pop() {
        let permit = semaphore.clone().acquire_owned().await?;
        let _guard = Some(permit);
        let entries = match webdav::list(client, account, &dir, None).await {
            Ok(entries) => entries,
            Err(AppError::Status { status: 404, .. }) => Vec::new(),
            Err(err) => return Err(err),
        };
        for entry in entries {
            let Some(rel) = rel_below(root, &entry.path) else {
                continue;
            };
            if rel.is_empty() {
                continue;
            }
            if should_skip_rel(&rel) {
                // Track where skipped entries live so their parent folder is
                // never deleted (L15-S8: hidden children must survive).
                dirty_dirs.insert(parent_of(&rel));
                continue;
            }
            if entry.is_dir {
                if !map.contains_key(&rel) {
                    map.insert(
                        rel.clone(),
                        RemoteEntry {
                            is_dir: true,
                            size: 0,
                            mtime: parse_mtime(entry.mtime.as_deref()),
                            etag: entry.etag.clone(),
                        },
                    );
                }
                pending.push(entry.path.trim_matches('/').to_string());
            } else {
                map.insert(
                    rel,
                    RemoteEntry {
                        is_dir: false,
                        size: entry.size.unwrap_or(0),
                        mtime: parse_mtime(entry.mtime.as_deref()),
                        etag: entry.etag.clone(),
                    },
                );
            }
        }
    }
    Ok(RemoteListing {
        entries: map,
        dirty_dirs,
    })
}

/// Decide what to do with one path based on both sides and the journal.
fn decide(
    rel: &str,
    local: Option<&LocalEntry>,
    remote: Option<&RemoteEntry>,
    journal: &Journal,
) -> Action {
    let rec = journal.get(rel);
    match (local, remote) {
        (Some(local), Some(remote)) => {
            // Directories never contain content themselves; the files decide.
            if local.is_dir && remote.is_dir {
                // Seed first-sync folders into the journal so a later local
                // deletion can propagate (DeleteRemoteDir requires a record).
                return match rec {
                    Some(_) => Action::Skip(rel.to_string()),
                    None => Action::Seed(rel.to_string()),
                };
            }
            // Type conflict: local folder vs. remote file. Preserve the remote
            // file as a conflict copy, the folder wins the path.
            if local.is_dir {
                return Action::MoveRemoteConflict {
                    rel: rel.to_string(),
                    target: conflict_name(rel),
                };
            }
            // Type conflict: local file vs. remote folder. Preserve the local
            // file as a conflict copy locally, the folder wins the path.
            if remote.is_dir {
                return Action::MoveLocalConflict {
                    rel: rel.to_string(),
                    target: conflict_name(rel),
                };
            }
            if let Some(j) = rec {
                if local_unchanged(j, local) && remote_unchanged(j, remote) {
                    return Action::Skip(rel.to_string());
                }
                if local_unchanged(j, local) {
                    return Action::Download(rel.to_string());
                }
                if remote_unchanged(j, remote) {
                    return Action::Upload(rel.to_string());
                }
                return conflict(rel);
            }
            // First sync: newer mtime wins; equal mtimes with different sizes are a conflict.
            if local.mtime > remote.mtime {
                return Action::Upload(rel.to_string());
            }
            if remote.mtime > local.mtime {
                return Action::Download(rel.to_string());
            }
            if local.size != remote.size {
                return conflict(rel);
            }
            // Identical first-sync file: seed the journal so a later
            // one-sided delete is recognized as a deletion instead of
            // resurrecting the file on the other side.
            Action::Seed(rel.to_string())
        }
        (Some(local), None) => {
            if local.is_dir {
                // Empty or new local folder → create it remotely. A folder that
                // lost its remote side is resurrected rather than deleted.
                return Action::EnsureDir(rel.to_string());
            }
            if let Some(j) = rec {
                if j.local_size == local.size && j.local_mtime == local.mtime {
                    // Remote was deleted, local is untouched → propagate the deletion.
                    return Action::DeleteLocal(rel.to_string());
                }
                // Local changed after the remote deletion → resurrect it.
                return Action::Upload(rel.to_string());
            }
            Action::Upload(rel.to_string())
        }
        (None, Some(remote)) => {
            if remote.is_dir {
                // Only delete a remote folder that we synced before (journal
                // record). First-sync folders are never deleted.
                if let Some(j) = rec {
                    if j.is_dir {
                        return Action::DeleteRemoteDir(rel.to_string());
                    }
                }
                return Action::Skip(rel.to_string());
            }
            if let Some(j) = rec {
                if j.remote_size == remote.size && j.remote_mtime == remote.mtime {
                    return Action::DeleteRemote(rel.to_string());
                }
                return Action::Download(rel.to_string());
            }
            Action::Download(rel.to_string())
        }
        (None, None) => Action::Skip(rel.to_string()),
    }
}

fn conflict(rel: &str) -> Action {
    Action::UploadConflict {
        rel: rel.to_string(),
        target: conflict_name(rel),
    }
}

/// Build the ordered list of operations for one sync pass.
fn plan_ops(
    local: &BTreeMap<String, LocalEntry>,
    remote: &BTreeMap<String, RemoteEntry>,
    journal: &Journal,
    dirty_dirs: &BTreeSet<String>,
) -> Vec<Action> {
    let mut rels: BTreeSet<String> = BTreeSet::new();
    for rel in local.keys().chain(remote.keys()).chain(journal.keys()) {
        rels.insert(rel.clone());
    }

    let mut used_targets: BTreeMap<String, bool> = BTreeMap::new();
    let mut file_ops: Vec<Action> = Vec::new();
    let mut uploads: Vec<String> = Vec::new();
    let mut moved_dirs: Vec<String> = Vec::new();
    for rel in rels {
        let action = decide(&rel, local.get(&rel), remote.get(&rel), journal);
        match action {
            Action::Skip(_) => {}
            Action::Upload(p) => {
                used_targets.entry(p.clone()).or_insert(true);
                uploads.push(p.clone());
                file_ops.push(Action::Upload(p));
            }
            Action::UploadConflict { rel, target: _ } => {
                let target = unique_conflict_target(&rel, local, remote, &mut used_targets);
                uploads.push(target.clone());
                file_ops.push(Action::UploadConflict { rel, target });
            }
            Action::MoveRemoteConflict { rel, target: _ } => {
                let target = unique_conflict_target(&rel, local, remote, &mut used_targets);
                // The folder that won the path must be created remotely too.
                moved_dirs.push(rel.clone());
                file_ops.push(Action::MoveRemoteConflict { rel, target });
            }
            Action::MoveLocalConflict { rel, target: _ } => {
                let target = unique_conflict_target(&rel, local, remote, &mut used_targets);
                file_ops.push(Action::MoveLocalConflict { rel, target });
            }
            Action::DeleteRemoteDir(dir) => {
                // Never delete a folder that still has remote children in this
                // snapshot; the files are removed first, the (now empty) folder
                // follows on a later pass.
                if remote.keys().any(|k| k.starts_with(&format!("{}/", dir))) {
                    continue;
                }
                // Never delete a folder whose listing contained skipped
                // (hidden/temp) children either — they would be destroyed
                // invisibly. The dir itself or anything below it being dirty
                // blocks the deletion.
                if dirty_dirs
                    .iter()
                    .any(|d| d == &dir || d.starts_with(&format!("{}/", dir)))
                {
                    continue;
                }
                file_ops.push(Action::DeleteRemoteDir(dir));
            }
            other => file_ops.push(other),
        }
    }

    // Create missing remote parent directories before uploading, parents first.
    let mut dirs: BTreeSet<String> = BTreeSet::new();
    for upload in &uploads {
        let mut parent = parent_of(upload);
        while !parent.is_empty() {
            if !remote.contains_key(&parent) {
                dirs.insert(parent.clone());
            }
            parent = parent_of(&parent);
        }
    }
    // Folders that took over a path previously occupied by a remote file.
    for dir in &moved_dirs {
        let mut parent = parent_of(dir);
        while !parent.is_empty() {
            if !remote.contains_key(&parent) {
                dirs.insert(parent.clone());
            }
            parent = parent_of(&parent);
        }
        dirs.insert(dir.clone());
    }
    let mut dir_ops: Vec<Action> = dirs.into_iter().map(Action::EnsureDir).collect();
    dir_ops.sort_by_key(|a| match a {
        Action::EnsureDir(rel) => rel.matches('/').count(),
        _ => usize::MAX,
    });

    // Ordering fix (L15-S7): the MOVE of a MoveRemoteConflict must run before
    // any EnsureDir for the same path — the remote FILE still occupies it, so
    // an earlier MKCOL answers 405 (treated as "exists"), the folder is never
    // created and every upload into it fails with 409 one op later.
    let (moves, rest): (Vec<Action>, Vec<Action>) = file_ops
        .into_iter()
        .partition(|a| matches!(a, Action::MoveRemoteConflict { .. }));
    let mut ordered = moves;
    ordered.extend(dir_ops);
    ordered.extend(rest);
    ordered
}

/// Prune journal entries for files that no longer exist on either side.
fn prune_journal(
    journal: &mut Journal,
    local: &BTreeMap<String, LocalEntry>,
    remote: &BTreeMap<String, RemoteEntry>,
) {
    journal.retain(|rel, _| local.contains_key(rel) || remote.contains_key(rel));
}

struct PassCtx<'a> {
    client: &'a reqwest::Client,
    account: &'a Account,
    folder: &'a SyncFolder,
    local_root: &'a Path,
    local: &'a BTreeMap<String, LocalEntry>,
    remote: &'a BTreeMap<String, RemoteEntry>,
    journal: &'a mut Journal,
}

/// Outcome of a single operation.
enum ExecOutcome {
    /// Applied; counts towards `done`.
    Applied,
    /// Skipped without error because the world changed since the plan (file
    /// modified mid-pass, upload lost an If-Match race). The journal is left
    /// untouched so the next pass replans from fresh state.
    Deferred,
}

async fn exec_upload(ctx: &mut PassCtx<'_>, rel: &str) -> AppResult<ExecOutcome> {
    let local = *ctx
        .local
        .get(rel)
        .ok_or_else(|| AppError::App("local file disappeared during sync".into()))?;
    // Lost-update protection (#278): send the listed etag as `If-Match` so a
    // competing client's change between listing and upload answers 412
    // instead of being overwritten.
    let if_match = ctx.remote.get(rel).and_then(|r| r.etag.clone());
    let local_path = ctx.local_root.join(rel);
    let result = webdav::put_file_params(
        ctx.client,
        ctx.account,
        webdav::PutParams {
            remote_rel: &remote_rel(ctx.folder, rel),
            local_path: &local_path,
            mtime_secs: local.mtime,
            target_user: None,
            on_progress: None,
            if_match: if_match.as_deref(),
            forbid_overwrite: false,
        },
    )
    .await;
    if matches!(result, Err(AppError::TargetExists(_))) {
        // 412: someone else changed the remote file — treat it as a conflict
        // to be replanned next pass, not as a sync failure.
        return Ok(ExecOutcome::Deferred);
    }
    result?;
    ctx.journal.insert(
        rel.to_string(),
        JournalEntry {
            local_size: local.size,
            local_mtime: local.mtime,
            remote_size: local.size,
            remote_mtime: local.mtime,
            is_dir: false,
            local_mtime_nanos: (local.mtime_nanos > 0).then_some(local.mtime_nanos),
            remote_etag: None,
        },
    );
    Ok(ExecOutcome::Applied)
}

async fn exec_upload_conflict(
    ctx: &mut PassCtx<'_>,
    rel: &str,
    target: &str,
) -> AppResult<ExecOutcome> {
    let local = *ctx
        .local
        .get(rel)
        .ok_or_else(|| AppError::App("local file disappeared during sync".into()))?;
    let remote = ctx
        .remote
        .get(rel)
        .cloned()
        .ok_or_else(|| AppError::App("remote file disappeared during sync".into()))?;
    let local_path = ctx.local_root.join(rel);
    webdav::put_file_params(
        ctx.client,
        ctx.account,
        webdav::PutParams {
            remote_rel: &remote_rel(ctx.folder, target),
            local_path: &local_path,
            mtime_secs: local.mtime,
            target_user: None,
            on_progress: None,
            if_match: None,
            forbid_overwrite: false,
        },
    )
    .await?;
    // Record BOTH versions so the original file never re-syncs; the conflict
    // copy itself has no journal entry and is downloaded next pass.
    ctx.journal.insert(
        rel.to_string(),
        JournalEntry {
            local_size: local.size,
            local_mtime: local.mtime,
            remote_size: remote.size,
            remote_mtime: remote.mtime,
            is_dir: false,
            local_mtime_nanos: (local.mtime_nanos > 0).then_some(local.mtime_nanos),
            remote_etag: remote.etag.clone(),
        },
    );
    Ok(ExecOutcome::Applied)
}

async fn exec_download(ctx: &mut PassCtx<'_>, rel: &str) -> AppResult<ExecOutcome> {
    let remote = ctx
        .remote
        .get(rel)
        .cloned()
        .ok_or_else(|| AppError::App("remote file disappeared during sync".into()))?;
    let local_path = ctx.local_root.join(rel);
    // TOCTOU guard (L15-S2): re-stat before overwriting. Only replace the
    // file when it still matches the journal (or is gone); otherwise the user
    // changed it mid-pass and the download must be replanned.
    match tokio::fs::metadata(&local_path).await {
        Ok(meta) if meta.is_file() => {
            let (mtime, nanos) = stat_mtime(&meta);
            let current = LocalEntry {
                is_dir: false,
                size: meta.len(),
                mtime,
                mtime_nanos: nanos,
            };
            let matches_journal = ctx
                .journal
                .get(rel)
                .map(|j| local_unchanged(j, &current))
                .unwrap_or(false);
            if !matches_journal {
                return Ok(ExecOutcome::Deferred);
            }
        }
        Ok(_) => return Ok(ExecOutcome::Deferred),
        Err(err) if err.kind() == std::io::ErrorKind::NotFound => {}
        Err(_) => return Ok(ExecOutcome::Deferred),
    }
    if let Some(parent) = local_path.parent() {
        tokio::fs::create_dir_all(parent).await?;
    }
    webdav::get_file(
        ctx.client,
        ctx.account,
        &remote_rel(ctx.folder, rel),
        &local_path,
    )
    .await?;
    let meta = tokio::fs::metadata(&local_path).await?;
    let (local_mtime, local_nanos) = stat_mtime(&meta);
    ctx.journal.insert(
        rel.to_string(),
        JournalEntry {
            local_size: meta.len(),
            local_mtime,
            remote_size: remote.size,
            remote_mtime: remote.mtime,
            is_dir: false,
            local_mtime_nanos: (local_nanos > 0).then_some(local_nanos),
            remote_etag: remote.etag.clone(),
        },
    );
    Ok(ExecOutcome::Applied)
}

async fn exec_delete_remote(ctx: &mut PassCtx<'_>, rel: &str) -> AppResult<ExecOutcome> {
    webdav::delete(ctx.client, ctx.account, &remote_rel(ctx.folder, rel)).await?;
    ctx.journal.remove(rel);
    Ok(ExecOutcome::Applied)
}

async fn exec_delete_local(ctx: &mut PassCtx<'_>, rel: &str) -> AppResult<ExecOutcome> {
    let local_path = ctx.local_root.join(rel);
    // TOCTOU guard (L15-S2): never destroy a file that changed since the walk.
    match tokio::fs::metadata(&local_path).await {
        Ok(meta) => {
            if !meta.is_file() {
                // Directories are never removed here.
                return Ok(ExecOutcome::Deferred);
            }
            let (mtime, nanos) = stat_mtime(&meta);
            let current = LocalEntry {
                is_dir: false,
                size: meta.len(),
                mtime,
                mtime_nanos: nanos,
            };
            match ctx.journal.get(rel) {
                Some(j) if local_unchanged(j, &current) => {}
                _ => return Ok(ExecOutcome::Deferred),
            }
            tokio::fs::remove_file(&local_path).await?;
        }
        Err(err) if err.kind() == std::io::ErrorKind::NotFound => {}
        Err(_) => return Ok(ExecOutcome::Deferred),
    }
    ctx.journal.remove(rel);
    Ok(ExecOutcome::Applied)
}

async fn exec_mkdir(ctx: &mut PassCtx<'_>, rel: &str) -> AppResult<ExecOutcome> {
    webdav::make_collection(ctx.client, ctx.account, &remote_rel(ctx.folder, rel)).await?;
    // Remember that this folder was synced so a later local deletion can
    // propagate to the remote side (DeleteRemoteDir).
    ctx.journal.insert(
        rel.to_string(),
        JournalEntry {
            local_size: 0,
            local_mtime: 0,
            remote_size: 0,
            remote_mtime: 0,
            is_dir: true,
            local_mtime_nanos: None,
            remote_etag: None,
        },
    );
    Ok(ExecOutcome::Applied)
}

/// Delete a remote folder (only scheduled for empty folders without remote
/// children). The journal entry is dropped together with the folder.
async fn exec_delete_remote_dir(ctx: &mut PassCtx<'_>, rel: &str) -> AppResult<ExecOutcome> {
    webdav::delete(ctx.client, ctx.account, &remote_rel(ctx.folder, rel)).await?;
    ctx.journal.remove(rel);
    Ok(ExecOutcome::Applied)
}

/// Type conflict "local folder vs. remote file": move the remote file to a
/// conflict-copy name so the folder can be created at the original path.
async fn exec_move_remote_conflict(
    ctx: &mut PassCtx<'_>,
    rel: &str,
    target: &str,
) -> AppResult<ExecOutcome> {
    webdav::rename(
        ctx.client,
        ctx.account,
        &remote_rel(ctx.folder, rel),
        &remote_rel(ctx.folder, target),
    )
    .await?;
    // The conflict copy has no journal entry → it is downloaded next pass.
    // The folder wins the original path (EnsureDir is planned for it).
    ctx.journal.insert(
        rel.to_string(),
        JournalEntry {
            local_size: 0,
            local_mtime: 0,
            remote_size: 0,
            remote_mtime: 0,
            is_dir: true,
            local_mtime_nanos: None,
            remote_etag: None,
        },
    );
    Ok(ExecOutcome::Applied)
}

/// Type conflict "local file vs. remote folder": move the local file to a
/// conflict-copy name locally so the remote folder can be downloaded into the
/// original path.
async fn exec_move_local_conflict(
    ctx: &mut PassCtx<'_>,
    rel: &str,
    target: &str,
) -> AppResult<ExecOutcome> {
    let from = ctx.local_root.join(rel);
    let to = ctx.local_root.join(target);
    if let Some(parent) = to.parent() {
        tokio::fs::create_dir_all(parent).await?;
    }
    // TOCTOU guard: only move the file when it still matches the walk.
    match tokio::fs::metadata(&from).await {
        Ok(meta) if meta.is_file() => {
            let (mtime, nanos) = stat_mtime(&meta);
            let current = LocalEntry {
                is_dir: false,
                size: meta.len(),
                mtime,
                mtime_nanos: nanos,
            };
            if let Some(j) = ctx.journal.get(rel) {
                if !local_unchanged(j, &current) {
                    return Ok(ExecOutcome::Deferred);
                }
            }
        }
        Err(err) if err.kind() == std::io::ErrorKind::NotFound => return Ok(ExecOutcome::Deferred),
        Err(_) => return Ok(ExecOutcome::Deferred),
        Ok(_) => return Ok(ExecOutcome::Deferred),
    }
    tokio::fs::rename(&from, &to).await?;
    // Record the remote state for the original path so it does not count as a
    // local deletion; the moved file is uploaded as a new file next pass.
    let remote = ctx.remote.get(rel).cloned().unwrap_or(RemoteEntry {
        is_dir: false,
        size: 0,
        mtime: 0,
        etag: None,
    });
    ctx.journal.insert(
        rel.to_string(),
        JournalEntry {
            local_size: 0,
            local_mtime: 0,
            remote_size: remote.size,
            remote_mtime: remote.mtime,
            is_dir: false,
            local_mtime_nanos: None,
            remote_etag: remote.etag.clone(),
        },
    );
    Ok(ExecOutcome::Applied)
}

/// First-sync seeding (L15-S5): both sides exist identically but there is no
/// journal record yet — write one so later deletions are detected as such
/// instead of resurrecting the file on the other side.
async fn exec_seed(ctx: &mut PassCtx<'_>, rel: &str) -> AppResult<ExecOutcome> {
    let (local, remote) = match (ctx.local.get(rel), ctx.remote.get(rel)) {
        (Some(local), Some(remote)) => (*local, remote.clone()),
        _ => return Ok(ExecOutcome::Applied),
    };
    let entry = if local.is_dir && remote.is_dir {
        JournalEntry {
            local_size: 0,
            local_mtime: 0,
            remote_size: 0,
            remote_mtime: 0,
            is_dir: true,
            local_mtime_nanos: None,
            remote_etag: None,
        }
    } else if !local.is_dir && !remote.is_dir {
        JournalEntry {
            local_size: local.size,
            local_mtime: local.mtime,
            remote_size: remote.size,
            remote_mtime: remote.mtime,
            is_dir: false,
            local_mtime_nanos: (local.mtime_nanos > 0).then_some(local.mtime_nanos),
            remote_etag: remote.etag.clone(),
        }
    } else {
        // Type conflicts are handled by the dedicated move actions.
        return Ok(ExecOutcome::Applied);
    };
    ctx.journal.insert(rel.to_string(), entry);
    Ok(ExecOutcome::Applied)
}

/// A sync error decomposed into code + detail so the frontend can render a
/// localized message instead of the raw English backend text.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct PassError {
    pub code: String,
    pub detail: Option<String>,
}

impl PassError {
    pub fn from_app_error(err: &AppError) -> Self {
        Self {
            code: err.code().to_string(),
            detail: err.detail(),
        }
    }
}

struct PassResult {
    planned_uploads: u64,
    planned_downloads: u64,
    planned_deletes: u64,
    done: usize,
    failures: u64,
    error: Option<PassError>,
}

/// Execute one sync pass for a single folder. Returns stats for status updates.
async fn run_pass(
    app: &AppHandle,
    client: &reqwest::Client,
    account: &Account,
    folder: &SyncFolder,
) -> AppResult<PassResult> {
    let local_root = PathBuf::from(&folder.local_path);
    if !local_root.is_dir() {
        return Err(AppError::App(format!(
            "Local folder no longer exists: {}",
            folder.local_path
        )));
    }

    // Ensure the remote sync root exists, including its parent chain. MKCOL
    // only creates one level; without the ancestors, MKCOL on the root (or any
    // subfolder) fails with 409 "Parent node does not exist" (SabreDAV).
    webdav::ensure_collection(client, account, &folder.remote_path).await?;

    let mut journal = load_journal(app, &folder.id)?;
    let walked = walk_local(&local_root, folder.follow_symlinks).await;
    let remote_listing = list_remote(client, account, &folder.remote_path).await?;
    let local = walked.map;
    let remote = remote_listing.entries;
    let mut ops = plan_ops(&local, &remote, &journal, &remote_listing.dirty_dirs);

    // Fail closed (L15-S1): when the local snapshot is incomplete an entry
    // may be missing for reasons other than deletion. Suppress every
    // destructive operation and keep the journal intact; the next complete
    // pass replans from scratch.
    let walk_incomplete = !walked.complete;
    if walk_incomplete {
        ops.retain(|a| {
            !matches!(
                a,
                Action::DeleteRemote(_) | Action::DeleteLocal(_) | Action::DeleteRemoteDir(_)
            )
        });
    }

    let planned_uploads = ops
        .iter()
        .filter(|a| matches!(a, Action::Upload(_) | Action::UploadConflict { .. }))
        .count() as u64;
    let planned_downloads = ops
        .iter()
        .filter(|a| matches!(a, Action::Download(_)))
        .count() as u64;
    let planned_deletes = ops
        .iter()
        .filter(|a| {
            matches!(
                a,
                Action::DeleteRemote(_) | Action::DeleteLocal(_) | Action::DeleteRemoteDir(_)
            )
        })
        .count() as u64;

    let mut done = 0usize;
    let mut failures = 0u64;
    let mut error = None;
    {
        let mut ctx = PassCtx {
            client,
            account,
            folder,
            local_root: &local_root,
            local: &local,
            remote: &remote,
            journal: &mut journal,
        };
        for action in ops.iter().take(MAX_OPS_PER_PASS) {
            let result = match action {
                Action::Upload(rel) => exec_upload(&mut ctx, rel).await,
                Action::UploadConflict { rel, target } => {
                    exec_upload_conflict(&mut ctx, rel, target).await
                }
                Action::Download(rel) => exec_download(&mut ctx, rel).await,
                Action::DeleteRemote(rel) => exec_delete_remote(&mut ctx, rel).await,
                Action::DeleteLocal(rel) => exec_delete_local(&mut ctx, rel).await,
                Action::DeleteRemoteDir(rel) => exec_delete_remote_dir(&mut ctx, rel).await,
                Action::EnsureDir(rel) => exec_mkdir(&mut ctx, rel).await,
                Action::MoveRemoteConflict { rel, target } => {
                    exec_move_remote_conflict(&mut ctx, rel, target).await
                }
                Action::MoveLocalConflict { rel, target } => {
                    exec_move_local_conflict(&mut ctx, rel, target).await
                }
                Action::Seed(rel) => exec_seed(&mut ctx, rel).await,
                Action::Skip(_) => continue,
            };
            match result {
                Ok(ExecOutcome::Applied) => done += 1,
                Ok(ExecOutcome::Deferred) => {}
                Err(err) => {
                    failures += 1;
                    if error.is_none() {
                        error = Some(PassError::from_app_error(&err));
                    }
                }
            }
        }
    }

    // Only prune against a complete local snapshot; otherwise entries would
    // be dropped just because their subtree was unreadable.
    if walk_incomplete && error.is_none() {
        error = Some(PassError {
            code: "walk_incomplete".into(),
            detail: Some("Some files could not be read. Deletions were skipped for safety.".into()),
        });
        failures += 1;
    }
    if !walk_incomplete {
        prune_journal(&mut journal, &local, &remote);
    }
    persist_journal(app, &folder.id, &journal)?;
    Ok(PassResult {
        planned_uploads,
        planned_downloads,
        planned_deletes,
        done,
        failures,
        error,
    })
}

/// Loads and serves the persisted list of sync folders.
pub struct SyncEngine {
    folders: RwLock<Vec<SyncFolder>>,
    statuses: RwLock<BTreeMap<String, SyncFolderStatus>>,
    notify: Notify,
    /// True when the last pass had failures (for ok→error notifications).
    notifying_failure: AtomicBool,
    /// Consecutive failing passes; re-reminds only every Nth pass.
    failure_streak: AtomicU64,
}

/// Re-notify about persistent failures every Nth consecutive failing pass.
const NOTIFY_FAILURE_EVERY: u64 = 10;

impl Default for SyncEngine {
    fn default() -> Self {
        Self {
            folders: RwLock::new(Vec::new()),
            statuses: RwLock::new(BTreeMap::new()),
            notify: Notify::new(),
            notifying_failure: AtomicBool::new(false),
            failure_streak: AtomicU64::new(0),
        }
    }
}

fn folders_file(app: &AppHandle) -> AppResult<PathBuf> {
    let dir = app
        .path()
        .app_data_dir()
        .map_err(|e| AppError::App(e.to_string()))?;
    std::fs::create_dir_all(&dir)?;
    Ok(dir.join("sync-folders.json"))
}

fn journal_file(app: &AppHandle, folder_id: &str) -> AppResult<PathBuf> {
    let dir = app
        .path()
        .app_data_dir()
        .map_err(|e| AppError::App(e.to_string()))?;
    std::fs::create_dir_all(&dir)?;
    Ok(dir.join(format!("sync-journal-{}.json", folder_id)))
}

fn load_journal(app: &AppHandle, folder_id: &str) -> AppResult<Journal> {
    Ok(load_journal_from_disk(&journal_file(app, folder_id)?))
}

/// Load a journal file. A corrupt journal (crash mid-write, disk full) must
/// never kill the folder's sync permanently: the broken file is quarantined
/// and syncing restarts with an empty journal instead of failing every pass.
fn load_journal_from_disk(path: &Path) -> Journal {
    if !path.exists() {
        return BTreeMap::new();
    }
    let Ok(raw) = std::fs::read_to_string(path) else {
        return BTreeMap::new();
    };
    match serde_json::from_str::<Journal>(&raw) {
        Ok(journal) => journal,
        Err(_) => {
            // Keep the original file name + ".corrupt-<unix time>" for
            // diagnosis, then start over with an empty journal.
            let mut quarantined = path.as_os_str().to_os_string();
            quarantined.push(format!(".corrupt-{}", now_secs()));
            let _ = std::fs::rename(path, PathBuf::from(quarantined));
            BTreeMap::new()
        }
    }
}

fn persist_journal(app: &AppHandle, folder_id: &str, journal: &Journal) -> AppResult<()> {
    persist_journal_to_disk(&journal_file(app, folder_id)?, journal)
}

/// Atomically persist a journal (temp file + fsync + rename via the shared
/// persist helpers), so a crash can never leave a half-written journal
/// behind.
fn persist_journal_to_disk(path: &Path, journal: &Journal) -> AppResult<()> {
    let json = serde_json::to_string_pretty(journal).map_err(|e| AppError::Parse(e.to_string()))?;
    crate::persist::atomic_write(path, &json)
}

fn initial_status(folder: &SyncFolder) -> SyncFolderStatus {
    SyncFolderStatus {
        folder_id: folder.id.clone(),
        account_key: folder.account_key.clone(),
        local_path: folder.local_path.clone(),
        remote_path: folder.remote_path.clone(),
        paused: folder.paused,
        follow_symlinks: folder.follow_symlinks,
        state: if folder.paused {
            "paused".into()
        } else {
            "idle".into()
        },
        pending_uploads: 0,
        pending_downloads: 0,
        pending_deletes: 0,
        failures: 0,
        last_error: None,
        last_synced_at: None,
    }
}

impl SyncEngine {
    /// Wake up the worker to run a pass immediately.
    pub fn notify_one(&self) {
        self.notify.notify_one();
    }

    pub fn folders_snapshot(&self) -> Vec<SyncFolder> {
        self.folders
            .read()
            .map(|guard| guard.clone())
            .unwrap_or_default()
    }

    /// Load persisted folders from disk (called once at startup). Also seeds
    /// the status map so `statuses()` returns the folders immediately, even
    /// before the first worker pass. A corrupt folders file is quarantined
    /// for diagnosis instead of being silently overwritten by the next
    /// persist (L17-F3).
    pub fn load(&self, app: &AppHandle) {
        let path = match folders_file(app) {
            Ok(path) => path,
            Err(_) => return,
        };
        if !path.exists() {
            return;
        }
        let folders = match std::fs::read_to_string(&path)
            .map_err(|e| AppError::Parse(e.to_string()))
            .and_then(|raw| {
                serde_json::from_str::<Vec<SyncFolder>>(&raw)
                    .map_err(|e| AppError::Parse(e.to_string()))
            }) {
            Ok(folders) => folders,
            Err(_) => {
                crate::persist::quarantine_corrupt_file(&path);
                return;
            }
        };
        if let Ok(mut guard) = self.folders.write() {
            *guard = folders.clone();
        }
        if let Ok(mut status_guard) = self.statuses.write() {
            for folder in folders {
                status_guard
                    .entry(folder.id.clone())
                    .or_insert_with(|| initial_status(&folder));
            }
        }
    }

    pub fn persist(&self, app: &AppHandle) -> AppResult<()> {
        let json = serde_json::to_string_pretty(&self.folders_snapshot())
            .map_err(|e| AppError::Parse(e.to_string()))?;
        crate::persist::atomic_write(&folders_file(app)?, &json)?;
        Ok(())
    }

    pub fn add_folder(&self, app: &AppHandle, folder: SyncFolder) -> AppResult<SyncFolderStatus> {
        {
            let mut guard = self
                .folders
                .write()
                .map_err(|_| AppError::App("sync lock poisoned".into()))?;
            if guard
                .iter()
                .any(|f| f.account_key == folder.account_key && f.local_path == folder.local_path)
            {
                return Err(AppError::App(
                    "This folder is already being synced for this account.".into(),
                ));
            }
            guard.push(folder.clone());
        }
        self.persist(app)?;
        let status = initial_status(&folder);
        if let Ok(mut guard) = self.statuses.write() {
            guard.insert(folder.id.clone(), status.clone());
        }
        Ok(status)
    }

    pub fn remove_folder(&self, app: &AppHandle, folder_id: &str) -> AppResult<()> {
        if let Ok(mut guard) = self.folders.write() {
            guard.retain(|f| f.id != folder_id);
        }
        if let Ok(mut guard) = self.statuses.write() {
            guard.remove(folder_id);
        }
        if let Ok(path) = journal_file(app, folder_id) {
            let _ = std::fs::remove_file(path);
        }
        self.persist(app)
    }

    /// Remove every sync folder belonging to one account (used when the
    /// account is deleted so re-adding it does not resurrect stale folders).
    pub fn remove_folders_for_account(
        &self,
        app: &AppHandle,
        meta: &crate::state::AccountMeta,
    ) -> AppResult<()> {
        let key = format!("{}@{}", meta.username, meta.instance_url);
        let removed: Vec<String> = {
            let mut guard = self
                .folders
                .write()
                .map_err(|_| AppError::App("sync lock poisoned".into()))?;
            let removed: Vec<String> = guard
                .iter()
                .filter(|f| f.account_key == key)
                .map(|f| f.id.clone())
                .collect();
            guard.retain(|f| f.account_key != key);
            removed
        };
        if let Ok(mut guard) = self.statuses.write() {
            for id in &removed {
                guard.remove(id);
            }
        }
        for id in &removed {
            if let Ok(path) = journal_file(app, id) {
                let _ = std::fs::remove_file(path);
            }
        }
        self.persist(app)
    }

    pub fn set_paused(&self, app: &AppHandle, folder_id: &str, paused: bool) -> AppResult<()> {
        if let Ok(mut guard) = self.folders.write() {
            if let Some(folder) = guard.iter_mut().find(|f| f.id == folder_id) {
                folder.paused = paused;
            }
        }
        self.persist(app)?;
        if let Ok(mut guard) = self.statuses.write() {
            if let Some(status) = guard.get_mut(folder_id) {
                status.paused = paused;
                // Pausing freezes the folder; resuming returns it to idle so
                // the UI does not show a stale "syncing" state.
                status.state = if paused { "paused" } else { "idle" }.into();
                // Resuming also resets failure dampening (#283): the user saw
                // the state and explicitly asked for another try, so the next
                // failing pass notifies again immediately.
                if !paused {
                    status.failures = 0;
                    status.last_error = None;
                }
            }
        }
        if !paused {
            self.notifying_failure.store(false, Ordering::Relaxed);
            self.failure_streak.store(0, Ordering::Relaxed);
        }
        Ok(())
    }

    /// Statuses in folder order, for the frontend list.
    pub fn statuses(&self) -> Vec<SyncFolderStatus> {
        let folders = self.folders_snapshot();
        let statuses = self
            .statuses
            .read()
            .map(|guard| guard.clone())
            .unwrap_or_default();
        folders
            .iter()
            .filter_map(|f| statuses.get(&f.id).cloned())
            .collect()
    }

    /// Store fresh statuses; returns true when anything changed (for emitting).
    fn upsert_statuses(&self, statuses: Vec<SyncFolderStatus>) -> bool {
        let mut guard = match self.statuses.write() {
            Ok(guard) => guard,
            Err(_) => return false,
        };
        let mut changed = false;
        for status in statuses {
            match guard.get(&status.folder_id) {
                Some(prev) if prev == &status => {}
                _ => {
                    changed = true;
                    guard.insert(status.folder_id.clone(), status);
                }
            }
        }
        changed
    }

    /// Run a sync pass for every folder, then emit `sync-status` if anything
    /// changed. Called by the worker and after manual triggers.
    pub async fn run_all(&self, app: &AppHandle) {
        let state = app.state::<AppState>();
        let accounts = state.accounts_snapshot();
        let mut statuses: Vec<SyncFolderStatus> = Vec::new();
        // Totals across all folders, used to decide whether a native
        // notification is warranted (Q1: no spam on every idle tick).
        let mut files_done: u64 = 0;
        let mut files_failed: u64 = 0;

        for folder in self.folders_snapshot() {
            let mut status = SyncFolderStatus {
                folder_id: folder.id.clone(),
                account_key: folder.account_key.clone(),
                local_path: folder.local_path.clone(),
                remote_path: folder.remote_path.clone(),
                paused: folder.paused,
                follow_symlinks: folder.follow_symlinks,
                state: "idle".into(),
                pending_uploads: 0,
                pending_downloads: 0,
                pending_deletes: 0,
                failures: 0,
                last_error: None,
                last_synced_at: None,
            };

            if folder.paused {
                status.state = "paused".into();
                statuses.push(status);
                continue;
            }

            let Some(account) = accounts
                .iter()
                .find(|account| account_key(account) == folder.account_key)
                .cloned()
            else {
                status.state = "error".into();
                status.last_error = Some(PassError {
                    code: "account_missing".into(),
                    detail: None,
                });
                status.failures = 1;
                statuses.push(status);
                continue;
            };

            status.state = "syncing".into();
            match run_pass(app, &state.http_client, &account, &folder).await {
                Ok(result) => {
                    files_done += result.done as u64;
                    files_failed += result.failures;
                    status.pending_uploads = result.planned_uploads;
                    status.pending_downloads = result.planned_downloads;
                    status.pending_deletes = result.planned_deletes;
                    status.failures = result.failures;
                    status.last_error = result.error.clone();
                    status.last_synced_at = Some(now_secs());
                    let remaining = result
                        .planned_uploads
                        .saturating_add(result.planned_downloads)
                        .saturating_add(result.planned_deletes)
                        .saturating_sub(result.done as u64);
                    status.state = if result.failures > 0 {
                        "error"
                    } else if remaining > 0 {
                        "syncing"
                    } else {
                        "idle"
                    }
                    .into();
                }
                Err(err) => {
                    status.state = "error".into();
                    status.failures = 1;
                    files_failed += 1;
                    status.last_error = Some(PassError::from_app_error(&err));
                }
            }
            statuses.push(status);
        }

        if self.upsert_statuses(statuses) {
            let _ = app.emit("sync-status", self.statuses());
        }

        // Q1 + L15-S3: native OS notification — only when a pass actually did
        // work (no idle spam), and failures are dampened: notify on the
        // ok→error transition, then re-remind only every NOTIFY_FAILURE_EVERY
        // consecutive failing pass.
        if files_failed > 0 {
            let was_failing = self.notifying_failure.load(Ordering::Relaxed);
            let streak = self.failure_streak.fetch_add(1, Ordering::Relaxed) + 1;
            if !was_failing || streak.is_multiple_of(NOTIFY_FAILURE_EVERY) {
                notify(
                    app,
                    "FlutLink Sync",
                    &format!("{files_failed} file(s) failed to sync."),
                );
            }
            self.notifying_failure.store(true, Ordering::Relaxed);
        } else {
            self.notifying_failure.store(false, Ordering::Relaxed);
            self.failure_streak.store(0, Ordering::Relaxed);
            if files_done > 0 {
                notify(
                    app,
                    "FlutLink Sync",
                    &format!("{files_done} file(s) synced successfully."),
                );
            }
        }
    }
}

/// Show a native OS notification via `tauri-plugin-notification` (Q1).
/// Best-effort: platform notification errors are deliberately ignored.
fn notify(app: &AppHandle, title: &str, body: &str) {
    let _ = app.notification().builder().title(title).body(body).show();
}

/// Background loop: wakes on notify or the interval, then runs all folders.
pub fn spawn_worker(app: &AppHandle) {
    let app = app.clone();
    tauri::async_runtime::spawn(async move {
        let mut interval = tokio::time::interval(Duration::from_secs(SYNC_INTERVAL_SECS));
        interval.set_missed_tick_behavior(tokio::time::MissedTickBehavior::Delay);
        loop {
            let engine = app.state::<AppState>().sync.clone();
            tokio::select! {
                _ = engine.notify.notified() => {}
                _ = interval.tick() => {}
            }
            engine.run_all(&app).await;
        }
    });
}

#[cfg(test)]
mod tests {
    use super::*;

    fn local(size: u64, mtime: i64) -> LocalEntry {
        LocalEntry {
            is_dir: false,
            size,
            mtime,
            mtime_nanos: 0,
        }
    }

    fn local_dir() -> LocalEntry {
        LocalEntry {
            is_dir: true,
            size: 0,
            mtime: 0,
            mtime_nanos: 0,
        }
    }

    fn remote_file(size: u64, mtime: i64) -> RemoteEntry {
        RemoteEntry {
            is_dir: false,
            size,
            mtime,
            etag: None,
        }
    }

    fn remote_dir() -> RemoteEntry {
        RemoteEntry {
            is_dir: true,
            size: 0,
            mtime: 0,
            etag: None,
        }
    }

    fn rec(local_size: u64, local_mtime: i64, remote_size: u64, remote_mtime: i64) -> JournalEntry {
        JournalEntry {
            local_size,
            local_mtime,
            remote_size,
            remote_mtime,
            is_dir: false,
            local_mtime_nanos: None,
            remote_etag: None,
        }
    }

    fn dir_rec() -> JournalEntry {
        JournalEntry {
            local_size: 0,
            local_mtime: 0,
            remote_size: 0,
            remote_mtime: 0,
            is_dir: true,
            local_mtime_nanos: None,
            remote_etag: None,
        }
    }

    /// Empty dirty-dir set for plan_ops calls in tests without hidden files.
    fn no_dirty() -> BTreeSet<String> {
        BTreeSet::new()
    }

    fn journal(entries: Vec<(&str, JournalEntry)>) -> Journal {
        entries
            .into_iter()
            .map(|(rel, entry)| (rel.to_string(), entry))
            .collect()
    }

    #[test]
    fn new_local_uploads() {
        let j = journal(vec![]);
        assert_eq!(
            decide("a.txt", Some(&local(10, 100)), None, &j),
            Action::Upload("a.txt".into())
        );
    }

    #[test]
    fn new_remote_downloads() {
        let j = journal(vec![]);
        assert_eq!(
            decide("b.txt", None, Some(&remote_file(20, 200)), &j),
            Action::Download("b.txt".into())
        );
    }

    #[test]
    fn both_unchanged_skips() {
        let j = journal(vec![("a.txt", rec(10, 100, 10, 100))]);
        assert_eq!(
            decide(
                "a.txt",
                Some(&local(10, 100)),
                Some(&remote_file(10, 100)),
                &j
            ),
            Action::Skip("a.txt".into())
        );
    }

    #[test]
    fn local_modified_uploads() {
        let j = journal(vec![("a.txt", rec(10, 100, 10, 100))]);
        assert_eq!(
            decide(
                "a.txt",
                Some(&local(12, 200)),
                Some(&remote_file(10, 100)),
                &j
            ),
            Action::Upload("a.txt".into())
        );
    }

    #[test]
    fn remote_modified_downloads() {
        let j = journal(vec![("a.txt", rec(10, 100, 10, 100))]);
        assert_eq!(
            decide(
                "a.txt",
                Some(&local(10, 100)),
                Some(&remote_file(14, 300)),
                &j
            ),
            Action::Download("a.txt".into())
        );
    }

    #[test]
    fn remote_deleted_propagates() {
        let j = journal(vec![("a.txt", rec(10, 100, 10, 100))]);
        assert_eq!(
            decide("a.txt", Some(&local(10, 100)), None, &j),
            Action::DeleteLocal("a.txt".into())
        );
    }

    #[test]
    fn local_deleted_propagates() {
        let j = journal(vec![("a.txt", rec(10, 100, 10, 100))]);
        assert_eq!(
            decide("a.txt", None, Some(&remote_file(10, 100)), &j),
            Action::DeleteRemote("a.txt".into())
        );
    }

    #[test]
    fn both_changed_conflicts() {
        let j = journal(vec![("a.txt", rec(10, 100, 10, 100))]);
        assert_eq!(
            decide(
                "a.txt",
                Some(&local(12, 200)),
                Some(&remote_file(14, 300)),
                &j
            ),
            Action::UploadConflict {
                rel: "a.txt".into(),
                target: "a (conflict copy).txt".into()
            }
        );
    }

    #[test]
    fn local_resurrects_after_remote_delete() {
        let j = journal(vec![("a.txt", rec(10, 100, 10, 100))]);
        assert_eq!(
            decide("a.txt", Some(&local(12, 200)), None, &j),
            Action::Upload("a.txt".into())
        );
    }

    #[test]
    fn conflict_name_keeps_extension() {
        assert_eq!(
            conflict_name("Photos/a.txt"),
            "Photos/a (conflict copy).txt"
        );
    }

    #[test]
    fn conflict_name_without_extension() {
        assert_eq!(conflict_name("README"), "README (conflict copy)");
    }

    #[test]
    fn should_skip_rel_filters_hidden_names_on_both_sides() {
        for hidden in [
            ".env",
            ".gitignore",
            ".env.example",
            "sub/.env",
            "sub/.gitignore",
            "~$report.docx",
            "sub/report~",
            "Thumbs.db",
            "sub/Thumbs.db",
        ] {
            assert!(should_skip_rel(hidden), "must skip: {}", hidden);
        }
        for visible in ["env", "sub/a.txt", "Report.docx", "thumbs.dbx"] {
            assert!(!should_skip_rel(visible), "must not skip: {}", visible);
        }
    }

    #[test]
    fn hidden_files_are_skipped_in_both_sync_directions() {
        let tmp = std::env::temp_dir().join(format!("flutlink-sync-test-{}", std::process::id()));
        let sub = tmp.join("sub");
        std::fs::create_dir_all(&sub).unwrap();
        std::fs::write(tmp.join(".env"), "secret").unwrap();
        std::fs::write(tmp.join(".gitignore"), "*").unwrap();
        std::fs::write(tmp.join("Thumbs.db"), "x").unwrap();
        std::fs::write(sub.join(".hidden"), "x").unwrap();
        std::fs::write(sub.join("ok.txt"), "x").unwrap();

        let rt = tokio::runtime::Runtime::new().unwrap();
        let walked = rt.block_on(walk_local(&tmp, false));
        let local_map = &walked.map;

        for rel in local_map.keys() {
            assert!(
                !should_skip_rel(rel),
                "walk_local leaked a hidden entry: {}",
                rel
            );
        }
        assert!(
            local_map.keys().any(|k| k == "sub/ok.txt"),
            "visible file must still be walked"
        );
        std::fs::remove_dir_all(&tmp).unwrap();
    }

    #[test]
    fn rel_below_strips_segment_boundaries() {
        assert_eq!(
            rel_below("FlutLink/MyFolder", "/FlutLink/MyFolder/sub/a.txt").as_deref(),
            Some("sub/a.txt")
        );
        assert_eq!(
            rel_below("FlutLink/MyFolder", "/FlutLink/MyFolder").as_deref(),
            Some("")
        );
        assert_eq!(
            rel_below("FlutLink", "/FlutLink2/a.txt"),
            None,
            "similar root must not match"
        );
        assert_eq!(
            rel_below("/", "/Photos/a.txt").as_deref(),
            Some("Photos/a.txt")
        );
    }

    #[test]
    fn plan_creates_parent_dirs() {
        let mut local_map = BTreeMap::new();
        local_map.insert("docs/deep/file.txt".into(), local(1, 1));
        let remote_map = BTreeMap::new();
        let ops = plan_ops(&local_map, &remote_map, &BTreeMap::new(), &no_dirty());
        assert!(
            ops.iter().any(|a| *a == Action::EnsureDir("docs".into())),
            "parent dir docs must be created"
        );
        assert!(
            ops.iter()
                .any(|a| *a == Action::EnsureDir("docs/deep".into())),
            "parent dir docs/deep must be created"
        );
        assert!(
            ops.iter()
                .position(|a| *a == Action::EnsureDir("docs".into()))
                .unwrap()
                < ops
                    .iter()
                    .position(|a| *a == Action::EnsureDir("docs/deep".into()))
                    .unwrap(),
            "parents must be created before children"
        );
        assert!(
            ops.iter()
                .position(|a| *a == Action::EnsureDir("docs/deep".into()))
                .unwrap()
                < ops
                    .iter()
                    .position(|a| *a == Action::Upload("docs/deep/file.txt".into()))
                    .unwrap(),
            "dirs must be created before uploads"
        );
    }

    #[test]
    fn empty_local_dir_is_created_remotely() {
        let mut local_map = BTreeMap::new();
        local_map.insert("empty".into(), local_dir());
        let ops = plan_ops(&local_map, &BTreeMap::new(), &BTreeMap::new(), &no_dirty());
        assert!(ops.iter().any(|a| *a == Action::EnsureDir("empty".into())));
    }

    #[test]
    fn remote_empty_dir_is_deleted_only_when_synced() {
        // First sync: never delete folders the client never synced.
        let mut remote_map = BTreeMap::new();
        remote_map.insert("docs".into(), remote_dir());
        let ops = plan_ops(&BTreeMap::new(), &remote_map, &BTreeMap::new(), &no_dirty());
        assert!(!ops.iter().any(|a| matches!(a, Action::DeleteRemoteDir(_))));

        // Synced folder deleted locally → the (empty) remote folder is removed.
        let ops = plan_ops(
            &BTreeMap::new(),
            &remote_map,
            &journal(vec![("docs", dir_rec())]),
            &no_dirty(),
        );
        assert!(ops
            .iter()
            .any(|a| *a == Action::DeleteRemoteDir("docs".into())));
    }

    #[test]
    fn folder_vs_remote_file_moves_remote_file() {
        let mut local_map = BTreeMap::new();
        local_map.insert("Photos".into(), local_dir());
        let mut remote_map = BTreeMap::new();
        remote_map.insert("Photos".into(), remote_file(10, 100));
        let ops = plan_ops(&local_map, &remote_map, &BTreeMap::new(), &no_dirty());
        assert!(ops.iter().any(|a| matches!(
            a,
            Action::MoveRemoteConflict { rel, target } if rel == "Photos" && target == "Photos (conflict copy)"
        )));
        assert!(
            ops.iter().any(|a| *a == Action::EnsureDir("Photos".into())),
            "the folder must be created at the original path"
        );
    }

    #[test]
    fn file_vs_remote_folder_moves_local_file() {
        let mut local_map = BTreeMap::new();
        local_map.insert("Report".into(), local(10, 100));
        let mut remote_map = BTreeMap::new();
        remote_map.insert("Report".into(), remote_dir());
        let ops = plan_ops(&local_map, &remote_map, &BTreeMap::new(), &no_dirty());
        assert!(ops.iter().any(|a| matches!(
            a,
            Action::MoveLocalConflict { rel, target } if rel == "Report" && target == "Report (conflict copy)"
        )));
    }

    #[test]
    fn move_local_conflict_skips_existing_local_conflict_copy() {
        let mut local_map = BTreeMap::new();
        local_map.insert("Report".into(), local(10, 100));
        local_map.insert("Report (conflict copy)".into(), local(20, 200));
        let mut remote_map = BTreeMap::new();
        remote_map.insert("Report".into(), remote_dir());
        let ops = plan_ops(&local_map, &remote_map, &BTreeMap::new(), &no_dirty());
        assert!(ops.iter().any(|a| matches!(
            a,
            Action::MoveLocalConflict { rel, target }
                if rel == "Report" && target == "Report (conflict copy 2)"
        )));
    }

    #[test]
    fn conflict_targets_are_unique_within_a_pass() {
        let mut local_map = BTreeMap::new();
        local_map.insert("a.txt".into(), local(12, 200));
        local_map.insert("b.txt".into(), local(13, 300));
        let mut remote_map = BTreeMap::new();
        remote_map.insert("a.txt".into(), remote_file(14, 400));
        remote_map.insert("b.txt".into(), remote_file(15, 500));
        remote_map.insert("a (conflict copy).txt".into(), remote_file(1, 1));
        let mut journal_map = journal(vec![
            ("a.txt", rec(10, 100, 10, 100)),
            ("b.txt", rec(10, 100, 10, 100)),
        ]);
        journal_map.insert("a (conflict copy).txt".into(), rec(1, 1, 1, 1));
        let ops = plan_ops(&local_map, &remote_map, &journal_map, &no_dirty());
        let targets: Vec<&String> = ops
            .iter()
            .filter_map(|a| match a {
                Action::UploadConflict { target, .. } => Some(target),
                _ => None,
            })
            .collect();
        assert_eq!(targets.len(), 2);
        assert!(
            targets.contains(&&"a (conflict copy 2).txt".to_string()),
            "the taken conflict-copy name must be skipped: {:?}",
            targets
        );
        assert!(targets.contains(&&"b (conflict copy).txt".to_string()));
    }

    // --- walk_local symlink behaviour -------------------------------------

    #[cfg(unix)]
    fn unique_temp_dir(tag: &str) -> std::path::PathBuf {
        let dir = std::env::temp_dir().join(format!(
            "flutlink-test-{}-{}-{}",
            tag,
            std::process::id(),
            std::time::SystemTime::now()
                .duration_since(std::time::UNIX_EPOCH)
                .unwrap()
                .as_nanos()
        ));
        std::fs::create_dir_all(&dir).unwrap();
        dir
    }

    #[cfg(unix)]
    #[tokio::test]
    async fn walk_local_skips_symlinks_by_default() {
        let root = unique_temp_dir("walk-skip");
        let outside = unique_temp_dir("walk-skip-target");
        std::fs::write(outside.join("outside.txt"), b"target").unwrap();
        std::os::unix::fs::symlink(outside.join("outside.txt"), root.join("link.txt")).unwrap();
        std::os::unix::fs::symlink(&outside, root.join("self-link")).unwrap();

        let map = &walk_local(&root, false).await.map;
        assert!(
            map.is_empty(),
            "symlinks are skipped: {:?}",
            map.keys().collect::<Vec<_>>()
        );
        std::fs::remove_dir_all(&root).unwrap();
        std::fs::remove_dir_all(&outside).unwrap();
    }

    #[cfg(unix)]
    #[tokio::test]
    async fn walk_local_follows_symlinks_when_enabled() {
        let root = unique_temp_dir("walk-follow");
        let outside = unique_temp_dir("walk-follow-target");
        let sub = root.join("sub");
        std::fs::create_dir(&sub).unwrap();
        std::fs::write(sub.join("real.txt"), b"real").unwrap();
        std::os::unix::fs::symlink(sub.join("real.txt"), root.join("alias.txt")).unwrap();

        // A directory outside the root, reachable through a symlink.
        std::fs::write(outside.join("out.txt"), b"out!").unwrap();
        std::os::unix::fs::symlink(&outside, root.join("link-dir")).unwrap();

        let map = &walk_local(&root, true).await.map;
        assert_eq!(map.get("sub"), Some(&local_dir()));
        assert_eq!(map.get("sub/real.txt").map(|e| e.size), Some(4));
        assert_eq!(map.get("alias.txt").map(|e| e.size), Some(4));
        assert_eq!(map.get("link-dir"), Some(&local_dir()));
        assert_eq!(map.get("link-dir/out.txt").map(|e| e.size), Some(4));
        std::fs::remove_dir_all(&root).unwrap();
        std::fs::remove_dir_all(&outside).unwrap();
    }

    #[cfg(unix)]
    #[tokio::test]
    async fn walk_local_following_terminates_on_symlink_cycle() {
        let root = unique_temp_dir("walk-cycle");
        let sub = root.join("sub");
        std::fs::create_dir(&sub).unwrap();
        // sub -> root forms a cycle; following it must terminate.
        std::os::unix::fs::symlink(&root, sub.join("back")).unwrap();
        std::fs::write(root.join("leaf.txt"), b"leaf").unwrap();

        let map = &walk_local(&root, true).await.map;
        assert_eq!(map.get("leaf.txt").map(|e| e.size), Some(4));
        assert_eq!(map.get("sub"), Some(&local_dir()));
        assert!(
            !map.contains_key("sub/back"),
            "a symlink loop back to the root is skipped"
        );
        std::fs::remove_dir_all(&root).unwrap();
    }

    // --- L15-S5: first-sync seeding ---------------------------------------

    #[test]
    fn first_sync_identical_file_seeds_journal() {
        let j = journal(vec![]);
        assert_eq!(
            decide(
                "a.txt",
                Some(&local(10, 100)),
                Some(&remote_file(10, 100)),
                &j
            ),
            Action::Seed("a.txt".into())
        );
    }

    #[test]
    fn first_sync_identical_folder_seeds_journal() {
        let j = journal(vec![]);
        assert_eq!(
            decide("docs", Some(&local_dir()), Some(&remote_dir()), &j),
            Action::Seed("docs".into())
        );
    }

    #[test]
    fn seeded_folder_deletion_propagates_to_remote() {
        let j = journal(vec![("docs", dir_rec())]);
        assert_eq!(
            decide("docs", None, Some(&remote_dir()), &j),
            Action::DeleteRemoteDir("docs".into())
        );
    }

    #[test]
    fn seeded_file_deletion_propagates_both_ways() {
        let j = journal(vec![("a.txt", rec(10, 100, 10, 100))]);
        assert_eq!(
            decide("a.txt", None, Some(&remote_file(10, 100)), &j),
            Action::DeleteRemote("a.txt".into())
        );
        assert_eq!(
            decide("a.txt", Some(&local(10, 100)), None, &j),
            Action::DeleteLocal("a.txt".into())
        );
    }

    // --- L15-S7: conflict-move ordering -----------------------------------

    #[test]
    fn move_remote_conflict_runs_before_ensure_dir_of_same_path() {
        let mut local_map = BTreeMap::new();
        local_map.insert("Photos".into(), local_dir());
        local_map.insert("Photos/a.txt".into(), local(1, 1));
        let mut remote_map = BTreeMap::new();
        remote_map.insert("Photos".into(), remote_file(10, 100));
        let ops = plan_ops(&local_map, &remote_map, &BTreeMap::new(), &no_dirty());
        let move_pos = ops
            .iter()
            .position(|a| matches!(a, Action::MoveRemoteConflict { rel, .. } if rel == "Photos"));
        let mkdir_pos = ops
            .iter()
            .position(|a| *a == Action::EnsureDir("Photos".into()));
        let (Some(move_pos), Some(mkdir_pos)) = (move_pos, mkdir_pos) else {
            panic!("expected MoveRemoteConflict and EnsureDir, got {ops:?}");
        };
        assert!(
            move_pos < mkdir_pos,
            "MOVE must run before MKCOL for the same path"
        );
    }

    // --- L15-S8: dirty dirs block folder deletion -------------------------

    #[test]
    fn delete_remote_dir_refused_when_listing_was_incomplete() {
        let mut remote_map = BTreeMap::new();
        remote_map.insert("docs".into(), remote_dir());
        let mut dirty = BTreeSet::new();
        dirty.insert("docs".to_string());
        let ops = plan_ops(
            &BTreeMap::new(),
            &remote_map,
            &journal(vec![("docs", dir_rec())]),
            &dirty,
        );
        assert!(
            !ops.iter().any(|a| matches!(a, Action::DeleteRemoteDir(_))),
            "folder with hidden children must not be deleted"
        );
    }

    #[test]
    fn delete_remote_dir_refused_when_subdirectory_is_dirty() {
        let mut remote_map = BTreeMap::new();
        remote_map.insert("docs".into(), remote_dir());
        let mut dirty = BTreeSet::new();
        dirty.insert("docs/sub".to_string());
        let ops = plan_ops(
            &BTreeMap::new(),
            &remote_map,
            &journal(vec![("docs", dir_rec())]),
            &dirty,
        );
        assert!(
            !ops.iter().any(|a| matches!(a, Action::DeleteRemoteDir(_))),
            "a dirty subfolder must also block the parent's deletion"
        );
    }

    #[test]
    fn clean_empty_folder_is_deleted_even_with_dirty_dirs_elsewhere() {
        let mut remote_map = BTreeMap::new();
        remote_map.insert("docs".into(), remote_dir());
        let mut dirty = BTreeSet::new();
        dirty.insert("other".to_string());
        let ops = plan_ops(
            &BTreeMap::new(),
            &remote_map,
            &journal(vec![("docs", dir_rec())]),
            &dirty,
        );
        assert!(ops
            .iter()
            .any(|a| *a == Action::DeleteRemoteDir("docs".into())));
    }

    // --- #279: journal atomicity / corruption recovery ---------------------

    #[test]
    fn corrupt_journal_is_quarantined_and_reset() {
        let dir = std::env::temp_dir().join(format!("flutlink-journal-{}", std::process::id()));
        std::fs::create_dir_all(&dir).unwrap();
        let path = dir.join("journal.json");
        std::fs::write(&path, "{not json at all").unwrap();

        let loaded = load_journal_from_disk(&path);
        assert!(loaded.is_empty(), "corrupt journal loads as empty");
        assert!(!path.exists(), "the corrupt file is moved out of the way");
        let quarantined: Vec<_> = std::fs::read_dir(&dir)
            .unwrap()
            .filter_map(Result::ok)
            .map(|e| e.file_name().to_string_lossy().to_string())
            .collect();
        assert!(
            quarantined
                .iter()
                .any(|n| n.starts_with("journal.json.corrupt-")),
            "the broken file must be kept for diagnosis: {quarantined:?}"
        );
        std::fs::remove_dir_all(&dir).unwrap();
    }

    #[test]
    fn interrupted_journal_write_leaves_previous_state_readable() {
        let dir = std::env::temp_dir().join(format!("flutlink-journal2-{}", std::process::id()));
        std::fs::create_dir_all(&dir).unwrap();
        let path = dir.join("journal.json");

        let mut state: Journal = BTreeMap::new();
        state.insert("keep.txt".into(), rec(1, 1, 1, 1));
        persist_journal_to_disk(&path, &state).unwrap();

        // A leftover temp file from a crashed write must not affect the load.
        std::fs::write(
            dir.join(format!("journal.tmp-{}", std::process::id())),
            "garbage",
        )
        .unwrap();

        let loaded = load_journal_from_disk(&path);
        assert_eq!(
            loaded.get("keep.txt").map(|e| e.local_size),
            Some(1),
            "previous state survives a crashed write"
        );

        // Rewriting replaces the old state atomically.
        let mut updated: Journal = BTreeMap::new();
        updated.insert("new.txt".into(), rec(2, 2, 2, 2));
        persist_journal_to_disk(&path, &updated).unwrap();
        let reloaded = load_journal_from_disk(&path);
        assert!(reloaded.contains_key("new.txt"));
        assert!(!reloaded.contains_key("keep.txt"));

        // Legacy journals without the new fields still parse (serde default).
        std::fs::write(
            &path,
            r#"{"old.txt":{"local_size":3,"local_mtime":30,"remote_size":3,"remote_mtime":30,"is_dir":false}}"#,
        )
        .unwrap();
        let migrated = load_journal_from_disk(&path);
        assert_eq!(migrated.get("old.txt").map(|e| e.local_size), Some(3));

        std::fs::remove_dir_all(&dir).unwrap();
    }

    // --- mtime comparison helpers ------------------------------------------

    #[test]
    fn nanosecond_precision_distinguishes_fast_edits() {
        let base = 1_700_000_000i64;
        let j = JournalEntry {
            local_size: 10,
            local_mtime: base,
            remote_size: 10,
            remote_mtime: base,
            is_dir: false,
            local_mtime_nanos: Some(base * 1_000_000_000 + 5),
            remote_etag: None,
        };
        let changed = LocalEntry {
            is_dir: false,
            size: 10,
            mtime: base,
            mtime_nanos: base * 1_000_000_000 + 9,
        };
        let same = LocalEntry {
            is_dir: false,
            size: 10,
            mtime: base,
            mtime_nanos: base * 1_000_000_000 + 5,
        };
        assert!(!local_unchanged(&j, &changed));
        assert!(local_unchanged(&j, &same));
    }

    #[test]
    fn legacy_journal_without_nanos_falls_back_to_seconds() {
        let base = 1_700_000_000i64;
        let j = JournalEntry {
            local_size: 10,
            local_mtime: base,
            remote_size: 10,
            remote_mtime: base,
            is_dir: false,
            local_mtime_nanos: None,
            remote_etag: None,
        };
        // Same second, unknown nanos → treated as unchanged (no churn loop).
        let entry = LocalEntry {
            is_dir: false,
            size: 10,
            mtime: base,
            mtime_nanos: base * 1_000_000_000 + 999,
        };
        assert!(local_unchanged(&j, &entry));
    }
}
