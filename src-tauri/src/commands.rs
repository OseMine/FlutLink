use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::time::{SystemTime, UNIX_EPOCH};

use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Emitter, Manager, State};

use crate::accounts;
use crate::error::{AppError, AppResult};
use crate::nextcloud::{ocs, webdav};
use crate::state::{
    Account, AccountMeta, AppState, SyncFolder, SyncFolderStatus, TransferProgress, UserDetails,
    UserQuota, WebDavEntry,
};

fn to_meta_list(accounts: &[Account]) -> Vec<AccountMeta> {
    accounts.iter().map(|a| a.meta.clone()).collect()
}

fn current_account(state: &AppState) -> AppResult<Account> {
    state.current().ok_or(AppError::NoActiveAccount)
}

/// Returns the configured FlutCloud server URL (read from `FLUTCLOUD_URL` in
/// the local `.env` file). The frontend uses this so the URL never has to be
/// hard-coded in client code.
#[tauri::command]
pub fn get_flutcloud_url() -> AppResult<String> {
    crate::flutcloud::flutcloud_url()
}

#[tauri::command]
pub async fn account_add(
    app: AppHandle,
    state: State<'_, AppState>,
    instance_url: String,
    username: String,
    token: String,
) -> AppResult<AccountMeta> {
    let instance_url = crate::flutcloud::assert_flutcloud_url(&instance_url)?;
    let account = Account {
        meta: AccountMeta {
            username,
            instance_url,
            display_name: None,
            is_admin: false,
            is_active: false,
        },
        token,
    };

    // Refuse to connect to anything that is not a FlutCloud server running the
    // FlutCloud Nextcloud app.
    crate::flutcloud::verify_server(&state.http_client, &account).await?;

    let user = ocs::get_current_user(&state.http_client, &account).await?;
    let snapshot = state.accounts_snapshot();
    // Re-adding an already-known account keeps its active and admin state
    // (unless the server now reports otherwise) instead of silently resetting
    // them.
    let existing = snapshot.iter().find(|a| {
        a.meta.username == account.meta.username && a.meta.instance_url == account.meta.instance_url
    });
    let is_admin = match ocs::is_admin(&state.http_client, &account).await {
        Ok(is_admin) => is_admin,
        Err(err) => {
            // Transient probe failure: keep the previously stored flag so an
            // admin account is not demoted; the startup re-check fixes it later.
            eprintln!(
                "warn: could not determine admin status for {}@{}: {}",
                account.meta.username,
                account.meta.instance_url,
                err.message()
            );
            existing.map(|a| a.meta.is_admin).unwrap_or(false)
        }
    };

    let meta = AccountMeta {
        username: account.meta.username,
        instance_url: account.meta.instance_url,
        display_name: user.display_name,
        is_admin,
        is_active: existing
            .map(|a| a.meta.is_active)
            .unwrap_or(snapshot.is_empty()),
    };

    accounts::save_token(&meta, &account.token)?;
    let list = state.upsert(Account {
        meta: meta.clone(),
        token: account.token,
    });
    accounts::persist_accounts(&app, &list)?;
    crate::refresh_tray_menu(&app)?;
    Ok(meta)
}

#[tauri::command]
pub async fn account_list(state: State<'_, AppState>) -> AppResult<Vec<AccountMeta>> {
    Ok(to_meta_list(&state.accounts_snapshot()))
}

/// F8: information about accounts that were hidden during startup because they
/// point to a server other than the configured FlutCloud server. Returns `None`
/// when every saved account was loaded.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AccountFilterInfo {
    pub dropped_count: usize,
    pub server_url: Option<String>,
}

#[tauri::command]
pub fn account_filter_info(state: State<'_, AppState>) -> AppResult<Option<AccountFilterInfo>> {
    let dropped = state.filtered_accounts();
    if dropped.is_empty() {
        return Ok(None);
    }
    Ok(Some(AccountFilterInfo {
        dropped_count: dropped.len(),
        server_url: crate::flutcloud::flutcloud_url().ok(),
    }))
}

/// Project folder for the FlutCloud Nextcloud app, created during every
/// registration. Lives in the admin's files under `/FlutLink` so feature
/// requests and connection notes for the FlutLink desktop/mobile are
/// collected in one shared place.
const FLUTCLOUD_PROJECT_PATH: &str = "/FlutLink/FlutCloud";
const FLUTCLOUD_README: &str = r#"# FlutCloud — Nextcloud App

