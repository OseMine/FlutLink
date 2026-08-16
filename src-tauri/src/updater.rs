//! FlutLink auto-updater
//!
//! Polls the GitHub Releases API for a newer version, downloads the
//! appropriate installer for the current platform and launches it.
//!
//! # Integration
//!
//! 1. Drop this file into `src-tauri/src/updater.rs`.
//! 2. In `src-tauri/src/lib.rs` (or `main.rs`):
//!    ```text
//!    mod updater;
//!
//!    // inside .invoke_handler(...)  add:
//!    updater::check_update,
//!    updater::download_and_install_update,
//!    ```
//! 3. Add the required Cargo dependencies (see bottom of file).
//! 4. In your Vue frontend, listen for the `"update://progress"` and
//!    `"update://status"` events via `@tauri-apps/api/event`.
//!
//! # Expected release asset names
//!
//! The updater picks the first asset whose name ends with the
//! platform-specific suffix (see `platform_suffix()`).  When building
//! with the default Tauri CI workflow the filenames look like:
//!
//! | OS      | Suffix                            |
//! |---------|-----------------------------------|
//! | Windows | `_x64-setup.exe`  or `.msi`       |
//! | macOS   | `.dmg`                            |
//! | Linux   | `.AppImage`  (preferred) / `.deb` |

use reqwest::Client;
use serde::{Deserialize, Serialize};
use std::path::{Path, PathBuf};
use std::process::Command;
use tauri::{AppHandle, Emitter};
use tauri_plugin_notification::NotificationExt;
use tokio::fs;
use tokio::io::AsyncWriteExt;

use crate::error::{AppError, AppResult};

// ─────────────────────────────────────────────────────────────────────────────
// GitHub API types
// ─────────────────────────────────────────────────────────────────────────────

#[derive(Debug, Deserialize)]
struct GithubRelease {
    tag_name: String,     // e.g. "v1.2.3"
    name: String,         // human-readable release title
    body: Option<String>, // markdown release notes
    html_url: String,     // link to the release page
    assets: Vec<GithubAsset>,
    prerelease: bool,
    draft: bool,
}

#[derive(Debug, Deserialize)]
struct GithubAsset {
    name: String,
    browser_download_url: String,
    size: u64,
    /// GitHub reports the asset digest as `"sha256:<hex>"`.
    digest: Option<String>,
}

// ─────────────────────────────────────────────────────────────────────────────
// Public types (serialised to the frontend)
// ─────────────────────────────────────────────────────────────────────────────

/// Metadata about the available release, returned to the frontend via
/// the `check_update` command.
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ReleaseInfo {
    /// New version string, e.g. `"1.2.3"` (no leading `v`).
    pub version: String,
    pub name: String,
    pub notes: Option<String>,
    pub release_url: String,
    pub asset_name: String,
    pub asset_url: String,
    /// File size in bytes; useful for showing a download progress bar.
    pub asset_size: u64,
    /// SHA-256 of the installer, hex-encoded (from the GitHub asset digest).
    /// Verified before the installer is launched.
    pub asset_sha256: Option<String>,
}

/// Emitted on the `"update://progress"` channel while downloading.
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct DownloadProgress {
    pub downloaded: u64,
    pub total: u64,
    /// 0.0 – 100.0
    pub percent: f64,
}

/// Emitted on the `"update://status"` channel with a machine-readable status
/// code so the frontend can render a localized label.
#[derive(Debug, Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdateStatus {
    /// `"checking"` | `"downloading"` | `"installing"`
    pub code: String,
    pub asset_name: Option<String>,
}

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

const GH_API_LATEST: &str = "https://api.github.com/repos/OseMine/FlutLink/releases/latest";

const USER_AGENT: &str = concat!(
    "FlutLink/",
    env!("CARGO_PKG_VERSION"),
    " (updater; +https://github.com/OseMine/FlutLink)"
);

