mod accounts;
mod commands;
mod error;
mod flutcloud;
mod nextcloud;
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

/// Build the tray menu: "Show", a dynamic "Accounts" submenu and "Quit".
///
/// The Accounts submenu lists every configured account and marks the active
/// one. Clicking an entry switches to that account (see `setup_tray`).
fn build_tray_menu(app: &AppHandle) -> tauri::Result<Menu<Wry>> {
    let show = MenuItem::<Wry>::with_id(app, "show", "Show FlutLink", true, None::<&str>)?;
    let quit = MenuItem::<Wry>::with_id(app, "quit", "Quit FlutLink", true, None::<&str>)?;

    let state = app.state::<AppState>();
    let accounts = state.accounts_snapshot();
    let active_key = state.current().map(|a| crate::sync::account_key(&a));

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

    Menu::with_items(app, &[&show, &accounts_sub, &quit])
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
/// `-s/--sync`, `-p/--path <dir>`, `-u/--url <url>`, `-t/--tray`.
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

    if let Some(path) = cli_path {
        let handle = app.handle().clone();
        tauri::async_runtime::spawn(async move {
            match commands::sync_add(handle.clone(), handle.state::<AppState>(), path).await {
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
            app.state::<AppState>().sync.load(&handle);

            setup_tray(app, quit_flag.clone())?;
            sync::spawn_worker(&handle);
            handle_cli(app);
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            commands::get_flutcloud_url,
            commands::account_add,
            commands::account_list,
            commands::account_active,
            commands::account_switch,
            commands::account_remove,
            commands::account_storage,
            commands::register_user,
            commands::webdav_list,
            commands::webdav_search,
            commands::webdav_create_share,
            commands::webdav_upload_file,
            commands::webdav_download_file,
            commands::webdav_delete,
            commands::webdav_bulk_delete,
            commands::webdav_bulk_download,
            commands::webdav_upload_local_paths,
            commands::webdav_mkdir,
            commands::webdav_rename,
            commands::admin_list_users,
            commands::admin_get_user,
            commands::admin_set_user_quota,
            commands::admin_edit_user,
            commands::admin_create_user,
            commands::admin_delete_user,
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
