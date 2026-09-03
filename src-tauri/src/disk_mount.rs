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

use base64::Engine;
use dav_server::{fakels::FakeLs, localfs::LocalFs, DavHandler};
use http_body_util::{Either, Full};
use hyper::server::conn::http1;
use hyper::service::service_fn;
use hyper_util::rt::TokioIo;
use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Manager, State};
use tokio::sync::{oneshot, Mutex};

use crate::error::{AppError, AppResult};

/// Username used for Basic auth on the local WebDAV server.
const AUTH_USER: &str = "flutlink";
/// Length of the random token (bytes, encoded to ~53 chars base64).
const AUTH_TOKEN_LEN: usize = 32;

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
    #[allow(dead_code)]
    auth_token: String,
    cache_dir: PathBuf,
    shutdown_tx: oneshot::Sender<()>,
}

/// Tauri-managed state holding the currently mounted drive (if any).
#[derive(Default)]
pub struct DiskMountState {
    active_mount: Arc<Mutex<Option<ActiveMount>>>,
}

/// Generate a random base64 token for Basic auth.
fn generate_auth_token() -> String {
    use getrandom::fill;
    let mut buf = vec![0u8; AUTH_TOKEN_LEN];
    fill(&mut buf).expect("getrandom failed");
    base64::engine::general_purpose::STANDARD.encode(&buf)
}