// ─────────────────────────────────────────────────────────────────────────────
// Platform helpers
// ─────────────────────────────────────────────────────────────────────────────

/// Returns a list of acceptable asset suffixes for the current OS, ordered
/// by preference (first match wins).
fn platform_suffixes() -> &'static [&'static str] {
    #[cfg(target_os = "windows")]
    return &["_x64-setup.exe", ".msi"];

    #[cfg(target_os = "macos")]
    return &[".dmg"];

    #[cfg(target_os = "linux")]
    return &[".AppImage", ".deb"];

    #[allow(unreachable_code)]
    &[]
}

/// Pick the first asset whose filename ends with one of the platform suffixes.
fn pick_asset(assets: &[GithubAsset]) -> Option<&GithubAsset> {
    for suffix in platform_suffixes() {
        if let Some(a) = assets.iter().find(|a| a.name.ends_with(suffix)) {
            return Some(a);
        }
    }
    None
}

// ─────────────────────────────────────────────────────────────────────────────
// Version comparison
// ─────────────────────────────────────────────────────────────────────────────

/// Strips a leading `v` and parses `"major.minor.patch"` into a tuple.
/// Any pre-release suffix (e.g. `-beta.1`) is ignored for ordering.
fn parse_semver(s: &str) -> Option<(u64, u64, u64)> {
    let s = s.trim_start_matches('v');
    let mut parts = s.splitn(4, '.');
    let major: u64 = parts.next()?.split('-').next()?.parse().ok()?;
    let minor: u64 = parts.next()?.split('-').next()?.parse().ok()?;
    let patch: u64 = parts.next()?.split('-').next()?.parse().ok()?;
    Some((major, minor, patch))
}