Shared project space of the **FlutCloud Nextcloud app**.

## Purpose
- Feature requests for the FlutCloud app and the FlutLink desktop client
- Connection notes between FlutCloud, FlutLink (desktop) and the upcoming
  FlutLink mobile app (not yet in development)

## Feature requests
Create one folder per request, e.g. `FR-001-share-links/`, containing a note
describing: what it should do, why (use case) and the expected behaviour.

## Connecting FlutLink
- Desktop client: https://github.com/OseMine/FlutLink
- Mobile app: not yet in development

---

# FlutCloud — Nextcloud App

Gemeinsamer Projektbereich der **FlutCloud-Nextcloud-App**.

## Zweck
- Feature-Requests für die FlutCloud-App und den FlutLink-Desktop-Client
- Verbindungsnotizen zwischen FlutCloud, FlutLink (Desktop) und der geplanten
  FlutLink-Mobile-App (noch nicht in Entwicklung)

## Feature-Requests
Lege pro Request einen Ordner an, z. B. `FR-001-share-links/`, mit einer
Notiz, die beschreibt: was passieren soll, warum (Anwendungsfall) und das
erwartete Verhalten.

## FlutLink verbinden
- Desktop-Client: https://github.com/OseMine/FlutLink
- Mobile-App: noch nicht in Entwicklung
"#;

/// Input for creating a real account via the register page.
#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RegisterUserInput {
    pub instance_url: String,
    pub username: String,
    pub password: String,
    pub display_name: Option<String>,
    pub admin_username: String,
    pub admin_password: String,
}

/// Register a real new account on the Nextcloud server (no email required).
///
/// Account creation uses the OCS Provisioning API, so the flutcloud admin
/// credentials are required once. After the account exists, the FlutCloud
/// project folder `/FlutLink/FlutCloud` is ensured in the admin's files and
/// the new account is signed in with its real password.
#[tauri::command]
pub async fn register_user(
    app: AppHandle,
    state: State<'_, AppState>,
    input: RegisterUserInput,
) -> AppResult<AccountMeta> {
    let instance_url = crate::flutcloud::assert_flutcloud_url(&input.instance_url)?;

    let admin = Account {
        meta: AccountMeta {
            username: input.admin_username,
            instance_url: instance_url.clone(),
            display_name: None,
            is_admin: true,
            is_active: false,
        },
        token: input.admin_password,
    };

    // Refuse to register on anything that is not a FlutCloud server running the
    // FlutCloud Nextcloud app.
    crate::flutcloud::verify_server(&state.http_client, &admin).await?;

    // Create the real account. Fails with an OCS error if the admin
    // credentials are invalid or the username is already taken.
    ocs::create_user(
        &state.http_client,
        &admin,
        &input.username,
        &input.password,
        input.display_name.as_deref(),
    )
    .await?;

    // Project folder for the FlutCloud app in the admin's files. Creating it is
    // best-effort: account registration must not fail because the admin's
    // storage is read-only, full, or otherwise refuses the write.
    if let Err(err) =
        webdav::ensure_collection(&state.http_client, &admin, FLUTCLOUD_PROJECT_PATH).await
    {
        eprintln!(
            "warn: could not ensure {}: {}",
            FLUTCLOUD_PROJECT_PATH,
            err.message()
        );
    }
    if let Err(err) = webdav::put_text(
        &state.http_client,
        &admin,
        &format!("{}/README.md", FLUTCLOUD_PROJECT_PATH),
        FLUTCLOUD_README,
    )
    .await
    {
        eprintln!("warn: could not write project README: {}", err.message());
    }

    // Sign the new account in with its real password.
    let account = Account {
        meta: AccountMeta {
            username: input.username,
            instance_url,
            display_name: input.display_name,
            is_admin: false,
            is_active: false,
        },
        token: input.password,
    };
    let user = ocs::get_current_user(&state.http_client, &account).await?;
    let snapshot = state.accounts_snapshot();
    let existing = snapshot.iter().find(|a| {
        a.meta.username == account.meta.username && a.meta.instance_url == account.meta.instance_url
    });
    let is_admin = match ocs::is_admin(&state.http_client, &account).await {
        Ok(is_admin) => is_admin,
        Err(err) => {
            // Transient probe failure: keep the previously stored flag so an
            // admin account is not demoted; the startup re-check fixes it later.
            eprintln!(
                "warn: could not determine admin status for {}@{}: {}",
                account.meta.username,
                account.meta.instance_url,
                err.message()
            );
            existing.map(|a| a.meta.is_admin).unwrap_or(false)
        }
    };

    let meta = AccountMeta {
        username: account.meta.username,
        instance_url: account.meta.instance_url,
        display_name: user.display_name,
        is_admin,
        is_active: existing
            .map(|a| a.meta.is_active)
            .unwrap_or(snapshot.is_empty()),
    };

    accounts::save_token(&meta, &account.token)?;
    let list = state.upsert(Account {
        meta: meta.clone(),
        token: account.token,
    });
    accounts::persist_accounts(&app, &list)?;
    crate::refresh_tray_menu(&app)?;
    Ok(meta)
}

