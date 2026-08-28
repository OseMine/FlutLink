mod accounts;
mod cache;
mod commands;
mod error;
mod flutcloud;
mod guest;
mod history;
mod nextcloud;
mod persist;
mod settings;
mod state;
mod sync;
mod updater;

use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;

use error::{AppError, AppResult};
use state::AppState;
use tauri::{
    menu::{IsMenuItem, Menu, MenuItem, Submenu},
    tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent},
    AppHandle, Emitter, Manager, WindowEvent, Wry,
};
use tauri_plugin_cli::CliExt;

/// Show the main window (used by the tray menu / tray click).
fn show_main_window(app: &AppHandle) {
    if let Some(window) = app.get_webview_window("main") {
        let _ = window.unminimize();
        let _ = window.show();
        let _ = window.set_focus();
    }
}

/// Build the tray menu: sync quick actions, "Show", a dynamic "Accounts"
/// submenu and "Quit".
///
/// The Accounts submenu lists every configured account and marks the active
/// one. Clicking an entry switches to that account (see `setup_tray`).
fn build_tray_menu(app: &AppHandle) -> tauri::Result<Menu<Wry>> {
    let show = MenuItem::<Wry>::with_id(app, "show", "Show FlutLink", true, None::<&str>)?;
    let quit = MenuItem::<Wry>::with_id(app, "quit", "Quit FlutLink", true, None::<&str>)?;
    let sync_now = MenuItem::<Wry>::with_id(app, "sync-now", "Sync now", true, None::<&str>)?;
    let separator = tauri::menu::PredefinedMenuItem::separator(app)?;

    let state = app.state::<AppState>();
    let accounts = state.accounts_snapshot();
    let active_key = state.current().map(|a| crate::sync::account_key(&a));

    // #428: global upload pause toggle — the label flips with the state and
    // the menu is rebuilt after every toggle.
    let upload_label = if state.sync.is_upload_paused() {
        "Resume uploads"
    } else {
        "Pause uploads"
    };
    let toggle_uploads =
        MenuItem::<Wry>::with_id(app, "toggle-uploads", upload_label, true, None::<&str>)?;
    // #428: Online/Offline status indicator (disabled, informational). "Online"
    // tracks whether an account is connected; while a sync pass is in flight
    // the tooltip stays truthful because the worker is live either way.
    let online_status = MenuItem::<Wry>::with_id(
        app,
        "online-status",
        if accounts.is_empty() {
            "Status: Offline"
        } else {
            "Status: Online"
        },
        false,
        None::<&str>,
    )?;

    let mut account_items: Vec<MenuItem<Wry>> = Vec::new();
    if accounts.is_empty() {
        account_items.push(MenuItem::<Wry>::with_id(
            app,
            "accounts-empty",
            "No account connected",
            false,
            None::<&str>,
        )?);
    } else {
        for account in &accounts {
            let display = account
                .meta
                .display_name
                .as_deref()
                .unwrap_or(&account.meta.username);
            let label = if active_key.as_deref() == Some(crate::sync::account_key(account).as_str())
            {
                format!("✓ {display}")
            } else {
                display.to_string()
            };
            account_items.push(MenuItem::<Wry>::with_id(
                app,
                format!(
                    "switch:{}@{}",
                    account.meta.username, account.meta.instance_url
                ),
                label,
                true,
                None::<&str>,
            )?);
        }
    }

    let account_refs: Vec<&dyn IsMenuItem<Wry>> = account_items
        .iter()
        .map(|i| i as &dyn IsMenuItem<Wry>)
        .collect();
    let accounts_sub = Submenu::with_items(app, "Accounts", true, &account_refs)?;

    Menu::with_items(
        app,
        &[
            &show,
            &separator,
            &sync_now,
            &toggle_uploads,
            &online_status,
            &separator,
            &accounts_sub,
            &quit,
        ],
    )
}

/// Rebuild the tray menu from the current account list (used after accounts
/// change) and apply it to the tray icon.
pub fn refresh_tray_menu(app: &AppHandle) -> AppResult<()> {
    let menu = build_tray_menu(app).map_err(|e| AppError::App(e.to_string()))?;
    app.tray_by_id("flutlink-tray")
        .ok_or_else(|| AppError::App("System tray is not available".to_string()))?
        .set_menu(Some(menu))
        .map_err(|e| AppError::App(e.to_string()))
}

