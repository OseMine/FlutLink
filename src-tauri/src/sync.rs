use std::collections::{BTreeMap, BTreeSet};
use std::path::{Path, PathBuf};
use std::sync::RwLock;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Emitter, Manager};
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
#[derive(Debug, Clone, Copy, Serialize, Deserialize)]
pub struct JournalEntry {
    pub local_size: u64,
    pub local_mtime: i64,
    pub remote_size: u64,
    pub remote_mtime: i64,
    /// Directory marker: entries for synced folders carry no sizes/mtimes.
    /// Defaulted so journals written before this field stay readable.
    #[serde(default)]
    pub is_dir: bool,
}

/// rel path (relative to the sync root, `/`-separated) → last synced state.
pub type Journal = BTreeMap<String, JournalEntry>;

#[derive(Debug, Clone, Copy)]
struct LocalEntry {
    is_dir: bool,
    size: u64,
    mtime: i64,
}

#[derive(Debug, Clone, Copy)]
struct RemoteEntry {
    is_dir: bool,
    size: u64,
    mtime: i64,
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
    used: &mut BTreeSet<String>,
) -> String {
    let mut n = 0u64;
    loop {
        n += 1;
        let candidate = conflict_name_n(rel, n);
        if !local.contains_key(&candidate)
            && !remote.contains_key(&candidate)
            && !used.contains(&candidate)
        {
            used.insert(candidate.clone());
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

/// Recursively collect local files below `root` as rel → (size, mtime).
async fn walk_local(root: &Path) -> BTreeMap<String, LocalEntry> {
    let mut map = BTreeMap::new();
    let mut stack = vec![root.to_path_buf()];
    while let Some(dir) = stack.pop() {
        let Ok(mut entries) = tokio::fs::read_dir(&dir).await else {
            continue;
        };
        while let Ok(Some(entry)) = entries.next_entry().await {
            let meta = match entry.metadata().await {
                Ok(meta) => meta,
                Err(_) => continue,
            };
            if meta.file_type().is_symlink() {
                continue;
            }
            let name = entry.file_name();
            let name_str = name.to_string_lossy();
            if should_skip_name(&name_str) {
                continue;
            }
            let path = entry.path();
            let rel = rel_from(root, &path);
            if meta.is_dir() {
                stack.push(path);
                map.insert(
                    rel,
                    LocalEntry {
                        is_dir: true,
                        size: 0,
                        mtime: 0,
                    },
                );
            } else if meta.is_file() {
                let mtime = meta
                    .modified()
                    .ok()
                    .and_then(|t| t.duration_since(UNIX_EPOCH).ok())
                    .map(|d| d.as_secs() as i64)
                    .unwrap_or(0);
                map.insert(
                    rel,
                    LocalEntry {
                        is_dir: false,
                        size: meta.len(),
                        mtime,
                    },
                );
            }
        }
    }
    map
}

fn rel_from(base: &Path, path: &Path) -> String {
    path.strip_prefix(base)
        .unwrap_or(path)
        .components()
        .filter_map(|c| c.as_os_str().to_str())
        .collect::<Vec<_>>()
        .join("/")
}

/// Recursively list the remote sync root (BFS with Depth-1 PROPFIND).
async fn list_remote(
    client: &reqwest::Client,
    account: &Account,
    root: &str,
) -> AppResult<BTreeMap<String, RemoteEntry>> {
    let mut map = BTreeMap::new();
    let mut pending = vec![root.trim_matches('/').to_string()];
    while let Some(dir) = pending.pop() {
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
                    },
                );
            }
        }
    }
    Ok(map)
}

