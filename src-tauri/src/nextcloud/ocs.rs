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

/// List all users, paging through the OCS `offset`/`limit` parameters so the
/// result is not truncated at the server's hard limit of 200 per request.
pub async fn list_users(
    client: &Client,
    account: &Account,
    search: &str,
) -> AppResult<Vec<String>> {
    const LIMIT: usize = 200;
    let mut all: Vec<String> = Vec::new();
    let mut offset = 0usize;
    loop {
        let mut url = format!(
            "{}/ocs/v1.php/cloud/users?format=json&limit={}&offset={}",
            account.base_url(),
            LIMIT,
            offset
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
        let users: Vec<String> = json
            .pointer("/ocs/data/users")
            .and_then(|u| u.as_array())
            .map(|arr| {
                arr.iter()
                    .filter_map(|v| v.as_str().map(String::from))
                    .collect()
            })
            .unwrap_or_default();
        if users.is_empty() {
            break;
        }
        let count = users.len();
        all.extend(users);
        if count < LIMIT {
            break;
        }
        offset += LIMIT;
    }
    Ok(all)
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
    let groups = data
        .get("groups")
        .and_then(|g| g.as_array())
        .map(|arr| {
            arr.iter()
                .filter_map(|v| v.as_str().map(String::from))
                .collect()
        })
        .unwrap_or_default();
    let enabled = data
        .get("enabled")
        .and_then(|v| v.as_bool())
        .unwrap_or(true);
    Ok(UserDetails {
        id,
        display_name,
        email,
        quota,
        groups,
        enabled,
    })
}

/// Create a user via the OCS Provisioning API (POST /cloud/users).
pub async fn create_user(
    client: &Client,
    account: &Account,
    user_id: &str,
    password: &str,
    display_name: Option<&str>,
) -> AppResult<String> {
    let url = format!("{}/ocs/v1.php/cloud/users?format=json", account.base_url());
    let mut form: Vec<(&str, &str)> = vec![("userid", user_id), ("password", password)];
    if let Some(name) = display_name {
        if !name.is_empty() {
            form.push(("displayName", name));
        }
    }
    let res = request(client, account, Method::POST, &url, Some(&form)).await?;
    let json: Value = res.json().await?;
    if let Some(msg) = ocs_meta_error(&json) {
        return Err(AppError::Ocs(msg));
    }
    Ok(json
        .pointer("/ocs/meta/message")
        .and_then(|m| m.as_str())
        .map(String::from)
        .unwrap_or_else(|| "User created".to_string()))
}

/// Delete a user via the OCS Provisioning API (DELETE /cloud/users/{id}).
pub async fn delete_user(client: &Client, account: &Account, user_id: &str) -> AppResult<String> {
    let url = format!(
        "{}/ocs/v1.php/cloud/users/{}?format=json",
        account.base_url(),
        urlencoding::encode(user_id)
    );
    let res = request(client, account, Method::DELETE, &url, None).await?;
    let json: Value = res.json().await?;
    if let Some(msg) = ocs_meta_error(&json) {
        return Err(AppError::Ocs(msg));
    }
    Ok(json
        .pointer("/ocs/meta/message")
        .and_then(|m| m.as_str())
        .map(String::from)
        .unwrap_or_else(|| "User deleted".to_string()))
}

/// List all groups, paging through the OCS `offset`/`limit` parameters so the
/// result is not truncated at the server's hard limit of 200 per request.
pub async fn list_groups(
    client: &Client,
    account: &Account,
    search: &str,
) -> AppResult<Vec<String>> {
    const LIMIT: usize = 200;
    let mut all: Vec<String> = Vec::new();
    let mut seen: std::collections::HashSet<String> = std::collections::HashSet::new();
    let mut offset = 0usize;
    loop {
        let mut url = format!(
            "{}/ocs/v1.php/cloud/groups?format=json&limit={}&offset={}",
            account.base_url(),
            LIMIT,
            offset
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
        let groups: Vec<String> = json
            .pointer("/ocs/data/groups")
            .and_then(|g| g.as_array())
            .map(|arr| {
                arr.iter()
                    .filter_map(|v| v.as_str().map(String::from))
                    .collect()
            })
            .unwrap_or_default();
        if groups.is_empty() {
            break;
        }
        let mut new_groups = 0usize;
        for group in groups {
            if seen.insert(group.clone()) {
                all.push(group);
                new_groups += 1;
            }
        }
        // Guard against servers that ignore `offset` and return the same page
        // again: stop instead of looping forever on duplicate pages.
        if new_groups < LIMIT {
            break;
        }
        offset += LIMIT;
    }
    Ok(all)
}

/// Create a group via the OCS Provisioning API (POST /cloud/groups).
pub async fn create_group(client: &Client, account: &Account, group_id: &str) -> AppResult<String> {
    let url = format!("{}/ocs/v1.php/cloud/groups?format=json", account.base_url());
    let form = [("groupid", group_id)];
    let res = request(client, account, Method::POST, &url, Some(&form)).await?;
    let json: Value = res.json().await?;
    if let Some(msg) = ocs_meta_error(&json) {
        return Err(AppError::Ocs(msg));
    }
    Ok(json
        .pointer("/ocs/meta/message")
        .and_then(|m| m.as_str())
        .map(String::from)
        .unwrap_or_else(|| "Group created".to_string()))
}

/// Add a user to a group via the OCS Provisioning API
/// (POST /cloud/groups/{groupId}/users).
pub async fn add_group_member(
    client: &Client,
    account: &Account,
    group_id: &str,
    user_id: &str,
) -> AppResult<String> {
    let url = format!(
        "{}/ocs/v1.php/cloud/groups/{}?format=json",
        account.base_url(),
        urlencoding::encode(group_id)
    );
    let form = [("userid", user_id)];
    let res = request(client, account, Method::POST, &url, Some(&form)).await?;
    let json: Value = res.json().await?;
    if let Some(msg) = ocs_meta_error(&json) {
        return Err(AppError::Ocs(msg));
    }
    Ok(json
        .pointer("/ocs/meta/message")
        .and_then(|m| m.as_str())
        .map(String::from)
        .unwrap_or_else(|| "User added to group".to_string()))
}

/// Remove a user from a group via the OCS Provisioning API
/// (DELETE /cloud/groups/{groupId}/users/{userId}).
pub async fn remove_group_member(
    client: &Client,
    account: &Account,
    group_id: &str,
    user_id: &str,
) -> AppResult<String> {
    let url = format!(
        "{}/ocs/v1.php/cloud/groups/{}/users/{}?format=json",
        account.base_url(),
        urlencoding::encode(group_id),
        urlencoding::encode(user_id)
    );
    let res = request(client, account, Method::DELETE, &url, None).await?;
    let json: Value = res.json().await?;
    if let Some(msg) = ocs_meta_error(&json) {
        return Err(AppError::Ocs(msg));
    }
    Ok(json
        .pointer("/ocs/meta/message")
        .and_then(|m| m.as_str())
        .map(String::from)
        .unwrap_or_else(|| "User removed from group".to_string()))
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

/// Create a public link share for a path relative to the user's files root.
///
/// `target_user` switches the share to another user's files (admin
/// impersonation); the share is then attributed to that user's namespace.
pub async fn create_share(
    client: &Client,
    account: &Account,
    rel_path: &str,
    target_user: Option<&str>,
) -> AppResult<String> {
    let url = format!(
        "{}/ocs/v2.php/apps/files_sharing/api/v1/shares?format=json",
        account.base_url()
    );
    // F4: the path goes into the form UNENCODED. `req.form()` applies a single
    // form-url-encoding pass; PHP decodes it once, so the server receives the
    // raw path ("My Folder"). Encoding here a second time (encode_segments)
    // would produce "%2520" → "path not found" for any path with spaces,
    // umlauts or `#`/`&`/`+`/`?`.
    let form = [("path", rel_path), ("shareType", "3"), ("permissions", "1")];
    let res = request_as(
        client,
        account,
        Method::POST,
        &url,
        Some(&form),
        target_user,
    )
    .await?;
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

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn single_form_encoding_preserves_special_characters() {
        // F4: `req.form()` (serde_urlencoded) performs the single encoding pass
        // and PHP decodes it once, so the raw path must survive a roundtrip.
        let raw = "/My Folder/#test&more+file?.txt";
        let encoded = urlencoding::encode(raw);
        let decoded = urlencoding::decode(&encoded).unwrap();
        assert_eq!(decoded.as_ref(), raw);
    }

    #[test]
    fn pre_encoding_the_path_double_encodes() {
        // The old bug: encode_segments() + form-url-encoding turned "%20" into
        // "%2520", so the server looked for "My%20Folder" instead of "My Folder".
        let raw = "/My Folder";
        let pre_encoded = encode_segments(raw);
        let wire = urlencoding::encode(&pre_encoded);
        let after_server_decode = urlencoding::decode(&wire).unwrap();
        assert_ne!(after_server_decode.as_ref(), raw);
        assert_eq!(after_server_decode.as_ref(), "My%20Folder");
    }
}
