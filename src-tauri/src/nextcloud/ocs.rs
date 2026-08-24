use std::collections::HashSet;

use reqwest::{Client, Method};
use serde_json::Value;

use super::*;
use crate::error::{AppError, AppResult};
use crate::state::{Account, OcsUser, Share, UserDetails, UserQuota};

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
///
/// `Ok(false)` means the server answered and denied the request (the account
/// is a regular user). Network/parse failures propagate as `Err` so callers can
/// distinguish "not an admin" from "status unknown" and keep the previously
/// stored flag instead of demoting an admin account on a transient error.
pub async fn is_admin(client: &Client, account: &Account) -> AppResult<bool> {
    let url = format!(
        "{}/ocs/v1.php/cloud/users?format=json&limit=1",
        account.base_url()
    );
    let res = request(client, account, Method::GET, &url, None).await?;
    let json: Value = res.json().await?;
    Ok(ocs_meta_error(&json).is_none())
}

/// List users, paging through the OCS `offset`/`limit` parameters so the
/// result is not truncated at the server's hard limit of 200 per request.
///
/// With `limit = Some(n)` only a single page starting at `offset` is fetched
/// and the second return value reports whether a full page came back (so the
/// caller can fetch the next one). Without a limit every page is fetched, which
/// is used by callers that need the complete list.
pub async fn list_users(
    client: &Client,
    account: &Account,
    search: &str,
    limit: Option<usize>,
    offset: usize,
) -> AppResult<(Vec<String>, bool)> {
    const PAGE: usize = 200;
    if let Some(limit) = limit {
        if limit == 0 {
            return Ok((Vec::new(), false));
        }
        let page = list_users_page(client, account, search, offset, limit).await?;
        return Ok((page.clone(), page.len() == limit));
    }
    let mut all: Vec<String> = Vec::new();
    let mut seen: HashSet<String> = HashSet::new();
    let mut offset = 0usize;
    loop {
        let users = list_users_page(client, account, search, offset, PAGE).await?;
        if users.is_empty() {
            break;
        }
        let count = users.len();
        // Deduplicate while appending: when users are created/deleted mid-
        // pagination the offset shifts and pages overlap — a user id must
        // never appear twice in the result (same guard as `list_groups`).
        let new_count = extend_new(&mut seen, &mut all, &users);
        if new_count == 0 {
            // Progress guard: the server ignored `offset` and repeated an
            // already-seen page — stop instead of looping forever.
            break;
        }
        if count < PAGE {
            break;
        }
        offset += PAGE;
    }
    Ok((all, false))
}

/// Insert every element of `page` into `seen`, appending only the ones that
/// were new to `dst`. Returns how many elements were new.
fn extend_new(seen: &mut HashSet<String>, dst: &mut Vec<String>, page: &[String]) -> usize {
    let mut new_count = 0usize;
    for item in page {
        if seen.insert(item.clone()) {
            dst.push(item.clone());
            new_count += 1;
        }
    }
    new_count
}