#[tauri::command]
pub async fn account_active(state: State<'_, AppState>) -> AppResult<Option<AccountMeta>> {
    Ok(state.current().map(|a| a.meta))
}

#[tauri::command]
pub async fn account_switch(
    app: AppHandle,
    state: State<'_, AppState>,
    username: String,
    instance_url: String,
) -> AppResult<AccountMeta> {
    let account = state
        .set_active(&username, &instance_url)
        .ok_or_else(|| AppError::NotFound(format!("{}@{}", username, instance_url)))?;
    accounts::persist_accounts(&app, &state.accounts_snapshot())?;
    crate::refresh_tray_menu(&app)?;
    // Mirror the tray path (lib.rs): the frontend store reloads on this event,
    // keeping the is_active flags of every account in sync.
    let _ = app.emit("accounts-changed", ());
    Ok(account.meta)
}

#[tauri::command]
pub async fn account_remove(
    app: AppHandle,
    state: State<'_, AppState>,
    username: String,
    instance_url: String,
) -> AppResult<Vec<AccountMeta>> {
    let snapshot = state.accounts_snapshot();
    let target = snapshot
        .iter()
        .find(|a| a.meta.username == username && a.meta.instance_url == instance_url)
        .ok_or_else(|| AppError::NotFound(format!("{}@{}", username, instance_url)))?;
    // The keyring token must be deleted; swallowing the error would leave a
    // stale token behind that no account can ever use again.
    accounts::delete_token(&target.meta)?;
    // Also remove the sync folders of the removed account so a re-added
    // account with the same name does not keep syncing stale folders.
    state.sync.remove_folders_for_account(&app, &target.meta)?;
    let list = state.remove(&target.meta.username, &target.meta.instance_url);
    accounts::persist_accounts(&app, &list)?;
    crate::refresh_tray_menu(&app)?;
    Ok(to_meta_list(&list))
}

/// Re-evaluate the admin flag of every stored account against the server.
///
/// Runs once at app start: the stored flag is only overwritten when the OCS
/// probe succeeds, so a transient network error never demotes an admin account
/// to a regular one (the previous flag is kept). Persists and notifies the
/// frontend/tray only when something actually changed.
pub async fn refresh_admin_flags(app: &AppHandle) {
    let state = app.state::<AppState>();
    let mut changed = false;
    for account in state.accounts_snapshot() {
        match ocs::is_admin(&state.http_client, &account).await {
            Ok(is_admin) => {
                changed |= state.set_is_admin(
                    &account.meta.username,
                    &account.meta.instance_url,
                    is_admin,
                );
            }
            Err(err) => eprintln!(
                "warn: could not re-check admin status of {}@{}: {}",
                account.meta.username,
                account.meta.instance_url,
                err.message()
            ),
        }
    }
    if changed {
        if let Err(err) = accounts::persist_accounts(app, &state.accounts_snapshot()) {
            eprintln!(
                "warn: could not persist refreshed admin flags: {}",
                err.message()
            );
        }
        if let Err(err) = crate::refresh_tray_menu(app) {
            eprintln!("warn: could not refresh tray menu: {}", err.message());
        }
        let _ = app.emit("accounts-changed", ());
    }
}

#[tauri::command]
pub async fn webdav_list(
    state: State<'_, AppState>,
    path: Option<String>,
    target_user: Option<String>,
) -> AppResult<Vec<WebDavEntry>> {
    let account = current_account(&state)?;
    let path = path.unwrap_or_else(|| "/".into());
    let target = target_user.filter(|t| !t.trim().is_empty() && t != &account.meta.username);
    if target.is_some() && !account.meta.is_admin {
        return Err(AppError::Forbidden);
    }
    webdav::list(&state.http_client, &account, &path, target.as_deref()).await
}