/// System tray with "Show", a dynamic "Accounts" submenu and "Quit". The
/// window hides into the tray instead of quitting when closed (unless quitting
/// via the tray menu).
fn setup_tray(app: &tauri::App, quit_flag: Arc<AtomicBool>) -> tauri::Result<()> {
    let menu = build_tray_menu(app.handle())?;
    let mut builder = TrayIconBuilder::with_id("flutlink-tray")
        .menu(&menu)
        .tooltip("FlutLink — FlutCloud client");
    if let Some(icon) = app.default_window_icon().cloned() {
        builder = builder.icon(icon);
    }
    builder
        .on_menu_event(move |app, event| match event.id().as_ref() {
            "show" => show_main_window(app),
            "quit" => {
                quit_flag.store(true, Ordering::SeqCst);
                app.exit(0);
            }
            "sync-now" => {
                // #428: run every sync folder immediately from the tray.
                app.state::<AppState>().sync.notify_one();
            }
            "toggle-uploads" => {
                // #428: flip the global upload pause, rebuild the menu so the
                // label follows, and tell the frontend to re-read statuses.
                let engine = app.state::<AppState>().sync.clone();
                engine.set_upload_paused(!engine.is_upload_paused());
                let _ = refresh_tray_menu(app);
                let _ = app.emit("sync-folders-changed", ());
            }
            id if id.starts_with("switch:") => {
                // Tray ids carry the composite identity: switch:user@instance
                let identity = id.trim_start_matches("switch:").to_string();
                let Some((username, instance_url)) = identity.rsplit_once('@') else {
                    return;
                };
                let state = app.state::<AppState>();
                if state.set_active(username, instance_url).is_some() {
                    let _ = accounts::persist_accounts(app, &state.accounts_snapshot());
                    let _ = refresh_tray_menu(app);
                    let _ = app.emit("accounts-changed", ());
                    show_main_window(app);
                }
            }
            _ => {}
        })
        .on_tray_icon_event(|tray, event| {
            if let TrayIconEvent::Click {
                button: MouseButton::Left,
                button_state: MouseButtonState::Up,
                ..
            } = event
            {
                show_main_window(tray.app_handle());
            }
        })
        .build(app)?;
    Ok(())
}

