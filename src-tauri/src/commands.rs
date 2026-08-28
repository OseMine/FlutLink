use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::time::{SystemTime, UNIX_EPOCH};

use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Emitter, Manager, State};
use tauri_plugin_opener::OpenerExt;

use crate::accounts;
use crate::error::{AppError, AppResult};
use crate::history;
use crate::nextcloud::{ocs, webdav};
use crate::state::{
    Account, AccountMeta, AdminUsersResult, AppState, Share, StorageResult, SyncFolder,
    SyncFolderStatus, TransferProgress, UserDetails, WebDavEntry, WebDavListResult,
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
/// point to a server other than the configured FlutCloud server, plus accounts
/// whose keyring token could not be restored. Returns `None` when every saved
/// account was loaded.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AccountFilterInfo {
    pub dropped_count: usize,
    pub server_url: Option<String>,
    /// `user@instance_url` of accounts whose keyring token is missing.
    #[serde(default)]
    pub token_missing: Vec<String>,
}

#[tauri::command]
pub fn account_filter_info(state: State<'_, AppState>) -> AppResult<Option<AccountFilterInfo>> {
    let dropped = state.filtered_accounts();
    let token_missing = state.token_missing_accounts();
    if dropped.is_empty() && token_missing.is_empty() {
        return Ok(None);
    }
    Ok(Some(AccountFilterInfo {
        dropped_count: dropped.len(),
        server_url: crate::flutcloud::flutcloud_url().ok(),
        token_missing,
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
- Feature requests for the FlutCloud app and the FlutLink desktop and mobile
  clients (Kotlin Multiplatform)
- Connection notes between FlutCloud, FlutLink (desktop) and the FlutLink
  mobile client (Android/iOS, `kmp/`)

## Feature requests
Create one folder per request, e.g. `FR-001-share-links/`, containing a note
describing: what it should do, why (use case) and the expected behaviour.

## Connecting FlutLink
- Desktop client: https://github.com/OseMine/FlutLink
- Mobile client (Kotlin Multiplatform, `kmp/`): https://github.com/OseMine/FlutLink

---

# FlutCloud — Nextcloud App

Gemeinsamer Projektbereich der **FlutCloud-Nextcloud-App**.

## Zweck
- Feature-Requests für die FlutCloud-App sowie den FlutLink-Desktop- und
  Mobile-Client (Kotlin Multiplatform)
- Verbindungsnotizen zwischen FlutCloud, FlutLink (Desktop) und dem
  FlutLink-Mobile-Client (Android/iOS, `kmp/`)

## Feature-Requests
Lege pro Request einen Ordner an, z. B. `FR-001-share-links/`, mit einer
Notiz, die beschreibt: was passieren soll, warum (Anwendungsfall) und das
erwartete Verhalten.

## FlutLink verbinden
- Desktop-Client: https://github.com/OseMine/FlutLink
- Mobile-Client (Kotlin Multiplatform, `kmp/`): https://github.com/OseMine/FlutLink
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

/// Offline-cache namespace: the browsed user + the server instance, so listings
/// and quotas of different accounts/users never collide.
fn cache_namespace(account: &Account, target_user: Option<&str>) -> String {
    format!(
        "{}@{}",
        target_user.unwrap_or(&account.meta.username),
        account.meta.instance_url
    )
}

#[tauri::command]
pub async fn webdav_list(
    app: AppHandle,
    state: State<'_, AppState>,
    path: Option<String>,
    target_user: Option<String>,
) -> AppResult<WebDavListResult> {
    let account = current_account(&state)?;
    let path = path.unwrap_or_else(|| "/".into());
    let target = target_user.filter(|t| !t.trim().is_empty() && t != &account.meta.username);
    if target.is_some() && !account.meta.is_admin {
        return Err(AppError::Forbidden);
    }
    let namespace = cache_namespace(&account, target.as_deref());
    match webdav::list(&state.http_client, &account, &path, target.as_deref()).await {
        Ok(entries) => {
            // Refresh the offline cache so the browser can show the latest
            // listing when the server becomes unreachable later.
            if let Err(err) = crate::cache::save_listing(&app, &namespace, &path, &entries) {
                eprintln!("warn: could not cache listing {}: {}", path, err.message());
            }
            Ok(WebDavListResult {
                entries,
                stale: false,
            })
        }
        Err(err) if err.is_network() => {
            // Server unreachable: serve the last cached listing instead of an
            // empty folder/error. Only report the error when nothing was cached.
            if let Some(entries) = crate::cache::load_listing(&app, &namespace, &path)? {
                Ok(WebDavListResult {
                    entries,
                    stale: true,
                })
            } else {
                Err(err)
            }
        }
        Err(err) => Err(err),
    }
}

/// Search the active account's whole files tree for entries whose name
/// contains `query` (WebDAV-SEARCH). Admins may search another user's files
/// via `target_user`.
#[tauri::command]
pub async fn webdav_search(
    state: State<'_, AppState>,
    query: String,
    target_user: Option<String>,
) -> AppResult<Vec<WebDavEntry>> {
    let account = current_account(&state)?;
    let target = target_user.filter(|t| !t.trim().is_empty() && t != &account.meta.username);
    if target.is_some() && !account.meta.is_admin {
        return Err(AppError::Forbidden);
    }
    let query = query.trim();
    if query.is_empty() {
        return Ok(Vec::new());
    }
    webdav::search(&state.http_client, &account, query, target.as_deref()).await
}

/// Storage quota of the currently active account (from the OCS v2 user endpoint).
///
/// The quota is persisted in the offline cache and served from there when the
/// server is unreachable (instead of failing or showing "unavailable").
#[tauri::command]
pub async fn account_storage(
    app: AppHandle,
    state: State<'_, AppState>,
) -> AppResult<StorageResult> {
    let account = current_account(&state)?;
    let namespace = cache_namespace(&account, None);
    match ocs::get_current_quota(&state.http_client, &account).await {
        Ok(quota) => {
            if let Some(quota) = &quota {
                if let Err(err) = crate::cache::save_quota(&app, &namespace, quota) {
                    eprintln!("warn: could not cache quota: {}", err.message());
                }
            }
            Ok(StorageResult {
                quota,
                stale: false,
            })
        }
        Err(err) if err.is_network() => Ok(StorageResult {
            quota: crate::cache::load_quota(&app, &namespace)?,
            stale: true,
        }),
        Err(err) => Err(err),
    }
}

/// Create a share for the given file/folder and return the created share.
///
/// Defaults to a read-only public link. Passing `options.share_type`
/// (0 = user, 1 = group, 3 = link) together with `options.share_with` creates
/// a private share; `password`/`expire_date`/`public_upload` configure link
/// options.
///
/// Admins may share files of another user by passing `target_user`; the
/// `Impersonate-User` header is used so the share is attributed to that user.
#[derive(Debug, Clone, Default, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ShareInput {
    /// OCS shareType: 0 = user, 1 = group, 3 = link. Defaults to 3.
    pub share_type: Option<u32>,
    /// Recipient for user/group shares (username or group name).
    pub share_with: Option<String>,
    /// Password protecting a public link.
    pub password: Option<String>,
    /// Expiry date as `YYYY-MM-DD`.
    pub expire_date: Option<String>,
    /// Allow uploads to a public link.
    pub public_upload: Option<bool>,
}

#[tauri::command]
pub async fn webdav_create_share(
    state: State<'_, AppState>,
    path: String,
    target_user: Option<String>,
    options: Option<ShareInput>,
) -> AppResult<Share> {
    let account = current_account(&state)?;
    validate_writable_dav_path(&path)?;
    let target = target_user.filter(|t| !t.trim().is_empty() && t != &account.meta.username);
    if target.is_some() && !account.meta.is_admin {
        return Err(AppError::Forbidden);
    }
    let options = options.unwrap_or_default();
    let share_type = options.share_type.unwrap_or(3);
    let share_with = options.share_with.filter(|s| !s.trim().is_empty());
    if share_type < 3 && share_with.is_none() {
        return Err(AppError::App(
            "A user or group share requires a recipient (shareWith).".into(),
        ));
    }
    let password = options.password.filter(|p| !p.is_empty());
    let expire_date = options.expire_date.filter(|d| !d.is_empty());
    let opts = ocs::ShareOptions {
        share_type,
        share_with: share_with.as_deref(),
        password: password.as_deref(),
        expire_date: expire_date.as_deref(),
        permissions: None,
        public_upload: options.public_upload.unwrap_or(false),
    };
    ocs::create_share(&state.http_client, &account, &path, target.as_deref(), opts).await
}

/// List the shares of the active account. With `path` only the shares of that
/// path are returned, otherwise all shares.
#[tauri::command]
pub async fn webdav_list_shares(
    state: State<'_, AppState>,
    path: Option<String>,
    target_user: Option<String>,
) -> AppResult<Vec<Share>> {
    let account = current_account(&state)?;
    let target = target_user.filter(|t| !t.trim().is_empty() && t != &account.meta.username);
    if target.is_some() && !account.meta.is_admin {
        return Err(AppError::Forbidden);
    }
    if let Some(p) = &path {
        validate_dav_path(p)?;
    }
    ocs::list_shares(
        &state.http_client,
        &account,
        path.as_deref(),
        target.as_deref(),
    )
    .await
}

/// Revoke a share by id.
#[tauri::command]
pub async fn webdav_delete_share(
    state: State<'_, AppState>,
    share_id: u64,
    target_user: Option<String>,
) -> AppResult<()> {
    let account = current_account(&state)?;
    let target = target_user.filter(|t| !t.trim().is_empty() && t != &account.meta.username);
    if target.is_some() && !account.meta.is_admin {
        return Err(AppError::Forbidden);
    }
    ocs::delete_share(&state.http_client, &account, share_id, target.as_deref()).await
}

/// #406: change password, expiry date or permissions of an existing share.
///
/// `password`/`expire_date` at `null` leave the value untouched; an empty
/// string removes it. `public_upload` (link shares) toggles the permission
/// bits (15) without touching either text field.
#[derive(Debug, Clone, Default, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ShareUpdateInput {
    /// New password; empty string clears an existing password.
    pub password: Option<String>,
    /// New expiry date as `YYYY-MM-DD`; empty string clears it.
    pub expire_date: Option<String>,
    /// Allow uploads to a public link.
    pub public_upload: Option<bool>,
}

#[tauri::command]
pub async fn webdav_update_share(
    state: State<'_, AppState>,
    share_id: u64,
    target_user: Option<String>,
    update: Option<ShareUpdateInput>,
) -> AppResult<()> {
    let account = current_account(&state)?;
    let target = target_user.filter(|t| !t.trim().is_empty() && t != &account.meta.username);
    if target.is_some() && !account.meta.is_admin {
        return Err(AppError::Forbidden);
    }
    let update = update.unwrap_or_default();
    let permissions = update.public_upload.map(|allow| if allow { 15 } else { 1 });
    let opts = ocs::ShareUpdate {
        password: update.password.as_deref(),
        expire_date: update.expire_date.as_deref(),
        permissions,
    };
    ocs::update_share(
        &state.http_client,
        &account,
        share_id,
        target.as_deref(),
        &opts,
    )
    .await
}

/// Reject paths that are not absolute or escape the user's root (`..`).
///
/// This base check applies to every WebDAV command, read and write alike:
/// browsing/opening/downloading inside the FlutCloud virtual namespaces
/// (`resources`/`parts`) is legitimate — `webdav_list` serves their entries
/// with `isResource`/`isPart` flags — so only modifications are refused via
/// [`validate_writable_dav_path`] (L17-F2).
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
    }
    Ok(())
}