/// Storage quota of the currently active account (from the OCS v2 user endpoint).
#[tauri::command]
pub async fn account_storage(state: State<'_, AppState>) -> AppResult<Option<UserQuota>> {
    let account = current_account(&state)?;
    ocs::get_current_quota(&state.http_client, &account).await
}

/// Create a public link share for the given file/folder and return the URL.
///
/// Admins may share files of another user by passing `target_user`; the
/// `Impersonate-User` header is used so the share is attributed to the admin.
#[tauri::command]
pub async fn webdav_create_share(
    state: State<'_, AppState>,
    path: String,
    target_user: Option<String>,
) -> AppResult<String> {
    let account = current_account(&state)?;
    let target = target_user.filter(|t| !t.trim().is_empty() && t != &account.meta.username);
    if target.is_some() && !account.meta.is_admin {
        return Err(AppError::Forbidden);
    }
    validate_dav_path(&path)?;
    ocs::create_share(&state.http_client, &account, &path, target.as_deref()).await
}

/// Reject paths that are not absolute, escape the user's home (`..`) or target
/// the FlutCloud virtual namespaces (`resources`/`parts`), which are managed by
/// the server app and must not be modified through the client.
fn validate_dav_path(path: &str) -> AppResult<()> {
    if !path.starts_with('/') {
        return Err(AppError::App(
            "Path must be absolute (start with '/').".into(),
        ));
    }
    for segment in path.split('/') {
        if segment == ".." {
            return Err(AppError::App("Path must not contain '..'.".into()));
        }
        if segment.eq_ignore_ascii_case("resources") || segment.eq_ignore_ascii_case("parts") {
            return Err(AppError::App(
                "The virtual 'resources'/'parts' folders cannot be modified.".into(),
            ));
        }
    }
    Ok(())
}

/// New absolute WebDAV path for renaming `path` to `new_name`, keeping the
/// parent directory (with its leading slash) intact:
/// `/Documents/report.pdf` → `/Documents/neu.pdf`, `/report.pdf` → `/neu.pdf`.
fn rename_new_path(path: &str, new_name: &str) -> String {
    let parent = path
        .rsplit_once('/')
        .map(|(parent, _)| if parent.is_empty() { "/" } else { parent })
        .unwrap_or("/");
    if parent == "/" {
        format!("/{}", new_name)
    } else {
        format!("{}/{}", parent, new_name)
    }
}

/// Upload a local file to the cloud at `remote_path` (absolute, decoded path
/// relative to the user's files root, e.g. `/Documents/report.pdf`).
#[tauri::command]
pub async fn webdav_upload_file(
    app: AppHandle,
    state: State<'_, AppState>,
    remote_path: String,
    local_path: String,
    target_user: Option<String>,
) -> AppResult<()> {
    let account = current_account(&state)?;
    validate_dav_path(&remote_path)?;
    let target = target_user.filter(|t| !t.trim().is_empty() && t != &account.meta.username);
    if target.is_some() && !account.meta.is_admin {
        return Err(AppError::Forbidden);
    }
    let mtime = std::fs::metadata(&local_path)
        .ok()
        .and_then(|m| m.modified().ok())
        .and_then(|t| t.duration_since(UNIX_EPOCH).ok())
        .map(|d| d.as_secs() as i64)
        .unwrap_or(0);
    let progress = transfer_progress(app, "upload", &remote_path, 0, 1);
    webdav::put_file_as_progress(
        &state.http_client,
        &account,
        &remote_path,
        std::path::Path::new(&local_path),
        mtime,
        target.as_deref(),
        Some(progress),
    )
    .await
}

/// Download a cloud file at `remote_path` to `local_path`.
#[tauri::command]
pub async fn webdav_download_file(
    app: AppHandle,
    state: State<'_, AppState>,
    remote_path: String,
    local_path: String,
    target_user: Option<String>,
) -> AppResult<()> {
    let account = current_account(&state)?;
    validate_dav_path(&remote_path)?;
    let target = target_user.filter(|t| !t.trim().is_empty() && t != &account.meta.username);
    if target.is_some() && !account.meta.is_admin {
        return Err(AppError::Forbidden);
    }
    if let Some(parent) = std::path::Path::new(&local_path).parent() {
        std::fs::create_dir_all(parent)?;
    }
    let progress = transfer_progress(app, "download", &remote_path, 0, 1);
    webdav::get_file_as_progress(
        &state.http_client,
        &account,
        &remote_path,
        std::path::Path::new(&local_path),
        target.as_deref(),
        Some(progress),
    )
    .await
}

