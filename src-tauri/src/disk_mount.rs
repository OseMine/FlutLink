//! Disk Mount System
//!
//! Driverless WebDAV mounting on all desktop OSes: a local WebDAV server
//! (`dav-server` + `hyper`) serves the mount cache directory on
//! `127.0.0.1:<ephemeral port>`, and the OS WebDAV redirector maps it to a
//! drive (Windows `net use`/WebClient, macOS `mount_webdav`, Linux `gio`).
//! No admin rights and no kernel drivers (WinFsp/macFUSE) are required.

use std::convert::Infallible;
use std::path::PathBuf;
use std::sync::Arc;

use dav_server::{fakels::FakeLs, localfs::LocalFs, DavHandler};
use hyper::server::conn::http1;
use hyper::service::service_fn;
use hyper_util::rt::TokioIo;
use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Manager, State};
use tokio::sync::{oneshot, Mutex};

use crate::error::{AppError, AppResult};

/// Default cache folder for a mounted drive, inside the platform app-data
/// directory — e.g. `%APPDATA%\de.flut.flutlink\cache\mountcache` on Windows.
pub fn default_cache_dir(app: &AppHandle) -> Option<PathBuf> {
    let dir = app.path().app_data_dir().ok()?;
    Some(dir.join("cache").join("mountcache"))
}

/// Snapshot of the disk-mount state reported to the frontend.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct MountStatus {
    pub is_mounted: bool,
    pub mount_point: Option<String>,
    pub server_url: Option<String>,
    pub cache_dir: String,
}

struct ActiveMount {
    mount_point: String,
    server_url: String,
    shutdown_tx: oneshot::Sender<()>,
}

/// Tauri-managed state holding the currently mounted drive (if any).
#[derive(Default)]
pub struct DiskMountState {
    active_mount: Arc<Mutex<Option<ActiveMount>>>,
}

/// Start the local WebDAV server and mount it as a drive in the OS.
#[tauri::command]
pub async fn mount_disk(
    app: AppHandle,
    state: State<'_, DiskMountState>,
    custom_cache_dir: Option<String>,
) -> AppResult<MountStatus> {
    let mut active = state.active_mount.lock().await;
    if active.is_some() {
        return Err(AppError::App("The disk is already mounted.".into()));
    }

    // The mount cache serves both as the WebDAV root and as the local mirror;
    // without it the virtual drive has nowhere to store file data.
    let cache_path = match custom_cache_dir {
        Some(path) => PathBuf::from(path),
        None => default_cache_dir(&app)
            .ok_or_else(|| AppError::App("Could not resolve the app data directory.".into()))?,
    };
    std::fs::create_dir_all(&cache_path)
        .map_err(|e| AppError::App(format!("Could not create the cache directory: {e}")))?;

    let dav_handler = DavHandler::builder()
        .filesystem(LocalFs::new(&cache_path, false, false, false))
        .locksystem(FakeLs::new())
        .build_handler();

    // Bind an ephemeral loopback port; the OS picks a free one (127.0.0.1:0).
    let listener = tokio::net::TcpListener::bind("127.0.0.1:0")
        .await
        .map_err(|e| AppError::App(format!("Could not bind the WebDAV port: {e}")))?;
    let addr = listener
        .local_addr()
        .map_err(|e| AppError::App(e.to_string()))?;
    let server_url = format!("http://{addr}");

    let (shutdown_tx, mut shutdown_rx) = oneshot::channel::<()>();
    tokio::spawn(async move {
        loop {
            tokio::select! {
                _ = &mut shutdown_rx => break,
                Ok((stream, _)) = listener.accept() => {
                    let dav = dav_handler.clone();
                    tokio::spawn(async move {
                        let io = TokioIo::new(stream);
                        if let Err(err) = http1::Builder::new()
                            .serve_connection(
                                io,
                                service_fn(move |req| {
                                    let dav = dav.clone();
                                    async move { Ok::<_, Infallible>(dav.handle(req).await) }
                                }),
                            )
                            .await
                        {
                            eprintln!("WebDAV serve error: {err:?}");
                        }
                    });
                }
            }
        }
    });

    let mount_point = match mount_os_drive(&server_url).await {
        Ok(point) => point,
        Err(e) => {
            let _ = shutdown_tx.send(());
            return Err(e);
        }
    };

    let status = MountStatus {
        is_mounted: true,
        mount_point: Some(mount_point.clone()),
        server_url: Some(server_url.clone()),
        cache_dir: cache_path.to_string_lossy().to_string(),
    };
    *active = Some(ActiveMount {
        mount_point,
        server_url,
        shutdown_tx,
    });
    Ok(status)
}

/// Unmount the drive and stop the local server.
#[tauri::command]
pub async fn unmount_disk(state: State<'_, DiskMountState>) -> AppResult<()> {
    let mut active = state.active_mount.lock().await;
    match active.take() {
        Some(mount) => {
            unmount_os_drive(&mount.mount_point).await?;
            let _ = mount.shutdown_tx.send(());
            Ok(())
        }
        None => Err(AppError::App("No active disk to unmount.".into())),
    }
}

