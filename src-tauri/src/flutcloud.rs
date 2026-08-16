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
    // Only the *success* value is cached. When the variable is missing, the
    // error is not stored so a corrected `.env` takes effect without a restart.
    static URL: OnceLock<String> = OnceLock::new();
    if let Some(url) = URL.get() {
        return Ok(url.clone());
    }
    let _ = dotenvy::dotenv();
    let url = std::env::var("FLUTCLOUD_URL").map_err(|_| {
        AppError::App(
            "FLUTCLOUD_URL is not set. Add it to the `.env` file in the repository root.".into(),
        )
    })?;
    Ok(URL.get_or_init(|| url.clone()).clone())
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
    // R7-1: normalize the env value too, so a trailing slash in `.env`
    // (`https://flutcloud.de/`) still matches the instance URL.
    if urls_equal(instance_url, &flutcloud_url()?) {
        Ok(normalized)
    } else {
        Err(AppError::NotFlutCloud(normalized))
    }
}

/// Compare two URLs for equality, ignoring trailing slashes and case. Used by
/// [`assert_flutcloud_url`] and covered directly in tests so both the instance
/// side and the `.env` side get slash handling.
fn urls_equal(a: &str, b: &str) -> bool {
    normalize_url(a).eq_ignore_ascii_case(&normalize_url(b))
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
    fn trailing_slash_on_env_side_still_matches() {
        // R7-1: a trailing slash in `.env` (`FLUTCLOUD_URL=https://…/`) must not
        // break account_add/register_user. urls_equal normalizes both sides, so
        // this holds regardless of which side carries the slash.
        assert!(urls_equal("https://flutcloud.de", "https://flutcloud.de/"));
        assert!(urls_equal("https://flutcloud.de/", "https://flutcloud.de"));
        assert!(urls_equal(
            "https://flutcloud.de///",
            "https://flutcloud.de"
        ));
        assert!(urls_equal("https://flutcloud.de", "HTTPS://FLUTCLOUD.DE"));
        assert!(!urls_equal("https://flutcloud.de", "https://other.example"));
    }

    #[test]
    fn rejects_foreign_servers() {
        assert!(assert_flutcloud_url("https://nextcloud.example.com").is_err());
        assert!(assert_flutcloud_url("https://evil.example.org").is_err());
    }
}
