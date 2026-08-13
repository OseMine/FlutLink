use std::sync::OnceLock;

use reqwest::Client;
use serde_json::Value;

use crate::error::{AppError, AppResult};
use crate::nextcloud;
use crate::state::Account;

/// The only Nextcloud server FlutLink connects to.
///
/// FlutLink is a dedicated client for the FlutCloud server, not a generic
/// Nextcloud client. This URL is enforced everywhere an account is created.
/// The value is only ever read from the `FLUTCLOUD_URL` variable in the local
/// `.env` file (which is gitignored); it is intentionally not hard-coded
/// anywhere in this repository.
pub fn flutcloud_url() -> AppResult<String> {
    static URL: OnceLock<Result<String, String>> = OnceLock::new();
    URL.get_or_init(|| {
        let _ = dotenvy::dotenv();
        std::env::var("FLUTCLOUD_URL").map_err(|_| {
            "FLUTCLOUD_URL is not set. Add it to the `.env` file in the repository root."
                .to_string()
        })
    })
    .clone()
    .map_err(AppError::App)
}

/// Strip trailing slashes so `https://flutcloud.example/` matches
/// `https://flutcloud.example`.
fn normalize_url(url: &str) -> String {
    url.trim_end_matches('/').to_string()
}

/// Normalize and validate an instance URL. Any server other than the FlutCloud
/// server is rejected with `AppError::NotFlutCloud`.
pub fn assert_flutcloud_url(instance_url: &str) -> AppResult<String> {
    let normalized = normalize_url(instance_url);
    if normalized.eq_ignore_ascii_case(&flutcloud_url()?) {
        Ok(normalized)
    } else {
        Err(AppError::NotFlutCloud(normalized))
    }
}

/// Verify that the server is a real FlutCloud server by querying the OCS
/// capabilities endpoint and checking for the `flutcloud` capability. The
/// FlutCloud Nextcloud app (see `flutcloud-app/`) advertises it.
pub async fn verify_server(client: &Client, account: &Account) -> AppResult<()> {
    let url = format!(
        "{}/ocs/v2.php/cloud/capabilities?format=json",
        account.base_url()
    );
    let res = nextcloud::request(client, account, reqwest::Method::GET, &url, None).await?;
    let json: Value = res.json().await?;
    if let Some(msg) = nextcloud::ocs_meta_error(&json) {
        return Err(AppError::Ocs(msg));
    }
    if json.pointer("/ocs/data/capabilities/flutcloud").is_none() {
        return Err(AppError::FlutCloudAppMissing);
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn normalizes_trailing_slashes() {
        assert_eq!(normalize_url("https://server/"), "https://server");
        assert_eq!(normalize_url("https://server///"), "https://server");
        assert_eq!(normalize_url("https://server"), "https://server");
    }

    #[test]
    fn accepts_the_flutcloud_url_with_and_without_slash() {
        let Ok(url) = flutcloud_url() else {
            eprintln!("FLUTCLOUD_URL not set; skipping");
            return;
        };
        assert_eq!(assert_flutcloud_url(&url).unwrap(), url);
        assert_eq!(assert_flutcloud_url(&format!("{url}/")).unwrap(), url);
    }

    #[test]
    fn rejects_foreign_servers() {
        assert!(assert_flutcloud_url("https://nextcloud.example.com").is_err());
        assert!(assert_flutcloud_url("https://evil.example.org").is_err());
    }
}