/// Report the current mount status.
#[tauri::command]
pub async fn get_mount_status(
    app: AppHandle,
    state: State<'_, DiskMountState>,
) -> AppResult<MountStatus> {
    let cache_dir = default_cache_dir(&app)
        .unwrap_or_default()
        .to_string_lossy()
        .to_string();
    let active = state.active_mount.lock().await;
    match active.as_ref() {
        Some(mount) => Ok(MountStatus {
            is_mounted: true,
            mount_point: Some(mount.mount_point.clone()),
            server_url: Some(mount.server_url.clone()),
            cache_dir,
        }),
        None => Ok(MountStatus {
            is_mounted: false,
            mount_point: None,
            server_url: None,
            cache_dir,
        }),
    }
}

// =========================================================================
// OS-SPECIFIC DRIVE BINDING
// =========================================================================

#[cfg(target_os = "windows")]
async fn mount_os_drive(server_url: &str) -> AppResult<String> {
    let drive_letter = "Z:";
    let output = std::process::Command::new("net")
        .args(["use", drive_letter, server_url])
        .output()
        .map_err(|e| AppError::App(format!("Could not run 'net use': {e}")))?;
    if output.status.success() {
        Ok(drive_letter.to_string())
    } else {
        Err(AppError::App(
            String::from_utf8_lossy(&output.stderr)
                .trim_end()
                .to_string(),
        ))
    }
}

#[cfg(target_os = "windows")]
async fn unmount_os_drive(mount_point: &str) -> AppResult<()> {
    let output = std::process::Command::new("net")
        .args(["use", mount_point, "/delete", "/yes"])
        .output()
        .map_err(|e| AppError::App(format!("Could not unmount the drive: {e}")))?;
    if output.status.success() {
        Ok(())
    } else {
        Err(AppError::App(
            String::from_utf8_lossy(&output.stderr)
                .trim_end()
                .to_string(),
        ))
    }
}

#[cfg(target_os = "macos")]
async fn mount_os_drive(server_url: &str) -> AppResult<String> {
    let dav_url = server_url.replace("http://", "http://guest@");
    let mount_dir = "/Volumes/FlutLink";
    std::fs::create_dir_all(mount_dir).ok();

    let output = std::process::Command::new("mount_webdav")
        .args(["-S", "-v", "FlutLink", &dav_url, mount_dir])
        .output()
        .map_err(|e| AppError::App(format!("Could not run mount_webdav: {e}")))?;
    if output.status.success() {
        Ok(mount_dir.to_string())
    } else {
        Err(AppError::App(
            String::from_utf8_lossy(&output.stderr)
                .trim_end()
                .to_string(),
        ))
    }
}

#[cfg(target_os = "macos")]
async fn unmount_os_drive(mount_point: &str) -> AppResult<()> {
    let output = std::process::Command::new("umount")
        .arg(mount_point)
        .output()
        .map_err(|e| AppError::App(format!("Could not run umount: {e}")))?;
    if output.status.success() {
        Ok(())
    } else {
        Err(AppError::App(
            String::from_utf8_lossy(&output.stderr)
                .trim_end()
                .to_string(),
        ))
    }
}

#[cfg(target_os = "linux")]
async fn mount_os_drive(server_url: &str) -> AppResult<String> {
    let dav_url = server_url.replace("http://", "dav://");
    let output = std::process::Command::new("gio")
        .args(["mount", &dav_url])
        .output()
        .map_err(|e| AppError::App(format!("Could not run gio mount: {e}")))?;
    if output.status.success() {
        Ok(dav_url)
    } else {
        Err(AppError::App(
            String::from_utf8_lossy(&output.stderr)
                .trim_end()
                .to_string(),
        ))
    }
}

#[cfg(target_os = "linux")]
async fn unmount_os_drive(mount_point: &str) -> AppResult<()> {
    let output = std::process::Command::new("gio")
        .args(["mount", "-u", mount_point])
        .output()
        .map_err(|e| AppError::App(format!("Could not run gio unmount: {e}")))?;
    if output.status.success() {
        Ok(())
    } else {
        Err(AppError::App(
            String::from_utf8_lossy(&output.stderr)
                .trim_end()
                .to_string(),
        ))
    }
}

#[cfg(not(any(target_os = "windows", target_os = "macos", target_os = "linux")))]
async fn mount_os_drive(_server_url: &str) -> AppResult<String> {
    Err(AppError::App(
        "Disk mounting is not supported on this platform.".into(),
    ))
}

#[cfg(not(any(target_os = "windows", target_os = "macos", target_os = "linux")))]
async fn unmount_os_drive(_mount_point: &str) -> AppResult<()> {
    Err(AppError::App(
        "Disk unmounting is not supported on this platform.".into(),
    ))
}