/// Additionally reject the FlutCloud virtual namespaces (`resources`/`parts`)
/// for write access: they are managed by the server app and must not be
/// modified through the client. Applied to all writing commands only.
fn validate_writable_dav_path(path: &str) -> AppResult<()> {
    validate_dav_path(path)?;
    for segment in path.split('/') {
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

/// Reject rename targets that would silently turn a rename into a move or a
/// path traversal: the new name must be a single name (no `/`) and must not be
/// `.`, `..` or empty. Validated directly on the name, not on the composed
/// path, so `/` cannot slip through as a subfolder separator.
fn validate_rename_name(new_name: &str) -> AppResult<()> {
    if new_name.is_empty() || new_name == "." || new_name == ".." || new_name.contains('/') {
        return Err(AppError::App(
            "The new name must be a plain name without '/', '.' or '..'.".into(),
        ));
    }
    Ok(())
}

/// Upload a local file to the cloud at `remote_path` (absolute, decoded path
/// relative to the user's files root, e.g. `/Documents/report.pdf`).
///
/// Without `overwrite`, an existing destination is refused with
/// [`AppError::TargetExists`] instead of being silently replaced.
#[tauri::command]
pub async fn webdav_upload_file(
    app: AppHandle,
    state: State<'_, AppState>,
    remote_path: String,
    local_path: String,
    target_user: Option<String>,
    overwrite: bool,
) -> AppResult<()> {
    let account = current_account(&state)?;
    validate_writable_dav_path(&remote_path)?;
    let target = target_user.filter(|t| !t.trim().is_empty() && t != &account.meta.username);
    if target.is_some() && !account.meta.is_admin {
        return Err(AppError::Forbidden);
    }
    if !overwrite
        && webdav::exists(
            &state.http_client,
            &account,
            &remote_path,
            target.as_deref(),
        )
        .await?
    {
        return Err(AppError::TargetExists(remote_path.clone()));
    }
    let mtime = std::fs::metadata(&local_path)
        .ok()
        .and_then(|m| m.modified().ok())
        .and_then(|t| t.duration_since(UNIX_EPOCH).ok())
        .map(|d| d.as_secs() as i64)
        .unwrap_or(0);
    let progress = transfer_progress(app, "upload", &remote_path, 0, 1);
    // TOCTOU guard: with overwrite=false the PUT itself carries
    // `If-None-Match: *` (chunked uploads: `Overwrite: F` on the MOVE), so a
    // file created between the exists() check and the upload is never
    // silently replaced — the server answers 412 → `AppError::TargetExists`.
    webdav::put_file_params(
        &state.http_client,
        &account,
        webdav::PutParams {
            remote_rel: &remote_path,
            local_path: std::path::Path::new(&local_path),
            mtime_secs: mtime,
            target_user: target.as_deref(),
            on_progress: Some(progress),
            if_match: None,
            forbid_overwrite: !overwrite,
        },
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

/// Download `remote_path` into the dedicated open-cache directory (a subdir of
/// the system temp dir) and open it with the default application. Files from
/// previous opens are removed first (best-effort), so the temp directory never
/// grows with every opened file.
#[tauri::command]
pub async fn open_remote_file(
    app: AppHandle,
    state: State<'_, AppState>,
    remote_path: String,
    target_user: Option<String>,
) -> AppResult<()> {
    let account = current_account(&state)?;
    validate_dav_path(&remote_path)?;
    let target = target_user.filter(|t| !t.trim().is_empty() && t != &account.meta.username);
    if target.is_some() && !account.meta.is_admin {
        return Err(AppError::Forbidden);
    }

    let cache_dir = open_cache_dir();
    // Clean up leftovers from previous open operations (best-effort).
    cleanup_open_cache();
    std::fs::create_dir_all(&cache_dir)?;

    let file_name = Path::new(&remote_path)
        .file_name()
        .and_then(|name| name.to_str())
        .filter(|name| !name.is_empty())
        .unwrap_or("file");
    let local_path = cache_dir.join(file_name);

    let progress = transfer_progress(app.clone(), "download", &remote_path, 0, 1);
    webdav::get_file_as_progress(
        &state.http_client,
        &account,
        &remote_path,
        &local_path,
        target.as_deref(),
        Some(progress),
    )
    .await?;

    app.opener()
        .open_path(local_path.to_string_lossy().to_string(), None::<&str>)
        .map_err(|e| AppError::App(e.to_string()))?;
    history::record_open(&app, &remote_path);
    Ok(())
}

/// #427: recently opened remote files, newest first.
#[tauri::command]
pub fn file_history_list(app: AppHandle) -> AppResult<Vec<history::FileHistoryEntry>> {
    history::load(&app)
}

/// #427: clear the "recently opened" history.
#[tauri::command]
pub fn file_history_clear(app: AppHandle) -> AppResult<()> {
    history::clear(&app)
}

/// #410: toggle whether the backend announces newly appeared shares.
#[tauri::command]
pub fn set_share_notify(app: AppHandle, enabled: bool) -> AppResult<()> {
    let mut settings = crate::settings::load(&app);
    settings.share_notify_enabled = enabled;
    crate::settings::save(&app, &settings)
}

/// Download a cloud folder as a ZIP archive (Nextcloud WebDAV extension) to
/// `local_path`.
#[tauri::command]
pub async fn webdav_download_zip(
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
    webdav::download_zip_as(
        &state.http_client,
        &account,
        &remote_path,
        std::path::Path::new(&local_path),
        target.as_deref(),
        Some(progress),
    )
    .await
}

/// Fetch a preview thumbnail for a file as a base64 `data:` URL (Nextcloud
/// `/core/preview.png`). Returns `None` when the server has no preview.
#[tauri::command]
pub async fn webdav_thumbnail(
    state: State<'_, AppState>,
    path: String,
    size: Option<u32>,
    target_user: Option<String>,
) -> AppResult<Option<String>> {
    use base64::engine::general_purpose::STANDARD;
    use base64::Engine as _;

    let account = current_account(&state)?;
    validate_dav_path(&path)?;
    let target = target_user.filter(|t| !t.trim().is_empty() && t != &account.meta.username);
    if target.is_some() && !account.meta.is_admin {
        return Err(AppError::Forbidden);
    }
    let size = size.unwrap_or(256).clamp(16, 1024);
    let Some(preview) =
        webdav::preview(&state.http_client, &account, &path, size, target.as_deref()).await?
    else {
        return Ok(None);
    };
    let data = STANDARD.encode(preview.bytes);
    Ok(Some(format!(
        "data:{};base64,{}",
        preview.content_type, data
    )))
}

/// Delete a cloud file or folder.
#[tauri::command]
pub async fn webdav_delete(
    state: State<'_, AppState>,
    path: String,
    target_user: Option<String>,
) -> AppResult<()> {
    let account = current_account(&state)?;
    validate_writable_dav_path(&path)?;
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
    // L19-F2: validate every path up front — validating inside the delete
    // loop let a protected path in the middle of the selection abort the
    // bulk delete with the earlier paths already gone (partial deletion).
    for path in &paths {
        validate_writable_dav_path(path)?;
    }
    let total = paths.len() as u64;
    for (i, path) in paths.iter().enumerate() {
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

/// Map a cloud path (`/a/b/c.txt`) to a local path under `dest`, preserving
/// the relative directory structure (`dest/a/b/c.txt`). Equal-named leaves
/// from different folders no longer collide on disk.
fn bulk_local_path(dest: &Path, path: &str) -> PathBuf {
    let mut out = dest.to_path_buf();
    for segment in path.trim_start_matches('/').split('/') {
        if !segment.is_empty() {
            out = out.join(segment);
        }
    }
    out
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
        let local = bulk_local_path(&dest, &t.path);
        if t.is_dir {
            download_tree(ctx.clone(), &t.path, &local).await?;
        } else {
            if let Some(parent) = local.parent() {
                std::fs::create_dir_all(parent)?;
            }
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
/// of files uploaded. Without `overwrite`, existing remote files abort the
/// upload with [`AppError::TargetExists`] instead of being silently replaced.
async fn upload_tree(
    ctx: TransferCtx<'_>,
    local: &Path,
    remote_rel: &str,
    overwrite: bool,
) -> AppResult<u64> {
    let mut files_written = 0u64;
    let mut entries = tokio::fs::read_dir(local).await?;
    while let Some(entry) = entries.next_entry().await? {
        let path = entry.path();
        let name = entry.file_name().to_string_lossy().into_owned();
        let remote = format!("{}/{}", remote_rel.trim_end_matches('/'), name);
        if path.is_dir() {
            webdav::ensure_collection_as(&ctx.state.http_client, ctx.account, &remote, ctx.target)
                .await?;
            files_written += Box::pin(upload_tree(ctx.clone(), &path, &remote, overwrite)).await?;
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
            webdav::put_file_params(
                &ctx.state.http_client,
                ctx.account,
                webdav::PutParams {
                    remote_rel: &remote,
                    local_path: std::path::Path::new(&path),
                    mtime_secs: mtime,
                    target_user: ctx.target,
                    on_progress: Some(progress),
                    if_match: None,
                    forbid_overwrite: !overwrite,
                },
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
///
/// Without `overwrite`, existing remote files abort the upload with
/// [`AppError::TargetExists`] instead of being silently replaced.
#[tauri::command]
pub async fn webdav_upload_local_paths(
    app: AppHandle,
    state: State<'_, AppState>,
    local_paths: Vec<String>,
    remote_dir: String,
    target_user: Option<String>,
    overwrite: bool,
) -> AppResult<()> {
    let account = current_account(&state)?;
    validate_writable_dav_path(&remote_dir)?;
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
            upload_tree(ctx.clone(), &path, &remote, overwrite).await?;
        } else if path.is_file() {
            let mtime = std::fs::metadata(&path)
                .ok()
                .and_then(|m| m.modified().ok())
                .and_then(|t| t.duration_since(UNIX_EPOCH).ok())
                .map(|d| d.as_secs() as i64)
                .unwrap_or(0);
            let progress =
                transfer_progress(app.clone(), "upload", &remote, ctx.index, total_files);
            webdav::put_file_params(
                &state.http_client,
                &account,
                webdav::PutParams {
                    remote_rel: &remote,
                    local_path: std::path::Path::new(&path),
                    mtime_secs: mtime,
                    target_user: target.as_deref(),
                    on_progress: Some(progress),
                    if_match: None,
                    forbid_overwrite: !overwrite,
                },
            )
            .await?;
        }
        ctx = ctx.next();
    }
    Ok(())
}

fn count_files(path: &Path) -> u64 {
    count_files_inner(path, &mut std::collections::HashSet::new())
}

fn count_files_inner(
    path: &Path,
    visited: &mut std::collections::HashSet<std::path::PathBuf>,
) -> u64 {
    // Resolve symlinks and skip already-visited directories to prevent loops.
    let canonical = path.canonicalize().unwrap_or_else(|_| path.to_path_buf());
    if !visited.insert(canonical) {
        return 0;
    }
    let mut count = 0u64;
    if let Ok(entries) = std::fs::read_dir(path) {
        for entry in entries.flatten() {
            let p = entry.path();
            if p.is_dir() {
                count += count_files_inner(&p, visited);
            } else {
                count += 1;
            }
        }
    }
    count
}

/// Create a single cloud folder at `path`. Only the leaf collection is
/// created: the leaf name is validated like [`validate_rename_name`], so a
/// name like `a/b` cannot silently materialize parent folders along the way
/// (the parent of `path` is expected to already exist).
#[tauri::command]
pub async fn webdav_mkdir(
    state: State<'_, AppState>,
    path: String,
    target_user: Option<String>,
) -> AppResult<()> {
    let account = current_account(&state)?;
    validate_writable_dav_path(&path)?;
    validate_rename_name(mkdir_leaf_name(&path))?;
    let target = target_user.filter(|t| !t.trim().is_empty() && t != &account.meta.username);
    if target.is_some() && !account.meta.is_admin {
        return Err(AppError::Forbidden);
    }
    webdav::make_collection_as(&state.http_client, &account, &path, target.as_deref()).await
}

/// Leaf segment of an absolute path, e.g. `/Documents/neu` → `neu`. Used by
/// [`webdav_mkdir`] so the created name is validated as a single plain
/// segment instead of being treated as a chain of folders.
fn mkdir_leaf_name(path: &str) -> &str {
    path.rsplit('/').find(|s| !s.is_empty()).unwrap_or_default()
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
    validate_writable_dav_path(&path)?;
    validate_rename_name(&new_name)?;
    let target = target_user.filter(|t| !t.trim().is_empty() && t != &account.meta.username);
    if target.is_some() && !account.meta.is_admin {
        return Err(AppError::Forbidden);
    }
    let new_path = rename_new_path(&path, &new_name);
    validate_writable_dav_path(&new_path)?;
    webdav::rename_as(
        &state.http_client,
        &account,
        &path,
        &new_path,
        target.as_deref(),
    )
    .await
}

/// Destination path for a copy/move into `dest_folder` (#411): the source's
/// file name is appended verbatim, so moving `/A/report.pdf` into `/B` targets
/// `/B/report.pdf`.
fn move_dest_path(source: &str, dest_folder: &str) -> String {
    let name = source
        .rsplit_once('/')
        .map(|(_, name)| name)
        .unwrap_or(source);
    if dest_folder == "/" {
        format!("/{}", name)
    } else {
        format!("{}/{}", dest_folder, name)
    }
}

/// Copy a cloud file or folder into `dest_folder` via WebDAV COPY (#411).
#[tauri::command]
pub async fn webdav_copy(
    state: State<'_, AppState>,
    source: String,
    dest_folder: String,
    target_user: Option<String>,
) -> AppResult<()> {
    let account = current_account(&state)?;
    validate_writable_dav_path(&source)?;
    validate_writable_dav_path(&dest_folder)?;
    let target = target_user.filter(|t| !t.trim().is_empty() && t != &account.meta.username);
    if target.is_some() && !account.meta.is_admin {
        return Err(AppError::Forbidden);
    }
    let dest = move_dest_path(&source, &dest_folder);
    validate_writable_dav_path(&dest)?;
    webdav::copy_as(
        &state.http_client,
        &account,
        &source,
        &dest,
        target.as_deref(),
    )
    .await
}

/// Move a cloud file or folder into `dest_folder` via WebDAV MOVE (#411).
#[tauri::command]
pub async fn webdav_move(
    state: State<'_, AppState>,
    source: String,
    dest_folder: String,
    target_user: Option<String>,
) -> AppResult<()> {
    let account = current_account(&state)?;
    validate_writable_dav_path(&source)?;
    validate_writable_dav_path(&dest_folder)?;
    let target = target_user.filter(|t| !t.trim().is_empty() && t != &account.meta.username);
    if target.is_some() && !account.meta.is_admin {
        return Err(AppError::Forbidden);
    }
    let dest = move_dest_path(&source, &dest_folder);
    validate_writable_dav_path(&dest)?;
    webdav::rename_as(
        &state.http_client,
        &account,
        &source,
        &dest,
        target.as_deref(),
    )
    .await
}

/// Create a unique, unpredictable temp directory for a single open-file
/// operation.  Using a cryptographically random subdirectory under the
/// well-known cache root prevents symlink attacks (TOCTOU): an attacker
/// cannot predict the path before the download starts.
fn open_cache_dir() -> PathBuf {
    let mut buf = [0u8; 16];
    getrandom::getrandom(&mut buf).expect("failed to generate random bytes");
    let random_id = buf.iter().map(|b| format!("{:02x}", b)).collect::<String>();
    std::env::temp_dir().join("flutlink-open").join(random_id)
}

/// Remove stale `flutlink-open/*` directories left by earlier open operations
/// that were interrupted.  All stale directories are cleaned up (best-effort).
fn cleanup_open_cache() {
    let base = std::env::temp_dir().join("flutlink-open");
    if let Ok(entries) = std::fs::read_dir(&base) {
        for entry in entries.flatten() {
            if entry.path().is_dir() {
                let _ = std::fs::remove_dir_all(entry.path());
            }
        }
    }
}

// --- Guest access (complete public shares, no account required) ----------

/// Verify that the fixed FlutCloud server supports guest browsing
/// (anonymous probe; keeps the FlutCloud-only policy intact).
#[tauri::command]
pub async fn guest_verify_server(state: State<'_, AppState>) -> AppResult<()> {
    crate::guest::verify_guest_server(&state.http_client).await
}

/// All completely public shared folders in one bundled list.
#[tauri::command]
pub async fn guest_list_shares(
    state: State<'_, AppState>,
) -> AppResult<Vec<crate::guest::GuestShare>> {
    crate::guest::list_shares(&state.http_client).await
}

/// Browse into a public share folder (`path` defaults to the share root).
#[tauri::command]
pub async fn guest_list_entries(
    state: State<'_, AppState>,
    token: String,
    path: Option<String>,
) -> AppResult<crate::guest::GuestListing> {
    let path = path.unwrap_or_else(|| "/".to_string());
    crate::guest::list_entries(&state.http_client, &token, &path).await
}

/// Download a file from a public share to `local_path`.
#[tauri::command]
pub async fn guest_download_file(
    state: State<'_, AppState>,
    token: String,
    remote_path: String,
    local_path: String,
) -> AppResult<()> {
    crate::guest::validate_guest_target(&token, &remote_path)?;
    // Validate local_path: reject empty paths, null bytes, and path traversal.
    if local_path.is_empty() || local_path.contains('\0') {
        return Err(AppError::App("Invalid local path.".into()));
    }
    for segment in local_path.split(['/', '\\']) {
        if segment == ".." {
            return Err(AppError::App("Local path must not contain '..'.".into()));
        }
    }
    crate::guest::download_file(
        &state.http_client,
        &token,
        &remote_path,
        Path::new(&local_path),
    )
    .await
}

/// Download a public-share file into the dedicated open-cache directory and
/// open it with the default application (`open_remote_file` for guests).
#[tauri::command]
pub async fn guest_open_file(
    app: AppHandle,
    state: State<'_, AppState>,
    token: String,
    remote_path: String,
) -> AppResult<()> {
    crate::guest::validate_guest_target(&token, &remote_path)?;

    let cache_dir = open_cache_dir();
    cleanup_open_cache();
    std::fs::create_dir_all(&cache_dir)?;

    let file_name = Path::new(&remote_path)
        .file_name()
        .and_then(|name| name.to_str())
        .filter(|name| !name.is_empty())
        .unwrap_or("file");
    let local_path = cache_dir.join(file_name);

    crate::guest::download_file(&state.http_client, &token, &remote_path, &local_path).await?;

    app.opener()
        .open_path(local_path.to_string_lossy().to_string(), None::<&str>)
        .map_err(|e| AppError::App(e.to_string()))?;
    Ok(())
}

// --- Guest admin (require authenticated admin session) -------------------

#[tauri::command]
pub async fn guest_admin_set_category(
    state: State<'_, AppState>,
    name: String,
    prefixless: bool,
    visibility: String,
) -> AppResult<()> {
    let account = current_account(&state)?;
    if !account.meta.is_admin {
        return Err(AppError::Forbidden);
    }
    crate::guest::set_category(&state.http_client, &account, &name, prefixless, &visibility).await
}

#[tauri::command]
pub async fn guest_admin_delete_category(
    state: State<'_, AppState>,
    name: String,
) -> AppResult<()> {
    let account = current_account(&state)?;
    if !account.meta.is_admin {
        return Err(AppError::Forbidden);
    }
    crate::guest::delete_category(&state.http_client, &account, &name).await
}

#[tauri::command]
pub async fn guest_admin_assign_category(
    state: State<'_, AppState>,
    token: String,
    category: String,
) -> AppResult<()> {
    let account = current_account(&state)?;
    if !account.meta.is_admin {
        return Err(AppError::Forbidden);
    }
    crate::guest::assign_category(&state.http_client, &account, &token, &category).await
}

#[tauri::command]
pub async fn guest_admin_unassign_category(
    state: State<'_, AppState>,
    token: String,
) -> AppResult<()> {
    let account = current_account(&state)?;
    if !account.meta.is_admin {
        return Err(AppError::Forbidden);
    }
    crate::guest::unassign_category(&state.http_client, &account, &token).await
}

#[tauri::command]
pub async fn guest_admin_lock_path(
    state: State<'_, AppState>,
    token: String,
    path: String,
) -> AppResult<Vec<String>> {
    let account = current_account(&state)?;
    if !account.meta.is_admin {
        return Err(AppError::Forbidden);
    }
    crate::guest::lock_path(&state.http_client, &account, &token, &path).await
}

#[tauri::command]
pub async fn guest_admin_unlock_path(
    state: State<'_, AppState>,
    token: String,
    path: String,
) -> AppResult<Vec<String>> {
    let account = current_account(&state)?;
    if !account.meta.is_admin {
        return Err(AppError::Forbidden);
    }
    crate::guest::unlock_path(&state.http_client, &account, &token, &path).await
}

/// Admin: read the current lock list of a share (#373) so already-locked
/// folders render correctly instead of always appearing unlocked.
#[tauri::command]
pub async fn guest_admin_list_locks(
    state: State<'_, AppState>,
    token: String,
) -> AppResult<Vec<String>> {
    let account = current_account(&state)?;
    if !account.meta.is_admin {
        return Err(AppError::Forbidden);
    }
    crate::guest::list_locks(&state.http_client, &account, &token).await
}

#[tauri::command]
pub async fn admin_list_users(
    state: State<'_, AppState>,
    search: Option<String>,
    limit: Option<usize>,
    offset: Option<usize>,
) -> AppResult<AdminUsersResult> {
    let account = current_account(&state)?;
    if !account.meta.is_admin {
        return Err(AppError::Forbidden);
    }
    let (users, has_more) = ocs::list_users(
        &state.http_client,
        &account,
        search.as_deref().unwrap_or(""),
        limit,
        offset.unwrap_or(0),
    )
    .await?;
    Ok(AdminUsersResult { users, has_more })
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
    if user_id == account.meta.username {
        return Err(AppError::App(
            "You cannot delete your own account.".to_string(),
        ));
    }
    ocs::delete_user(&state.http_client, &account, &user_id).await
}

#[tauri::command]
pub async fn admin_list_groups(
    state: State<'_, AppState>,
    search: Option<String>,
) -> AppResult<Vec<String>> {
    let account = current_account(&state)?;
    if !account.meta.is_admin {
        return Err(AppError::Forbidden);
    }
    ocs::list_groups(
        &state.http_client,
        &account,
        search.as_deref().unwrap_or(""),
    )
    .await
}

#[tauri::command]
pub async fn admin_create_group(state: State<'_, AppState>, group_id: String) -> AppResult<String> {
    let account = current_account(&state)?;
    if !account.meta.is_admin {
        return Err(AppError::Forbidden);
    }
    ocs::create_group(&state.http_client, &account, &group_id).await
}

#[tauri::command]
pub async fn admin_add_group_member(
    state: State<'_, AppState>,
    group_id: String,
    user_id: String,
) -> AppResult<String> {
    let account = current_account(&state)?;
    if !account.meta.is_admin {
        return Err(AppError::Forbidden);
    }
    ocs::add_group_member(&state.http_client, &account, &group_id, &user_id).await
}

#[tauri::command]
pub async fn admin_remove_group_member(
    state: State<'_, AppState>,
    group_id: String,
    user_id: String,
) -> AppResult<String> {
    let account = current_account(&state)?;
    if !account.meta.is_admin {
        return Err(AppError::Forbidden);
    }
    ocs::remove_group_member(&state.http_client, &account, &group_id, &user_id).await
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
    follow_symlinks: Option<bool>,
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
        follow_symlinks: follow_symlinks.unwrap_or(false),
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
    }

    /// L17-F2: reading inside the virtual namespaces is allowed (browse/
    /// open/download/thumbnail) — only writes are refused.
    #[test]
    fn validate_dav_path_allows_virtual_namespaces_for_reads() {
        assert!(validate_dav_path("/resources/x").is_ok());
        assert!(validate_dav_path("/Resources/report.pdf").is_ok());
        assert!(validate_dav_path("/parts/x").is_ok());
    }

    #[test]
    fn validate_writable_dav_path_rejects_virtual_namespaces() {
        assert!(
            validate_writable_dav_path("/Resources/x").is_err(),
            "virtual folders are protected"
        );
        assert!(
            validate_writable_dav_path("/parts/x").is_err(),
            "virtual folders are protected"
        );
        assert!(
            validate_writable_dav_path("/Docs/resources").is_err(),
            "any segment is checked"
        );
        assert!(validate_writable_dav_path("/Documents/report.pdf").is_ok());
        assert!(
            validate_writable_dav_path("Documents/neu.pdf").is_err(),
            "base rules still apply"
        );
        assert!(
            validate_writable_dav_path("/../etc/passwd").is_err(),
            "base rules still apply"
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

    #[test]
    fn validate_rename_name_accepts_plain_names() {
        assert!(validate_rename_name("neu.pdf").is_ok());
        assert!(validate_rename_name("bericht 2024.txt").is_ok());
        assert!(validate_rename_name("_unterordner").is_ok());
    }

    #[test]
    fn move_dest_path_appends_source_name_to_folder() {
        assert_eq!(move_dest_path("/A/report.pdf", "/B"), "/B/report.pdf");
        assert_eq!(move_dest_path("/A/report.pdf", "/"), "/report.pdf");
        assert_eq!(move_dest_path("/readme.md", "/Docs"), "/Docs/readme.md");
        assert_eq!(move_dest_path("/A/B/c.txt", "/A"), "/A/c.txt");
    }

    #[test]
    fn validate_rename_name_rejects_slashes_and_dots() {
        assert!(
            validate_rename_name("sub/neu.pdf").is_err(),
            "must not contain '/'"
        );
        assert!(
            validate_rename_name("../neu.pdf").is_err(),
            "must not contain '/'"
        );
        assert!(validate_rename_name("..").is_err(), "must not be '..'");
        assert!(validate_rename_name(".").is_err(), "must not be '.'");
        assert!(validate_rename_name("").is_err(), "must not be empty");
    }

    #[test]
    fn bulk_local_path_preserves_relative_structure() {
        let dest = Path::new("/tmp/out");
        assert_eq!(
            bulk_local_path(dest, "/Docs/report.pdf"),
            Path::new("/tmp/out/Docs/report.pdf")
        );
        assert_eq!(
            bulk_local_path(dest, "/readme.md"),
            Path::new("/tmp/out/readme.md")
        );
        assert_eq!(
            bulk_local_path(dest, "/a/notes.txt"),
            Path::new("/tmp/out/a/notes.txt")
        );
        assert_ne!(
            bulk_local_path(dest, "/a/notes.txt"),
            bulk_local_path(dest, "/b/notes.txt")
        );
    }

    #[test]
    fn mkdir_leaf_name_extracts_last_segment() {
        assert_eq!(mkdir_leaf_name("/Documents/neu"), "neu");
        assert_eq!(mkdir_leaf_name("/neu"), "neu");
        assert_eq!(mkdir_leaf_name("/Documents/sub/neu"), "neu");
        assert_eq!(mkdir_leaf_name("/"), "");
        assert_eq!(mkdir_leaf_name("/Documents/"), "Documents");
    }
}