/// Decide what to do with one path based on both sides and the journal.
fn decide(
    rel: &str,
    local: Option<&LocalEntry>,
    remote: Option<&RemoteEntry>,
    journal: &Journal,
) -> Action {
    let rec = journal.get(rel).copied();
    match (local, remote) {
        (Some(local), Some(remote)) => {
            // Directories never contain content themselves; the files decide.
            if local.is_dir && remote.is_dir {
                return Action::Skip(rel.to_string());
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
                let local_same = j.local_size == local.size && j.local_mtime == local.mtime;
                let remote_same = j.remote_size == remote.size && j.remote_mtime == remote.mtime;
                if local_same && remote_same {
                    return Action::Skip(rel.to_string());
                }
                if local_same {
                    return Action::Download(rel.to_string());
                }
                if remote_same {
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
            Action::Skip(rel.to_string())
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
) -> Vec<Action> {
    let mut rels: BTreeSet<String> = BTreeSet::new();
    for rel in local.keys().chain(remote.keys()).chain(journal.keys()) {
        rels.insert(rel.clone());
    }

    let mut used_targets: BTreeSet<String> = BTreeSet::new();
    let mut file_ops: Vec<Action> = Vec::new();
    let mut uploads: Vec<String> = Vec::new();
    let mut moved_dirs: Vec<String> = Vec::new();
    for rel in rels {
        let action = decide(&rel, local.get(&rel), remote.get(&rel), journal);
        match action {
            Action::Skip(_) => {}
            Action::Upload(p) => {
                used_targets.insert(p.clone());
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
    dir_ops.extend(file_ops);
    dir_ops
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

async fn exec_upload(ctx: &mut PassCtx<'_>, rel: &str) -> AppResult<()> {
    let local = *ctx
        .local
        .get(rel)
        .ok_or_else(|| AppError::App("local file disappeared during sync".into()))?;
    let local_path = ctx.local_root.join(rel);
    webdav::put_file(
        ctx.client,
        ctx.account,
        &remote_rel(ctx.folder, rel),
        &local_path,
        local.mtime,
    )
    .await?;
    ctx.journal.insert(
        rel.to_string(),
        JournalEntry {
            local_size: local.size,
            local_mtime: local.mtime,
            remote_size: local.size,
            remote_mtime: local.mtime,
            is_dir: false,
        },
    );
    Ok(())
}

async fn exec_upload_conflict(ctx: &mut PassCtx<'_>, rel: &str, target: &str) -> AppResult<()> {
    let local = *ctx
        .local
        .get(rel)
        .ok_or_else(|| AppError::App("local file disappeared during sync".into()))?;
    let remote = *ctx
        .remote
        .get(rel)
        .ok_or_else(|| AppError::App("remote file disappeared during sync".into()))?;
    let local_path = ctx.local_root.join(rel);
    webdav::put_file(
        ctx.client,
        ctx.account,
        &remote_rel(ctx.folder, target),
        &local_path,
        local.mtime,
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
        },
    );
    Ok(())
}

async fn exec_download(ctx: &mut PassCtx<'_>, rel: &str) -> AppResult<()> {
    let remote = *ctx
        .remote
        .get(rel)
        .ok_or_else(|| AppError::App("remote file disappeared during sync".into()))?;
    let local_path = ctx.local_root.join(rel);
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
    let local_mtime = meta
        .modified()
        .ok()
        .and_then(|t| t.duration_since(UNIX_EPOCH).ok())
        .map(|d| d.as_secs() as i64)
        .unwrap_or(0);
    ctx.journal.insert(
        rel.to_string(),
        JournalEntry {
            local_size: meta.len(),
            local_mtime,
            remote_size: remote.size,
            remote_mtime: remote.mtime,
            is_dir: false,
        },
    );
    Ok(())
}

async fn exec_delete_remote(ctx: &mut PassCtx<'_>, rel: &str) -> AppResult<()> {
    webdav::delete(ctx.client, ctx.account, &remote_rel(ctx.folder, rel)).await?;
    ctx.journal.remove(rel);
    Ok(())
}

async fn exec_delete_local(ctx: &mut PassCtx<'_>, rel: &str) -> AppResult<()> {
    let local_path = ctx.local_root.join(rel);
    if tokio::fs::try_exists(&local_path).await.unwrap_or(false) {
        tokio::fs::remove_file(&local_path).await?;
    }
    ctx.journal.remove(rel);
    Ok(())
}

async fn exec_mkdir(ctx: &mut PassCtx<'_>, rel: &str) -> AppResult<()> {
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
        },
    );
    Ok(())
}

/// Delete a remote folder (only scheduled for empty folders without remote
/// children). The journal entry is dropped together with the folder.
async fn exec_delete_remote_dir(ctx: &mut PassCtx<'_>, rel: &str) -> AppResult<()> {
    webdav::delete(ctx.client, ctx.account, &remote_rel(ctx.folder, rel)).await?;
    ctx.journal.remove(rel);
    Ok(())
}

/// Type conflict "local folder vs. remote file": move the remote file to a
/// conflict-copy name so the folder can be created at the original path.
async fn exec_move_remote_conflict(
    ctx: &mut PassCtx<'_>,
    rel: &str,
    target: &str,
) -> AppResult<()> {
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
        },
    );
    Ok(())
}

/// Type conflict "local file vs. remote folder": move the local file to a
/// conflict-copy name locally so the remote folder can be downloaded into the
/// original path.
async fn exec_move_local_conflict(ctx: &mut PassCtx<'_>, rel: &str, target: &str) -> AppResult<()> {
    let from = ctx.local_root.join(rel);
    let to = ctx.local_root.join(target);
    if let Some(parent) = to.parent() {
        tokio::fs::create_dir_all(parent).await?;
    }
    tokio::fs::rename(&from, &to).await?;
    // Record the remote state for the original path so it does not count as a
    // local deletion; the moved file is uploaded as a new file next pass.
    let remote = ctx.remote.get(rel).copied().unwrap_or(RemoteEntry {
        is_dir: false,
        size: 0,
        mtime: 0,
    });
    ctx.journal.insert(
        rel.to_string(),
        JournalEntry {
            local_size: 0,
            local_mtime: 0,
            remote_size: remote.size,
            remote_mtime: remote.mtime,
            is_dir: false,
        },
    );
    Ok(())
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
    let local = walk_local(&local_root).await;
    let remote = list_remote(client, account, &folder.remote_path).await?;
    let ops = plan_ops(&local, &remote, &journal);

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
                Action::Skip(_) => continue,
            };
            match result {
                Ok(()) => done += 1,
                Err(err) => {
                    failures += 1;
                    if error.is_none() {
                        error = Some(PassError::from_app_error(&err));
                    }
                }
            }
        }
    }

    prune_journal(&mut journal, &local, &remote);
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
}

