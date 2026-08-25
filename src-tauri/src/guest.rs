//! Guest access to completely public shares ("complete public shares").
//!
//! Guests browse the FlutCloud server without an account: folders that are
//! shared publicly as a whole (password-free link shares) are listed in one
//! bundled view and can be opened read-only. All requests run anonymously
//! against the fixed FlutCloud server ([`flutcloud_url`]); write operations
//! do not exist on this code path at all and are additionally rejected by
//! the server (FlutCloud app + read-only link permissions).
//!
//! The `admin_*` functions in this module are for the logged-in admin to
//! manage categories, share-to-category assignments, and per-share subfolder
//! locks on the FlutCloud server.

use std::path::Path;

use reqwest::{Client, Method};
use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::error::{AppError, AppResult};
use crate::nextcloud;
use crate::state::Account;

/// Feature announced by the FlutCloud app when guest access is available.
pub const GUEST_FEATURE: &str = "complete-public-shares";

/// A folder that is shared publicly as a whole.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct GuestShare {
    pub token: String,
    pub name: String,
    pub owner: String,
    pub owner_display: Option<String>,
    pub category: Option<String>,
    pub url: String,
    /// Base of the anonymous WebDAV download endpoint for this share
    /// (`/public.php/webdav/<token>`), without a trailing slash.
    pub download_base: String,
    pub mtime: Option<i64>,
}

/// One entry inside a public share folder.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct GuestEntry {
    pub name: String,
    pub path: String,
    pub is_dir: bool,
    pub size: Option<u64>,
    pub mtime: Option<i64>,
    pub content_type: Option<String>,
}

/// Folder listing inside a public share.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct GuestListing {
    pub token: String,
    pub name: String,
    pub path: String,
    pub entries: Vec<GuestEntry>,
}

fn base_url() -> AppResult<String> {
    Ok(crate::flutcloud::flutcloud_url()?
        .trim_end_matches('/')
        .to_string())
}

/// Anonymous request against the fixed FlutCloud server (no account).
async fn guest_request(client: &Client, method: Method, url: &str) -> AppResult<reqwest::Response> {
    let res = client
        .request(method, url)
        .header("OCS-APIRequest", "true")
        .header("Accept", "application/json")
        .send()
        .await?;
    let status = res.status();
    if status.is_success() {
        Ok(res)
    } else {
        let body = res.text().await.unwrap_or_default();
        Err(AppError::Status {
            status: status.as_u16(),
            body,
        })
    }
}

/// Unwrap the OCS envelope after checking `meta`.
fn ocs_data(json: Value) -> AppResult<Value> {
    if let Some(msg) = nextcloud::ocs_meta_error(&json) {
        return Err(AppError::Ocs(msg));
    }
    Ok(json.pointer("/ocs/data").cloned().unwrap_or(Value::Null))
}

/// Reject empty tokens and path traversal up front; the server enforces the
/// same rules again (defense in depth).
pub fn validate_guest_target(token: &str, rel_path: &str) -> AppResult<()> {
    if token.trim().is_empty() || token.contains('/') {
        return Err(AppError::App("Invalid share token.".into()));
    }
    for segment in rel_path.split('/') {
        if segment == ".." {
            return Err(AppError::App("Path must not contain '..'.".into()));
        }
    }
    Ok(())
}

/// Verify that the fixed server is a FlutCloud server that supports guest
/// access (anonymous ping probe + feature check). Keeps the FlutCloud-only
/// policy intact for guests.
pub async fn verify_guest_server(client: &Client) -> AppResult<()> {
    let url = format!("{}/ocs/v2.php/apps/flutcloud/api/v1/ping", base_url()?);
    let res = guest_request(client, Method::GET, &url).await?;
    let json: Value = res.json().await?;
    let data = ocs_data(json)?;
    let supported = data
        .get("features")
        .and_then(|f| f.as_array())
        .is_some_and(|features| {
            features
                .iter()
                .filter_map(|v| v.as_str())
                .any(|f| f == GUEST_FEATURE)
        });
    if supported {
        Ok(())
    } else {
        // The app is installed but too old for guest access.
        Err(AppError::FlutCloudAppMissing)
    }
}

