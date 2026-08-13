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

use state::AppState;
use tauri::{
    menu::{Menu, MenuItem},
    tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent},
    AppHandle, Emitter, Manager, WindowEvent,
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

/// System tray with "Show" and "Quit" actions. The window hides into the tray
/// instead of quitting when closed (unless quitting via the tray menu).
fn setup_tray(app: &tauri::App, quit_flag: Arc<AtomicBool>) -> tauri::Result<()> {
    let show = MenuItem::with_id(app, "show", "Show FlutLink", true, None::<&str>)?;
    let quit = MenuItem::with_id(app, "quit", "Quit FlutLink", true, None::<&str>)?;
    let menu = Menu::with_items(app, &[&show, &quit])?;

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
            let accounts = accounts::load_accounts(&handle).unwrap_or_default();
            app.state::<AppState>().set_accounts(accounts);
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
            commands::webdav_create_share,
            commands::admin_list_users,
            commands::admin_get_user,
            commands::admin_set_user_quota,
            commands::admin_edit_user,
            commands::admin_create_user,
            commands::admin_delete_user,
            commands::ocs_current_user,
            commands::sync_list,
            commands::sync_add,
            commands::sync_remove,
            commands::sync_set_paused,
            commands::sync_trigger,
            updater::check_update,
            updater::download_and_install_update,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