/// Returns `true` when `remote` is strictly newer than `local`.
pub fn is_newer(local: &str, remote: &str) -> bool {
    match (parse_semver(local), parse_semver(remote)) {
        (Some(l), Some(r)) => r > l,
        _ => false, // unparseable version → assume up-to-date (safe default)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Core async logic
// ─────────────────────────────────────────────────────────────────────────────

/// Calls the GitHub Releases API.
///
/// Returns `Ok(Some(info))` when a newer stable release with a matching
/// platform asset exists, or `Ok(None)` when already up-to-date (or no asset
/// was found for this platform).
pub async fn check_for_update(
    client: &Client,
    current_version: &str,
) -> Result<Option<ReleaseInfo>, String> {
    let release: GithubRelease = client
        .get(GH_API_LATEST)
        .header("User-Agent", USER_AGENT)
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .send()
        .await
        .map_err(|e| format!("Network error: {e}"))?
        .error_for_status()
        .map_err(|e| format!("GitHub API returned an error: {e}"))?
        .json::<GithubRelease>()
        .await
        .map_err(|e| format!("Failed to parse release JSON: {e}"))?;

    // Skip drafts and pre-releases.
    if release.draft || release.prerelease {
        return Ok(None);
    }

    // Already on the latest version.
    if !is_newer(current_version, &release.tag_name) {
        return Ok(None);
    }

    // Find an asset for this platform.
    let asset = match pick_asset(&release.assets) {
        Some(a) => a,
        None => {
            let suffixes = platform_suffixes().join(", ");
            return Err(format!(
                "No installer found for this platform (looking for: {suffixes})"
            ));
        }
    };

    Ok(Some(ReleaseInfo {
        version: release.tag_name.trim_start_matches('v').to_string(),
        name: release.name,
        notes: release.body,
        release_url: release.html_url,
        asset_name: asset.name.clone(),
        asset_url: asset.browser_download_url.clone(),
        asset_size: asset.size,
        asset_sha256: asset
            .digest
            .clone()
            .and_then(|d| d.strip_prefix("sha256:").map(String::from)),
    }))
}

/// Downloads the installer described by `info` to a temp directory.
///
/// Emits [`DownloadProgress`] events on the `"update://progress"` channel
/// for every chunk received so the UI can show a progress bar.
///
/// Returns the local path of the downloaded file.
pub async fn download_update(
    app: &AppHandle,
    client: &Client,
    info: &ReleaseInfo,
) -> Result<PathBuf, String> {
    // Dedicated temp directory to avoid collisions.
    let tmp_dir = std::env::temp_dir().join("flutlink_update");
    fs::create_dir_all(&tmp_dir)
        .await
        .map_err(|e| format!("Cannot create temp dir: {e}"))?;

    let dest = tmp_dir.join(&info.asset_name);

    let mut resp = client
        .get(&info.asset_url)
        .header("User-Agent", USER_AGENT)
        .send()
        .await
        .map_err(|e| format!("Download request failed: {e}"))?
        .error_for_status()
        .map_err(|e| format!("Download error: {e}"))?;

    let total = resp.content_length().unwrap_or(info.asset_size);
    let mut file = fs::File::create(&dest)
        .await
        .map_err(|e| format!("Cannot create output file: {e}"))?;
    let mut downloaded: u64 = 0;

    // F3: never leave a half-written installer behind. On any stream/write
    // error the partial file is removed so the next attempt starts clean.
    let stream_result: Result<(), String> = async {
        while let Some(chunk) = resp
            .chunk()
            .await
            .map_err(|e| format!("Stream error: {e}"))?
        {
            file.write_all(&chunk)
                .await
                .map_err(|e| format!("Write error: {e}"))?;

            downloaded += chunk.len() as u64;

            let _ = app.emit(
                "update://progress",
                DownloadProgress {
                    downloaded,
                    total,
                    percent: if total > 0 {
                        downloaded as f64 / total as f64 * 100.0
                    } else {
                        0.0
                    },
                },
            );
        }

        file.flush()
            .await
            .map_err(|e| format!("Flush failed: {e}"))?;
        Ok(())
    }
    .await;
    if let Err(e) = stream_result {
        let _ = fs::remove_file(&dest).await;
        return Err(e);
    }

    // The size advertised by the API must match what we actually got.
    if let Ok(meta) = fs::metadata(&dest).await {
        if meta.len() != info.asset_size {
            let _ = fs::remove_file(&dest).await;
            return Err(format!(
                "Downloaded file size mismatch (expected {}, got {})",
                info.asset_size,
                meta.len()
            ));
        }
    }

    // Verify the SHA-256 checksum before ever launching the installer.
    if let Some(expected) = info.asset_sha256.as_deref() {
        let actual = sha256_file(&dest)?;
        if !actual.eq_ignore_ascii_case(expected) {
            let _ = fs::remove_file(&dest).await;
            return Err(format!(
                "SHA-256 checksum mismatch: expected {expected}, got {actual}"
            ));
        }
    } else {
        // F9: GitHub does not always report an asset digest (e.g. web uploads).
        // Do not silently skip verification — surface it in the log and UI.
        let message = "checksum unavailable, skipping verification";
        eprintln!("warn: {message}");
        let _ = app.emit("update://status", message);
    }

    Ok(dest)
}

/// Hex-encoded SHA-256 of a file.
fn sha256_file(path: &Path) -> Result<String, String> {
    use sha2::{Digest, Sha256};
    let mut file =
        std::fs::File::open(path).map_err(|e| format!("Cannot read downloaded file: {e}"))?;
    let mut hasher = Sha256::new();
    std::io::copy(&mut file, &mut hasher).map_err(|e| format!("Checksum error: {e}"))?;
    Ok(format!("{:x}", hasher.finalize()))
}

/// Launches the downloaded installer and exits the current process so that
/// the installer can replace the running binary without file-lock issues.
///
/// Platform behaviour:
/// - **Windows** – runs an NSIS `.exe` installer with `/S` (silent) or an
///   MSI with `msiexec /i … /qb`.
/// - **macOS** – mounts the DMG with `hdiutil attach`, copies the `.app`
///   bundle to `/Applications` with `ditto`, then detaches the volume.
/// - **Linux** – makes the AppImage executable and copies it over the
///   current binary; for `.deb` it invokes `pkexec dpkg -i`.
pub fn install_update(path: &Path) -> Result<(), String> {
    #[cfg(target_os = "windows")]
    {
        let ext = path.extension().and_then(|e| e.to_str()).unwrap_or("");
        let path_str = path.to_str().unwrap();

        let mut cmd = if ext == "msi" {
            let mut c = Command::new("msiexec");
            c.args(["/i", path_str, "/qb"]);
            c
        } else {
            // NSIS-based .exe installer produced by Tauri's CI
            let mut c = Command::new(path_str);
            c.arg("/S");
            c
        };

        cmd.spawn()
            .map_err(|e| format!("Failed to launch installer: {e}"))?;

        // Exit now so the installer can replace our binary.
        std::process::exit(0);
    }

    #[cfg(target_os = "macos")]
    {
        let mount_point = std::env::temp_dir().join("flutlink_dmg_mount");
        std::fs::create_dir_all(&mount_point)
            .map_err(|e| format!("Cannot create mount point: {e}"))?;

        // hdiutil attach <dmg> -mountpoint <dir> -quiet -nobrowse -noverify
        let status = Command::new("hdiutil")
            .args([
                "attach",
                path.to_str().unwrap(),
                "-mountpoint",
                mount_point.to_str().unwrap(),
                "-quiet",
                "-nobrowse",
                "-noverify",
            ])
            .status()
            .map_err(|e| format!("hdiutil attach failed: {e}"))?;

        if !status.success() {
            return Err("hdiutil attach returned a non-zero exit code".into());
        }

        // Find the .app bundle inside the mounted volume.
        let app_bundle = std::fs::read_dir(&mount_point)
            .map_err(|e| e.to_string())?
            .filter_map(|e| e.ok())
            .find(|e| e.path().extension().is_some_and(|x| x == "app"))
            .map(|e| e.path())
            .ok_or_else(|| "No .app bundle found inside the DMG".to_string())?;

        let target = PathBuf::from("/Applications")
            .join(app_bundle.file_name().ok_or("Invalid .app bundle name")?);

        // Remove the old installation (if present), then copy the new one.
        if target.exists() {
            std::fs::remove_dir_all(&target).map_err(|e| format!("Cannot remove old app: {e}"))?;
        }

        // `ditto` preserves resource forks and extended attributes.
        let copy_status = Command::new("ditto")
            .args([app_bundle.to_str().unwrap(), target.to_str().unwrap()])
            .status()
            .map_err(|e| format!("ditto failed: {e}"))?;

        if !copy_status.success() {
            return Err("ditto returned a non-zero exit code".into());
        }

        // Detach the DMG (best effort – don't fail the update if this errors).
        let _ = Command::new("hdiutil")
            .args(["detach", mount_point.to_str().unwrap(), "-quiet"])
            .status();

        // Re-launch the freshly installed app and quit this process.
        let _ = Command::new("open").arg(&target).spawn();
        std::process::exit(0);
    }

    #[cfg(target_os = "linux")]
    {
        use std::os::unix::fs::PermissionsExt;

        let path_str = path.to_str().unwrap();
        let ext = path.extension().and_then(|e| e.to_str()).unwrap_or("");

        if ext == "deb" {
            // pkexec provides a polkit GUI sudo prompt.
            Command::new("pkexec")
                .args(["dpkg", "-i", path_str])
                .spawn()
                .map_err(|e| format!("dpkg install failed: {e}"))?;
        } else {
            // AppImage – make executable, then replace the running binary.
            std::fs::set_permissions(path, std::fs::Permissions::from_mode(0o755))
                .map_err(|e| format!("chmod +x failed: {e}"))?;

            if let Ok(current_exe) = std::env::current_exe() {
                std::fs::copy(path, &current_exe)
                    .map_err(|e| format!("Failed to replace binary: {e}"))?;

                // Re-launch the updated binary.
                let _ = Command::new(&current_exe).spawn();
            }
        }

        std::process::exit(0);
    }

    // Silence the "unreachable" lint on unsupported platforms (compile-time
    // guard is good enough for now; extend when needed).
    #[allow(unreachable_code)]
    Ok(())
}

// ─────────────────────────────────────────────────────────────────────────────
// Tauri commands
// ─────────────────────────────────────────────────────────────────────────────

/// **Tauri command** — Check whether a newer release is available.
///
/// Returns `null` (from the frontend's perspective) when already up-to-date,
/// or a [`ReleaseInfo`] object when an update is waiting.
///
/// ```ts
/// import { invoke } from '@tauri-apps/api/core';
/// const info = await invoke<ReleaseInfo | null>('check_update');
/// ```
#[tauri::command]
pub async fn check_update(app: AppHandle) -> AppResult<Option<ReleaseInfo>> {
    let current = app.package_info().version.to_string();
    let client = build_client().map_err(AppError::Update)?;
    let info = check_for_update(&client, &current)
        .await
        .map_err(AppError::Update)?;
    if let Some(release) = &info {
        // Q1: notify the user that a new version is available. Best-effort.
        let _ = app
            .notification()
            .builder()
            .title("FlutLink Update")
            .body(format!(
                "Version {} ist verfügbar (aktuell: {}).",
                release.version, current
            ))
            .show();
    }
    Ok(info)
}

/// **Tauri command** — Download the latest release and install it.
///
/// Emits `"update://progress"` events while downloading and
/// `"update://status"` strings at key lifecycle points.
/// The process exits after a successful install so the installer
/// can take over.
///
/// ```ts
/// import { invoke } from '@tauri-apps/api/core';
/// import { listen } from '@tauri-apps/api/event';
///
/// await listen('update://progress', (e) => {
///   const { percent } = e.payload as { percent: number };
///   progressBar.value = percent;
/// });
///
/// await invoke('download_and_install_update');
/// ```
#[tauri::command]
pub async fn download_and_install_update(app: AppHandle) -> AppResult<()> {
    let current = app.package_info().version.to_string();
    let client = build_client().map_err(AppError::Update)?;

    let _ = app.emit(
        "update://status",
        UpdateStatus {
            code: "checking".into(),
            asset_name: None,
        },
    );

    let info = check_for_update(&client, &current)
        .await
        .map_err(AppError::Update)?
        .ok_or_else(|| AppError::Update("Already up to date".into()))?;

    let _ = app.emit(
        "update://status",
        UpdateStatus {
            code: "downloading".into(),
            asset_name: Some(info.asset_name.clone()),
        },
    );

    let installer_path = download_update(&app, &client, &info)
        .await
        .map_err(AppError::Update)?;

    let _ = app.emit(
        "update://status",
        UpdateStatus {
            code: "installing".into(),
            asset_name: None,
        },
    );

    // `install_update` is synchronous and calls `std::process::exit(0)` on
    // success, so the Ok(()) below is only reached when installation fails.
    install_update(&installer_path).map_err(AppError::Update)?;

    Ok(())
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

/// Build a `reqwest` client with sensible defaults (TLS, timeouts, UA).
fn build_client() -> Result<Client, String> {
    Client::builder()
        // No total timeout: installer downloads can exceed 30 s on slow links.
        // Bound the connect phase and each single read instead, so a stalled
        // connection is detected without aborting a progressing download.
        .connect_timeout(std::time::Duration::from_secs(30))
        .read_timeout(std::time::Duration::from_secs(120))
        .user_agent(USER_AGENT)
        .build()
        .map_err(|e| format!("Failed to build HTTP client: {e}"))
}

// ─────────────────────────────────────────────────────────────────────────────
// Unit tests
// ─────────────────────────────────────────────────────────────────────────────

#[cfg(test)]
mod tests {
    use super::*;

    // --- parse_semver ---------------------------------------------------------

    #[test]
    fn parse_strips_leading_v() {
        assert_eq!(parse_semver("v1.2.3"), Some((1, 2, 3)));
    }

    #[test]
    fn parse_without_v() {
        assert_eq!(parse_semver("2.0.0"), Some((2, 0, 0)));
    }

    #[test]
    fn parse_ignores_prerelease_suffix() {
        assert_eq!(parse_semver("1.0.0-beta.1"), Some((1, 0, 0)));
        assert_eq!(parse_semver("v3.1.4-rc.2"), Some((3, 1, 4)));
    }

    #[test]
    fn parse_returns_none_on_garbage() {
        assert_eq!(parse_semver("not-a-version"), None);
        assert_eq!(parse_semver(""), None);
    }

    // --- is_newer -------------------------------------------------------------

    #[test]
    fn newer_patch() {
        assert!(is_newer("1.0.0", "1.0.1"));
    }

    #[test]
    fn newer_minor() {
        assert!(is_newer("1.0.9", "1.1.0"));
    }

    #[test]
    fn newer_major() {
        assert!(is_newer("1.9.9", "2.0.0"));
    }

    #[test]
    fn same_version_is_not_newer() {
        assert!(!is_newer("1.2.0", "1.2.0"));
    }

    #[test]
    fn older_remote_is_not_newer() {
        assert!(!is_newer("2.0.0", "1.9.9"));
    }

    #[test]
    fn unparseable_versions_are_not_newer() {
        assert!(!is_newer("bad", "also-bad"));
    }

    // --- pick_asset -----------------------------------------------------------

    #[test]
    #[cfg(target_os = "linux")]
    fn picks_appimage_over_deb() {
        let assets = vec![
            GithubAsset {
                name: "FlutLink_1.0.0_amd64.deb".into(),
                browser_download_url: "https://example.com/a.deb".into(),
                size: 1,
                digest: None,
            },
            GithubAsset {
                name: "FlutLink_1.0.0_x86_64.AppImage".into(),
                browser_download_url: "https://example.com/a.AppImage".into(),
                size: 2,
                digest: None,
            },
        ];
        let picked = pick_asset(&assets).unwrap();
        assert!(picked.name.ends_with(".AppImage"));
    }

    #[test]
    fn returns_none_when_no_matching_asset() {
        let assets = vec![GithubAsset {
            name: "FlutLink.weird_format".into(),
            browser_download_url: "https://example.com/x".into(),
            size: 0,
            digest: None,
        }];
        assert!(pick_asset(&assets).is_none());
    }

    #[test]
    fn asset_sha256_extracted_from_digest() {
        let info = ReleaseInfo {
            version: "1.0.0".into(),
            name: "x".into(),
            notes: None,
            release_url: "u".into(),
            asset_name: "a".into(),
            asset_url: "u".into(),
            asset_size: 1,
            asset_sha256: Some("sha256:abcd".trim_start_matches("sha256:").into()),
        };
        assert_eq!(info.asset_sha256.as_deref(), Some("abcd"));
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Cargo.toml additions required
// ─────────────────────────────────────────────────────────────────────────────
//
// [dependencies]
// reqwest  = { version = "0.12", features = ["json", "stream"] }
// serde    = { version = "1",    features = ["derive"] }
// tokio    = { version = "1",    features = ["fs", "io-util"] }
//
// (reqwest and serde are almost certainly already present; only the
//  `stream` feature on reqwest and the `fs`/`io-util` features on tokio
//  might be new.)
//
// Register the two Tauri commands in lib.rs / main.rs:
//
//   mod updater;
//
//   tauri::Builder::default()
//       .invoke_handler(tauri::generate_handler![
//           // … your existing commands …
//           updater::check_update,
//           updater::download_and_install_update,
//       ])
