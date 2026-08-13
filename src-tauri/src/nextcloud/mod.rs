pub mod ocs;
pub mod webdav;

use reqwest::{Client, Method, Response};
use serde_json::Value;

use crate::error::{AppError, AppResult};
use crate::state::Account;

/// Perform an authenticated OCS request against the account's instance.
pub async fn request(
    client: &Client,
    account: &Account,
    method: Method,
    url: &str,
    form: Option<&[(&str, &str)]>,
) -> AppResult<Response> {
    let mut req = client
        .request(method, url)
        .basic_auth(&account.meta.username, Some(&account.token))
        .header("OCS-APIRequest", "true")
        .header("Accept", "application/json");
    if let Some(fields) = form {
        req = req.form(fields);
    }
    let res = req.send().await?;
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

/// Inspect an OCS JSON payload. Returns an error message when the request
/// did not succeed. Success is signalled by `meta.status == "ok"` (v1 uses
/// `statuscode` 100, v2 uses `statuscode` 200).
pub fn ocs_meta_error(json: &Value) -> Option<String> {
    let meta = json.pointer("/ocs/meta")?;

    let status = meta.get("status").and_then(|v| v.as_str());
    if status.is_some_and(|s| s.eq_ignore_ascii_case("ok")) {
        return None;
    }

    let statuscode = meta
        .get("statuscode")
        .and_then(|v| v.as_u64())
        .or_else(|| {
            meta.get("statuscode")
                .and_then(|v| v.as_str())
                .and_then(|s| s.trim().parse().ok())
        })
        .unwrap_or(0);
    if statuscode == 100 || statuscode == 200 {
        return None;
    }

    Some(
        meta.get("message")
            .and_then(|m| m.as_str())
            .unwrap_or("Unknown OCS error")
            .to_string(),
    )
}

/// Encode every path segment, keeping separators intact.
pub fn encode_segments(path: &str) -> String {
    path.split('/')
        .filter(|segment| !segment.is_empty())
        .map(|segment| urlencoding::encode(segment))
        .collect::<Vec<_>>()
        .join("/")
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn accepts_v1_success() {
        let payload =
            json!({"ocs": {"meta": {"status": "ok", "statuscode": 100, "message": "OK"}}});
        assert!(ocs_meta_error(&payload).is_none());
    }

    #[test]
    fn accepts_v2_success() {
        let payload =
            json!({"ocs": {"meta": {"status": "ok", "statuscode": 200, "message": "OK"}}});
        assert!(ocs_meta_error(&payload).is_none());
    }

    #[test]
    fn accepts_string_statuscode() {
        let payload =
            json!({"ocs": {"meta": {"status": "ok", "statuscode": "100", "message": "OK"}}});
        assert!(ocs_meta_error(&payload).is_none());
    }

    #[test]
    fn rejects_failure() {
        let payload = json!({"ocs": {"meta": {"status": "failure", "statuscode": 997, "message": "Unauthorised"}}});
        assert_eq!(ocs_meta_error(&payload).as_deref(), Some("Unauthorised"));
    }

    #[test]
    fn missing_meta_is_not_an_ocs_error() {
        assert!(ocs_meta_error(&json!({"data": []})).is_none());
    }
}