/// Delete a cloud file or folder.
#[tauri::command]
pub async fn webdav_delete(
    state: State<'_, AppState>,
    path: String,
    target_user: Option<String>,
) -> AppResult<()> {
    let account = current_account(&state)?;
    validate_dav_path(&path)?;
    let target = target_user.filter(|t| !t.trim().is_empty() && t != &account.meta.username);
    if target.is_some() && !account.meta.is_admin {
        return Err(AppError::Forbidden);
    }
    webdav::delete_as(&state.http_client, &account, &path, target.as_deref()).await
}

/// Build a progress callback that emits `file://progress` events for the given
/// file. `index`/`total_files` describe the position of the file within the
/// whole bulk operation.
fn transfer_progress(
    app: AppHandle,
    direction: &str,
    path: &str,
    index: u64,
    total_files: u64,
) -> webdav::ProgressFn {
    let app = app.clone();
    let direction = direction.to_string();
    let path = path.to_string();
    Arc::new(move |transferred, total| {
        let percent = if total > 0 {
            transferred as f64 / total as f64 * 100.0
        } else {
            0.0
        };
        let _ = app.emit(
            "file://progress",
            TransferProgress {
                direction: direction.clone(),
                path: path.clone(),
                index,
                total_files,
                transferred,
                total,
                percent,
            },
        );
    })
}

/// A selected entry for bulk operations.
#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct BulkTarget {
    pub path: String,
    pub is_dir: bool,
}

/// Delete multiple cloud files/folders. Emits `file://progress` events with
/// `direction = "delete"` and a per-file count.
#[tauri::command]
pub async fn webdav_bulk_delete(
    app: AppHandle,
    state: State<'_, AppState>,
    paths: Vec<String>,
    target_user: Option<String>,
) -> AppResult<()> {
    let account = current_account(&state)?;
    let target = target_user.filter(|t| !t.trim().is_empty() && t != &account.meta.username);
    if target.is_some() && !account.meta.is_admin {
        return Err(AppError::Forbidden);
    }
    let total = paths.len() as u64;
    for (i, path) in paths.iter().enumerate() {
        validate_dav_path(path)?;
        webdav::delete_as(&state.http_client, &account, path, target.as_deref()).await?;
        let progress = transfer_progress(app.clone(), "delete", path, i as u64, total);
        progress(i as u64 + 1, total);
    }
    Ok(())
}

/// Shared context for recursive tree transfers (bulk download/upload).
#[derive(Clone)]
struct TransferCtx<'a> {
    app: AppHandle,
    state: &'a AppState,
    account: &'a Account,
    target: Option<&'a str>,
    index: u64,
    total_files: u64,
}

impl TransferCtx<'_> {
    fn next(self) -> Self {
        Self {
            index: self.index + 1,
            ..self
        }
    }
}

/// Recursively download a remote tree into `dest` (relative remote path
/// preserving its structure under `dest`). Returns the number of files written.
async fn download_tree(ctx: TransferCtx<'_>, remote_rel: &str, dest: &Path) -> AppResult<u64> {
    std::fs::create_dir_all(dest)?;
    let entries = webdav::list(&ctx.state.http_client, ctx.account, remote_rel, ctx.target).await?;
    let mut files_written = 0u64;
    for entry in &entries {
        let local = dest.join(&entry.name);
        if entry.is_dir {
            files_written += Box::pin(download_tree(ctx.clone(), &entry.path, &local)).await?;
        } else {
            let progress = transfer_progress(
                ctx.app.clone(),
                "download",
                &entry.path,
                ctx.index,
                ctx.total_files,
            );
            webdav::get_file_as_progress(
                &ctx.state.http_client,
                ctx.account,
                &entry.path,
                &local,
                ctx.target,
                Some(progress),
            )
            .await?;
            files_written += 1;
        }
    }
    Ok(files_written)
}