/// Every completely public share in one bundled list.
pub async fn list_shares(client: &Client) -> AppResult<Vec<GuestShare>> {
    let url = format!(
        "{}/ocs/v2.php/apps/flutcloud/api/v1/public?format=json",
        base_url()?
    );
    let res = guest_request(client, Method::GET, &url).await?;
    let json: Value = res.json().await?;
    let data = ocs_data(json)?;
    let shares = data
        .get("shares")
        .and_then(|s| s.as_array())
        .cloned()
        .unwrap_or_default();
    Ok(serde_json::from_value(Value::Array(shares))?)
}

/// Browse into a public share (`path` relative to the share root).
pub async fn list_entries(client: &Client, token: &str, path: &str) -> AppResult<GuestListing> {
    validate_guest_target(token, path)?;
    let url = format!(
        "{}/ocs/v2.php/apps/flutcloud/api/v1/public/{}?format=json&path={}",
        base_url()?,
        urlencoding::encode(token),
        nextcloud::encode_segments(path)
    );
    let res = guest_request(client, Method::GET, &url).await?;
    let json: Value = res.json().await?;
    let data = ocs_data(json)?;
    Ok(serde_json::from_value(data)?)
}

/// Stream a file from the share's anonymous WebDAV endpoint to `dest`
/// (temp + rename, mirroring the transfer helpers in `webdav.rs`).
pub async fn download_file(
    client: &Client,
    token: &str,
    rel_path: &str,
    dest: &Path,
) -> AppResult<()> {
    validate_guest_target(token, rel_path)?;
    let url = format!(
        "{}/public.php/webdav/{}/{}",
        base_url()?,
        urlencoding::encode(token),
        nextcloud::encode_segments(rel_path)
    );
    let mut res = client.get(url).basic_auth(token, Some("")).send().await?;
    let status = res.status();
    if !status.is_success() {
        let body = res.text().await.unwrap_or_default();
        return Err(AppError::Status {
            status: status.as_u16(),
            body,
        });
    }

    if let Some(parent) = dest.parent() {
        std::fs::create_dir_all(parent)?;
    }
    let tmp = dest.with_extension("flutlink-part");
    {
        use std::io::Write;
        let mut file = std::fs::File::create(&tmp)?;
        while let Some(chunk) = res.chunk().await? {
            file.write_all(&chunk)?;
        }
        file.sync_all()?;
    }
    std::fs::rename(&tmp, dest)?;
    Ok(())
}

// ---------------------------------------------------------------------------
// Admin operations (require authenticated admin session)
// ---------------------------------------------------------------------------

/// Admin: create or update a public-share category.
pub async fn set_category(
    client: &Client,
    account: &Account,
    name: &str,
    prefixless: bool,
) -> AppResult<()> {
    let url = format!(
        "{}/ocs/v2.php/apps/flutcloud/api/v1/public/categories",
        base_url()?
    );
    let form = [
        ("name", name),
        ("prefixless", if prefixless { "true" } else { "false" }),
    ];
    let res = nextcloud::request(client, account, Method::POST, &url, Some(&form)).await?;
    let json: Value = res.json().await?;
    if let Some(msg) = nextcloud::ocs_meta_error(&json) {
        return Err(AppError::Ocs(msg));
    }
    Ok(())
}

/// Admin: delete a public-share category.
pub async fn delete_category(client: &Client, account: &Account, name: &str) -> AppResult<()> {
    let url = format!(
        "{}/ocs/v2.php/apps/flutcloud/api/v1/public/categories/{}",
        base_url()?,
        urlencoding::encode(name)
    );
    let res = nextcloud::request(client, account, Method::DELETE, &url, None).await?;
    let json: Value = res.json().await?;
    if let Some(msg) = nextcloud::ocs_meta_error(&json) {
        return Err(AppError::Ocs(msg));
    }
    Ok(())
}

