use reqwest::{Client, Method};
use serde_json::Value;

use super::*;
use crate::error::{AppError, AppResult};
use crate::state::{Account, OcsUser, UserDetails, UserQuota};

pub async fn get_current_user(client: &Client, account: &Account) -> AppResult<OcsUser> {
    let url = format!("{}/ocs/v2.php/cloud/user?format=json", account.base_url());
    let res = request(client, account, Method::GET, &url, None).await?;
    let json: Value = res.json().await?;
    if let Some(msg) = ocs_meta_error(&json) {
        return Err(AppError::Ocs(msg));
    }
    let data = json
        .get("ocs")
        .and_then(|o| o.get("data"))
        .ok_or_else(|| AppError::Parse("missing ocs.data in user response".into()))?;
    let id = data
        .get("id")
        .and_then(|v| v.as_str())
        .map(String::from)
        .ok_or_else(|| AppError::Parse("missing user id".into()))?;
    let display_name = data
        .get("display-name")
        .and_then(|v| v.as_str())
        .map(String::from);
    let is_admin = data
        .get("isAdmin")
        .and_then(|v| v.as_bool())
        .unwrap_or(false);
    Ok(OcsUser {
        id,
        display_name,
        is_admin,
    })
}

/// Quota of the currently authenticated account. Works for any user (admin or
/// not) via the OCS v2 /cloud/user endpoint.
pub async fn get_current_quota(client: &Client, account: &Account) -> AppResult<Option<UserQuota>> {
    let url = format!("{}/ocs/v2.php/cloud/user?format=json", account.base_url());
    let res = request(client, account, Method::GET, &url, None).await?;
    let json: Value = res.json().await?;
    if let Some(msg) = ocs_meta_error(&json) {
        return Err(AppError::Ocs(msg));
    }
    Ok(json.pointer("/ocs/data/quota").map(|q| UserQuota {
        total: parse_u64(q.get("total")),
        used: parse_u64(q.get("used")),
        free: parse_u64(q.get("free")),
        relative: q.get("relative").and_then(|v| v.as_f64()),
    }))
}

/// Probe whether the account has admin rights by attempting to list users.
/// OCS v1 always responds with HTTP 200, so the success is judged from the
/// response body (`meta.statuscode`), not the HTTP status code.
pub async fn is_admin(client: &Client, account: &Account) -> AppResult<bool> {
    let url = format!(
        "{}/ocs/v1.php/cloud/users?format=json&limit=1",
        account.base_url()
    );
    match request(client, account, Method::GET, &url, None).await {
        Ok(res) => {
            let json: Value = res.json().await?;
            Ok(ocs_meta_error(&json).is_none())
        }
        Err(_) => Ok(false),
    }
}

pub async fn list_users(
    client: &Client,
    account: &Account,
    search: &str,
) -> AppResult<Vec<String>> {
    let mut url = format!(
        "{}/ocs/v1.php/cloud/users?format=json&limit=200",
        account.base_url()
    );
    if !search.is_empty() {
        url.push_str("&search=");
        url.push_str(&urlencoding::encode(search));
    }
    let res = request(client, account, Method::GET, &url, None).await?;
    let json: Value = res.json().await?;
    if let Some(msg) = ocs_meta_error(&json) {
        return Err(AppError::Ocs(msg));
    }
    let users = json
        .pointer("/ocs/data/users")
        .and_then(|u| u.as_array())
        .map(|arr| {
            arr.iter()
                .filter_map(|v| v.as_str().map(String::from))
                .collect()
        })
        .unwrap_or_default();
    Ok(users)
}

pub async fn get_user(client: &Client, account: &Account, user_id: &str) -> AppResult<UserDetails> {
    let url = format!(
        "{}/ocs/v1.php/cloud/users/{}?format=json",
        account.base_url(),
        urlencoding::encode(user_id)
    );
    let res = request(client, account, Method::GET, &url, None).await?;
    let json: Value = res.json().await?;
    if let Some(msg) = ocs_meta_error(&json) {
        return Err(AppError::Ocs(msg));
    }
    let data = json
        .pointer("/ocs/data")
        .ok_or_else(|| AppError::Parse("missing ocs.data in user response".into()))?;
    let id = data
        .get("id")
        .and_then(|v| v.as_str())
        .map(String::from)
        .unwrap_or_else(|| user_id.to_string());
    let display_name = data
        .get("display-name")
        .and_then(|v| v.as_str())
        .map(String::from);
    let email = data.get("email").and_then(|v| v.as_str()).map(String::from);
    let quota = data.get("quota").map(|q| UserQuota {
        total: parse_u64(q.get("total")),
        used: parse_u64(q.get("used")),
        free: parse_u64(q.get("free")),
        relative: q.get("relative").and_then(|v| v.as_f64()),
    });
    Ok(UserDetails {
        id,
        display_name,
        email,
        quota,
    })
}

/// Update a single user attribute (displayname, email, password, quota, ...)
/// via the OCS Provisioning API edit-user endpoint.
pub async fn update_user(
    client: &Client,
    account: &Account,
    user_id: &str,
    key: &str,
    value: &str,
) -> AppResult<String> {
    let url = format!(
        "{}/ocs/v1.php/cloud/users/{}?format=json",
        account.base_url(),
        urlencoding::encode(user_id)
    );
    let form = [("key", key), ("value", value)];
    let res = request(client, account, Method::PUT, &url, Some(&form)).await?;
    let json: Value = res.json().await?;
    if let Some(msg) = ocs_meta_error(&json) {
        return Err(AppError::Ocs(msg));
    }
    let message = json
        .pointer("/ocs/meta/message")
        .and_then(|m| m.as_str())
        .map(String::from)
        .unwrap_or_else(|| "User updated".to_string());
    Ok(message)
}

pub async fn set_user_quota(
    client: &Client,
    account: &Account,
    user_id: &str,
    quota_bytes: u64,
) -> AppResult<String> {
    update_user(client, account, user_id, "quota", &quota_bytes.to_string()).await
}

/// Create a public link share for a path relative to the user's files root.
pub async fn create_share(client: &Client, account: &Account, rel_path: &str) -> AppResult<String> {
    let url = format!(
        "{}/ocs/v2.php/apps/files_sharing/api/v1/shares?format=json",
        account.base_url()
    );
    let encoded_path = encode_segments(rel_path);
    let form = [
        ("path", encoded_path.as_str()),
        ("shareType", "3"),
        ("permissions", "1"),
    ];
    let res = request(client, account, Method::POST, &url, Some(&form)).await?;
    let json: Value = res.json().await?;
    if let Some(msg) = ocs_meta_error(&json) {
        return Err(AppError::Ocs(msg));
    }
    json.pointer("/ocs/data/url")
        .and_then(|u| u.as_str())
        .map(String::from)
        .ok_or_else(|| AppError::Parse("share endpoint returned no url".into()))
}

fn parse_u64(value: Option<&Value>) -> Option<u64> {
    value
        .and_then(|v| v.as_u64())
        .or_else(|| value.and_then(|v| v.as_str()).and_then(|s| s.parse().ok()))
}