/// Download multiple cloud files/folders into `dest_dir`, preserving the
/// folder structure. Emits `file://progress` events per file.
#[tauri::command]
pub async fn webdav_bulk_download(
    app: AppHandle,
    state: State<'_, AppState>,
    targets: Vec<BulkTarget>,
    dest_dir: String,
    target_user: Option<String>,
) -> AppResult<()> {
    let account = current_account(&state)?;
    let target = target_user.filter(|t| !t.trim().is_empty() && t != &account.meta.username);
    if target.is_some() && !account.meta.is_admin {
        return Err(AppError::Forbidden);
    }
    let dest = PathBuf::from(&dest_dir);
    let total_files = targets.len() as u64;
    for t in &targets {
        validate_dav_path(&t.path)?;
    }
    let mut ctx = TransferCtx {
        app: app.clone(),
        state: &state,
        account: &account,
        target: target.as_deref(),
        index: 0,
        total_files,
    };
    for t in &targets {
        let local = dest.join(
            t.path
                .rsplit('/')
                .next()
                .unwrap_or_default()
                .trim_matches('/'),
        );
        if t.is_dir {
            download_tree(ctx.clone(), &t.path, &local).await?;
        } else {
            std::fs::create_dir_all(&dest)?;
            let progress =
                transfer_progress(app.clone(), "download", &t.path, ctx.index, total_files);
            webdav::get_file_as_progress(
                &state.http_client,
                &account,
                &t.path,
                &local,
                target.as_deref(),
                Some(progress),
            )
            .await?;
        }
        ctx = ctx.next();
    }
    Ok(())
}

/// Recursively upload a local tree into a remote folder, returning the number
/// of files uploaded.
async fn upload_tree(ctx: TransferCtx<'_>, local: &Path, remote_rel: &str) -> AppResult<u64> {
    let mut files_written = 0u64;
    let mut entries = tokio::fs::read_dir(local).await?;
    while let Some(entry) = entries.next_entry().await? {
        let path = entry.path();
        let name = entry.file_name().to_string_lossy().into_owned();
        let remote = format!("{}/{}", remote_rel.trim_end_matches('/'), name);
        if path.is_dir() {
            webdav::ensure_collection_as(&ctx.state.http_client, ctx.account, &remote, ctx.target)
                .await?;
            files_written += Box::pin(upload_tree(ctx.clone(), &path, &remote)).await?;
        } else {
            let mtime = std::fs::metadata(&path)
                .ok()
                .and_then(|m| m.modified().ok())
                .and_then(|t| t.duration_since(UNIX_EPOCH).ok())
                .map(|d| d.as_secs() as i64)
                .unwrap_or(0);
            let progress = transfer_progress(
                ctx.app.clone(),
                "upload",
                &remote,
                ctx.index,
                ctx.total_files,
            );
            webdav::put_file_as_progress(
                &ctx.state.http_client,
                ctx.account,
                &remote,
                &path,
                mtime,
                ctx.target,
                Some(progress),
            )
            .await?;
            files_written += 1;
        }
    }
    Ok(files_written)
}

/// Upload multiple local files/folders (e.g. from drag & drop) into the given
/// remote directory, recursively for local subfolders. Emits
/// `file://progress` events per file.
#[tauri::command]
pub async fn webdav_upload_local_paths(
    app: AppHandle,
    state: State<'_, AppState>,
    local_paths: Vec<String>,
    remote_dir: String,
    target_user: Option<String>,
) -> AppResult<()> {
    let account = current_account(&state)?;
    validate_dav_path(&remote_dir)?;
    let target = target_user.filter(|t| !t.trim().is_empty() && t != &account.meta.username);
    if target.is_some() && !account.meta.is_admin {
        return Err(AppError::Forbidden);
    }
    let mut total_files = 0u64;
    for p in &local_paths {
        let path = PathBuf::from(p);
        if path.is_dir() {
            total_files += count_files(&path);
        } else if path.is_file() {
            total_files += 1;
        }
    }
    let mut ctx = TransferCtx {
        app: app.clone(),
        state: &state,
        account: &account,
        target: target.as_deref(),
        index: 0,
        total_files,
    };
    for p in &local_paths {
        let path = PathBuf::from(p);
        let name = path
            .file_name()
            .map(|n| n.to_string_lossy().into_owned())
            .unwrap_or_default();
        let remote = format!("{}/{}", remote_dir.trim_end_matches('/'), name);
        if path.is_dir() {
            webdav::ensure_collection_as(&state.http_client, &account, &remote, target.as_deref())
                .await?;
            upload_tree(ctx.clone(), &path, &remote).await?;
        } else if path.is_file() {
            let mtime = std::fs::metadata(&path)
                .ok()
                .and_then(|m| m.modified().ok())
                .and_then(|t| t.duration_since(UNIX_EPOCH).ok())
                .map(|d| d.as_secs() as i64)
                .unwrap_or(0);
            let progress =
                transfer_progress(app.clone(), "upload", &remote, ctx.index, total_files);
            webdav::put_file_as_progress(
                &state.http_client,
                &account,
                &remote,
                &path,
                mtime,
                target.as_deref(),
                Some(progress),
            )
            .await?;
        }
        ctx = ctx.next();
    }
    Ok(())
}