/// Admin: assign a complete public share to a category.
pub async fn assign_category(
    client: &Client,
    account: &Account,
    token: &str,
    category: &str,
) -> AppResult<()> {
    validate_guest_target(token, "/")?;
    let url = format!(
        "{}/ocs/v2.php/apps/flutcloud/api/v1/public/shares/{}/category",
        base_url()?,
        urlencoding::encode(token)
    );
    let form = [("category", category)];
    let res = nextcloud::request(client, account, Method::POST, &url, Some(&form)).await?;
    let json: Value = res.json().await?;
    if let Some(msg) = nextcloud::ocs_meta_error(&json) {
        return Err(AppError::Ocs(msg));
    }
    Ok(())
}

/// Admin: remove a share's category assignment.
pub async fn unassign_category(client: &Client, account: &Account, token: &str) -> AppResult<()> {
    validate_guest_target(token, "/")?;
    let url = format!(
        "{}/ocs/v2.php/apps/flutcloud/api/v1/public/shares/{}/category",
        base_url()?,
        urlencoding::encode(token)
    );
    let res = nextcloud::request(client, account, Method::DELETE, &url, None).await?;
    let json: Value = res.json().await?;
    if let Some(msg) = nextcloud::ocs_meta_error(&json) {
        return Err(AppError::Ocs(msg));
    }
    Ok(())
}

/// Admin: lock a subfolder of a share (recursive). Returns the updated lock
/// list.
pub async fn lock_path(
    client: &Client,
    account: &Account,
    token: &str,
    path: &str,
) -> AppResult<Vec<String>> {
    validate_guest_target(token, path)?;
    let url = format!(
        "{}/ocs/v2.php/apps/flutcloud/api/v1/public/shares/{}/lock",
        base_url()?,
        urlencoding::encode(token)
    );
    let form = [("path", path)];
    let res = nextcloud::request(client, account, Method::POST, &url, Some(&form)).await?;
    let json: Value = res.json().await?;
    if let Some(msg) = nextcloud::ocs_meta_error(&json) {
        return Err(AppError::Ocs(msg));
    }
    parse_locks(&json)
}

/// Admin: unlock a subfolder of a share. Returns the updated lock list.
pub async fn unlock_path(
    client: &Client,
    account: &Account,
    token: &str,
    path: &str,
) -> AppResult<Vec<String>> {
    validate_guest_target(token, path)?;
    let url = format!(
        "{}/ocs/v2.php/apps/flutcloud/api/v1/public/shares/{}/lock",
        base_url()?,
        urlencoding::encode(token)
    );
    let form = [("path", path)];
    let res = nextcloud::request(client, account, Method::DELETE, &url, Some(&form)).await?;
    let json: Value = res.json().await?;
    if let Some(msg) = nextcloud::ocs_meta_error(&json) {
        return Err(AppError::Ocs(msg));
    }
    parse_locks(&json)
}

/// Extract the `locks` array from an OCS lock/unlock response.
fn parse_locks(json: &Value) -> AppResult<Vec<String>> {
    let data = json.pointer("/ocs/data").cloned().unwrap_or(Value::Null);
    Ok(data
        .get("locks")
        .and_then(|l| l.as_array())
        .cloned()
        .unwrap_or_default()
        .into_iter()
        .filter_map(|v| v.as_str().map(String::from))
        .collect())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rejects_bad_tokens_and_traversal() {
        assert!(validate_guest_target("", "/x").is_err());
        assert!(validate_guest_target("a/b", "/x").is_err());
        assert!(validate_guest_target("tok", "/a/../b").is_err());
        assert!(validate_guest_target("tok", "../escape").is_err());
        assert!(validate_guest_target("tok", "/sub/file.txt").is_ok());
        assert!(validate_guest_target("tok", "/").is_ok());
    }
}