/// Handle CLI flags passed to the app binary:
/// `-s/--sync`, `-p/--path <dir>`, `-u/--url <url>`, `-t/--tray`,
/// `--download <remote> --download-to <local>` and `--list <path>` (the latter
/// two are headless: they print JSON to stdout and need not show a window).
fn handle_cli(app: &tauri::App) {
    let Ok(matches) = app.cli().matches() else {
        return;
    };
    let args = &matches.args;

    let want_tray = args.get("tray").is_some_and(|a| a.occurrences > 0);
    let want_sync = args.get("sync").is_some_and(|a| a.occurrences > 0);
    let cli_path = args
        .get("path")
        .and_then(|a| a.value.as_str())
        .map(str::to_string);
    let cli_url = args
        .get("url")
        .and_then(|a| a.value.as_str())
        .map(str::to_string);

    if let Some(url) = cli_url {
        let _ = app.emit("flutlink:cli-open", url);
    }

    // #400: headless `--download <remote> --download-to <local>`.
    let cli_download = args
        .get("download")
        .and_then(|a| a.value.as_str())
        .map(str::to_string);
    let cli_download_to = args
        .get("download-to")
        .and_then(|a| a.value.as_str())
        .map(str::to_string);
    match (cli_download, cli_download_to) {
        (Some(remote), Some(local)) => {
            if let Some(parent) = std::path::Path::new(&local).parent() {
                let _ = std::fs::create_dir_all(parent);
            }
            let handle = app.handle().clone();
            tauri::async_runtime::spawn(async move {
                let state = handle.state::<AppState>();
                let Some(account) = state.current() else {
                    eprintln!("flutlink --download: no active account (connect one first)");
                    return;
                };
                match nextcloud::webdav::get_file(
                    &state.http_client,
                    &account,
                    &remote,
                    std::path::Path::new(&local),
                )
                .await
                {
                    Ok(()) => println!(
                        "{}",
                        serde_json::json!({ "ok": true, "remote": remote, "local": local })
                    ),
                    Err(err) => eprintln!("flutlink --download: {}", err.message()),
                }
            });
        }
        (Some(_), None) | (None, Some(_)) => {
            eprintln!(
                "flutlink: --download requires both --download <remote> and --download-to <local>"
            );
        }
        _ => {}
    }

    // #400: headless `--list <path>` — print the folder listing as JSON.
    if let Some(path) = args
        .get("list")
        .and_then(|a| a.value.as_str())
        .map(str::to_string)
    {
        let handle = app.handle().clone();
        tauri::async_runtime::spawn(async move {
            let state = handle.state::<AppState>();
            let Some(account) = state.current() else {
                eprintln!("flutlink --list: no active account (connect one first)");
                return;
            };
            match nextcloud::webdav::list(&state.http_client, &account, &path, None).await {
                Ok(entries) => match serde_json::to_string(&entries) {
                    Ok(json) => println!("{json}"),
                    Err(err) => eprintln!("flutlink --list: could not serialize: {err}"),
                },
                Err(err) => eprintln!("flutlink --list: {}", err.message()),
            }
        });
    }

    if let Some(path) = cli_path {
        let handle = app.handle().clone();
        tauri::async_runtime::spawn(async move {
            match commands::sync_add(handle.clone(), handle.state::<AppState>(), path, None).await {
                Ok(_) => {
                    let _ = handle.emit("sync-folders-changed", ());
                }
                Err(err) => eprintln!("flutlink --path: {}", err.message()),
            }
        });
    }

    if want_sync {
        app.state::<AppState>().sync.notify_one();
    }

    if want_tray {
        if let Some(window) = app.get_webview_window("main") {
            let _ = window.hide();
        }
    }
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let quit_flag = Arc::new(AtomicBool::new(false));
    let quit_flag_close = quit_flag.clone();

    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_cli::init())
        .plugin(tauri_plugin_notification::init())
        .manage(AppState::new())
        .on_window_event(move |window, event| {
            if let WindowEvent::CloseRequested { api, .. } = event {
                if !quit_flag_close.load(Ordering::SeqCst) {
                    api.prevent_close();
                    let _ = window.hide();
                }
            }
        })
        .setup(move |app| {
            let handle = app.handle().clone();
            let loaded = accounts::load_accounts(&handle).unwrap_or_default();
            app.state::<AppState>().set_accounts(loaded.accounts);
            // F8: surface dropped accounts so the frontend can explain why some
            // saved accounts do not show up (server mismatch / missing FLUTCLOUD_URL).
            app.state::<AppState>()
                .set_filtered_accounts(loaded.dropped);
            // Surface accounts whose keyring token is gone so they never
            // vanish without an explanation.
            app.state::<AppState>()
                .set_token_missing_accounts(loaded.token_missing);
            app.state::<AppState>().sync.load(&handle);

            setup_tray(app, quit_flag.clone())?;
            sync::spawn_worker(&handle);

            // P15: the stored admin flag is re-evaluated once at startup so a
            // transient network failure at sign-in can never permanently demote
            // an admin account to a regular one.
            let refresh_handle = app.handle().clone();
            tauri::async_runtime::spawn(async move {
                commands::refresh_admin_flags(&refresh_handle).await;
            });

            handle_cli(app);
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            commands::get_flutcloud_url,
            commands::account_add,
            commands::account_list,
            commands::account_switch,
            commands::account_remove,
            commands::account_storage,
            commands::register_user,
            commands::webdav_list,
            commands::webdav_search,
            commands::webdav_create_share,
            commands::webdav_list_shares,
            commands::webdav_update_share,
            commands::webdav_delete_share,
            commands::webdav_upload_file,
            commands::webdav_download_file,
            commands::open_remote_file,
            commands::webdav_download_zip,
            commands::webdav_thumbnail,
            commands::webdav_delete,
            commands::webdav_bulk_delete,
            commands::webdav_bulk_download,
            commands::webdav_upload_local_paths,
            commands::webdav_mkdir,
            commands::webdav_rename,
            commands::webdav_copy,
            commands::webdav_move,
            commands::guest_verify_server,
            commands::guest_list_shares,
            commands::guest_list_entries,
            commands::guest_download_file,
            commands::guest_open_file,
            commands::guest_admin_set_category,
            commands::guest_admin_delete_category,
            commands::guest_admin_assign_category,
            commands::guest_admin_unassign_category,
            commands::guest_admin_lock_path,
            commands::guest_admin_unlock_path,
            commands::guest_admin_list_locks,
            commands::file_history_list,
            commands::file_history_clear,
            commands::set_share_notify,
            commands::admin_list_users,
            commands::admin_get_user,
            commands::admin_set_user_quota,
            commands::admin_edit_user,
            commands::admin_create_user,
            commands::admin_delete_user,
            commands::admin_list_groups,
            commands::admin_create_group,
            commands::admin_add_group_member,
            commands::admin_remove_group_member,
            commands::sync_list,
            commands::sync_add,
            commands::sync_remove,
            commands::sync_set_paused,
            commands::sync_trigger,
            commands::account_filter_info,
            updater::check_update,
            updater::download_and_install_update,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