fn count_files(path: &Path) -> u64 {
    let mut count = 0u64;
    if let Ok(entries) = std::fs::read_dir(path) {
        for entry in entries.flatten() {
            let p = entry.path();
            if p.is_dir() {
                count += count_files(&p);
            } else {
                count += 1;
            }
        }
    }
    count
}

/// Create a cloud folder (with all missing parents).
#[tauri::command]
pub async fn webdav_mkdir(
    state: State<'_, AppState>,
    path: String,
    target_user: Option<String>,
) -> AppResult<()> {
    let account = current_account(&state)?;
    validate_dav_path(&path)?;
    let target = target_user.filter(|t| !t.trim().is_empty() && t != &account.meta.username);
    if target.is_some() && !account.meta.is_admin {
        return Err(AppError::Forbidden);
    }
    webdav::ensure_collection_as(&state.http_client, &account, &path, target.as_deref()).await
}

/// Rename/move a cloud file or folder.
#[tauri::command]
pub async fn webdav_rename(
    state: State<'_, AppState>,
    path: String,
    new_name: String,
    target_user: Option<String>,
) -> AppResult<()> {
    let account = current_account(&state)?;
    validate_dav_path(&path)?;
    let target = target_user.filter(|t| !t.trim().is_empty() && t != &account.meta.username);
    if target.is_some() && !account.meta.is_admin {
        return Err(AppError::Forbidden);
    }
    let new_path = rename_new_path(&path, &new_name);
    validate_dav_path(&new_path)?;
    webdav::rename_as(
        &state.http_client,
        &account,
        &path,
        &new_path,
        target.as_deref(),
    )
    .await
}

#[tauri::command]
pub async fn admin_list_users(
    state: State<'_, AppState>,
    search: Option<String>,
) -> AppResult<Vec<String>> {
    let account = current_account(&state)?;
    if !account.meta.is_admin {
        return Err(AppError::Forbidden);
    }
    ocs::list_users(
        &state.http_client,
        &account,
        search.as_deref().unwrap_or(""),
    )
    .await
}

#[tauri::command]
pub async fn admin_get_user(state: State<'_, AppState>, user_id: String) -> AppResult<UserDetails> {
    let account = current_account(&state)?;
    if !account.meta.is_admin {
        return Err(AppError::Forbidden);
    }
    ocs::get_user(&state.http_client, &account, &user_id).await
}

#[tauri::command]
pub async fn admin_set_user_quota(
    state: State<'_, AppState>,
    user_id: String,
    quota: String,
) -> AppResult<String> {
    let account = current_account(&state)?;
    if !account.meta.is_admin {
        return Err(AppError::Forbidden);
    }
    ocs::update_user(&state.http_client, &account, &user_id, "quota", &quota).await
}

#[tauri::command]
pub async fn admin_create_user(
    state: State<'_, AppState>,
    user_id: String,
    password: String,
    display_name: Option<String>,
) -> AppResult<String> {
    let account = current_account(&state)?;
    if !account.meta.is_admin {
        return Err(AppError::Forbidden);
    }
    ocs::create_user(
        &state.http_client,
        &account,
        &user_id,
        &password,
        display_name.as_deref(),
    )
    .await
}

#[tauri::command]
pub async fn admin_delete_user(state: State<'_, AppState>, user_id: String) -> AppResult<String> {
    let account = current_account(&state)?;
    if !account.meta.is_admin {
        return Err(AppError::Forbidden);
    }
    ocs::delete_user(&state.http_client, &account, &user_id).await
}

/// Allowed attribute keys for `admin_edit_user`. Anything else is refused so
/// the admin UI cannot accidentally corrupt server-side settings.
const ADMIN_EDIT_KEYS: &[&str] = &[
    "displayname",
    "email",
    "quota",
    "password",
    "language",
    "locale",
    "enabled",
];