/// Fetch a single page of users via the OCS `offset`/`limit` parameters.
async fn list_users_page(
    client: &Client,
    account: &Account,
    search: &str,
    offset: usize,
    limit: usize,
) -> AppResult<Vec<String>> {
    let mut url = format!(
        "{}/ocs/v1.php/cloud/users?format=json&limit={}&offset={}",
        account.base_url(),
        limit,
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
    Ok(json
        .pointer("/ocs/data/users")
        .and_then(|u| u.as_array())
        .map(|arr| {
            arr.iter()
                .filter_map(|v| v.as_str().map(String::from))
                .collect()
        })
        .unwrap_or_default())
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
        // L17-N4: stop on the raw page length (like `list_users`), not on the
        // number of new entries — a full 200-entry page with a single
        // duplicate (entries moved mid-pagination) must not end the paging.
        let count = groups.len();
        let mut new_groups = 0usize;
        for group in groups {
            if seen.insert(group.clone()) {
                all.push(group);
                new_groups += 1;
            }
        }
        // Guard against servers that ignore `offset` and return the same page
        // again: stop instead of looping forever on duplicate pages.
        if new_groups == 0 {
            break;
        }
        if count < LIMIT {
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

/// Create a share for a path relative to the user's files root.
///
/// `opts` controls the share kind (public link vs. user/group), the recipient
/// (`share_with`) and link options (password, expiry, public upload). By
/// default a read-only public link is created (OCS `shareType=3` +
/// `permissions=1`), preserving the pre-existing behaviour.
///
/// `target_user` switches the share to another user's files (admin
/// impersonation); the share is then attributed to that user's namespace.
pub async fn create_share(
    client: &Client,
    account: &Account,
    rel_path: &str,
    target_user: Option<&str>,
    opts: ShareOptions<'_>,
) -> AppResult<Share> {
    let url = format!(
        "{}/ocs/v2.php/apps/files_sharing/api/v1/shares?format=json",
        account.base_url()
    );
    let form = build_share_form(rel_path, &opts);
    let fields: Vec<(&str, &str)> = form
        .iter()
        .map(|(key, value)| (key.as_str(), value.as_str()))
        .collect();
    let res = request_as(
        client,
        account,
        Method::POST,
        &url,
        Some(&fields),
        target_user,
    )
    .await?;
    let json: Value = res.json().await?;
    if let Some(msg) = ocs_meta_error(&json) {
        return Err(AppError::Ocs(msg));
    }
    let share = json
        .pointer("/ocs/data")
        .and_then(parse_share)
        .ok_or_else(|| AppError::Parse("share endpoint returned no share data".into()))?;
    verify_share_owner(&share, target_user)?;
    Ok(share)
}

/// Impersonation guard for share responses: when operating as another user,
/// the server must attribute the share to that user (`uid_owner`). A share
/// owned by anyone else proves that `Impersonate-User` was ignored and the
/// operation silently happened in the admin's namespace — refuse it instead
/// of showing/creating wrong-namespace shares.
fn verify_share_owner(share: &Share, target_user: Option<&str>) -> AppResult<()> {
    let Some(target) = target_user else {
        return Ok(());
    };
    if share.uid_owner.as_deref() != Some(target) {
        return Err(AppError::App(format!(
            "Server did not honor the impersonated namespace for '{}'.",
            target
        )));
    }
    Ok(())
}

/// Options controlling `create_share`. The defaults (read-only public link)
/// keep the original behaviour when no option is provided.
#[derive(Debug, Clone)]
pub struct ShareOptions<'a> {
    /// OCS shareType: 0 = user, 1 = group, 3 = public link.
    pub share_type: u32,
    /// Recipient for user/group shares (username or group name).
    pub share_with: Option<&'a str>,
    /// Password protecting a public link.
    pub password: Option<&'a str>,
    /// Expiry date as `YYYY-MM-DD`.
    pub expire_date: Option<&'a str>,
    /// Explicit OCS permission bits. Takes precedence over `public_upload`.
    pub permissions: Option<u32>,
    /// Allow uploads to a public link (maps to permissions 15).
    pub public_upload: bool,
}

impl Default for ShareOptions<'_> {
    fn default() -> Self {
        Self {
            share_type: 3,
            share_with: None,
            password: None,
            expire_date: None,
            permissions: None,
            public_upload: false,
        }
    }
}

/// Build the OCS share form. The `path` goes in RAW: `req.form()` applies a
/// single form-url-encoding pass and PHP decodes it once, so the server
/// receives the raw path ("My Folder"). Encoding here a second time
/// (`encode_segments`) would produce "%2520" → "path not found" for any path
/// with spaces, umlauts or `#`/`&`/`+`/`?` (F4).
fn build_share_form(rel_path: &str, opts: &ShareOptions<'_>) -> Vec<(String, String)> {
    let mut form = vec![
        ("path".to_string(), rel_path.to_string()),
        ("shareType".to_string(), opts.share_type.to_string()),
    ];
    if let Some(with) = opts.share_with {
        form.push(("shareWith".to_string(), with.to_string()));
    }
    if let Some(password) = opts.password {
        form.push(("password".to_string(), password.to_string()));
    }
    if let Some(expire) = opts.expire_date {
        form.push(("expireDate".to_string(), expire.to_string()));
    }
    let permissions = opts.permissions.or({
        if opts.public_upload {
            Some(15)
        } else if opts.share_type == 3 {
            Some(1)
        } else {
            None
        }
    });
    if let Some(p) = permissions {
        form.push(("permissions".to_string(), p.to_string()));
    }
    form
}
/// List the shares for a path (or all shares of the account when `path` is
/// `None`).
pub async fn list_shares(
    client: &Client,
    account: &Account,
    path: Option<&str>,
    target_user: Option<&str>,
) -> AppResult<Vec<Share>> {
    let mut url = format!(
        "{}/ocs/v2.php/apps/files_sharing/api/v1/shares?format=json",
        account.base_url()
    );
    if let Some(p) = path {
        url.push_str("&path=");
        url.push_str(&urlencoding::encode(p));
    }
    let res = request_as(client, account, Method::GET, &url, None, target_user).await?;
    let json: Value = res.json().await?;
    if let Some(msg) = ocs_meta_error(&json) {
        return Err(AppError::Ocs(msg));
    }
    let shares: Vec<Share> = json
        .pointer("/ocs/data")
        .and_then(|data| data.as_array())
        .map(|arr| arr.iter().filter_map(parse_share).collect())
        .unwrap_or_default();
    // Impersonation guard: while browsing as another user, drop every share
    // that is not owned by the target user (the server ignored the
    // `Impersonate-User` header and answered with the admin's shares).
    let filtered: Vec<Share> = shares
        .into_iter()
        .filter(|share| match target_user {
            Some(target) => share.uid_owner.as_deref() == Some(target),
            None => true,
        })
        .collect();
    Ok(filtered)
}

/// Revoke a share by id.
pub async fn delete_share(
    client: &Client,
    account: &Account,
    share_id: u64,
    target_user: Option<&str>,
) -> AppResult<()> {
    let url = format!(
        "{}/ocs/v2.php/apps/files_sharing/api/v1/shares/{}?format=json",
        account.base_url(),
        share_id
    );
    let res = request_as(client, account, Method::DELETE, &url, None, target_user).await?;
    let json: Value = res.json().await?;
    if let Some(msg) = ocs_meta_error(&json) {
        return Err(AppError::Ocs(msg));
    }
    Ok(())
}

/// Parse a share object from the OCS `data` payload. Unknown/falsy fields are
/// mapped to `None` so a partially malformed response does not break the whole
/// listing.
fn parse_share(value: &Value) -> Option<Share> {
    let id = value.get("id").and_then(|v| v.as_u64())?;
    Some(Share {
        id,
        share_type: value
            .get("share_type")
            .and_then(|v| v.as_u64())
            .map(|v| v as u32)
            .unwrap_or(0),
        path: value.get("path").and_then(|v| v.as_str()).map(String::from),
        share_with: value
            .get("share_with")
            .and_then(|v| v.as_str())
            .map(String::from),
        share_with_displayname: value
            .get("share_with_displayname")
            .and_then(|v| v.as_str())
            .map(String::from),
        permissions: value
            .get("permissions")
            .and_then(|v| v.as_u64())
            .map(|v| v as u32),
        url: value.get("url").and_then(|v| v.as_str()).map(String::from),
        has_password: match value.get("password") {
            Some(v) if v.is_boolean() => v.as_bool(),
            Some(v) if v.is_string() => Some(!v.as_str().unwrap_or_default().is_empty()),
            Some(v) if v.is_null() => Some(false),
            _ => None,
        },
        expiration: value
            .get("expiration")
            .and_then(|v| v.as_str())
            .map(String::from),
        uid_owner: value
            .get("uid_owner")
            .and_then(|v| v.as_str())
            .map(String::from),
    })
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

    fn form_map(form: &[(String, String)]) -> std::collections::HashMap<&str, &str> {
        form.iter().map(|(k, v)| (k.as_str(), v.as_str())).collect()
    }

    #[test]
    fn share_form_keeps_path_raw_for_roundtrip() {
        // The path must survive a single form-url-encoding pass intact (F4).
        let raw = "/My Folder/#test&more+file?.txt";
        let opts = ShareOptions::default();
        let form = build_share_form(raw, &opts);
        let map = form_map(&form);
        assert_eq!(map.get("path"), Some(&raw));
        assert_eq!(map.get("shareType"), Some(&"3"));
        assert_eq!(map.get("permissions"), Some(&"1"));
    }

    #[test]
    fn share_form_supports_private_user_and_group_shares() {
        let user = ShareOptions {
            share_type: 0,
            share_with: Some("alice"),
            ..ShareOptions::default()
        };
        let form = build_share_form("/Documents/report.pdf", &user);
        let map = form_map(&form);
        assert_eq!(map.get("shareType"), Some(&"0"));
        assert_eq!(map.get("shareWith"), Some(&"alice"));
        // user/group shares keep the server default permissions (no override)
        assert!(!map.contains_key("permissions"));

        let group = ShareOptions {
            share_type: 1,
            share_with: Some("team"),
            ..ShareOptions::default()
        };
        let form = build_share_form("/Team", &group);
        let map = form_map(&form);
        assert_eq!(map.get("shareType"), Some(&"1"));
        assert_eq!(map.get("shareWith"), Some(&"team"));
    }

    #[test]
    fn share_form_supports_link_options() {
        let link = ShareOptions {
            share_type: 3,
            password: Some("secret"),
            expire_date: Some("2026-12-31"),
            public_upload: true,
            ..ShareOptions::default()
        };
        let form = build_share_form("/Album", &link);
        let map = form_map(&form);
        assert_eq!(map.get("password"), Some(&"secret"));
        assert_eq!(map.get("expireDate"), Some(&"2026-12-31"));
        // publicUpload → permissions 15 (read + write + create + delete)
        assert_eq!(map.get("permissions"), Some(&"15"));

        // explicit permissions take precedence over public_upload
        let explicit = ShareOptions {
            permissions: Some(1),
            public_upload: true,
            ..ShareOptions::default()
        };
        let form = build_share_form("/X", &explicit);
        let map = form_map(&form);
        assert_eq!(map.get("permissions"), Some(&"1"));
    }

    #[test]
    fn parse_share_maps_ocs_payload() {
        let value = serde_json::json!({
            "id": 42,
            "share_type": 3,
            "path": "/Album",
            "share_with": null,
            "permissions": 15,
            "url": "https://cloud.example/s/abc123",
            "password": null,
            "expiration": "2026-12-31T00:00:00+00:00"
        });
        let share = parse_share(&value).expect("share parses");
        assert_eq!(share.id, 42);
        assert_eq!(share.share_type, 3);
        assert_eq!(share.url.as_deref(), Some("https://cloud.example/s/abc123"));
        assert_eq!(share.has_password, Some(false));
        assert_eq!(share.permissions, Some(15));

        let user = serde_json::json!({
            "id": 7,
            "share_type": 0,
            "share_with": "alice",
            "share_with_displayname": "Alice",
            "password": ""
        });
        let share = parse_share(&user).expect("share parses");
        assert_eq!(share.share_with.as_deref(), Some("alice"));
        assert_eq!(share.has_password, Some(false));
        assert!(share.url.is_none());
    }

    #[test]
    fn progress_guard_stops_on_repeated_page() {
        // Server ignores `offset` and repeats the same full page — the guard
        // must detect zero progress and stop the pagination.
        let page: Vec<String> = (0..200).map(|i| format!("user{i}")).collect();
        let mut seen = HashSet::new();
        let mut dst = Vec::new();
        assert_eq!(extend_new(&mut seen, &mut dst, &page), 200);
        assert_eq!(extend_new(&mut seen, &mut dst, &page), 0);
    }

    #[test]
    fn progress_guard_counts_partial_new_users() {
        let mut seen = HashSet::from([String::from("a")]);
        let page = vec![String::from("a"), String::from("b")];
        let mut dst = Vec::new();
        assert_eq!(extend_new(&mut seen, &mut dst, &page), 1);
    }

    #[test]
    fn overlapping_pages_are_deduplicated() {
        // L15-W6: users created/deleted mid-pagination shift the offset so
        // pages overlap — ids must never appear twice in the merged result.
        let mut seen = HashSet::new();
        let mut all = Vec::new();
        let page1: Vec<String> = (0..5).map(|i| format!("user{i}")).collect();
        let page2: Vec<String> = (3..8).map(|i| format!("user{i}")).collect();
        extend_new(&mut seen, &mut all, &page1);
        extend_new(&mut seen, &mut all, &page2);
        let expected: Vec<String> = (0..8).map(|i| format!("user{i}")).collect();
        assert_eq!(all, expected);
    }

    #[test]
    fn share_owner_verification_rejects_foreign_namespace() {
        // Impersonation honored → ok.
        let own = Share {
            id: 1,
            share_type: 3,
            path: Some("/Album".into()),
            share_with: None,
            share_with_displayname: None,
            permissions: Some(1),
            url: None,
            has_password: None,
            expiration: None,
            uid_owner: Some("target".into()),
        };
        assert!(verify_share_owner(&own, Some("target")).is_ok());

        // Server ignored `Impersonate-User`: the share is owned by the admin.
        let admin_owned = Share {
            uid_owner: Some("admin".into()),
            ..own.clone()
        };
        let err = verify_share_owner(&admin_owned, Some("target")).unwrap_err();
        assert!(err.message().contains("target"));

        // Without impersonation every owner is accepted.
        assert!(verify_share_owner(&admin_owned, None).is_ok());
        assert!(verify_share_owner(&own, None).is_ok());
    }

    #[test]
    fn parse_share_reads_uid_owner() {
        let value = serde_json::json!({
            "id": 9,
            "share_type": 0,
            "uid_owner": "alice",
        });
        let share = parse_share(&value).expect("share parses");
        assert_eq!(share.uid_owner.as_deref(), Some("alice"));

        let legacy = serde_json::json!({ "id": 10 });
        let share = parse_share(&legacy).expect("share parses");
        assert_eq!(share.uid_owner, None);
    }
}