impl Default for SyncEngine {
    fn default() -> Self {
        Self {
            folders: RwLock::new(Vec::new()),
            statuses: RwLock::new(BTreeMap::new()),
            notify: Notify::new(),
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
    let path = journal_file(app, folder_id)?;
    if !path.exists() {
        return Ok(BTreeMap::new());
    }
    let raw = std::fs::read_to_string(&path)?;
    serde_json::from_str(&raw).map_err(|e| AppError::Parse(e.to_string()))
}

fn persist_journal(app: &AppHandle, folder_id: &str, journal: &Journal) -> AppResult<()> {
    let json = serde_json::to_string_pretty(journal).map_err(|e| AppError::Parse(e.to_string()))?;
    std::fs::write(journal_file(app, folder_id)?, json)?;
    Ok(())
}

fn initial_status(folder: &SyncFolder) -> SyncFolderStatus {
    SyncFolderStatus {
        folder_id: folder.id.clone(),
        account_key: folder.account_key.clone(),
        local_path: folder.local_path.clone(),
        remote_path: folder.remote_path.clone(),
        paused: folder.paused,
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
    /// before the first worker pass.
    pub fn load(&self, app: &AppHandle) {
        let path = match folders_file(app) {
            Ok(path) => path,
            Err(_) => return,
        };
        if !path.exists() {
            return;
        }
        if let Ok(raw) = std::fs::read_to_string(&path) {
            if let Ok(folders) = serde_json::from_str::<Vec<SyncFolder>>(&raw) {
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
        }
    }

    pub fn persist(&self, app: &AppHandle) -> AppResult<()> {
        let json = serde_json::to_string_pretty(&self.folders_snapshot())
            .map_err(|e| AppError::Parse(e.to_string()))?;
        std::fs::write(folders_file(app)?, json)?;
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
            }
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

        for folder in self.folders_snapshot() {
            let mut status = SyncFolderStatus {
                folder_id: folder.id.clone(),
                account_key: folder.account_key.clone(),
                local_path: folder.local_path.clone(),
                remote_path: folder.remote_path.clone(),
                paused: folder.paused,
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
            // Ensure the cloud root exists (including parent chain); harmless
            // when it already does.
            let _ =
                webdav::ensure_collection(&state.http_client, &account, &folder.remote_path).await;

            match run_pass(app, &state.http_client, &account, &folder).await {
                Ok(result) => {
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
                    status.last_error = Some(PassError::from_app_error(&err));
                }
            }
            statuses.push(status);
        }

        if self.upsert_statuses(statuses) {
            let _ = app.emit("sync-status", self.statuses());
        }
    }
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
        }
    }

    fn local_dir() -> LocalEntry {
        LocalEntry {
            is_dir: true,
            size: 0,
            mtime: 0,
        }
    }

    fn remote_file(size: u64, mtime: i64) -> RemoteEntry {
        RemoteEntry {
            is_dir: false,
            size,
            mtime,
        }
    }

    fn remote_dir() -> RemoteEntry {
        RemoteEntry {
            is_dir: true,
            size: 0,
            mtime: 0,
        }
    }

    fn rec(local_size: u64, local_mtime: i64, remote_size: u64, remote_mtime: i64) -> JournalEntry {
        JournalEntry {
            local_size,
            local_mtime,
            remote_size,
            remote_mtime,
            is_dir: false,
        }
    }

    fn dir_rec() -> JournalEntry {
        JournalEntry {
            local_size: 0,
            local_mtime: 0,
            remote_size: 0,
            remote_mtime: 0,
            is_dir: true,
        }
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
        let local_map = rt.block_on(walk_local(&tmp));

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
        let ops = plan_ops(&local_map, &remote_map, &BTreeMap::new());
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
        let ops = plan_ops(&local_map, &BTreeMap::new(), &BTreeMap::new());
        assert!(ops.iter().any(|a| *a == Action::EnsureDir("empty".into())));
    }

    #[test]
    fn remote_empty_dir_is_deleted_only_when_synced() {
        // First sync: never delete folders the client never synced.
        let mut remote_map = BTreeMap::new();
        remote_map.insert("docs".into(), remote_dir());
        let ops = plan_ops(&BTreeMap::new(), &remote_map, &BTreeMap::new());
        assert!(!ops.iter().any(|a| matches!(a, Action::DeleteRemoteDir(_))));

        // Synced folder deleted locally → the (empty) remote folder is removed.
        let ops = plan_ops(
            &BTreeMap::new(),
            &remote_map,
            &journal(vec![("docs", dir_rec())]),
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
        let ops = plan_ops(&local_map, &remote_map, &BTreeMap::new());
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
        let ops = plan_ops(&local_map, &remote_map, &BTreeMap::new());
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
        let ops = plan_ops(&local_map, &remote_map, &BTreeMap::new());
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
        let ops = plan_ops(&local_map, &remote_map, &journal_map);
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
}