#[tauri::command]
pub async fn admin_edit_user(
    state: State<'_, AppState>,
    user_id: String,
    key: String,
    value: String,
) -> AppResult<String> {
    let account = current_account(&state)?;
    if !account.meta.is_admin {
        return Err(AppError::Forbidden);
    }
    if !ADMIN_EDIT_KEYS.contains(&key.as_str()) {
        return Err(AppError::App(format!(
            "Editing key '{}' is not allowed.",
            key
        )));
    }
    ocs::update_user(&state.http_client, &account, &user_id, &key, &value).await
}

fn now_nanos() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_nanos() as u64)
        .unwrap_or(0)
}

/// All sync folders of every account, with live status.
#[tauri::command]
pub async fn sync_list(state: State<'_, AppState>) -> AppResult<Vec<SyncFolderStatus>> {
    Ok(state.sync.statuses())
}

/// Add a local folder to the two-way sync of the active account.
#[tauri::command]
pub async fn sync_add(
    app: AppHandle,
    state: State<'_, AppState>,
    local_path: String,
) -> AppResult<SyncFolderStatus> {
    let account = current_account(&state)?;
    let local = PathBuf::from(&local_path);
    if !local.is_dir() {
        return Err(AppError::App(
            "Selected folder does not exist or is not a directory.".into(),
        ));
    }
    let canonical = local
        .canonicalize()
        .map_err(|e| AppError::App(format!("Cannot resolve folder path: {}", e)))?;
    let name = canonical
        .file_name()
        .map(|n| n.to_string_lossy().into_owned())
        .filter(|n| !n.is_empty())
        .unwrap_or_else(|| "FlutLink".into());
    let folder = SyncFolder {
        id: format!("{:016x}", now_nanos()),
        account_key: crate::sync::account_key(&account),
        local_path: canonical.to_string_lossy().into_owned(),
        remote_path: format!("/FlutLink/{}", name),
        paused: false,
    };
    // Two folders with the same remote path for one account would overwrite
    // each other's remote data; refuse duplicates before anything is written.
    if let Some(existing) = state
        .sync
        .folders_snapshot()
        .iter()
        .find(|f| f.account_key == folder.account_key && f.remote_path == folder.remote_path)
    {
        return Err(AppError::SyncFolderConflict {
            local_path: existing.local_path.clone(),
            remote_path: existing.remote_path.clone(),
        });
    }
    let status = state.sync.add_folder(&app, folder)?;
    state.sync.notify_one();
    Ok(status)
}

/// Remove a sync folder (keeps all local and remote files untouched).
#[tauri::command]
pub async fn sync_remove(
    app: AppHandle,
    state: State<'_, AppState>,
    folder_id: String,
) -> AppResult<()> {
    state.sync.remove_folder(&app, &folder_id)?;
    Ok(())
}

/// Pause or resume a sync folder.
#[tauri::command]
pub async fn sync_set_paused(
    app: AppHandle,
    state: State<'_, AppState>,
    folder_id: String,
    paused: bool,
) -> AppResult<()> {
    state.sync.set_paused(&app, &folder_id, paused)?;
    Ok(())
}

/// Ask the worker to run a pass now.
#[tauri::command]
pub async fn sync_trigger(state: State<'_, AppState>) -> AppResult<()> {
    state.sync.notify_one();
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn validate_dav_path_accepts_absolute_paths() {
        assert!(validate_dav_path("/Documents/report.pdf").is_ok());
        assert!(validate_dav_path("/report.pdf").is_ok());
        assert!(validate_dav_path("/").is_ok());
    }

    #[test]
    fn validate_dav_path_rejects_relative_and_escapes() {
        assert!(
            validate_dav_path("Documents/neu.pdf").is_err(),
            "must start with '/'"
        );
        assert!(
            validate_dav_path("/../etc/passwd").is_err(),
            "must not contain '..'"
        );
        assert!(
            validate_dav_path("/Resources/x").is_err(),
            "virtual folders are protected"
        );
        assert!(
            validate_dav_path("/parts/x").is_err(),
            "virtual folders are protected"
        );
    }

    #[test]
    fn rename_new_path_keeps_subfolder_prefix() {
        assert_eq!(
            rename_new_path("/Documents/report.pdf", "neu.pdf"),
            "/Documents/neu.pdf"
        );
        assert_eq!(
            rename_new_path("/Documents/Sub/invoice.txt", "final.txt"),
            "/Documents/Sub/final.txt"
        );
    }

    #[test]
    fn rename_new_path_keeps_root_slash() {
        assert_eq!(rename_new_path("/report.pdf", "neu.pdf"), "/neu.pdf");
    }
}