/// Validate and prepare the cache directory, returning its canonical path.
fn prepare_cache_dir(custom: Option<String>, app: &AppHandle) -> AppResult<PathBuf> {
    let path = match custom {
        Some(p) => {
            let pb = PathBuf::from(&p);
            // Validate: path must exist after create_dir_all and be writable.
            std::fs::create_dir_all(&pb).map_err(|e| {
                AppError::App(format!("Could not create cache directory '{p}': {e}"))
            })?;
            // Probe writability.
            let probe = pb.join(".flutlink_write_test");
            std::fs::write(&probe, b"ok").map_err(|e| {
                AppError::App(format!("Cache directory '{p}' is not writable: {e}"))
            })?;
            let _ = std::fs::remove_file(&probe);
            pb
        }
        None => default_cache_dir(app)
            .ok_or_else(|| AppError::App("Could not resolve the app data directory.".into()))?
            .to_owned(),
    };
    Ok(path)
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

    let cache_path = prepare_cache_dir(custom_cache_dir, &app)?;
    let auth_token = generate_auth_token();
    let expected_auth = format!(
        "Basic {}",
        base64::engine::general_purpose::STANDARD.encode(format!("{AUTH_USER}:{auth_token}"))
    );

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
                    let expected = expected_auth.clone();
                    tokio::spawn(async move {
                        let io = TokioIo::new(stream);
                        if let Err(err) = http1::Builder::new()
                            .serve_connection(
                                io,
                                service_fn(move |req| {
                                    let dav = dav.clone();
                                    let expected = expected.clone();
                                    async move {
                                        // Basic auth gate: every request must carry the
                                        // matching Authorization header.  OPTIONS
                                        // (WebDAV discovery) is exempt so OS clients can
                                        // probe the server before sending credentials.
                                        if req.method() != hyper::Method::OPTIONS {
                                            let authed = req
                                                .headers()
                                                .get(hyper::header::AUTHORIZATION)
                                                .and_then(|v| v.to_str().ok())
                                                .map(|v| v == expected)
                                                .unwrap_or(false);
                                            if !authed {
                                                let mut resp = hyper::Response::new(
                                                    Either::Left(Full::new(
                                                        bytes::Bytes::from("Unauthorized"),
                                                    )),
                                                );
                                                *resp.status_mut() = hyper::StatusCode::UNAUTHORIZED;
                                                resp.headers_mut().insert(
                                                    hyper::header::WWW_AUTHENTICATE,
                                                    "Basic realm=\"flutlink\"".parse().unwrap(),
                                                );
                                                return Ok::<_, Infallible>(resp);
                                            }
                                        }
                                        let dav_resp = dav.handle(req).await;
                                        let (parts, body) = dav_resp.into_parts();
                                        Ok::<_, Infallible>(hyper::Response::from_parts(parts, Either::Right(body)))
                                    }
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

    let mount_point = match mount_os_drive(&server_url, &auth_token).await {
        Ok(point) => point,
        Err(e) => {
            let _ = shutdown_tx.send(());
            return Err(e);
        }
    };

    let cache_dir_str = cache_path.to_string_lossy().to_string();
    let status = MountStatus {
        is_mounted: true,
        mount_point: Some(mount_point.clone()),
        server_url: Some(server_url.clone()),
        cache_dir: cache_dir_str,
    };
    *active = Some(ActiveMount {
        mount_point,
        server_url,
        auth_token,
        cache_dir: cache_path,
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
    let active = state.active_mount.lock().await;
    match active.as_ref() {
        Some(mount) => Ok(MountStatus {
            is_mounted: true,
            mount_point: Some(mount.mount_point.clone()),
            server_url: Some(mount.server_url.clone()),
            cache_dir: mount.cache_dir.to_string_lossy().to_string(),
        }),
        None => {
            let cache_dir = default_cache_dir(&app)
                .unwrap_or_default()
                .to_string_lossy()
                .to_string();
            Ok(MountStatus {
                is_mounted: false,
                mount_point: None,
                server_url: None,
                cache_dir,
            })
        }
    }
}

/// Shut down a running disk mount (called during app exit / crash cleanup).
pub async fn shutdown_if_mounted(state: &DiskMountState) {
    let mut active = state.active_mount.lock().await;
    if let Some(mount) = active.take() {
        // Best-effort OS unmount; ignore errors during shutdown.
        let _ = unmount_os_drive(&mount.mount_point).await;
        let _ = mount.shutdown_tx.send(());
    }
}

// =========================================================================
// OS-SPECIFIC DRIVE BINDING
// =========================================================================

#[cfg(target_os = "windows")]
async fn mount_os_drive(server_url: &str, auth_token: &str) -> AppResult<String> {
    // Use `*` so Windows picks a free drive letter instead of colliding with Z:.
    let output = std::process::Command::new("net")
        .args([
            "use",
            "*",
            server_url,
            &format!("{AUTH_USER}:{auth_token}"),
            "/persistent:no",
        ])
        .output()
        .map_err(|e| AppError::App(format!("Could not run 'net use': {e}")))?;
    if output.status.success() {
        // `net use` prints the assigned letter on stdout, e.g. "Z: was mapped successfully."
        let stdout = String::from_utf8_lossy(&output.stdout);
        // Extract the drive letter from the first line: "Z: ..."
        if let Some(letter) = stdout
            .split_whitespace()
            .next()
            .and_then(|s| s.strip_suffix(':').map(|l| format!("{l}:")))
        {
            Ok(letter)
        } else {
            // Fallback: the drive was mapped but we can't parse the letter.
            Err(AppError::App(
                "Drive mapped but could not determine the letter.".into(),
            ))
        }
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
async fn mount_os_drive(server_url: &str, _auth_token: &str) -> AppResult<String> {
    // mount_webdav -S (silent) + -v (volume name).  The local WebDAV server
    // accepts anonymous connections (auth is only a localhost gate for Windows),
    // so we pass the bare server URL without `guest@`.
    let mount_dir = "/Volumes/FlutLink";
    std::fs::create_dir_all(mount_dir).ok();

    let output = std::process::Command::new("mount_webdav")
        .args(["-S", "-v", "FlutLink", server_url, mount_dir])
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
async fn mount_os_drive(server_url: &str, _auth_token: &str) -> AppResult<String> {
    let dav_url = server_url.replace("http://", "dav://");
    let output = std::process::Command::new("gio")
        .args(["mount", &dav_url])
        .output()
        .map_err(|e| AppError::App(format!("Could not run gio mount: {e}")))?;
    if output.status.success() {
        // Resolve the actual GVFS mount point from `gio mount -l`.
        resolve_gvfs_mount_point(&dav_url).await
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
    // Try to unmount by the resolved GVFS path first, then fall back to the
    // dav:// URL.
    let output = std::process::Command::new("gio")
        .args(["mount", "-u", mount_point])
        .output()
        .map_err(|e| AppError::App(format!("Could not run gio unmount: {e}")))?;
    if output.status.success() {
        Ok(())
    } else {
        // If the path is a local filesystem path (GVFS), try converting to
        // dav:// URL and unmount by URL.
        if !mount_point.starts_with("dav://") {
            let dav_url = mount_point_to_dav_url(mount_point);
            let output2 = std::process::Command::new("gio")
                .args(["mount", "-u", &dav_url])
                .output()
                .map_err(|e| AppError::App(format!("Could not run gio unmount: {e}")))?;
            if output2.status.success() {
                return Ok(());
            }
        }
        Err(AppError::App(
            String::from_utf8_lossy(&output.stderr)
                .trim_end()
                .to_string(),
        ))
    }
}

/// Try to find the GVFS mount point for a given dav:// URL by listing mounts.
#[cfg(target_os = "linux")]
async fn resolve_gvfs_mount_point(dav_url: &str) -> AppResult<String> {
    let output = std::process::Command::new("gio")
        .args(["mount", "-l"])
        .output()
        .map_err(|e| AppError::App(format!("Could not run 'gio mount -l': {e}")))?;
    if output.status.success() {
        let listing = String::from_utf8_lossy(&output.stdout);
        // Find a line containing the dav_url's host/port that points to a gvfs path.
        let url_host_port = dav_url
            .trim_start_matches("dav://")
            .split('/')
            .next()
            .unwrap_or("");
        for line in listing.lines() {
            if line.contains(url_host_port) && line.contains("/run/") {
                // Extract the mount path: after the URL reference, e.g.
                // "  Mount(1): ... -> dav://127.0.0.1:12345 -> /run/user/1000/gvfs/dav:..."
                if let Some(path) = line.split_whitespace().last() {
                    if path.starts_with("/run/") {
                        return Ok(path.to_string());
                    }
                }
            }
        }
    }
    // Fallback: return the dav:// URL itself (not ideal but functional).
    Ok(dav_url.to_string())
}

/// Best-effort conversion of a local GVFS path back to a dav:// URL for unmount.
#[cfg(target_os = "linux")]
fn mount_point_to_dav_url(path: &str) -> String {
    // GVFS paths look like /run/user/1000/gvfs/dav:host=127.0.0.1,port=12345
    // or /run/user/1000/gvfs/davs:host=127.0.0.1,port=12345
    if let Some(gvfs_suffix) = path.strip_prefix("/run/user/") {
        if let Some(after_user) = gvfs_suffix.find('/') {
            let rest = &gvfs_suffix[after_user + 1..];
            if let Some(rest) = rest.strip_prefix("gvfs/") {
                // rest = "dav:host=127.0.0.1,port=12345"
                let scheme = if rest.starts_with("davs:") {
                    "https"
                } else {
                    "http"
                };
                let params = rest.trim_start_matches("dav:").trim_start_matches("davs:");
                let host = params
                    .split(',')
                    .find(|s| s.starts_with("host="))
                    .and_then(|s| s.strip_prefix("host="))
                    .unwrap_or("127.0.0.1");
                let port = params
                    .split(',')
                    .find(|s| s.starts_with("port="))
                    .and_then(|s| s.strip_prefix("port="))
                    .unwrap_or("0");
                return format!("{scheme}://{host}:{port}");
            }
        }
    }
    // Can't parse; return as-is (gio -u might handle it).
    path.to_string()
}

#[cfg(not(any(target_os = "windows", target_os = "macos", target_os = "linux")))]
async fn mount_os_drive(_server_url: &str, _auth_token: &str) -> AppResult<String> {
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
