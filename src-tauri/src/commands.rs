use std::path::PathBuf;
use std::time::{SystemTime, UNIX_EPOCH};

use serde::Deserialize;
use tauri::{AppHandle, State};

use crate::accounts;
use crate::error::{AppError, AppResult};
use crate::nextcloud::{ocs, webdav};
use crate::state::{
    Account, AccountMeta, AppState, OcsUser, SyncFolder, SyncFolderStatus, UserDetails, UserQuota,
    WebDavEntry,
};

fn to_meta_list(accounts: &[Account]) -> Vec<AccountMeta> {
    accounts.iter().map(|a| a.meta.clone()).collect()
}

fn current_account(state: &AppState) -> AppResult<Account> {
    state.current().ok_or(AppError::NoActiveAccount)
}

#[tauri::command]
pub async fn account_add(
    app: AppHandle,
    state: State<'_, AppState>,
    instance_url: String,
    username: String,
    token: String,
) -> AppResult<AccountMeta> {
    let instance_url = instance_url.trim_end_matches('/').to_string();
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

    let user = ocs::get_current_user(&state.http_client, &account).await?;
    let is_admin = ocs::is_admin(&state.http_client, &account)
        .await
        .unwrap_or(false);
    let has_accounts = !state.accounts_snapshot().is_empty();

    let meta = AccountMeta {
        username: account.meta.username,
        instance_url: account.meta.instance_url,
        display_name: user.display_name,
        is_admin,
        is_active: !has_accounts,
    };

    accounts::save_token(&meta, &account.token)?;
    let list = state.upsert(Account {
        meta: meta.clone(),
        token: account.token,
    });
    accounts::persist_accounts(&app, &list)?;
    Ok(meta)
}

#[tauri::command]
pub async fn account_list(state: State<'_, AppState>) -> AppResult<Vec<AccountMeta>> {
    Ok(to_meta_list(&state.accounts_snapshot()))
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
    let instance_url = input.instance_url.trim_end_matches('/').to_string();

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

    // Project folder for the FlutCloud app in the admin's files.
    webdav::ensure_collection(&state.http_client, &admin, FLUTCLOUD_PROJECT_PATH).await?;
    webdav::put_text(
        &state.http_client,
        &admin,
        &format!("{}/README.md", FLUTCLOUD_PROJECT_PATH),
        FLUTCLOUD_README,
    )
    .await?;

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
    let is_admin = ocs::is_admin(&state.http_client, &account)
        .await
        .unwrap_or(false);
    let has_accounts = !state.accounts_snapshot().is_empty();

    let meta = AccountMeta {
        username: account.meta.username,
        instance_url: account.meta.instance_url,
        display_name: user.display_name,
        is_admin,
        is_active: !has_accounts,
    };

    accounts::save_token(&meta, &account.token)?;
    let list = state.upsert(Account {
        meta: meta.clone(),
        token: account.token,
    });
    accounts::persist_accounts(&app, &list)?;
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
) -> AppResult<AccountMeta> {
    let account = state
        .set_active(&username)
        .ok_or_else(|| AppError::NotFound(username))?;
    accounts::persist_accounts(&app, &state.accounts_snapshot())?;
    Ok(account.meta)
}

#[tauri::command]
pub async fn account_remove(
    app: AppHandle,
    state: State<'_, AppState>,
    username: String,
) -> AppResult<Vec<AccountMeta>> {
    let snapshot = state.accounts_snapshot();
    let target = snapshot
        .iter()
        .find(|a| a.meta.username == username)
        .ok_or_else(|| AppError::NotFound(username))?;
    let _ = accounts::delete_token(&target.meta);
    let list = state.remove(&target.meta.username);
    accounts::persist_accounts(&app, &list)?;
    Ok(to_meta_list(&list))
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
#[tauri::command]
pub async fn webdav_create_share(state: State<'_, AppState>, path: String) -> AppResult<String> {
    let account = current_account(&state)?;
    ocs::create_share(&state.http_client, &account, &path).await
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
    ocs::update_user(&state.http_client, &account, &user_id, &key, &value).await
}

#[tauri::command]
pub async fn ocs_current_user(state: State<'_, AppState>) -> AppResult<OcsUser> {
    let account = current_account(&state)?;
    ocs::get_current_user(&state.http_client, &account).await
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
